-- Isolated PostgreSQL 16 fixtures; all business data is rolled back.
-- Multi-connection scheduling/warranty races belong to validate_sprint3.py.
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
CREATE FUNCTION pg_temp.make_s3_sale(p_product uuid,p_receipt uuid,p_customer uuid) RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE ord uuid:=gen_random_uuid(); item uuid:=gen_random_uuid(); dev uuid:=gen_random_uuid();
 actor uuid:=current_setting('s3.dispatcher')::uuid;
BEGIN
 INSERT INTO sasms.devices(id,product_id,receipt_item_id,warehouse_id,serial)
  VALUES(dev,p_product,p_receipt,'00000000-0000-4000-8000-000000000011','S3-'||upper(gen_random_uuid()::text));
 INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,customer_id,channel,created_by)
  VALUES(ord,'S3-'||ord,'00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000011',p_customer,'POS',actor);
 INSERT INTO sasms.sales_order_items(id,order_id,product_id,quantity,unit_price) VALUES(item,ord,p_product,1,100);
 INSERT INTO sasms.order_devices(order_item_id,order_id,product_id,device_id) VALUES(item,ord,p_product,dev);
 PERFORM sasms.reserve_order(ord,actor);
 INSERT INTO sasms.payments(order_id,method,status,amount,confirmed_by,captured_at)
  VALUES(ord,'CASH','CAPTURED',100,actor,clock_timestamp());
 PERFORM sasms.complete_sale(ord,actor);
 RETURN ord;
END $$;
CREATE FUNCTION pg_temp.activate_then_fail(p_device uuid,p_actor uuid) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 PERFORM sasms.activate_warranty(p_device,p_actor);
 RAISE EXCEPTION 'Synthetic downstream rollback' USING ERRCODE='40001';
END $$;

DO $$
DECLARE dispatcher uuid:=gen_random_uuid(); tech uuid:=gen_random_uuid(); other_tech uuid:=gen_random_uuid();
 outsider uuid:=gen_random_uuid(); inactive uuid:=gen_random_uuid(); other_store uuid:=gen_random_uuid();
 customer uuid:=gen_random_uuid(); ord uuid; draft uuid:=gen_random_uuid(); draft_item uuid:=gen_random_uuid(); sig text;
BEGIN
 FOREACH sig IN ARRAY ARRAY[
  'sasms.schedule_delivery(uuid,uuid,timestamptz,timestamptz,text,uuid)',
  'sasms.record_delivery_result(uuid,text,text,uuid)','sasms.activate_warranty(uuid,uuid)'] LOOP
  PERFORM pg_temp.assert_true('app allowlist and reader denied: '||sig,
   has_function_privilege('sasms_app',sig,'EXECUTE') AND NOT has_function_privilege('sasms_reader',sig,'EXECUTE'));
  PERFORM pg_temp.assert_true('PUBLIC EXECUTE revoked: '||sig,
   NOT EXISTS(SELECT 1 FROM pg_proc p,LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a
    WHERE p.oid=sig::regprocedure AND a.grantee=0 AND a.privilege_type='EXECUTE'));
 END LOOP;
 INSERT INTO sasms.stores(id,code,name,address) VALUES(other_store,'S3-OTHER-'||other_store,'Synthetic other store','Synthetic');
 INSERT INTO sasms.users(id,email,full_name,is_active) VALUES
  (dispatcher,dispatcher||'@example.invalid','Synthetic dispatcher',true),
  (tech,tech||'@example.invalid','Synthetic technician',true),
  (other_tech,other_tech||'@example.invalid','Synthetic second technician',true),
  (outsider,outsider||'@example.invalid','Synthetic other store dispatcher',true),
  (inactive,inactive||'@example.invalid','Synthetic inactive technician',false);
 INSERT INTO sasms.user_roles(user_id,role_code,store_id) VALUES
  (dispatcher,'DISPATCHER','00000000-0000-4000-8000-000000000010'),
  (dispatcher,'CUSTOMER_SERVICE','00000000-0000-4000-8000-000000000010'),
  (dispatcher,'CASHIER','00000000-0000-4000-8000-000000000010'),
  (tech,'TECHNICIAN','00000000-0000-4000-8000-000000000010'),
  (other_tech,'TECHNICIAN','00000000-0000-4000-8000-000000000010'),
  (outsider,'DISPATCHER',other_store),(outsider,'TECHNICIAN',other_store),
  (outsider,'CUSTOMER_SERVICE',other_store),
  (inactive,'TECHNICIAN','00000000-0000-4000-8000-000000000010');
 INSERT INTO sasms.customers(id,code,full_name,phone) VALUES(customer,'S3-'||customer,'Synthetic customer','0912345678');
 PERFORM set_config('s3.dispatcher',dispatcher::text,true);
 PERFORM set_config('s3.tech',tech::text,true);
 PERFORM set_config('s3.other_tech',other_tech::text,true);
 PERFORM set_config('s3.outsider',outsider::text,true);
 PERFORM set_config('s3.inactive',inactive::text,true);
 UPDATE sasms.products SET requires_installation=true WHERE id='00000000-0000-4000-8000-000000000030';
 UPDATE sasms.products SET requires_installation=false WHERE id='00000000-0000-4000-8000-000000000031';
 ord:=pg_temp.make_s3_sale('00000000-0000-4000-8000-000000000030','00000000-0000-4000-8000-000000000061',customer);
 PERFORM set_config('s3.install_order',ord::text,true);
 ord:=pg_temp.make_s3_sale('00000000-0000-4000-8000-000000000030','00000000-0000-4000-8000-000000000061',customer);
 PERFORM set_config('s3.second_order',ord::text,true);
 ord:=pg_temp.make_s3_sale('00000000-0000-4000-8000-000000000031','00000000-0000-4000-8000-000000000062',customer);
 UPDATE sasms.devices SET sold_at='2024-02-29 10:00:00+00' WHERE sold_order_id=ord;
 PERFORM set_config('s3.noninstall_order',ord::text,true);
 ord:=pg_temp.make_s3_sale('00000000-0000-4000-8000-000000000031','00000000-0000-4000-8000-000000000062',NULL);
 PERFORM set_config('s3.no_customer_order',ord::text,true);
 INSERT INTO sasms.sales_orders(id,code,store_id,warehouse_id,customer_id,channel,created_by)
  VALUES(draft,'S3-DRAFT-'||draft,'00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000011',customer,'POS',dispatcher);
 INSERT INTO sasms.sales_order_items(id,order_id,product_id,quantity,unit_price)
  VALUES(draft_item,draft,'00000000-0000-4000-8000-000000000030',1,100);
 PERFORM set_config('s3.draft',draft::text,true);
 PERFORM set_config('s3.draft_item',draft_item::text,true);
END $$;

SET LOCAL ROLE sasms_reader;
SELECT pg_temp.expect_state('reader cannot activate warranty',format('SELECT sasms.activate_warranty(%L,%L)',gen_random_uuid(),current_setting('s3.dispatcher')),'42501');
SET LOCAL ROLE sasms_app;
DO $$
DECLARE actor uuid:=current_setting('s3.dispatcher')::uuid; tech uuid:=current_setting('s3.tech')::uuid;
 ord uuid:=current_setting('s3.install_order')::uuid; second_ord uuid:=current_setting('s3.second_order')::uuid;
 outside uuid:=current_setting('s3.outsider')::uuid; dev uuid; noninstall uuid; job uuid; second_job uuid; replacement uuid;
 wid uuid; window_start timestamptz:=clock_timestamp()+interval '2 days'; window_end timestamptz;
BEGIN
 window_end:=window_start+interval '1 hour';
 SELECT id INTO STRICT dev FROM sasms.devices WHERE sold_order_id=ord;
 SELECT id INTO STRICT noninstall FROM sasms.devices WHERE sold_order_id=current_setting('s3.noninstall_order')::uuid;
 PERFORM pg_temp.expect_state('app cannot mutate logistics tables','INSERT INTO sasms.deliveries(order_id,address_snapshot) VALUES(gen_random_uuid(),''Synthetic'')','42501');
 PERFORM pg_temp.expect_state('app cannot mutate warranty tables','DELETE FROM sasms.warranties WHERE false','42501');
 PERFORM pg_temp.expect_state('draft installation snapshot cannot be forged',format('UPDATE sasms.sales_order_items SET requires_installation=false WHERE id=%L',current_setting('s3.draft_item')),'23514');
 PERFORM pg_temp.expect_state('only PAID may schedule',format('SELECT sasms.schedule_delivery(%L,%L,%L,%L,%L,%L)',current_setting('s3.draft'),tech,window_start,window_end,'Synthetic',actor),'23514');
 PERFORM pg_temp.expect_state('wrong store cannot schedule',format('SELECT sasms.schedule_delivery(%L,%L,%L,%L,%L,%L)',ord,tech,window_start,window_end,'Synthetic',outside),'42501');
 PERFORM pg_temp.expect_state('inactive technician cannot schedule',format('SELECT sasms.schedule_delivery(%L,%L,%L,%L,%L,%L)',ord,current_setting('s3.inactive'),window_start,window_end,'Synthetic',actor),'42501');
 PERFORM pg_temp.expect_state('wrong store technician cannot schedule',format('SELECT sasms.schedule_delivery(%L,%L,%L,%L,%L,%L)',ord,outside,window_start,window_end,'Synthetic',actor),'42501');
 PERFORM pg_temp.expect_state('invalid end cannot schedule',format('SELECT sasms.schedule_delivery(%L,%L,%L,%L,%L,%L)',ord,tech,window_start,window_start,'Synthetic',actor),'23514');
 job:=sasms.schedule_delivery(ord,tech,window_start,window_end,'Synthetic address',actor);
 PERFORM pg_temp.assert_true('schedule links matching sold device',EXISTS(SELECT 1 FROM sasms.delivery_items WHERE delivery_id=job AND device_id=dev AND order_id=ord));
 PERFORM pg_temp.assert_true('schedule notification once without phone',
  (SELECT count(*)=1 AND bool_and(NOT(payload ? 'phone')) FROM sasms.outbox_events WHERE aggregate_id=job AND event_type='DeliveryScheduled'));
 PERFORM pg_temp.expect_state('29 minute buffer rejected',format('SELECT sasms.schedule_delivery(%L,%L,%L,%L,%L,%L)',second_ord,tech,window_end+interval '29 minutes',window_end+interval '89 minutes','Synthetic',actor),'23P01');
 second_job:=sasms.schedule_delivery(second_ord,tech,window_end+interval '30 minutes',window_end+interval '90 minutes','Synthetic',actor);
 PERFORM pg_temp.assert_true('exactly 30 minute buffer accepted',second_job IS NOT NULL);
 PERFORM pg_temp.expect_state('unassigned technician result rejected',format('SELECT sasms.record_delivery_result(%L,%L,%L,%L)',job,'COMPLETED','synthetic://proof',current_setting('s3.other_tech')),'42501');
 PERFORM pg_temp.expect_state('installation incomplete blocks warranty',format('SELECT sasms.activate_warranty(%L,%L)',dev,actor),'23514');
 PERFORM pg_temp.assert_true('rejected activation leaves no warranty/SMS',NOT EXISTS(SELECT 1 FROM sasms.warranties WHERE device_id=dev)
  AND NOT EXISTS(SELECT 1 FROM sasms.outbox_events WHERE event_type='WarrantyActivated' AND payload->>'device_id'=dev::text));
 PERFORM sasms.record_delivery_result(job,'FAILED','synthetic://failed-attempt',tech);
 PERFORM pg_temp.assert_true('failed job leaves SOLD stock',EXISTS(SELECT 1 FROM sasms.devices WHERE id=dev AND status='SOLD'));
 PERFORM pg_temp.expect_state('failed installation blocks warranty',format('SELECT sasms.activate_warranty(%L,%L)',dev,actor),'23514');
 replacement:=sasms.schedule_delivery(ord,tech,window_start+interval '1 day',window_end+interval '1 day','Synthetic retry',actor);
 PERFORM sasms.record_delivery_result(replacement,'COMPLETED','synthetic://completed-proof',tech);
 PERFORM pg_temp.assert_true('same delivery result is idempotent',sasms.record_delivery_result(replacement,'COMPLETED','synthetic://completed-proof',tech)=replacement);
 PERFORM pg_temp.expect_state('terminal delivery result cannot change',format('SELECT sasms.record_delivery_result(%L,%L,%L,%L)',replacement,'FAILED','synthetic://changed',actor),'23514');
 PERFORM pg_temp.expect_state('wrong store cannot activate',format('SELECT sasms.activate_warranty(%L,%L)',dev,outside),'42501');
 wid:=sasms.activate_warranty(dev,tech);
 PERFORM pg_temp.assert_true('installation warranty uses completed device job time',EXISTS(SELECT 1 FROM sasms.warranties w
  JOIN sasms.technician_assignments a ON a.delivery_id=replacement WHERE w.id=wid AND w.activation_basis='INSTALLATION_COMPLETED'
  AND w.starts_on=(a.completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AND w.activated_at=a.completed_at));
 PERFORM pg_temp.assert_true('duplicate scan returns original warranty',sasms.activate_warranty(dev,actor)=wid);
 PERFORM pg_temp.assert_true('one active warranty and one SMS event',
  (SELECT count(*)=1 FROM sasms.warranties WHERE device_id=dev) AND
  (SELECT count(*)=1 FROM sasms.outbox_events WHERE aggregate_id=wid AND event_type='WarrantyActivated'));
 PERFORM set_config('s3.warranty',wid::text,true);
 PERFORM set_config('s3.sms_event',(SELECT id::text FROM sasms.outbox_events WHERE aggregate_id=wid AND event_type='WarrantyActivated'),true);
 PERFORM set_config('s3.delivery_sms_event',(SELECT id::text FROM sasms.outbox_events WHERE aggregate_id=second_job AND event_type='DeliveryScheduled'),true);
 PERFORM set_config('s3.stale_delivery_sms_event',(SELECT id::text FROM sasms.outbox_events WHERE aggregate_id=job AND event_type='DeliveryScheduled'),true);
 PERFORM pg_temp.expect_state('downstream rollback is atomic',format('SELECT pg_temp.activate_then_fail(%L,%L)',noninstall,actor),'40001');
 PERFORM pg_temp.assert_true('rollback leaves no orphan SMS',NOT EXISTS(SELECT 1 FROM sasms.warranties WHERE device_id=noninstall)
  AND NOT EXISTS(SELECT 1 FROM sasms.outbox_events WHERE event_type='WarrantyActivated' AND payload->>'device_id'=noninstall::text));
 wid:=sasms.activate_warranty(noninstall,actor);
 PERFORM pg_temp.assert_true('WAR-12 leap day uses sale date and calendar month end',EXISTS(SELECT 1 FROM sasms.warranties
  WHERE id=wid AND starts_on=DATE '2024-02-29' AND ends_on=DATE '2025-02-28' AND policy_version='WAR-12' AND activation_basis='PAID'));
 PERFORM pg_temp.expect_state('missing sale customer rejected',format('SELECT sasms.activate_warranty(%L,%L)',
  (SELECT id FROM sasms.devices WHERE sold_order_id=current_setting('s3.no_customer_order')::uuid),actor),'23514');
END $$;
SELECT pg_temp.expect_state('app cannot claim SMS',format('SELECT * FROM sasms.claim_sms_notification(%L)',current_setting('s3.sms_event')),'42501');
SET LOCAL ROLE sasms_sms_worker;
DO $$
DECLARE claimed record; eid uuid:=current_setting('s3.sms_event')::uuid;
BEGIN
 PERFORM pg_temp.assert_true('failed delivery notification is suppressed',NOT EXISTS(
  SELECT 1 FROM sasms.claim_sms_notification(current_setting('s3.stale_delivery_sms_event')::uuid)));
 SELECT * INTO STRICT claimed FROM sasms.claim_sms_notification(eid);
 PERFORM pg_temp.assert_true('worker claim resolves normalized destination and message',
  claimed.event_id=eid AND claimed.destination='+84912345678' AND claimed.template_code='WAR-12'
  AND claimed.attempt=1 AND length(claimed.parameters->>'message')>0);
 PERFORM pg_temp.assert_true('processing event cannot be claimed twice',NOT EXISTS(SELECT 1 FROM sasms.claim_sms_notification(eid)));
 PERFORM sasms.finish_sms_notification(eid,1,'FAILED',NULL,'SYNTHETIC_REJECTION');
END $$;
SET LOCAL ROLE sasms_app;
DO $$
DECLARE eid uuid:=current_setting('s3.sms_event')::uuid;
BEGIN
 PERFORM pg_temp.assert_true('stale delivery SMS is FAILED without transport attempt',EXISTS(
  SELECT 1 FROM sasms.outbox_events WHERE id=current_setting('s3.stale_delivery_sms_event')::uuid
  AND notification_status='FAILED' AND notification_error_code='SMS_STALE_EVENT' AND attempts=0));
 PERFORM pg_temp.assert_true('SMS FAILED preserves active warranty',
  EXISTS(SELECT 1 FROM sasms.warranties WHERE id=current_setting('s3.warranty')::uuid AND status='ACTIVE')
  AND EXISTS(SELECT 1 FROM sasms.outbox_events WHERE id=eid AND notification_status='FAILED' AND attempts=1));
 PERFORM pg_temp.expect_state('wrong store cannot retry SMS',format('SELECT sasms.requeue_failed_sms(%L,%L)',eid,current_setting('s3.outsider')),'42501');
 PERFORM sasms.requeue_failed_sms(eid,current_setting('s3.dispatcher')::uuid);
END $$;
SET LOCAL ROLE sasms_sms_worker;
DO $$
DECLARE claimed record; eid uuid:=current_setting('s3.sms_event')::uuid; delivery_eid uuid:=current_setting('s3.delivery_sms_event')::uuid;
BEGIN
 SELECT * INTO STRICT claimed FROM sasms.claim_sms_notification(eid);
 PERFORM pg_temp.assert_true('retry retains event UUID and increments attempt',claimed.event_id=eid AND claimed.attempt=2);
 PERFORM pg_temp.expect_state('stale worker result rejected',format('SELECT sasms.finish_sms_notification(%L,1,%L,NULL,NULL)',eid,'ACCEPTED'),'23514');
 PERFORM sasms.finish_sms_notification(eid,2,'UNKNOWN',NULL,'SYNTHETIC_TIMEOUT');
 SELECT * INTO STRICT claimed FROM sasms.claim_sms_notification(delivery_eid);
 PERFORM pg_temp.assert_true('delivery event resolves schedule template',claimed.template_code='DELIVERY-SCHEDULED' AND claimed.attempt=1);
 PERFORM sasms.finish_sms_notification(delivery_eid,1,'MOCK_ACCEPTED','stub-reference',NULL);
END $$;
SET LOCAL ROLE sasms_app;
SELECT pg_temp.expect_state('UNKNOWN cannot be blindly retried',format('SELECT sasms.requeue_failed_sms(%L,%L)',current_setting('s3.sms_event'),current_setting('s3.dispatcher')),'23514');
SELECT pg_temp.assert_true('mock acceptance never publishes as real provider',
 EXISTS(SELECT 1 FROM sasms.outbox_events WHERE id=current_setting('s3.delivery_sms_event')::uuid
  AND notification_status='MOCK_ACCEPTED' AND published_at IS NULL));
RESET ROLE;
ROLLBACK;
