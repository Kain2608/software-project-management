-- D34 BR-03: atomic POS 10m / ONLINE 30m holds and pending-payment protection.
ALTER TABLE sasms.sales_orders ADD COLUMN hold_expires_at timestamptz;
CREATE FUNCTION sasms.order_hold_duration(channel text) RETURNS interval
LANGUAGE sql IMMUTABLE PARALLEL SAFE SET search_path=pg_catalog AS $$
 SELECT CASE channel WHEN 'POS' THEN interval '10 minutes' WHEN 'ONLINE' THEN interval '30 minutes' END;
$$;
-- Existing hold start is unknown: preserve a conservative deadline, never grant
-- an extra fresh lease to an existing cart during upgrade.
UPDATE sasms.sales_orders o SET hold_expires_at=o.created_at+sasms.order_hold_duration(o.channel)
WHERE o.status='RESERVED' OR (o.status='DRAFT' AND EXISTS(
 SELECT 1 FROM sasms.order_devices d WHERE d.order_id=o.id AND d.active));

CREATE FUNCTION sasms.assert_live_order_lease(p_order uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE ord sasms.sales_orders;
BEGIN
 SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id=p_order FOR UPDATE;
 IF ord.status<>'DRAFT' THEN RAISE EXCEPTION 'Order is not DRAFT' USING ERRCODE='23514'; END IF;
 IF ord.hold_expires_at IS NULL THEN
  UPDATE sasms.sales_orders SET hold_expires_at=clock_timestamp()+sasms.order_hold_duration(channel) WHERE id=p_order;
 ELSIF ord.hold_expires_at<=clock_timestamp() THEN
  RAISE EXCEPTION 'Order hold has expired; create a new cart' USING ERRCODE='23514';
 END IF;
END $$;

CREATE OR REPLACE FUNCTION sasms.guard_order_child() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE old_order uuid; new_order uuid; ord sasms.sales_orders; dev sasms.devices;
BEGIN
 IF TG_OP<>'INSERT' THEN old_order:=OLD.order_id; END IF;
 IF TG_OP<>'DELETE' THEN new_order:=NEW.order_id; END IF;
 IF TG_OP='UPDATE' AND old_order IS DISTINCT FROM new_order THEN
  RAISE EXCEPTION 'Moving rows between orders is forbidden' USING ERRCODE='23514';
 END IF;
 SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id=coalesce(new_order,old_order) FOR UPDATE;
 IF TG_TABLE_NAME='payments' THEN
  IF TG_OP<>'INSERT' AND OLD.status='CAPTURED' THEN
   RAISE EXCEPTION 'Captured payments are immutable; use a reviewed refund workflow' USING ERRCODE='23514';
  END IF;
  IF ord.status NOT IN ('DRAFT','RESERVED') THEN
   RAISE EXCEPTION 'Order is frozen: %',ord.status USING ERRCODE='23514';
  END IF;
  IF TG_OP='DELETE' THEN
   IF OLD.status='PENDING' THEN RAISE EXCEPTION 'Reconcile pending payment before removal' USING ERRCODE='23514'; END IF;
   RETURN OLD;
  END IF;
  IF NEW.status IN ('PENDING','CAPTURED') THEN
   IF ord.status<>'RESERVED' THEN RAISE EXCEPTION 'Reserve before initiating payment' USING ERRCODE='23514'; END IF;
   -- A verified callback for an already in-flight payment must not lose stock
   -- merely because its response arrives after the original deadline.
   IF NOT (TG_OP='UPDATE' AND OLD.status='PENDING')
      AND (ord.hold_expires_at IS NULL OR ord.hold_expires_at<=clock_timestamp()) THEN
    RAISE EXCEPTION 'Hold expired before payment initiation' USING ERRCODE='23514';
   END IF;
  END IF;
 ELSE
  IF ord.status<>'DRAFT' THEN RAISE EXCEPTION 'Order is frozen: %',ord.status USING ERRCODE='23514'; END IF;
  IF TG_TABLE_NAME='order_devices' AND TG_OP<>'DELETE' THEN
   IF NEW.active THEN
   PERFORM sasms.assert_live_order_lease(ord.id);
   SELECT * INTO STRICT dev FROM sasms.devices WHERE id=NEW.device_id FOR UPDATE NOWAIT;
   IF dev.status<>'IN_STOCK' OR dev.warehouse_id<>ord.warehouse_id THEN
    RAISE EXCEPTION 'Device unavailable in this warehouse' USING ERRCODE='23514';
   END IF;
   IF EXISTS(SELECT 1 FROM sasms.order_devices d JOIN sasms.sales_orders o ON o.id=d.order_id
     WHERE d.device_id=dev.id AND d.active AND d.order_id<>ord.id AND o.status='DRAFT'
       AND coalesce(o.hold_expires_at,o.created_at+sasms.order_hold_duration(o.channel))>clock_timestamp()) THEN
    RAISE EXCEPTION 'Device held by another cart' USING ERRCODE='55P03';
   END IF;
   END IF;
  END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;

-- Preserve established stock/money/audit/outbox behavior, adding guards to the
-- exact previously versioned implementation; fail rather than patch an unknown one.
DO $$ DECLARE definition text; signature text;
BEGIN
 FOREACH signature IN ARRAY ARRAY['sasms.reserve_order(uuid,uuid)',
   'sasms.complete_sale(uuid,uuid)','sasms.cancel_order(uuid,uuid)'] LOOP
  definition:=pg_get_functiondef(signature::regprocedure);
  IF position('SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id = p_order FOR UPDATE;' IN definition)=0 THEN
   RAISE EXCEPTION 'Unexpected order routine: %',signature;
  END IF;
  definition:=replace(definition,
   'SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id = p_order FOR UPDATE;',
   'SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id = p_order FOR UPDATE;
    PERFORM sasms.assert_permission(p_actor,''sales.checkout'',ord.store_id);');
  IF signature='sasms.reserve_order(uuid,uuid)' THEN
   definition:=replace(definition,
    'IF NOT EXISTS (SELECT 1 FROM sasms.sales_order_items WHERE order_id = p_order) THEN',
    'PERFORM sasms.assert_live_order_lease(p_order);
     IF NOT EXISTS (SELECT 1 FROM sasms.sales_order_items WHERE order_id = p_order) THEN');
   definition:=replace(definition,
    'UPDATE sasms.devices SET status = ''RESERVED'', reserved_order_id = p_order WHERE id = dev.id;',
    'IF EXISTS(SELECT 1 FROM sasms.order_devices od JOIN sasms.sales_orders so ON so.id=od.order_id
       WHERE od.device_id=dev.id AND od.active AND od.order_id<>p_order AND so.status=''DRAFT''
       AND coalesce(so.hold_expires_at,so.created_at+sasms.order_hold_duration(so.channel))>clock_timestamp()) THEN
      RAISE EXCEPTION ''Device held by another cart'' USING ERRCODE=''55P03'';
     END IF;
     UPDATE sasms.devices SET status = ''RESERVED'', reserved_order_id = p_order WHERE id = dev.id;');
  ELSIF signature='sasms.complete_sale(uuid,uuid)' THEN
   definition:=replace(definition,
    'SELECT coalesce(sum(amount), 0) INTO captured',
    'IF (ord.hold_expires_at IS NULL OR ord.hold_expires_at<=clock_timestamp()) AND NOT EXISTS(
      SELECT 1 FROM sasms.payments WHERE order_id=p_order AND status IN (''PENDING'',''CAPTURED'')) THEN
      RAISE EXCEPTION ''Order hold expired before payment'' USING ERRCODE=''23514'';
     END IF;
     SELECT coalesce(sum(amount), 0) INTO captured');
  ELSE
   definition:=replace(definition,'status = ''CAPTURED''','status IN (''PENDING'',''CAPTURED'')');
   definition:=replace(definition,'Reconcile captured funds before cancelling','Reconcile pending/captured funds before cancelling');
  END IF;
  EXECUTE definition;
 END LOOP;
END $$;

CREATE FUNCTION sasms.expire_order_reservation(p_order uuid,p_actor uuid) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE ord sasms.sales_orders;
BEGIN
 SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id=p_order FOR UPDATE;
 PERFORM sasms.assert_permission(p_actor,'sales.checkout',ord.store_id);
 IF ord.status NOT IN ('DRAFT','RESERVED') OR ord.hold_expires_at IS NULL
   OR ord.hold_expires_at>clock_timestamp() THEN RETURN false; END IF;
 IF EXISTS(SELECT 1 FROM sasms.payments WHERE order_id=p_order AND status IN ('PENDING','CAPTURED')) THEN
  RETURN false;
 END IF;
 PERFORM sasms.cancel_order(p_order,p_actor);
 RETURN true;
END $$;
REVOKE ALL ON FUNCTION sasms.order_hold_duration(text),sasms.assert_live_order_lease(uuid),
 sasms.expire_order_reservation(uuid,uuid) FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='sasms_app') THEN
  GRANT EXECUTE ON FUNCTION sasms.expire_order_reservation(uuid,uuid) TO sasms_app;
 END IF;
END $$;
COMMENT ON COLUMN sasms.sales_orders.hold_expires_at IS 'BR-03 server deadline, POS 10m / ONLINE 30m from first hold; never refreshed by scans/reserve. PENDING/CAPTURED protect stock until reconciliation.';
