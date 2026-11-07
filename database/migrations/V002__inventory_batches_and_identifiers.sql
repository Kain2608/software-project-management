-- S1-06 / TSK-01-04; S1-07 DB support. Pure SQL, runner owns the transaction.
-- Generic constraints stay in force. Manufacturer rules require actual approval.
CREATE TABLE sasms.identifier_rules (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 brand_id uuid NOT NULL REFERENCES sasms.brands(id),
 version integer NOT NULL CHECK (version > 0),
 serial_pattern text NOT NULL CHECK (length(serial_pattern) BETWEEN 2 AND 256
     AND left(serial_pattern,1)='^' AND right(serial_pattern,1)='$'),
 imei_required boolean NOT NULL DEFAULT false,
 source_reference text,
 approved_by uuid REFERENCES sasms.users(id),
 approved_at timestamptz,
 is_active boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE (brand_id,version),
 CHECK ((approved_by IS NULL) = (approved_at IS NULL)),
 CHECK (approved_at IS NULL OR length(btrim(source_reference)) > 0),
 CHECK (NOT is_active OR (approved_at IS NOT NULL AND approved_by IS NOT NULL AND source_reference IS NOT NULL))
);
CREATE UNIQUE INDEX uq_identifier_rules_active_brand ON sasms.identifier_rules(brand_id) WHERE is_active;

CREATE TABLE sasms.inventory_import_batches (
 request_key uuid PRIMARY KEY,
 receipt_item_id uuid NOT NULL REFERENCES sasms.goods_receipt_items(id),
 actor_id uuid NOT NULL REFERENCES sasms.users(id),
 normalized_payload jsonb NOT NULL CHECK (jsonb_typeof(normalized_payload)='array'
     AND jsonb_array_length(normalized_payload) BETWEEN 1 AND 500),
 device_ids uuid[] NOT NULL CHECK (cardinality(device_ids) BETWEEN 1 AND 500),
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK (cardinality(device_ids) = jsonb_array_length(normalized_payload))
);
CREATE INDEX ix_import_batches_receipt_date ON sasms.inventory_import_batches(receipt_item_id,created_at DESC);

CREATE FUNCTION sasms.guard_identifier_rule() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
BEGIN
 -- Serialize rule activation with device validation on the same brand.
 IF TG_OP='DELETE' THEN
  PERFORM 1 FROM sasms.brands WHERE id=OLD.brand_id FOR UPDATE;
  IF OLD.approved_at IS NOT NULL THEN
   RAISE EXCEPTION 'Approved identifier rules are retained; deactivate instead' USING ERRCODE='23514';
  END IF;
  RETURN OLD;
 END IF;
 IF TG_OP='UPDATE' AND NEW.brand_id IS DISTINCT FROM OLD.brand_id THEN
  RAISE EXCEPTION 'Rule brand is immutable; create a new rule version' USING ERRCODE='23514';
 END IF;
 PERFORM 1 FROM sasms.brands WHERE id=NEW.brand_id FOR UPDATE;
 IF TG_OP='UPDATE' AND OLD.approved_at IS NOT NULL
    AND (to_jsonb(NEW)-'is_active') IS DISTINCT FROM (to_jsonb(OLD)-'is_active') THEN
  RAISE EXCEPTION 'Approved rule definition is immutable; create a new version' USING ERRCODE='23514';
 END IF;
 -- Parse the regex before it can be approved, even for a draft rule.
 BEGIN
  PERFORM 'REGEX-PROBE' ~ NEW.serial_pattern;
 EXCEPTION WHEN invalid_regular_expression THEN
  RAISE EXCEPTION 'Invalid serial regular expression' USING ERRCODE='23514';
 END;
 IF NEW.approved_at IS NOT NULL AND (NEW.source_reference IS NULL OR length(btrim(NEW.source_reference))=0) THEN
  RAISE EXCEPTION 'Approved rule requires a source reference' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guard_identifier_rules BEFORE INSERT OR UPDATE OR DELETE ON sasms.identifier_rules
FOR EACH ROW EXECUTE FUNCTION sasms.guard_identifier_rule();

CREATE FUNCTION sasms.validate_device_identifiers() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE brand uuid; rule sasms.identifier_rules;
BEGIN
 NEW.serial := upper(btrim(NEW.serial));
 NEW.imei := nullif(btrim(NEW.imei),'');
 SELECT brand_id INTO brand FROM sasms.products WHERE id=NEW.product_id FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Product not found' USING ERRCODE='23503'; END IF;
 PERFORM 1 FROM sasms.brands WHERE id=brand FOR SHARE;
 SELECT * INTO rule FROM sasms.identifier_rules WHERE brand_id=brand AND is_active;
 IF FOUND THEN
  IF NEW.serial !~ rule.serial_pattern OR (rule.imei_required AND NEW.imei IS NULL) THEN
   RAISE EXCEPTION 'Device does not satisfy the active approved identifier rule' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER validate_identifiers BEFORE INSERT OR UPDATE OF serial,imei,product_id ON sasms.devices
FOR EACH ROW EXECUTE FUNCTION sasms.validate_device_identifiers();

-- Retain the audited S0 stock transaction as a private implementation detail.
ALTER FUNCTION sasms.receive_device(uuid,text,text,uuid) RENAME TO receive_device_unchecked_s0;
REVOKE ALL ON FUNCTION sasms.receive_device_unchecked_s0(uuid,text,text,uuid) FROM PUBLIC;
DO $$
DECLARE grantee_name text;
BEGIN
 -- RENAME preserves the old OID/ACL. Revoke every old non-owner caller, including
 -- integration roles added outside the baseline, so the private path stays private.
 FOR grantee_name IN
  SELECT DISTINCT r.rolname FROM pg_proc p
  CROSS JOIN LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a
  JOIN pg_roles r ON r.oid=a.grantee
  WHERE p.oid='sasms.receive_device_unchecked_s0(uuid,text,text,uuid)'::regprocedure
   AND a.grantee<>p.proowner
 LOOP
  EXECUTE format('REVOKE ALL ON FUNCTION sasms.receive_device_unchecked_s0(uuid,text,text,uuid) FROM %I CASCADE',grantee_name);
 END LOOP;
END $$;

CREATE FUNCTION sasms.receive_device_batch(p_receipt_item uuid,p_request_key uuid,p_devices jsonb,p_actor uuid)
RETURNS uuid[] LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE store uuid; row_data jsonb; normalized jsonb := '[]';
        serial_value text; imei_value text; existing sasms.inventory_import_batches;
        ids uuid[] := ARRAY[]::uuid[];
BEGIN
 SELECT w.store_id INTO store
  FROM sasms.goods_receipt_items ri JOIN sasms.goods_receipts r ON r.id=ri.receipt_id
  JOIN sasms.warehouses w ON w.id=r.warehouse_id WHERE ri.id=p_receipt_item;
 IF NOT FOUND THEN RAISE EXCEPTION 'Receipt item not found' USING ERRCODE='23503'; END IF;
 PERFORM sasms.assert_permission(p_actor,'inventory.receive',store);
 IF p_request_key IS NULL THEN RAISE EXCEPTION 'Request key is required' USING ERRCODE='23514'; END IF;
 IF p_devices IS NULL OR jsonb_typeof(p_devices)<>'array' THEN
  RAISE EXCEPTION 'Devices must be a JSON array' USING ERRCODE='23514';
 END IF;
 IF jsonb_array_length(p_devices) NOT BETWEEN 1 AND 500 THEN
  RAISE EXCEPTION 'Batch must contain 1..500 devices' USING ERRCODE='23514';
 END IF;
 FOR row_data IN SELECT value FROM jsonb_array_elements(p_devices)
 LOOP
  IF jsonb_typeof(row_data)<>'object' THEN
   RAISE EXCEPTION 'Each device must be an object' USING ERRCODE='23514';
  END IF;
  IF EXISTS(SELECT 1 FROM jsonb_object_keys(row_data) AS keys(key) WHERE key NOT IN ('serial','imei'))
     OR jsonb_typeof(row_data->'serial') IS DISTINCT FROM 'string'
     OR (row_data ? 'imei' AND jsonb_typeof(row_data->'imei') NOT IN ('string','null')) THEN
   RAISE EXCEPTION 'Device keys/types are invalid; expected serial string and optional imei string/null' USING ERRCODE='23514';
  END IF;
  serial_value := upper(btrim(row_data->>'serial'));
  imei_value := nullif(btrim(row_data->>'imei'),'');
  IF serial_value IS NULL OR length(serial_value) NOT BETWEEN 1 AND 100 OR serial_value ~ '[[:space:]]'
     OR (imei_value IS NOT NULL AND imei_value !~ '^[0-9]{15}$') THEN
   RAISE EXCEPTION 'Invalid generic Serial/IMEI format' USING ERRCODE='23514';
  END IF;
  normalized := normalized || jsonb_build_array(jsonb_build_object('serial',serial_value,'imei',imei_value));
 END LOOP;
 IF EXISTS(SELECT 1 FROM jsonb_array_elements(normalized) x GROUP BY x->>'serial' HAVING count(*)>1)
    OR EXISTS(SELECT 1 FROM jsonb_array_elements(normalized) x WHERE x->>'imei' IS NOT NULL
              GROUP BY x->>'imei' HAVING count(*)>1) THEN
  RAISE EXCEPTION 'Duplicate identifier within batch' USING ERRCODE='23505';
 END IF;
 -- Same key is serialized even when requests target different receipt items.
 PERFORM pg_advisory_xact_lock(hashtextextended('sasms.inventory-import:'||p_request_key::text,0));
 SELECT * INTO existing FROM sasms.inventory_import_batches WHERE request_key=p_request_key;
 IF FOUND THEN
  IF existing.receipt_item_id IS DISTINCT FROM p_receipt_item OR existing.actor_id IS DISTINCT FROM p_actor
     OR existing.normalized_payload IS DISTINCT FROM normalized THEN
   RAISE EXCEPTION 'Request key reused with a different receipt, actor or payload' USING ERRCODE='23514';
  END IF;
  RETURN existing.device_ids;
 END IF;
 FOR row_data IN SELECT value FROM jsonb_array_elements(normalized)
 LOOP
  ids := array_append(ids,sasms.receive_device_unchecked_s0(p_receipt_item,row_data->>'serial',row_data->>'imei',p_actor));
 END LOOP;
 INSERT INTO sasms.inventory_import_batches(request_key,receipt_item_id,actor_id,normalized_payload,device_ids)
  VALUES(p_request_key,p_receipt_item,p_actor,normalized,ids);
 RETURN ids;
END $$;

CREATE FUNCTION sasms.receive_device(p_receipt_item uuid,p_serial text,p_imei text,p_actor uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE ids uuid[];
BEGIN
 ids := sasms.receive_device_batch(p_receipt_item,gen_random_uuid(),
    jsonb_build_array(jsonb_build_object('serial',p_serial,'imei',p_imei)),p_actor);
 RETURN ids[1];
END $$;

REVOKE ALL ON sasms.identifier_rules,sasms.inventory_import_batches FROM PUBLIC;
REVOKE ALL ON FUNCTION sasms.guard_identifier_rule(),sasms.validate_device_identifiers(),
 sasms.receive_device_batch(uuid,uuid,jsonb,uuid),sasms.receive_device(uuid,text,text,uuid) FROM PUBLIC;
DO $$
BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='sasms_app') THEN
  REVOKE ALL ON FUNCTION sasms.receive_device_unchecked_s0(uuid,text,text,uuid) FROM sasms_app;
  GRANT SELECT ON sasms.identifier_rules,sasms.inventory_import_batches TO sasms_app;
  GRANT EXECUTE ON FUNCTION sasms.receive_device_batch(uuid,uuid,jsonb,uuid),sasms.receive_device(uuid,text,text,uuid) TO sasms_app;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='sasms_reader') THEN
  REVOKE ALL ON FUNCTION sasms.receive_device_unchecked_s0(uuid,text,text,uuid) FROM sasms_reader;
 END IF;
END $$;
COMMENT ON TABLE sasms.identifier_rules IS 'D35 implementation contract: only genuinely approved manufacturer rules may be activated; no vendor regex seeded';
COMMENT ON TABLE sasms.inventory_import_batches IS 'S1-06/S1-07: atomic, ordered canonical payload-aware inventory import retries';
