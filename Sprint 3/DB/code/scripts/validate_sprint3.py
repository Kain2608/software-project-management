#!/usr/bin/env python3
"""Verify Sprint 3 on an isolated PG16 database; stdlib + psql, never production.

--apply bootstraps an empty database with Sprint 0/1/2 regression first.
Owner fixtures simulate PAID sales for concurrency; this is not HTTP checkout evidence.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
import uuid

from validate_db import Validator, ROOT, STORE, WAREHOUSE, PRODUCT, RECEIPT_ITEM


class Sprint3Validator(Validator):
    def command_run(self, label, command):
        result = subprocess.run(command, cwd=ROOT, env=self.env, capture_output=True,
                                text=True, encoding='utf-8', errors='replace', timeout=600)
        self.log(f'\n=== {label} ===\n{result.stdout}{result.stderr}')
        if result.returncode:
            raise RuntimeError(label+' failed; see validation.log')

    def upgrade(self):
        output, _ = self.run('PostgreSQL version', sql="SELECT current_setting('server_version_num')::int")
        if not 160000 <= int(output) < 170000:
            raise RuntimeError('PostgreSQL 16 required')
        if self.args.apply:
            command = [sys.executable, str(ROOT/'scripts/validate_sprint2.py'), '--apply',
                       '--psql', self.args.psql, '--evidence-dir', str(self.args.evidence_dir/'sprint2-regression')]
            if self.args.dsn:
                command += ['--dsn', self.args.dsn]
            self.command_run('Sprint 0 / 1 / 2 regression', command)
            prior = json.loads((self.args.evidence_dir/'sprint2-regression/summary.json').read_text(encoding='utf-8'))
            if prior['status'] != 'passed':
                raise RuntimeError('Prior regression did not pass')
            self.summary['sprint2_regression'] = {'status': prior['status'], 'assertions': prior['assertions'],
                'identifier_lease_assertions': prior['identifier_lease_assertions'], 'concurrency': prior['concurrency']}
        command = [sys.executable, str(ROOT/'scripts/migrate_db.py'), '--psql', self.args.psql, '--target-version', '7']
        if self.args.dsn:
            command += ['--dsn', self.args.dsn]
        self.command_run('Apply Sprint 3 migrations', command)
        before = self.snapshot()
        self.command_run('Verify migration checksum and reapply noop', command)
        if self.snapshot() != before:
            raise RuntimeError('Migration reapply changed business rows')
        self.run('Provision dedicated SMS worker group', file=ROOT/'database/sprint3/runtime-roles.sql')
        output, _ = self.run('Current schema version and count', sql="SELECT json_build_object('version',"
            "(SELECT max(version) FROM sasms_meta.schema_migrations),'tables',"
            "(SELECT count(*) FROM pg_tables WHERE schemaname='sasms'))")
        self.summary['schema'] = json.loads(output)
        if self.summary['schema'] != {'version': 7, 'tables': 41}:
            raise RuntimeError('Expected V007 and 41 business tables')

    def races(self):
        actor, tech, customer = [str(uuid.uuid4()) for _ in range(3)]
        orders = [str(uuid.uuid4()) for _ in range(2)]
        items = [str(uuid.uuid4()) for _ in range(2)]
        devices = [str(uuid.uuid4()) for _ in range(2)]
        order_list = ','.join("'"+v+"'" for v in orders)
        device_list = ','.join("'"+v+"'" for v in devices)
        setup = ["BEGIN;", f"INSERT INTO sasms.users(id,email,full_name,is_active) VALUES "
                 f"('{actor}','{actor}@example.invalid','Synthetic dispatcher',true),"
                 f"('{tech}','{tech}@example.invalid','Synthetic technician',true);",
                 f"INSERT INTO sasms.user_roles(user_id,role_code,store_id) VALUES"
                 f"('{actor}','DISPATCHER','{STORE}'),('{actor}','CUSTOMER_SERVICE','{STORE}'),"
                 f"('{tech}','TECHNICIAN','{STORE}');",
                 f"INSERT INTO sasms.customers(id,code,full_name) VALUES('{customer}','S3-{customer}','Synthetic customer');"]
        for order, item, device in zip(orders, items, devices):
            setup += [f"INSERT INTO sasms.devices(id,product_id,receipt_item_id,warehouse_id,serial) "
                      f"VALUES('{device}','{PRODUCT}','{RECEIPT_ITEM}','{WAREHOUSE}','S3-{device}');",
                      f"INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,customer_id,channel,created_by) "
                      f"VALUES('{order}','S3-{order}','{STORE}','{WAREHOUSE}','{customer}','ONLINE','{actor}');",
                      f"INSERT INTO sasms.sales_order_items(id,order_id,product_id,quantity,unit_price) "
                      f"VALUES('{item}','{order}','{PRODUCT}',1,100);",
                      f"INSERT INTO sasms.order_devices(order_item_id,order_id,product_id,device_id) "
                      f"VALUES('{item}','{order}','{PRODUCT}','{device}');",
                      f"UPDATE sasms.devices SET status='SOLD',sold_order_id='{order}',sold_at=clock_timestamp() WHERE id='{device}';",
                      f"UPDATE sasms.sales_orders SET status='PAID',paid_at=clock_timestamp(),total_amount=100 WHERE id='{order}';"]
        setup += ['COMMIT;']
        try:
            self.run('Commit exact synthetic concurrency fixtures', sql='\n'.join(setup))
            barrier = threading.Barrier(2)

            def schedule(order):
                barrier.wait(timeout=10)
                return self.invoke(sql=f"BEGIN; SET LOCAL ROLE sasms_app; SET LOCAL lock_timeout='10s'; "
                    f"SELECT pg_backend_pid(); SELECT sasms.schedule_delivery('{order}','{tech}',"
                    f"date_trunc('day',clock_timestamp())+interval '2 days 9 hours',"
                    f"date_trunc('day',clock_timestamp())+interval '2 days 10 hours','Synthetic address','{actor}');"
                    "SELECT pg_sleep(0.5); COMMIT;", timeout=20)

            with ThreadPoolExecutor(max_workers=2) as pool:
                results = list(pool.map(schedule, orders))
            for index, result in enumerate(results):
                self.log(f'\n=== Schedule connection {index+1}, exit {result.returncode} ===\n{result.stdout}{result.stderr}')
            winners = [index for index, result in enumerate(results) if result.returncode == 0]
            if len(winners) != 1 or not all(r.returncode == 0 or '23P01' in r.stderr for r in results):
                raise RuntimeError('Concurrent technician overlap did not yield one success / one 23P01')
            pids = [next(int(line) for line in r.stdout.splitlines() if line.strip().isdigit()) for r in results]
            if len(set(pids)) != 2:
                raise RuntimeError('Concurrency connections were not independent')
            winner = winners[0]
            output, _ = self.run('Complete winning delivery', sql=f"BEGIN; SET LOCAL ROLE sasms_app; "
                f"SELECT sasms.record_delivery_result(d.id,'COMPLETED','synthetic-test-evidence','{tech}') "
                f"FROM sasms.deliveries d WHERE d.order_id='{orders[winner]}'; COMMIT;")
            barrier = threading.Barrier(2)

            def activate(_):
                barrier.wait(timeout=10)
                return self.invoke(sql=f"BEGIN; SET LOCAL ROLE sasms_app; SELECT sasms.activate_warranty("
                    f"'{devices[winner]}','{actor}'); SELECT pg_sleep(0.3); COMMIT;")

            with ThreadPoolExecutor(max_workers=2) as pool:
                activations = list(pool.map(activate, range(2)))
            warranty_ids = []
            for index, result in enumerate(activations):
                self.log(f'\n=== Warranty connection {index+1}, exit {result.returncode} ===\n{result.stdout}{result.stderr}')
                if result.returncode:
                    raise RuntimeError('Concurrent warranty activation failed')
                warranty_ids.append(next(str(uuid.UUID(line.strip())) for line in result.stdout.splitlines() if re.fullmatch(r'[0-9a-f-]{36}', line.strip())))
            if len(set(warranty_ids)) != 1:
                raise RuntimeError('Concurrent activation returned different warranties')
            output, _ = self.run('Unique concurrent warranty and SMS event', sql=f"SELECT json_build_object("
                f"'warranties',(SELECT count(*) FROM sasms.warranties WHERE device_id='{devices[winner]}'),"
                f"'events',(SELECT count(*) FROM sasms.outbox_events WHERE aggregate_id='{warranty_ids[0]}' AND event_type='WarrantyActivated'))")
            if json.loads(output) != {'warranties': 1, 'events': 1}:
                raise RuntimeError('Duplicate warranty or SMS event after concurrent activation')
            output, _ = self.run('Warranty event reference', sql=f"SELECT id FROM sasms.outbox_events WHERE aggregate_id='{warranty_ids[0]}' AND event_type='WarrantyActivated'")
            event = str(uuid.UUID(output))
            barrier = threading.Barrier(2)

            def claim(_):
                barrier.wait(timeout=10)
                return self.invoke(sql=f"BEGIN; SET LOCAL ROLE sasms_sms_worker; SELECT event_id FROM "
                    f"sasms.claim_sms_notification('{event}'); SELECT pg_sleep(0.5); COMMIT;")

            with ThreadPoolExecutor(max_workers=2) as pool:
                claims = list(pool.map(claim, range(2)))
            if any(r.returncode for r in claims) or sum(event in r.stdout for r in claims) != 1:
                raise RuntimeError('Concurrent workers claimed the same event twice')
            self.run('Mock transport result commits separately', sql=f"BEGIN; SET LOCAL ROLE sasms_sms_worker; "
                f"SELECT sasms.finish_sms_notification('{event}',1,'MOCK_ACCEPTED','mock-{event}',NULL); COMMIT;")
            self.summary['concurrency'] = {'schedule_backend_pids': pids, 'schedule_successes': 1,
                'schedule_23P01_rejections': 1, 'warranty_connections': 2, 'warranty_rows': 1,
                'warranty_sms_events': 1, 'worker_connections': 2, 'event_claims': 1,
                'transport_result': 'MOCK_ACCEPTED', 'external_sms_sent': False}
        finally:
            self.run('Cleanup only exact committed fixture UUIDs', sql=f"""
BEGIN;
DELETE FROM sasms.outbox_events WHERE aggregate_id IN (
 SELECT id FROM sasms.warranties WHERE device_id IN ({device_list})
 UNION ALL SELECT id FROM sasms.deliveries WHERE order_id IN ({order_list}));
DELETE FROM sasms.warranties WHERE device_id IN ({device_list});
DELETE FROM sasms.technician_assignments WHERE delivery_id IN (SELECT id FROM sasms.deliveries WHERE order_id IN ({order_list}));
DELETE FROM sasms.delivery_items WHERE order_id IN ({order_list});
DELETE FROM sasms.deliveries WHERE order_id IN ({order_list});
UPDATE sasms.sales_orders SET status='DRAFT',paid_at=NULL WHERE id IN ({order_list});
DELETE FROM sasms.order_devices WHERE order_id IN ({order_list});
DELETE FROM sasms.sales_order_items WHERE order_id IN ({order_list});
DELETE FROM sasms.devices WHERE id IN ({device_list});
DELETE FROM sasms.sales_orders WHERE id IN ({order_list});
DELETE FROM sasms.customers WHERE id='{customer}';
DELETE FROM sasms.audit_logs WHERE actor_id IN ('{actor}','{tech}');
DELETE FROM sasms.user_roles WHERE user_id IN ('{actor}','{tech}');
DELETE FROM sasms.users WHERE id IN ('{actor}','{tech}');
COMMIT;
""")

    def validate(self):
        sources = [Path('scripts/validate_sprint3.py'), Path('scripts/validate_sprint2.py'),
                   Path('scripts/migrate_db.py'), Path('database/sprint3/runtime-roles.sql')]
        sources += [p.relative_to(ROOT) for p in sorted((ROOT/'database/migrations').glob('*.sql'))]
        sources += [p.relative_to(ROOT) for p in sorted((ROOT/'database/tests/sprint3').glob('*.sql'))]
        self.summary['source_sha256'] = {p.as_posix(): hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in sources}
        self.summary['scope'] = 'Isolated PostgreSQL 16, SQL behavior/ACL/races; no real SMS, HTTP checkout, browser, Staging or approval claim'
        self.upgrade()
        before = self.snapshot()
        _, notices = self.run('Logistics warranty and SMS assertions', file=ROOT/'database/tests/sprint3/logistics_and_warranty.sql')
        self.summary['assertions'] = len(re.findall(r'PASS: ', notices))
        if self.summary['assertions'] < 30 or self.snapshot() != before:
            raise RuntimeError('Assertions incomplete or fixtures leaked')
        self.races()
        if self.snapshot() != before:
            raise RuntimeError('Concurrent fixtures changed existing data')
        self.summary['fixtures_cleaned'] = True
        if any(hashlib.sha256((ROOT/p).read_bytes()).hexdigest() != h for p,h in self.summary['source_sha256'].items()):
            raise RuntimeError('Sources changed during validation; rerun')
        self.summary['status'] = 'passed'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--psql', default='psql')
    parser.add_argument('--dsn', default=os.environ.get('DATABASE_URL'))
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--evidence-dir', type=Path, default=ROOT/'database/evidence/sprint3')
    args = parser.parse_args()
    args.evidence_dir = args.evidence_dir.resolve()
    validator = Sprint3Validator(args)
    try:
        validator.validate()
    except Exception as error:
        validator.summary['status'] = 'failed'
        validator.summary['error'] = validator.clean(str(error))
        validator.log('FAILED: '+str(error))
        print(validator.clean(str(error)), file=sys.stderr)
        return 1
    finally:
        validator.save()
    print(f"Sprint 3 passed: {validator.summary['assertions']} assertions, three concurrency cases")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
