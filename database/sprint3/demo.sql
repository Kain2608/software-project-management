\set ON_ERROR_STOP on
BEGIN READ ONLY;
SELECT version,filename FROM sasms_meta.schema_migrations ORDER BY version;
SELECT p.oid::regprocedure AS api, p.prosecdef AS security_definer, p.proconfig
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='sasms' AND p.proname IN
 ('schedule_delivery','record_delivery_result','activate_warranty','claim_sms_notification','finish_sms_notification','requeue_failed_sms')
 ORDER BY p.proname;
SELECT status,count(*) FROM sasms.technician_assignments GROUP BY status ORDER BY status;
SELECT policy_version,activation_basis,count(*) FROM sasms.warranties GROUP BY policy_version,activation_basis;
SELECT event_type,notification_status,count(*) FROM sasms.outbox_events
 WHERE event_type IN ('WarrantyActivated','DeliveryScheduled') GROUP BY event_type,notification_status;
ROLLBACK;
