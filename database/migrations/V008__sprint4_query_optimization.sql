-- Sprint 4 / S4-04 and S4-07: access paths for the existing read contracts.
-- The migration runner owns the transaction. No business data or ACL is changed.
-- QR, case-sensitive Serial, IMEI and the secondary-IMEI registry already have
-- UNIQUE indexes. Duplicating them would add receipt/sale write cost.

-- paid_at (not order creation time) defines gross-paid report membership.
CREATE INDEX ix_sales_orders_s4_paid_store_date
 ON sasms.sales_orders(store_id,paid_at,id) INCLUDE(total_amount)
 WHERE status='PAID';

-- A single range scan serves the three RECEIVE/SALE/RETURN counters.
CREATE INDEX ix_movements_s4_warehouse_date
 ON sasms.inventory_movements(warehouse_id,created_at) INCLUDE(kind);

-- Bound the AI candidate check and current dashboard stock count to sellable
-- devices. The historical stock index includes SOLD/RESERVED/etc. as well.
CREATE INDEX ix_devices_s4_sellable
 ON sasms.devices(warehouse_id,product_id) WHERE status='IN_STOCK';

-- Existing primary keys start with return_id/delivery_id. Timeline lookups
-- start with device_id and otherwise scan all return/delivery item rows.
CREATE INDEX ix_return_items_s4_device
 ON sasms.return_items(device_id,return_id);
CREATE INDEX ix_delivery_items_s4_device
 ON sasms.delivery_items(device_id,delivery_id);

COMMENT ON INDEX sasms.ix_sales_orders_s4_paid_store_date IS
 'S4-04 gross PAID amount/count by store and half-open paid_at range; not net revenue or refund evidence.';
COMMENT ON INDEX sasms.ix_devices_s4_sellable IS
 'S4-07 real-time IN_STOCK counts for bounded AI candidates and dashboard; no materialized/cache stock.';
