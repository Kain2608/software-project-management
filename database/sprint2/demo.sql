-- SASMS Sprint 2 read-only SQL demo against synthetic baseline data.
-- Run with psql as owner, or a trusted backend login in sasms_app.
-- This does not exercise POS concurrency, checkout, printing or a live AI provider.
\set ON_ERROR_STOP on

BEGIN READ ONLY;
SET LOCAL search_path = pg_catalog, sasms;

-- Baseline synthetic store DEMO-HN; total_physical_stock counts device records,
-- including SOLD. It is not a count of goods physically present in the warehouse.
SELECT
    warehouse_code,
    sku,
    product_name,
    price,
    total_physical_stock AS tong_ban_ghi_thiet_bi,
    available_stock AS in_stock,
    reserved_stock AS reserved,
    sold_stock AS sold
FROM sasms.v_realtime_inventory
WHERE store_id = '00000000-0000-4000-8000-000000000010'
ORDER BY warehouse_code, sku;

-- Source product DEMO-TV-55 is a synthetic DEMO product, not a Sony catalog item.
-- An empty result is valid if no other candidate currently has IN_STOCK devices.
-- Candidate ranking alone does not establish accessory compatibility.
SELECT * FROM sasms.get_cross_sell_fallback_rules(
    '00000000-0000-4000-8000-000000000030',
    '00000000-0000-4000-8000-000000000010',
    5
);

ROLLBACK;
