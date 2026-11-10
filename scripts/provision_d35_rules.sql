-- SASMS Sprint 1 - Provisioning D35 Official Manufacturer Identifier Rules
-- Based on: Sprint 0/BA/D35_Quy_tac_Serial_IMEI_SASMS.docx by Nguyen Quang Huy (BA)
-- Sources: SRC-01 (Sony Support 00248421), SRC-02 (Samsung), SRC-03 (LG), SRC-04 (Apple), SRC-05 (GSMA TS.06 v22)
\set ON_ERROR_STOP on
BEGIN;

-- 1. Ensure manufacturer brands exist
INSERT INTO sasms.brands (id, name) VALUES
    ('00000000-0000-4000-8000-000000000020', 'Sony'),
    ('00000000-0000-4000-8000-000000000021', 'Samsung'),
    ('00000000-0000-4000-8000-000000000022', 'LG'),
    ('00000000-0000-4000-8000-000000000023', 'Apple')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- 2. Deactivate old active rules for these brands if any
UPDATE sasms.identifier_rules
   SET is_active = false
 WHERE brand_id IN (
    '00000000-0000-4000-8000-000000000020',
    '00000000-0000-4000-8000-000000000021',
    '00000000-0000-4000-8000-000000000022',
    '00000000-0000-4000-8000-000000000023'
 ) AND is_active = true;

-- 3. Provision official D35 rules with approval metadata
-- Profile SONY_TV_7: 7 numeric digits ^[0-9]{7}$, no IMEI required
INSERT INTO sasms.identifier_rules (
    brand_id, version, serial_pattern, imei_required,
    source_reference, approved_by, approved_at, is_active
) VALUES (
    '00000000-0000-4000-8000-000000000020', 1, '^[0-9]{7}$', false,
    'D35 SER-06; SRC-01 Sony Support 00248421',
    (SELECT id FROM sasms.users WHERE email = 'admin@sasms.local' OR id = '00000000-0000-4000-8000-000000000001' LIMIT 1),
    now(), true
);

-- Profile SAMSUNG_GENERIC: 11-15 uppercase alphanumeric characters, optional IMEI
INSERT INTO sasms.identifier_rules (
    brand_id, version, serial_pattern, imei_required,
    source_reference, approved_by, approved_at, is_active
) VALUES (
    '00000000-0000-4000-8000-000000000021', 1, '^[A-Z0-9]{11,15}$', false,
    'D35 Profile SAMSUNG_GENERIC; SRC-02 Samsung Support',
    (SELECT id FROM sasms.users WHERE email = 'admin@sasms.local' OR id = '00000000-0000-4000-8000-000000000001' LIMIT 1),
    now(), true
);

-- Profile LG_GENERIC: 3 digits + 4 letters + 5 digits, optional IMEI
INSERT INTO sasms.identifier_rules (
    brand_id, version, serial_pattern, imei_required,
    source_reference, approved_by, approved_at, is_active
) VALUES (
    '00000000-0000-4000-8000-000000000022', 1, '^[0-9]{3}[A-Z]{4}[0-9]{5}$', false,
    'D35 Profile LG_GENERIC; SRC-03 LG Support',
    (SELECT id FROM sasms.users WHERE email = 'admin@sasms.local' OR id = '00000000-0000-4000-8000-000000000001' LIMIT 1),
    now(), true
);

-- Profile APPLE_SERIAL_IMEI: 10-12 uppercase alphanumeric characters, IMEI required (15 digits)
INSERT INTO sasms.identifier_rules (
    brand_id, version, serial_pattern, imei_required,
    source_reference, approved_by, approved_at, is_active
) VALUES (
    '00000000-0000-4000-8000-000000000023', 1, '^[A-Z0-9]{10,12}$', true,
    'D35 Profile APPLE_SERIAL_IMEI; SRC-04 Apple Support, SRC-05 GSMA TS.06',
    (SELECT id FROM sasms.users WHERE email = 'admin@sasms.local' OR id = '00000000-0000-4000-8000-000000000001' LIMIT 1),
    now(), true
);

COMMIT;

