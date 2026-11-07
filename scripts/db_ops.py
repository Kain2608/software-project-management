#!/usr/bin/env python3
"""D22: PostgreSQL 16 logical backup and restore into a NEW, private database.

Uses Python stdlib and PostgreSQL clients. Never drops a database, overwrites an
archive, stores a DSN in the manifest or opens PUBLIC access after restoration.
Database roles referenced by the archive must already exist on the target server.
This is a logical backup tool; it does not implement WAL archiving or PITR.
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
from urllib.parse import quote, unquote, urlsplit, urlunsplit


def redact(value: str, *dsns: str | None) -> str:
    for dsn in dsns:
        if dsn:
            value = value.replace(dsn, '[REDACTED_DSN]')
    value = re.sub(r"(?i)(password\s*=\s*)('(?:\\.|[^'\\])*'|\S+)", r'\1[REDACTED]', value)
    return re.sub(r'(postgres(?:ql)?://[^:\s/@]+:)[^@\s]+@', r'\1[REDACTED]@', value)


def conn_literal(value: str) -> str:
    return "'" + value.replace('\\', '\\\\').replace("'", "\\'") + "'"


def target_dsn(dsn: str | None, database: str) -> str:
    """Replace dbname without losing libpq quoting, URI options or authentication."""
    if not dsn:
        return 'dbname=' + conn_literal(database)
    if dsn.startswith(('postgres://', 'postgresql://')):
        parsed = urlsplit(dsn)
        # Preserve URI query bytes: libpq treats '+' literally, unlike HTML form
        # decoding. Re-encoding with parse_qsl/urlencode could change passwords.
        query = '&'.join(part for part in parsed.query.split('&')
                         if part and unquote(part.partition('=')[0]) != 'dbname')
        return urlunsplit((parsed.scheme, parsed.netloc, '/' + quote(database, safe=''), query, ''))
    # libpq keyword syntax: quoted/unquoted values both allow backslash escapes.
    options, index = [], 0
    while index < len(dsn):
        while index < len(dsn) and dsn[index].isspace():
            index += 1
        if index == len(dsn):
            break
        start = index
        while index < len(dsn) and not dsn[index].isspace() and dsn[index] != '=':
            index += 1
        key = dsn[start:index]
        while index < len(dsn) and dsn[index].isspace():
            index += 1
        if not key or index >= len(dsn) or dsn[index] != '=':
            # A plain dbname uses the environment for other connection fields.
            if not options and '=' not in dsn and not any(char.isspace() for char in dsn):
                return 'dbname=' + conn_literal(database)
            raise ValueError('Invalid libpq keyword DSN')
        index += 1
        while index < len(dsn) and dsn[index].isspace():
            index += 1
        quoted = index < len(dsn) and dsn[index] == "'"
        if quoted:
            index += 1
        chars, closed = [], not quoted
        while index < len(dsn):
            char = dsn[index]
            if char == '\\':
                index += 1
                if index == len(dsn):
                    raise ValueError('Invalid trailing escape in DSN')
                chars.append(dsn[index])
                index += 1
            elif quoted and char == "'":
                index += 1
                closed = True
                break
            elif not quoted and char.isspace():
                break
            else:
                chars.append(char)
                index += 1
        if not closed:
            raise ValueError('Unterminated quoted DSN value')
        if key != 'dbname':
            options.append((key, ''.join(chars)))
    options.append(('dbname', database))
    return ' '.join(key + '=' + conn_literal(value) for key, value in options)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def run(command: list[str], *, dsns: tuple = (), stdout=None, timeout: int = 600) -> str:
    try:
        result = subprocess.run(command, stdout=stdout if stdout is not None else subprocess.PIPE,
                                stderr=subprocess.PIPE, env=dict(os.environ, PGCLIENTENCODING='UTF8'), timeout=timeout)
    except subprocess.TimeoutExpired as error:
        raise RuntimeError('PostgreSQL operation timed out; inspect the private target if restore had started') from error
    if result.returncode:
        raise RuntimeError(redact(result.stderr.decode('utf-8', errors='replace'), *dsns))
    return result.stdout.decode('utf-8') if result.stdout is not None else ''


def sql(psql: str, dsn: str | None, query: str) -> str:
    command = [psql, '-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-c', query]
    if dsn:
        command += ['--dbname', dsn]
    return run(command, dsns=(dsn,), timeout=30).strip()


def server_info(psql: str, dsn: str | None) -> dict:
    result = json.loads(sql(psql, dsn, "SELECT json_build_object('database',current_database(),"
                            "'version',version(),'version_num',current_setting('server_version_num')::integer)"))
    if not 160000 <= result['version_num'] < 170000:
        raise ValueError('This project backup/restore procedure requires PostgreSQL major version 16')
    return result


def backup(args) -> dict:
    archive = args.output.resolve()
    manifest_path = Path(str(archive) + '.manifest.json')
    if archive.exists() or manifest_path.exists():
        raise ValueError('Backup output or manifest already exists; use a new output filename')
    info = server_info(args.psql, args.dsn)
    client = run([args.pg_dump, '--version']).strip()
    if not re.search(r'\b16\.', client):
        raise ValueError('Use a PostgreSQL 16 pg_dump client')
    archive.parent.mkdir(parents=True, exist_ok=True)
    command = [args.pg_dump, '--format=custom', '--compress=6', '--no-owner']
    if args.dsn:
        command += ['--dbname', args.dsn]
    created = False
    try:
        # O_EXCL prevents concurrent runs from replacing each other's archives.
        fd = os.open(archive, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        created = True
        with os.fdopen(fd, 'wb') as stream:
            run(command, dsns=(args.dsn,), stdout=stream, timeout=args.timeout_seconds)
            stream.flush()
            os.fsync(stream.fileno())
    except Exception:
        if created:
            archive.unlink(missing_ok=True)
        raise
    manifest = {'action': 'backup', 'format': 'custom', 'archive_sha256': sha256(archive),
                'archive_bytes': archive.stat().st_size, 'source_database': info['database'],
                'postgresql': info, 'pg_dump': client,
                'created_at_utc': datetime.now(timezone.utc).isoformat(),
                'limitations': 'Logical full backup; not encrypted by this tool; no WAL/PITR; protect storage and referenced database roles separately.'}
    manifest_fd = os.open(manifest_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(manifest_fd, 'w', encoding='utf-8', newline='\n') as stream:
        json.dump(manifest, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    return manifest


def restore(args) -> dict:
    if not re.fullmatch(r'[a-z][a-z0-9_]{0,62}', args.target_db) or args.target_db in ('postgres', 'template0', 'template1'):
        raise ValueError('Use a NEW non-system target name: 1..63 lowercase ASCII letters, digits or underscores')
    archive = args.input.resolve()
    manifest_path = Path(str(archive) + '.manifest.json')
    if not archive.is_file() or not manifest_path.is_file():
        raise ValueError('Archive and matching .manifest.json are required')
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    digest = sha256(archive)
    if manifest.get('format') != 'custom' or manifest.get('archive_sha256') != digest:
        raise ValueError('Backup checksum/format mismatch; target was not created')
    info = server_info(args.psql, args.maintenance_dsn)
    client = run([args.pg_restore, '--version']).strip()
    if not re.search(r'\b16\.', client):
        raise ValueError('Use a PostgreSQL 16 pg_restore client')
    run([args.pg_restore, '--list', str(archive)], timeout=30)
    exists = sql(args.psql, args.maintenance_dsn,
                 f"SELECT EXISTS(SELECT 1 FROM pg_database WHERE datname='{args.target_db}')")
    if exists == 't':
        raise ValueError('Target database already exists; restore never overwrites an existing database')
    # Name is validated ASCII and quoted as an SQL identifier, never shell text.
    # Keep connections disabled during provisioning: REVOKE after a normal CREATE
    # would leave a short PUBLIC CONNECT window and could retain unwanted sessions.
    sql(args.psql, args.maintenance_dsn,
        f'CREATE DATABASE "{args.target_db}" WITH TEMPLATE template0 ENCODING \'UTF8\' ALLOW_CONNECTIONS false')
    sql(args.psql, args.maintenance_dsn, f'REVOKE ALL ON DATABASE "{args.target_db}" FROM PUBLIC')
    sql(args.psql, args.maintenance_dsn, f'ALTER DATABASE "{args.target_db}" ALLOW_CONNECTIONS true')
    destination = target_dsn(args.maintenance_dsn, args.target_db)
    try:
        run([args.pg_restore, '--exit-on-error', '--single-transaction', '--no-owner', '--dbname', destination, str(archive)],
            dsns=(args.maintenance_dsn, destination), timeout=args.timeout_seconds)
        sql(args.psql, args.maintenance_dsn, f'REVOKE ALL ON DATABASE "{args.target_db}" FROM PUBLIC')
    except Exception as error:
        raise RuntimeError(f'Restore failed; NEW database {args.target_db} is retained for inspection (no automatic DROP). '
                           + redact(str(error), args.maintenance_dsn, destination)) from error
    # No CONNECT grant is issued. Operators explicitly review access before use.
    public_connect = sql(args.psql, args.maintenance_dsn,
        "SELECT EXISTS(SELECT 1 FROM pg_database d CROSS JOIN LATERAL "
        "aclexplode(coalesce(d.datacl,acldefault('d',d.datdba))) a "
        f"WHERE d.datname='{args.target_db}' AND a.grantee=0 AND a.privilege_type='CONNECT')")
    if public_connect != 'f':
        raise RuntimeError('Unexpected PUBLIC CONNECT grant after restore; owner must inspect target')
    return {'action': 'restore', 'format': 'custom', 'target_database': args.target_db,
            'archive_sha256': digest, 'archive_bytes': archive.stat().st_size,
            'public_connect_disabled': True, 'pg_restore': client,
            'finished_at_utc': datetime.now(timezone.utc).isoformat()}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    dump = sub.add_parser('backup', help='Create a NEW custom archive and SHA256 manifest')
    dump.add_argument('--dsn', default=os.environ.get('DATABASE_URL'))
    dump.add_argument('--output', type=Path, required=True)
    dump.add_argument('--pg-dump', default='pg_dump')
    load = sub.add_parser('restore', help='Restore a verified archive into a NEW private database')
    load.add_argument('--maintenance-dsn', default=os.environ.get('DATABASE_URL'))
    load.add_argument('--target-db', required=True)
    load.add_argument('--input', type=Path, required=True)
    load.add_argument('--pg-restore', default='pg_restore')
    for command in (dump, load):
        command.add_argument('--psql', default='psql')
        command.add_argument('--timeout-seconds', type=int, default=600)
    args = parser.parse_args()
    if args.timeout_seconds < 1:
        parser.error('Timeout must be positive')
    try:
        print(json.dumps(backup(args) if args.action == 'backup' else restore(args), ensure_ascii=False, indent=2))
        return 0
    except (OSError, ValueError, RuntimeError) as error:
        print(redact(str(error), getattr(args, 'dsn', None), getattr(args, 'maintenance_dsn', None)), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
