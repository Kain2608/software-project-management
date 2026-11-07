#!/usr/bin/env python3
"""Validate SASMS in an isolated PostgreSQL16 database owned by the caller.

Uses only Python stdlib and psql. Never points at production; --apply refuses an
existing sasms schema. Concurrency fixtures briefly commit and are removed by
their exact UUIDs without dropping schemas or disabling triggers.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
ACTOR = "00000000-0000-4000-8000-000000000001"
STORE = "00000000-0000-4000-8000-000000000010"
WAREHOUSE = "00000000-0000-4000-8000-000000000011"
PRODUCT = "00000000-0000-4000-8000-000000000030"
RECEIPT_ITEM = "00000000-0000-4000-8000-000000000061"


class Validator:
    def __init__(self, args):
        self.args = args
        self.lines = []
        self.summary = {"started_at_utc": datetime.now(timezone.utc).isoformat(),
                        "status": "running", "checks": {}}
        self.env = dict(os.environ, PGCLIENTENCODING="UTF8")
        self.command = [args.psql, "-X", "-q", "-A", "-t", "-P", "pager=off",
                        "-v", "ON_ERROR_STOP=1", "-v", "VERBOSITY=verbose"]
        if args.dsn:
            self.command += ["--dbname", args.dsn]

    def clean(self, text):
        if self.args.dsn:
            text = text.replace(self.args.dsn, "[REDACTED_DSN]")
        text = re.sub(r"(?i)(password\s*=\s*)('[^']*'|\S+)", r"\1[REDACTED]", text)
        return re.sub(r"(postgres(?:ql)?://[^:\s/@]+:)[^@\s]+@", r"\1[REDACTED]@", text)

    def log(self, message):
        self.lines.append(self.clean(message))

    def invoke(self, sql=None, file=None, timeout=90):
        command = self.command + (["-f", str(file)] if file else ["-c", sql])
        return subprocess.run(command, cwd=ROOT, env=self.env, capture_output=True,
                              text=True, encoding="utf-8", errors="replace", timeout=timeout)

    def run(self, label, sql=None, file=None, timeout=90):
        start = time.monotonic()
        result = self.invoke(sql=sql, file=file, timeout=timeout)
        self.log(f"\n=== {label} ===\n{result.stdout}{result.stderr}".rstrip())
        if result.returncode:
            raise RuntimeError(f"{label} failed (psql exit {result.returncode}); see validation.log")
        self.summary["checks"][label] = {"passed": True, "seconds": round(time.monotonic()-start, 4)}
        return result.stdout.strip(), result.stderr

    def snapshot(self):
        output, _ = self.run("table names", sql="SELECT json_agg(table_name ORDER BY table_name) FROM information_schema.tables WHERE table_schema='sasms' AND table_type='BASE TABLE'")
        tables = json.loads(output)
        if not tables or any(not re.fullmatch(r"[a-z_]+", name) for name in tables):
            raise RuntimeError("Unexpected table names")
        query = " UNION ALL ".join(
            f"SELECT '{name}' name,md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'null')) digest FROM sasms.{name} t"
            for name in tables)
        output, _ = self.run("seed snapshot", sql=f"SELECT jsonb_object_agg(name,digest) FROM ({query}) s")
        return json.loads(output)

    def concurrency(self):
        suffix = uuid.uuid4().hex.upper()
        orders = [str(uuid.uuid4()), str(uuid.uuid4())]
        device = None
        order_list = ",".join(f"'{value}'" for value in orders)
        try:
            output, _ = self.run("race receive fixture", sql=f"SELECT sasms.receive_device('{RECEIPT_ITEM}','TEST-RACE-{suffix}',NULL,'{ACTOR}')")
            device = str(uuid.UUID(output))
            statements = ["BEGIN;"]
            for index, order in enumerate(orders):
                item = str(uuid.uuid4())
                statements += [
                    f"INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,channel,created_by) VALUES('{order}','RACE-{suffix}-{index}','{STORE}','{WAREHOUSE}','POS','{ACTOR}');",
                    f"INSERT INTO sasms.sales_order_items(id,order_id,product_id,quantity,unit_price) VALUES('{item}','{order}','{PRODUCT}',1,100);",
                    f"INSERT INTO sasms.order_devices(order_item_id,order_id,product_id,device_id) VALUES('{item}','{order}','{PRODUCT}','{device}');"]
            statements.append("COMMIT;")
            self.run("race cart fixtures", sql="\n".join(statements))
            barrier = threading.Barrier(2)

            def worker(order):
                barrier.wait(timeout=10)
                start = time.monotonic()
                result = self.invoke(sql=f"BEGIN; SET LOCAL lock_timeout='8s'; SELECT sasms.reserve_order('{order}','{ACTOR}'); SELECT pg_sleep(0.6); COMMIT;", timeout=20)
                return order, result, round(time.monotonic()-start, 4)

            with ThreadPoolExecutor(max_workers=2) as pool:
                results = list(pool.map(worker, orders))
            winners = []
            for index, (order, result, elapsed) in enumerate(results):
                self.log(f"\n=== race session {index+1}: exit {result.returncode}, seconds {elapsed} ===\n{result.stdout}{result.stderr}".rstrip())
                if result.returncode == 0:
                    winners.append(order)
                elif "23514" not in result.stderr or "Device unavailable" not in result.stderr:
                    raise RuntimeError("Race failed for an unexpected reason")
            if len(winners) != 1:
                raise RuntimeError(f"Expected exactly one successful reservation, got {len(winners)}")
            winner = winners[0]
            output, _ = self.run("race stock ownership", sql=f"SELECT json_build_object('reserved_devices',(SELECT count(*) FROM sasms.devices WHERE id='{device}' AND status='RESERVED'),'reserved_orders',(SELECT count(*) FROM sasms.sales_orders WHERE id IN ({order_list}) AND status='RESERVED'),'draft_orders',(SELECT count(*) FROM sasms.sales_orders WHERE id IN ({order_list}) AND status='DRAFT'),'reserve_movements',(SELECT count(*) FROM sasms.inventory_movements WHERE device_id='{device}' AND kind='RESERVE'))")
            ownership = json.loads(output)
            if any(value != 1 for value in ownership.values()):
                raise RuntimeError(f"Race ownership mismatch: {ownership}")
            output, _ = self.run("race winner checkout rollback", sql=f"""
BEGIN;
INSERT INTO sasms.payments(order_id,method,status,amount,confirmed_by,captured_at)
 VALUES('{winner}','CASH','CAPTURED',100,'{ACTOR}',now());
SELECT sasms.complete_sale('{winner}','{ACTOR}');
SELECT json_build_object(
 'sold_devices',(SELECT count(*) FROM sasms.devices WHERE id='{device}' AND status='SOLD'),
 'paid_orders',(SELECT count(*) FROM sasms.sales_orders WHERE id IN ({order_list}) AND status='PAID'),
 'sale_movements',(SELECT count(*) FROM sasms.inventory_movements WHERE device_id='{device}' AND kind='SALE'),
 'invoices',(SELECT count(*) FROM sasms.invoices WHERE order_id IN ({order_list})));
ROLLBACK;""")
            sale = json.loads(next(line for line in output.splitlines() if line.startswith("{")))
            if any(value != 1 for value in sale.values()):
                raise RuntimeError(f"Race checkout count mismatch: {sale}")
            self.summary["concurrency"] = {"sessions": 2, "successful_reservations": 1,
                "expected_23514_rejections": 1, "stock_ownership": ownership,
                "checkout_counts_inside_rollback_transaction": sale}
        finally:
            if device:
                self.run("race exact fixture cleanup", sql=f"""
BEGIN;
SELECT sasms.cancel_order(id,'{ACTOR}') FROM sasms.sales_orders WHERE id IN ({order_list});
-- Owner-only cleanup of these synthetic UUIDs in the isolated test database.
UPDATE sasms.sales_orders SET status='DRAFT',paid_at=NULL WHERE id IN ({order_list});
DELETE FROM sasms.order_devices WHERE order_id IN ({order_list});
DELETE FROM sasms.sales_order_items WHERE order_id IN ({order_list});
DELETE FROM sasms.payments WHERE order_id IN ({order_list});
DELETE FROM sasms.inventory_movements WHERE device_id='{device}';
DELETE FROM sasms.audit_logs WHERE entity_id IN ('{device}',{order_list});
DELETE FROM sasms.outbox_events WHERE aggregate_id IN ('{device}',{order_list});
DELETE FROM sasms.devices WHERE id='{device}';
DELETE FROM sasms.sales_orders WHERE id IN ({order_list});
COMMIT;""")

    def validate(self):
        sources = [Path("database/schema.sql"), Path("database/seed.sql"),
                   Path("database/runtime-roles.sql"), Path("scripts/validate_db.py")]
        sources += sorted(Path("database/tests").glob("*.sql")) if Path.cwd() == ROOT else sorted(p.relative_to(ROOT) for p in (ROOT/"database/tests").glob("*.sql"))
        self.summary["source_sha256"] = {str(p).replace("\\", "/"): hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in sources}
        output, _ = self.run("PostgreSQL16 version", sql="SELECT json_build_object('version',version(),'version_num',current_setting('server_version_num')::int)")
        self.summary["postgresql"] = json.loads(output)
        if not 160000 <= self.summary["postgresql"]["version_num"] < 170000:
            raise RuntimeError("Validation requires PostgreSQL major version 16")
        output, _ = self.run("schema presence", sql="SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname='sasms')")
        exists = output == "t"
        if self.args.apply:
            if exists:
                raise RuntimeError("--apply refuses an existing sasms schema; use a new isolated validation database")
            self.run("schema apply", file="database/schema.sql")
        elif not exists:
            raise RuntimeError("sasms schema is absent; use --apply on an isolated validation database")
        self.run("seed first apply", file="database/seed.sql")
        before = self.snapshot()
        self.run("seed second apply", file="database/seed.sql")
        if self.snapshot() != before:
            raise RuntimeError("Second seed application changed table contents")
        self.summary["seed_idempotency"] = {"passed": True, "compared_tables": len(before)}
        _, notices = self.run("constraint and lifecycle assertions", file="database/tests/constraints.sql")
        self.summary["constraint_assertions"] = len(re.findall(r"PASS: ", notices))
        self.run("runtime role provisioning", file="database/runtime-roles.sql")
        _, notices = self.run("runtime ACL assertions", file="database/tests/runtime_acl.sql")
        self.summary["runtime_acl_assertions"] = len(re.findall(r"PASS: ", notices))
        self.concurrency()
        if self.args.benchmark:
            output, _ = self.run("100000-device lookup benchmark rollback", file="database/tests/benchmark.sql", timeout=180)
            decoder = json.JSONDecoder()
            plans = []
            offset = 0
            while True:
                start = output.find("[", offset)
                if start < 0:
                    break
                plan, length = decoder.raw_decode(output[start:])
                plans.append(plan[0])
                offset = start + length
            if len(plans) != 3 or any(p["Plan"]["Node Type"] not in ("Index Scan", "Index Only Scan", "Bitmap Heap Scan") for p in plans):
                raise RuntimeError("Expected three indexed lookup plans")
            self.summary["lookup_benchmark"] = {"synthetic_device_count": 100000,
                "transaction_rolled_back": True, "scope": "database lookups only; no end-to-end SLA claim",
                "plans": plans}
        if self.snapshot() != before:
            raise RuntimeError("Validation fixtures changed persistent table contents")
        self.summary["fixtures_cleaned"] = True
        if any(hashlib.sha256((ROOT/p).read_bytes()).hexdigest() != digest for p,digest in self.summary["source_sha256"].items()):
            raise RuntimeError("Source files changed during validation; rerun against stable sources")
        self.summary["status"] = "passed"

    def save(self):
        self.summary["finished_at_utc"] = datetime.now(timezone.utc).isoformat()
        target = self.args.evidence_dir
        target.mkdir(parents=True, exist_ok=True)
        (target/"validation.log").write_text("\n".join(self.lines)+"\n", encoding="utf-8")
        (target/"summary.json").write_text(json.dumps(self.summary, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--psql", default="psql", help="Path to psql executable")
    parser.add_argument("--dsn", default=os.environ.get("DATABASE_URL"), help="libpq DSN; never written to evidence (or use PG* environment variables)")
    parser.add_argument("--apply", action="store_true", help="Apply baseline only when sasms schema is absent; refuses existing schema")
    parser.add_argument("--benchmark", action="store_true", help="Load 100000 synthetic devices and roll back after indexed lookup EXPLAIN")
    parser.add_argument("--evidence-dir", type=Path, default=ROOT/"database/evidence")
    args = parser.parse_args()
    validator = Validator(args)
    try:
        validator.validate()
    except Exception as error:
        message = validator.clean(str(error))
        validator.summary["status"] = "failed"
        validator.summary["error"] = message
        validator.log(f"FAILED: {message}")
        print(f"Validation failed: {message}", file=sys.stderr)
        return 1
    finally:
        validator.save()
    print(f"Validation passed: {validator.summary['constraint_assertions']} constraint assertions, "
          f"{validator.summary['runtime_acl_assertions']} ACL assertions, two-session stock race; evidence: {args.evidence_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
