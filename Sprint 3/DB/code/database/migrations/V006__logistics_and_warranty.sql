-- Sprint 3 / D41, D42, D34 FR-11/12, WAR-12. Existing tables are retained.
-- Migration runner owns the transaction. Never call an external SMS provider here.
ALTER TABLE sasms.products ADD COLUMN requires_installation boolean NOT NULL DEFAULT true;
COMMENT ON COLUMN sasms.products.requires_installation IS
 'D41/D42 SKU configuration. Conservative true for historical/unknown SKU; DBA configures false only from the approved SKU requirement.';
ALTER TABLE sasms.sales_order_items ADD COLUMN requires_installation boolean NOT NULL DEFAULT true;
CREATE FUNCTION sasms.snapshot_order_installation() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
BEGIN
 IF TG_OP='INSERT' OR NEW.product_id IS DISTINCT FROM OLD.product_id THEN
  SELECT requires_installation INTO STRICT NEW.requires_installation FROM sasms.products WHERE id=NEW.product_id;
 ELSIF NEW.requires_installation IS DISTINCT FROM OLD.requires_installation THEN
  RAISE EXCEPTION 'Order installation requirement is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER snapshot_order_installation BEFORE INSERT OR UPDATE ON sasms.sales_order_items
 FOR EACH ROW EXECUTE FUNCTION sasms.snapshot_order_installation();

ALTER TABLE sasms.deliveries ADD COLUMN scheduled_end_at timestamptz;
ALTER TABLE sasms.deliveries ADD CONSTRAINT ck_delivery_s3_window CHECK
 (scheduled_end_at IS NULL OR (scheduled_at IS NOT NULL AND isfinite(scheduled_at)
  AND isfinite(scheduled_end_at) AND scheduled_end_at>scheduled_at));
ALTER TABLE sasms.technician_assignments ADD COLUMN result_reference text;
ALTER TABLE sasms.technician_assignments ADD COLUMN failed_at timestamptz;
ALTER TABLE sasms.technician_assignments DROP CONSTRAINT technician_assignments_status_check;
ALTER TABLE sasms.technician_assignments ADD CONSTRAINT technician_assignments_status_check
 CHECK(status IN ('ASSIGNED','COMPLETED','FAILED','CANCELLED'));
ALTER TABLE sasms.technician_assignments ADD CONSTRAINT ck_assignment_s3_failed_at
 CHECK((status='FAILED')=(failed_at IS NOT NULL));
CREATE INDEX ix_assignment_s3_technician ON sasms.technician_assignments(technician_id,delivery_id) WHERE status='ASSIGNED';
CREATE INDEX ix_delivery_s3_order ON sasms.deliveries(order_id,status);

ALTER TABLE sasms.warranties ADD COLUMN policy_version text NOT NULL DEFAULT 'LEGACY_UNVERSIONED';
ALTER TABLE sasms.warranties ADD COLUMN activation_basis text;
ALTER TABLE sasms.warranties ADD COLUMN activated_at timestamptz;
ALTER TABLE sasms.warranties ADD CONSTRAINT ck_warranty_s3_basis
 CHECK(activation_basis IS NULL OR activation_basis IN ('PAID','INSTALLATION_COMPLETED'));
-- Incompatible duplicate ACTIVE legacy data fails the migration for inspection.
CREATE UNIQUE INDEX uq_warranty_s3_active_device ON sasms.warranties(device_id) WHERE status='ACTIVE';
INSERT INTO sasms.warranty_policies(code,name,duration_months,terms)
 VALUES('WAR-12','WAR-12 / D34 BR-05',12,'12 calendar months; PAID for non-installation, COMPLETED for installation; D34/D42.')
 ON CONFLICT(code) DO NOTHING;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM sasms.warranty_policies WHERE code='WAR-12' AND duration_months<>12) THEN
  RAISE EXCEPTION 'Existing WAR-12 policy differs from D34 BR-05' USING ERRCODE='23514';
 END IF;
END $$;

CREATE FUNCTION sasms.schedule_delivery(
 p_order uuid,p_technician uuid,p_start timestamptz,p_end timestamptz,p_address text,p_actor uuid
) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE ord sasms.sales_orders; job uuid; busy boolean;
BEGIN
 SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id=p_order FOR UPDATE;
 PERFORM sasms.assert_permission(p_actor,'logistics.manage',ord.store_id);
 IF ord.status<>'PAID' THEN RAISE EXCEPTION 'Only PAID orders can be scheduled' USING ERRCODE='23514'; END IF;
 IF p_start IS NULL OR p_end IS NULL OR NOT isfinite(p_start) OR NOT isfinite(p_end)
    OR p_end<=p_start OR p_start<clock_timestamp() THEN
  RAISE EXCEPTION 'A future finite delivery window with end after start is required' USING ERRCODE='23514';
 END IF;
 IF p_address IS NULL OR length(btrim(p_address)) NOT BETWEEN 1 AND 2000 THEN
  RAISE EXCEPTION 'Delivery address is required (1..2000 characters)' USING ERRCODE='23514';
 END IF;
 -- All scheduling calls serialize on the same technician, across stores/orders.
 -- Never infer technician membership merely from a broad permission grant.
 PERFORM 1 FROM sasms.users WHERE id=p_technician FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM sasms.users u JOIN sasms.user_roles ur ON ur.user_id=u.id
  WHERE u.id=p_technician AND u.is_active AND ur.role_code='TECHNICIAN'
   AND (ur.store_id IS NULL OR ur.store_id=ord.store_id))
  OR NOT sasms.has_permission(p_technician,'logistics.execute',ord.store_id) THEN
  RAISE EXCEPTION 'Active store-scoped technician is required' USING ERRCODE='42501';
 END IF;
 IF EXISTS(SELECT 1 FROM sasms.deliveries d WHERE d.order_id=p_order
   AND d.status IN ('PLANNED','ASSIGNED','IN_TRANSIT','DELIVERED')) THEN
  RAISE EXCEPTION 'Order already has an active or completed delivery' USING ERRCODE='23514';
 END IF;
 SELECT EXISTS(SELECT 1 FROM sasms.technician_assignments a JOIN sasms.deliveries d ON d.id=a.delivery_id
  WHERE a.technician_id=p_technician AND a.status='ASSIGNED'
   AND d.status IN ('PLANNED','ASSIGNED','IN_TRANSIT') AND
   (d.scheduled_at IS NULL OR d.scheduled_end_at IS NULL OR
    (d.scheduled_at<p_end+interval '30 minutes' AND d.scheduled_end_at+interval '30 minutes'>p_start))) INTO busy;
 IF busy THEN RAISE EXCEPTION 'Technician schedule conflicts with 30 minute buffer' USING ERRCODE='23P01'; END IF;
 IF NOT EXISTS(SELECT 1 FROM sasms.order_devices od JOIN sasms.devices dev ON dev.id=od.device_id
   WHERE od.order_id=p_order AND od.active AND dev.status='SOLD' AND dev.sold_order_id=p_order) THEN
  RAISE EXCEPTION 'Paid order has no sold devices to deliver' USING ERRCODE='23514';
 END IF;
 INSERT INTO sasms.deliveries(order_id,address_snapshot,scheduled_at,scheduled_end_at,status)
  VALUES(p_order,btrim(p_address),p_start,p_end,'ASSIGNED') RETURNING id INTO job;
 INSERT INTO sasms.delivery_items(delivery_id,order_id,device_id)
  SELECT job,p_order,dev.id FROM sasms.devices dev JOIN sasms.order_devices od ON od.device_id=dev.id
  WHERE od.order_id=p_order AND od.active AND dev.status='SOLD' AND dev.sold_order_id=p_order;
 INSERT INTO sasms.technician_assignments(delivery_id,technician_id,assigned_by)
  VALUES(job,p_technician,p_actor);
 INSERT INTO sasms.audit_logs(actor_id,action,entity_type,entity_id,metadata)
  VALUES(p_actor,'logistics.schedule','delivery',job,jsonb_build_object('order_id',p_order,'technician_id',p_technician));
 INSERT INTO sasms.outbox_events(aggregate_type,aggregate_id,event_type,payload)
  VALUES('delivery',job,'DeliveryScheduled',jsonb_build_object('delivery_id',job,'order_id',p_order,
   'notification_reference',job::text,'template','DELIVERY-SCHEDULED'));
 RETURN job;
END $$;

CREATE FUNCTION sasms.record_delivery_result(p_delivery uuid,p_result text,p_evidence text,p_actor uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE job sasms.deliveries; ord sasms.sales_orders; task sasms.technician_assignments; done timestamptz;
BEGIN
 SELECT * INTO STRICT job FROM sasms.deliveries WHERE id=p_delivery;
 SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id=job.order_id FOR UPDATE;
 SELECT * INTO STRICT job FROM sasms.deliveries WHERE id=p_delivery FOR UPDATE;
 SELECT * INTO STRICT task FROM sasms.technician_assignments WHERE delivery_id=p_delivery FOR UPDATE;
 IF NOT sasms.has_permission(p_actor,'logistics.manage',ord.store_id) THEN
  PERFORM sasms.assert_permission(p_actor,'logistics.execute',ord.store_id);
  IF task.technician_id IS DISTINCT FROM p_actor THEN
   RAISE EXCEPTION 'Only the assigned technician may record this result' USING ERRCODE='42501';
  END IF;
 END IF;
 IF p_result IS NULL OR p_result NOT IN ('COMPLETED','FAILED') OR p_evidence IS NULL
    OR length(btrim(p_evidence)) NOT BETWEEN 1 AND 2000 THEN
  RAISE EXCEPTION 'COMPLETED/FAILED and an evidence reference are required' USING ERRCODE='23514';
 END IF;
 IF task.status=p_result THEN RETURN job.id; END IF;
 IF ord.status<>'PAID' OR task.status<>'ASSIGNED' OR job.status NOT IN ('ASSIGNED','IN_TRANSIT') THEN
  RAISE EXCEPTION 'Delivery result cannot change in this state' USING ERRCODE='23514';
 END IF;
 done:=clock_timestamp();
 UPDATE sasms.technician_assignments SET status=p_result,result_reference=btrim(p_evidence),
  completed_at=CASE WHEN p_result='COMPLETED' THEN done END,
  failed_at=CASE WHEN p_result='FAILED' THEN done END WHERE id=task.id;
 UPDATE sasms.deliveries SET status=CASE WHEN p_result='COMPLETED' THEN 'DELIVERED' ELSE 'FAILED' END,
  delivered_at=CASE WHEN p_result='COMPLETED' THEN done END WHERE id=p_delivery;
 -- FAILED records the job failure only; no stock or money reversal is inferred.
 INSERT INTO sasms.audit_logs(actor_id,action,entity_type,entity_id,metadata)
  VALUES(p_actor,'logistics.'||lower(p_result),'delivery',job.id,jsonb_build_object('result_reference',btrim(p_evidence)));
 RETURN job.id;
END $$;

CREATE FUNCTION sasms.activate_warranty(p_device uuid,p_actor uuid) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE dev sasms.devices; ord sasms.sales_orders; policy sasms.warranty_policies;
 warranty_id uuid; needs_install boolean; basis_time timestamptz; start_day date; basis text;
BEGIN
 -- Match checkout lock order: sales order before device. Recheck device after lock.
 SELECT * INTO STRICT dev FROM sasms.devices WHERE id=p_device;
 IF dev.sold_order_id IS NULL THEN RAISE EXCEPTION 'Device has no paid sale' USING ERRCODE='23514'; END IF;
 SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id=dev.sold_order_id FOR UPDATE;
 PERFORM sasms.assert_permission(p_actor,'warranty.manage',ord.store_id);
 SELECT * INTO STRICT dev FROM sasms.devices WHERE id=p_device FOR UPDATE;
 IF dev.status<>'SOLD' OR dev.sold_order_id IS DISTINCT FROM ord.id OR ord.status<>'PAID' THEN
  RAISE EXCEPTION 'Warranty requires a SOLD device and its PAID order' USING ERRCODE='23514';
 END IF;
 SELECT id INTO warranty_id FROM sasms.warranties WHERE device_id=p_device
  ORDER BY (status='ACTIVE') DESC,starts_on DESC,id LIMIT 1;
 IF FOUND THEN RETURN warranty_id; END IF;
 IF ord.customer_id IS NULL THEN
  RAISE EXCEPTION 'Warranty requires the sale customer identity' USING ERRCODE='23514';
 END IF;
 SELECT i.requires_installation INTO STRICT needs_install FROM sasms.sales_order_items i
  JOIN sasms.order_devices od ON od.order_item_id=i.id
  WHERE od.device_id=p_device AND od.order_id=ord.id AND od.active;
 IF needs_install THEN
  SELECT min(a.completed_at) INTO basis_time FROM sasms.delivery_items di
   JOIN sasms.deliveries d ON d.id=di.delivery_id
   JOIN sasms.technician_assignments a ON a.delivery_id=d.id
   WHERE di.device_id=p_device AND di.order_id=ord.id AND d.status='DELIVERED' AND a.status='COMPLETED';
  IF basis_time IS NULL THEN
   RAISE EXCEPTION 'Installation is not COMPLETED for this device and sale' USING ERRCODE='23514';
  END IF;
  basis:='INSTALLATION_COMPLETED';
 ELSE
  basis_time:=dev.sold_at;
  basis:='PAID';
 END IF;
 SELECT * INTO STRICT policy FROM sasms.warranty_policies WHERE code='WAR-12' FOR SHARE;
 IF policy.duration_months<>12 THEN RAISE EXCEPTION 'WAR-12 policy changed' USING ERRCODE='23514'; END IF;
 start_day:=(basis_time AT TIME ZONE 'Asia/Ho_Chi_Minh')::date;
 INSERT INTO sasms.warranties(device_id,policy_id,customer_id,starts_on,ends_on,policy_version,activation_basis,activated_at)
  VALUES(p_device,policy.id,ord.customer_id,start_day,(start_day+interval '12 months')::date,
   'WAR-12',basis,basis_time) RETURNING id INTO warranty_id;
 INSERT INTO sasms.outbox_events(aggregate_type,aggregate_id,event_type,payload)
  VALUES('warranty',warranty_id,'WarrantyActivated',jsonb_build_object(
   'warranty_id',warranty_id,'device_id',p_device,'customer_id',ord.customer_id,
   'notification_reference',warranty_id::text,'template','WAR-12'));
 INSERT INTO sasms.audit_logs(actor_id,action,entity_type,entity_id,metadata)
  VALUES(p_actor,'warranty.activate','warranty',warranty_id,jsonb_build_object('policy_version','WAR-12','activation_basis',basis));
 RETURN warranty_id;
END $$;

REVOKE ALL ON FUNCTION sasms.snapshot_order_installation(),
 sasms.schedule_delivery(uuid,uuid,timestamptz,timestamptz,text,uuid),
 sasms.record_delivery_result(uuid,text,text,uuid),sasms.activate_warranty(uuid,uuid) FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='sasms_app') THEN
  REVOKE INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON sasms.deliveries,sasms.delivery_items,
   sasms.technician_assignments,sasms.warranty_policies,sasms.warranties,sasms.outbox_events FROM sasms_app;
  GRANT SELECT ON sasms.deliveries,sasms.delivery_items,sasms.technician_assignments,
   sasms.warranty_policies,sasms.warranties TO sasms_app;
  GRANT EXECUTE ON FUNCTION sasms.schedule_delivery(uuid,uuid,timestamptz,timestamptz,text,uuid),
   sasms.record_delivery_result(uuid,text,text,uuid),sasms.activate_warranty(uuid,uuid) TO sasms_app;
 END IF;
END $$;
