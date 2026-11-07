\set ON_ERROR_STOP on
-- Real PostgreSQL assertions. All fixtures, functions and results roll back.
BEGIN;
SET search_path = sasms, pg_catalog;
CREATE TEMP TABLE test_results (label text PRIMARY KEY);
CREATE TEMP TABLE fixture (label text PRIMARY KEY, id uuid NOT NULL DEFAULT gen_random_uuid());
INSERT INTO fixture(label) SELECT unnest(ARRAY[
 'actor','other_actor','store','other_store','warehouse','other_warehouse',
 'brand','category','product','other_product','supplier','po','po_item','other_po_item',
 'receipt','receipt_item','other_receipt_item','sale','cancel','empty','quantity','atomic'
]);
CREATE FUNCTION pg_temp.f(key text) RETURNS uuid LANGUAGE sql AS
 'SELECT id FROM fixture WHERE label = key';
CREATE FUNCTION pg_temp.ok(value boolean, label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 IF value IS DISTINCT FROM true THEN RAISE EXCEPTION 'ASSERTION FAILED: %', label; END IF;
 INSERT INTO test_results VALUES (label);
 RAISE NOTICE 'PASS: %', label;
END;
$$;
CREATE FUNCTION pg_temp.reject(statement text, expected_state text, label text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE actual_state text; actual_message text;
BEGIN
 BEGIN
  EXECUTE statement;
 EXCEPTION WHEN OTHERS THEN
  GET STACKED DIAGNOSTICS actual_state = RETURNED_SQLSTATE, actual_message = MESSAGE_TEXT;
 END;
 IF actual_state IS DISTINCT FROM expected_state THEN
  RAISE EXCEPTION 'ASSERTION FAILED: %, expected SQLSTATE %, got % (%)',
   label, expected_state, coalesce(actual_state,'SUCCESS'), actual_message;
 END IF;
 INSERT INTO test_results VALUES (label);
 RAISE NOTICE 'PASS: % [%]', label, expected_state;
END;
$$;

SELECT pg_temp.ok((SELECT count(*) = 37 FROM information_schema.tables
 WHERE table_schema = 'sasms' AND table_type = 'BASE TABLE'), '37 baseline tables');
SELECT pg_temp.ok((SELECT count(*) = 9 FROM roles), 'nine seeded business roles');
SELECT pg_temp.ok((SELECT count(*) = 17 FROM permissions), 'seventeen seeded permissions');
SELECT pg_temp.ok(NOT EXISTS (
 SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace,
 LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a
 WHERE n.nspname='sasms' AND a.grantee=0 AND a.privilege_type='EXECUTE'
), 'PUBLIC cannot execute SASMS functions');

INSERT INTO users(id,email,full_name) VALUES
 (pg_temp.f('actor'),'sprint0-test@example.invalid','DB test actor'),
 (pg_temp.f('other_actor'),'sprint0-other@example.invalid','DB test reviewer');
SELECT pg_temp.reject($q$INSERT INTO users(id,email,full_name)
 VALUES(pg_temp.f('actor'),'sprint0-duplicate@example.invalid','Duplicate actor')$q$,
 '23505','duplicate primary key rejected');
SELECT pg_temp.reject($q$INSERT INTO users(id,email,full_name)
 VALUES(NULL,'sprint0-null@example.invalid','Missing key')$q$,
 '23502','primary key cannot be null');
SELECT pg_temp.reject($q$INSERT INTO user_roles(user_id,role_code)
 VALUES(pg_temp.f('actor'),'NONEXISTENT_ROLE')$q$,
 '23503','unknown role foreign key rejected');
INSERT INTO stores(id,code,name,address) VALUES
 (pg_temp.f('store'),'TEST-S0-A','Test A','Test only'),
 (pg_temp.f('other_store'),'TEST-S0-B','Test B','Test only');
INSERT INTO warehouses(id,store_id,code,name) VALUES
 (pg_temp.f('warehouse'),pg_temp.f('store'),'TEST-S0-WA','Test warehouse A'),
 (pg_temp.f('other_warehouse'),pg_temp.f('other_store'),'TEST-S0-WB','Test warehouse B');
INSERT INTO brands(id,name) VALUES (pg_temp.f('brand'),'Sprint0 test brand');
INSERT INTO categories(id,name) VALUES (pg_temp.f('category'),'Sprint0 test category');
INSERT INTO products(id,sku,brand_id,category_id,name,list_price) VALUES
 (pg_temp.f('product'),'TEST-S0-A',pg_temp.f('brand'),pg_temp.f('category'),'Device A',100),
 (pg_temp.f('other_product'),'TEST-S0-B',pg_temp.f('brand'),pg_temp.f('category'),'Device B',200);
INSERT INTO suppliers(id,code,name) VALUES (pg_temp.f('supplier'),'TEST-S0-S','Test supplier');
INSERT INTO purchase_orders(id,code,supplier_id,warehouse_id,status,created_by)
 VALUES (pg_temp.f('po'),'TEST-S0-PO',pg_temp.f('supplier'),pg_temp.f('warehouse'),'APPROVED',pg_temp.f('actor'));
INSERT INTO purchase_order_items(id,purchase_order_id,product_id,quantity,unit_cost) VALUES
 (pg_temp.f('po_item'),pg_temp.f('po'),pg_temp.f('product'),20,50),
 (pg_temp.f('other_po_item'),pg_temp.f('po'),pg_temp.f('other_product'),2,80);
INSERT INTO goods_receipts(id,code,purchase_order_id,warehouse_id,received_by)
 VALUES (pg_temp.f('receipt'),'TEST-S0-GR',pg_temp.f('po'),pg_temp.f('warehouse'),pg_temp.f('actor'));
INSERT INTO goods_receipt_items(id,receipt_id,purchase_order_id,purchase_order_item_id,product_id,quantity) VALUES
 (pg_temp.f('receipt_item'),pg_temp.f('receipt'),pg_temp.f('po'),pg_temp.f('po_item'),pg_temp.f('product'),20),
 (pg_temp.f('other_receipt_item'),pg_temp.f('receipt'),pg_temp.f('po'),pg_temp.f('other_po_item'),pg_temp.f('other_product'),2);

INSERT INTO fixture(label,id) VALUES
 ('sold_device',receive_device(pg_temp.f('receipt_item'),'  test-s0-001  ','123456789012345',pg_temp.f('actor'))),
 ('cancel_device',receive_device(pg_temp.f('receipt_item'),'TEST-S0-002',NULL,pg_temp.f('actor'))),
 ('atomic_device',receive_device(pg_temp.f('receipt_item'),'TEST-S0-003',NULL,pg_temp.f('actor')));
SELECT pg_temp.ok((SELECT serial='TEST-S0-001' AND warehouse_id=pg_temp.f('warehouse')
 FROM devices WHERE id=pg_temp.f('sold_device')), 'receipt normalizes serial and derives warehouse');
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),' test-s0-001 ',NULL,pg_temp.f('actor'))$q$,
 '23505','normalized serial globally unique');
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('other_receipt_item'),'TEST-S0-DUP',NULL,pg_temp.f('actor'));
 SELECT receive_device(pg_temp.f('receipt_item'),'TEST-S0-DUP',NULL,pg_temp.f('actor'))$q$,
 '23505','serial unique across products');
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),'TEST-S0-BADIMEI','12345678901234',pg_temp.f('actor'))$q$,
 '23514','IMEI must contain fifteen digits');
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),'TEST-S0-BADCHAR','12345678901234X',pg_temp.f('actor'))$q$,
 '23514','IMEI rejects nonnumeric characters');
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),'TEST-S0-DUPIMEI','123456789012345',pg_temp.f('actor'))$q$,
 '23505','IMEI unique');
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),'TEST S0 SPACE',NULL,pg_temp.f('actor'))$q$,
 '23514','serial rejects embedded whitespace');
SELECT pg_temp.reject($q$INSERT INTO devices(product_id,receipt_item_id,warehouse_id,serial)
 VALUES(pg_temp.f('product'),pg_temp.f('receipt_item'),pg_temp.f('warehouse'),'lowercase')$q$,
 '23514','direct inserts must use canonical serial');
SELECT pg_temp.reject($q$INSERT INTO devices(product_id,receipt_item_id,warehouse_id,serial)
 VALUES(pg_temp.f('other_product'),pg_temp.f('receipt_item'),pg_temp.f('warehouse'),'TEST-S0-WRONG-PRODUCT')$q$,
 '23503','receipt device product composite FK');
SELECT pg_temp.reject($q$INSERT INTO goods_receipts(code,purchase_order_id,warehouse_id,received_by)
 VALUES('TEST-S0-BAD-WH',pg_temp.f('po'),pg_temp.f('other_warehouse'),pg_temp.f('actor'))$q$,
 '23503','receipt warehouse must match purchase order');
SELECT pg_temp.reject($q$INSERT INTO purchase_order_items(purchase_order_id,product_id,quantity,unit_cost)
 VALUES(pg_temp.f('po'),pg_temp.f('product'),0,1)$q$, '23514','purchase quantity positive');
SELECT pg_temp.reject($q$UPDATE products SET list_price=-1 WHERE id=pg_temp.f('product')$q$,
 '23514','product price nonnegative');
SELECT pg_temp.reject($q$INSERT INTO product_images(product_id,object_key,position,size_bytes)
 VALUES(pg_temp.f('product'),'test.jpg',4,1)$q$, '23514','at most three image positions');
SELECT pg_temp.reject($q$INSERT INTO product_images(product_id,object_key,position,size_bytes)
 VALUES(pg_temp.f('product'),'test.jpg',1,2097153)$q$, '23514','image size at most two MiB');
SELECT pg_temp.reject($q$INSERT INTO stocktakes(code,warehouse_id,status,counted_by,approved_by)
 VALUES('TEST-S0-SELF',pg_temp.f('warehouse'),'APPROVED',pg_temp.f('actor'),pg_temp.f('actor'))$q$,
 '23514','stocktake approval separates duties');

-- A failed actor FK occurs after the device INSERT, proving statement atomicity.
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('receipt_item'),'TEST-S0-ROLLBACK',NULL,gen_random_uuid())$q$,
 '23503','receive rejects missing actor');
SELECT pg_temp.ok(NOT EXISTS(SELECT 1 FROM devices WHERE serial='TEST-S0-ROLLBACK'),
 'failed receive rolls back inserted device');
SELECT pg_temp.reject($q$UPDATE goods_receipts SET status='CLOSED' WHERE id=pg_temp.f('receipt');
 SELECT receive_device(pg_temp.f('receipt_item'),'TEST-S0-CLOSED',NULL,pg_temp.f('actor'))$q$,
 '23514','closed receipt cannot receive devices');
SELECT pg_temp.reject($q$UPDATE purchase_orders SET status='DRAFT' WHERE id=pg_temp.f('po');
 SELECT receive_device(pg_temp.f('receipt_item'),'TEST-S0-DRAFT-PO',NULL,pg_temp.f('actor'))$q$,
 '23514','unapproved purchase order cannot receive devices');
SELECT receive_device(pg_temp.f('other_receipt_item'),'TEST-S0-Q1',NULL,pg_temp.f('actor'));
SELECT receive_device(pg_temp.f('other_receipt_item'),'TEST-S0-Q2',NULL,pg_temp.f('actor'));
SELECT pg_temp.reject($q$SELECT receive_device(pg_temp.f('other_receipt_item'),'TEST-S0-Q3',NULL,pg_temp.f('actor'))$q$,
 '23514','receiving cannot exceed declared quantity');

INSERT INTO sales_orders(id,code,store_id,warehouse_id,channel,created_by)
 SELECT id,'TEST-S0-'||upper(label),pg_temp.f('store'),pg_temp.f('warehouse'),'POS',pg_temp.f('actor')
 FROM fixture WHERE label IN ('sale','cancel','empty','quantity','atomic');
SELECT pg_temp.reject($q$INSERT INTO sales_orders(code,store_id,warehouse_id,channel,created_by)
 VALUES('TEST-S0-WRONG-STORE',pg_temp.f('store'),pg_temp.f('other_warehouse'),'POS',pg_temp.f('actor'))$q$,
 '23503','sales warehouse belongs to store');
INSERT INTO sales_order_items(order_id,product_id,quantity,unit_price)
 SELECT id,pg_temp.f('product'),CASE WHEN label='quantity' THEN 2 ELSE 1 END,100
 FROM fixture WHERE label IN ('sale','cancel','quantity','atomic');
INSERT INTO order_devices(order_item_id,order_id,product_id,device_id)
 SELECT i.id,i.order_id,i.product_id,pg_temp.f(CASE f.label WHEN 'sale' THEN 'sold_device'
 WHEN 'cancel' THEN 'cancel_device' ELSE 'atomic_device' END)
 FROM sales_order_items i JOIN fixture f ON f.id=i.order_id
 WHERE f.label IN ('sale','cancel','quantity','atomic');
SELECT pg_temp.reject($q$INSERT INTO order_devices(order_item_id,order_id,product_id,device_id)
 SELECT id,order_id,product_id,(SELECT id FROM devices WHERE serial='TEST-S0-Q1')
 FROM sales_order_items WHERE order_id=pg_temp.f('sale')$q$,
 '23503','allocated device must match order line product');
UPDATE sales_order_items SET discount_amount=10,tax_rate=10 WHERE order_id=pg_temp.f('sale');
SELECT pg_temp.ok((SELECT line_total=99 FROM sales_order_items WHERE order_id=pg_temp.f('sale')),
 'line total applies discount then tax with numeric rounding');
UPDATE sales_order_items SET discount_amount=0,tax_rate=0 WHERE order_id=pg_temp.f('sale');
SELECT pg_temp.reject($q$UPDATE sales_order_items SET discount_amount=101 WHERE order_id=pg_temp.f('sale')$q$,
 '23514','discount cannot exceed gross line amount');
SELECT pg_temp.reject($q$UPDATE sales_order_items SET tax_rate=101 WHERE order_id=pg_temp.f('sale')$q$,
 '23514','tax rate bounded');
SELECT pg_temp.reject($q$SELECT reserve_order(pg_temp.f('empty'),pg_temp.f('actor'))$q$,
 '23514','empty cart cannot reserve');
SELECT pg_temp.reject($q$SELECT reserve_order(pg_temp.f('quantity'),pg_temp.f('actor'))$q$,
 '23514','allocation count equals quantity');
SELECT pg_temp.reject($q$SELECT reserve_order(pg_temp.f('atomic'),gen_random_uuid())$q$,
 '23503','reserve actor failure occurs after stock update');
SELECT pg_temp.ok((SELECT status='IN_STOCK' AND reserved_order_id IS NULL FROM devices
 WHERE id=pg_temp.f('atomic_device')) AND NOT EXISTS(SELECT 1 FROM inventory_movements
 WHERE order_id=pg_temp.f('atomic')) AND NOT EXISTS(SELECT 1 FROM outbox_events WHERE aggregate_id=pg_temp.f('atomic')),
 'failed reserve rolls back stock movement and outbox');
SELECT pg_temp.ok(reserve_order(pg_temp.f('sale'),pg_temp.f('actor'))=100, 'reserve calculates authoritative total');
SELECT pg_temp.ok(reserve_order(pg_temp.f('sale'),pg_temp.f('actor'))=100 AND
 (SELECT count(*)=1 FROM audit_logs WHERE entity_id=pg_temp.f('sale') AND action='sales.reserve') AND
 (SELECT count(*)=1 FROM outbox_events WHERE aggregate_id=pg_temp.f('sale') AND event_type='OrderReserved'),
 'reserve retry produces one audit and one outbox event');
SELECT pg_temp.reject($q$UPDATE sales_order_items SET quantity=2 WHERE order_id=pg_temp.f('sale')$q$,
 '23514','reserved cart line frozen');
SELECT pg_temp.reject($q$DELETE FROM order_devices WHERE order_id=pg_temp.f('sale')$q$,
 '23514','reserved device selections frozen');
SELECT pg_temp.reject($q$UPDATE sales_order_items SET order_id=pg_temp.f('empty') WHERE order_id=pg_temp.f('sale')$q$,
 '23514','rows cannot move between orders');
SELECT pg_temp.reject($q$SELECT complete_sale(pg_temp.f('sale'),pg_temp.f('actor'))$q$,
 '23514','checkout requires confirmed payment');
SELECT pg_temp.reject($q$INSERT INTO payments(order_id,method,status,amount,confirmed_by,captured_at)
 VALUES(pg_temp.f('sale'),'CASH','CAPTURED',99,pg_temp.f('actor'),now());
 SELECT complete_sale(pg_temp.f('sale'),pg_temp.f('actor'))$q$,
 '23514','underpayment rejected');
SELECT pg_temp.reject($q$INSERT INTO payments(order_id,method,status,amount,confirmed_by,captured_at)
 VALUES(pg_temp.f('sale'),'CASH','CAPTURED',101,pg_temp.f('actor'),now());
 SELECT complete_sale(pg_temp.f('sale'),pg_temp.f('actor'))$q$,
 '23514','overpayment rejected pending reconciliation');
INSERT INTO payments(order_id,method,status,amount,confirmed_by,captured_at)
 VALUES(pg_temp.f('sale'),'CASH','CAPTURED',100,pg_temp.f('actor'),now());
SELECT pg_temp.reject($q$UPDATE payments SET status='FAILED',captured_at=NULL WHERE order_id=pg_temp.f('sale')$q$,
 '23514','captured funds cannot be rewritten');
SELECT pg_temp.reject($q$SELECT complete_sale(pg_temp.f('sale'),gen_random_uuid())$q$,
 '23503','checkout actor failure rolls back sale');
SELECT pg_temp.ok((SELECT status='RESERVED' FROM devices WHERE id=pg_temp.f('sold_device')) AND
 NOT EXISTS(SELECT 1 FROM invoices WHERE order_id=pg_temp.f('sale')),
 'failed checkout keeps reservation and creates no invoice');
SELECT complete_sale(pg_temp.f('sale'),pg_temp.f('actor'));
SELECT complete_sale(pg_temp.f('sale'),pg_temp.f('actor'));
SELECT pg_temp.ok((SELECT status='PAID' AND paid_at IS NOT NULL FROM sales_orders WHERE id=pg_temp.f('sale')) AND
 (SELECT status='SOLD' AND reserved_order_id IS NULL AND sold_order_id=pg_temp.f('sale') FROM devices WHERE id=pg_temp.f('sold_device')) AND
 (SELECT count(*)=1 FROM invoices WHERE order_id=pg_temp.f('sale') AND status='DRAFT') AND
 (SELECT count(*)=1 FROM inventory_movements WHERE order_id=pg_temp.f('sale') AND kind='SALE') AND
 (SELECT count(*)=1 FROM outbox_events WHERE aggregate_id=pg_temp.f('sale') AND event_type='OrderPaid'),
 'checkout retry sells once and retains internal invoice draft');
SELECT pg_temp.reject($q$UPDATE payments SET amount=200 WHERE order_id=pg_temp.f('sale')$q$,
 '23514','late payment callback cannot mutate paid order');
SELECT pg_temp.reject($q$SELECT cancel_order(pg_temp.f('sale'),pg_temp.f('actor'))$q$,
 '23514','completed sale cannot cancel');
SELECT pg_temp.reject($q$SELECT reserve_order(pg_temp.f('quantity'),pg_temp.f('actor'))$q$,
 '23514','quantity mismatch remains rejected');

SELECT reserve_order(pg_temp.f('cancel'),pg_temp.f('actor'));
SELECT pg_temp.reject($q$INSERT INTO payments(order_id,method,status,amount,confirmed_by,captured_at)
 VALUES(pg_temp.f('cancel'),'CASH','CAPTURED',100,pg_temp.f('actor'),now());
 SELECT cancel_order(pg_temp.f('cancel'),pg_temp.f('actor'))$q$,
 '23514','captured funds prevent cancellation');
SELECT pg_temp.reject($q$SELECT cancel_order(pg_temp.f('cancel'),gen_random_uuid())$q$,
 '23503','cancel actor failure rolls back release');
SELECT pg_temp.ok((SELECT status='RESERVED' FROM devices WHERE id=pg_temp.f('cancel_device')),
 'failed cancellation retains reservation');
SELECT cancel_order(pg_temp.f('cancel'),pg_temp.f('actor'));
SELECT cancel_order(pg_temp.f('cancel'),pg_temp.f('actor'));
SELECT pg_temp.ok((SELECT status='IN_STOCK' AND reserved_order_id IS NULL FROM devices WHERE id=pg_temp.f('cancel_device')) AND
 (SELECT status='CANCELLED' FROM sales_orders WHERE id=pg_temp.f('cancel')) AND
 (SELECT count(*)=1 FROM inventory_movements WHERE order_id=pg_temp.f('cancel') AND kind='RELEASE') AND
 (SELECT count(*)=1 FROM outbox_events WHERE aggregate_id=pg_temp.f('cancel') AND event_type='OrderCancelled'),
 'cancellation retry releases once');
SELECT pg_temp.reject($q$SELECT reserve_order(pg_temp.f('cancel'),pg_temp.f('actor'))$q$,
 '23514','cancelled order cannot reserve');

SELECT count(*) AS passed_assertions FROM test_results;
ROLLBACK;
