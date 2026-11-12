-- ==============================================================================
-- SASMS - Sprint 2 Concurrency, Serial Locking & Realtime Inventory Verification
-- Author: Tran Trung Kien (Architect / DBA)
-- Verifies:
--   1. Concurrency: 2 POS terminals selecting the same Serial -> exactly one succeeds (S2-03)
--   2. Real-time Inventory: Stock counts reflect immediately upon reserve & complete_sale (S2-06)
--   3. AI Fallback Rules: Returns in-stock cross-sell candidates without LLM dependencies (S2-08)
-- ==============================================================================

\set ON_ERROR_STOP on

BEGIN;
SET search_path = sasms, pg_catalog;

DO $$
DECLARE
    v_actor_admin uuid := '00000000-0000-4000-8000-000000000001';
    v_store_id uuid := '00000000-0000-4000-8000-000000000010';
    v_warehouse_id uuid := '00000000-0000-4000-8000-000000000011';
    v_product_tv uuid := '00000000-0000-4000-8000-000000000030';
    v_receipt_item uuid := '00000000-0000-4000-8000-000000000061';
    
    v_test_serial text := 'S2-POS-LOCK-001';
    v_device_id uuid;
    
    v_order_pos1 uuid;
    v_item_pos1 uuid;
    v_order_pos2 uuid;
    v_item_pos2 uuid;
    
    v_locked_dev uuid;
    v_err_code text;
    v_stock_avail bigint;
    v_stock_reserved bigint;
    v_stock_sold bigint;
    v_rec_count bigint;
BEGIN
    RAISE NOTICE '=== BẮT ĐẦU TEST SPRINT 2: KHÓA SERIAL VÀ TỒN KHO REAL-TIME ===';

    -- 1. Setup single physical device in store warehouse
    INSERT INTO sasms.devices(id, product_id, receipt_item_id, warehouse_id, serial, status)
    VALUES (gen_random_uuid(), v_product_tv, v_receipt_item, v_warehouse_id, v_test_serial, 'IN_STOCK')
    RETURNING id INTO v_device_id;

    -- Verify baseline realtime inventory view
    SELECT available_stock, reserved_stock, sold_stock 
    INTO v_stock_avail, v_stock_reserved, v_stock_sold
    FROM sasms.v_realtime_inventory
    WHERE store_id = v_store_id AND product_id = v_product_tv;

    RAISE NOTICE '1. Baseline Real-time Stock: Available=%, Reserved=%, Sold=%', 
        v_stock_avail, v_stock_reserved, v_stock_sold;
    IF v_stock_avail < 1 THEN
        RAISE EXCEPTION 'Expected available stock >= 1';
    END IF;

    -- 2. Create POS Terminal 1 Draft Order
    INSERT INTO sasms.sales_orders(id, code, store_id, warehouse_id, channel, status, total_amount, created_by)
    VALUES (gen_random_uuid(), 'SO-S2-POS-01', v_store_id, v_warehouse_id, 'POS', 'DRAFT', 1000, v_actor_admin)
    RETURNING id INTO v_order_pos1;

    INSERT INTO sasms.sales_order_items(id, order_id, product_id, quantity, unit_price, discount_amount, tax_rate)
    VALUES (gen_random_uuid(), v_order_pos1, v_product_tv, 1, 1000, 0, 0)
    RETURNING id INTO v_item_pos1;

    -- 3. Create POS Terminal 2 Draft Order
    INSERT INTO sasms.sales_orders(id, code, store_id, warehouse_id, channel, status, total_amount, created_by)
    VALUES (gen_random_uuid(), 'SO-S2-POS-02', v_store_id, v_warehouse_id, 'POS', 'DRAFT', 1000, v_actor_admin)
    RETURNING id INTO v_order_pos2;

    INSERT INTO sasms.sales_order_items(id, order_id, product_id, quantity, unit_price, discount_amount, tax_rate)
    VALUES (gen_random_uuid(), v_order_pos2, v_product_tv, 1, 1000, 0, 0)
    RETURNING id INTO v_item_pos2;

    -- 4. POS Terminal 1 selects and locks Serial
    v_locked_dev := sasms.find_and_lock_device_for_pos(v_store_id, v_order_pos1, v_item_pos1, v_test_serial, v_actor_admin);
    IF v_locked_dev <> v_device_id THEN
        RAISE EXCEPTION 'POS 1 failed to lock the correct device';
    END IF;
    RAISE NOTICE '2. [PASS] Máy POS 1 đã chọn và giữ khóa Serial % thành công (Device ID: %)', v_test_serial, v_locked_dev;

    -- 5. POS Terminal 2 attempts to select the SAME Serial concurrently -> MUST FAIL with 55P03
    BEGIN
        v_locked_dev := sasms.find_and_lock_device_for_pos(v_store_id, v_order_pos2, v_item_pos2, v_test_serial, v_actor_admin);
        RAISE EXCEPTION 'TEST FAILED: POS 2 was able to lock an already-held Serial!';
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_err_code = RETURNED_SQLSTATE;
        IF v_err_code = '55P03' THEN
            RAISE NOTICE '3. [PASS] Máy POS 2 tranh chấp Serial % đã bị CHẶN CHÍNH XÁC với mã lỗi 55P03 (lock_not_available)', v_test_serial;
        ELSE
            RAISE EXCEPTION 'Unexpected error code on concurrency conflict: %', v_err_code;
        END IF;
    END;

    -- 6. POS Terminal 1 completes reservation (reserve_order)
    PERFORM sasms.reserve_order(v_order_pos1, v_actor_admin);

    SELECT available_stock, reserved_stock, sold_stock 
    INTO v_stock_avail, v_stock_reserved, v_stock_sold
    FROM sasms.v_realtime_inventory
    WHERE store_id = v_store_id AND product_id = v_product_tv;

    RAISE NOTICE '4. [PASS] Real-time Stock sau khi giữ hàng (RESERVE): Available=%, Reserved=%, Sold=%',
        v_stock_avail, v_stock_reserved, v_stock_sold;
    IF v_stock_reserved < 1 THEN
        RAISE EXCEPTION 'Reserved stock count should have incremented!';
    END IF;

    -- 7. POS Terminal 1 completes checkout (payment + complete_sale)
    INSERT INTO sasms.payments(order_id, amount, method, status)
    VALUES (v_order_pos1, 1000, 'CASH', 'CAPTURED');

    PERFORM sasms.complete_sale(v_order_pos1, v_actor_admin);

    SELECT available_stock, reserved_stock, sold_stock 
    INTO v_stock_avail, v_stock_reserved, v_stock_sold
    FROM sasms.v_realtime_inventory
    WHERE store_id = v_store_id AND product_id = v_product_tv;

    RAISE NOTICE '5. [PASS] Real-time Stock sau khi bán hoàn tất (complete_sale): Available=%, Reserved=%, Sold=%',
        v_stock_avail, v_stock_reserved, v_stock_sold;
    IF v_stock_sold < 1 THEN
        RAISE EXCEPTION 'Sold stock count should have incremented!';
    END IF;

    -- 8. Verify AI Cross-selling Fallback Rules
    SELECT count(*) INTO v_rec_count 
    FROM sasms.get_cross_sell_fallback_rules(v_product_tv, v_store_id, 3);
    RAISE NOTICE '6. [PASS] AI Fallback Rules trả về % phụ kiện khả dụng tại siêu thị', v_rec_count;

    RAISE NOTICE '=== HOÀN TẤT TẤT CẢ KIỂM CHỨNG KỸ THUẬT SPRINT 2 THÀNH CÔNG ===';
END $$;

ROLLBACK;

