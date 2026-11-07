-- Run as the DBA after V001/V002. Also supports provisioning logins/groups later
-- than the migration. Baseline creates checked NOLOGIN groups, never passwords.
\set ON_ERROR_STOP on
\ir ../runtime-roles.sql
BEGIN;
GRANT SELECT ON sasms.identifier_rules,sasms.inventory_import_batches TO sasms_app;
GRANT EXECUTE ON FUNCTION sasms.has_permission(uuid,text,uuid),sasms.assert_permission(uuid,text,uuid),
 sasms.create_product(uuid,text,uuid,uuid,text,jsonb,numeric,integer),
 sasms.update_product(uuid,uuid,integer,text,jsonb,numeric,integer,boolean),
 sasms.receive_device_batch(uuid,uuid,jsonb,uuid),sasms.receive_device(uuid,text,text,uuid) TO sasms_app;
REVOKE ALL ON FUNCTION sasms.receive_device_unchecked_s0(uuid,text,text,uuid)
 FROM PUBLIC,sasms_app,sasms_reader;
REVOKE INSERT,UPDATE,DELETE ON sasms.products,sasms.users,sasms.roles,sasms.permissions,
 sasms.user_roles,sasms.role_permissions,sasms.identifier_rules,sasms.inventory_import_batches FROM sasms_app;
COMMIT;
