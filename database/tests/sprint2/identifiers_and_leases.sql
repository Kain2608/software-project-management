\set ON_ERROR_STOP on
BEGIN;
SET search_path=sasms,pg_catalog;
CREATE FUNCTION pg_temp.ok(label text,value boolean) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF value IS DISTINCT FROM true THEN RAISE EXCEPTION 'ASSERTION FAILED: %',label; END IF;
 RAISE NOTICE 'PASS: %',label; END $$;
CREATE FUNCTION pg_temp.reject(label text,statement text,expected text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE actual text;
BEGIN
 BEGIN EXECUTE statement; EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS actual=RETURNED_SQLSTATE; END;
 IF actual IS DISTINCT FROM expected THEN RAISE EXCEPTION 'ASSERTION FAILED: %, expected %, got %',label,expected,coalesce(actual,'SUCCESS'); END IF;
 RAISE NOTICE 'PASS: % [%]',label,expected;
END $$;
DO $$
DECLARE actor uuid:=gen_random_uuid(); cashier uuid:=gen_random_uuid(); key uuid:=gen_random_uuid();
 ids uuid[]; other_ids uuid[]; payload jsonb; dev uuid; ord_a uuid:=gen_random_uuid(); ord_b uuid:=gen_random_uuid();
 item_a uuid:=gen_random_uuid(); item_b uuid:=gen_random_uuid(); online uuid:=gen_random_uuid();
 pending_order uuid:=gen_random_uuid(); pending_item uuid:=gen_random_uuid(); payment uuid:=gen_random_uuid();
 plain_order uuid:=gen_random_uuid(); plain_item uuid:=gen_random_uuid();
 wh uuid:=gen_random_uuid(); accessory uuid:=gen_random_uuid(); secondary_count bigint; deadline timestamptz;
 store uuid:='00000000-0000-4000-8000-000000000010'; warehouse uuid:='00000000-0000-4000-8000-000000000011';
 product uuid:='00000000-0000-4000-8000-000000000030'; receipt uuid:='00000000-0000-4000-8000-000000000061';
BEGIN
 INSERT INTO users(id,email,full_name,is_active) VALUES(actor,'v3-admin@example.invalid','Synthetic DB test',true),
  (cashier,'v3-cashier@example.invalid','Synthetic cashier',true);
 INSERT INTO user_roles(user_id,role_code,store_id) VALUES(actor,'ADMIN',NULL),
  (actor,'CATALOG_MANAGER',NULL),(actor,'WAREHOUSE_STAFF',NULL),(cashier,'CASHIER',store);
 PERFORM pg_temp.ok('nine canonical BA roles',(SELECT count(*)=9 FROM roles WHERE NOT is_legacy));
 PERFORM pg_temp.ok('ADMIN does not automatically approve refund',NOT EXISTS(SELECT 1 FROM role_permissions WHERE role_code='ADMIN' AND permission_code='payments.refund'));
 PERFORM pg_temp.ok('catalog manager cannot checkout',NOT EXISTS(SELECT 1 FROM role_permissions WHERE role_code='CATALOG_MANAGER' AND permission_code='sales.checkout'));
 PERFORM pg_temp.ok('41 current business tables',(SELECT count(*)=41 FROM pg_tables WHERE schemaname='sasms'));
 PERFORM pg_temp.ok('valid IMEI checksum',sasms.valid_imei('490154203237518'));
 PERFORM pg_temp.ok('invalid IMEI checksum rejected',NOT sasms.valid_imei('123456789012345'));
 PERFORM pg_temp.ok('malformed IMEI is false',NOT coalesce(sasms.valid_imei('not-numeric'),false));
 payload:='[{"serial":"  001abc XYZ-1  ","imei":" 490154203237518 ","imei2":"356938035643809"}]';
 ids:=sasms.receive_device_batch(receipt,key,payload,actor);
 PERFORM pg_temp.ok('raw Serial and normalized case preserved',EXISTS(SELECT 1 FROM devices
  WHERE id=ids[1] AND serial='001abc XYZ-1' AND serial_raw='  001abc XYZ-1  '));
 PERFORM pg_temp.ok('raw IMEI and both slots preserved',(SELECT count(*)=2 FROM device_identifiers
  WHERE device_id=ids[1] AND kind='IMEI') AND EXISTS(SELECT 1 FROM device_identifiers
  WHERE device_id=ids[1] AND kind='IMEI' AND slot=1 AND raw_value=' 490154203237518 '));
 PERFORM pg_temp.ok('batch retry returns same IDs',sasms.receive_device_batch(receipt,key,payload,actor)=ids);
 PERFORM pg_temp.reject('case-changed retry is a different payload',format(
  'SELECT sasms.receive_device_batch(%L,%L,%L,%L)',receipt,key,
  '[{"serial":"001ABC XYZ-1","imei":"490154203237518","imei2":"356938035643809"}]',actor),'23514');
 other_ids:=sasms.receive_device_batch(receipt,gen_random_uuid(),'[{"serial":"001ABC XYZ-1"}]',actor);
 PERFORM pg_temp.ok('case-sensitive Serial keys remain distinct',ids[1]<>other_ids[1]);
 PERFORM pg_temp.reject('IMEI duplicate across slots is rejected',format(
  'SELECT sasms.receive_device_batch(%L,%L,%L,%L)',receipt,gen_random_uuid(),
  '[{"serial":"V3-cross-slot","imei":"356938035643809"}]',actor),'23505');
 PERFORM pg_temp.reject('invalid Luhn at SQL boundary',format(
  'SELECT sasms.receive_device_batch(%L,%L,%L,%L)',receipt,gen_random_uuid(),
  '[{"serial":"V3-invalid-imei","imei":"123456789012345"}]',actor),'23514');
 PERFORM pg_temp.reject('control characters in Serial rejected',format(
  'SELECT sasms.receive_device_batch(%L,%L,%L,%L)',receipt,gen_random_uuid(),
  jsonb_build_array(jsonb_build_object('serial','V3'||chr(9)||'bad')),actor),'23514');
 PERFORM pg_temp.ok('128-character Serial accepted',cardinality(sasms.receive_device_batch(receipt,
  gen_random_uuid(),jsonb_build_array(jsonb_build_object('serial',repeat('x',128))),actor))=1);
 PERFORM pg_temp.reject('129-character Serial rejected',format(
  'SELECT sasms.receive_device_batch(%L,%L,%L,%L)',receipt,gen_random_uuid(),
  jsonb_build_array(jsonb_build_object('serial',repeat('x',129))),actor),'23514');
 PERFORM pg_temp.reject('identifier history cannot be overwritten',format(
  'UPDATE sasms.devices SET serial=%L WHERE id=%L','changed',ids[1]),'23514');
 PERFORM pg_temp.reject('identifier registry history retained',format(
  'DELETE FROM sasms.device_identifiers WHERE device_id=%L',ids[1]),'23514');
 secondary_count:=(SELECT count(*) FROM devices);
 PERFORM pg_temp.reject('invalid second batch item rolls back first',format(
  'SELECT sasms.receive_device_batch(%L,%L,%L,%L)',receipt,gen_random_uuid(),
  '[{"serial":"V3-rollback-first"},{"serial":"V3-rollback-second","imei":"123456789012345"}]',actor),'23514');
 PERFORM pg_temp.ok('failed batch leaves stock unchanged',(SELECT count(*) FROM devices)=secondary_count);
 INSERT INTO product_identifier_profiles VALUES(product,'SONY_TV_7',false,'SYNTHETIC D35 test approval',actor,now());
 PERFORM pg_temp.reject('Sony profile applies only to its configured SKU',format(
  'SELECT sasms.receive_device_batch(%L,%L,%L,%L)',receipt,gen_random_uuid(),
  '[{"serial":"V3-invalid-sony"}]',actor),'23514');
 PERFORM pg_temp.ok('configured Sony SKU accepts seven digits',cardinality(sasms.receive_device_batch(receipt,
  gen_random_uuid(),'[{"serial":"0012345"}]',actor))=1);
 other_ids:=sasms.receive_device_batch('00000000-0000-4000-8000-000000000062',gen_random_uuid(),
  '[{"serial":"V3 HDMI flexible"}]',actor);
 PERFORM pg_temp.ok('other SKU under same brand remains generic',cardinality(other_ids)=1);
 PERFORM pg_temp.ok('DEMO brand is unchanged',(SELECT name='Hãng DEMO' FROM brands WHERE id='00000000-0000-4000-8000-000000000020'));
 -- Use an existing generic device for hold lifecycle; fixture profile does not
 -- rewrite pre-existing identifiers or historical stock.
 dev:=ids[1];
 INSERT INTO sales_orders(id,code,store_id,warehouse_id,channel,created_by) VALUES
  (ord_a,'V4-A',store,warehouse,'POS',cashier),(ord_b,'V4-B',store,warehouse,'POS',cashier),
  (online,'V4-ONLINE',store,warehouse,'ONLINE',cashier),
  (pending_order,'V4-PENDING',store,warehouse,'POS',cashier),(plain_order,'V4-PLAIN',store,warehouse,'POS',cashier);
 INSERT INTO sales_order_items(id,order_id,product_id,quantity,unit_price) VALUES
  (item_a,ord_a,product,1,1000),(item_b,ord_b,product,1,1000),
  (pending_item,pending_order,product,1,1000),(plain_item,plain_order,product,1,1000);
 PERFORM sasms.find_and_lock_device_for_pos(store,ord_a,item_a,'001abc XYZ-1',cashier);
 SELECT hold_expires_at INTO deadline FROM sales_orders WHERE id=ord_a;
 PERFORM pg_temp.ok('POS hold is ten server minutes',deadline BETWEEN clock_timestamp()+interval '9 minutes 55 seconds' AND clock_timestamp()+interval '10 minutes');
 PERFORM sasms.find_and_lock_device_for_pos(store,ord_a,item_a,'001abc XYZ-1',cashier);
 PERFORM pg_temp.ok('scan retry does not refresh hold',(SELECT hold_expires_at=deadline FROM sales_orders WHERE id=ord_a));
 UPDATE sales_orders SET hold_expires_at=clock_timestamp()-interval '1 second' WHERE id=ord_a;
 PERFORM sasms.find_and_lock_device_for_pos(store,ord_b,item_b,'001abc XYZ-1',cashier);
 PERFORM pg_temp.reject('expired A cannot steal B lease by reserve',format('SELECT sasms.reserve_order(%L,%L)',ord_a,cashier),'23514');
 PERFORM pg_temp.reject('reserve checks actor before side effects',format('SELECT sasms.reserve_order(%L,%L)',ord_b,gen_random_uuid()),'42501');
 PERFORM sasms.reserve_order(ord_b,cashier);
 PERFORM pg_temp.ok('new lease owner reserves successfully',(SELECT reserved_order_id=ord_b FROM devices WHERE id=dev));
 PERFORM sasms.assert_live_order_lease(online);
 PERFORM pg_temp.ok('ONLINE hold is thirty server minutes',(SELECT hold_expires_at BETWEEN clock_timestamp()+interval '29 minutes 55 seconds' AND clock_timestamp()+interval '30 minutes' FROM sales_orders WHERE id=online));
 PERFORM sasms.cancel_order(ord_b,cashier);
 PERFORM sasms.find_and_lock_device_for_pos(store,pending_order,pending_item,'001abc XYZ-1',cashier);
 PERFORM sasms.reserve_order(pending_order,cashier);
 INSERT INTO payments(id,order_id,method,status,amount) VALUES(payment,pending_order,'CARD','PENDING',1000);
 UPDATE sales_orders SET hold_expires_at=clock_timestamp()-interval '1 second' WHERE id=pending_order;
 PERFORM pg_temp.ok('TTL never releases PENDING payment',NOT sasms.expire_order_reservation(pending_order,cashier));
 PERFORM pg_temp.reject('manual cancel also protects PENDING',format('SELECT sasms.cancel_order(%L,%L)',pending_order,cashier),'23514');
 UPDATE payments SET status='CAPTURED',confirmed_by=cashier,captured_at=now() WHERE id=payment;
 PERFORM sasms.complete_sale(pending_order,cashier);
 PERFORM pg_temp.ok('verified pending callback completes after TTL',(SELECT status='PAID' FROM sales_orders WHERE id=pending_order));
 -- Reserve a different device, then expire without in-flight money.
 SELECT id INTO dev FROM devices WHERE serial='001ABC XYZ-1';
 PERFORM sasms.find_and_lock_device_for_pos(store,plain_order,plain_item,'001ABC XYZ-1',cashier);
 PERFORM sasms.reserve_order(plain_order,cashier);
 UPDATE sales_orders SET hold_expires_at=clock_timestamp()-interval '1 second' WHERE id=plain_order;
 PERFORM pg_temp.reject('expired hold cannot initiate payment',format(
  'INSERT INTO sasms.payments(order_id,method,status,amount) VALUES(%L,''CARD'',''PENDING'',1000)',plain_order),'23514');
 PERFORM pg_temp.ok('expired unpaid reservation releases once',sasms.expire_order_reservation(plain_order,cashier)
  AND NOT sasms.expire_order_reservation(plain_order,cashier));
 PERFORM pg_temp.ok('expired release returns stock',(SELECT status='IN_STOCK' FROM devices WHERE id=dev));
 INSERT INTO warehouses(id,store_id,code,name) VALUES(wh,store,'V4-SECOND-WH','Synthetic warehouse');
 INSERT INTO devices(id,product_id,receipt_item_id,warehouse_id,serial)
 VALUES(accessory,'00000000-0000-4000-8000-000000000031','00000000-0000-4000-8000-000000000062',wh,'V4-HDMI-SECOND');
 PERFORM pg_temp.ok('fallback deduplicates SKU across warehouses',(SELECT count(*)=1 FROM
  sasms.get_cross_sell_fallback_rules(product,store,5) WHERE recommended_product_id='00000000-0000-4000-8000-000000000031'));
 PERFORM pg_temp.ok('fallback sums available stock across store',(SELECT available_stock=2 FROM
  sasms.get_cross_sell_fallback_rules(product,store,5) WHERE recommended_product_id='00000000-0000-4000-8000-000000000031'));
 PERFORM pg_temp.reject('fallback validates limit',format('SELECT * FROM sasms.get_cross_sell_fallback_rules(%L,%L,0)',product,store),'23514');
END $$;
SET LOCAL ROLE sasms_app;
SELECT pg_temp.reject('runtime cannot forge SKU approval',
 'INSERT INTO sasms.product_identifier_profiles DEFAULT VALUES','42501');
SELECT pg_temp.reject('runtime cannot write identifier registry',
 'INSERT INTO sasms.device_identifiers DEFAULT VALUES','42501');
SELECT pg_temp.ok('reader cannot expire reservations',NOT has_function_privilege('sasms_reader','sasms.expire_order_reservation(uuid,uuid)','EXECUTE'));
ROLLBACK;
