\set ON_ERROR_STOP on
-- Test-only synthetic users/rules; all rows and results roll back.
BEGIN;
SET search_path = sasms, pg_catalog;
CREATE TEMP TABLE s1_results(label text PRIMARY KEY);
CREATE TEMP TABLE s1_fixture(label text PRIMARY KEY, id uuid NOT NULL DEFAULT gen_random_uuid());
INSERT INTO s1_fixture(label) SELECT unnest(ARRAY[
 'admin','warehouse_actor','disabled','sales_actor','unassigned','store','other_store',
 'warehouse','other_warehouse','product','other_product','supplier','po','po_item',
 'receipt','receipt_item','request','other_request','rule'
]);
CREATE FUNCTION pg_temp.f(key text) RETURNS uuid LANGUAGE sql AS
 'SELECT id FROM s1_fixture WHERE label=key';
CREATE FUNCTION pg_temp.ok(value boolean,label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 IF value IS DISTINCT FROM true THEN RAISE EXCEPTION 'ASSERTION FAILED: %',label; END IF;
 INSERT INTO s1_results VALUES(label);
 RAISE NOTICE 'PASS: %',label;
END $$;
CREATE FUNCTION pg_temp.reject(statement text,expected text,label text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE actual text; detail text;
BEGIN
 BEGIN EXECUTE statement;
 EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS actual=RETURNED_SQLSTATE,detail=MESSAGE_TEXT;
 END;
 IF actual IS DISTINCT FROM expected THEN
  RAISE EXCEPTION 'ASSERTION FAILED: %, expected %, got % (%)',label,expected,coalesce(actual,'SUCCESS'),detail;
 END IF;
 INSERT INTO s1_results VALUES(label);
 RAISE NOTICE 'PASS: % [%]',label,expected;
END $$;

SELECT pg_temp.ok((SELECT count(*)=39 FROM pg_tables WHERE schemaname='sasms'),'39 versioned business tables');
SELECT pg_temp.ok((SELECT count(*)=9 FROM roles),'nine roles remain seeded');
SELECT pg_temp.ok((SELECT count(*)=17 FROM permissions),'seventeen explicit permissions remain seeded');
SELECT pg_temp.ok(NOT EXISTS(
 SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace,
 LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a
 WHERE n.nspname='sasms' AND a.grantee=0 AND a.privilege_type='EXECUTE'
),'PUBLIC cannot invoke business functions');

INSERT INTO users(id,email,full_name,auth_subject,is_active)
SELECT id,'s1-'||label||'@example.invalid','S1 synthetic '||label,'urn:test:s1:'||label,label<>'disabled'
FROM s1_fixture WHERE label IN('admin','warehouse_actor','disabled','sales_actor','unassigned');
INSERT INTO stores(id,code,name,address) VALUES
 (pg_temp.f('store'),'S1-TEST-STORE','Synthetic store A','Synthetic address'),
 (pg_temp.f('other_store'),'S1-TEST-OTHER','Synthetic store B','Synthetic address');
INSERT INTO warehouses(id,store_id,code,name) VALUES
 (pg_temp.f('warehouse'),pg_temp.f('store'),'S1-TEST-WH','Synthetic warehouse A'),
 (pg_temp.f('other_warehouse'),pg_temp.f('other_store'),'S1-TEST-OTHER-WH','Synthetic warehouse B');
INSERT INTO user_roles(user_id,role_code,store_id) VALUES
 (pg_temp.f('admin'),'ADMIN',NULL),
 (pg_temp.f('warehouse_actor'),'WAREHOUSE',pg_temp.f('store')),
 (pg_temp.f('disabled'),'ADMIN',NULL),
 (pg_temp.f('sales_actor'),'SALES',pg_temp.f('store'));
SELECT pg_temp.ok(has_permission(pg_temp.f('admin'),'catalog.write',NULL),'global catalog grant accepted');
SELECT pg_temp.ok(has_permission(pg_temp.f('admin'),'inventory.receive',pg_temp.f('other_store')),'explicit global assignment spans active stores');
SELECT pg_temp.ok(has_permission(pg_temp.f('warehouse_actor'),'inventory.receive',pg_temp.f('store')),'warehouse permission accepted in assigned store');
SELECT pg_temp.ok(NOT has_permission(pg_temp.f('warehouse_actor'),'inventory.receive',pg_temp.f('other_store')),'cross-store access denied');
SELECT pg_temp.ok(NOT has_permission(pg_temp.f('warehouse_actor'),'catalog.write',NULL),'store assignment cannot write shared catalog');
SELECT pg_temp.ok(NOT has_permission(pg_temp.f('disabled'),'catalog.write',NULL),'inactive account denied');
SELECT pg_temp.ok(NOT has_permission(pg_temp.f('unassigned'),'catalog.read',NULL),'unassigned active account denied');
SELECT pg_temp.ok(NOT has_permission(pg_temp.f('admin'),'permission.not.defined',NULL),'unknown permission denied');
SELECT pg_temp.ok(NOT has_permission(NULL,'catalog.read',NULL),'missing actor denied');
SELECT pg_temp.reject($q$SELECT assert_permission(pg_temp.f('sales_actor'),'inventory.receive',pg_temp.f('store'))$q$,
 '42501','sales role cannot receive stock');
SELECT pg_temp.reject($q$UPDATE stores SET is_active=false WHERE id=pg_temp.f('store');
 SELECT assert_permission(pg_temp.f('admin'),'inventory.receive',pg_temp.f('store'))$q$,
 '42501','inactive store denied even to global actor');
SELECT pg_temp.reject($q$INSERT INTO user_roles(user_id,role_code,store_id) VALUES(pg_temp.f('admin'),'ADMIN',NULL)$q$,
 '23505','duplicate global role assignment rejected');
SELECT pg_temp.reject($q$INSERT INTO user_roles(user_id,role_code,store_id) VALUES(pg_temp.f('warehouse_actor'),'WAREHOUSE',pg_temp.f('store'))$q$,
 '23505','duplicate scoped role assignment rejected');
SELECT pg_temp.reject($q$INSERT INTO user_roles(user_id,role_code) VALUES(pg_temp.f('unassigned'),'UNKNOWN_ROLE')$q$,
 '23503','unknown role FK rejected');
SELECT pg_temp.reject($q$UPDATE users SET auth_subject='  bad subject  ' WHERE id=pg_temp.f('admin')$q$,
 '23514','identity subject must be canonical');

UPDATE s1_fixture SET id=create_product(pg_temp.f('admin'),' s1-test-tv ',
 '00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000021',
 ' Synthetic television ','{"resolution":"4K","screen_inches":55}',100,24) WHERE label='product';
UPDATE s1_fixture SET id=create_product(pg_temp.f('admin'),'S1-TEST-OTHER',
 '00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000021',
 'Synthetic second product','{}',200,12) WHERE label='other_product';
SELECT pg_temp.ok((SELECT sku='S1-TEST-TV' AND name='Synthetic television' AND revision=0
 FROM products WHERE id=pg_temp.f('product')),'catalog creation canonicalizes SKU and name');
SELECT pg_temp.ok((SELECT count(*)=1 FROM audit_logs WHERE entity_id=pg_temp.f('product') AND action='catalog.create'),
 'catalog creation emits one audit');
SELECT pg_temp.reject($q$SELECT create_product(pg_temp.f('admin'),'s1-test-tv',
 '00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000021','Duplicate','{}',1,1)$q$,
 '23505','SKU canonical uniqueness enforced');
SELECT pg_temp.reject($q$SELECT create_product(pg_temp.f('warehouse_actor'),'S1-FORBIDDEN',
 '00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000021','Forbidden','{}',1,1)$q$,
 '42501','catalog write requires global catalog permission');
SELECT pg_temp.reject($q$SELECT create_product(pg_temp.f('admin'),'SKU WITH SPACE',
 '00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000021','Bad SKU','{}',1,1)$q$,
 '23514','SKU rejects internal whitespace');
SELECT pg_temp.reject($q$SELECT create_product(pg_temp.f('admin'),'S1-NEGATIVE',
 '00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000021','Bad price','{}',-1,1)$q$,
 '23514','catalog rejects negative price');
SELECT pg_temp.reject($q$SELECT create_product(pg_temp.f('admin'),'S1-BAD-SPECS',
 '00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000021','Bad specs','[]',1,1)$q$,
 '23514','specifications must be a JSON object');
SELECT pg_temp.reject($q$SELECT create_product(pg_temp.f('admin'),'S1-HUGE-SPECS',
 '00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000021','Huge specs',jsonb_build_object('text',repeat('x',66000)),1,1)$q$,
 '23514','specification size bounded at 64KiB');
SELECT pg_temp.ok(update_product(pg_temp.f('admin'),pg_temp.f('product'),0,'Revised television','{"screen_inches":55}',110,24,true)=1,
 'optimistic update advances revision');
SELECT pg_temp.reject($q$SELECT update_product(pg_temp.f('admin'),pg_temp.f('product'),0,'Stale update','{}',1,1,true)$q$,
 '40001','stale product revision rejected');
SELECT pg_temp.ok((SELECT revision=1 AND list_price=110 FROM products WHERE id=pg_temp.f('product')) AND
 (SELECT count(*)=1 FROM audit_logs WHERE entity_id=pg_temp.f('product') AND action='catalog.update'),
 'rejected stale update changes no price revision or audit');
SELECT pg_temp.reject($q$SELECT update_product(pg_temp.f('admin'),gen_random_uuid(),0,'Missing','{}',1,1,true)$q$,
 'P0002','missing product update reported');
SELECT update_product(pg_temp.f('admin'),pg_temp.f('other_product'),0,'Archived product','{}',200,12,false);
SELECT pg_temp.ok((SELECT NOT is_active AND revision=1 FROM products WHERE id=pg_temp.f('other_product')),
 'soft archive retains product identity');

INSERT INTO suppliers(id,code,name) VALUES(pg_temp.f('supplier'),'S1-TEST-SUP','Synthetic supplier');
INSERT INTO purchase_orders(id,code,supplier_id,warehouse_id,status,created_by)
 VALUES(pg_temp.f('po'),'S1-TEST-PO',pg_temp.f('supplier'),pg_temp.f('warehouse'),'APPROVED',pg_temp.f('admin'));
INSERT INTO purchase_order_items(id,purchase_order_id,product_id,quantity,unit_cost)
 VALUES(pg_temp.f('po_item'),pg_temp.f('po'),pg_temp.f('product'),20,50);
INSERT INTO goods_receipts(id,code,purchase_order_id,warehouse_id,received_by)
 VALUES(pg_temp.f('receipt'),'S1-TEST-GR',pg_temp.f('po'),pg_temp.f('warehouse'),pg_temp.f('warehouse_actor'));
INSERT INTO goods_receipt_items(id,receipt_id,purchase_order_id,purchase_order_item_id,product_id,quantity)
 VALUES(pg_temp.f('receipt_item'),pg_temp.f('receipt'),pg_temp.f('po'),pg_temp.f('po_item'),pg_temp.f('product'),20);

SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),'S1-DISABLED',NULL,pg_temp.f('disabled'))$q$,
 '42501','single-device legacy entry point enforces active-user permission');
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),'S1-WRONG-ROLE',NULL,pg_temp.f('sales_actor'))$q$,
 '42501','single-device entry point enforces inventory permission');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),gen_random_uuid(),
 '[{"serial":"S1-ONE","imei":null}]',pg_temp.f('unassigned'))$q$,
 '42501','batch entry point denies unassigned user');

INSERT INTO s1_fixture(label,id)
SELECT 'batch-device-'||position,device_id FROM unnest(receive_device_batch(
 pg_temp.f('receipt_item'),pg_temp.f('request'),
 '[{"serial":"  s1-batch-001  ","imei":"123456789012345"},{"serial":"S1-BATCH-002","imei":""}]',
 pg_temp.f('warehouse_actor'))) WITH ORDINALITY AS imported(device_id,position);
SELECT pg_temp.ok((SELECT count(*)=2 FROM devices WHERE receipt_item_id=pg_temp.f('receipt_item')) AND
 (SELECT serial='S1-BATCH-001' AND imei='123456789012345' FROM devices WHERE id=pg_temp.f('batch-device-1')) AND
 (SELECT serial='S1-BATCH-002' AND imei IS NULL FROM devices WHERE id=pg_temp.f('batch-device-2')),
 'batch receives canonical serials and optional IMEI atomically');
SELECT pg_temp.ok((SELECT count(*)=2 FROM inventory_movements WHERE device_id IN(pg_temp.f('batch-device-1'),pg_temp.f('batch-device-2')) AND kind='RECEIVE') AND
 (SELECT count(*)=2 FROM audit_logs WHERE entity_id IN(pg_temp.f('batch-device-1'),pg_temp.f('batch-device-2')) AND action='inventory.receive') AND
 (SELECT count(*)=2 FROM outbox_events WHERE aggregate_id IN(pg_temp.f('batch-device-1'),pg_temp.f('batch-device-2')) AND event_type='DeviceReceived'),
 'batch preserves stock audit and outbox evidence per device');
SELECT pg_temp.ok(receive_device_batch(pg_temp.f('receipt_item'),pg_temp.f('request'),
 '[{"serial":"S1-BATCH-001","imei":"123456789012345"},{"serial":" s1-batch-002 ","imei":null}]',
 pg_temp.f('warehouse_actor'))=ARRAY[pg_temp.f('batch-device-1'),pg_temp.f('batch-device-2')],
 'canonical equivalent retry returns original ordered identifiers');
SELECT pg_temp.ok((SELECT count(*)=2 FROM devices WHERE receipt_item_id=pg_temp.f('receipt_item')) AND
 (SELECT count(*)=1 FROM inventory_import_batches WHERE request_key=pg_temp.f('request')),
 'retry creates no duplicate device or batch record');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),pg_temp.f('request'),
 '[{"serial":"S1-DIFFERENT"}]',pg_temp.f('warehouse_actor'))$q$,
 '23514','same request key with changed payload rejected');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),pg_temp.f('request'),
 '[{"serial":"S1-BATCH-002"},{"serial":"S1-BATCH-001","imei":"123456789012345"}]',pg_temp.f('warehouse_actor'))$q$,
 '23514','request replay preserves payload order');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),pg_temp.f('request'),
 '[{"serial":"S1-BATCH-001","imei":"123456789012345"},{"serial":"S1-BATCH-002"}]',pg_temp.f('admin'))$q$,
 '23514','request key cannot be replayed by another authorized actor');
SELECT pg_temp.reject($q$UPDATE users SET is_active=false WHERE id=pg_temp.f('warehouse_actor');
 SELECT receive_device_batch(pg_temp.f('receipt_item'),pg_temp.f('request'),
 '[{"serial":"S1-BATCH-001","imei":"123456789012345"},{"serial":"S1-BATCH-002"}]',pg_temp.f('warehouse_actor'))$q$,
 '42501','replay does not bypass newly revoked actor access');
UPDATE goods_receipts SET status='CLOSED' WHERE id=pg_temp.f('receipt');
SELECT pg_temp.ok(receive_device_batch(pg_temp.f('receipt_item'),pg_temp.f('request'),
 '[{"serial":"S1-BATCH-001","imei":"123456789012345"},{"serial":"S1-BATCH-002"}]',pg_temp.f('warehouse_actor'))=
 ARRAY[pg_temp.f('batch-device-1'),pg_temp.f('batch-device-2')],
 'identical retry after receipt closure returns prior result');
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),'S1-CLOSED',NULL,pg_temp.f('warehouse_actor'))$q$,
 '23514','new receive remains forbidden after receipt closure');
UPDATE goods_receipts SET status='OPEN' WHERE id=pg_temp.f('receipt');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),gen_random_uuid(),
 '[]',pg_temp.f('warehouse_actor'))$q$,'23514','empty import batch rejected');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),gen_random_uuid(),
 '{}',pg_temp.f('warehouse_actor'))$q$,'23514','import payload must be an array');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),gen_random_uuid(),
 '[42]',pg_temp.f('warehouse_actor'))$q$,'23514','each batch row must be an object');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),gen_random_uuid(),
 '[{"serial":"S1-EXTRA","status":"SOLD"}]',pg_temp.f('warehouse_actor'))$q$,
 '23514','batch rejects unrecognized client-supplied fields');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),gen_random_uuid(),
 '[{"serial":123}]',pg_temp.f('warehouse_actor'))$q$,'23514','serial must be a JSON string');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),gen_random_uuid(),
 '[{"serial":"S1-NUMERIC-IMEI","imei":123456789012345}]',pg_temp.f('warehouse_actor'))$q$,
 '23514','IMEI must retain string representation');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),NULL,
 '[{"serial":"S1-NULL-KEY"}]',pg_temp.f('warehouse_actor'))$q$,'23514','request key is mandatory');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),gen_random_uuid(),
 '[{"serial":"S1-DUP-INNER"},{"serial":" s1-dup-inner "}]',pg_temp.f('warehouse_actor'))$q$,
 '23505','duplicate normalized serial inside batch rejected');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),gen_random_uuid(),
 '[{"serial":"S1-I1","imei":"555555555555555"},{"serial":"S1-I2","imei":"555555555555555"}]',pg_temp.f('warehouse_actor'))$q$,
 '23505','duplicate IMEI inside batch rejected');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),gen_random_uuid(),
 (SELECT jsonb_agg(jsonb_build_object('serial','S1-TOO-MANY-'||i)) FROM generate_series(1,501) AS fixture(i)),
 pg_temp.f('warehouse_actor'))$q$,'23514','import batch capped at five hundred devices');
SELECT pg_temp.reject($q$SELECT receive_device_batch(pg_temp.f('receipt_item'),pg_temp.f('other_request'),
 '[{"serial":"S1-ATOMIC-FIRST"},{"serial":" s1-batch-001 "}]',pg_temp.f('warehouse_actor'))$q$,
 '23505','later global duplicate rejects entire batch');
SELECT pg_temp.ok(NOT EXISTS(SELECT 1 FROM devices WHERE serial='S1-ATOMIC-FIRST') AND
 NOT EXISTS(SELECT 1 FROM inventory_import_batches WHERE request_key=pg_temp.f('other_request')) AND
 (SELECT count(*)=2 FROM devices WHERE receipt_item_id=pg_temp.f('receipt_item')),
 'failed late batch row leaves no first device or idempotency record');
SELECT pg_temp.reject($q$UPDATE goods_receipt_items SET quantity=2 WHERE id=pg_temp.f('receipt_item');
 SELECT receive_device(pg_temp.f('receipt_item'),'S1-OVER-RECEIPT',NULL,pg_temp.f('warehouse_actor'))$q$,
 '23514','batch API preserves receipt quantity limit');
SELECT pg_temp.reject($q$UPDATE purchase_order_items SET quantity=2 WHERE id=pg_temp.f('po_item');
 SELECT receive_device(pg_temp.f('receipt_item'),'S1-OVER-PO',NULL,pg_temp.f('warehouse_actor'))$q$,
 '23514','batch API preserves purchase order quantity limit');
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),'S1-BAD-IMEI','12345',pg_temp.f('warehouse_actor'))$q$,
 '23514','generic IMEI format remains fifteen digits');
SELECT pg_temp.ok((SELECT count(*)=0 FROM identifier_rules),'no invented manufacturer regex is seeded');

-- This is a synthetic test rule, not a real manufacturer policy or approval.
SELECT pg_temp.reject($q$INSERT INTO identifier_rules(brand_id,version,serial_pattern,is_active)
 VALUES('00000000-0000-4000-8000-000000000020',1,'^S1-RULE-[0-9]+$',true)$q$,
 '23514','active identifier rule requires approval and source');
SELECT pg_temp.reject($q$INSERT INTO identifier_rules(brand_id,version,serial_pattern)
 VALUES('00000000-0000-4000-8000-000000000020',1,'^[broken$')$q$,
 '23514','invalid regular expression rejected before activation');
INSERT INTO identifier_rules(id,brand_id,version,serial_pattern,imei_required,source_reference)
 VALUES(pg_temp.f('rule'),'00000000-0000-4000-8000-000000000020',1,'^S1-RULE-[0-9]+$',true,'TEST_ONLY synthetic fixture');
SELECT receive_device(pg_temp.f('receipt_item'),'S1-GENERIC-WHILE-DRAFT',NULL,pg_temp.f('warehouse_actor'));
SELECT pg_temp.ok(EXISTS(SELECT 1 FROM devices WHERE serial='S1-GENERIC-WHILE-DRAFT'),
 'draft rule does not silently enforce a manufacturer format');
UPDATE identifier_rules SET approved_by=pg_temp.f('admin'),approved_at=now(),is_active=true WHERE id=pg_temp.f('rule');
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),'S1-NOT-MATCHING','333333333333333',pg_temp.f('warehouse_actor'))$q$,
 '23514','approved active rule rejects nonmatching serial');
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),'S1-RULE-001',NULL,pg_temp.f('warehouse_actor'))$q$,
 '23514','approved active rule can require IMEI');
SELECT receive_device(pg_temp.f('receipt_item'),'s1-rule-001','333333333333333',pg_temp.f('warehouse_actor'));
SELECT pg_temp.ok(EXISTS(SELECT 1 FROM devices WHERE serial='S1-RULE-001' AND imei='333333333333333'),
 'approved active synthetic rule accepts matching canonical identifier');
SELECT pg_temp.reject($q$UPDATE identifier_rules SET serial_pattern='^CHANGED$' WHERE id=pg_temp.f('rule')$q$,
 '23514','approved rule definition is immutable');
SELECT pg_temp.reject($q$DELETE FROM identifier_rules WHERE id=pg_temp.f('rule')$q$,
 '23514','approved rule history cannot be deleted');
SELECT pg_temp.reject($q$INSERT INTO identifier_rules(brand_id,version,serial_pattern,source_reference,approved_by,approved_at,is_active)
 VALUES('00000000-0000-4000-8000-000000000020',2,'^S1-SECOND$', 'TEST_ONLY second fixture',pg_temp.f('admin'),now(),true)$q$,
 '23505','only one active rule allowed per brand');
UPDATE identifier_rules SET is_active=false WHERE id=pg_temp.f('rule');
SELECT receive_device(pg_temp.f('receipt_item'),'S1-GENERIC-AFTER-DEACTIVATE',NULL,pg_temp.f('warehouse_actor'));
SELECT pg_temp.ok(EXISTS(SELECT 1 FROM devices WHERE serial='S1-GENERIC-AFTER-DEACTIVATE'),
 'rule deactivation restores generic validation for future imports');

SELECT set_config('s1.test_admin',pg_temp.f('admin')::text,true);
SELECT set_config('s1.test_store',pg_temp.f('store')::text,true);
SELECT set_config('s1.test_receipt_item',pg_temp.f('receipt_item')::text,true);
SET LOCAL ROLE sasms_app;
DO $$
DECLARE actor uuid:=current_setting('s1.test_admin')::uuid; product uuid; blocked boolean;
BEGIN
 product:=sasms.create_product(actor,'S1-REAL-APP-CALL','00000000-0000-4000-8000-000000000020',
  '00000000-0000-4000-8000-000000000021','Synthetic runtime-created catalog','{}',300,12);
 PERFORM sasms.receive_device_batch(current_setting('s1.test_receipt_item')::uuid,gen_random_uuid(),
  '[{"serial":"S1-REAL-APP-DEVICE"}]',actor);
 IF NOT sasms.has_permission(actor,'inventory.receive',current_setting('s1.test_store')::uuid) THEN
  RAISE EXCEPTION 'Restricted runtime cannot call permission helper';
 END IF;
 RAISE NOTICE 'PASS: restricted sasms_app calls catalog permission and authorized batch routines';
 blocked:=false;
 BEGIN INSERT INTO sasms.user_roles(user_id,role_code) VALUES(actor,'ADMIN');
 EXCEPTION WHEN insufficient_privilege THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'Runtime could self-grant IAM'; END IF;
 RAISE NOTICE 'PASS: runtime cannot directly assign user roles';
 blocked:=false;
 BEGIN UPDATE sasms.products SET list_price=0 WHERE id=product;
 EXCEPTION WHEN insufficient_privilege THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'Runtime could bypass catalog revision'; END IF;
 RAISE NOTICE 'PASS: runtime cannot directly update catalog';
 blocked:=false;
 BEGIN PERFORM sasms.receive_device_unchecked_s0(current_setting('s1.test_receipt_item')::uuid,'S1-BYPASS',NULL,actor);
 EXCEPTION WHEN insufficient_privilege THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'Runtime could call unchecked legacy receive'; END IF;
 RAISE NOTICE 'PASS: runtime cannot invoke unchecked legacy stock function';
 blocked:=false;
 BEGIN INSERT INTO sasms.inventory_import_batches(request_key,receipt_item_id,actor_id,normalized_payload,device_ids)
  VALUES(gen_random_uuid(),current_setting('s1.test_receipt_item')::uuid,actor,'[]',ARRAY[]::uuid[]);
 EXCEPTION WHEN insufficient_privilege THEN blocked:=true; END;
 IF NOT blocked THEN RAISE EXCEPTION 'Runtime could forge request ledger'; END IF;
 RAISE NOTICE 'PASS: runtime cannot forge import idempotency records';
END $$;
SET LOCAL ROLE NONE;

SELECT count(*) AS passed_assertions FROM s1_results;
ROLLBACK;
