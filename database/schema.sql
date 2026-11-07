-- D57 / SASMS / Sprint 0 / Tran Trung Kien
-- PostgreSQL 16+, fresh database only. No DROP, no application login/secrets.
-- This rebuilt baseline is proposed pending D34/BA/PM review.
\set ON_ERROR_STOP on
BEGIN;
CREATE SCHEMA sasms;
REVOKE ALL ON SCHEMA sasms FROM PUBLIC;
SET search_path = sasms, pg_catalog;

CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email text NOT NULL CHECK (email = lower(btrim(email)) AND email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'),
    full_name text NOT NULL CHECK (length(btrim(full_name)) > 0),
    auth_subject text UNIQUE,
    is_active boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);
CREATE TABLE roles (
    code text PRIMARY KEY CHECK (code ~ '^[A-Z_]+$'),
    name text NOT NULL,
    description text NOT NULL
);
CREATE TABLE permissions (
    code text PRIMARY KEY,
    description text NOT NULL
);
CREATE TABLE stores (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE CHECK (length(btrim(code)) > 0),
    name text NOT NULL,
    address text NOT NULL,
    is_active boolean NOT NULL DEFAULT true
);
CREATE TABLE user_roles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id),
    role_code text NOT NULL REFERENCES roles(code),
    store_id uuid REFERENCES stores(id),
    CONSTRAINT uq_user_role_scope UNIQUE NULLS NOT DISTINCT (user_id, role_code, store_id)
);
CREATE TABLE role_permissions (
    role_code text NOT NULL REFERENCES roles(code),
    permission_code text NOT NULL REFERENCES permissions(code),
    PRIMARY KEY (role_code, permission_code)
);
CREATE TABLE warehouses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id uuid NOT NULL REFERENCES stores(id),
    code text NOT NULL UNIQUE,
    name text NOT NULL,
    UNIQUE (id, store_id)
);
CREATE TABLE brands (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL UNIQUE
);
CREATE TABLE categories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id uuid REFERENCES categories(id),
    name text NOT NULL UNIQUE,
    CHECK (parent_id IS DISTINCT FROM id)
);
CREATE TABLE products (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sku text NOT NULL UNIQUE CHECK (sku = upper(btrim(sku)) AND length(sku) > 0),
    brand_id uuid NOT NULL REFERENCES brands(id),
    category_id uuid NOT NULL REFERENCES categories(id),
    name text NOT NULL,
    description text,
    specifications jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(specifications) = 'object'),
    list_price numeric(14,2) NOT NULL CHECK (list_price >= 0),
    warranty_months integer NOT NULL DEFAULT 12 CHECK (warranty_months BETWEEN 0 AND 120),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE product_images (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id uuid NOT NULL REFERENCES products(id),
    object_key text NOT NULL CHECK (length(btrim(object_key)) > 0),
    position smallint NOT NULL CHECK (position BETWEEN 1 AND 3),
    size_bytes integer NOT NULL CHECK (size_bytes BETWEEN 1 AND 2097152),
    UNIQUE (product_id, position)
);
CREATE TABLE suppliers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE,
    name text NOT NULL,
    contact_phone text,
    contact_email text
);
CREATE TABLE purchase_orders (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE,
    supplier_id uuid NOT NULL REFERENCES suppliers(id),
    warehouse_id uuid NOT NULL REFERENCES warehouses(id),
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPROVED','CLOSED','CANCELLED')),
    created_by uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, warehouse_id)
);
CREATE TABLE purchase_order_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id uuid NOT NULL REFERENCES purchase_orders(id),
    product_id uuid NOT NULL REFERENCES products(id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_cost numeric(14,2) NOT NULL CHECK (unit_cost >= 0),
    UNIQUE (purchase_order_id, product_id),
    UNIQUE (id, product_id, purchase_order_id)
);
CREATE TABLE goods_receipts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE,
    purchase_order_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    received_by uuid NOT NULL REFERENCES users(id),
    received_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (purchase_order_id, warehouse_id) REFERENCES purchase_orders(id, warehouse_id),
    UNIQUE (id, purchase_order_id)
);
CREATE TABLE goods_receipt_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id uuid NOT NULL,
    purchase_order_id uuid NOT NULL,
    purchase_order_item_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    FOREIGN KEY (receipt_id, purchase_order_id) REFERENCES goods_receipts(id, purchase_order_id),
    FOREIGN KEY (purchase_order_item_id, product_id, purchase_order_id)
        REFERENCES purchase_order_items(id, product_id, purchase_order_id),
    UNIQUE (receipt_id, product_id),
    UNIQUE (id, product_id)
);
CREATE TABLE customers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE,
    full_name text NOT NULL,
    phone text,
    email text,
    address text,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE sales_orders (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE,
    idempotency_key uuid NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    store_id uuid NOT NULL REFERENCES stores(id),
    warehouse_id uuid NOT NULL,
    customer_id uuid REFERENCES customers(id),
    channel text NOT NULL CHECK (channel IN ('POS','ONLINE')),
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','RESERVED','PAID','CANCELLED')),
    total_amount numeric(14,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    created_by uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    paid_at timestamptz,
    FOREIGN KEY (warehouse_id, store_id) REFERENCES warehouses(id, store_id),
    CHECK ((status = 'PAID') = (paid_at IS NOT NULL))
);
CREATE TABLE sales_order_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL REFERENCES sales_orders(id),
    product_id uuid NOT NULL REFERENCES products(id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(14,2) NOT NULL CHECK (unit_price >= 0),
    discount_amount numeric(14,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0 AND discount_amount <= quantity * unit_price),
    tax_rate numeric(5,2) NOT NULL DEFAULT 0 CHECK (tax_rate BETWEEN 0 AND 100),
    line_total numeric(14,2) GENERATED ALWAYS AS
        (round((quantity * unit_price - discount_amount) * (1 + tax_rate / 100), 2)) STORED,
    UNIQUE (order_id, product_id),
    UNIQUE (id, order_id, product_id)
);
CREATE TABLE devices (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id uuid NOT NULL REFERENCES products(id),
    receipt_item_id uuid NOT NULL,
    warehouse_id uuid NOT NULL REFERENCES warehouses(id),
    serial text NOT NULL UNIQUE CHECK (serial = upper(btrim(serial)) AND serial !~ '[[:space:]]' AND length(serial) BETWEEN 1 AND 100),
    imei text UNIQUE CHECK (imei ~ '^[0-9]{15}$'),
    qr_token uuid NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    status text NOT NULL DEFAULT 'IN_STOCK' CHECK (status IN ('IN_STOCK','RESERVED','SOLD','WARRANTY','RETURNED','SCRAPPED')),
    reserved_order_id uuid REFERENCES sales_orders(id),
    sold_order_id uuid REFERENCES sales_orders(id),
    received_at timestamptz NOT NULL DEFAULT now(),
    sold_at timestamptz,
    FOREIGN KEY (receipt_item_id, product_id) REFERENCES goods_receipt_items(id, product_id),
    CHECK ((status = 'RESERVED') = (reserved_order_id IS NOT NULL)),
    CHECK ((sold_order_id IS NOT NULL) = (sold_at IS NOT NULL)),
    CHECK (status <> 'SOLD' OR sold_order_id IS NOT NULL),
    UNIQUE (id, product_id)
);
CREATE TABLE order_devices (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_item_id uuid NOT NULL,
    order_id uuid NOT NULL,
    product_id uuid NOT NULL,
    device_id uuid NOT NULL,
    active boolean NOT NULL DEFAULT true,
    FOREIGN KEY (order_item_id, order_id, product_id) REFERENCES sales_order_items(id, order_id, product_id),
    FOREIGN KEY (device_id, product_id) REFERENCES devices(id, product_id),
    UNIQUE (order_id, device_id)
);
-- Draft carts may select the same device. reserve_order serializes ownership.
-- A partial unique on active carts would make abandoned drafts block stock.
CREATE TABLE inventory_movements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id uuid NOT NULL REFERENCES devices(id),
    warehouse_id uuid NOT NULL REFERENCES warehouses(id),
    kind text NOT NULL CHECK (kind IN ('RECEIVE','RESERVE','RELEASE','SALE','RETURN','TRANSFER_IN','TRANSFER_OUT','ADJUST')),
    order_id uuid REFERENCES sales_orders(id),
    actor_id uuid NOT NULL REFERENCES users(id),
    reason text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_core_movement ON inventory_movements(device_id, kind, order_id)
    NULLS NOT DISTINCT WHERE kind IN ('RECEIVE','RESERVE','RELEASE','SALE');
CREATE TABLE stocktakes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE,
    warehouse_id uuid NOT NULL REFERENCES warehouses(id),
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','COUNTING','APPROVED','CANCELLED')),
    counted_by uuid NOT NULL REFERENCES users(id),
    approved_by uuid REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (approved_by IS DISTINCT FROM counted_by),
    CHECK ((status = 'APPROVED') = (approved_by IS NOT NULL))
);
CREATE TABLE stocktake_items (
    stocktake_id uuid NOT NULL REFERENCES stocktakes(id),
    device_id uuid NOT NULL REFERENCES devices(id),
    is_found boolean NOT NULL,
    note text,
    PRIMARY KEY (stocktake_id, device_id)
);
CREATE TABLE payments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL REFERENCES sales_orders(id),
    method text NOT NULL CHECK (method IN ('CASH','BANK_TRANSFER','CARD')),
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','CAPTURED','FAILED')),
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    provider_reference text CHECK (provider_reference IS NULL OR length(btrim(provider_reference)) > 0),
    confirmed_by uuid REFERENCES users(id),
    captured_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'CAPTURED') = (captured_at IS NOT NULL)),
    CHECK (status <> 'CAPTURED' OR confirmed_by IS NOT NULL)
);
CREATE UNIQUE INDEX uq_payments_provider_reference ON payments(method, provider_reference) WHERE provider_reference IS NOT NULL;
CREATE TABLE invoices (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL UNIQUE REFERENCES sales_orders(id),
    number text NOT NULL UNIQUE,
    amount numeric(14,2) NOT NULL CHECK (amount >= 0),
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ISSUED','FAILED','CANCELLED')),
    provider_reference text UNIQUE,
    issued_at timestamptz,
    CHECK ((status = 'ISSUED') = (issued_at IS NOT NULL))
);
CREATE TABLE deliveries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL REFERENCES sales_orders(id),
    address_snapshot text NOT NULL,
    scheduled_at timestamptz,
    delivered_at timestamptz,
    status text NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED','ASSIGNED','IN_TRANSIT','DELIVERED','FAILED','CANCELLED')),
    UNIQUE (id, order_id),
    CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL))
);
CREATE TABLE delivery_items (
    delivery_id uuid NOT NULL,
    order_id uuid NOT NULL,
    device_id uuid NOT NULL,
    PRIMARY KEY (delivery_id, device_id),
    FOREIGN KEY (delivery_id, order_id) REFERENCES deliveries(id, order_id),
    FOREIGN KEY (order_id, device_id) REFERENCES order_devices(order_id, device_id)
);
CREATE TABLE technician_assignments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id uuid NOT NULL REFERENCES deliveries(id),
    technician_id uuid NOT NULL REFERENCES users(id),
    assigned_by uuid NOT NULL REFERENCES users(id),
    status text NOT NULL DEFAULT 'ASSIGNED' CHECK (status IN ('ASSIGNED','COMPLETED','CANCELLED')),
    completed_at timestamptz,
    UNIQUE (delivery_id, technician_id),
    CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);
CREATE TABLE warranty_policies (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE,
    name text NOT NULL,
    duration_months integer NOT NULL CHECK (duration_months BETWEEN 1 AND 120),
    terms text NOT NULL
);
CREATE TABLE warranties (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id uuid NOT NULL REFERENCES devices(id),
    policy_id uuid NOT NULL REFERENCES warranty_policies(id),
    customer_id uuid NOT NULL REFERENCES customers(id),
    starts_on date NOT NULL,
    ends_on date NOT NULL CHECK (ends_on >= starts_on),
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','EXPIRED','VOID')),
    UNIQUE (device_id, policy_id)
);
CREATE TABLE warranty_claims (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    warranty_id uuid NOT NULL REFERENCES warranties(id),
    code text NOT NULL UNIQUE,
    description text NOT NULL,
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','REJECTED')),
    opened_by uuid NOT NULL REFERENCES users(id),
    opened_at timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz,
    CHECK (status <> 'RESOLVED' OR resolved_at IS NOT NULL)
);
CREATE TABLE returns (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL REFERENCES sales_orders(id),
    code text NOT NULL UNIQUE,
    reason text NOT NULL,
    status text NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','APPROVED','REJECTED','COMPLETED')),
    requested_by uuid NOT NULL REFERENCES users(id),
    approved_by uuid REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, order_id),
    CHECK (approved_by IS DISTINCT FROM requested_by),
    CHECK (status NOT IN ('APPROVED','COMPLETED') OR approved_by IS NOT NULL)
);
CREATE TABLE return_items (
    return_id uuid NOT NULL,
    order_id uuid NOT NULL,
    device_id uuid NOT NULL,
    refund_amount numeric(14,2) NOT NULL CHECK (refund_amount >= 0),
    condition_note text NOT NULL,
    PRIMARY KEY (return_id, device_id),
    FOREIGN KEY (return_id, order_id) REFERENCES returns(id, order_id),
    FOREIGN KEY (order_id, device_id) REFERENCES order_devices(order_id, device_id)
);
CREATE TABLE ai_recommendations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    source_product_id uuid NOT NULL REFERENCES products(id),
    recommended_product_id uuid NOT NULL REFERENCES products(id),
    source text NOT NULL CHECK (source IN ('RULE','LLM','CACHE')),
    score numeric(5,4) NOT NULL CHECK (score BETWEEN 0 AND 1),
    model_version text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    CHECK (source_product_id <> recommended_product_id)
);
CREATE TABLE audit_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid NOT NULL REFERENCES users(id),
    action text NOT NULL,
    entity_type text NOT NULL,
    entity_id uuid NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(metadata) = 'object'),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type text NOT NULL,
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    UNIQUE (aggregate_id, event_type)
);

-- Access paths: QR/serial/IMEI have unique indexes already.
CREATE INDEX ix_devices_stock ON devices(warehouse_id, product_id, status);
CREATE INDEX ix_devices_reserved ON devices(reserved_order_id) WHERE reserved_order_id IS NOT NULL;
CREATE INDEX ix_devices_receipt ON devices(receipt_item_id);
CREATE INDEX ix_sales_orders_store_date ON sales_orders(store_id, created_at DESC);
CREATE INDEX ix_sales_orders_customer ON sales_orders(customer_id);
CREATE INDEX ix_sales_items_product ON sales_order_items(product_id);
CREATE INDEX ix_order_devices_device ON order_devices(device_id);
CREATE INDEX ix_payments_order ON payments(order_id);
CREATE INDEX ix_movements_device_date ON inventory_movements(device_id, created_at DESC);
CREATE INDEX ix_outbox_pending ON outbox_events(created_at) WHERE published_at IS NULL;
CREATE INDEX ix_audit_entity_date ON audit_logs(entity_type, entity_id, created_at DESC);
CREATE INDEX ix_warranties_customer ON warranties(customer_id);
CREATE INDEX ix_ai_product_expiry ON ai_recommendations(source_product_id, expires_at DESC);
CREATE INDEX ix_user_roles_user ON user_roles(user_id);
CREATE INDEX ix_products_category ON products(category_id);
CREATE INDEX ix_products_brand ON products(brand_id);

CREATE VIEW stock_availability AS
SELECT warehouse_id, product_id,
       count(*) FILTER (WHERE status = 'IN_STOCK') AS available,
       count(*) FILTER (WHERE status = 'RESERVED') AS reserved,
       count(*) FILTER (WHERE status = 'SOLD') AS sold
FROM devices GROUP BY warehouse_id, product_id;

-- Serialize cart edits/payment callbacks with the parent order transition.
CREATE FUNCTION guard_order_child() RETURNS trigger LANGUAGE plpgsql
SECURITY DEFINER SET search_path = pg_catalog, sasms AS $$
DECLARE old_order uuid; new_order uuid; order_status text;
BEGIN
    IF TG_OP <> 'INSERT' THEN old_order := OLD.order_id; END IF;
    IF TG_OP <> 'DELETE' THEN new_order := NEW.order_id; END IF;
    IF TG_OP = 'UPDATE' AND old_order <> new_order THEN
        RAISE EXCEPTION 'Moving rows between orders is forbidden' USING ERRCODE = '23514';
    END IF;
    IF TG_TABLE_NAME = 'payments' AND TG_OP <> 'INSERT' THEN
        IF OLD.status = 'CAPTURED' THEN
            RAISE EXCEPTION 'Captured payments are immutable; use a reviewed refund workflow' USING ERRCODE = '23514';
        END IF;
    END IF;
    SELECT status INTO order_status FROM sasms.sales_orders
      WHERE id = coalesce(new_order, old_order) FOR UPDATE;
    IF (TG_TABLE_NAME = 'payments' AND order_status NOT IN ('DRAFT','RESERVED'))
       OR (TG_TABLE_NAME <> 'payments' AND order_status <> 'DRAFT') THEN
        RAISE EXCEPTION 'Order is frozen: %', order_status USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER guard_sales_items BEFORE INSERT OR UPDATE OR DELETE ON sales_order_items
FOR EACH ROW EXECUTE FUNCTION guard_order_child();
CREATE TRIGGER guard_order_devices BEFORE INSERT OR UPDATE OR DELETE ON order_devices
FOR EACH ROW EXECUTE FUNCTION guard_order_child();
CREATE TRIGGER guard_payments BEFORE INSERT OR UPDATE OR DELETE ON payments
FOR EACH ROW EXECUTE FUNCTION guard_order_child();

CREATE FUNCTION receive_device(p_receipt_item uuid, p_serial text, p_imei text, p_actor uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, sasms AS $$
DECLARE line sasms.goods_receipt_items; receipt sasms.goods_receipts; po_line sasms.purchase_order_items;
        po sasms.purchase_orders;
        device_id uuid; current_count bigint;
BEGIN
    SELECT * INTO STRICT line FROM sasms.goods_receipt_items WHERE id = p_receipt_item;
    -- Lock the PO header first so approval cannot be revoked mid-receive.
    SELECT * INTO STRICT po FROM sasms.purchase_orders WHERE id = line.purchase_order_id FOR UPDATE;
    SELECT * INTO STRICT receipt FROM sasms.goods_receipts WHERE id = line.receipt_id FOR UPDATE;
    SELECT * INTO STRICT line FROM sasms.goods_receipt_items WHERE id = p_receipt_item FOR UPDATE;
    SELECT * INTO STRICT po_line FROM sasms.purchase_order_items WHERE id = line.purchase_order_item_id FOR UPDATE;
    IF receipt.status <> 'OPEN' OR po.status <> 'APPROVED' OR line.purchase_order_id <> po.id THEN
        RAISE EXCEPTION 'Receipt must be OPEN and purchase order APPROVED' USING ERRCODE = '23514';
    END IF;
    SELECT count(*) INTO current_count FROM sasms.devices WHERE receipt_item_id = line.id;
    IF current_count >= line.quantity THEN
        RAISE EXCEPTION 'Receipt quantity exceeded' USING ERRCODE = '23514';
    END IF;
    SELECT count(*) INTO current_count FROM sasms.devices d JOIN sasms.goods_receipt_items ri ON ri.id = d.receipt_item_id
      WHERE ri.purchase_order_item_id = po_line.id;
    IF current_count >= po_line.quantity THEN
        RAISE EXCEPTION 'Purchase order quantity exceeded' USING ERRCODE = '23514';
    END IF;
    INSERT INTO sasms.devices(product_id, receipt_item_id, warehouse_id, serial, imei)
      VALUES (line.product_id, line.id, receipt.warehouse_id, upper(btrim(p_serial)), nullif(btrim(p_imei), ''))
      RETURNING id INTO device_id;
    INSERT INTO sasms.inventory_movements(device_id, warehouse_id, kind, actor_id, reason)
      VALUES (device_id, receipt.warehouse_id, 'RECEIVE', p_actor, receipt.code);
    INSERT INTO sasms.audit_logs(actor_id, action, entity_type, entity_id)
      VALUES (p_actor, 'inventory.receive', 'device', device_id);
    INSERT INTO sasms.outbox_events(aggregate_type, aggregate_id, event_type, payload)
      VALUES ('device', device_id, 'DeviceReceived', jsonb_build_object('device_id', device_id));
    RETURN device_id;
END;
$$;

CREATE FUNCTION reserve_order(p_order uuid, p_actor uuid) RETURNS numeric
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, sasms AS $$
DECLARE ord sasms.sales_orders; dev sasms.devices; amount numeric;
BEGIN
    SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id = p_order FOR UPDATE;
    IF ord.status IN ('RESERVED','PAID') THEN RETURN ord.total_amount; END IF;
    IF ord.status <> 'DRAFT' THEN RAISE EXCEPTION 'Order is not DRAFT' USING ERRCODE = '23514'; END IF;
    IF NOT EXISTS (SELECT 1 FROM sasms.sales_order_items WHERE order_id = p_order) THEN
        RAISE EXCEPTION 'Empty order' USING ERRCODE = '23514';
    END IF;
    IF EXISTS (
        SELECT 1 FROM sasms.sales_order_items i
        WHERE i.order_id = p_order AND i.quantity <> (
            SELECT count(*) FROM sasms.order_devices od WHERE od.order_item_id = i.id AND od.active
        )
    ) THEN RAISE EXCEPTION 'Device count must equal line quantity' USING ERRCODE = '23514'; END IF;
    -- Global order avoids deadlocks when carts select multiple common devices.
    FOR dev IN SELECT d.* FROM sasms.devices d JOIN sasms.order_devices od ON od.device_id = d.id
        WHERE od.order_id = p_order AND od.active ORDER BY d.id FOR UPDATE OF d
    LOOP
        IF dev.status <> 'IN_STOCK' OR dev.warehouse_id <> ord.warehouse_id THEN
            RAISE EXCEPTION 'Device unavailable or in another warehouse: %', dev.id USING ERRCODE = '23514';
        END IF;
        UPDATE sasms.devices SET status = 'RESERVED', reserved_order_id = p_order WHERE id = dev.id;
        INSERT INTO sasms.inventory_movements(device_id, warehouse_id, kind, order_id, actor_id, reason)
          VALUES (dev.id, dev.warehouse_id, 'RESERVE', p_order, p_actor, ord.code);
    END LOOP;
    SELECT sum(line_total) INTO amount FROM sasms.sales_order_items WHERE order_id = p_order;
    UPDATE sasms.sales_orders SET status = 'RESERVED', total_amount = amount WHERE id = p_order;
    INSERT INTO sasms.audit_logs(actor_id, action, entity_type, entity_id)
      VALUES (p_actor, 'sales.reserve', 'sales_order', p_order);
    INSERT INTO sasms.outbox_events(aggregate_type, aggregate_id, event_type, payload)
      VALUES ('sales_order', p_order, 'OrderReserved', jsonb_build_object('order_id', p_order, 'total', amount));
    RETURN amount;
END;
$$;

CREATE FUNCTION complete_sale(p_order uuid, p_actor uuid) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, sasms AS $$
DECLARE ord sasms.sales_orders; dev sasms.devices; captured numeric;
BEGIN
    SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id = p_order FOR UPDATE;
    IF ord.status = 'PAID' THEN RETURN ord.id; END IF;
    IF ord.status <> 'RESERVED' THEN RAISE EXCEPTION 'Order must be RESERVED' USING ERRCODE = '23514'; END IF;
    SELECT coalesce(sum(amount), 0) INTO captured FROM sasms.payments WHERE order_id = p_order AND status = 'CAPTURED';
    IF captured <> ord.total_amount THEN
        RAISE EXCEPTION 'Captured payment must equal total: % <> %', captured, ord.total_amount USING ERRCODE = '23514';
    END IF;
    FOR dev IN SELECT d.* FROM sasms.devices d JOIN sasms.order_devices od ON od.device_id = d.id
        WHERE od.order_id = p_order AND od.active ORDER BY d.id FOR UPDATE OF d
    LOOP
        IF dev.status <> 'RESERVED' OR dev.reserved_order_id IS DISTINCT FROM p_order THEN
            RAISE EXCEPTION 'Reservation ownership mismatch' USING ERRCODE = '23514';
        END IF;
        UPDATE sasms.devices SET status = 'SOLD', reserved_order_id = NULL, sold_order_id = p_order, sold_at = now() WHERE id = dev.id;
        INSERT INTO sasms.inventory_movements(device_id, warehouse_id, kind, order_id, actor_id, reason)
          VALUES (dev.id, dev.warehouse_id, 'SALE', p_order, p_actor, ord.code);
    END LOOP;
    UPDATE sasms.sales_orders SET status = 'PAID', paid_at = now() WHERE id = p_order;
    -- Internal receipt draft only: never pretends an e-invoice provider has issued it.
    INSERT INTO sasms.invoices(order_id, number, amount) VALUES (p_order, 'RECEIPT-' || ord.code, ord.total_amount);
    INSERT INTO sasms.audit_logs(actor_id, action, entity_type, entity_id)
      VALUES (p_actor, 'sales.checkout', 'sales_order', p_order);
    INSERT INTO sasms.outbox_events(aggregate_type, aggregate_id, event_type, payload)
      VALUES ('sales_order', p_order, 'OrderPaid', jsonb_build_object('order_id', p_order, 'total', ord.total_amount));
    RETURN ord.id;
END;
$$;

CREATE FUNCTION cancel_order(p_order uuid, p_actor uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, sasms AS $$
DECLARE ord sasms.sales_orders; dev sasms.devices;
BEGIN
    SELECT * INTO STRICT ord FROM sasms.sales_orders WHERE id = p_order FOR UPDATE;
    IF ord.status = 'CANCELLED' THEN RETURN; END IF;
    IF ord.status NOT IN ('DRAFT','RESERVED') THEN RAISE EXCEPTION 'Cannot cancel completed order' USING ERRCODE = '23514'; END IF;
    IF EXISTS (SELECT 1 FROM sasms.payments WHERE order_id = p_order AND status = 'CAPTURED') THEN
        RAISE EXCEPTION 'Reconcile captured funds before cancelling' USING ERRCODE = '23514';
    END IF;
    FOR dev IN SELECT * FROM sasms.devices WHERE reserved_order_id = p_order ORDER BY id FOR UPDATE
    LOOP
        UPDATE sasms.devices SET status = 'IN_STOCK', reserved_order_id = NULL WHERE id = dev.id;
        INSERT INTO sasms.inventory_movements(device_id, warehouse_id, kind, order_id, actor_id, reason)
          VALUES (dev.id, dev.warehouse_id, 'RELEASE', p_order, p_actor, ord.code);
    END LOOP;
    -- Keep allocation history. Cancelled selections are not counted as stock ownership.
    UPDATE sasms.sales_orders SET status = 'CANCELLED' WHERE id = p_order;
    INSERT INTO sasms.audit_logs(actor_id, action, entity_type, entity_id)
      VALUES (p_actor, 'sales.cancel', 'sales_order', p_order);
    INSERT INTO sasms.outbox_events(aggregate_type, aggregate_id, event_type, payload)
      VALUES ('sales_order', p_order, 'OrderCancelled', jsonb_build_object('order_id', p_order));
END;
$$;

-- Application runtime cannot mutate stock/history/status except via the API routines.
REVOKE ALL ON ALL TABLES IN SCHEMA sasms FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA sasms FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA sasms REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
COMMENT ON SCHEMA sasms IS 'D57 rebuilt Sprint 0 proposal, 37 tables; pending BA/PM sign-off';
COMMIT;
