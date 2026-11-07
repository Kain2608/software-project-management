#!/usr/bin/env python3
"""Validate the real Sprint 1 upgrade in an isolated PostgreSQL 16 database.

Requires an owner connection, Python stdlib and psql. --apply bootstraps an empty
database and runs the frozen Sprint 0 regression first. Existing baselines are
upgraded in place; already upgraded databases can rerun the Sprint 1 checks.
Never use this fixture/provisioning runner against production.
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
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import uuid

from validate_db import Validator as BaseValidator

ROOT = Path(__file__).resolve().parents[1]
ACTOR = "00000000-0000-4000-8000-000000000001"
STORE = "00000000-0000-4000-8000-000000000010"
WAREHOUSE = "00000000-0000-4000-8000-000000000011"
PRODUCT = "00000000-0000-4000-8000-000000000030"
RECEIPT_ITEM = "00000000-0000-4000-8000-000000000061"


class Sprint1Validator(BaseValidator):
    def __init__(self, args):
        super().__init__(args)
        self.upgrade_fixture = None
        self.legacy_role = "s1_legacy_probe_"+uuid.uuid4().hex[:12]
        self.summary["scope"] = "isolated PostgreSQL database behavior; no API, device scan, remote Staging, provider or approval claim"

    def rows(self):
        output, _ = self.run("snapshot table names", sql="SELECT json_agg(tablename ORDER BY tablename) FROM pg_tables WHERE schemaname='sasms'")
        tables = json.loads(output)
        if not tables or any(not re.fullmatch(r"[a-z_]+", name) for name in tables):
            raise RuntimeError("Unexpected SASMS table names")
        query = " UNION ALL ".join(
            f"SELECT '{name}' AS name,coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb) AS rows FROM sasms.{name} t"
            for name in tables)
        output, _ = self.run("snapshot table contents", sql=f"SELECT jsonb_object_agg(name,rows) FROM ({query}) tables")
        return json.loads(output)

    def migration_command(self, directory=None):
        command = [sys.executable, str(ROOT/"scripts/migrate_db.py"), "--psql", self.args.psql]
        if self.args.dsn:
            command += ["--dsn", self.args.dsn]
        if directory:
            command += ["--migrations-dir", str(directory)]
        return command

    def migrate(self, label, directory=None, expect_failure=None):
        start = time.monotonic()
        result = subprocess.run(self.migration_command(directory), cwd=ROOT, env=self.env,
                                capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90)
        self.log(f"\n=== {label} ===\n{result.stdout}{result.stderr}".rstrip())
        if expect_failure:
            if result.returncode == 0 or expect_failure.lower() not in (result.stdout+result.stderr).lower():
                raise RuntimeError(f"{label} did not reject the intended failure")
            self.summary["checks"][label] = {"passed": True, "expected_failure_rejected": True,
                "seconds": round(time.monotonic()-start, 4)}
        elif result.returncode:
            raise RuntimeError(f"{label} failed; see validation.log")
        else:
            self.summary["checks"][label] = {"passed": True, "seconds": round(time.monotonic()-start, 4)}
        return result

    def migration_safety(self):
        output, _ = self.run("migration ledger before safety probes", sql="SELECT coalesce(jsonb_agg(to_jsonb(m) ORDER BY to_jsonb(m)::text),'[]') FROM sasms_meta.schema_migrations m")
        ledger = json.loads(output)
        self.migrate("migration reapply is a noop")
        output, _ = self.run("migration ledger after noop", sql="SELECT coalesce(jsonb_agg(to_jsonb(m) ORDER BY to_jsonb(m)::text),'[]') FROM sasms_meta.schema_migrations m")
        if json.loads(output) != ledger:
            raise RuntimeError("Migration reapply changed the ledger")
        source = ROOT/"database/migrations"
        with tempfile.TemporaryDirectory(prefix="sasms-s1-migration-probe-") as tmp:
            directory = Path(tmp)
            for path in source.glob("V*.sql"):
                shutil.copy2(path, directory/path.name)
            first = sorted(directory.glob("V*.sql"))[0]
            with first.open("a", encoding="utf-8", newline="\n") as stream:
                stream.write("\n-- INTENTIONAL CHECKSUM MISMATCH PROBE ONLY\n")
            self.migrate("changed applied migration is rejected", directory, expect_failure="checksum")
        with tempfile.TemporaryDirectory(prefix="sasms-s1-atomic-probe-") as tmp:
            directory = Path(tmp)
            paths = sorted(source.glob("V*.sql"))
            for path in paths:
                shutil.copy2(path, directory/path.name)
            version = max(int(re.match(r"V(\d+)__", path.name).group(1)) for path in paths)+1
            (directory/f"V{version:03d}__s1_atomic_probe.sql").write_text(
                "CREATE TABLE sasms.s1_validation_atomic_probe (id integer);\n", encoding="utf-8")
            (directory/f"V{version+1:03d}__s1_intentional_failure.sql").write_text(
                "DO $$ BEGIN RAISE EXCEPTION 'S1_INTENTIONAL_ROLLBACK_PROBE'; END $$;\n", encoding="utf-8")
            self.migrate("failed pending migrations roll back together", directory,
                         expect_failure="S1_INTENTIONAL_ROLLBACK_PROBE")
        output, _ = self.run("atomic probe table absent", sql="SELECT to_regclass('sasms.s1_validation_atomic_probe') IS NULL")
        if output != "t":
            raise RuntimeError("Failed migration left a committed probe table")
        output, _ = self.run("migration ledger after rejected probes", sql="SELECT coalesce(jsonb_agg(to_jsonb(m) ORDER BY to_jsonb(m)::text),'[]') FROM sasms_meta.schema_migrations m")
        if json.loads(output) != ledger:
            raise RuntimeError("Rejected migration changed the ledger")
        self.summary["migration_safety"] = {"reapply_noop": True, "checksum_rejected": True,
            "all_pending_ddl_and_ledger_rolled_back": True, "applied_migrations": len(ledger)}

    def baseline(self):
        output, _ = self.run("PostgreSQL16 version", sql="SELECT json_build_object('version',version(),'version_num',current_setting('server_version_num')::int)")
        self.summary["postgresql"] = json.loads(output)
        if not 160000 <= self.summary["postgresql"]["version_num"] < 170000:
            raise RuntimeError("Validation requires PostgreSQL major version 16")
        output, _ = self.run("baseline schema presence", sql="SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname='sasms')")
        exists = output == "t"
        if self.args.apply and exists:
            raise RuntimeError("--apply requires an empty isolated database; it never resets an existing schema")
        if not exists and not self.args.apply:
            raise RuntimeError("No Sprint0 baseline; use --apply for a new isolated validation database")
        output, _ = self.run("Sprint1 migration schema presence", sql="SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname='sasms_meta')")
        already_upgraded = output == "t"
        if not already_upgraded:
            command = [sys.executable, str(ROOT/"scripts/validate_db.py"), "--psql", self.args.psql,
                       "--evidence-dir", str(self.args.evidence_dir/"baseline-regression")]
            if self.args.dsn:
                command += ["--dsn", self.args.dsn]
            if self.args.apply:
                command.append("--apply")
            result = subprocess.run(command, cwd=ROOT, env=self.env, capture_output=True,
                                    text=True, encoding="utf-8", errors="replace", timeout=120)
            self.log(f"\n=== frozen Sprint0 regression before upgrade ===\n{result.stdout}{result.stderr}".rstrip())
            if result.returncode:
                raise RuntimeError("Frozen Sprint0 regression failed")
            baseline_summary = json.loads((self.args.evidence_dir/"baseline-regression/summary.json").read_text(encoding="utf-8"))
            self.summary["baseline_regression"] = {"passed": baseline_summary["status"] == "passed",
                "constraint_assertions": baseline_summary["constraint_assertions"],
                "runtime_acl_assertions": baseline_summary["runtime_acl_assertions"]}
            serial = "S1-PREUPGRADE-"+uuid.uuid4().hex.upper()
            output, _ = self.run("pre-existing device fixture before upgrade", sql=f"SELECT sasms.receive_device('{RECEIPT_ITEM}','{serial}',NULL,'{ACTOR}')")
            self.upgrade_fixture = str(uuid.UUID(output))
            self.run("pre-upgrade intentional permission revocation and legacy integration grant", sql=f"""
DELETE FROM sasms.role_permissions WHERE role_code='SALES' AND permission_code='ai.use';
CREATE ROLE {self.legacy_role} NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
GRANT EXECUTE ON FUNCTION sasms.receive_device(uuid,text,text,uuid) TO {self.legacy_role};
""")
            before = self.rows()
            self.migrate("apply Sprint1 migrations to populated Sprint0")
            after = self.rows()
            for table, rows in before.items():
                for row in rows:
                    if not any(all(candidate.get(key) == value for key, value in row.items()) for candidate in after[table]):
                        raise RuntimeError(f"Upgrade changed or removed existing row in {table}")
            output, _ = self.run("prior revocation and legacy private ACL preserved safely", sql=f"""
SELECT json_build_object(
 'revoked_grant_still_absent',NOT EXISTS(SELECT 1 FROM sasms.role_permissions WHERE role_code='SALES' AND permission_code='ai.use'),
 'legacy_private_execute_removed',NOT has_function_privilege('{self.legacy_role}','sasms.receive_device_unchecked_s0(uuid,text,text,uuid)','EXECUTE'));
DROP ROLE {self.legacy_role};
""")
            acl = json.loads(next(line for line in output.splitlines() if line.startswith("{")))
            if not all(acl.values()):
                raise RuntimeError("Upgrade restored a revoked permission or leaked unchecked receive to an old integration role")
            self.summary["upgrade_preservation"] = {"passed": True, "baseline_tables": len(before),
                "baseline_rows": sum(len(rows) for rows in before.values()), "pre_existing_device_preserved": True,
                **acl}
        else:
            self.migrate("verify already applied Sprint1 migrations")
            self.summary["baseline_regression"] = {"skipped": "already upgraded database; prior baseline evidence remains separate"}

    def validate(self):
        sources = [Path("database/schema.sql"), Path("database/seed.sql"),
                   Path("database/runtime-roles.sql"), Path("scripts/migrate_db.py"),
                   Path("scripts/validate_sprint1.py"), Path("scripts/generate_db_artifacts.py"),
                   Path(".github/workflows/sprint1-database.yml")]
        sources += [p.relative_to(ROOT) for p in sorted((ROOT/"database/migrations").glob("V*.sql"))]
        sources += [p.relative_to(ROOT) for p in sorted((ROOT/"database/tests/sprint1").glob("*.sql"))]
        sources += [p.relative_to(ROOT) for p in sorted((ROOT/"database/sprint1").glob("*.sql"))]
        self.summary["source_sha256"] = {p.as_posix(): hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in sources}
        self.baseline()
        self.migration_safety()
        self.run("Sprint1 runtime role provisioning", file="database/sprint1/runtime-roles.sql")
        before = self.rows()
        self.run("transactional Sprint1 database demonstration", file="database/sprint1/demo.sql")
        if self.rows() != before:
            raise RuntimeError("Sprint1 demonstration failed to roll back its fixtures")
        _, notices = self.run("Sprint1 catalog IAM and batch assertions", file="database/tests/sprint1/assertions.sql")
        self.summary["assertions"] = len(re.findall(r"PASS: ", notices))
        if self.rows() != before:
            raise RuntimeError("Transactional Sprint1 assertions left persistent fixtures")
        self.concurrent_imports()
        if self.rows() != before:
            raise RuntimeError("Concurrent import fixtures were not cleaned exactly")
        self.summary["fixtures_cleaned"] = True
        if any(hashlib.sha256((ROOT/p).read_bytes()).hexdigest() != digest for p,digest in self.summary["source_sha256"].items()):
            raise RuntimeError("Validated sources changed during run; rerun against stable sources")
        self.summary["status"] = "passed"

    def concurrent_imports(self):
        suffix = uuid.uuid4().hex.upper()
        actor = str(uuid.uuid4())
        ids = {kind: [str(uuid.uuid4()),str(uuid.uuid4())] for kind in ("po","po_item","receipt","receipt_item")}
        keys, serials = [], []
        cases = []
        def uuid_list(values):
            return ",".join("'"+value+"'" for value in values)
        try:
            statements = ["BEGIN;", f"INSERT INTO sasms.users(id,email,full_name,is_active) VALUES('{actor}','s1-race-{suffix.lower()}@example.invalid','Synthetic concurrency actor',true);",
                          f"INSERT INTO sasms.user_roles(user_id,role_code,store_id) VALUES('{actor}','ADMIN',NULL);"]
            for index in range(2):
                statements += [
                    f"INSERT INTO sasms.purchase_orders(id,code,supplier_id,warehouse_id,status,created_by) VALUES('{ids['po'][index]}','S1-RACE-PO-{suffix}-{index}','00000000-0000-4000-8000-000000000040','{WAREHOUSE}','APPROVED','{actor}');",
                    f"INSERT INTO sasms.purchase_order_items(id,purchase_order_id,product_id,quantity,unit_cost) VALUES('{ids['po_item'][index]}','{ids['po'][index]}','{PRODUCT}',20,50);",
                    f"INSERT INTO sasms.goods_receipts(id,code,purchase_order_id,warehouse_id,received_by) VALUES('{ids['receipt'][index]}','S1-RACE-GR-{suffix}-{index}','{ids['po'][index]}','{WAREHOUSE}','{actor}');",
                    f"INSERT INTO sasms.goods_receipt_items(id,receipt_id,purchase_order_id,purchase_order_item_id,product_id,quantity) VALUES('{ids['receipt_item'][index]}','{ids['receipt'][index]}','{ids['po'][index]}','{ids['po_item'][index]}','{PRODUCT}',20);"]
            self.run("concurrent import independent PO fixtures", sql="\n".join(statements+["COMMIT;"]))
            for case in ("same_serial","same_imei","same_request_key"):
                barrier = threading.Barrier(2)
                request_keys = [str(uuid.uuid4()),str(uuid.uuid4())]
                receipt_items = ids['receipt_item'][:]
                serial_values = [f"S1-RACE-{suffix}-{case.upper()}-{i}" for i in range(2)]
                imei_values = [None,None]
                if case == "same_serial":
                    serial_values[1] = serial_values[0].lower()
                elif case == "same_imei":
                    value = "88"+str(uuid.uuid4().int)[-13:]
                    imei_values = [value,value]
                else:
                    request_keys[1] = request_keys[0]
                    receipt_items[1] = receipt_items[0]
                    serial_values[1] = serial_values[0].lower()
                keys += request_keys
                serials += [value.upper() for value in serial_values]
                def worker(index):
                    payload = json.dumps([{"serial":serial_values[index],"imei":imei_values[index]}],separators=(",",":"))
                    barrier.wait(timeout=10)
                    start = time.monotonic()
                    result = self.invoke(sql=f"BEGIN; SET LOCAL lock_timeout='10s'; SET LOCAL ROLE sasms_app; SELECT to_json(sasms.receive_device_batch('{receipt_items[index]}','{request_keys[index]}','{payload}'::jsonb,'{actor}')); SELECT pg_sleep(0.5); COMMIT;",timeout=20)
                    return result,round(time.monotonic()-start,4)
                with ThreadPoolExecutor(max_workers=2) as pool:
                    results = list(pool.map(worker,range(2)))
                successes = []
                for index,(result,elapsed) in enumerate(results):
                    self.log(f"\n=== {case} independent session {index+1}: exit {result.returncode}, seconds {elapsed} ===\n{result.stdout}{result.stderr}".rstrip())
                    if result.returncode == 0:
                        successes.append(json.loads(next(line for line in result.stdout.splitlines() if line.startswith("["))))
                    elif case == "same_request_key" or "23505" not in result.stderr:
                        raise RuntimeError(f"Unexpected concurrency rejection in {case}")
                expected = 2 if case == "same_request_key" else 1
                if len(successes) != expected:
                    raise RuntimeError(f"Wrong successful session count in {case}")
                if expected == 2 and successes[0] != successes[1]:
                    raise RuntimeError("Concurrent equivalent request-key retry returned different devices")
                device = str(uuid.UUID(successes[0][0]))
                output,_ = self.run(case+" committed invariants",sql=f"""
SELECT json_build_object(
 'devices',(SELECT count(*) FROM sasms.devices WHERE serial IN({','.join(repr(value.upper()) for value in serial_values)})),
 'batches',(SELECT count(*) FROM sasms.inventory_import_batches WHERE request_key IN({uuid_list(request_keys)})),
 'receive_movements',(SELECT count(*) FROM sasms.inventory_movements WHERE device_id='{device}' AND kind='RECEIVE'),
 'audits',(SELECT count(*) FROM sasms.audit_logs WHERE entity_id='{device}' AND action='inventory.receive'),
 'outbox_events',(SELECT count(*) FROM sasms.outbox_events WHERE aggregate_id='{device}' AND event_type='DeviceReceived'));
""")
                invariants=json.loads(output)
                if any(value != 1 for value in invariants.values()):
                    raise RuntimeError(f"Duplicate committed side effect in {case}: {invariants}")
                cases.append({"case":case,"independent_sessions":2,"successful_calls":expected,
                    "expected_unique_rejections":2-expected,"distinct_purchase_orders":case!="same_request_key",
                    "committed_invariants":invariants})
            self.summary["concurrency"] = cases
        finally:
            if keys:
                self.run("concurrency exact UUID fixture cleanup",sql=f"""
BEGIN;
DELETE FROM sasms.inventory_import_batches WHERE request_key IN({uuid_list(keys)});
DELETE FROM sasms.inventory_movements WHERE actor_id='{actor}';
DELETE FROM sasms.audit_logs WHERE actor_id='{actor}';
DELETE FROM sasms.outbox_events WHERE aggregate_id IN(SELECT id FROM sasms.devices WHERE receipt_item_id IN({uuid_list(ids['receipt_item'])}));
DELETE FROM sasms.devices WHERE receipt_item_id IN({uuid_list(ids['receipt_item'])});
DELETE FROM sasms.goods_receipt_items WHERE id IN({uuid_list(ids['receipt_item'])});
DELETE FROM sasms.goods_receipts WHERE id IN({uuid_list(ids['receipt'])});
DELETE FROM sasms.purchase_order_items WHERE id IN({uuid_list(ids['po_item'])});
DELETE FROM sasms.purchase_orders WHERE id IN({uuid_list(ids['po'])});
DELETE FROM sasms.user_roles WHERE user_id='{actor}';
DELETE FROM sasms.users WHERE id='{actor}';
COMMIT;
""")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--psql", default="psql")
    parser.add_argument("--dsn", default=os.environ.get("DATABASE_URL"))
    parser.add_argument("--apply", action="store_true", help="Bootstrap a NEW isolated DB and run frozen Sprint0 regression before upgrade")
    parser.add_argument("--evidence-dir", type=Path, default=ROOT/"database/evidence/sprint1")
    args = parser.parse_args()
    validator = Sprint1Validator(args)
    try:
        validator.validate()
    except Exception as error:
        message = validator.clean(str(error))
        validator.summary["status"] = "failed"
        validator.summary["error"] = message
        validator.log("FAILED: "+message)
        print("Sprint1 validation failed: "+message, file=sys.stderr)
        return 1
    finally:
        validator.save()
    print(f"Sprint1 validation passed: {validator.summary['assertions']} assertions; "
          f"migration safety and concurrent Serial/IMEI/import-key checks; evidence: {args.evidence_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
