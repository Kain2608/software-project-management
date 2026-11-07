\set ON_ERROR_STOP on
-- Requires runtime-roles.sql, database owner and isolated validation database.
BEGIN;
SET search_path = sasms, pg_catalog;
DO $$
BEGIN
 IF has_column_privilege('sasms_app','sasms.sales_orders','status','INSERT')
 OR has_column_privilege('sasms_app','sasms.sales_orders','total_amount','UPDATE')
 OR has_table_privilege('sasms_app','sasms.devices','UPDATE')
 OR has_table_privilege('sasms_app','sasms.audit_logs','INSERT') THEN
  RAISE EXCEPTION 'Runtime grants allow protected state mutation';
 END IF;
 RAISE NOTICE 'PASS: runtime ACL protects order status total stock and audit';
END $$;
SET LOCAL ROLE sasms_app;
DO $$
DECLARE actor uuid := '00000000-0000-4000-8000-000000000001';
 ord uuid := gen_random_uuid(); item uuid := gen_random_uuid(); dev uuid; blocked boolean;
BEGIN
 dev := sasms.receive_device('00000000-0000-4000-8000-000000000061','TEST-S0-ACL',NULL,actor);
 INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,channel,created_by)
 VALUES(ord,'TEST-S0-ACL','00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000011','POS',actor);
 INSERT INTO sasms.sales_order_items(id,order_id,product_id,quantity,unit_price)
 VALUES(item,ord,'00000000-0000-4000-8000-000000000030',1,100);
 INSERT INTO sasms.order_devices(order_item_id,order_id,product_id,device_id)
 VALUES(item,ord,'00000000-0000-4000-8000-000000000030',dev);
 PERFORM sasms.reserve_order(ord,actor);
 INSERT INTO sasms.payments(order_id,method,status,amount,confirmed_by,captured_at)
 VALUES(ord,'CASH','CAPTURED',100,actor,now());
 PERFORM sasms.complete_sale(ord,actor);
 IF NOT EXISTS(SELECT 1 FROM sasms.devices WHERE id=dev AND status='SOLD') THEN
  RAISE EXCEPTION 'Runtime checkout path did not sell selected device';
 END IF;
 RAISE NOTICE 'PASS: sasms_app can receive create reserve confirm and complete through allowed paths';
 blocked := false;
 BEGIN UPDATE sasms.devices SET status='IN_STOCK' WHERE id=dev;
 EXCEPTION WHEN insufficient_privilege THEN blocked := true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'Runtime updated stock directly'; END IF;
 RAISE NOTICE 'PASS: actual sasms_app UPDATE stock rejected';
 blocked := false;
 BEGIN UPDATE sasms.sales_orders SET total_amount=0 WHERE id=ord;
 EXCEPTION WHEN insufficient_privilege THEN blocked := true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'Runtime updated order total directly'; END IF;
 RAISE NOTICE 'PASS: actual sasms_app UPDATE total rejected';
 blocked := false;
 BEGIN INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,channel,created_by,status)
 VALUES(gen_random_uuid(),'TEST-S0-ACL-BYPASS','00000000-0000-4000-8000-000000000010',
 '00000000-0000-4000-8000-000000000011','POS',actor,'PAID');
 EXCEPTION WHEN insufficient_privilege THEN blocked := true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'Runtime supplied protected order status'; END IF;
 RAISE NOTICE 'PASS: actual sasms_app INSERT status rejected';
 blocked := false;
 BEGIN INSERT INTO sasms.audit_logs(actor_id,action,entity_type,entity_id) VALUES(actor,'forged','order',ord);
 EXCEPTION WHEN insufficient_privilege THEN blocked := true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'Runtime forged audit'; END IF;
 RAISE NOTICE 'PASS: actual sasms_app INSERT audit rejected';
END $$;
SET LOCAL ROLE sasms_reader;
DO $$
DECLARE blocked boolean := false;
BEGIN
 PERFORM count(*) FROM sasms.products;
 PERFORM count(*) FROM sasms.stock_availability;
 BEGIN PERFORM count(*) FROM sasms.customers;
 EXCEPTION WHEN insufficient_privilege THEN blocked := true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'Reader accessed customer data'; END IF;
 RAISE NOTICE 'PASS: reader can query catalog and stock aggregates but cannot query customer data';
END $$;
ROLLBACK;
