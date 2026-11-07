-- Read-only reconciliation. Each query should return zero rows in normal core flows.
\set ON_ERROR_STOP on
SET search_path = sasms, pg_catalog;
-- Stock reserved by an order that is not RESERVED.
SELECT d.id AS device_id, d.reserved_order_id, o.status
FROM devices d JOIN sales_orders o ON o.id = d.reserved_order_id
WHERE d.status = 'RESERVED' AND o.status <> 'RESERVED';
-- Paid orders whose captured payments or receipt no longer match frozen totals.
SELECT o.id AS order_id FROM sales_orders o
WHERE o.status = 'PAID' AND (
  o.total_amount <> (SELECT coalesce(sum(amount),0) FROM payments WHERE order_id=o.id AND status='CAPTURED')
  OR NOT EXISTS (SELECT 1 FROM invoices i WHERE i.order_id=o.id AND i.amount=o.total_amount)
  OR EXISTS (SELECT 1 FROM order_devices od JOIN devices d ON d.id=od.device_id
             WHERE od.order_id=o.id AND od.active AND (d.status<>'SOLD' OR d.sold_order_id IS DISTINCT FROM o.id))
);
-- Devices received without stock/audit evidence: catches writes outside approved routines.
SELECT d.id AS device_id FROM devices d WHERE
  NOT EXISTS (SELECT 1 FROM inventory_movements m WHERE m.device_id=d.id AND m.kind='RECEIVE')
  OR NOT EXISTS (SELECT 1 FROM audit_logs a WHERE a.entity_id=d.id AND a.action='inventory.receive');
