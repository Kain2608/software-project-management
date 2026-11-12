-- ==============================================================================
-- SASMS - Sprint 2 Demo Script: POS Checkout, Serial Locking & Realtime Stock
-- Author: Tran Trung Kien (Architect / DBA)
-- Safe to run in psql / pgAdmin - executes inside a transaction and rolls back
-- ==============================================================================

\set ON_ERROR_STOP on

BEGIN;
SET search_path = sasms, pg_catalog;

-- 1. Xem tồn kho thời gian thực tại Siêu thị 1 (Store 00000000-0000-4000-8000-000000000010)
SELECT 
    sku,
    product_name,
    price,
    total_physical_stock AS tong_ton_vat_ly,
    available_stock AS ton_kha_dung,
    reserved_stock AS dang_giu_cho,
    sold_stock AS da_xuat_ban
FROM sasms.v_realtime_inventory
WHERE store_id = '00000000-0000-4000-8000-000000000010'
ORDER BY total_physical_stock DESC;

-- 2. Demo gợi ý phụ kiện bán kèm (AI Cross-selling Fallback Rule) khi chọn Tivi Sony 55 inch
SELECT * FROM sasms.get_cross_sell_fallback_rules(
    '00000000-0000-4000-8000-000000000030', -- Tivi Sony
    '00000000-0000-4000-8000-000000000010', -- Siêu thị 1
    5
);

ROLLBACK;

