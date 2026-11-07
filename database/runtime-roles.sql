-- Optional DBA provisioning in an isolated SASMS cluster. Requires CREATEROLE.
-- These are group roles, never public logins. Assign a real LOGIN via secret tooling.
\set ON_ERROR_STOP on
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sasms_app') THEN
        CREATE ROLE sasms_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sasms_reader') THEN
        CREATE ROLE sasms_reader NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname IN ('sasms_app','sasms_reader')
        AND (rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)) THEN
        RAISE EXCEPTION 'Existing runtime roles have unsafe attributes; DBA review required';
    END IF;
END $$;
GRANT USAGE ON SCHEMA sasms TO sasms_app, sasms_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA sasms TO sasms_app;
-- Browser/end users must never connect as sasms_app: business scopes are enforced by backend.
GRANT INSERT (id, code, idempotency_key, store_id, warehouse_id, customer_id, channel, created_by)
    ON sasms.sales_orders TO sasms_app;
GRANT INSERT, UPDATE, DELETE ON sasms.sales_order_items, sasms.order_devices, sasms.payments TO sasms_app;
GRANT EXECUTE ON FUNCTION sasms.receive_device(uuid,text,text,uuid),
    sasms.reserve_order(uuid,uuid), sasms.complete_sale(uuid,uuid), sasms.cancel_order(uuid,uuid) TO sasms_app;
GRANT SELECT ON sasms.brands, sasms.categories, sasms.products, sasms.product_images,
    sasms.stock_availability TO sasms_reader;
-- The future outbox worker needs its own narrowly scoped role/migration.
COMMIT;
