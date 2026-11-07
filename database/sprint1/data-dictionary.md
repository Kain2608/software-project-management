# Sprint 1 — Từ điển dữ liệu sau migration

Sinh từ catalog PostgreSQL bằng `scripts/generate_db_artifacts.py`. `schema.sql` + `database/migrations/*.sql` là nguồn thực thi. Mọi khóa ngoại dùng NO ACTION mặc định; không xóa dây chuyền chứng từ. Các CHECK, index và cột sinh được ghi dưới từng bảng.

## `ai_recommendations`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `source_product_id` | `uuid` | Không | — | FK |
| `recommended_product_id` | `uuid` | Không | — | FK |
| `source` | `text` | Không | — | — |
| `score` | `numeric(5,4)` | Không | — | — |
| `model_version` | `text` | Không | — | — |
| `created_at` | `timestamp with time zone` | Không | now() | — |
| `expires_at` | `timestamp with time zone` | Không | — | — |

```sql
CHECK ((expires_at > created_at))
CHECK ((source_product_id <> recommended_product_id))
PRIMARY KEY (id)
FOREIGN KEY (recommended_product_id) REFERENCES sasms.products(id)
CHECK (((score >= (0)::numeric) AND (score <= (1)::numeric)))
CHECK ((source = ANY (ARRAY['RULE'::text, 'LLM'::text, 'CACHE'::text])))
FOREIGN KEY (source_product_id) REFERENCES sasms.products(id)
CREATE UNIQUE INDEX ai_recommendations_pkey ON sasms.ai_recommendations USING btree (id)
CREATE INDEX ix_ai_product_expiry ON sasms.ai_recommendations USING btree (source_product_id, expires_at DESC)
```

## `audit_logs`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `actor_id` | `uuid` | Không | — | FK |
| `action` | `text` | Không | — | — |
| `entity_type` | `text` | Không | — | — |
| `entity_id` | `uuid` | Không | — | — |
| `metadata` | `jsonb` | Không | '{}'::jsonb | — |
| `created_at` | `timestamp with time zone` | Không | now() | — |

```sql
FOREIGN KEY (actor_id) REFERENCES sasms.users(id)
CHECK ((jsonb_typeof(metadata) = 'object'::text))
PRIMARY KEY (id)
CREATE UNIQUE INDEX audit_logs_pkey ON sasms.audit_logs USING btree (id)
CREATE INDEX ix_audit_entity_date ON sasms.audit_logs USING btree (entity_type, entity_id, created_at DESC)
```

## `brands`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `name` | `text` | Không | — | — |

```sql
UNIQUE (name)
PRIMARY KEY (id)
CREATE UNIQUE INDEX brands_name_key ON sasms.brands USING btree (name)
CREATE UNIQUE INDEX brands_pkey ON sasms.brands USING btree (id)
```

## `categories`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `parent_id` | `uuid` | Có | — | FK |
| `name` | `text` | Không | — | — |

```sql
CHECK ((parent_id IS DISTINCT FROM id))
UNIQUE (name)
FOREIGN KEY (parent_id) REFERENCES sasms.categories(id)
PRIMARY KEY (id)
CREATE UNIQUE INDEX categories_name_key ON sasms.categories USING btree (name)
CREATE UNIQUE INDEX categories_pkey ON sasms.categories USING btree (id)
```

## `customers`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `code` | `text` | Không | — | — |
| `full_name` | `text` | Không | — | — |
| `phone` | `text` | Có | — | — |
| `email` | `text` | Có | — | — |
| `address` | `text` | Có | — | — |
| `created_at` | `timestamp with time zone` | Không | now() | — |

```sql
UNIQUE (code)
PRIMARY KEY (id)
CREATE UNIQUE INDEX customers_code_key ON sasms.customers USING btree (code)
CREATE UNIQUE INDEX customers_pkey ON sasms.customers USING btree (id)
```

## `deliveries`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `order_id` | `uuid` | Không | — | FK |
| `address_snapshot` | `text` | Không | — | — |
| `scheduled_at` | `timestamp with time zone` | Có | — | — |
| `delivered_at` | `timestamp with time zone` | Có | — | — |
| `status` | `text` | Không | 'PLANNED'::text | — |

```sql
CHECK (((status = 'DELIVERED'::text) = (delivered_at IS NOT NULL)))
UNIQUE (id, order_id)
FOREIGN KEY (order_id) REFERENCES sasms.sales_orders(id)
PRIMARY KEY (id)
CHECK ((status = ANY (ARRAY['PLANNED'::text, 'ASSIGNED'::text, 'IN_TRANSIT'::text, 'DELIVERED'::text, 'FAILED'::text, 'CANCELLED'::text])))
CREATE UNIQUE INDEX deliveries_id_order_id_key ON sasms.deliveries USING btree (id, order_id)
CREATE UNIQUE INDEX deliveries_pkey ON sasms.deliveries USING btree (id)
```

## `delivery_items`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `delivery_id` | `uuid` | Không | — | PK, FK |
| `order_id` | `uuid` | Không | — | FK |
| `device_id` | `uuid` | Không | — | PK, FK |

```sql
FOREIGN KEY (delivery_id, order_id) REFERENCES sasms.deliveries(id, order_id)
FOREIGN KEY (order_id, device_id) REFERENCES sasms.order_devices(order_id, device_id)
PRIMARY KEY (delivery_id, device_id)
CREATE UNIQUE INDEX delivery_items_pkey ON sasms.delivery_items USING btree (delivery_id, device_id)
```

## `devices`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `product_id` | `uuid` | Không | — | FK |
| `receipt_item_id` | `uuid` | Không | — | FK |
| `warehouse_id` | `uuid` | Không | — | FK |
| `serial` | `text` | Không | — | — |
| `imei` | `text` | Có | — | — |
| `qr_token` | `uuid` | Không | gen_random_uuid() | — |
| `status` | `text` | Không | 'IN_STOCK'::text | — |
| `reserved_order_id` | `uuid` | Có | — | FK |
| `sold_order_id` | `uuid` | Có | — | FK |
| `received_at` | `timestamp with time zone` | Không | now() | — |
| `sold_at` | `timestamp with time zone` | Có | — | — |

```sql
CHECK (((status = 'RESERVED'::text) = (reserved_order_id IS NOT NULL)))
CHECK (((sold_order_id IS NOT NULL) = (sold_at IS NOT NULL)))
CHECK (((status <> 'SOLD'::text) OR (sold_order_id IS NOT NULL)))
UNIQUE (id, product_id)
CHECK ((imei ~ '^[0-9]{15}$'::text))
UNIQUE (imei)
PRIMARY KEY (id)
FOREIGN KEY (product_id) REFERENCES sasms.products(id)
UNIQUE (qr_token)
FOREIGN KEY (receipt_item_id, product_id) REFERENCES sasms.goods_receipt_items(id, product_id)
FOREIGN KEY (reserved_order_id) REFERENCES sasms.sales_orders(id)
CHECK (((serial = upper(btrim(serial))) AND (serial !~ '[[:space:]]'::text) AND ((length(serial) >= 1) AND (length(serial) <= 100))))
UNIQUE (serial)
FOREIGN KEY (sold_order_id) REFERENCES sasms.sales_orders(id)
CHECK ((status = ANY (ARRAY['IN_STOCK'::text, 'RESERVED'::text, 'SOLD'::text, 'WARRANTY'::text, 'RETURNED'::text, 'SCRAPPED'::text])))
FOREIGN KEY (warehouse_id) REFERENCES sasms.warehouses(id)
CREATE UNIQUE INDEX devices_id_product_id_key ON sasms.devices USING btree (id, product_id)
CREATE UNIQUE INDEX devices_imei_key ON sasms.devices USING btree (imei)
CREATE UNIQUE INDEX devices_pkey ON sasms.devices USING btree (id)
CREATE UNIQUE INDEX devices_qr_token_key ON sasms.devices USING btree (qr_token)
CREATE UNIQUE INDEX devices_serial_key ON sasms.devices USING btree (serial)
CREATE INDEX ix_devices_receipt ON sasms.devices USING btree (receipt_item_id)
CREATE INDEX ix_devices_reserved ON sasms.devices USING btree (reserved_order_id) WHERE (reserved_order_id IS NOT NULL)
CREATE INDEX ix_devices_stock ON sasms.devices USING btree (warehouse_id, product_id, status)
```

## `goods_receipt_items`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `receipt_id` | `uuid` | Không | — | FK |
| `purchase_order_id` | `uuid` | Không | — | FK |
| `purchase_order_item_id` | `uuid` | Không | — | FK |
| `product_id` | `uuid` | Không | — | FK |
| `quantity` | `integer` | Không | — | — |

```sql
UNIQUE (id, product_id)
PRIMARY KEY (id)
FOREIGN KEY (purchase_order_item_id, product_id, purchase_order_id) REFERENCES sasms.purchase_order_items(id, product_id, purchase_order_id)
CHECK ((quantity > 0))
UNIQUE (receipt_id, product_id)
FOREIGN KEY (receipt_id, purchase_order_id) REFERENCES sasms.goods_receipts(id, purchase_order_id)
CREATE UNIQUE INDEX goods_receipt_items_id_product_id_key ON sasms.goods_receipt_items USING btree (id, product_id)
CREATE UNIQUE INDEX goods_receipt_items_pkey ON sasms.goods_receipt_items USING btree (id)
CREATE UNIQUE INDEX goods_receipt_items_receipt_id_product_id_key ON sasms.goods_receipt_items USING btree (receipt_id, product_id)
```

## `goods_receipts`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `code` | `text` | Không | — | — |
| `purchase_order_id` | `uuid` | Không | — | FK |
| `warehouse_id` | `uuid` | Không | — | FK |
| `status` | `text` | Không | 'OPEN'::text | — |
| `received_by` | `uuid` | Không | — | FK |
| `received_at` | `timestamp with time zone` | Không | now() | — |

```sql
UNIQUE (code)
UNIQUE (id, purchase_order_id)
PRIMARY KEY (id)
FOREIGN KEY (purchase_order_id, warehouse_id) REFERENCES sasms.purchase_orders(id, warehouse_id)
FOREIGN KEY (received_by) REFERENCES sasms.users(id)
CHECK ((status = ANY (ARRAY['OPEN'::text, 'CLOSED'::text])))
CREATE UNIQUE INDEX goods_receipts_code_key ON sasms.goods_receipts USING btree (code)
CREATE UNIQUE INDEX goods_receipts_id_purchase_order_id_key ON sasms.goods_receipts USING btree (id, purchase_order_id)
CREATE UNIQUE INDEX goods_receipts_pkey ON sasms.goods_receipts USING btree (id)
```

## `identifier_rules`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `brand_id` | `uuid` | Không | — | FK |
| `version` | `integer` | Không | — | — |
| `serial_pattern` | `text` | Không | — | — |
| `imei_required` | `boolean` | Không | false | — |
| `source_reference` | `text` | Có | — | — |
| `approved_by` | `uuid` | Có | — | FK |
| `approved_at` | `timestamp with time zone` | Có | — | — |
| `is_active` | `boolean` | Không | false | — |
| `created_at` | `timestamp with time zone` | Không | now() | — |

```sql
FOREIGN KEY (approved_by) REFERENCES sasms.users(id)
FOREIGN KEY (brand_id) REFERENCES sasms.brands(id)
UNIQUE (brand_id, version)
CHECK (((approved_by IS NULL) = (approved_at IS NULL)))
CHECK (((approved_at IS NULL) OR (length(btrim(source_reference)) > 0)))
CHECK (((NOT is_active) OR ((approved_at IS NOT NULL) AND (approved_by IS NOT NULL) AND (source_reference IS NOT NULL))))
PRIMARY KEY (id)
CHECK ((((length(serial_pattern) >= 2) AND (length(serial_pattern) <= 256)) AND ("left"(serial_pattern, 1) = '^'::text) AND ("right"(serial_pattern, 1) = '$'::text)))
CHECK ((version > 0))
CREATE UNIQUE INDEX identifier_rules_brand_id_version_key ON sasms.identifier_rules USING btree (brand_id, version)
CREATE UNIQUE INDEX identifier_rules_pkey ON sasms.identifier_rules USING btree (id)
CREATE UNIQUE INDEX uq_identifier_rules_active_brand ON sasms.identifier_rules USING btree (brand_id) WHERE is_active
```

## `inventory_import_batches`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `request_key` | `uuid` | Không | — | PK |
| `receipt_item_id` | `uuid` | Không | — | FK |
| `actor_id` | `uuid` | Không | — | FK |
| `normalized_payload` | `jsonb` | Không | — | — |
| `device_ids` | `uuid[]` | Không | — | — |
| `created_at` | `timestamp with time zone` | Không | now() | — |

```sql
FOREIGN KEY (actor_id) REFERENCES sasms.users(id)
CHECK ((cardinality(device_ids) = jsonb_array_length(normalized_payload)))
CHECK (((cardinality(device_ids) >= 1) AND (cardinality(device_ids) <= 500)))
CHECK (((jsonb_typeof(normalized_payload) = 'array'::text) AND ((jsonb_array_length(normalized_payload) >= 1) AND (jsonb_array_length(normalized_payload) <= 500))))
PRIMARY KEY (request_key)
FOREIGN KEY (receipt_item_id) REFERENCES sasms.goods_receipt_items(id)
CREATE UNIQUE INDEX inventory_import_batches_pkey ON sasms.inventory_import_batches USING btree (request_key)
CREATE INDEX ix_import_batches_receipt_date ON sasms.inventory_import_batches USING btree (receipt_item_id, created_at DESC)
```

## `inventory_movements`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `device_id` | `uuid` | Không | — | FK |
| `warehouse_id` | `uuid` | Không | — | FK |
| `kind` | `text` | Không | — | — |
| `order_id` | `uuid` | Có | — | FK |
| `actor_id` | `uuid` | Không | — | FK |
| `reason` | `text` | Không | — | — |
| `created_at` | `timestamp with time zone` | Không | now() | — |

```sql
FOREIGN KEY (actor_id) REFERENCES sasms.users(id)
FOREIGN KEY (device_id) REFERENCES sasms.devices(id)
CHECK ((kind = ANY (ARRAY['RECEIVE'::text, 'RESERVE'::text, 'RELEASE'::text, 'SALE'::text, 'RETURN'::text, 'TRANSFER_IN'::text, 'TRANSFER_OUT'::text, 'ADJUST'::text])))
FOREIGN KEY (order_id) REFERENCES sasms.sales_orders(id)
PRIMARY KEY (id)
FOREIGN KEY (warehouse_id) REFERENCES sasms.warehouses(id)
CREATE UNIQUE INDEX inventory_movements_pkey ON sasms.inventory_movements USING btree (id)
CREATE INDEX ix_movements_device_date ON sasms.inventory_movements USING btree (device_id, created_at DESC)
CREATE UNIQUE INDEX uq_core_movement ON sasms.inventory_movements USING btree (device_id, kind, order_id) NULLS NOT DISTINCT WHERE (kind = ANY (ARRAY['RECEIVE'::text, 'RESERVE'::text, 'RELEASE'::text, 'SALE'::text]))
```

## `invoices`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `order_id` | `uuid` | Không | — | FK |
| `number` | `text` | Không | — | — |
| `amount` | `numeric(14,2)` | Không | — | — |
| `status` | `text` | Không | 'DRAFT'::text | — |
| `provider_reference` | `text` | Có | — | — |
| `issued_at` | `timestamp with time zone` | Có | — | — |

```sql
CHECK ((amount >= (0)::numeric))
CHECK (((status = 'ISSUED'::text) = (issued_at IS NOT NULL)))
UNIQUE (number)
FOREIGN KEY (order_id) REFERENCES sasms.sales_orders(id)
UNIQUE (order_id)
PRIMARY KEY (id)
UNIQUE (provider_reference)
CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ISSUED'::text, 'FAILED'::text, 'CANCELLED'::text])))
CREATE UNIQUE INDEX invoices_number_key ON sasms.invoices USING btree (number)
CREATE UNIQUE INDEX invoices_order_id_key ON sasms.invoices USING btree (order_id)
CREATE UNIQUE INDEX invoices_pkey ON sasms.invoices USING btree (id)
CREATE UNIQUE INDEX invoices_provider_reference_key ON sasms.invoices USING btree (provider_reference)
```

## `order_devices`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `order_item_id` | `uuid` | Không | — | FK |
| `order_id` | `uuid` | Không | — | FK |
| `product_id` | `uuid` | Không | — | FK |
| `device_id` | `uuid` | Không | — | FK |
| `active` | `boolean` | Không | true | — |

```sql
FOREIGN KEY (device_id, product_id) REFERENCES sasms.devices(id, product_id)
UNIQUE (order_id, device_id)
FOREIGN KEY (order_item_id, order_id, product_id) REFERENCES sasms.sales_order_items(id, order_id, product_id)
PRIMARY KEY (id)
CREATE INDEX ix_order_devices_device ON sasms.order_devices USING btree (device_id)
CREATE UNIQUE INDEX order_devices_order_id_device_id_key ON sasms.order_devices USING btree (order_id, device_id)
CREATE UNIQUE INDEX order_devices_pkey ON sasms.order_devices USING btree (id)
```

## `outbox_events`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `aggregate_type` | `text` | Không | — | — |
| `aggregate_id` | `uuid` | Không | — | — |
| `event_type` | `text` | Không | — | — |
| `payload` | `jsonb` | Không | — | — |
| `created_at` | `timestamp with time zone` | Không | now() | — |
| `published_at` | `timestamp with time zone` | Có | — | — |
| `attempts` | `integer` | Không | 0 | — |

```sql
UNIQUE (aggregate_id, event_type)
CHECK ((attempts >= 0))
CHECK ((jsonb_typeof(payload) = 'object'::text))
PRIMARY KEY (id)
CREATE INDEX ix_outbox_pending ON sasms.outbox_events USING btree (created_at) WHERE (published_at IS NULL)
CREATE UNIQUE INDEX outbox_events_aggregate_id_event_type_key ON sasms.outbox_events USING btree (aggregate_id, event_type)
CREATE UNIQUE INDEX outbox_events_pkey ON sasms.outbox_events USING btree (id)
```

## `payments`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `order_id` | `uuid` | Không | — | FK |
| `method` | `text` | Không | — | — |
| `status` | `text` | Không | 'PENDING'::text | — |
| `amount` | `numeric(14,2)` | Không | — | — |
| `provider_reference` | `text` | Có | — | — |
| `confirmed_by` | `uuid` | Có | — | FK |
| `captured_at` | `timestamp with time zone` | Có | — | — |
| `created_at` | `timestamp with time zone` | Không | now() | — |

```sql
CHECK ((amount > (0)::numeric))
CHECK (((status = 'CAPTURED'::text) = (captured_at IS NOT NULL)))
CHECK (((status <> 'CAPTURED'::text) OR (confirmed_by IS NOT NULL)))
FOREIGN KEY (confirmed_by) REFERENCES sasms.users(id)
CHECK ((method = ANY (ARRAY['CASH'::text, 'BANK_TRANSFER'::text, 'CARD'::text])))
FOREIGN KEY (order_id) REFERENCES sasms.sales_orders(id)
PRIMARY KEY (id)
CHECK (((provider_reference IS NULL) OR (length(btrim(provider_reference)) > 0)))
CHECK ((status = ANY (ARRAY['PENDING'::text, 'CAPTURED'::text, 'FAILED'::text])))
CREATE INDEX ix_payments_order ON sasms.payments USING btree (order_id)
CREATE UNIQUE INDEX payments_pkey ON sasms.payments USING btree (id)
CREATE UNIQUE INDEX uq_payments_provider_reference ON sasms.payments USING btree (method, provider_reference) WHERE (provider_reference IS NOT NULL)
```

## `permissions`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `code` | `text` | Không | — | PK |
| `description` | `text` | Không | — | — |

```sql
PRIMARY KEY (code)
CREATE UNIQUE INDEX permissions_pkey ON sasms.permissions USING btree (code)
```

## `product_images`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `product_id` | `uuid` | Không | — | FK |
| `object_key` | `text` | Không | — | — |
| `position` | `smallint` | Không | — | — |
| `size_bytes` | `integer` | Không | — | — |

```sql
CHECK ((length(btrim(object_key)) > 0))
PRIMARY KEY (id)
CHECK ((("position" >= 1) AND ("position" <= 3)))
FOREIGN KEY (product_id) REFERENCES sasms.products(id)
UNIQUE (product_id, "position")
CHECK (((size_bytes >= 1) AND (size_bytes <= 2097152)))
CREATE UNIQUE INDEX product_images_pkey ON sasms.product_images USING btree (id)
CREATE UNIQUE INDEX product_images_product_id_position_key ON sasms.product_images USING btree (product_id, "position")
```

## `products`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `sku` | `text` | Không | — | — |
| `brand_id` | `uuid` | Không | — | FK |
| `category_id` | `uuid` | Không | — | FK |
| `name` | `text` | Không | — | — |
| `description` | `text` | Có | — | — |
| `specifications` | `jsonb` | Không | '{}'::jsonb | — |
| `list_price` | `numeric(14,2)` | Không | — | — |
| `warranty_months` | `integer` | Không | 12 | — |
| `is_active` | `boolean` | Không | true | — |
| `created_at` | `timestamp with time zone` | Không | now() | — |
| `revision` | `integer` | Không | 0 | — |
| `updated_at` | `timestamp with time zone` | Không | now() | — |

```sql
CHECK (((name = btrim(name)) AND ((length(name) >= 1) AND (length(name) <= 200))))
CHECK ((revision >= 0))
CHECK ((((length(sku) >= 1) AND (length(sku) <= 64)) AND (sku !~ '[[:space:]]'::text)))
CHECK ((octet_length((specifications)::text) <= 65536))
FOREIGN KEY (brand_id) REFERENCES sasms.brands(id)
FOREIGN KEY (category_id) REFERENCES sasms.categories(id)
CHECK ((list_price >= (0)::numeric))
PRIMARY KEY (id)
CHECK (((sku = upper(btrim(sku))) AND (length(sku) > 0)))
UNIQUE (sku)
CHECK ((jsonb_typeof(specifications) = 'object'::text))
CHECK (((warranty_months >= 0) AND (warranty_months <= 120)))
CREATE INDEX ix_products_brand ON sasms.products USING btree (brand_id)
CREATE INDEX ix_products_category ON sasms.products USING btree (category_id)
CREATE INDEX ix_products_s1_active_category_name ON sasms.products USING btree (category_id, name, id) WHERE is_active
CREATE INDEX ix_products_s1_specifications ON sasms.products USING gin (specifications jsonb_path_ops)
CREATE UNIQUE INDEX products_pkey ON sasms.products USING btree (id)
CREATE UNIQUE INDEX products_sku_key ON sasms.products USING btree (sku)
```

## `purchase_order_items`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `purchase_order_id` | `uuid` | Không | — | FK |
| `product_id` | `uuid` | Không | — | FK |
| `quantity` | `integer` | Không | — | — |
| `unit_cost` | `numeric(14,2)` | Không | — | — |

```sql
UNIQUE (id, product_id, purchase_order_id)
PRIMARY KEY (id)
FOREIGN KEY (product_id) REFERENCES sasms.products(id)
FOREIGN KEY (purchase_order_id) REFERENCES sasms.purchase_orders(id)
UNIQUE (purchase_order_id, product_id)
CHECK ((quantity > 0))
CHECK ((unit_cost >= (0)::numeric))
CREATE UNIQUE INDEX purchase_order_items_id_product_id_purchase_order_id_key ON sasms.purchase_order_items USING btree (id, product_id, purchase_order_id)
CREATE UNIQUE INDEX purchase_order_items_pkey ON sasms.purchase_order_items USING btree (id)
CREATE UNIQUE INDEX purchase_order_items_purchase_order_id_product_id_key ON sasms.purchase_order_items USING btree (purchase_order_id, product_id)
```

## `purchase_orders`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `code` | `text` | Không | — | — |
| `supplier_id` | `uuid` | Không | — | FK |
| `warehouse_id` | `uuid` | Không | — | FK |
| `status` | `text` | Không | 'DRAFT'::text | — |
| `created_by` | `uuid` | Không | — | FK |
| `created_at` | `timestamp with time zone` | Không | now() | — |

```sql
UNIQUE (code)
FOREIGN KEY (created_by) REFERENCES sasms.users(id)
UNIQUE (id, warehouse_id)
PRIMARY KEY (id)
CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text, 'CLOSED'::text, 'CANCELLED'::text])))
FOREIGN KEY (supplier_id) REFERENCES sasms.suppliers(id)
FOREIGN KEY (warehouse_id) REFERENCES sasms.warehouses(id)
CREATE UNIQUE INDEX purchase_orders_code_key ON sasms.purchase_orders USING btree (code)
CREATE UNIQUE INDEX purchase_orders_id_warehouse_id_key ON sasms.purchase_orders USING btree (id, warehouse_id)
CREATE UNIQUE INDEX purchase_orders_pkey ON sasms.purchase_orders USING btree (id)
```

## `return_items`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `return_id` | `uuid` | Không | — | PK, FK |
| `order_id` | `uuid` | Không | — | FK |
| `device_id` | `uuid` | Không | — | PK, FK |
| `refund_amount` | `numeric(14,2)` | Không | — | — |
| `condition_note` | `text` | Không | — | — |

```sql
FOREIGN KEY (order_id, device_id) REFERENCES sasms.order_devices(order_id, device_id)
PRIMARY KEY (return_id, device_id)
CHECK ((refund_amount >= (0)::numeric))
FOREIGN KEY (return_id, order_id) REFERENCES sasms.returns(id, order_id)
CREATE UNIQUE INDEX return_items_pkey ON sasms.return_items USING btree (return_id, device_id)
```

## `returns`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `order_id` | `uuid` | Không | — | FK |
| `code` | `text` | Không | — | — |
| `reason` | `text` | Không | — | — |
| `status` | `text` | Không | 'REQUESTED'::text | — |
| `requested_by` | `uuid` | Không | — | FK |
| `approved_by` | `uuid` | Có | — | FK |
| `created_at` | `timestamp with time zone` | Không | now() | — |

```sql
FOREIGN KEY (approved_by) REFERENCES sasms.users(id)
CHECK ((approved_by IS DISTINCT FROM requested_by))
CHECK (((status <> ALL (ARRAY['APPROVED'::text, 'COMPLETED'::text])) OR (approved_by IS NOT NULL)))
UNIQUE (code)
UNIQUE (id, order_id)
FOREIGN KEY (order_id) REFERENCES sasms.sales_orders(id)
PRIMARY KEY (id)
FOREIGN KEY (requested_by) REFERENCES sasms.users(id)
CHECK ((status = ANY (ARRAY['REQUESTED'::text, 'APPROVED'::text, 'REJECTED'::text, 'COMPLETED'::text])))
CREATE UNIQUE INDEX returns_code_key ON sasms.returns USING btree (code)
CREATE UNIQUE INDEX returns_id_order_id_key ON sasms.returns USING btree (id, order_id)
CREATE UNIQUE INDEX returns_pkey ON sasms.returns USING btree (id)
```

## `role_permissions`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `role_code` | `text` | Không | — | PK, FK |
| `permission_code` | `text` | Không | — | PK, FK |

```sql
FOREIGN KEY (permission_code) REFERENCES sasms.permissions(code)
PRIMARY KEY (role_code, permission_code)
FOREIGN KEY (role_code) REFERENCES sasms.roles(code)
CREATE UNIQUE INDEX role_permissions_pkey ON sasms.role_permissions USING btree (role_code, permission_code)
```

## `roles`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `code` | `text` | Không | — | PK |
| `name` | `text` | Không | — | — |
| `description` | `text` | Không | — | — |

```sql
CHECK ((code ~ '^[A-Z_]+$'::text))
PRIMARY KEY (code)
CREATE UNIQUE INDEX roles_pkey ON sasms.roles USING btree (code)
```

## `sales_order_items`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `order_id` | `uuid` | Không | — | FK |
| `product_id` | `uuid` | Không | — | FK |
| `quantity` | `integer` | Không | — | — |
| `unit_price` | `numeric(14,2)` | Không | — | — |
| `discount_amount` | `numeric(14,2)` | Không | 0 | — |
| `tax_rate` | `numeric(5,2)` | Không | 0 | — |
| `line_total` | `numeric(14,2)` | Có | GENERATED: round(((((quantity)::numeric * unit_price) - discount_amount) * ((1)::numeric + (tax_rate / (100)::numeric))), 2) | — |

```sql
CHECK (((discount_amount >= (0)::numeric) AND (discount_amount <= ((quantity)::numeric * unit_price))))
UNIQUE (id, order_id, product_id)
FOREIGN KEY (order_id) REFERENCES sasms.sales_orders(id)
UNIQUE (order_id, product_id)
PRIMARY KEY (id)
FOREIGN KEY (product_id) REFERENCES sasms.products(id)
CHECK ((quantity > 0))
CHECK (((tax_rate >= (0)::numeric) AND (tax_rate <= (100)::numeric)))
CHECK ((unit_price >= (0)::numeric))
CREATE INDEX ix_sales_items_product ON sasms.sales_order_items USING btree (product_id)
CREATE UNIQUE INDEX sales_order_items_id_order_id_product_id_key ON sasms.sales_order_items USING btree (id, order_id, product_id)
CREATE UNIQUE INDEX sales_order_items_order_id_product_id_key ON sasms.sales_order_items USING btree (order_id, product_id)
CREATE UNIQUE INDEX sales_order_items_pkey ON sasms.sales_order_items USING btree (id)
```

## `sales_orders`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `code` | `text` | Không | — | — |
| `idempotency_key` | `uuid` | Không | gen_random_uuid() | — |
| `store_id` | `uuid` | Không | — | FK |
| `warehouse_id` | `uuid` | Không | — | FK |
| `customer_id` | `uuid` | Có | — | FK |
| `channel` | `text` | Không | — | — |
| `status` | `text` | Không | 'DRAFT'::text | — |
| `total_amount` | `numeric(14,2)` | Không | 0 | — |
| `created_by` | `uuid` | Không | — | FK |
| `created_at` | `timestamp with time zone` | Không | now() | — |
| `paid_at` | `timestamp with time zone` | Có | — | — |

```sql
CHECK ((channel = ANY (ARRAY['POS'::text, 'ONLINE'::text])))
CHECK (((status = 'PAID'::text) = (paid_at IS NOT NULL)))
UNIQUE (code)
FOREIGN KEY (created_by) REFERENCES sasms.users(id)
FOREIGN KEY (customer_id) REFERENCES sasms.customers(id)
UNIQUE (idempotency_key)
PRIMARY KEY (id)
CHECK ((status = ANY (ARRAY['DRAFT'::text, 'RESERVED'::text, 'PAID'::text, 'CANCELLED'::text])))
FOREIGN KEY (store_id) REFERENCES sasms.stores(id)
CHECK ((total_amount >= (0)::numeric))
FOREIGN KEY (warehouse_id, store_id) REFERENCES sasms.warehouses(id, store_id)
CREATE INDEX ix_sales_orders_customer ON sasms.sales_orders USING btree (customer_id)
CREATE INDEX ix_sales_orders_store_date ON sasms.sales_orders USING btree (store_id, created_at DESC)
CREATE UNIQUE INDEX sales_orders_code_key ON sasms.sales_orders USING btree (code)
CREATE UNIQUE INDEX sales_orders_idempotency_key_key ON sasms.sales_orders USING btree (idempotency_key)
CREATE UNIQUE INDEX sales_orders_pkey ON sasms.sales_orders USING btree (id)
```

## `stocktake_items`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `stocktake_id` | `uuid` | Không | — | PK, FK |
| `device_id` | `uuid` | Không | — | PK, FK |
| `is_found` | `boolean` | Không | — | — |
| `note` | `text` | Có | — | — |

```sql
FOREIGN KEY (device_id) REFERENCES sasms.devices(id)
PRIMARY KEY (stocktake_id, device_id)
FOREIGN KEY (stocktake_id) REFERENCES sasms.stocktakes(id)
CREATE UNIQUE INDEX stocktake_items_pkey ON sasms.stocktake_items USING btree (stocktake_id, device_id)
```

## `stocktakes`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `code` | `text` | Không | — | — |
| `warehouse_id` | `uuid` | Không | — | FK |
| `status` | `text` | Không | 'DRAFT'::text | — |
| `counted_by` | `uuid` | Không | — | FK |
| `approved_by` | `uuid` | Có | — | FK |
| `created_at` | `timestamp with time zone` | Không | now() | — |

```sql
FOREIGN KEY (approved_by) REFERENCES sasms.users(id)
CHECK ((approved_by IS DISTINCT FROM counted_by))
CHECK (((status = 'APPROVED'::text) = (approved_by IS NOT NULL)))
UNIQUE (code)
FOREIGN KEY (counted_by) REFERENCES sasms.users(id)
PRIMARY KEY (id)
CHECK ((status = ANY (ARRAY['DRAFT'::text, 'COUNTING'::text, 'APPROVED'::text, 'CANCELLED'::text])))
FOREIGN KEY (warehouse_id) REFERENCES sasms.warehouses(id)
CREATE UNIQUE INDEX stocktakes_code_key ON sasms.stocktakes USING btree (code)
CREATE UNIQUE INDEX stocktakes_pkey ON sasms.stocktakes USING btree (id)
```

## `stores`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `code` | `text` | Không | — | — |
| `name` | `text` | Không | — | — |
| `address` | `text` | Không | — | — |
| `is_active` | `boolean` | Không | true | — |

```sql
CHECK ((length(btrim(code)) > 0))
UNIQUE (code)
PRIMARY KEY (id)
CREATE UNIQUE INDEX stores_code_key ON sasms.stores USING btree (code)
CREATE UNIQUE INDEX stores_pkey ON sasms.stores USING btree (id)
```

## `suppliers`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `code` | `text` | Không | — | — |
| `name` | `text` | Không | — | — |
| `contact_phone` | `text` | Có | — | — |
| `contact_email` | `text` | Có | — | — |

```sql
UNIQUE (code)
PRIMARY KEY (id)
CREATE UNIQUE INDEX suppliers_code_key ON sasms.suppliers USING btree (code)
CREATE UNIQUE INDEX suppliers_pkey ON sasms.suppliers USING btree (id)
```

## `technician_assignments`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `delivery_id` | `uuid` | Không | — | FK |
| `technician_id` | `uuid` | Không | — | FK |
| `assigned_by` | `uuid` | Không | — | FK |
| `status` | `text` | Không | 'ASSIGNED'::text | — |
| `completed_at` | `timestamp with time zone` | Có | — | — |

```sql
FOREIGN KEY (assigned_by) REFERENCES sasms.users(id)
CHECK (((status = 'COMPLETED'::text) = (completed_at IS NOT NULL)))
FOREIGN KEY (delivery_id) REFERENCES sasms.deliveries(id)
UNIQUE (delivery_id, technician_id)
PRIMARY KEY (id)
CHECK ((status = ANY (ARRAY['ASSIGNED'::text, 'COMPLETED'::text, 'CANCELLED'::text])))
FOREIGN KEY (technician_id) REFERENCES sasms.users(id)
CREATE UNIQUE INDEX technician_assignments_delivery_id_technician_id_key ON sasms.technician_assignments USING btree (delivery_id, technician_id)
CREATE UNIQUE INDEX technician_assignments_pkey ON sasms.technician_assignments USING btree (id)
```

## `user_roles`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `user_id` | `uuid` | Không | — | FK |
| `role_code` | `text` | Không | — | FK |
| `store_id` | `uuid` | Có | — | FK |

```sql
UNIQUE NULLS NOT DISTINCT (user_id, role_code, store_id)
PRIMARY KEY (id)
FOREIGN KEY (role_code) REFERENCES sasms.roles(code)
FOREIGN KEY (store_id) REFERENCES sasms.stores(id)
FOREIGN KEY (user_id) REFERENCES sasms.users(id)
CREATE INDEX ix_user_roles_s1_role_scope ON sasms.user_roles USING btree (role_code, store_id, user_id)
CREATE INDEX ix_user_roles_user ON sasms.user_roles USING btree (user_id)
CREATE UNIQUE INDEX uq_user_role_scope ON sasms.user_roles USING btree (user_id, role_code, store_id) NULLS NOT DISTINCT
CREATE UNIQUE INDEX user_roles_pkey ON sasms.user_roles USING btree (id)
```

## `users`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `email` | `text` | Không | — | — |
| `full_name` | `text` | Không | — | — |
| `auth_subject` | `text` | Có | — | — |
| `is_active` | `boolean` | Không | false | — |
| `created_at` | `timestamp with time zone` | Không | now() | — |

```sql
CHECK (((auth_subject IS NULL) OR ((auth_subject = btrim(auth_subject)) AND ((length(auth_subject) >= 1) AND (length(auth_subject) <= 255)))))
UNIQUE (email)
UNIQUE (auth_subject)
CHECK (((email = lower(btrim(email))) AND (email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'::text)))
CHECK ((length(btrim(full_name)) > 0))
PRIMARY KEY (id)
CREATE UNIQUE INDEX uq_users_email ON sasms.users USING btree (email)
CREATE UNIQUE INDEX users_auth_subject_key ON sasms.users USING btree (auth_subject)
CREATE UNIQUE INDEX users_pkey ON sasms.users USING btree (id)
```

## `warehouses`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `store_id` | `uuid` | Không | — | FK |
| `code` | `text` | Không | — | — |
| `name` | `text` | Không | — | — |

```sql
UNIQUE (code)
UNIQUE (id, store_id)
PRIMARY KEY (id)
FOREIGN KEY (store_id) REFERENCES sasms.stores(id)
CREATE UNIQUE INDEX warehouses_code_key ON sasms.warehouses USING btree (code)
CREATE UNIQUE INDEX warehouses_id_store_id_key ON sasms.warehouses USING btree (id, store_id)
CREATE UNIQUE INDEX warehouses_pkey ON sasms.warehouses USING btree (id)
```

## `warranties`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `device_id` | `uuid` | Không | — | FK |
| `policy_id` | `uuid` | Không | — | FK |
| `customer_id` | `uuid` | Không | — | FK |
| `starts_on` | `date` | Không | — | — |
| `ends_on` | `date` | Không | — | — |
| `status` | `text` | Không | 'ACTIVE'::text | — |

```sql
CHECK ((ends_on >= starts_on))
FOREIGN KEY (customer_id) REFERENCES sasms.customers(id)
FOREIGN KEY (device_id) REFERENCES sasms.devices(id)
UNIQUE (device_id, policy_id)
PRIMARY KEY (id)
FOREIGN KEY (policy_id) REFERENCES sasms.warranty_policies(id)
CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'EXPIRED'::text, 'VOID'::text])))
CREATE INDEX ix_warranties_customer ON sasms.warranties USING btree (customer_id)
CREATE UNIQUE INDEX warranties_device_id_policy_id_key ON sasms.warranties USING btree (device_id, policy_id)
CREATE UNIQUE INDEX warranties_pkey ON sasms.warranties USING btree (id)
```

## `warranty_claims`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `warranty_id` | `uuid` | Không | — | FK |
| `code` | `text` | Không | — | — |
| `description` | `text` | Không | — | — |
| `status` | `text` | Không | 'OPEN'::text | — |
| `opened_by` | `uuid` | Không | — | FK |
| `opened_at` | `timestamp with time zone` | Không | now() | — |
| `resolved_at` | `timestamp with time zone` | Có | — | — |

```sql
CHECK (((status <> 'RESOLVED'::text) OR (resolved_at IS NOT NULL)))
UNIQUE (code)
FOREIGN KEY (opened_by) REFERENCES sasms.users(id)
PRIMARY KEY (id)
CHECK ((status = ANY (ARRAY['OPEN'::text, 'IN_PROGRESS'::text, 'RESOLVED'::text, 'REJECTED'::text])))
FOREIGN KEY (warranty_id) REFERENCES sasms.warranties(id)
CREATE UNIQUE INDEX warranty_claims_code_key ON sasms.warranty_claims USING btree (code)
CREATE UNIQUE INDEX warranty_claims_pkey ON sasms.warranty_claims USING btree (id)
```

## `warranty_policies`

| Cột | Kiểu PostgreSQL | NULL | Mặc định / cột sinh | Khóa |
|---|---|---|---|---|
| `id` | `uuid` | Không | gen_random_uuid() | PK |
| `code` | `text` | Không | — | — |
| `name` | `text` | Không | — | — |
| `duration_months` | `integer` | Không | — | — |
| `terms` | `text` | Không | — | — |

```sql
UNIQUE (code)
CHECK (((duration_months >= 1) AND (duration_months <= 120)))
PRIMARY KEY (id)
CREATE UNIQUE INDEX warranty_policies_code_key ON sasms.warranty_policies USING btree (code)
CREATE UNIQUE INDEX warranty_policies_pkey ON sasms.warranty_policies USING btree (id)
```
