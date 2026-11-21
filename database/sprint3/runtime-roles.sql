\set ON_ERROR_STOP on
BEGIN;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='sasms_sms_worker') THEN
  CREATE ROLE sasms_sms_worker NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
 END IF;
END $$;
GRANT USAGE ON SCHEMA sasms TO sasms_sms_worker;
GRANT EXECUTE ON FUNCTION sasms.claim_sms_notification(uuid),
 sasms.finish_sms_notification(uuid,integer,text,text,text) TO sasms_sms_worker;
COMMIT;
