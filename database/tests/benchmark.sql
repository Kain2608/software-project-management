\set ON_ERROR_STOP on
-- Synthetic DB lookup evidence only; not an API/QR end-to-end SLA.
BEGIN;
SET search_path = sasms, pg_catalog;
INSERT INTO devices(product_id,receipt_item_id,warehouse_id,serial,imei,qr_token)
 SELECT '00000000-0000-4000-8000-000000000030',
 '00000000-0000-4000-8000-000000000061','00000000-0000-4000-8000-000000000011',
 'S0-BENCH-'||lpad(i::text,6,'0'),'990'||lpad(i::text,12,'0'),md5('s0-bench-qr-'||i)::uuid
 FROM generate_series(1,100000) AS fixture(i);
ANALYZE devices;
DO $$
BEGIN
 IF (SELECT count(*) FROM sasms.devices WHERE serial LIKE 'S0-BENCH-%') <> 100000 THEN
  RAISE EXCEPTION 'Benchmark fixture count mismatch';
 END IF;
 RAISE NOTICE 'PASS: 100000 synthetic devices loaded inside rollback transaction';
END $$;
EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON)
 SELECT id,product_id,status FROM devices WHERE serial='S0-BENCH-050000';
EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON)
 SELECT id,product_id,status FROM devices WHERE imei='990000000050000';
EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON)
 SELECT id,product_id,status FROM devices WHERE qr_token=md5('s0-bench-qr-50000')::uuid;
ROLLBACK;
-- Restore planner statistics after rolling the fixture back.
ANALYZE sasms.devices;
