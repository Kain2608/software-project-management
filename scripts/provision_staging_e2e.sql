-- ==============================================================================
-- SASMS - Provision Runtime LOGIN for E2E Staging Environment
-- Author: Tran Trung Kien (Architect / DBA)
-- Purpose: Create non-superuser LOGIN role granted to sasms_app group role.
-- Compatible with: PostgreSQL 16, Sprint 4 (41 tables, migrations V001..V008)
-- ==============================================================================

\set ON_ERROR_STOP on

BEGIN;

-- 1. Verify prerequisite roles
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sasms_app') THEN
        RAISE EXCEPTION 'Required group role sasms_app is missing. Run database/runtime-roles.sql first.'
            USING ERRCODE = 'P0002';
    END IF;

    -- Ensure sasms_app is strictly a NOLOGIN non-superuser role
    IF EXISTS (
        SELECT 1 FROM pg_roles 
        WHERE rolname = 'sasms_app' 
          AND (rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'Group role sasms_app has unsafe attributes; DBA review required'
            USING ERRCODE = '23514';
    END IF;
END $$;

-- 2. Drop existing staging login if present, then recreate with clean attributes
DROP ROLE IF EXISTS tv4_staging_app_user;

-- Create the runtime login (non-superuser, no-createdb, strictly isolated)
-- Default password for local staging E2E integration: SasmsApp_Staging_2026!
CREATE ROLE tv4_staging_app_user 
    LOGIN 
    NOSUPERUSER 
    NOCREATEDB 
    NOCREATEROLE 
    NOREPLICATION 
    NOBYPASSRLS 
    PASSWORD 'SasmsApp_Staging_2026!';

-- Grant group membership
GRANT sasms_app TO tv4_staging_app_user;

-- 3. Ensure sasms_app has all necessary execution rights on Sprint 1-4 extensions
GRANT USAGE ON SCHEMA sasms TO sasms_app;
GRANT SELECT ON ALL TABLES IN SCHEMA sasms TO sasms_app;
GRANT INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA sasms TO sasms_app;
-- Revoke DDL & truncate to protect schema and audit trail integrity
REVOKE TRUNCATE ON ALL TABLES IN SCHEMA sasms FROM sasms_app;

-- Grant execution on all Stored Procedures & Functions in sasms schema
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA sasms TO sasms_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA sasms TO sasms_app;

COMMIT;

-- 4. Verification Block: ensure user can login, has no superuser, and is restricted
DO $$
DECLARE
    v_can_login boolean;
    v_is_super boolean;
    v_is_member boolean;
BEGIN
    SELECT rolcanlogin, rolsuper INTO v_can_login, v_is_super
    FROM pg_roles WHERE rolname = 'tv4_staging_app_user';

    IF NOT v_can_login OR v_is_super THEN
        RAISE EXCEPTION 'Verification failed: tv4_staging_app_user must have LOGIN=true and SUPERUSER=false';
    END IF;

    SELECT pg_has_role('tv4_staging_app_user', 'sasms_app', 'member') INTO v_is_member;
    IF NOT v_is_member THEN
        RAISE EXCEPTION 'Verification failed: tv4_staging_app_user is not a member of sasms_app';
    END IF;

    RAISE NOTICE '=== PROVISIONING SUCCESSFUL ===';
    RAISE NOTICE 'User: tv4_staging_app_user | LOGIN: true | Superuser: false | Member of: sasms_app';
END $$;
