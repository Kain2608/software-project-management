-- DBA-only explicit SKU configuration after V003; values from actual approval.
-- psql -v product_id=<uuid> -v profile_code=SONY_TV_7 -v requires_imei=false
--      -v approved_by=<uuid> -v approved_at=<ISO8601> -v source_reference=<record>
--      -f scripts/provision_d35_rules.sql
\set ON_ERROR_STOP on
\if :{?product_id}
\else
  \echo 'Required: product_id, profile_code, requires_imei, approved_by, approved_at, source_reference'
  DO $$ BEGIN RAISE EXCEPTION 'Required approval variable missing'; END $$;
\endif
\if :{?profile_code}
\else
  DO $$ BEGIN RAISE EXCEPTION 'Required approval variable missing'; END $$;
\endif
\if :{?requires_imei}
\else
  DO $$ BEGIN RAISE EXCEPTION 'Required approval variable missing'; END $$;
\endif
\if :{?approved_by}
\else
  DO $$ BEGIN RAISE EXCEPTION 'Required approval variable missing'; END $$;
\endif
\if :{?approved_at}
\else
  DO $$ BEGIN RAISE EXCEPTION 'Required approval variable missing'; END $$;
\endif
\if :{?source_reference}
\else
  DO $$ BEGIN RAISE EXCEPTION 'Required approval variable missing'; END $$;
\endif
BEGIN;
CREATE TEMP TABLE requested_profile ON COMMIT DROP AS SELECT
 :'product_id'::uuid product_id, :'profile_code'::text profile_code,
 :'requires_imei'::boolean requires_imei, :'approved_by'::uuid approved_by,
 :'approved_at'::timestamptz approved_at, :'source_reference'::text source_reference;
SELECT p.id FROM sasms.products p JOIN requested_profile r ON r.product_id=p.id FOR UPDATE;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM sasms.products p JOIN requested_profile r ON r.product_id=p.id) THEN
  RAISE EXCEPTION 'Explicit SKU does not exist' USING ERRCODE='23503';
 END IF;
 PERFORM sasms.assert_permission(r.approved_by,'catalog.write',NULL) FROM requested_profile r;
 IF EXISTS(SELECT 1 FROM sasms.product_identifier_profiles p JOIN requested_profile r USING(product_id)
  WHERE (p.profile_code,p.requires_imei,p.approved_by,p.approved_at,p.source_reference)
   IS DISTINCT FROM (r.profile_code,r.requires_imei,r.approved_by,r.approved_at,r.source_reference)) THEN
  RAISE EXCEPTION 'Different approved SKU profile exists; use a reviewed versioned change' USING ERRCODE='23514';
 END IF;
END $$;
INSERT INTO sasms.product_identifier_profiles
 (product_id,profile_code,requires_imei,approved_by,approved_at,source_reference)
SELECT product_id,profile_code,requires_imei,approved_by,approved_at,source_reference
FROM requested_profile WHERE NOT EXISTS(
 SELECT 1 FROM sasms.product_identifier_profiles p WHERE p.product_id=requested_profile.product_id);
COMMIT;
