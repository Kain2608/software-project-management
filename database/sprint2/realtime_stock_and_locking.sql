-- ==============================================================================
-- SASMS - Sprint 2 Database Extensions: Real-time Inventory & POS Serial Locking
-- Author: Tran Trung Kien (Architect / DBA)
-- Tasks: S2-03 (TSK-02-02: POS Serial Locking API), S2-06 (TSK-02-05: Realtime Stock API)
-- Supports: S2-08 (AI Cross-selling Fallback Rules), M3 & M4 Quality Gates
-- ==============================================================================

-- Fail the caller and roll back the complete extension on any SQL/ACL error.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL search_path = sasms, pg_catalog;

-- 1. Indexes supporting POS queries and inventory tracking (benchmark separately)
CREATE INDEX IF NOT EXISTS idx_devices_warehouse_product_status 
    ON sasms.devices(warehouse_id, product_id, status);

CREATE INDEX IF NOT EXISTS idx_devices_serial_warehouse 
    ON sasms.devices(serial, warehouse_id);

CREATE INDEX IF NOT EXISTS idx_order_devices_order_active 
    ON sasms.order_devices(order_id) WHERE active;

-- 2. Real-time Inventory View: aggregated stock per store & warehouse
CREATE OR REPLACE VIEW sasms.v_realtime_inventory AS
SELECT 
    w.store_id,
    s.code AS store_code,
    s.name AS store_name,
    w.id AS warehouse_id,
    w.code AS warehouse_code,
    w.name AS warehouse_name,
    p.id AS product_id,
    p.sku,
    p.name AS product_name,
    p.list_price AS price,
    COUNT(d.id) AS total_physical_stock,
    COUNT(d.id) FILTER (WHERE d.status = 'IN_STOCK') AS available_stock,
    COUNT(d.id) FILTER (WHERE d.status = 'RESERVED') AS reserved_stock,
    COUNT(d.id) FILTER (WHERE d.status = 'SOLD') AS sold_stock
FROM sasms.products p
CROSS JOIN sasms.warehouses w
JOIN sasms.stores s ON s.id = w.store_id
LEFT JOIN sasms.devices d ON d.product_id = p.id AND d.warehouse_id = w.id
WHERE p.is_active AND s.is_active
GROUP BY w.store_id, s.code, s.name, w.id, w.code, w.name, p.id, p.sku, p.name, p.list_price;

-- 3. Stored Function: Find and lock a serial for POS checkout
-- Enforces concurrency protection: if 2 POS terminals pick the same serial,
-- the first one locks it via SELECT ... FOR UPDATE NOWAIT, the second is rejected.
-- p_actor_id is trusted backend identity context, as in Sprint 1; it is not a
-- credential. Only trusted backend logins may inherit sasms_app.
CREATE OR REPLACE FUNCTION sasms.find_and_lock_device_for_pos(
    p_store_id uuid,
    p_order_id uuid,
    p_order_item_id uuid,
    p_serial text,
    p_actor_id uuid
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, sasms AS $$
DECLARE
    v_order sasms.sales_orders;
    v_device sasms.devices;
    v_order_item sasms.sales_order_items;
    v_clean_serial text;
BEGIN
    v_clean_serial := upper(btrim(p_serial));

    -- Verify actor authorization
    PERFORM sasms.assert_permission(p_actor_id, 'sales.checkout', p_store_id);

    -- Verify and lock the order header
    SELECT * INTO STRICT v_order 
    FROM sasms.sales_orders 
    WHERE id = p_order_id FOR UPDATE;

    IF v_order.status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Order % is not in DRAFT status (current: %)', p_order_id, v_order.status
            USING ERRCODE = '23514';
    END IF;

    IF v_order.store_id IS DISTINCT FROM p_store_id THEN
        RAISE EXCEPTION 'Order store % does not match POS store %', v_order.store_id, p_store_id
            USING ERRCODE = '23514';
    END IF;

    -- The existing draft lease is anchored to the order creation time, not the
    -- latest scan. Do not let an old cart acquire a lease that already expired.
    IF v_order.created_at <= statement_timestamp() - interval '15 minutes' THEN
        RAISE EXCEPTION 'Draft order % has expired; create a new POS cart', p_order_id
            USING ERRCODE = '23514';
    END IF;

    -- Verify order item exists and belongs to the order
    SELECT * INTO STRICT v_order_item
    FROM sasms.sales_order_items
    WHERE id = p_order_item_id AND order_id = p_order_id;

    -- Find and lock the target device by serial in the order warehouse
    SELECT * INTO v_device
    FROM sasms.devices
    WHERE serial = v_clean_serial
      AND warehouse_id = v_order.warehouse_id
    FOR UPDATE NOWAIT;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Serial % not found in warehouse % of store %', v_clean_serial, v_order.warehouse_id, p_store_id
            USING ERRCODE = 'P0002';
    END IF;

    -- Ensure product match
    IF v_device.product_id <> v_order_item.product_id THEN
        RAISE EXCEPTION 'Serial % belongs to product %, expected product %',
            v_clean_serial, v_device.product_id, v_order_item.product_id
            USING ERRCODE = '23514';
    END IF;

    -- Concurrency check: device must be IN_STOCK and not currently reserved
    IF v_device.status <> 'IN_STOCK' OR v_device.reserved_order_id IS NOT NULL THEN
        RAISE EXCEPTION 'Device with serial % is not available for sale (status: %)',
            v_clean_serial, v_device.status
            USING ERRCODE = '23514';
    END IF;

    -- Check if another active cart has attached this device
    IF EXISTS (
        SELECT 1 FROM sasms.order_devices od
        JOIN sasms.sales_orders so ON so.id = od.order_id
        WHERE od.device_id = v_device.id 
          AND od.active = true 
          AND od.order_id <> p_order_id
          AND so.status = 'DRAFT'
          AND so.created_at > (statement_timestamp() - interval '15 minutes')
    ) THEN
        RAISE EXCEPTION 'Device with serial % is temporarily locked by another active POS terminal',
            v_clean_serial
            USING ERRCODE = '55P03'; -- lock_not_available
    END IF;

    -- Link device to order line
    INSERT INTO sasms.order_devices(order_item_id, order_id, product_id, device_id, active)
    VALUES (v_order_item.id, p_order_id, v_device.product_id, v_device.id, true)
    ON CONFLICT (order_id, device_id) 
    DO UPDATE SET active = true, order_item_id = v_order_item.id;

    RETURN v_device.id;
END;
$$;

-- 4. Stored Function: Release locked device from POS cart
CREATE OR REPLACE FUNCTION sasms.release_device_lock(
    p_order_id uuid,
    p_device_id uuid,
    p_actor_id uuid
) RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, sasms AS $$
DECLARE
    v_order sasms.sales_orders;
    v_released boolean;
BEGIN
    SELECT * INTO STRICT v_order 
    FROM sasms.sales_orders 
    WHERE id = p_order_id FOR UPDATE;

    -- Derive the scope from the persisted order; never trust a caller-supplied
    -- store. This also rejects NULL, inactive and unauthorized actor IDs.
    PERFORM sasms.assert_permission(p_actor_id, 'sales.checkout', v_order.store_id);

    IF v_order.status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Cannot release device from non-draft order %', p_order_id
            USING ERRCODE = '23514';
    END IF;

    -- Use the same header -> device lock order as selection/reservation. The
    -- device row serializes release against another terminal selecting it.
    PERFORM d.id
    FROM sasms.devices d
    JOIN sasms.order_devices od ON od.device_id = d.id
    WHERE od.order_id = p_order_id AND od.device_id = p_device_id AND od.active
    FOR UPDATE OF d NOWAIT;

    UPDATE sasms.order_devices
    SET active = false 
    WHERE order_id = p_order_id AND device_id = p_device_id AND active;
    v_released := FOUND;

    RETURN v_released;
END;
$$;

-- 5. Stored Function: AI Recommendation Fallback Rules (S2-08 DB Support)
-- Returns in-stock product candidates; compatibility and promotions are not inferred.
CREATE OR REPLACE FUNCTION sasms.get_cross_sell_fallback_rules(
    p_product_id uuid,
    p_store_id uuid,
    p_limit integer DEFAULT 5
) RETURNS TABLE (
    recommended_product_id uuid,
    sku text,
    product_name text,
    price numeric(14,2),
    available_stock bigint,
    recommendation_reason text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, sasms AS $$
DECLARE
    v_base_product sasms.products;
BEGIN
    SELECT * INTO STRICT v_base_product 
    FROM sasms.products 
    WHERE id = p_product_id;

    RETURN QUERY
    SELECT 
        v.product_id,
        v.sku,
        v.product_name,
        v.price,
        v.available_stock,
        CASE 
            WHEN p.category_id = v_base_product.category_id THEN 'Sản phẩm cùng danh mục còn tồn kho'
            WHEN p.brand_id = v_base_product.brand_id THEN 'Sản phẩm cùng thương hiệu còn tồn kho'
            ELSE 'Sản phẩm còn tồn kho tại cửa hàng'
        END AS recommendation_reason
    FROM sasms.v_realtime_inventory v
    JOIN sasms.products p ON p.id = v.product_id
    WHERE v.store_id = p_store_id
      AND v.product_id <> p_product_id
      AND v.available_stock > 0
    ORDER BY 
        (p.brand_id = v_base_product.brand_id) DESC,
        v.available_stock DESC,
        v.price ASC
    LIMIT p_limit;
END;
$$;

-- SECURITY DEFINER entry points must never inherit PUBLIC EXECUTE, even when
-- installed by a DBA other than the baseline owner (default ACLs are per owner).
REVOKE ALL ON sasms.v_realtime_inventory FROM PUBLIC;
REVOKE ALL ON FUNCTION
    sasms.find_and_lock_device_for_pos(uuid,uuid,uuid,text,uuid),
    sasms.release_device_lock(uuid,uuid,uuid),
    sasms.get_cross_sell_fallback_rules(uuid,uuid,integer)
FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sasms_app') THEN
        GRANT SELECT ON sasms.v_realtime_inventory TO sasms_app;
        GRANT EXECUTE ON FUNCTION
            sasms.find_and_lock_device_for_pos(uuid,uuid,uuid,text,uuid),
            sasms.release_device_lock(uuid,uuid,uuid),
            sasms.get_cross_sell_fallback_rules(uuid,uuid,integer)
        TO sasms_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sasms_reader') THEN
        REVOKE ALL ON sasms.v_realtime_inventory FROM sasms_reader;
        REVOKE ALL ON FUNCTION
            sasms.find_and_lock_device_for_pos(uuid,uuid,uuid,text,uuid),
            sasms.release_device_lock(uuid,uuid,uuid),
            sasms.get_cross_sell_fallback_rules(uuid,uuid,integer)
        FROM sasms_reader;
    END IF;
END;
$$;

COMMIT;

