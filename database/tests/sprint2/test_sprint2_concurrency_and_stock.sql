-- Transactional ACL, authorization and lifecycle regression only.
-- Actual independent POS sessions are exercised by scripts/validate_sprint2.py.
\set ON_ERROR_STOP on
BEGIN;
SET search_path = sasms, pg_catalog;

CREATE FUNCTION pg_temp.assert_true(label text, result boolean) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    IF result IS DISTINCT FROM true THEN RAISE EXCEPTION 'FAIL: %', label; END IF;
    RAISE NOTICE 'PASS: %', label;
END $$;
CREATE FUNCTION pg_temp.expect_state(label text, statement text, expected text) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE actual text;
BEGIN
    BEGIN EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS actual = RETURNED_SQLSTATE;
    END;
    IF actual IS DISTINCT FROM expected THEN
        RAISE EXCEPTION 'FAIL: % expected SQLSTATE %, received %', label, expected, actual;
    END IF;
    RAISE NOTICE 'PASS: % (SQLSTATE %)', label, actual;
END $$;

-- Owner creates synthetic fixtures; never reactivate the inactive seed operator.
DO $$
DECLARE
    actor uuid := gen_random_uuid(); denied uuid := gen_random_uuid();
    outsider uuid := gen_random_uuid(); inactive uuid := gen_random_uuid();
    other_store uuid := gen_random_uuid(); ord uuid := gen_random_uuid();
    item uuid := gen_random_uuid(); dev uuid := gen_random_uuid();
    accessory uuid := gen_random_uuid(); suffix text := gen_random_uuid()::text;
    old_order uuid := gen_random_uuid(); old_item uuid := gen_random_uuid();
    signature text;
BEGIN
    FOREACH signature IN ARRAY ARRAY[
        'sasms.find_and_lock_device_for_pos(uuid,uuid,uuid,text,uuid)',
        'sasms.release_device_lock(uuid,uuid,uuid)',
        'sasms.get_cross_sell_fallback_rules(uuid,uuid,integer)'
    ] LOOP
        PERFORM pg_temp.assert_true('app EXECUTE and reader denied: ' || signature,
            has_function_privilege('sasms_app',signature,'EXECUTE')
            AND NOT has_function_privilege('sasms_reader',signature,'EXECUTE'));
        PERFORM pg_temp.assert_true('PUBLIC EXECUTE denied: ' || signature,
            NOT EXISTS(SELECT 1 FROM pg_proc p,
                LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a
                WHERE p.oid=signature::regprocedure AND a.grantee=0 AND a.privilege_type='EXECUTE'));
    END LOOP;
    PERFORM pg_temp.assert_true('app SELECT on realtime view',
        has_table_privilege('sasms_app','sasms.v_realtime_inventory','SELECT'));
    INSERT INTO sasms.stores(id,code,name,address)
    VALUES(other_store,'S2-OTHER-'||suffix,'Synthetic other store','Synthetic address');
    INSERT INTO sasms.users(id,email,full_name,is_active) VALUES
        (actor,'s2-cashier-'||suffix||'@example.invalid','Synthetic cashier',true),
        (denied,'s2-sales-'||suffix||'@example.invalid','Synthetic salesperson',true),
        (outsider,'s2-outside-'||suffix||'@example.invalid','Synthetic other-store cashier',true),
        (inactive,'s2-inactive-'||suffix||'@example.invalid','Synthetic inactive cashier',false);
    INSERT INTO sasms.user_roles(user_id,role_code,store_id) VALUES
        (actor,'CASHIER','00000000-0000-4000-8000-000000000010'),
        (denied,'SALES','00000000-0000-4000-8000-000000000010'),
        (outsider,'CASHIER',other_store),
        (inactive,'CASHIER','00000000-0000-4000-8000-000000000010');
    INSERT INTO sasms.devices(id,product_id,receipt_item_id,warehouse_id,serial) VALUES
        (dev,'00000000-0000-4000-8000-000000000030','00000000-0000-4000-8000-000000000061',
         '00000000-0000-4000-8000-000000000011','S2-UNIT-TV-'||upper(suffix)),
        (accessory,'00000000-0000-4000-8000-000000000031','00000000-0000-4000-8000-000000000062',
         '00000000-0000-4000-8000-000000000011','S2-UNIT-HDMI-'||upper(suffix));
    INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,channel,created_by)
    VALUES(ord,'S2-UNIT-'||suffix,'00000000-0000-4000-8000-000000000010',
           '00000000-0000-4000-8000-000000000011','POS',actor);
    INSERT INTO sasms.sales_order_items(id,order_id,product_id,quantity,unit_price)
    VALUES(item,ord,'00000000-0000-4000-8000-000000000030',1,1000);
    INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,channel,created_by,created_at)
    VALUES(old_order,'S2-EXPIRED-'||suffix,'00000000-0000-4000-8000-000000000010',
           '00000000-0000-4000-8000-000000000011','POS',actor,now()-interval '16 minutes');
    INSERT INTO sasms.sales_order_items(id,order_id,product_id,quantity,unit_price)
    VALUES(old_item,old_order,'00000000-0000-4000-8000-000000000030',1,1000);
    PERFORM set_config('s2.actor',actor::text,true);
    PERFORM set_config('s2.denied',denied::text,true);
    PERFORM set_config('s2.outsider',outsider::text,true);
    PERFORM set_config('s2.inactive',inactive::text,true);
    PERFORM set_config('s2.order',ord::text,true);
    PERFORM set_config('s2.item',item::text,true);
    PERFORM set_config('s2.device',dev::text,true);
    PERFORM set_config('s2.old_order',old_order::text,true);
    PERFORM set_config('s2.old_item',old_item::text,true);
END $$;

SET LOCAL ROLE sasms_reader;
SELECT pg_temp.expect_state('reader cannot execute release',format(
    'SELECT sasms.release_device_lock(%L,%L,%L)',current_setting('s2.order'),
    current_setting('s2.device'),current_setting('s2.actor')),'42501');

SET LOCAL ROLE sasms_app;
DO $$
DECLARE
    actor uuid := current_setting('s2.actor')::uuid;
    ord uuid := current_setting('s2.order')::uuid;
    item uuid := current_setting('s2.item')::uuid;
    dev uuid := current_setting('s2.device')::uuid;
    store uuid := '00000000-0000-4000-8000-000000000010';
    product uuid := '00000000-0000-4000-8000-000000000030';
    warehouse uuid := '00000000-0000-4000-8000-000000000011';
    serial text; candidate uuid; label text; forbidden uuid;
    before_stock record; after_stock record;
BEGIN
    SELECT d.serial INTO STRICT serial FROM sasms.devices d WHERE id=dev;
    SELECT * INTO STRICT before_stock FROM sasms.v_realtime_inventory
    WHERE warehouse_id=warehouse AND product_id=product;
    PERFORM pg_temp.assert_true('view price is products.list_price',
        before_stock.price=(SELECT list_price FROM sasms.products WHERE id=product));
    PERFORM pg_temp.expect_state('expired draft cannot acquire an expired lease',format(
        'SELECT sasms.find_and_lock_device_for_pos(%L,%L,%L,%L,%L)',store,
        current_setting('s2.old_order'),current_setting('s2.old_item'),serial,actor),'23514');
    PERFORM pg_temp.assert_true('runtime app locks normalized Serial',
        sasms.find_and_lock_device_for_pos(store,ord,item,'  '||lower(serial)||'  ',actor)=dev);
    PERFORM pg_temp.assert_true('same cart selection is idempotent',
        sasms.find_and_lock_device_for_pos(store,ord,item,serial,actor)=dev
        AND (SELECT count(*) FROM sasms.order_devices WHERE order_id=ord AND active)=1);

    FOR label,forbidden IN SELECT * FROM (VALUES
        ('missing checkout permission',current_setting('s2.denied')::uuid),
        ('wrong store',current_setting('s2.outsider')::uuid),
        ('inactive actor',current_setting('s2.inactive')::uuid),
        ('NULL actor',NULL::uuid),('unknown actor',gen_random_uuid())
    ) forbidden_actors LOOP
        PERFORM pg_temp.expect_state('release denies '||label,
            format('SELECT sasms.release_device_lock(%L,%L,%L)',ord,dev,forbidden),'42501');
        PERFORM pg_temp.expect_state('lock denies '||label,
            format('SELECT sasms.find_and_lock_device_for_pos(%L,%L,%L,%L,%L)',
                   store,ord,item,serial,forbidden),'42501');
    END LOOP;
    PERFORM pg_temp.assert_true('denied release preserves active allocation',
        EXISTS(SELECT 1 FROM sasms.order_devices WHERE order_id=ord AND device_id=dev AND active));
    PERFORM pg_temp.assert_true('authorized release changes allocation',sasms.release_device_lock(ord,dev,actor));
    PERFORM pg_temp.assert_true('release returns false when already released',
        NOT sasms.release_device_lock(ord,dev,actor));
    PERFORM pg_temp.assert_true('release returns false for unrelated device',
        NOT sasms.release_device_lock(ord,gen_random_uuid(),actor));
    PERFORM pg_temp.assert_true('released device can be selected again',
        sasms.find_and_lock_device_for_pos(store,ord,item,serial,actor)=dev);
    SELECT * INTO STRICT after_stock FROM sasms.v_realtime_inventory
    WHERE warehouse_id=warehouse AND product_id=product;
    PERFORM pg_temp.assert_true('draft selection leaves physical stock unchanged',
        (after_stock.available_stock,after_stock.reserved_stock,after_stock.sold_stock)=
        (before_stock.available_stock,before_stock.reserved_stock,before_stock.sold_stock));

    PERFORM sasms.reserve_order(ord,actor);
    SELECT * INTO STRICT after_stock FROM sasms.v_realtime_inventory
    WHERE warehouse_id=warehouse AND product_id=product;
    PERFORM pg_temp.assert_true('reserve exact stock delta (-1,+1,0)',
        (after_stock.available_stock,after_stock.reserved_stock,after_stock.sold_stock)=
        (before_stock.available_stock-1,before_stock.reserved_stock+1,before_stock.sold_stock));
    PERFORM pg_temp.expect_state('release denies RESERVED order',
        format('SELECT sasms.release_device_lock(%L,%L,%L)',ord,dev,actor),'23514');
    INSERT INTO sasms.payments(order_id,amount,method,status,confirmed_by,captured_at)
    VALUES(ord,1000,'CASH','CAPTURED',actor,now());
    PERFORM sasms.complete_sale(ord,actor);
    SELECT * INTO STRICT after_stock FROM sasms.v_realtime_inventory
    WHERE warehouse_id=warehouse AND product_id=product;
    PERFORM pg_temp.assert_true('sale exact stock delta (-1,0,+1)',
        (after_stock.available_stock,after_stock.reserved_stock,after_stock.sold_stock)=
        (before_stock.available_stock-1,before_stock.reserved_stock,before_stock.sold_stock+1));
    PERFORM pg_temp.assert_true('sale has one invoice and movement',
        (SELECT count(*) FROM sasms.invoices WHERE order_id=ord)=1
        AND (SELECT count(*) FROM sasms.inventory_movements WHERE device_id=dev AND kind='SALE')=1);
    SELECT recommended_product_id INTO candidate FROM sasms.get_cross_sell_fallback_rules(product,store,3)
    WHERE recommended_product_id='00000000-0000-4000-8000-000000000031';
    PERFORM pg_temp.assert_true('fallback returns available accessory with catalog price',
        candidate IS NOT NULL AND EXISTS(
            SELECT 1 FROM sasms.get_cross_sell_fallback_rules(product,store,3) r
            JOIN sasms.products p ON p.id=r.recommended_product_id
            WHERE r.recommended_product_id=candidate AND r.available_stock>0 AND r.price=p.list_price));
END $$;
ROLLBACK;
