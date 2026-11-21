#!/usr/bin/env python3
"""Verify V008 and measure real before/after DB plans on an isolated PG16 database.

--apply bootstraps an empty database with S0-S3 regression. --benchmark additionally
requires --apply and an explicitly named sasms_s4_* database. It commits uniquely
identified synthetic data, measures V007 then V008 on identical data, and removes
only its own fixtures. These are SQL measurements, never HTTP/LLM/Production SLAs.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import csv
from datetime import datetime, timedelta, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

from validate_db import ROOT, STORE, WAREHOUSE, PRODUCT, RECEIPT_ITEM, Validator

BASELINES = ROOT/'database/tests/sprint4/baseline_queries.json'


def literal(value):
    if value is None:
        return 'NULL'
    if isinstance(value, int):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def bind(sql, parameters):
    parts = sql.split('?')
    if len(parts) != len(parameters)+1:
        raise ValueError('JDBC SQL placeholder count changed; update the benchmark bindings')
    return ''.join(part + (literal(parameters[i]) if i < len(parameters) else '')
                   for i, part in enumerate(parts))


def quantile(values, fraction):
    return sorted(values)[max(0, math.ceil(len(values)*fraction)-1)]


class Sprint4Validator(Validator):
    def __init__(self, args):
        super().__init__(args)
        self.fixture = uuid.uuid4().hex.upper()
        self.fixture_created = False
        self.benchmark = {'scope': 'PostgreSQL execution plans only; connection setup, network, HTTP/JWT, browser and external LLM excluded',
                          'baseline': 'V007 original JDBC SQL', 'optimized': 'V008 canonical JDBC SQL',
                          'warmup_per_connection': 1, 'clients': args.clients, 'samples_per_client': args.samples,
                          'fixture': {'devices': 100000, 'orders': 50000, 'movements': 100000,
                                      'stores': 20, 'warehouses': 20, 'products': 100}, 'phases': {}}

    def command_run(self, label, command, timeout=900):
        result = subprocess.run(command, cwd=ROOT, env=self.env, capture_output=True,
                                text=True, encoding='utf-8', errors='replace', timeout=timeout)
        self.log(f'\n=== {label} ===\n{result.stdout}{result.stderr}')
        if result.returncode:
            raise RuntimeError(label+' failed; see validation.log')
        return result

    def migration(self):
        command = [sys.executable, str(ROOT/'scripts/migrate_db.py'), '--psql', self.args.psql,
                   '--target-version', '8']
        if self.args.dsn:
            command += ['--dsn', self.args.dsn]
        self.command_run('Apply V008 query indexes', command)
        before = self.snapshot()
        self.command_run('Verify migration checksum and no-op repeat', command)
        if self.snapshot() != before:
            raise RuntimeError('Migration replay changed business rows')

    def fixture_sql(self):
        # IDs are derived from a random run namespace. No real customer/PII is used.
        prefix = 'S4PERF-'+self.fixture
        actor = self.ident('actor', 0)
        return f"""
BEGIN;
INSERT INTO sasms.users(id,email,full_name,is_active)
 VALUES('{actor}','{prefix.lower()}@example.invalid','Synthetic Sprint 4 benchmark',true);
INSERT INTO sasms.stores(id,code,name,address)
 SELECT md5('{prefix}-store-'||i)::uuid,'{prefix}-STORE-'||i,'Synthetic S4 store','Synthetic' FROM generate_series(0,19) i;
INSERT INTO sasms.warehouses(id,store_id,code,name)
 SELECT md5('{prefix}-warehouse-'||i)::uuid,md5('{prefix}-store-'||i)::uuid,'{prefix}-WH-'||i,'Synthetic S4 warehouse' FROM generate_series(0,19) i;
INSERT INTO sasms.user_roles(user_id,role_code,store_id)
 SELECT '{actor}',role,md5('{prefix}-store-0')::uuid FROM unnest(ARRAY['WAREHOUSE_STAFF','STORE_MANAGER']) role;
INSERT INTO sasms.products(id,sku,brand_id,category_id,name,list_price)
 SELECT md5('{prefix}-product-'||i)::uuid,'{prefix}-SKU-'||i,'00000000-0000-4000-8000-000000000020',
 '00000000-0000-4000-8000-000000000021','Synthetic S4 product '||i,100 FROM generate_series(0,99) i;
INSERT INTO sasms.purchase_orders(id,code,supplier_id,warehouse_id,created_by)
 SELECT md5('{prefix}-po-'||i)::uuid,'{prefix}-PO-'||i,'00000000-0000-4000-8000-000000000040',
 md5('{prefix}-warehouse-'||i)::uuid,'{actor}' FROM generate_series(0,19) i;
INSERT INTO sasms.purchase_order_items(id,purchase_order_id,product_id,quantity,unit_cost)
 SELECT md5('{prefix}-poi-'||w||'-'||p)::uuid,md5('{prefix}-po-'||w)::uuid,
 md5('{prefix}-product-'||p)::uuid,1000,50 FROM generate_series(0,19) w CROSS JOIN generate_series(0,99) p;
INSERT INTO sasms.goods_receipts(id,code,purchase_order_id,warehouse_id,received_by)
 SELECT md5('{prefix}-gr-'||i)::uuid,'{prefix}-GR-'||i,md5('{prefix}-po-'||i)::uuid,
 md5('{prefix}-warehouse-'||i)::uuid,'{actor}' FROM generate_series(0,19) i;
INSERT INTO sasms.goods_receipt_items(id,receipt_id,purchase_order_id,purchase_order_item_id,product_id,quantity)
 SELECT md5('{prefix}-gri-'||w||'-'||p)::uuid,md5('{prefix}-gr-'||w)::uuid,md5('{prefix}-po-'||w)::uuid,
 md5('{prefix}-poi-'||w||'-'||p)::uuid,md5('{prefix}-product-'||p)::uuid,1000
 FROM generate_series(0,19) w CROSS JOIN generate_series(0,99) p;
INSERT INTO sasms.devices(id,product_id,receipt_item_id,warehouse_id,serial,qr_token)
 SELECT md5('{prefix}-device-'||i)::uuid,md5('{prefix}-product-'||(i%100))::uuid,
 md5('{prefix}-gri-'||((i/100)%20)||'-'||(i%100))::uuid,md5('{prefix}-warehouse-'||((i/100)%20))::uuid,
 '{prefix}-SERIAL-'||i,md5('{prefix}-qr-'||i)::uuid FROM generate_series(0,99999) i;
INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,channel,created_by,created_at,status,paid_at,total_amount)
 SELECT md5('{prefix}-order-'||i)::uuid,'{prefix}-ORDER-'||i,md5('{prefix}-store-'||(i%20))::uuid,
 md5('{prefix}-warehouse-'||(i%20))::uuid,'POS','{actor}',
 timestamptz '2026-01-01 00:00:00+00'+i*interval '1 hour',
 CASE WHEN (i/20)%5=0 THEN 'DRAFT' ELSE 'PAID' END,
 CASE WHEN (i/20)%5=0 THEN NULL ELSE timestamptz '2026-01-01 00:00:00+00'+i*interval '1 hour' END,100
 FROM generate_series(0,49999) i;
INSERT INTO sasms.inventory_movements(id,device_id,warehouse_id,kind,actor_id,reason,created_at)
 SELECT md5('{prefix}-movement-'||i)::uuid,md5('{prefix}-device-'||i)::uuid,
 md5('{prefix}-warehouse-'||((i/100)%20))::uuid,
 (ARRAY['RECEIVE','RETURN','SALE'])[1+i%3],'{actor}','Synthetic S4 benchmark',
 timestamptz '2026-01-01 00:00:00+00'+i*interval '1 hour' FROM generate_series(0,99999) i;
COMMIT;
ANALYZE sasms.devices; ANALYZE sasms.device_identifiers; ANALYZE sasms.products;
ANALYZE sasms.warehouses; ANALYZE sasms.sales_orders; ANALYZE sasms.inventory_movements;
"""

    def ident(self, kind, index):
        return str(uuid.UUID(hashlib.md5(f'S4PERF-{self.fixture}-{kind}-{index}'.encode()).hexdigest()))

    def fixture_cleanup(self):
        if not self.fixture_created:
            return
        prefix = 'S4PERF-'+self.fixture
        # Commit and vacuum large fixture tables before deleting their parents.
        # PG's RI checks on old, unindexed FK columns otherwise rescan thousands
        # of dead tuples for every parent deletion. Each operation targets only
        # this run's exact UUIDs; no business trigger or FK is disabled.
        for table,kind,last in [('inventory_movements','movement',99999),
                                ('devices','device',99999),('sales_orders','order',49999)]:
            self.run('Remove exact benchmark '+table,sql=f"DELETE FROM sasms.{table} WHERE id IN("
                     f"SELECT md5('{prefix}-{kind}-'||i)::uuid FROM generate_series(0,{last}) i);",timeout=300)
            self.run('Reclaim benchmark-only dead tuples '+table,sql=f'VACUUM (ANALYZE) sasms.{table};',timeout=120)
            if table == 'devices':
                self.run('Reclaim synthetic identifier tuples',sql='VACUUM (ANALYZE) sasms.device_identifiers;',timeout=120)
        self.run('Remove exact benchmark catalog and actor UUIDs', sql=f"""
BEGIN;
DELETE FROM sasms.goods_receipt_items WHERE id IN(SELECT md5('{prefix}-gri-'||w||'-'||p)::uuid FROM generate_series(0,19) w CROSS JOIN generate_series(0,99) p);
DELETE FROM sasms.goods_receipts WHERE id IN(SELECT md5('{prefix}-gr-'||i)::uuid FROM generate_series(0,19) i);
DELETE FROM sasms.purchase_order_items WHERE id IN(SELECT md5('{prefix}-poi-'||w||'-'||p)::uuid FROM generate_series(0,19) w CROSS JOIN generate_series(0,99) p);
DELETE FROM sasms.purchase_orders WHERE id IN(SELECT md5('{prefix}-po-'||i)::uuid FROM generate_series(0,19) i);
DELETE FROM sasms.products WHERE id IN(SELECT md5('{prefix}-product-'||i)::uuid FROM generate_series(0,99) i);
DELETE FROM sasms.user_roles WHERE user_id='{self.ident('actor',0)}';
DELETE FROM sasms.users WHERE id='{self.ident('actor',0)}';
DELETE FROM sasms.warehouses WHERE id IN(SELECT md5('{prefix}-warehouse-'||i)::uuid FROM generate_series(0,19) i);
DELETE FROM sasms.stores WHERE id IN(SELECT md5('{prefix}-store-'||i)::uuid FROM generate_series(0,19) i);
COMMIT;
ANALYZE sasms.products;
ANALYZE sasms.warehouses; ANALYZE sasms.sales_orders; ANALYZE sasms.inventory_movements;
""", timeout=300)

    def query_set(self, phase):
        document = json.loads(BASELINES.read_text(encoding='utf-8'))
        parameters = {}
        actor = self.ident('actor', 0)
        warehouse = self.ident('warehouse', 0)
        store = self.ident('store', 0)
        start, end = '2026-11-01T00:00:00+00', '2026-12-01T00:00:00+00'
        parameters['qr'] = [self.ident('qr', 0), actor]
        parameters['ai_stock'] = [warehouse, '{'+','.join('S4PERF-'+self.fixture+'-SKU-'+str(i) for i in range(3))+'}']
        parameters['bi_summary'] = ([store,start,end,store,start,end,store,store,start,end,store,start,end,store,start,end]
                                    if phase == 'before' else [store,start,end,store,store,start,end])
        parameters['inventory_stock'] = [actor, warehouse, warehouse, None, None, 1000, 0]
        queries = {}
        for name, spec in document['queries'].items():
            sql = spec['sql']
            if phase == 'after':
                source = (ROOT/spec['path']).read_text(encoding='utf-8')
                match = re.search(r'\b'+spec['constant']+r'\s*=\s*"""(.*?)"""',source,re.S)
                if not match:
                    raise RuntimeError('Canonical JDBC query missing: '+name)
                sql = match[1]
            queries[name] = bind(sql, parameters[name])
        return queries

    def measure(self, phase):
        phase_result = {}
        queries = self.query_set(phase)
        for name, query in queries.items():
            self.log(f'Benchmark {phase}/{name}: {self.args.clients} connections x {self.args.samples} samples')
            result, _ = self.run(f'{phase} {name} exact output', sql='BEGIN; SET LOCAL ROLE sasms_app; '
                +'SELECT coalesce(jsonb_agg(to_jsonb(q)),\'[]\'::jsonb) FROM ('+query+') q; ROLLBACK;')
            expected = json.loads(result)
            if phase == 'after' and expected != self.benchmark['phases']['before'][name]['result']:
                raise RuntimeError('Optimization changed query results: '+name)
            # All psql connections wait on the same future timestamp inside PG.
            # Warmup starts together; sampled plans exclude that synchronization.
            ready = (datetime.now(timezone.utc)+timedelta(seconds=8)).isoformat()
            sql = ("BEGIN;\nSET LOCAL ROLE sasms_app;\nSET LOCAL statement_timeout='30s';\n"
                   "SELECT pg_backend_pid();\n\\o " + os.devnull + "\n"
                   "SELECT pg_sleep(greatest(0,extract(epoch FROM ("
                   +literal(ready)+"::timestamptz-clock_timestamp()))));\n"+query+';\n\\o\n'
                   +('\n'.join('EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) '+query+';' for _ in range(self.args.samples)))
                   +'\nROLLBACK;')

            def client(_):
                outcome = subprocess.run(self.command+['-f','-'],input=sql,cwd=ROOT,env=self.env,
                                         capture_output=True,text=True,encoding='utf-8',timeout=180)
                if outcome.returncode:
                    self.log(outcome.stderr)
                    raise RuntimeError(f'{phase}/{name} client failed')
                lines = outcome.stdout.strip().splitlines()
                if not lines or not lines[0].strip().isdigit():
                    raise RuntimeError('Missing independent backend PID')
                pid = int(lines[0])
                payload = '\n'.join(lines[1:]).strip()
                plans, decoder = [], json.JSONDecoder()
                while payload:
                    plan, used = decoder.raw_decode(payload)
                    plans.append(plan[0])
                    payload = payload[used:].lstrip()
                if len(plans) != self.args.samples:
                    raise RuntimeError('Missing measured query samples')
                return pid, plans

            started = time.monotonic()
            with ThreadPoolExecutor(max_workers=self.args.clients) as pool:
                measured = list(pool.map(client, range(self.args.clients)))
            pids = [pid for pid,_ in measured]
            if len(set(pids)) != self.args.clients:
                raise RuntimeError('Benchmark did not use independent connections')
            plans = [plan for _,group in measured for plan in group]
            durations = [plan['Execution Time'] for plan in plans]
            phase_result[name] = {'result': expected, 'samples':len(durations), 'backend_pids':pids,
                'p50_execution_ms':quantile(durations,.5), 'p95_execution_ms':quantile(durations,.95),
                'p99_execution_ms':quantile(durations,.99), 'max_execution_ms':max(durations),
                'wall_seconds_including_start_and_sync':round(time.monotonic()-started,3),
                'query_sha256':hashlib.sha256(query.encode()).hexdigest(),
                'representative_plan':plans[len(plans)//2]}
            # Keep every timing sample compactly, plus the representative plan
            # above; retaining thousands of repeated plan trees bloats handoff.
            out = self.args.evidence_dir/'plans'
            out.mkdir(parents=True, exist_ok=True)
            with (out/f'{phase}-{name}-samples.csv').open('w',encoding='utf-8',newline='') as stream:
                writer = csv.writer(stream)
                writer.writerow(['backend_pid','sample','execution_ms','planning_ms','shared_hit_blocks','shared_read_blocks'])
                for pid,group in measured:
                    for number,plan in enumerate(group,1):
                        writer.writerow([pid,number,plan['Execution Time'],plan['Planning Time'],
                                         plan['Plan'].get('Shared Hit Blocks',0),plan['Plan'].get('Shared Read Blocks',0)])
            print(f'{phase}/{name}: p95={phase_result[name]["p95_execution_ms"]:.3f}ms ({len(durations)} samples)',flush=True)
        self.benchmark['phases'][phase] = phase_result

    def validate(self):
        sources = [Path('scripts/validate_sprint4.py'), BASELINES.relative_to(ROOT)]
        sources += [p.relative_to(ROOT) for p in sorted((ROOT/'database/migrations').glob('*.sql'))]
        sources += [p.relative_to(ROOT) for p in sorted((ROOT/'database/tests/sprint4').glob('*.sql'))]
        baseline_specs = json.loads(BASELINES.read_text(encoding='utf-8'))['queries']
        sources += [Path(spec['path']) for spec in baseline_specs.values()]
        self.summary['source_sha256'] = {p.as_posix():hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in sources}
        self.summary['scope'] = 'Isolated PostgreSQL16 V008, query equality, ACL/regression; SQL timings do not establish end-to-end SLAs'
        version,_ = self.run('PostgreSQL16 preflight',sql="SELECT current_setting('server_version_num')::int")
        if not 160000 <= int(version) < 170000:
            raise RuntimeError('PostgreSQL16 required')
        if self.args.benchmark:
            database,_ = self.run('Benchmark database name guard',sql='SELECT current_database()')
            if not self.args.apply or not re.fullmatch(r'sasms_s4_[a-z0-9_]+',database):
                raise RuntimeError('--benchmark requires --apply and a fresh sasms_s4_* database')
        if self.args.apply:
            command = [sys.executable,str(ROOT/'scripts/validate_sprint3.py'),'--apply','--psql',self.args.psql,
                       '--evidence-dir',str(self.args.evidence_dir/'sprint3-regression')]
            if self.args.dsn:
                command += ['--dsn',self.args.dsn]
            self.command_run('S0-S3 regression on fresh database',command)
            prior = json.loads((self.args.evidence_dir/'sprint3-regression/summary.json').read_text(encoding='utf-8'))
            if prior['status'] != 'passed':
                raise RuntimeError('S3 regression failed')
            self.summary['sprint3_regression'] = {'status':prior['status'],'assertions':prior['assertions'],
                                                  'concurrency':prior['concurrency']}
        before = self.snapshot()
        self.summary['benchmark_fixture_namespace'] = self.fixture if self.args.benchmark else None
        try:
            if self.args.benchmark:
                ledger,_ = self.run('Confirm genuine V007 baseline',sql='SELECT max(version) FROM sasms_meta.schema_migrations')
                if int(ledger) != 7:
                    raise RuntimeError('Before/after benchmark requires genuine V007 before V008')
                self.run('Commit synthetic multi-store benchmark fixtures',sql=self.fixture_sql(),timeout=600)
                self.fixture_created = True
                self.measure('before')
            self.migration()
            if self.args.benchmark:
                self.run('Refresh planner statistics after indexes',sql='ANALYZE sasms.devices; ANALYZE sasms.sales_orders; ANALYZE sasms.inventory_movements;')
                self.measure('after')
                self.benchmark['result_equality'] = True
                self.benchmark['improvement_p95_percent'] = {
                    key:round(100*(1-self.benchmark['phases']['after'][key]['p95_execution_ms']/
                                    self.benchmark['phases']['before'][key]['p95_execution_ms']),2)
                    for key in self.benchmark['phases']['before']}
                self.summary['benchmark'] = self.benchmark
        finally:
            self.fixture_cleanup()
        if self.snapshot() != before:
            raise RuntimeError('Benchmark or migration changed non-fixture business data')
        self.summary['fixtures_cleaned'] = True
        _,notices = self.run('S4 query correctness and runtime ACL',file=ROOT/'database/tests/sprint4/query_optimization.sql')
        self.summary['assertions'] = len(re.findall(r'PASS: ',notices))
        if self.summary['assertions'] < 10 or self.snapshot() != before:
            raise RuntimeError('Incomplete S4 assertions or leaked test fixtures')
        schema,_ = self.run('S4 schema shape',sql="SELECT json_build_object('version',(SELECT max(version) FROM sasms_meta.schema_migrations),'tables',(SELECT count(*) FROM pg_tables WHERE schemaname='sasms'))")
        self.summary['schema'] = json.loads(schema)
        if self.summary['schema'] != {'version':8,'tables':41}:
            raise RuntimeError('Expected V008 and 41 business tables')
        if any(hashlib.sha256((ROOT/p).read_bytes()).hexdigest() != digest for p,digest in self.summary['source_sha256'].items()):
            raise RuntimeError('Sources changed during validation; rerun with stable files')
        self.summary['status'] = 'passed'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--psql',default='psql')
    parser.add_argument('--dsn',default=os.environ.get('DATABASE_URL'))
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--benchmark',action='store_true')
    parser.add_argument('--clients',type=int,default=50)
    parser.add_argument('--samples',type=int,default=30)
    parser.add_argument('--evidence-dir',type=Path,default=ROOT/'database/evidence/sprint4')
    args = parser.parse_args()
    if not 1<=args.clients<=50 or not 5<=args.samples<=200:
        parser.error('Use 1..50 clients and 5..200 measured samples per client')
    args.evidence_dir = args.evidence_dir.resolve()
    validator = Sprint4Validator(args)
    try:
        validator.validate()
    except Exception as error:
        validator.summary['status']='failed'
        validator.summary['error']=validator.clean(str(error))
        validator.log('FAILED: '+str(error))
        print(validator.clean(str(error)),file=sys.stderr)
        return 1
    finally:
        validator.save()
    print(f'Sprint4 passed: {validator.summary["assertions"]} assertions, V008, fixture cleanup verified.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
