-- Sprint 4 query/ACL regression. Owner creates synthetic reporting fixtures;
-- runtime assertions use sasms_app. This is not a checkout/refund/API E2E test.
-- Every fixture, temporary helper and test query is rolled back.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL search_path=sasms,pg_catalog;
CREATE FUNCTION pg_temp.assert_true(label text,result boolean) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 IF result IS DISTINCT FROM true THEN RAISE EXCEPTION 'FAIL: %',label; END IF;
 RAISE NOTICE 'PASS: %',label;
END $$;
CREATE FUNCTION pg_temp.expect_state(label text,statement text,expected text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE actual text;
BEGIN
 BEGIN EXECUTE statement;
 EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS actual=RETURNED_SQLSTATE; END;
 IF actual IS DISTINCT FROM expected THEN RAISE EXCEPTION 'FAIL: % expected %, got %',label,expected,actual; END IF;
 RAISE NOTICE 'PASS: % (SQLSTATE %)',label,actual;
END $$;
CREATE FUNCTION pg_temp.synthetic_imei() RETURNS text LANGUAGE plpgsql AS $$
DECLARE base text:=lpad((abs(hashtextextended(gen_random_uuid()::text,0)%100000000000000))::text,14,'0'); total integer;
BEGIN
 SELECT sum(CASE WHEN n>9 THEN n-9 ELSE n END) INTO total
 FROM (SELECT substring(base FROM i FOR 1)::integer*CASE WHEN i%2=0 THEN 2 ELSE 1 END n
       FROM generate_series(1,14) i) digits;
 RETURN base||((10-total%10)%10)::text;
END $$;
CREATE FUNCTION pg_temp.synthetic_receipt(warehouse uuid,actor uuid,product uuid) RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE po uuid:=gen_random_uuid(); poi uuid:=gen_random_uuid(); gr uuid:=gen_random_uuid(); gri uuid:=gen_random_uuid();
BEGIN
 INSERT INTO sasms.purchase_orders(id,code,supplier_id,warehouse_id,status,created_by)
 VALUES(po,'S4-'||po,'00000000-0000-4000-8000-000000000040',warehouse,'APPROVED',actor);
 INSERT INTO sasms.purchase_order_items(id,purchase_order_id,product_id,quantity,unit_cost)
 VALUES(poi,po,product,10,10);
 INSERT INTO sasms.goods_receipts(id,code,purchase_order_id,warehouse_id,received_by)
 VALUES(gr,'S4-'||gr,po,warehouse,actor);
 INSERT INTO sasms.goods_receipt_items(id,receipt_id,purchase_order_id,purchase_order_item_id,product_id,quantity)
 VALUES(gri,gr,po,poi,product,10);
 RETURN gri;
END $$;

DO $$
DECLARE idx text; store_a uuid:=gen_random_uuid(); store_b uuid:=gen_random_uuid();
 warehouse_a uuid:=gen_random_uuid(); warehouse_b uuid:=gen_random_uuid();
 manager uuid:=gen_random_uuid(); outsider uuid:=gen_random_uuid(); inactive uuid:=gen_random_uuid(); cashier uuid:=gen_random_uuid();
 receipt_a uuid; receipt_b uuid; ord uuid:=gen_random_uuid(); item uuid:=gen_random_uuid(); draft uuid:=gen_random_uuid();
 dev uuid:=gen_random_uuid(); reserved uuid:=gen_random_uuid(); sold uuid:=gen_random_uuid(); returned uuid:=gen_random_uuid(); other_dev uuid:=gen_random_uuid();
 ret uuid:=gen_random_uuid(); delivery uuid:=gen_random_uuid(); imei1 text:=pg_temp.synthetic_imei(); imei2 text:=pg_temp.synthetic_imei();
 product uuid:='00000000-0000-4000-8000-000000000030';
BEGIN
 FOREACH idx IN ARRAY ARRAY['ix_sales_orders_s4_paid_store_date','ix_movements_s4_warehouse_date',
  'ix_devices_s4_sellable','ix_return_items_s4_device','ix_delivery_items_s4_device'] LOOP
  PERFORM pg_temp.assert_true('valid, ready S4 index: '||idx,EXISTS(
   SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='sasms' AND c.relname=idx AND i.indisvalid AND i.indisready));
 END LOOP;
 PERFORM pg_temp.assert_true('QR, Serial and primary IMEI keep their existing unique access paths',
  (SELECT count(*)=3 FROM pg_indexes WHERE schemaname='sasms' AND tablename='devices'
   AND indexname IN ('devices_qr_token_key','devices_serial_key','devices_imei_key') AND indexdef LIKE 'CREATE UNIQUE INDEX%'));
 PERFORM pg_temp.assert_true('secondary IMEI and Serial registry keeps its unique lookup',EXISTS(
  SELECT 1 FROM pg_indexes WHERE schemaname='sasms' AND tablename='device_identifiers'
   AND indexdef LIKE 'CREATE UNIQUE INDEX%' AND indexdef LIKE '%(kind, normalized_value)%'));
 PERFORM pg_temp.assert_true('partial PAID index uses paid_at and covers amount',
  pg_get_indexdef('sasms.ix_sales_orders_s4_paid_store_date'::regclass) LIKE '%(store_id, paid_at, id) INCLUDE (total_amount)%'
  AND pg_get_expr((SELECT indpred FROM pg_index WHERE indexrelid='sasms.ix_sales_orders_s4_paid_store_date'::regclass),
                  'sasms.sales_orders'::regclass) LIKE '%PAID%');
 PERFORM pg_temp.assert_true('sellable index excludes non-IN_STOCK devices',
  pg_get_expr((SELECT indpred FROM pg_index WHERE indexrelid='sasms.ix_devices_s4_sellable'::regclass),
                  'sasms.devices'::regclass) LIKE '%IN_STOCK%');
 PERFORM pg_temp.assert_true('app can read query sources without gaining return or ledger mutation',
  has_table_privilege('sasms_app','sasms.devices','SELECT')
  AND NOT has_table_privilege('sasms_app','sasms.returns','INSERT')
  AND NOT has_table_privilege('sasms_app','sasms.inventory_movements','INSERT'));
 PERFORM pg_temp.assert_true('reader still cannot read orders or raw device identifiers',
  NOT has_table_privilege('sasms_reader','sasms.sales_orders','SELECT')
  AND NOT has_table_privilege('sasms_reader','sasms.device_identifiers','SELECT'));

 INSERT INTO sasms.stores(id,code,name,address) VALUES
  (store_a,'S4-A-'||store_a,'Synthetic report store A','Synthetic'),
  (store_b,'S4-B-'||store_b,'Synthetic report store B','Synthetic');
 INSERT INTO sasms.warehouses(id,store_id,code,name) VALUES
  (warehouse_a,store_a,'S4-A-'||warehouse_a,'Synthetic A'),(warehouse_b,store_b,'S4-B-'||warehouse_b,'Synthetic B');
 INSERT INTO sasms.users(id,email,full_name,is_active) VALUES
  (manager,manager||'@example.invalid','Synthetic manager A',true),
  (outsider,outsider||'@example.invalid','Synthetic manager B',true),
  (inactive,inactive||'@example.invalid','Synthetic inactive manager',false),
  (cashier,cashier||'@example.invalid','Synthetic cashier A',true);
 INSERT INTO sasms.user_roles(user_id,role_code,store_id) VALUES
  (manager,'STORE_MANAGER',store_a),(manager,'CASHIER',store_a),
  (outsider,'STORE_MANAGER',store_b),(outsider,'CASHIER',store_b),
  (inactive,'STORE_MANAGER',store_a),(cashier,'CASHIER',store_a);
 receipt_a:=pg_temp.synthetic_receipt(warehouse_a,manager,product);
 receipt_b:=pg_temp.synthetic_receipt(warehouse_b,outsider,product);
 INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,channel,created_by)
 VALUES(ord,'S4-'||ord,store_a,warehouse_a,'POS',manager),(draft,'S4-'||draft,store_a,warehouse_a,'POS',manager);
 INSERT INTO sasms.sales_order_items(id,order_id,product_id,quantity,unit_price) VALUES(item,ord,product,2,62500);
 INSERT INTO sasms.devices(id,product_id,receipt_item_id,warehouse_id,serial,imei,status,reserved_order_id,sold_order_id,sold_at) VALUES
  (dev,product,receipt_a,warehouse_a,'S4-Mixed-'||dev,imei1,'IN_STOCK',NULL,NULL,NULL),
  (reserved,product,receipt_a,warehouse_a,'S4-'||reserved,NULL,'RESERVED',draft,NULL,NULL),
  (sold,product,receipt_a,warehouse_a,'S4-'||sold,NULL,'IN_STOCK',NULL,NULL,NULL),
  (returned,product,receipt_a,warehouse_a,'S4-'||returned,NULL,'IN_STOCK',NULL,NULL,NULL),
  (other_dev,product,receipt_b,warehouse_b,'S4-'||other_dev,NULL,'IN_STOCK',NULL,NULL,NULL);
 INSERT INTO sasms.device_identifiers(device_id,kind,slot,raw_value,normalized_value) VALUES(dev,'IMEI',2,imei2,imei2);
 INSERT INTO sasms.order_devices(order_item_id,order_id,product_id,device_id) VALUES(item,ord,product,sold),(item,ord,product,returned);
 UPDATE sasms.devices SET status=CASE WHEN id=sold THEN 'SOLD' ELSE 'RETURNED' END,
  sold_order_id=ord,sold_at='2026-11-08 00:00:00+07' WHERE id IN(sold,returned);
 UPDATE sasms.sales_orders SET status='PAID',total_amount=125000,paid_at='2026-11-08 00:00:00+07' WHERE id=ord;
 INSERT INTO sasms.sales_orders(code,store_id,warehouse_id,channel,created_by,status,total_amount,paid_at) VALUES
  ('S4-'||gen_random_uuid(),store_a,warehouse_a,'POS',manager,'PAID',75000,'2026-11-20 23:59:59.999999+07'),
  ('S4-'||gen_random_uuid(),store_a,warehouse_a,'POS',manager,'PAID',999000,'2026-11-21 00:00:00+07'),
  ('S4-'||gen_random_uuid(),store_a,warehouse_a,'POS',manager,'PAID',888000,'2026-11-07 23:59:59.999999+07'),
  ('S4-'||gen_random_uuid(),store_b,warehouse_b,'POS',outsider,'PAID',500000,'2026-11-10 12:00:00+07');
 INSERT INTO sasms.inventory_movements(device_id,warehouse_id,kind,order_id,actor_id,reason,created_at) VALUES
  (dev,warehouse_a,'RECEIVE',NULL,manager,'Synthetic query fixture','2026-11-08 00:00:00+07'),
  (sold,warehouse_a,'SALE',ord,manager,'Synthetic query fixture','2026-11-10 12:00:00+07'),
  (returned,warehouse_a,'RETURN',ord,manager,'Synthetic query fixture','2026-11-20 23:59:59.999999+07'),
  (dev,warehouse_a,'TRANSFER_IN',NULL,manager,'Synthetic query fixture','2026-11-10 12:00:00+07'),
  (returned,warehouse_a,'RETURN',ord,manager,'Synthetic query fixture','2026-11-21 00:00:00+07'),
  (other_dev,warehouse_b,'RECEIVE',NULL,outsider,'Synthetic query fixture','2026-11-10 12:00:00+07');
 INSERT INTO sasms.returns(id,order_id,code,reason,requested_by) VALUES(ret,ord,'S4-'||ret,'Synthetic request only',cashier);
 INSERT INTO sasms.return_items(return_id,order_id,device_id,refund_amount,condition_note)
 VALUES(ret,ord,returned,100,'Synthetic pending inspection; no refund asserted');
 INSERT INTO sasms.deliveries(id,order_id,address_snapshot,scheduled_at) VALUES(delivery,ord,'Synthetic','2026-11-10 12:00:00+07');
 INSERT INTO sasms.delivery_items(delivery_id,order_id,device_id) VALUES(delivery,ord,sold);

 PERFORM set_config('s4.store',store_a::text,true); PERFORM set_config('s4.other_store',store_b::text,true);
 PERFORM set_config('s4.warehouse',warehouse_a::text,true); PERFORM set_config('s4.other_warehouse',warehouse_b::text,true);
 PERFORM set_config('s4.manager',manager::text,true); PERFORM set_config('s4.outsider',outsider::text,true);
 PERFORM set_config('s4.inactive',inactive::text,true); PERFORM set_config('s4.cashier',cashier::text,true);
 PERFORM set_config('s4.device',dev::text,true); PERFORM set_config('s4.sold',sold::text,true);
 PERFORM set_config('s4.returned',returned::text,true); PERFORM set_config('s4.order',ord::text,true);
 PERFORM set_config('s4.imei1',imei1,true); PERFORM set_config('s4.imei2',imei2,true);
END $$;

SET LOCAL ROLE sasms_app;
DO $$
DECLARE store uuid:=current_setting('s4.store')::uuid; warehouse uuid:=current_setting('s4.warehouse')::uuid;
 actor uuid:=current_setting('s4.manager')::uuid; dev uuid:=current_setting('s4.device')::uuid;
 f timestamptz:='2026-11-08 00:00:00+07'; t timestamptz:='2026-11-21 00:00:00+07';
 result record; old_gross numeric; old_count bigint; timeline_count bigint;
BEGIN
 PERFORM pg_temp.assert_true('active store manager can read its reports',sasms.has_permission(actor,'reports.read',store));
 PERFORM pg_temp.expect_state('cross-store report access denied',format('SELECT sasms.assert_permission(%L,%L,%L)',
  current_setting('s4.outsider'),'reports.read',store),'42501');
 PERFORM pg_temp.expect_state('inactive report actor denied',format('SELECT sasms.assert_permission(%L,%L,%L)',
  current_setting('s4.inactive'),'reports.read',store),'42501');
 PERFORM pg_temp.expect_state('cashier without reports permission denied',format('SELECT sasms.assert_permission(%L,%L,%L)',
  current_setting('s4.cashier'),'reports.read',store),'42501');
 PERFORM pg_temp.expect_state('null report actor denied',format('SELECT sasms.assert_permission(NULL,%L,%L)',
  'reports.read',store),'42501');

 -- Same three aggregates as JdbcStoreDashboardDataAccess.SUMMARY_SQL.
 SELECT paid.gross,paid.paid_orders,stock.available_devices,movements.received,movements.sold,movements.returned INTO result
 FROM (SELECT coalesce(sum(o.total_amount),0) gross,count(*) paid_orders FROM sasms.sales_orders o
       WHERE o.store_id=store AND o.status='PAID' AND o.paid_at>=f AND o.paid_at<t) paid
 CROSS JOIN (SELECT count(*) available_devices FROM sasms.devices d JOIN sasms.warehouses w ON w.id=d.warehouse_id
       WHERE w.store_id=store AND d.status='IN_STOCK') stock
 CROSS JOIN (SELECT count(*) FILTER(WHERE m.kind='RECEIVE') received,count(*) FILTER(WHERE m.kind='SALE') sold,
       count(*) FILTER(WHERE m.kind='RETURN') returned FROM sasms.inventory_movements m
       JOIN sasms.warehouses w ON w.id=m.warehouse_id WHERE w.store_id=store AND m.created_at>=f AND m.created_at<t) movements;
 PERFORM pg_temp.assert_true('gross paid sum includes from and excludes to, unpaid and another store',result.gross=200000);
 PERFORM pg_temp.assert_true('paid sum and count share the exact reporting set',result.paid_orders=2);
 PERFORM pg_temp.assert_true('available means IN_STOCK only in this store',result.available_devices=1);
 PERFORM pg_temp.assert_true('one movement scan keeps RECEIVE/SALE/RETURN separate',
  result.received=1 AND result.sold=1 AND result.returned=1);
 SELECT coalesce(sum(o.total_amount),0) INTO old_gross FROM sasms.sales_orders o
  WHERE o.store_id=store AND o.status='PAID' AND o.paid_at>=f AND o.paid_at<t;
 SELECT count(*) INTO old_count FROM sasms.sales_orders o
  WHERE o.store_id=store AND o.status='PAID' AND o.paid_at>=f AND o.paid_at<t;
 PERFORM pg_temp.assert_true('combined paid aggregate preserves previous scalar-query result',result.gross=old_gross AND result.paid_orders=old_count);
 SELECT coalesce(sum(total_amount),0) gross,count(*) paid_orders INTO result FROM sasms.sales_orders
  WHERE store_id=store AND status='PAID' AND paid_at>='2000-01-01' AND paid_at<'2000-01-02';
 PERFORM pg_temp.assert_true('empty reporting range is one zero-valued row',result.gross=0 AND result.paid_orders=0);
 PERFORM pg_temp.assert_true('IN_STOCK candidate count agrees with the previous real-time view',
  (SELECT count(*) FROM sasms.devices WHERE warehouse_id=warehouse AND product_id='00000000-0000-4000-8000-000000000030' AND status='IN_STOCK')
  =(SELECT available FROM sasms.stock_availability WHERE warehouse_id=warehouse AND product_id='00000000-0000-4000-8000-000000000030'));

 -- Same bounded, real-time LATERAL count as JdbcAiCrossSellDataAccess.
 SELECT count(*) candidates,sum(sa.available) available INTO result FROM sasms.products p
 JOIN sasms.categories c ON c.id=p.category_id
 JOIN LATERAL (SELECT count(*) available FROM sasms.devices d
  WHERE d.product_id=p.id AND d.warehouse_id=warehouse AND d.status='IN_STOCK') sa ON sa.available>0
 WHERE p.sku=ANY(ARRAY['DEMO-TV-55','DEMO-HDMI']) AND p.is_active=TRUE;
 PERFORM pg_temp.assert_true('AI counts only requested in-stock candidates in the chosen warehouse',result.candidates=1 AND result.available=1);
 PERFORM pg_temp.assert_true('AI warehouse ownership rejects another store',NOT EXISTS(
  SELECT 1 FROM sasms.warehouses w WHERE w.id=current_setting('s4.other_warehouse')::uuid AND w.store_id=store));
 PERFORM pg_temp.assert_true('AI actor scope does not cross stores',NOT sasms.has_permission(actor,'ai.use',current_setting('s4.other_store')::uuid));

 PERFORM pg_temp.assert_true('QR lookup returns only an authorized device',EXISTS(
  SELECT 1 FROM sasms.devices d JOIN sasms.warehouses w ON w.id=d.warehouse_id
  WHERE d.qr_token=(SELECT qr_token FROM sasms.devices WHERE id=dev)
   AND sasms.has_permission(actor,'inventory.read',w.store_id) AND d.id=dev));
 PERFORM pg_temp.assert_true('Serial remains case-sensitive',NOT EXISTS(
  SELECT 1 FROM sasms.devices WHERE serial=upper('S4-Mixed-'||dev)));
 PERFORM pg_temp.assert_true('both IMEI slots resolve to the same authorized device',
  (SELECT count(*) FROM sasms.device_identifiers i JOIN sasms.devices d ON d.id=i.device_id
   JOIN sasms.warehouses w ON w.id=d.warehouse_id WHERE i.kind='IMEI'
    AND i.normalized_value=ANY(ARRAY[current_setting('s4.imei1'),current_setting('s4.imei2')])
    AND sasms.has_permission(actor,'inventory.read',w.store_id) AND d.id=dev)=2);
 PERFORM pg_temp.assert_true('identifier lookup conceals a device from a cross-store actor',NOT EXISTS(
  SELECT 1 FROM sasms.device_identifiers i JOIN sasms.devices d ON d.id=i.device_id
  JOIN sasms.warehouses w ON w.id=d.warehouse_id WHERE i.kind='IMEI' AND i.normalized_value=current_setting('s4.imei2')
   AND sasms.has_permission(current_setting('s4.outsider')::uuid,'inventory.read',w.store_id)));
 PERFORM pg_temp.assert_true('return timeline reverse lookup preserves pending request state',EXISTS(
  SELECT 1 FROM sasms.return_items ri JOIN sasms.returns r ON r.id=ri.return_id
  JOIN sasms.sales_orders o ON o.id=r.order_id WHERE ri.device_id=current_setting('s4.returned')::uuid
   AND r.status='REQUESTED' AND sasms.has_permission(actor,'sales.read',o.store_id)));
 PERFORM pg_temp.assert_true('delivery timeline reverse lookup stays linked to its original order',EXISTS(
  SELECT 1 FROM sasms.delivery_items di JOIN sasms.deliveries d ON d.id=di.delivery_id
  JOIN sasms.sales_orders o ON o.id=d.order_id WHERE di.device_id=current_setting('s4.sold')::uuid
   AND o.id=current_setting('s4.order')::uuid AND sasms.has_permission(actor,'sales.read',o.store_id)));
 SELECT count(*) INTO timeline_count FROM sasms.return_items ri JOIN sasms.returns r ON r.id=ri.return_id
  JOIN sasms.sales_orders o ON o.id=r.order_id WHERE ri.device_id=current_setting('s4.returned')::uuid
   AND sasms.has_permission(current_setting('s4.outsider')::uuid,'sales.read',o.store_id);
 PERFORM pg_temp.assert_true('return timeline conceals another store',timeline_count=0);
END $$;
RESET ROLE;
ROLLBACK;
