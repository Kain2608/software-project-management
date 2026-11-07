-- DS1 database-only demo. Run on an isolated validation DB after migrations.
-- Uses synthetic identities, not a real login/JWT. Every fixture rolls back.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE actor uuid := gen_random_uuid(); product uuid; purchase uuid := gen_random_uuid();
 po_item uuid := gen_random_uuid(); receipt uuid := gen_random_uuid(); receipt_item uuid := gen_random_uuid();
 key uuid := gen_random_uuid(); ids uuid[]; retry_ids uuid[];
 tag text := upper(replace(gen_random_uuid()::text,'-','')); new_revision integer;
BEGIN
 INSERT INTO sasms.users(id,email,full_name,is_active)
 VALUES(actor,lower('demo-'||tag||'@example.invalid'),'Sprint 1 synthetic demo actor',true);
 INSERT INTO sasms.user_roles(user_id,role_code,store_id) VALUES(actor,'ADMIN',NULL);
 product := sasms.create_product(actor,'S1-DEMO-'||tag,
  '00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000021',
  'Sprint 1 DEMO TV','{"screen_inches":55,"resolution":"4K"}',12000000,24);
 new_revision := sasms.update_product(actor,product,0,'Sprint 1 DEMO TV updated',
  '{"screen_inches":55,"resolution":"4K"}',11900000,24,true);
 IF new_revision<>1 THEN RAISE EXCEPTION 'Unexpected catalog revision'; END IF;
 INSERT INTO sasms.purchase_orders(id,code,supplier_id,warehouse_id,status,created_by)
 VALUES(purchase,'S1-DEMO-PO-'||tag,'00000000-0000-4000-8000-000000000040',
  '00000000-0000-4000-8000-000000000011','APPROVED',actor);
 INSERT INTO sasms.purchase_order_items(id,purchase_order_id,product_id,quantity,unit_cost)
 VALUES(po_item,purchase,product,2,10000000);
 INSERT INTO sasms.goods_receipts(id,code,purchase_order_id,warehouse_id,received_by)
 VALUES(receipt,'S1-DEMO-GR-'||tag,purchase,'00000000-0000-4000-8000-000000000011',actor);
 INSERT INTO sasms.goods_receipt_items(id,receipt_id,purchase_order_id,purchase_order_item_id,product_id,quantity)
 VALUES(receipt_item,receipt,purchase,po_item,product,2);
 ids := sasms.receive_device_batch(receipt_item,key,
  jsonb_build_array(jsonb_build_object('serial',' demo-'||tag||'-01 '),jsonb_build_object('serial','DEMO-'||tag||'-02')),actor);
 retry_ids := sasms.receive_device_batch(receipt_item,key,
  jsonb_build_array(jsonb_build_object('serial','DEMO-'||tag||'-01'),jsonb_build_object('serial','DEMO-'||tag||'-02','imei',NULL)),actor);
 IF ids IS DISTINCT FROM retry_ids OR cardinality(ids)<>2 THEN
  RAISE EXCEPTION 'Unexpected batch replay result';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM sasms.stock_availability WHERE product_id=product AND available=2) THEN
  RAISE EXCEPTION 'Stock does not match imported devices';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM sasms.devices WHERE id=ids[1] AND serial='DEMO-'||tag||'-01') THEN
  RAISE EXCEPTION 'Canonical serial lookup failed';
 END IF;
 RAISE NOTICE 'DEMO PASS: scoped permission, SKU create/update revision, two devices, canonical QR-token-ready lookup, exact retry IDs';
END $$;
ROLLBACK;
