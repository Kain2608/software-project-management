#!/usr/bin/env python3
"""Exercise D22 logical backup/restore on a dedicated PostgreSQL 16 database.

Python stdlib and PostgreSQL clients only. --apply bootstraps an EMPTY isolated
source; it refuses an existing sasms schema. Without --apply, use a dedicated
upgraded test database. Never run this fixture/provisioning tool on production.
Archive and restored database are retained; no database is dropped automatically.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid

from db_ops import redact as redact_connection_text, target_dsn

ROOT = Path(__file__).resolve().parents[1]
STORE = '00000000-0000-4000-8000-000000000010'
RECEIPT_ITEM = '00000000-0000-4000-8000-000000000061'


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


class Drill:
    def __init__(self, args):
        self.args = args
        self.env = dict(os.environ, PGCLIENTENCODING='UTF8')
        self.lines = []
        self.summary = {'started_at_utc': datetime.now(timezone.utc).isoformat(),
                        'status': 'running', 'checks': {},
                        'scope': 'isolated logical-backup drill only; no remote Staging/Prod, PITR, SLA or approval claim'}
        self.suffix = uuid.uuid4().hex[:16]
        self.target = 'sasms_restore_drill_' + self.suffix
        self.tamper_target = 'sasms_tamper_probe_' + self.suffix
        self.actor = str(uuid.uuid4())
        self.request_key = str(uuid.uuid4())
        self.serials = ['ENV-' + self.suffix.upper() + '-01', 'ENV-' + self.suffix.upper() + '-02']
        self.payload = [{'serial': self.serials[0], 'imei': None},
                        {'serial': self.serials[1], 'imei': '77' + str(uuid.uuid4().int)[-13:]}]
        self.fixture_created = False

    def redact(self, value: str) -> str:
        return redact_connection_text(value, self.args.source_dsn, self.args.maintenance_dsn)

    def log(self, text: str):
        self.lines.append(self.redact(text))

    def command(self, label, command, *, input_text=None, expected_failure=False):
        result = subprocess.run(command, cwd=ROOT, env=self.env, input=input_text,
                                capture_output=True, text=True, encoding='utf-8',
                                errors='replace', timeout=180)
        self.log(f'\n=== {label} ===\n{result.stdout}{result.stderr}'.rstrip())
        if expected_failure:
            if result.returncode == 0:
                raise RuntimeError(label + ': expected refusal, command succeeded')
            self.summary['checks'][label] = {'passed': True, 'expected_refusal': True}
        elif result.returncode:
            raise RuntimeError(label + f': command failed ({result.returncode}); see environment-validation.log')
        else:
            self.summary['checks'][label] = {'passed': True}
        return result

    def psql(self, label, sql=None, *, target=None, maintenance=False, file=None):
        dsn = self.args.maintenance_dsn if maintenance or target else self.args.source_dsn
        command = [self.args.psql, '-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1',
                   '-v', 'VERBOSITY=verbose', '-P', 'pager=off', '--dbname', dsn]
        if file:
            return self.command(label, command + ['-f', str(ROOT/file)])
        prefix = ''
        if target:
            if not re.fullmatch(r'[a-z][a-z0-9_]{0,62}', target):
                raise ValueError('Unsafe generated database name')
            # psql reuses host/user/TLS/password connection settings without
            # reconstructing or printing either libpq DSN or URI credentials.
            prefix = '\\connect -reuse-previous=on ' + target + '\n'
        return self.command(label, command + ['-f', '-'], input_text=prefix + "SET timezone='UTC';\n" + sql)

    @staticmethod
    def json_result(result):
        value = result.stdout.strip()
        try:
            return json.loads(value)
        except json.JSONDecodeError:
            # Quiet psql normally emits only the JSON. This also accepts an
            # informational connection line from older compatible clients.
            for line in value.splitlines():
                if line.startswith('{') or line.startswith('['):
                    return json.loads(line)
            raise RuntimeError('Expected a JSON result from PostgreSQL/tool')

    def fingerprint(self, label, *, target=None):
        tables = self.json_result(self.psql(label + ' tables', """
SELECT json_agg(schema_name||'.'||table_name ORDER BY schema_name COLLATE "C",table_name COLLATE "C")
FROM (SELECT table_schema AS schema_name,table_name FROM information_schema.tables
 WHERE table_schema IN ('sasms','sasms_meta') AND table_type='BASE TABLE') t;
""", target=target))
        if not tables or any(not re.fullmatch(r'(sasms|sasms_meta)\.[a-z_]+', name) for name in tables):
            raise RuntimeError('Unexpected table names in fingerprint')
        query = ' UNION ALL '.join(
            f"SELECT '{name}' name,md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text COLLATE \"C\")::text,'[]')) digest FROM {name} t"
            for name in tables)
        data = self.json_result(self.psql(label + ' data', f'SELECT jsonb_object_agg(name,digest) FROM ({query}) s;', target=target))
        return data

    def catalog(self, label, *, target=None):
        result = self.json_result(self.psql(label, r"""
SELECT json_build_object(
 'columns',(SELECT json_agg(t ORDER BY schema_name COLLATE "C",table_name COLLATE "C",position) FROM (
  SELECT n.nspname schema_name,c.relname table_name,a.attnum position,a.attname column_name,
   format_type(a.atttypid,a.atttypmod) data_type,a.attnotnull not_null,a.attgenerated generated,
   pg_get_expr(d.adbin,d.adrelid) default_expression
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
  LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
  WHERE n.nspname IN ('sasms','sasms_meta') AND c.relkind IN ('r','v')) t),
 'constraints',(SELECT json_agg(t ORDER BY schema_name COLLATE "C",table_name COLLATE "C",constraint_name COLLATE "C") FROM (
  SELECT n.nspname schema_name,c.relname table_name,k.conname constraint_name,pg_get_constraintdef(k.oid,true) definition
  FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname IN ('sasms','sasms_meta')) t),
 'indexes',(SELECT json_agg(t ORDER BY schemaname COLLATE "C",indexname COLLATE "C") FROM (
  SELECT schemaname,indexname,indexdef FROM pg_indexes WHERE schemaname IN ('sasms','sasms_meta')) t),
 'routines',(SELECT json_agg(t ORDER BY routine_name COLLATE "C",arguments COLLATE "C") FROM (
  SELECT p.proname routine_name,pg_get_function_identity_arguments(p.oid) arguments,
   p.prosecdef security_definer,p.proconfig configuration,pg_get_functiondef(p.oid) definition
  FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='sasms') t),
 'triggers',(SELECT json_agg(t ORDER BY table_name COLLATE "C",trigger_name COLLATE "C") FROM (
  SELECT c.relname table_name,g.tgname trigger_name,pg_get_triggerdef(g.oid) definition
  FROM pg_trigger g JOIN pg_class c ON c.oid=g.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname='sasms' AND NOT g.tgisinternal) t),
 'views',(SELECT json_agg(t ORDER BY view_name COLLATE "C") FROM (
  SELECT c.relname view_name,pg_get_viewdef(c.oid,true) definition
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='sasms' AND c.relkind='v') t),
 'acl',(SELECT json_agg(t ORDER BY kind COLLATE "C",object_name COLLATE "C",grantee COLLATE "C",privilege_type COLLATE "C",is_grantable) FROM (
  SELECT 'relation' kind,n.nspname||'.'||c.relname object_name,
   CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END grantee,a.privilege_type,a.is_grantable
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  CROSS JOIN LATERAL aclexplode(coalesce(c.relacl,acldefault('r',c.relowner))) a
  WHERE n.nspname IN ('sasms','sasms_meta') AND c.relkind IN ('r','v') AND a.grantee<>c.relowner
  UNION ALL
  SELECT 'routine',n.nspname||'.'||p.proname||'('||pg_get_function_identity_arguments(p.oid)||')',
   CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END,a.privilege_type,a.is_grantable
  FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
  CROSS JOIN LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a
  WHERE n.nspname='sasms' AND a.grantee<>p.proowner) t),
 'critical_runtime_acl',json_build_object(
  'batch_execute',has_function_privilege('sasms_app','sasms.receive_device_batch(uuid,uuid,jsonb,uuid)','EXECUTE'),
  'private_receive_execute',has_function_privilege('sasms_app','sasms.receive_device_unchecked_s0(uuid,text,text,uuid)','EXECUTE'),
  'direct_stock_update',has_table_privilege('sasms_app','sasms.devices','UPDATE'),
  'direct_audit_insert',has_table_privilege('sasms_app','sasms.audit_logs','INSERT'),
  'reader_customer_select',has_table_privilege('sasms_reader','sasms.customers','SELECT'))
);
""", target=target))
        expected = {'batch_execute': True, 'private_receive_execute': False, 'direct_stock_update': False,
                    'direct_audit_insert': False, 'reader_customer_select': False}
        if result['critical_runtime_acl'] != expected:
            raise RuntimeError('Critical runtime ACL does not match the intended baseline')
        digest = hashlib.sha256(json.dumps(result, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
        return digest, {key: len(value) for key, value in result.items() if isinstance(value, list)}

    def bootstrap(self):
        server = self.json_result(self.psql('PostgreSQL source version', "SELECT json_build_object('version',version(),'version_num',current_setting('server_version_num')::int,'database',current_database());"))
        if not 160000 <= server['version_num'] < 170000:
            raise RuntimeError('This drill requires PostgreSQL major version 16')
        self.summary['postgresql'] = server
        exists = self.json_result(self.psql('source schema preflight', "SELECT json_build_object('exists',to_regnamespace('sasms') IS NOT NULL OR to_regnamespace('sasms_meta') IS NOT NULL);"))['exists']
        if self.args.apply:
            if exists:
                raise RuntimeError('--apply refuses a source with an existing sasms/sasms_meta schema')
            for filename in ['database/schema.sql', 'database/seed.sql', 'database/runtime-roles.sql']:
                self.psql('bootstrap ' + filename, file=filename)
            self.command('upgrade dedicated source', [sys.executable, str(ROOT/'scripts/migrate_db.py'),
                         '--psql', self.args.psql, '--dsn', self.args.source_dsn])
            self.psql('provision Sprint1 runtime', file='database/sprint1/runtime-roles.sql')
        elif not exists:
            raise RuntimeError('Source schema is absent; use --apply only for an empty isolated database')
        count = self.json_result(self.psql('upgraded source shape', "SELECT json_build_object('tables',(SELECT count(*) FROM information_schema.tables WHERE table_schema='sasms' AND table_type='BASE TABLE'),'migrations',(SELECT count(*) FROM sasms_meta.schema_migrations));"))
        if count != {'tables': 39, 'migrations': 2}:
            raise RuntimeError('Expected the current 39-table, 2-migration test source')
        self.summary['source_shape'] = count

    def fixtures(self):
        payload = json.dumps(self.payload, separators=(',', ':'))
        sql = f"""
BEGIN;
INSERT INTO sasms.users(id,email,full_name,is_active)
 VALUES('{self.actor}','environment-{self.suffix}@example.invalid','Synthetic environment drill actor',true);
INSERT INTO sasms.user_roles(user_id,role_code,store_id) VALUES('{self.actor}','ADMIN',NULL);
SELECT to_json(sasms.receive_device_batch('{RECEIPT_ITEM}','{self.request_key}','{payload}'::jsonb,'{self.actor}'));
COMMIT;
"""
        self.device_ids = self.json_result(self.psql('create two synthetic imported devices', sql))
        if len(self.device_ids) != 2:
            raise RuntimeError('Expected two fixture device IDs')
        self.fixture_created = True

    def cleanup(self):
        if not self.fixture_created:
            return
        self.psql('exact source fixture cleanup', f"""
BEGIN;
DELETE FROM sasms.inventory_import_batches WHERE request_key='{self.request_key}';
DELETE FROM sasms.inventory_movements WHERE actor_id='{self.actor}';
DELETE FROM sasms.audit_logs WHERE actor_id='{self.actor}';
DELETE FROM sasms.outbox_events WHERE aggregate_id IN(SELECT id FROM sasms.devices WHERE serial IN('{self.serials[0]}','{self.serials[1]}'));
DELETE FROM sasms.devices WHERE serial IN('{self.serials[0]}','{self.serials[1]}');
DELETE FROM sasms.user_roles WHERE user_id='{self.actor}';
DELETE FROM sasms.users WHERE id='{self.actor}';
COMMIT;
""")
        self.fixture_created = False

    def ops(self, label, arguments, *, fail=False):
        result = self.command(label, [sys.executable, str(ROOT/'scripts/db_ops.py')] + arguments,
                              expected_failure=fail)
        return None if fail else self.json_result(result)

    def connection_guards(self):
        # Synthetic strings only, never emitted to stdout/log/evidence. Cover URI
        # raw '+' semantics and libpq quoting independently of any live password.
        uri = 'postgresql://test_user:p%2Bass@db.example.invalid:5432/source?sslmode=verify-full&password=a+b%2Bc&dbname=ignored&application_name=drill'
        expected_uri = 'postgresql://test_user:p%2Bass@db.example.invalid:5432/target_drill?sslmode=verify-full&password=a+b%2Bc&application_name=drill'
        keyword = "host='db.example.invalid' user='test_user' password='ab\\'cd\\\\ef' dbname='source name' sslmode=verify-full"
        expected_keyword = "host='db.example.invalid' user='test_user' password='ab\\'cd\\\\ef' sslmode='verify-full' dbname='target_drill'"
        if target_dsn(uri, 'target_drill') != expected_uri:
            raise RuntimeError('Connection helper failed URI option preservation')
        if target_dsn(keyword, 'target_drill') != expected_keyword:
            raise RuntimeError('Connection helper failed quoted keyword preservation')
        if redact_connection_text("connection failed password='ab\\'cd\\\\ef' extra") != 'connection failed password=[REDACTED] extra':
            raise RuntimeError('Connection helper leaked an escaped quoted password fragment')
        if redact_connection_text('postgresql://test_user:synthetic-secret@db.example.invalid/source') != 'postgresql://test_user:[REDACTED]@db.example.invalid/source':
            raise RuntimeError('Connection helper leaked a URI password')
        self.summary['connection_helper_assertions'] = 4
        self.summary['checks']['connection string preservation and credential redaction'] = {'passed': True, 'assertions': 4}
        self.log('PASS: four synthetic connection-string/redaction assertions; credential values not logged.')

    def validate(self):
        source_paths = [Path('scripts/validate_environment.py'), Path('scripts/db_ops.py'),
                        Path('scripts/migrate_db.py'), Path('database/schema.sql'), Path('database/seed.sql'),
                        Path('database/runtime-roles.sql'), Path('database/sprint1/runtime-roles.sql')]
        source_paths += [p.relative_to(ROOT) for p in sorted((ROOT/'database/migrations').glob('V*.sql'))]
        self.summary['source_sha256'] = {p.as_posix(): sha256(ROOT/p) for p in source_paths}
        self.connection_guards()
        self.summary['client_versions'] = {}
        for name, path in [('psql', self.args.psql), ('pg_dump', self.args.pg_dump), ('pg_restore', self.args.pg_restore)]:
            result = self.command(name + ' version', [path, '--version'])
            self.summary['client_versions'][name] = result.stdout.strip()
        self.bootstrap()
        baseline = self.fingerprint('source before synthetic fixtures')
        baseline_catalog, _ = self.catalog('source schema before drill')
        try:
            self.fixtures()
            original = self.fingerprint('quiescent source before backup')
            original_catalog, counts = self.catalog('source schema and ACL before backup')
            self.summary['catalog_objects'] = counts
            work = self.args.work_dir/self.suffix
            work.mkdir(parents=True, exist_ok=False)
            archive = work/'source.dump'
            manifest = Path(str(archive)+'.manifest.json')
            backup_args = ['backup', '--dsn', self.args.source_dsn, '--output', str(archive),
                           '--psql', self.args.psql, '--pg-dump', self.args.pg_dump]
            backup = self.ops('logical backup of quiescent source', backup_args)
            if not archive.is_file() or not manifest.is_file():
                raise RuntimeError('Backup archive and matching manifest were not created')
            archive_digest = sha256(archive)
            manifest_digest = sha256(manifest)
            if backup.get('archive_sha256') != archive_digest:
                raise RuntimeError('Backup reported checksum does not match actual archive')
            self.ops('refuse existing archive or manifest', backup_args, fail=True)
            if sha256(archive) != archive_digest or sha256(manifest) != manifest_digest:
                raise RuntimeError('Refused backup overwrote the archive or manifest')
            restore_args = ['restore', '--maintenance-dsn', self.args.maintenance_dsn,
                            '--target-db', self.target, '--input', str(archive),
                            '--psql', self.args.psql, '--pg-restore', self.args.pg_restore]
            restored = self.ops('restore only into a new private database', restore_args)
            if restored.get('target_database') != self.target or restored.get('public_connect_disabled') is not True:
                raise RuntimeError('Restore did not report the expected new private target')
            target_data = self.fingerprint('restored target', target=self.target)
            target_catalog, _ = self.catalog('restored constraints routines and runtime ACL', target=self.target)
            if target_data != original or target_catalog != original_catalog:
                raise RuntimeError('Restored data, migration history, constraints, routines or runtime grants differ')
            privacy = self.json_result(self.psql('verify PUBLIC cannot connect to restored target', """
SELECT json_build_object('public_connect_disabled',NOT EXISTS(
 SELECT 1 FROM pg_database d CROSS JOIN LATERAL aclexplode(coalesce(d.datacl,acldefault('d',d.datdba))) a
 WHERE d.datname=current_database() AND a.grantee=0 AND a.privilege_type='CONNECT'));
""", target=self.target))
            if privacy['public_connect_disabled'] is not True:
                raise RuntimeError('Restored database grants CONNECT to PUBLIC')
            retry_payload = json.dumps(self.payload, separators=(',', ':'))
            result = self.psql('retry restored batch as actual sasms_app', f"""
BEGIN;
SET LOCAL ROLE sasms_app;
SELECT to_json(sasms.receive_device_batch('{RECEIPT_ITEM}','{self.request_key}','{retry_payload}'::jsonb,'{self.actor}'));
COMMIT;
""", target=self.target)
            if self.json_result(result) != self.device_ids or self.fingerprint('restored target after retry', target=self.target) != original:
                raise RuntimeError('Restored runtime retry changed IDs or created duplicate effects')
            self.ops('refuse an already existing restore target', restore_args, fail=True)
            if self.fingerprint('existing target after refused restore', target=self.target) != original:
                raise RuntimeError('Refused restore changed the existing target')
            tamper_args = restore_args[:]
            tamper_args[tamper_args.index('--target-db')+1] = self.tamper_target
            with archive.open('r+b') as stream:
                original_byte = stream.read(1)
                stream.seek(0)
                stream.write(bytes([original_byte[0] ^ 1]))
            try:
                self.ops('refuse archive checksum tampering before CREATE DATABASE', tamper_args, fail=True)
                probe = self.json_result(self.psql('tampered archive created no target',
                    f"SELECT json_build_object('absent',NOT EXISTS(SELECT 1 FROM pg_database WHERE datname='{self.tamper_target}'));", maintenance=True))
                if probe['absent'] is not True:
                    raise RuntimeError('Tampered archive left a newly created database')
            finally:
                with archive.open('r+b') as stream:
                    stream.write(original_byte)
            if sha256(archive) != archive_digest:
                raise RuntimeError('Synthetic tamper test did not restore its own archive bytes')
            if self.fingerprint('original source after backup restore and refusal probes') != original:
                raise RuntimeError('Backup/restore operations changed original source data')
            self.summary['restore_drill'] = {'business_tables': 39, 'migration_ledger_compared': True,
                'data_and_schema_match': True, 'schema_catalog_sha256': original_catalog,
                'runtime_retry_same_device_ids': True, 'runtime_retry_no_extra_effects': True,
                'public_connect_disabled': True, 'source_unchanged_by_backup_restore': True,
                'restored_database': self.target, 'restored_database_retained_for_inspection': True,
                'archive_sha256': archive_digest, 'archive_retained': True,
                'refusal_probes': ['existing_backup_output', 'existing_restore_target', 'tampered_archive_before_create']}
        finally:
            self.cleanup()
        if self.fingerprint('source after exact fixture cleanup') != baseline:
            raise RuntimeError('Synthetic source fixtures were not cleaned exactly')
        after_catalog, _ = self.catalog('source schema after cleanup')
        if after_catalog != baseline_catalog:
            raise RuntimeError('Source schema or runtime ACL changed during the drill')
        self.summary['source_fixtures_cleaned'] = True
        if any(sha256(ROOT/path) != digest for path, digest in self.summary['source_sha256'].items()):
            raise RuntimeError('Source files changed during validation; rerun with stable sources')
        self.summary['status'] = 'passed'

    def save(self):
        self.summary['finished_at_utc'] = datetime.now(timezone.utc).isoformat()
        self.summary['check_count'] = len(self.summary['checks'])
        self.args.evidence_dir.mkdir(parents=True, exist_ok=True)
        (self.args.evidence_dir/'environment-summary.json').write_text(
            json.dumps(self.summary, ensure_ascii=False, indent=2)+'\n', encoding='utf-8', newline='\n')
        (self.args.evidence_dir/'environment-validation.log').write_text(
            '\n'.join(self.lines)+'\n', encoding='utf-8', newline='\n')


def main() -> int:
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--psql', default='psql')
    parser.add_argument('--pg-dump', default='pg_dump')
    parser.add_argument('--pg-restore', default='pg_restore')
    parser.add_argument('--source-dsn', default=os.environ.get('DATABASE_URL'), required=not os.environ.get('DATABASE_URL'))
    parser.add_argument('--maintenance-dsn', required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--evidence-dir', type=Path, default=ROOT/'Tiền Sprint/DB/evidence')
    parser.add_argument('--work-dir', type=Path, default=ROOT.parent/'_pre-sprint-validation')
    args = parser.parse_args()
    drill = Drill(args)
    try:
        drill.validate()
    except Exception as error:
        message = drill.redact(str(error))
        drill.summary['status'] = 'failed'
        drill.summary['error'] = message
        drill.log('FAILED: ' + message)
        print('Environment validation failed: ' + message, file=sys.stderr)
        return 1
    finally:
        drill.save()
    print(f"Environment validation passed: {drill.summary['check_count']} checks; new private restore, data/schema/ACL match and refusal probes.")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
