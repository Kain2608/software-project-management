-- D56/S3-08: separate committed business events from external notification IO.
-- Worker group provisioned separately; no provider secret or phone in the outbox.
ALTER TABLE sasms.outbox_events
 ADD COLUMN notification_status text NOT NULL DEFAULT 'QUEUED'
  CHECK (notification_status IN ('QUEUED','PROCESSING','ACCEPTED','FAILED','UNKNOWN','MOCK_ACCEPTED')),
 ADD COLUMN notification_locked_at timestamptz,
 ADD COLUMN provider_reference text CHECK (length(provider_reference) <= 128),
 ADD COLUMN notification_error_code text CHECK (notification_error_code ~ '^[A-Za-z0-9_-]{1,64}$');
CREATE INDEX ix_sms_outbox_queued ON sasms.outbox_events(created_at,id)
 WHERE notification_status='QUEUED' AND event_type IN ('WarrantyActivated','DeliveryScheduled');

CREATE FUNCTION sasms.claim_sms_notification(p_event uuid DEFAULT NULL)
RETURNS TABLE(event_id uuid,destination text,template_code text,parameters jsonb,attempt integer)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE ev sasms.outbox_events; phone text; message text;
BEGIN
 SELECT * INTO ev FROM sasms.outbox_events e
 WHERE e.event_type IN ('WarrantyActivated','DeliveryScheduled')
  AND e.notification_status='QUEUED' AND (p_event IS NULL OR e.id=p_event)
 ORDER BY e.created_at,e.id FOR UPDATE SKIP LOCKED LIMIT 1;
 IF NOT FOUND THEN RETURN; END IF;
 IF ev.event_type='DeliveryScheduled' AND NOT EXISTS(
  SELECT 1 FROM sasms.deliveries d WHERE d.id=ev.aggregate_id
   AND d.status IN ('ASSIGNED','IN_TRANSIT') AND d.scheduled_at>clock_timestamp()) THEN
  UPDATE sasms.outbox_events SET notification_status='FAILED',
   notification_error_code='SMS_STALE_EVENT' WHERE id=ev.id;
  RETURN;
 END IF;
 IF ev.event_type='WarrantyActivated' THEN
  SELECT c.phone, 'Bao hanh WAR-12 da kich hoat. Ma: '||w.id::text||
    '. Tu '||w.starts_on::text||' den '||w.ends_on::text||'.'
   INTO phone,message FROM sasms.warranties w
   JOIN sasms.customers c ON c.id=w.customer_id WHERE w.id=ev.aggregate_id;
 ELSE
  SELECT c.phone, 'Lich giao lap: '||to_char(d.scheduled_at AT TIME ZONE 'Asia/Ho_Chi_Minh','YYYY-MM-DD HH24:MI')||
    '. Ma: '||d.id::text||'.'
   INTO phone,message FROM sasms.deliveries d
   JOIN sasms.sales_orders o ON o.id=d.order_id
   JOIN sasms.customers c ON c.id=o.customer_id WHERE d.id=ev.aggregate_id;
 END IF;
 UPDATE sasms.outbox_events e SET notification_status='PROCESSING',
  notification_locked_at=clock_timestamp(),attempts=e.attempts+1,
  notification_error_code=NULL WHERE e.id=ev.id RETURNING e.attempts INTO attempt;
 event_id:=ev.id;
 -- Local Vietnamese phone conversion is deliberately narrow; the adapter validates E.164.
 destination:=CASE WHEN phone ~ '^0[0-9]{9}$' THEN '+84'||substr(phone,2) ELSE phone END;
 template_code:=CASE ev.event_type WHEN 'WarrantyActivated' THEN 'WAR-12' ELSE 'DELIVERY-SCHEDULED' END;
 parameters:=jsonb_build_object('message',coalesce(message,''));
 RETURN NEXT;
END $$;

CREATE FUNCTION sasms.finish_sms_notification(p_event uuid,p_attempt integer,p_status text,
 p_provider_ref text,p_error_code text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE ev sasms.outbox_events;
BEGIN
 IF p_status IS NULL OR p_status NOT IN ('ACCEPTED','FAILED','UNKNOWN','MOCK_ACCEPTED')
  OR (p_provider_ref IS NOT NULL AND p_provider_ref !~ '^[A-Za-z0-9_.:-]{1,128}$')
  OR (p_error_code IS NOT NULL AND p_error_code !~ '^[A-Za-z0-9_-]{1,64}$') THEN
  RAISE EXCEPTION 'Invalid SMS result metadata' USING ERRCODE='23514';
 END IF;
 SELECT * INTO STRICT ev FROM sasms.outbox_events WHERE id=p_event FOR UPDATE;
 IF ev.event_type NOT IN ('WarrantyActivated','DeliveryScheduled')
  OR ev.notification_status<>'PROCESSING' OR ev.attempts IS DISTINCT FROM p_attempt THEN
  RAISE EXCEPTION 'SMS claim is stale or event is not processing' USING ERRCODE='23514';
 END IF;
 UPDATE sasms.outbox_events SET notification_status=p_status,provider_reference=p_provider_ref,
  notification_error_code=p_error_code,
  published_at=CASE WHEN p_status='ACCEPTED' THEN clock_timestamp() ELSE NULL END
 WHERE id=p_event;
END $$;

-- FAILED is an explicit rejection/configuration failure. UNKNOWN and abandoned
-- PROCESSING require provider reconciliation; they must never auto-requeue.
CREATE FUNCTION sasms.requeue_failed_sms(p_event uuid,p_actor uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE ev sasms.outbox_events; scope uuid; permission text;
BEGIN
 SELECT * INTO STRICT ev FROM sasms.outbox_events WHERE id=p_event FOR UPDATE;
 IF ev.event_type='WarrantyActivated' THEN
  SELECT o.store_id INTO STRICT scope FROM sasms.warranties w
   JOIN sasms.devices d ON d.id=w.device_id JOIN sasms.sales_orders o ON o.id=d.sold_order_id
   WHERE w.id=ev.aggregate_id;
  permission:='warranty.manage';
 ELSIF ev.event_type='DeliveryScheduled' THEN
  SELECT o.store_id INTO STRICT scope FROM sasms.deliveries d
   JOIN sasms.sales_orders o ON o.id=d.order_id WHERE d.id=ev.aggregate_id;
  permission:='logistics.manage';
 ELSE RAISE EXCEPTION 'Not an SMS event' USING ERRCODE='23514'; END IF;
 PERFORM sasms.assert_permission(p_actor,permission,scope);
 IF ev.notification_status<>'FAILED' OR ev.attempts>=3 THEN
  RAISE EXCEPTION 'SMS is not safely retryable or attempt limit reached' USING ERRCODE='23514';
 END IF;
 UPDATE sasms.outbox_events SET notification_status='QUEUED',notification_locked_at=NULL,
  provider_reference=NULL,notification_error_code=NULL WHERE id=p_event;
 INSERT INTO sasms.audit_logs(actor_id,action,entity_type,entity_id)
  VALUES(p_actor,'sms.requeue','outbox_event',p_event);
END $$;

REVOKE ALL ON FUNCTION sasms.claim_sms_notification(uuid),
 sasms.finish_sms_notification(uuid,integer,text,text,text),sasms.requeue_failed_sms(uuid,uuid) FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='sasms_app') THEN
  GRANT EXECUTE ON FUNCTION sasms.requeue_failed_sms(uuid,uuid) TO sasms_app;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='sasms_sms_worker') THEN
  GRANT USAGE ON SCHEMA sasms TO sasms_sms_worker;
  GRANT EXECUTE ON FUNCTION sasms.claim_sms_notification(uuid),
   sasms.finish_sms_notification(uuid,integer,text,text,text) TO sasms_sms_worker;
 END IF;
END $$;
COMMENT ON COLUMN sasms.outbox_events.notification_status IS
 'SMS transport state only. ACCEPTED is not delivered; MOCK_ACCEPTED never means provider success. UNKNOWN/PROCESSING require reconciliation, never automatic resend.';
