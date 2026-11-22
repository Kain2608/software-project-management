#!/usr/bin/env python3
"""Reconcile the Sprint 3/4 AI SQL against a real PostgreSQL database (S3-03, S4-03, and the S2-08 SQL).

What it proves, on an isolated database that already has database/schema.sql + migrations applied:
  * the exact SQL strings used by the Java code (extracted from the source, not copied) run and return
    the values expected from a hand-built fixture;
  * AI cross-sell scope: warehouse/store membership, per-warehouse stock, inactive/out-of-stock filtering,
    ai.use store scope through sasms.has_permission;
  * dashboard AI attribution: PAID-only, half-open range, store isolation, earliest recommendation wins,
    each order line counted once;
  * sasms_app may INSERT into ai_recommendations (and cannot UPDATE it).
All fixture rows live in one transaction that is ROLLED BACK; nothing persists. Uses only the stdlib + psql.

    python scripts/validate_ai_sprint34.py --psql <path-to-psql> --dsn "host=... dbname=sasms_s4_x user=..." \
        --evidence-dir "Sprint 4/AI/evidence"
(or use PG* environment variables). Output: ds4-ai-dashboard-reconciliation.json
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "backend/src/main/java/com/smartelectronicsupermarket"
SOURCES = [
    JAVA / "reporting/data/JdbcStoreDashboardDataAccess.java",
    JAVA / "ai/data/JdbcAiCrossSellDataAccess.java",
    JAVA / "ai/data/JdbcAiRecommendationRecorder.java",
    JAVA / "ai/summary/JdbcSpecSummaryDataAccess.java",
]


def java_sql() -> dict[str, str]:
    found: dict[str, str] = {}
    pattern = re.compile(r'static final String (\w+)\s*=\s*"""\s*\n(.*?)"""', re.S)
    for path in SOURCES:
        for name, body in pattern.findall(path.read_text(encoding="utf-8")):
            found[name] = body
    return found


def lit(value) -> str:
    if isinstance(value, int):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def bind(sql: str, params: list) -> str:
    parts = sql.split("?")
    if len(parts) - 1 != len(params):
        raise ValueError(f"expected {len(parts) - 1} parameters, got {len(params)}")
    out = [parts[0]]
    for value, tail in zip(params, parts[1:]):
        out.append(lit(value))
        out.append(tail)
    return "".join(out)


def text_array(skus: list[str]) -> str:
    return "{" + ",".join('"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"' for s in skus) + "}"


def uid(label: str) -> str:
    """Deterministic UUID text computed by PostgreSQL (md5) so the script and SQL agree."""
    import hashlib
    h = hashlib.md5(f"dsai-{label}".encode()).hexdigest()
    return f"{h[:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:]}"


SEED_STORE = "00000000-0000-4000-8000-000000000010"
SEED_WH = "00000000-0000-4000-8000-000000000011"
SEED_BRAND = "00000000-0000-4000-8000-000000000020"
SEED_SUPPLIER = "00000000-0000-4000-8000-000000000040"
SEED_ACTOR = "00000000-0000-4000-8000-000000000001"

STORE_A, WH_A = SEED_STORE, SEED_WH
STORE_B, WH_B = uid("store-b"), uid("wh-b")
U_CASH_A, U_CASH_B, U_GLOBAL = uid("u-cash-a"), uid("u-cash-b"), uid("u-global")
CAT_PHONE, CAT_ACC = uid("cat-phone"), uid("cat-acc")
PRODUCTS = {  # key: (sku, name, category, price, active)
    "PHONE": ("DSAI-PHONE", "Dien thoai DSAI", CAT_PHONE, "20000000.00", True),
    "CASE": ("DSAI-CASE", "Op lung DSAI", CAT_ACC, "450000.00", True),
    "CHARGER": ("DSAI-CHARGER", "Cu sac DSAI", CAT_ACC, "590000.00", True),
    "CABLE": ("DSAI-CABLE", "Cap DSAI", CAT_ACC, "180000.00", True),
    "OFF": ("DSAI-OFF", "Hang ngung ban DSAI", CAT_ACC, "99000.00", False),
}
PID = {k: uid("p-" + k) for k in PRODUCTS}
STOCK = [  # (warehouse, product key, devices)
    (WH_A, "CASE", 3), (WH_A, "CHARGER", 2), (WH_A, "PHONE", 1), (WH_A, "OFF", 1), (WH_B, "CASE", 9),
]
SPEC_JSON = '{"screen_inches":6.1,"display":"OLED","note":"Màn hình đẹp \\"tuyệt\\""}'
RANGE_FROM, RANGE_TO = "2026-11-01T00:00:00Z", "2026-12-01T00:00:00Z"
# order: (key, store, wh, created, paid, [(product, qty)], status)
ORDERS = [
    ("O1", STORE_A, WH_A, "2026-11-10 09:00+00", "2026-11-10 10:00+00", [("PHONE", 1), ("CASE", 1)], "PAID"),
    ("O2", STORE_A, WH_A, "2026-11-12 09:00+00", "2026-11-12 10:00+00", [("PHONE", 1), ("CASE", 1), ("CHARGER", 1)], "PAID"),
    ("O3", STORE_A, WH_A, "2026-11-15 09:00+00", "2026-11-15 10:00+00", [("PHONE", 1), ("CASE", 1)], "PAID"),
    ("O4", STORE_A, WH_A, "2026-11-20 09:00+00", "2026-11-20 10:00+00", [("CASE", 1)], "PAID"),
    ("O5", STORE_A, WH_A, "2026-11-21 09:00+00", None, [("PHONE", 1), ("CASE", 1)], "DRAFT"),
    ("O6", STORE_A, WH_A, "2026-12-02 09:00+00", "2026-12-02 10:00+00", [("PHONE", 1), ("CASE", 1)], "PAID"),
    ("O7", STORE_B, WH_B, "2026-11-11 09:00+00", "2026-11-11 10:00+00", [("PHONE", 1), ("CASE", 1)], "PAID"),
]
# recommendations: (source product, recommended product, source, created_at)
RECS = [
    ("PHONE", "CASE", "LLM", "2026-11-10 09:30+00"),      # O1 -> attributed (LLM)
    ("PHONE", "CASE", "CACHE", "2026-11-12 09:20+00"),    # O2 -> earliest wins (CACHE)
    ("PHONE", "CASE", "LLM", "2026-11-12 09:40+00"),      # O2 duplicate, later -> ignored
    ("PHONE", "CHARGER", "RULE", "2026-11-12 09:30+00"),  # O2 -> attributed (RULE)
    ("PHONE", "CASE", "LLM", "2026-11-15 08:00+00"),      # O3: before order existed -> NOT attributed
    ("PHONE", "CASE", "LLM", "2026-11-20 09:30+00"),      # O4: PHONE not in order -> NOT attributed
    ("PHONE", "CASE", "LLM", "2026-12-02 09:30+00"),      # O6: outside range
    ("PHONE", "CASE", "LLM", "2026-11-11 09:30+00"),      # O7: other store
]


def fixture_sql() -> str:
    s = []
    a = s.append
    a("BEGIN;")
    a("SET search_path = sasms, pg_catalog;")
    a(f"INSERT INTO stores(id,code,name,address) VALUES ('{STORE_B}','DSAI-B','Store B','x');")
    a(f"INSERT INTO warehouses(id,store_id,code,name) VALUES ('{WH_B}','{STORE_B}','DSAI-WH-B','Kho B');")
    for u, mail in ((U_CASH_A, "cash.a"), (U_CASH_B, "cash.b"), (U_GLOBAL, "global")):
        a(f"INSERT INTO users(id,email,full_name,is_active) VALUES ('{u}','dsai.{mail}@example.invalid','DSAI {mail}',true);")
    a(f"INSERT INTO user_roles(user_id,role_code,store_id) VALUES ('{U_CASH_A}','CASHIER','{STORE_A}'),"
      f"('{U_CASH_B}','CASHIER','{STORE_B}'),('{U_GLOBAL}','SALES_STAFF',NULL);")
    a(f"INSERT INTO categories(id,name) VALUES ('{CAT_PHONE}','SMARTPHONE'),('{CAT_ACC}','ACCESSORY');")
    for key, (sku, name, cat, price, active) in PRODUCTS.items():
        spec = f"$j${SPEC_JSON}$j$::jsonb" if key == "PHONE" else "'{}'::jsonb"
        a(f"INSERT INTO products(id,sku,brand_id,category_id,name,specifications,list_price,is_active) VALUES "
          f"('{PID[key]}','{sku}','{SEED_BRAND}','{cat}','{name}',{spec},{price},{str(active).lower()});")
    for wh, key, qty in STOCK:
        tag = f"{wh[:8]}-{key}"
        po, poi, gr, gri = (uid(f"{p}-{tag}") for p in ("po", "poi", "gr", "gri"))
        a(f"INSERT INTO purchase_orders(id,code,supplier_id,warehouse_id,created_by) VALUES "
          f"('{po}','DSAI-PO-{tag}','{SEED_SUPPLIER}','{wh}','{SEED_ACTOR}');")
        a(f"INSERT INTO purchase_order_items(id,purchase_order_id,product_id,quantity,unit_cost) VALUES "
          f"('{poi}','{po}','{PID[key]}',{qty},50);")
        a(f"INSERT INTO goods_receipts(id,code,purchase_order_id,warehouse_id,received_by) VALUES "
          f"('{gr}','DSAI-GR-{tag}','{po}','{wh}','{SEED_ACTOR}');")
        a(f"INSERT INTO goods_receipt_items(id,receipt_id,purchase_order_id,purchase_order_item_id,product_id,quantity) "
          f"VALUES ('{gri}','{gr}','{po}','{poi}','{PID[key]}',{qty});")
        a(f"INSERT INTO devices(id,product_id,receipt_item_id,warehouse_id,serial,qr_token) "
          f"SELECT md5('dsai-dev-{tag}-'||i)::uuid,'{PID[key]}','{gri}','{wh}','DSAI-{tag}-'||i,md5('dsai-qr-{tag}-'||i)::uuid "
          f"FROM generate_series(1,{qty}) i;")
    for key, store, wh, created, paid, lines, status in ORDERS:
        oid = uid("o-" + key)
        a(f"INSERT INTO sales_orders(id,code,store_id,warehouse_id,channel,created_by,created_at) VALUES "
          f"('{oid}','DSAI-{key}','{store}','{wh}','POS','{SEED_ACTOR}','{created}');")
        total = 0
        for product, qty in lines:
            price = float(PRODUCTS[product][3])
            total += price * qty
            a(f"INSERT INTO sales_order_items(id,order_id,product_id,quantity,unit_price) VALUES "
              f"('{uid(f'oi-{key}-{product}')}','{oid}','{PID[product]}',{qty},{PRODUCTS[product][3]});")
        if status == "PAID":
            a(f"UPDATE sales_orders SET status='PAID',paid_at='{paid}',total_amount={total:.2f} WHERE id='{oid}';")
    for src, dst, source, created in RECS:
        a(f"INSERT INTO ai_recommendations(source_product_id,recommended_product_id,source,score,model_version,"
          f"created_at,expires_at) VALUES ('{PID[src]}','{PID[dst]}','{source}',1.0,'dsai-fixture','{created}',"
          f"timestamptz '{created}' + interval '1 hour');")
    return "\n".join(s) + "\n"


def probe(name: str, sql: str) -> str:
    return f"SELECT '@@{name}=' || coalesce((SELECT jsonb_agg(t) FROM ({sql}) t), '[]'::jsonb)::text;\n"


def build_script(q: dict[str, str]) -> str:
    out = [fixture_sql()]
    add = out.append
    sku_list = [PRODUCTS[k][0] for k in ("CASE", "CHARGER", "CABLE", "OFF", "PHONE")]
    inj = ['DSAI-CASE", "DSAI-PHONE', "x'; DROP TABLE sasms.products; --"]
    # --- permissions (S2-08 / S3-03 ai.use + store scope) ---
    for who, actor in (("cash_a", U_CASH_A), ("cash_b", U_CASH_B), ("global", U_GLOBAL)):
        for scope, store in (("store_a", STORE_A), ("store_b", STORE_B)):
            add(f"SELECT '@@perm_{who}_{scope}=' || sasms.has_permission('{actor}','ai.use','{store}')::text;\n")
        add(f"SELECT '@@perm_{who}_global=' || sasms.has_permission('{actor}','ai.use',NULL)::text;\n")
    add("SELECT '@@perm_admin_only_ai=' || coalesce((SELECT bool_or(permission_code='ai.use') "
        "FROM sasms.role_permissions WHERE role_code='ADMIN'),false)::text;\n")
    add(f"SELECT '@@perm_cash_a_reports=' || sasms.has_permission('{U_CASH_A}','reports.read','{STORE_A}')::text;\n")
    # --- S2-08 cross-sell SQL ---
    add(probe("wh_a_in_store_a", bind(q["WAREHOUSE_IN_STORE_SQL"], [WH_A, STORE_A])))
    add(probe("wh_b_in_store_a", bind(q["WAREHOUSE_IN_STORE_SQL"], [WH_B, STORE_A])))
    add(probe("product_ids", bind(q["PRODUCT_IDS_SQL"], [text_array([PRODUCTS["PHONE"][0], "NO-SUCH-SKU"])])))
    add(probe("sellable_wh_a", bind(q["SELLABLE_SQL"], [WH_A, text_array(sku_list)])))
    add(probe("sellable_wh_b", bind(q["SELLABLE_SQL"], [WH_B, text_array(sku_list)])))
    add(probe("sellable_injection", bind(q["SELLABLE_SQL"], [WH_A, text_array(inj)])))
    # --- S3-03 product retrieval ---
    add(probe("spec_product", bind(q["PRODUCT_SQL"], [PID["PHONE"]])))
    add(probe("spec_product_missing", bind(q["PRODUCT_SQL"], [uid("nope")])))
    # --- recorder INSERT as the runtime role, then it must not be able to UPDATE ---
    add("SET LOCAL ROLE sasms_app;\n")
    add(bind(q["INSERT_SQL"], [PID["PHONE"], PID["CASE"], "LLM", "0.9000", "dsai-recorder", 60]).rstrip() + ";\n")
    add("SELECT '@@recorder_insert_as_sasms_app=ok';\n")
    add("SAVEPOINT s;\n")
    add("DO $$ BEGIN\n BEGIN UPDATE sasms.ai_recommendations SET score=0 WHERE model_version='dsai-recorder';\n"
        " RAISE NOTICE '@@recorder_update_denied=false';\n EXCEPTION WHEN insufficient_privilege THEN\n"
        " RAISE NOTICE '@@recorder_update_denied=true'; END; END $$;\n")
    # --- S4-03 dashboard SQL, read as the runtime role ---
    sr = [STORE_A, RANGE_FROM, RANGE_TO]
    add(probe("dash_ai_store_a", bind(q["AI_SQL"], sr)))
    add(probe("dash_ai_store_b", bind(q["AI_SQL"], [STORE_B, RANGE_FROM, RANGE_TO])))
    add(probe("dash_summary_store_a", bind(q["SUMMARY_SQL"], [STORE_A, RANGE_FROM, RANGE_TO, STORE_A, STORE_A, RANGE_FROM, RANGE_TO])))
    add(probe("dash_top_store_a", bind(q["PRODUCTS_SQL"], sr + [5])))
    add("RESET ROLE;\nROLLBACK;\n")
    return "".join(out)


def run_psql(psql: str, dsn: str | None, script: str) -> tuple[dict[str, str], str]:
    cmd = [psql, "-X", "-q", "-A", "-t", "-P", "pager=off", "-v", "ON_ERROR_STOP=1"]
    if dsn:
        cmd += ["--dbname", dsn]
    env = dict(os.environ, PGCLIENTENCODING="UTF8")
    proc = subprocess.run(cmd, input=script, capture_output=True, text=True, encoding="utf-8",
                          errors="replace", env=env, timeout=180)
    text = proc.stdout + "\n" + proc.stderr
    if dsn:
        text = text.replace(dsn, "[REDACTED_DSN]")
    if proc.returncode:
        raise RuntimeError("psql failed:\n" + text[-3000:])
    values = {m.group(1): m.group(2).strip() for m in re.finditer(r"@@(\w+)=(.*)", text)}
    return values, text


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--psql", default="psql")
    parser.add_argument("--dsn", default=os.environ.get("DATABASE_URL"))
    parser.add_argument("--evidence-dir", type=Path, default=ROOT / "Sprint 4/AI/evidence")
    args = parser.parse_args()

    db, _ = run_psql(args.psql, args.dsn, "SELECT '@@db=' || current_database();\n"
                     "SELECT '@@ver=' || current_setting('server_version');\n"
                     "SELECT '@@tables=' || count(*) FROM pg_tables WHERE schemaname='sasms';\n")
    if not re.fullmatch(r"sasms_(s4|ai)_[a-z0-9_]+", db["db"]):
        raise SystemExit(f"refusing to run on database {db['db']!r}; use an isolated sasms_s4_* / sasms_ai_* database")
    q = java_sql()
    wanted = {"WAREHOUSE_IN_STORE_SQL", "PRODUCT_IDS_SQL", "SELLABLE_SQL", "PRODUCT_SQL", "INSERT_SQL",
              "AI_SQL", "SUMMARY_SQL", "PRODUCTS_SQL"}
    missing = wanted - set(q)
    if missing:
        raise SystemExit(f"SQL constants not found in Java sources: {sorted(missing)}")
    values, raw = run_psql(args.psql, args.dsn, build_script(q))

    def j(name):
        return json.loads(values[name])

    def only(rows, **where):
        return [r for r in rows if all(r.get(k) == v for k, v in where.items())]

    checks: list[dict] = []

    def check(name, expected, actual, note=""):
        checks.append({"check": name, "expected": expected, "actual": actual,
                       "result": "PASS" if expected == actual else "FAIL", "note": note})

    for who in ("cash_a", "cash_b", "global"):
        for scope in ("store_a", "store_b"):
            expect = (who == "global") or (who, scope) in (("cash_a", "store_a"), ("cash_b", "store_b"))
            check(f"has_permission ai.use: {who} @ {scope}", str(expect).lower(), values[f"perm_{who}_{scope}"])
        check(f"has_permission ai.use global (NULL store): {who}", str(who == "global").lower(),
              values[f"perm_{who}_global"], "store-scoped roles never satisfy a global check")
    check("cashier has no reports.read", "false", values["perm_cash_a_reports"])
    check("ADMIN role alone has no ai.use (V005/D34: duties need explicit business roles)", "false",
          values["perm_admin_only_ai"])
    check("warehouse A belongs to store A", True, j("wh_a_in_store_a")[0]["exists"])
    check("warehouse B does not belong to store A", False, j("wh_b_in_store_a")[0]["exists"])
    check("product ids by SKU (unknown SKU absent)", [PRODUCTS["PHONE"][0]],
          sorted(r["sku"] for r in j("product_ids")))
    wa = {r["sku"]: r for r in j("sellable_wh_a")}
    check("sellable in warehouse A: only active in-stock SKUs", ["DSAI-CASE", "DSAI-CHARGER", "DSAI-PHONE"], sorted(wa))
    check("stock is per warehouse (A=3, not 12)", 3, wa["DSAI-CASE"]["available"])
    check("price comes from products.list_price", 450000.0, float(wa["DSAI-CASE"]["list_price"]))
    check("category name returned for is_serialized", "SMARTPHONE", wa["DSAI-PHONE"]["category_name"])
    wb = {r["sku"]: r for r in j("sellable_wh_b")}
    check("warehouse B sees its own stock only", {"DSAI-CASE": 9}, {k: v["available"] for k, v in wb.items()})
    check("SKU injection attempt returns nothing and does not error", [], j("sellable_injection"))
    spec = j("spec_product")[0]
    check("spec summary retrieval returns brand/category/specs", ["SMARTPHONE", True],
          [spec["category_name"], spec["is_active"]])
    check("specifications JSON round-trips (UTF-8, quotes)", json.loads(SPEC_JSON), json.loads(spec["specifications"]))
    check("unknown product id returns no row", [], j("spec_product_missing"))
    check("sasms_app can INSERT ai_recommendations", "ok", values["recorder_insert_as_sasms_app"])
    check("sasms_app cannot UPDATE ai_recommendations", "true", values.get("recorder_update_denied",
          "true" if "recorder_update_denied=true" in raw else "false"))

    ai_a = j("dash_ai_store_a")[0]
    check("AI attribution store A: orders", 2, ai_a["orders"], "O1,O2 only; O3 (rec before order), O4 (no source line), O5 DRAFT, O6 out of range excluded")
    check("AI attribution store A: lines", 3, ai_a["items"])
    check("AI attribution store A: revenue", 1490000.0, float(ai_a["revenue"]), "450000 (O1 case) + 450000 (O2 case) + 590000 (O2 charger)")
    check("AI attribution store A: source LLM/CACHE/RULE", [1, 1, 1], [ai_a["llm"], ai_a["cache"], ai_a["rule"]],
          "O2 case has CACHE then LLM recs: earliest wins, line counted once")
    ai_b = j("dash_ai_store_b")[0]
    check("AI attribution store B is isolated", [1, 1, 450000.0], [ai_b["orders"], ai_b["items"], float(ai_b["revenue"])])
    sm = j("dash_summary_store_a")[0]
    check("gross PAID revenue store A in range", 62390000.0, float(sm["gross"]), "O1+O2+O3+O4; DRAFT/other month/other store excluded")
    check("PAID order count store A in range", 4, sm["paid_orders"])
    check("available devices store A (IN_STOCK, warehouse A)", 7, sm["available_devices"])
    check("inventory movement counters (fixture inserts no movements)", [0, 0, 0],
          [sm["received"], sm["sold"], sm["returned"]], "direct device inserts do not create movement rows")
    top = j("dash_top_store_a")
    check("top products by quantity (store A, range)", [["DSAI-CASE", 4], ["DSAI-PHONE", 3], ["DSAI-CHARGER", 1]],
          [[r["sku"], r["quantity"]] for r in top])

    passed = sum(c["result"] == "PASS" for c in checks)
    evidence = {
        "task": "S4-03 TSK-04-02 (also validates S2-08 and S3-03 SQL)",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "database": {"name": db["db"], "server_version": db["ver"], "sasms_tables": int(db["tables"])},
        "method": "SQL extracted from Java sources and run in one transaction that is rolled back; expected values hand-computed from the fixture",
        "fixture": {"orders": len(ORDERS), "recommendations": len(RECS), "stores": 2, "range": [RANGE_FROM, RANGE_TO]},
        "passed": passed, "total": len(checks), "checks": checks,
        "not_covered": ["PostgreSQL 16 (server used may differ; CI uses 16)", "HTTP/JWT layer", "Production data volume"],
    }
    args.evidence_dir.mkdir(parents=True, exist_ok=True)
    out = args.evidence_dir / "ds4-ai-dashboard-reconciliation.json"
    out.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{passed}/{len(checks)} checks passed on {db['db']} (PostgreSQL {db['ver']}) -> {out}")
    for c in checks:
        if c["result"] != "PASS":
            print("FAIL:", c["check"], "expected", c["expected"], "actual", c["actual"])
    return 0 if passed == len(checks) else 1


if __name__ == "__main__":
    sys.exit(main())
