-- D34 BR-01 / D35 SER-01..09. Preserve V001/V002 and historical payloads.
CREATE FUNCTION sasms.valid_imei(value text) RETURNS boolean
LANGUAGE sql IMMUTABLE PARALLEL SAFE SET search_path=pg_catalog AS $$
 SELECT value ~ '^[0-9]{15}$' AND (
  SELECT sum(CASE WHEN n>9 THEN n-9 ELSE n END)%10=0
  FROM (SELECT substring(value FROM i FOR 1)::integer *
    CASE WHEN i%2=0 THEN 2 ELSE 1 END n FROM generate_series(1,15) i
    WHERE value ~ '^[0-9]{15}$') digits
 );
$$;

ALTER TABLE sasms.devices DROP CONSTRAINT devices_serial_check;
ALTER TABLE sasms.devices ADD CONSTRAINT devices_serial_check CHECK (
 serial=btrim(serial) AND length(serial) BETWEEN 1 AND 128 AND serial !~ '[[:cntrl:]]');
ALTER TABLE sasms.devices ADD COLUMN serial_raw text;
ALTER TABLE sasms.devices ADD COLUMN imei_raw text;
COMMENT ON COLUMN sasms.devices.serial_raw IS 'Original new input; NULL for legacy rows whose original spelling is unknown.';

CREATE TABLE sasms.product_identifier_profiles (
 product_id uuid PRIMARY KEY REFERENCES sasms.products(id),
 profile_code text NOT NULL CHECK(profile_code IN ('GENERIC','SONY_TV_7','APPLE_SERIAL_IMEI')),
 requires_imei boolean NOT NULL DEFAULT false,
 source_reference text NOT NULL CHECK(length(btrim(source_reference))>0),
 approved_by uuid NOT NULL REFERENCES sasms.users(id),
 approved_at timestamptz NOT NULL,
 CHECK(profile_code<>'APPLE_SERIAL_IMEI' OR requires_imei)
);
COMMENT ON TABLE sasms.product_identifier_profiles IS 'D35 explicit SKU profile; no inferred manufacturer-wide regex or automatic approval.';
CREATE FUNCTION sasms.guard_product_identifier_profile() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
BEGIN
 IF TG_OP<>'INSERT' THEN
  RAISE EXCEPTION 'Approved SKU profile is retained; a reviewed versioned change is required' USING ERRCODE='23514';
 END IF;
 PERFORM 1 FROM sasms.products WHERE id=NEW.product_id FOR UPDATE;
 PERFORM sasms.assert_permission(NEW.approved_by,'catalog.write',NULL);
 RETURN NEW;
END $$;
CREATE TRIGGER guard_product_identifier_profile BEFORE INSERT OR UPDATE OR DELETE ON sasms.product_identifier_profiles
 FOR EACH ROW EXECUTE FUNCTION sasms.guard_product_identifier_profile();

CREATE TABLE sasms.device_identifiers (
 device_id uuid NOT NULL REFERENCES sasms.devices(id),
 kind text NOT NULL CHECK(kind IN ('SERIAL','IMEI')),
 slot smallint NOT NULL CHECK(slot IN (1,2)),
 raw_value text,
 normalized_value text NOT NULL,
 legacy_unverified boolean NOT NULL DEFAULT false,
 PRIMARY KEY(device_id,kind,slot),
 UNIQUE(kind,normalized_value),
 CHECK(kind<>'SERIAL' OR slot=1),
 CHECK(raw_value IS NULL OR btrim(raw_value)=normalized_value),
 CHECK((kind='SERIAL' AND length(normalized_value) BETWEEN 1 AND 128
          AND normalized_value=btrim(normalized_value) AND normalized_value !~ '[[:cntrl:]]')
       OR (kind='IMEI' AND (legacy_unverified OR sasms.valid_imei(normalized_value))))
);
-- Preserve pre-migration identifiers without inventing raw inputs or certifying
-- historical IMEI checksum. Legacy data remains visible for controlled cleanup.
INSERT INTO sasms.device_identifiers(device_id,kind,slot,normalized_value,legacy_unverified)
 SELECT id,'SERIAL',1,serial,true FROM sasms.devices;
INSERT INTO sasms.device_identifiers(device_id,kind,slot,normalized_value,legacy_unverified)
 SELECT id,'IMEI',1,imei,true FROM sasms.devices WHERE imei IS NOT NULL;

CREATE OR REPLACE FUNCTION sasms.validate_device_identifiers() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE profile sasms.product_identifier_profiles;
BEGIN
 IF TG_OP='UPDATE' AND (NEW.serial,NEW.imei,NEW.product_id,
     NEW.serial_raw,NEW.imei_raw) IS DISTINCT FROM
    (OLD.serial,OLD.imei,OLD.product_id,OLD.serial_raw,OLD.imei_raw) THEN
  RAISE EXCEPTION 'Identifiers are immutable; a reviewed manager correction with reason and audit is required' USING ERRCODE='23514';
 END IF;
 NEW.serial_raw:=coalesce(NEW.serial_raw,NEW.serial);
 NEW.imei_raw:=coalesce(NEW.imei_raw,NEW.imei);
 NEW.serial:=btrim(NEW.serial); NEW.imei:=nullif(btrim(NEW.imei),'');
 IF NEW.serial IS NULL OR length(NEW.serial) NOT BETWEEN 1 AND 128 OR NEW.serial ~ '[[:cntrl:]]'
    OR btrim(NEW.serial_raw) IS DISTINCT FROM NEW.serial THEN
  RAISE EXCEPTION 'Invalid Serial: required, trimmed, 1..128 characters, no control characters' USING ERRCODE='23514';
 END IF;
 IF NEW.imei IS NOT NULL AND NOT coalesce(sasms.valid_imei(NEW.imei),false) THEN
  RAISE EXCEPTION 'IMEI must pass fifteen-digit Luhn' USING ERRCODE='23514';
 END IF;
 PERFORM 1 FROM sasms.products WHERE id=NEW.product_id FOR SHARE;
 SELECT * INTO profile FROM sasms.product_identifier_profiles WHERE product_id=NEW.product_id;
 IF FOUND AND ((profile.profile_code='SONY_TV_7' AND NEW.serial !~ '^[0-9]{7}$')
   OR (profile.requires_imei AND NEW.imei IS NULL)) THEN
  RAISE EXCEPTION 'Device does not satisfy its approved SKU identifier profile' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
DROP TRIGGER validate_identifiers ON sasms.devices;
CREATE TRIGGER validate_identifiers BEFORE INSERT OR UPDATE OF serial,imei,product_id,serial_raw,imei_raw
 ON sasms.devices FOR EACH ROW EXECUTE FUNCTION sasms.validate_device_identifiers();

CREATE FUNCTION sasms.guard_device_identifier_history() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
BEGIN
 IF TG_OP='INSERT' THEN
  IF NEW.legacy_unverified OR NEW.raw_value IS NULL THEN
   RAISE EXCEPTION 'New identifiers require raw input and current validation' USING ERRCODE='23514';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM sasms.devices d WHERE d.id=NEW.device_id
    AND ((NEW.kind='SERIAL' AND NEW.normalized_value=d.serial)
      OR (NEW.kind='IMEI' AND NEW.slot=1 AND NEW.normalized_value=d.imei)
      OR (NEW.kind='IMEI' AND NEW.slot=2 AND d.imei IS NOT NULL))) THEN
   RAISE EXCEPTION 'Identifier must match its device compatibility fields' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
 END IF;
 IF TG_OP='UPDATE' OR EXISTS(SELECT 1 FROM sasms.inventory_movements WHERE device_id=OLD.device_id)
   OR EXISTS(SELECT 1 FROM sasms.order_devices WHERE device_id=OLD.device_id) THEN
  RAISE EXCEPTION 'Identifier history cannot be overwritten or removed' USING ERRCODE='23514';
 END IF;
 RETURN OLD;
END $$;
CREATE TRIGGER guard_device_identifier_history BEFORE INSERT OR UPDATE OR DELETE ON sasms.device_identifiers
 FOR EACH ROW EXECUTE FUNCTION sasms.guard_device_identifier_history();

CREATE FUNCTION sasms.sync_device_identifiers() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
BEGIN
 IF TG_OP='DELETE' THEN
  DELETE FROM sasms.device_identifiers WHERE device_id=OLD.id;
  RETURN OLD;
 END IF;
 INSERT INTO sasms.device_identifiers(device_id,kind,slot,raw_value,normalized_value)
 VALUES(NEW.id,'SERIAL',1,NEW.serial_raw,NEW.serial);
 IF NEW.imei IS NOT NULL THEN
  INSERT INTO sasms.device_identifiers(device_id,kind,slot,raw_value,normalized_value)
  VALUES(NEW.id,'IMEI',1,NEW.imei_raw,NEW.imei);
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER sync_device_identifiers AFTER INSERT ON sasms.devices
 FOR EACH ROW EXECUTE FUNCTION sasms.sync_device_identifiers();
CREATE TRIGGER cleanup_device_identifiers BEFORE DELETE ON sasms.devices
 FOR EACH ROW EXECUTE FUNCTION sasms.sync_device_identifiers();

-- Remove uppercasing in the PRIVATE stock-receipt routine, preserving its
-- quantity/PO locks and movement/audit/outbox semantics byte-for-byte otherwise.
DO $$ DECLARE definition text;
BEGIN
 definition:=pg_get_functiondef('sasms.receive_device_unchecked_s0(uuid,text,text,uuid)'::regprocedure);
 IF position('upper(btrim(p_serial))' IN definition)=0 THEN
  RAISE EXCEPTION 'Unexpected private receipt implementation; migration requires review';
 END IF;
 EXECUTE replace(replace(definition,'upper(btrim(p_serial))','p_serial'),
    'nullif(btrim(p_imei), '''')','p_imei');
END $$;

CREATE OR REPLACE FUNCTION sasms.receive_device_batch(p_receipt_item uuid,p_request_key uuid,p_devices jsonb,p_actor uuid)
RETURNS uuid[] LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,sasms AS $$
DECLARE store uuid; row_data jsonb; normalized jsonb:='[]'; legacy_payload jsonb:='[]';
 serial_value text; imei_value text; imei2_value text; existing sasms.inventory_import_batches;
 ids uuid[]:=ARRAY[]::uuid[]; device uuid;
BEGIN
 SELECT w.store_id INTO store FROM sasms.goods_receipt_items ri
 JOIN sasms.goods_receipts r ON r.id=ri.receipt_id JOIN sasms.warehouses w ON w.id=r.warehouse_id
 WHERE ri.id=p_receipt_item;
 IF NOT FOUND THEN RAISE EXCEPTION 'Receipt item not found' USING ERRCODE='23503'; END IF;
 PERFORM sasms.assert_permission(p_actor,'inventory.receive',store);
 IF p_request_key IS NULL OR p_devices IS NULL OR jsonb_typeof(p_devices)<>'array' THEN
  RAISE EXCEPTION 'Request key and device array are required' USING ERRCODE='23514';
 END IF;
 IF jsonb_array_length(p_devices) NOT BETWEEN 1 AND 500 THEN
  RAISE EXCEPTION 'Batch must contain 1..500 devices' USING ERRCODE='23514';
 END IF;
 FOR row_data IN SELECT value FROM jsonb_array_elements(p_devices) LOOP
  IF jsonb_typeof(row_data)<>'object' THEN RAISE EXCEPTION 'Device must be an object' USING ERRCODE='23514'; END IF;
  IF EXISTS(SELECT 1 FROM jsonb_object_keys(row_data) k WHERE k NOT IN ('serial','imei','imei2'))
    OR jsonb_typeof(row_data->'serial') IS DISTINCT FROM 'string'
    OR (row_data ? 'imei' AND jsonb_typeof(row_data->'imei') NOT IN ('string','null'))
    OR (row_data ? 'imei2' AND jsonb_typeof(row_data->'imei2') NOT IN ('string','null')) THEN
   RAISE EXCEPTION 'Device keys/types are invalid' USING ERRCODE='23514';
  END IF;
  serial_value:=btrim(row_data->>'serial'); imei_value:=nullif(btrim(row_data->>'imei'),'');
  imei2_value:=nullif(btrim(row_data->>'imei2'),'');
  IF length(serial_value) NOT BETWEEN 1 AND 128 OR serial_value ~ '[[:cntrl:]]' THEN
   RAISE EXCEPTION 'Invalid Serial format' USING ERRCODE='23514';
  END IF;
  normalized:=normalized||jsonb_build_array(jsonb_build_object('serial',serial_value,'imei',imei_value,'imei2',imei2_value));
  legacy_payload:=legacy_payload||jsonb_build_array(jsonb_build_object('serial',upper(serial_value),'imei',imei_value));
 END LOOP;
 PERFORM pg_advisory_xact_lock(hashtextextended('sasms.inventory-import:'||p_request_key::text,0));
 SELECT * INTO existing FROM sasms.inventory_import_batches WHERE request_key=p_request_key;
 IF FOUND THEN
  IF existing.receipt_item_id IS DISTINCT FROM p_receipt_item OR existing.actor_id IS DISTINCT FROM p_actor
    OR (existing.normalized_payload IS DISTINCT FROM normalized AND NOT (
      NOT EXISTS(SELECT 1 FROM jsonb_array_elements(existing.normalized_payload) x WHERE x ? 'imei2')
      AND existing.normalized_payload=legacy_payload
      AND NOT EXISTS(SELECT 1 FROM jsonb_array_elements(normalized) x WHERE x->>'imei2' IS NOT NULL))) THEN
   RAISE EXCEPTION 'Request key reused with a different receipt, actor or payload' USING ERRCODE='23514';
  END IF;
  RETURN existing.device_ids;
 END IF;
 FOR row_data IN SELECT value FROM jsonb_array_elements(p_devices) LOOP
  device:=sasms.receive_device_unchecked_s0(p_receipt_item,row_data->>'serial',row_data->>'imei',p_actor);
  -- Secondary IMEI shares the same global registry and receipt transaction.
  IF nullif(btrim(row_data->>'imei2'),'') IS NOT NULL THEN
   IF NOT coalesce(sasms.valid_imei(btrim(row_data->>'imei2')),false)
      OR nullif(btrim(row_data->>'imei'),'') IS NULL THEN
    RAISE EXCEPTION 'Invalid secondary IMEI' USING ERRCODE='23514';
   END IF;
   INSERT INTO sasms.device_identifiers(device_id,kind,slot,raw_value,normalized_value)
   VALUES(device,'IMEI',2,row_data->>'imei2',btrim(row_data->>'imei2'));
  END IF;
  ids:=array_append(ids,device);
 END LOOP;
 INSERT INTO sasms.inventory_import_batches(request_key,receipt_item_id,actor_id,normalized_payload,device_ids)
 VALUES(p_request_key,p_receipt_item,p_actor,normalized,ids);
 RETURN ids;
END $$;

REVOKE ALL ON sasms.device_identifiers,sasms.product_identifier_profiles FROM PUBLIC;
REVOKE ALL ON FUNCTION sasms.valid_imei(text),sasms.guard_device_identifier_history(),sasms.sync_device_identifiers(),
 sasms.guard_product_identifier_profile() FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='sasms_app') THEN
  GRANT SELECT ON sasms.device_identifiers,sasms.product_identifier_profiles TO sasms_app;
  GRANT EXECUTE ON FUNCTION sasms.valid_imei(text) TO sasms_app;
 END IF;
END $$;
