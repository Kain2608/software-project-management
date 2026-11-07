#!/usr/bin/env python3
"""Apply versioned SASMS migrations atomically, preserving the Sprint 0 baseline.

Python stdlib + psql; connection secrets are never logged. This is the project's
SQL migration runner, not a Flyway history-table implementation. Run with the
DB owner after backup/review. --status is read-only.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
NAME = re.compile(r"V([0-9]{3,})__([a-z0-9_]+)\.sql$")


def literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def top_level_sql(source: str) -> str:
    """Remove SQL strings/comments/bodies before inspecting transaction commands."""
    output, index = [], 0
    while index < len(source):
        if source.startswith('--', index):
            end = source.find('\n', index)
            index = len(source) if end < 0 else end + 1
            output.append(' ')
        elif source.startswith('/*', index):
            index += 2
            depth = 1
            while depth and index < len(source):
                if source.startswith('/*', index):
                    depth += 1
                    index += 2
                elif source.startswith('*/', index):
                    depth -= 1
                    index += 2
                else:
                    index += 1
            if depth:
                raise ValueError('Unterminated SQL comment')
            output.append(' ')
        elif source[index] in ("'", '"'):
            quote = source[index]
            escaped = quote == "'" and index > 0 and source[index-1] in 'eE'
            index += 1
            while index < len(source):
                if escaped and source[index] == '\\':
                    index += 2
                elif source[index] == quote:
                    if index + 1 < len(source) and source[index+1] == quote:
                        index += 2
                    else:
                        index += 1
                        break
                else:
                    index += 1
            output.append(' STRING ')
        elif source[index] == '$' and (tag := re.match(r'\$(?:[A-Za-z_][A-Za-z0-9_]*)?\$', source[index:])):
            delimiter = tag[0]
            end = source.find(delimiter, index + len(delimiter))
            if end < 0:
                raise ValueError('Unterminated dollar-quoted SQL body')
            index = end + len(delimiter)
            output.append(' BODY ')
        else:
            output.append(source[index])
            index += 1
    return ''.join(output)


def read_migrations(directory: Path) -> list[dict]:
    migrations, versions = [], set()
    for path in sorted(directory.glob('*.sql')):
        match = NAME.fullmatch(path.name)
        if not match:
            raise ValueError(f'Invalid migration filename: {path.name}')
        version = int(match[1])
        if version in versions:
            raise ValueError(f'Duplicate migration version: {version}')
        versions.add(version)
        raw = path.read_bytes()
        source = raw.decode('utf-8-sig')
        visible = top_level_sql(source)
        if re.search(r'^\s*\\', visible, re.MULTILINE):
            raise ValueError(f'psql metacommands are forbidden in {path.name}')
        for statement in visible.split(';'):
            if re.match(r'^\s*(BEGIN|COMMIT|END|ROLLBACK|ABORT|START\s+TRANSACTION|PREPARE\s+TRANSACTION)\b', statement, re.IGNORECASE):
                raise ValueError(f'Transaction control is owned by the runner: {path.name}')
        migrations.append({'version': version, 'name': path.name,
                           'checksum': hashlib.sha256(raw).hexdigest(), 'source': source})
    if not migrations:
        raise ValueError('No migration files found')
    return sorted(migrations, key=lambda item: item['version'])


def build_sql(migrations: list[dict], lock_timeout: int, statement_timeout: int) -> str:
    expected = ',\n'.join(f"({m['version']},{literal(m['name'])},{literal(m['checksum'])})" for m in migrations)
    chunks = [f"""
BEGIN;
SET LOCAL lock_timeout = '{lock_timeout}s';
SET LOCAL statement_timeout = '{statement_timeout}s';
SET LOCAL standard_conforming_strings = on;
SELECT pg_advisory_xact_lock(hashtextextended('sasms:schema-migrations',0));
DO $preflight$
BEGIN
 IF to_regnamespace('sasms') IS NULL THEN
  RAISE EXCEPTION 'Sprint 0 sasms schema is absent; apply baseline first';
 END IF;
END $preflight$;
CREATE SCHEMA IF NOT EXISTS sasms_meta;
REVOKE ALL ON SCHEMA sasms_meta FROM PUBLIC;
CREATE TABLE IF NOT EXISTS sasms_meta.schema_migrations (
 version integer PRIMARY KEY CHECK (version > 0),
 filename text NOT NULL UNIQUE,
 checksum text NOT NULL CHECK (checksum ~ '^[0-9a-f]{{64}}$'),
 applied_at timestamptz NOT NULL DEFAULT now()
);
REVOKE ALL ON sasms_meta.schema_migrations FROM PUBLIC;
DO $verify$
BEGIN
 IF EXISTS (
  SELECT 1 FROM sasms_meta.schema_migrations applied
  LEFT JOIN (VALUES {expected}) AS source(version,filename,checksum) USING(version)
  WHERE source.version IS NULL OR source.filename <> applied.filename OR source.checksum <> applied.checksum
 ) THEN RAISE EXCEPTION 'Migration checksum/history mismatch: changed/missing applied migration; restore original files, then add a new version'
    USING ERRCODE='23514';
 END IF;
 IF EXISTS (
  SELECT 1 FROM (VALUES {expected}) AS source(version,filename,checksum)
  WHERE NOT EXISTS (SELECT 1 FROM sasms_meta.schema_migrations a WHERE a.version=source.version)
   AND source.version < coalesce((SELECT max(version) FROM sasms_meta.schema_migrations),0)
 ) THEN RAISE EXCEPTION 'Out-of-order migration is forbidden' USING ERRCODE='23514'; END IF;
END $verify$;
"""]
    for migration in migrations:
        # Delimiter depends on content; verify it cannot close the quoted payload.
        delimiter = '$payload_' + migration['checksum'][:20] + '$'
        if delimiter in migration['source']:
            raise ValueError('Migration contains its payload delimiter')
        chunks.append(f"""
DO $apply_migration$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM sasms_meta.schema_migrations WHERE version={migration['version']}) THEN
  EXECUTE {delimiter}{migration['source']}{delimiter};
  INSERT INTO sasms_meta.schema_migrations(version,filename,checksum)
   VALUES ({migration['version']},{literal(migration['name'])},{literal(migration['checksum'])});
  RAISE NOTICE 'APPLIED %', {literal(migration['name'])};
 ELSE
  RAISE NOTICE 'UNCHANGED %', {literal(migration['name'])};
 END IF;
END $apply_migration$;
""")
    chunks += ['COMMIT;', 'SELECT version,filename,checksum FROM sasms_meta.schema_migrations ORDER BY version;']
    return '\n'.join(chunks)


def redact(value: str, dsn: str | None) -> str:
    if dsn:
        value = value.replace(dsn, '[REDACTED_DSN]')
    value = re.sub(r"(?i)(password\s*=\s*)('[^']*'|\S+)", r'\1[REDACTED]', value)
    return re.sub(r'(postgres(?:ql)?://[^:\s/@]+:)[^@\s]+@', r'\1[REDACTED]@', value)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--psql', default='psql')
    parser.add_argument('--dsn', default=os.environ.get('DATABASE_URL'))
    parser.add_argument('--migrations-dir', type=Path, default=ROOT/'database/migrations')
    parser.add_argument('--status', action='store_true')
    parser.add_argument('--lock-timeout-seconds', type=int, default=10)
    parser.add_argument('--statement-timeout-seconds', type=int, default=120)
    args = parser.parse_args()
    if args.lock_timeout_seconds < 1 or args.statement_timeout_seconds < 1:
        parser.error('Timeouts must be positive seconds')
    command = [args.psql, '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=verbose', '-P', 'pager=off']
    if args.dsn:
        command += ['--dbname', args.dsn]
    env = dict(os.environ, PGCLIENTENCODING='UTF8')
    try:
        migrations = read_migrations(args.migrations_dir)
        if args.status:
            presence = subprocess.run(command + ['-At', '-c', "SELECT to_regclass('sasms_meta.schema_migrations') IS NOT NULL"],
                                      capture_output=True, text=True, encoding='utf-8', env=env, timeout=20)
            if presence.returncode:
                raise RuntimeError(redact(presence.stderr, args.dsn))
            if presence.stdout.strip() != 't':
                print('No versioned migrations have been applied.')
                return 0
            sql = 'SELECT version,filename,checksum,applied_at FROM sasms_meta.schema_migrations ORDER BY version;'
        else:
            sql = build_sql(migrations, args.lock_timeout_seconds, args.statement_timeout_seconds)
        result = subprocess.run(command + ['-f', '-'], input=sql, capture_output=True, text=True,
                                encoding='utf-8', env=env, timeout=args.statement_timeout_seconds * (len(migrations)+2))
        for stream, content in [(sys.stdout, result.stdout), (sys.stderr, result.stderr)]:
            if content:
                print(redact(content, args.dsn), end='', file=stream)
        return result.returncode
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(redact(str(error), args.dsn), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
