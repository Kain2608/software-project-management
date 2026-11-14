#!/usr/bin/env python3
"""Validate Sprint 2 in an isolated PostgreSQL 16 database using stdlib and psql.

An owner connection is required for fixtures, roles and synchronization inspection.
Business operations run as sasms_app with active, store-scoped CASHIER actors.
--apply bootstraps a NEW database through the frozen Sprint 0 and Sprint 1 checks.
Cleanup of committed synthetic captured payments temporarily disables only
guard_payments and restores it in the same owner-only cleanup transaction.
Never run this fixture/provisioning tool against a production database.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import uuid

from validate_db import Validator as BaseValidator

ROOT = Path(__file__).resolve().parents[1]
STORE = '00000000-0000-4000-8000-000000000010'
WAREHOUSE = '00000000-0000-4000-8000-000000000011'
PRODUCT = '00000000-0000-4000-8000-000000000030'
RECEIPT_ITEM = '00000000-0000-4000-8000-000000000061'


class Sprint2Validator(BaseValidator):
    def __init__(self, args):
        super().__init__(args)
        self.summary['scope'] = ('isolated PostgreSQL behavior, ACL and two POS connections; '
                                 'no API, browser, remote Staging deployment or health claim')
        self.summary['concurrency'] = []

    def prior_sprints(self):
        command = [sys.executable, str(ROOT/'scripts/validate_sprint1.py'),
                   '--psql', self.args.psql, '--evidence-dir',
                   str(self.args.evidence_dir/'sprint1-regression')]
        if self.args.dsn:
            command += ['--dsn', self.args.dsn]
        if self.args.apply:
            command.append('--apply')
        result = subprocess.run(command, cwd=ROOT, env=self.env, capture_output=True,
                                text=True, encoding='utf-8', errors='replace', timeout=300)
        self.log(f'\n=== Sprint 0 / 1 regression ===\n{result.stdout}{result.stderr}')
        if result.returncode:
            raise RuntimeError('Sprint 0 / 1 regression failed; see sprint1-regression evidence')
        prior = json.loads((self.args.evidence_dir/'sprint1-regression/summary.json').read_text(encoding='utf-8'))
        if prior['status'] != 'passed':
            raise RuntimeError('Sprint 1 did not record a passed result')
        self.summary['prior_sprints'] = {
            'sprint1_status': prior['status'], 'sprint1_assertions': prior['assertions'],
            'sprint0_regression': prior['baseline_regression'],
            'migration_safety': prior['migration_safety'],
        }

    def failure_detection(self):
        # Exercise the exact -f / ON_ERROR_STOP command used for real SQL files.
        # A failing statement must return nonzero and prevent subsequent SQL.
        with tempfile.TemporaryDirectory(prefix='sasms-s2-fail-probe-') as directory:
            path = Path(directory)/'intentional_failure.sql'
            path.write_text("SELECT 1;\nDO $$ BEGIN RAISE EXCEPTION 'S2_EXPECTED_FAILURE' "
                            "USING ERRCODE='P0001'; END $$;\nSELECT 'S2_MUST_NOT_RUN';\n", encoding='utf-8')
            result = self.invoke(file=path)
        self.log(f'\n=== intentional SQL failure detection ===\n{result.stdout}{result.stderr}')
        if result.returncode == 0 or 'S2_EXPECTED_FAILURE' not in result.stderr or 'S2_MUST_NOT_RUN' in result.stdout:
            raise RuntimeError('psql SQL failure was not stopped and propagated correctly')
        # The wrapper used by every migration/assertion must propagate that exit.
        propagated = False
        try:
            self.run('intentional assertion failure detection', sql=(
                "DO $$ BEGIN RAISE EXCEPTION 'S2_EXPECTED_ASSERTION_FAILURE'; END $$;"))
        except RuntimeError:
            propagated = True
        if not propagated:
            raise RuntimeError('Validation wrapper swallowed an SQL assertion failure')
        self.summary['ci_failure_detection'] = {
            'sql_file_nonzero_exit': result.returncode,
            'later_sql_not_executed': True, 'assertion_failure_propagated': True,
        }

    def stock(self):
        output, _ = self.run('committed realtime stock', sql=f"""
SELECT json_build_array(available_stock,reserved_stock,sold_stock)
FROM sasms.v_realtime_inventory WHERE warehouse_id='{WAREHOUSE}' AND product_id='{PRODUCT}';
""")
        return json.loads(output)

    def expect_conflict(self, label, result):
        self.log(f'\n=== {label}: exit {result.returncode} ===\n{result.stdout}{result.stderr}')
        if result.returncode == 0 or not re.search(r'\b55P03\b', result.stderr):
            raise RuntimeError(f'{label} did not reject contention with SQLSTATE 55P03')

    def wait_for(self, label, query, process, timeout=12):
        deadline = time.monotonic()+timeout
        while time.monotonic() < deadline:
            if process.poll() is not None:
                stdout, stderr = process.communicate()
                self.log(f'\n=== {label} exited early ===\n{stdout}{stderr}')
                raise RuntimeError(f'{label} exited before synchronization')
            result = self.invoke(sql=query, timeout=5)
            if result.returncode:
                self.log(result.stdout+result.stderr)
                raise RuntimeError(f'{label} synchronization inspection failed')
            value = result.stdout.strip()
            if value:
                return int(value)
            time.sleep(0.05)
        raise RuntimeError(f'Timed out waiting for {label}')

    def start(self, sql=None, application_name=None):
        command = self.command+(['-c', sql] if sql else [])
        env = dict(self.env, PGAPPNAME=application_name)
        return subprocess.Popen(command, cwd=ROOT, env=env, stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                text=True, encoding='utf-8', errors='replace')

    def concurrent_pos(self, outcome):
        suffix = uuid.uuid4().hex.upper()
        actors = [str(uuid.uuid4()), str(uuid.uuid4())]
        orders = [str(uuid.uuid4()), str(uuid.uuid4())]
        items = [str(uuid.uuid4()), str(uuid.uuid4())]
        device = str(uuid.uuid4())
        serial = 'S2-RACE-'+suffix
        gate_key = uuid.uuid4().int % (2**62)
        gate = first = None
        order_list = ','.join(f"'{value}'" for value in orders)
        actor_list = ','.join(f"'{value}'" for value in actors)
        def selection(index):
            return (f"SELECT sasms.find_and_lock_device_for_pos('{STORE}','{orders[index]}',"
                    f"'{items[index]}','{serial}','{actors[index]}');")
        def app_transaction(statement):
            return "BEGIN; SET LOCAL statement_timeout='15s'; SET LOCAL ROLE sasms_app; "+statement+" COMMIT;"
        try:
            statements = ['BEGIN;']
            for index in range(2):
                statements += [
                    f"INSERT INTO sasms.users(id,email,full_name,is_active) VALUES('{actors[index]}','s2-race-{suffix.lower()}-{index}@example.invalid','Synthetic POS cashier',true);",
                    f"INSERT INTO sasms.user_roles(user_id,role_code,store_id) VALUES('{actors[index]}','CASHIER','{STORE}');",
                    f"INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,channel,created_by) VALUES('{orders[index]}','S2-RACE-{suffix}-{index}','{STORE}','{WAREHOUSE}','POS','{actors[index]}');",
                    f"INSERT INTO sasms.sales_order_items(id,order_id,product_id,quantity,unit_price) VALUES('{items[index]}','{orders[index]}','{PRODUCT}',1,1000);",
                ]
            statements += [f"INSERT INTO sasms.devices(id,product_id,receipt_item_id,warehouse_id,serial) VALUES('{device}','{PRODUCT}','{RECEIPT_ITEM}','{WAREHOUSE}','{serial}');",'COMMIT;']
            self.run(outcome+' independent POS fixtures', sql='\n'.join(statements))
            before_stock = self.stock()
            gate_name, first_name = 's2-gate-'+suffix, 's2-pos1-'+suffix
            gate = self.start(application_name=gate_name)
            gate.stdin.write(f'SELECT pg_advisory_lock({gate_key});\n')
            gate.stdin.flush()
            self.wait_for('owner advisory gate', f"""
SELECT a.pid FROM pg_stat_activity a JOIN pg_locks l ON l.pid=a.pid
WHERE a.application_name='{gate_name}' AND l.locktype='advisory' AND l.granted;
""", gate)
            # POS1 reaches the advisory barrier only AFTER locking the real device.
            # It cannot COMMIT/ROLLBACK until POS2 has tried the same Serial.
            first = self.start(sql=(
                "BEGIN; SET LOCAL statement_timeout='20s'; SET LOCAL ROLE sasms_app; "
                "SELECT json_build_object('session_pid',pg_backend_pid()); "+selection(0)+
                f" SELECT pg_advisory_xact_lock({gate_key}); {outcome.upper()};"), application_name=first_name)
            first_pid = self.wait_for('POS1 holding device at advisory barrier', f"""
SELECT pid FROM pg_stat_activity WHERE application_name='{first_name}'
AND state='active' AND wait_event_type='Lock' AND wait_event='advisory';
""", first)
            loser = self.invoke(sql=app_transaction(
                "SELECT json_build_object('session_pid',pg_backend_pid()); "+selection(1)), timeout=10)
            self.expect_conflict(outcome+' overlapping POS2', loser)
            second_pid = json.loads(next(line for line in loser.stdout.splitlines() if line.startswith('{')))['session_pid']
            if first_pid == second_pid:
                raise RuntimeError('Contention test did not use distinct PostgreSQL connections')
            if self.stock() != before_stock:
                raise RuntimeError('Uncommitted draft allocation changed physical stock')
            stdout, stderr = gate.communicate(input=f'SELECT pg_advisory_unlock({gate_key});\n\\q\n', timeout=10)
            self.log(f'\n=== release owner gate ===\n{stdout}{stderr}')
            if gate.returncode:
                raise RuntimeError('Could not release the POS synchronization gate')
            stdout, stderr = first.communicate(timeout=10)
            self.log(f'\n=== POS1 {outcome}: exit {first.returncode} ===\n{stdout}{stderr}')
            if first.returncode or device not in stdout:
                raise RuntimeError(f'POS1 did not lock and {outcome} the fixture device')
            output, _ = self.run(outcome+' initial allocation count', sql=f"SELECT count(*) FROM sasms.order_devices WHERE device_id='{device}' AND active")
            if int(output) != (1 if outcome == 'commit' else 0):
                raise RuntimeError('Initial committed allocation count does not match transaction outcome')
            if outcome == 'commit':
                retry = self.invoke(sql=app_transaction(selection(1)))
                self.expect_conflict('POS2 after POS1 commit', retry)
                self.run('authorized POS1 release', sql=app_transaction(
                    f"SELECT sasms.release_device_lock('{orders[0]}','{device}','{actors[0]}');"))
            self.run('POS2 retry after '+('release' if outcome == 'commit' else 'rollback'),
                     sql=app_transaction(selection(1)))
            output, _ = self.run('exactly one committed active draft owner', sql=f"""
SELECT json_build_object(
 'all_active',(SELECT count(*) FROM sasms.order_devices WHERE device_id='{device}' AND active),
 'winner_active',(SELECT count(*) FROM sasms.order_devices WHERE device_id='{device}' AND order_id='{orders[1]}' AND active));
""")
            if json.loads(output) != {'all_active': 1, 'winner_active': 1} or self.stock() != before_stock:
                raise RuntimeError('Retry did not leave one draft allocation with unchanged physical stock')
            self.run('winning POS2 reserve commit', sql=app_transaction(f"SELECT sasms.reserve_order('{orders[1]}','{actors[1]}');"))
            reserved_stock = self.stock()
            expected_reserved = [before_stock[0]-1, before_stock[1]+1, before_stock[2]]
            if reserved_stock != expected_reserved:
                raise RuntimeError(f'Reserve stock delta differs: {reserved_stock} vs {expected_reserved}')
            self.run('winning POS2 captured payment and sale commit', sql=app_transaction(f"""
INSERT INTO sasms.payments(order_id,amount,method,status,confirmed_by,captured_at)
VALUES('{orders[1]}',1000,'CASH','CAPTURED','{actors[1]}',now());
SELECT sasms.complete_sale('{orders[1]}','{actors[1]}');
"""))
            sold_stock = self.stock()
            expected_sold = [before_stock[0]-1, before_stock[1], before_stock[2]+1]
            if sold_stock != expected_sold:
                raise RuntimeError(f'Sale stock delta differs: {sold_stock} vs {expected_sold}')
            output, _ = self.run('unique committed checkout side effects', sql=f"""
SELECT json_build_object(
 'sold_device',(SELECT count(*) FROM sasms.devices WHERE id='{device}' AND status='SOLD' AND sold_order_id='{orders[1]}'),
 'paid_order',(SELECT count(*) FROM sasms.sales_orders WHERE id IN({order_list}) AND status='PAID'),
 'reserve_movement',(SELECT count(*) FROM sasms.inventory_movements WHERE device_id='{device}' AND kind='RESERVE'),
 'sale_movement',(SELECT count(*) FROM sasms.inventory_movements WHERE device_id='{device}' AND kind='SALE'),
 'invoice',(SELECT count(*) FROM sasms.invoices WHERE order_id IN({order_list})),
 'checkout_audit',(SELECT count(*) FROM sasms.audit_logs WHERE entity_id IN({order_list}) AND action='sales.checkout'),
 'paid_event',(SELECT count(*) FROM sasms.outbox_events WHERE aggregate_id IN({order_list}) AND event_type='OrderPaid'));
""")
            side_effects = json.loads(output)
            if any(value != 1 for value in side_effects.values()):
                raise RuntimeError(f'Duplicate/missing committed checkout side effects: {side_effects}')
            self.summary['concurrency'].append({
                'case': 'POS1_'+outcome, 'independent_pos_connections': 2,
                'pos_backend_pids': [first_pid, second_pid],
                'synchronization': 'POS1 holds device and waits on owner advisory gate before POS2 starts',
                'overlap_55P03_rejections': 1,
                'post_commit_55P03_rejections': 1 if outcome == 'commit' else 0,
                'retry_after_release_or_rollback': True,
                'draft_stock_unchanged': True,
                'stock_before': before_stock, 'stock_after_reserve': reserved_stock,
                'stock_after_sale': sold_stock, 'committed_side_effects': side_effects,
            })
        finally:
            # Kill unfinished clients first: their connection closure rolls back and releases locks.
            for process in (first, gate):
                if process is not None and process.poll() is None:
                    process.kill()
                    process.communicate(timeout=10)
            self.run(outcome+' exact synthetic UUID cleanup', sql=f"""
BEGIN;
UPDATE sasms.sales_orders SET status='DRAFT',paid_at=NULL WHERE id IN({order_list});
DELETE FROM sasms.invoices WHERE order_id IN({order_list});
-- Isolated owner-only fixture cleanup: captured payments are immutable even
-- for the owner. Disable only their child guard for these exact synthetic
-- UUIDs, and restore it in the SAME transaction. Any failure rolls back the
-- trigger alteration as well as the deletes; no runtime role has this power.
ALTER TABLE sasms.payments DISABLE TRIGGER guard_payments;
DELETE FROM sasms.payments WHERE order_id IN({order_list});
ALTER TABLE sasms.payments ENABLE TRIGGER guard_payments;
DELETE FROM sasms.order_devices WHERE order_id IN({order_list});
DELETE FROM sasms.sales_order_items WHERE order_id IN({order_list});
DELETE FROM sasms.inventory_movements WHERE device_id='{device}';
DELETE FROM sasms.audit_logs WHERE actor_id IN({actor_list});
DELETE FROM sasms.outbox_events WHERE aggregate_id IN('{device}',{order_list});
DELETE FROM sasms.devices WHERE id='{device}';
DELETE FROM sasms.sales_orders WHERE id IN({order_list});
DELETE FROM sasms.user_roles WHERE user_id IN({actor_list});
DELETE FROM sasms.users WHERE id IN({actor_list});
COMMIT;
""")

    def validate(self):
        sources = [Path('scripts/validate_sprint2.py'), Path('scripts/validate_db.py'),
                   Path('scripts/validate_sprint1.py'), Path('.github/workflows/sprint2-staging.yml'),
                   Path('database/sprint2/realtime_stock_and_locking.sql'),
                   Path('database/tests/sprint2/test_sprint2_concurrency_and_stock.sql')]
        self.summary['source_sha256'] = {p.as_posix(): hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in sources}
        self.prior_sprints()
        self.failure_detection()
        output, _ = self.run('captured payment guard initially enabled', sql=(
            "SELECT tgenabled='O' FROM pg_trigger WHERE tgrelid='sasms.payments'::regclass "
            "AND tgname='guard_payments' AND NOT tgisinternal"))
        if output != 't':
            raise RuntimeError('Captured payment guard must be enabled before isolated fixture tests')
        before = self.snapshot()
        self.run('atomic Sprint2 extension apply', file='database/sprint2/realtime_stock_and_locking.sql')
        self.run('Sprint2 extension reapply', file='database/sprint2/realtime_stock_and_locking.sql')
        if self.snapshot() != before:
            raise RuntimeError('Sprint2 extension changed existing business rows')
        _, notices = self.run('Sprint2 runtime ACL authorization and stock lifecycle',
                              file='database/tests/sprint2/test_sprint2_concurrency_and_stock.sql')
        self.summary['assertions'] = len(re.findall(r'PASS: ', notices))
        if self.summary['assertions'] < 25 or self.snapshot() != before:
            raise RuntimeError('Sprint2 assertions did not run completely or leaked fixtures')
        for outcome in ('commit', 'rollback'):
            self.concurrent_pos(outcome)
            if self.snapshot() != before:
                raise RuntimeError(f'{outcome} race left persistent fixture changes')
        self.summary['fixtures_cleaned'] = True
        output, _ = self.run('captured payment guard restored', sql=(
            "SELECT tgenabled='O' FROM pg_trigger WHERE tgrelid='sasms.payments'::regclass "
            "AND tgname='guard_payments' AND NOT tgisinternal"))
        if output != 't':
            raise RuntimeError('Captured payment guard was not restored after fixture cleanup')
        self.summary['fixture_cleanup'] = {
            'owner_only_exact_synthetic_uuids': True,
            'captured_payment_guard_disabled_only_during_cleanup_transaction': True,
            'captured_payment_guard_restored': True,
        }
        if any(hashlib.sha256((ROOT/p).read_bytes()).hexdigest() != digest
               for p,digest in self.summary['source_sha256'].items()):
            raise RuntimeError('Sources changed during validation; rerun against stable sources')
        self.summary['status'] = 'passed'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--psql', default='psql')
    parser.add_argument('--dsn', default=os.environ.get('DATABASE_URL'))
    parser.add_argument('--apply', action='store_true', help='Bootstrap a NEW isolated DB through Sprint 0 / 1 regression')
    parser.add_argument('--evidence-dir', type=Path, default=ROOT/'database/evidence/sprint2')
    args = parser.parse_args()
    args.evidence_dir = args.evidence_dir.resolve()
    validator = Sprint2Validator(args)
    try:
        validator.validate()
    except Exception as error:
        message = validator.clean(str(error))
        validator.summary['status'] = 'failed'
        validator.summary['error'] = message
        validator.log('FAILED: '+message)
        print('Sprint2 validation failed: '+message, file=sys.stderr)
        return 1
    finally:
        validator.save()
    print(f"Sprint2 validation passed: {validator.summary['assertions']} assertions; "
          f"two POS commit/rollback contention cases; evidence: {args.evidence_dir}")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
