#!/usr/bin/env python3
"""Run the existing verified backup/restore drill on an isolated V008 source.

Retains the private restore database and archive for review. No databases are
dropped. This is a local logical-restore rehearsal, not a cloud RTO/PITR claim.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import sys
import time

from validate_environment import Drill, ROOT


class Sprint4Drill(Drill):
    def bootstrap(self):
        server = self.json_result(self.psql('Sprint4 source PostgreSQL version',
            "SELECT json_build_object('version',version(),'version_num',current_setting('server_version_num')::int,'database',current_database());"))
        if not 160000 <= server['version_num'] < 170000:
            raise RuntimeError('PostgreSQL16 required')
        if not server['database'].startswith('sasms_s4_'):
            raise RuntimeError('Restore rehearsal requires an isolated sasms_s4_* source database')
        shape = self.json_result(self.psql('Sprint4 source schema preflight',
            "SELECT json_build_object('tables',(SELECT count(*) FROM pg_tables WHERE schemaname='sasms'),"
            "'migrations',(SELECT count(*) FROM sasms_meta.schema_migrations),'version',"
            "(SELECT max(version) FROM sasms_meta.schema_migrations));"))
        if shape != {'tables':41,'migrations':8,'version':8}:
            raise RuntimeError('Expected 41 tables and V001-V008; validate Sprint4 first')
        self.summary['postgresql'] = server
        self.summary['source_shape'] = shape

    def ops(self, label, arguments, *, fail=False):
        started = time.monotonic()
        result = super().ops(label, arguments, fail=fail)
        self.summary.setdefault('operation_seconds',{})[label] = round(time.monotonic()-started,3)
        return result

    def validate(self):
        source = Path(__file__)
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        super().validate()
        if hashlib.sha256(source.read_bytes()).hexdigest() != digest:
            raise RuntimeError('Sprint4 drill source changed while running')
        self.summary['source_sha256'][source.relative_to(ROOT).as_posix()] = digest
        self.summary['scope'] = 'Local isolated V008 logical backup/restore, exact data/catalog/ACL comparison and refusal probes; no cloud/PITR/production RTO claim'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dsn',default=os.environ.get('DATABASE_URL'),required=not os.environ.get('DATABASE_URL'))
    parser.add_argument('--maintenance-dsn',required=True)
    parser.add_argument('--psql',default='psql')
    parser.add_argument('--pg-dump',default='pg_dump')
    parser.add_argument('--pg-restore',default='pg_restore')
    parser.add_argument('--evidence-dir',type=Path,default=ROOT/'Sprint 4/DB/evidence/restore')
    parser.add_argument('--work-dir',type=Path,default=ROOT/'database/.local/sprint4-backups')
    args = parser.parse_args()
    args.apply = False
    drill = Sprint4Drill(args)
    try:
        drill.validate()
    except Exception as error:
        drill.summary['status']='failed'
        drill.summary['error']=drill.redact(str(error))
        drill.log('FAILED: '+str(error))
        print(drill.redact(str(error)),file=sys.stderr)
        return 1
    finally:
        drill.save()
    print(f'Sprint4 restore drill passed: {drill.summary["check_count"]} checks, data/schema/ACL identical.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
