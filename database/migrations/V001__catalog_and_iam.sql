-- Sprint 1 / S1-03 + S1-10 / Tran Trung Kien.
-- Pure SQL migration, executed once inside the migration runner transaction.
-- Existing Sprint 0 records are preserved. Incompatible existing data causes a
-- validation error and rollback; this migration never silently edits that data.
-- Nine business roles remain a proposal pending BA/PM confirmation (D54).

ALTER TABLE sasms.products
    ADD COLUMN revision integer NOT NULL DEFAULT 0,
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE sasms.products
    ADD CONSTRAINT ck_products_s1_sku CHECK (
        length(sku) BETWEEN 1 AND 64 AND sku !~ '[[:space:]]'
    ) NOT VALID,
    ADD CONSTRAINT ck_products_s1_name CHECK (
        name = btrim(name) AND length(name) BETWEEN 1 AND 200
    ) NOT VALID,
    ADD CONSTRAINT ck_products_s1_specifications_size CHECK (
        octet_length(specifications::text) <= 65536
    ) NOT VALID,
    ADD CONSTRAINT ck_products_s1_revision CHECK (revision >= 0) NOT VALID;
ALTER TABLE sasms.products VALIDATE CONSTRAINT ck_products_s1_sku;
ALTER TABLE sasms.products VALIDATE CONSTRAINT ck_products_s1_name;
ALTER TABLE sasms.products VALIDATE CONSTRAINT ck_products_s1_specifications_size;
ALTER TABLE sasms.products VALIDATE CONSTRAINT ck_products_s1_revision;
ALTER TABLE sasms.users ADD CONSTRAINT ck_users_s1_auth_subject CHECK (
    auth_subject IS NULL OR (
        auth_subject = btrim(auth_subject) AND length(auth_subject) BETWEEN 1 AND 255
    )
) NOT VALID;
ALTER TABLE sasms.users VALIDATE CONSTRAINT ck_users_s1_auth_subject;

CREATE INDEX ix_products_s1_active_category_name
    ON sasms.products(category_id, name, id) WHERE is_active;
CREATE INDEX ix_products_s1_specifications ON sasms.products USING gin(specifications jsonb_path_ops);
CREATE INDEX ix_user_roles_s1_role_scope ON sasms.user_roles(role_code, store_id, user_id);

COMMENT ON COLUMN sasms.products.revision IS
    'Optimistic concurrency token, starts at 0 and increments on each UPDATE.';
COMMENT ON COLUMN sasms.users.auth_subject IS
    'Optional non-secret external identity mapping. Backend verifies issuer/subject and JWT; never store tokens/passwords here.';

-- Catalog identity is case-insensitive via canonical storage and existing UNIQUE.
-- The trigger also protects optimistic concurrency when a DBA legitimately edits
-- a product outside the functions. Runtime receives no direct catalog DML grant.
CREATE FUNCTION sasms.guard_product_revision() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, sasms AS $$
BEGIN
    NEW.sku := upper(btrim(NEW.sku));
    NEW.name := btrim(NEW.name);
    IF TG_OP = 'INSERT' THEN
        NEW.revision := 0;
    ELSE
        NEW.revision := OLD.revision + 1;
    END IF;
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER guard_product_revision
BEFORE INSERT OR UPDATE ON sasms.products
FOR EACH ROW EXECUTE FUNCTION sasms.guard_product_revision();

-- A NULL requested scope means a chain-wide resource, not an arbitrary store.
-- Deliberate global role assignments (user_roles.store_id IS NULL) may act in
-- any active store; local assignments may act only in that exact active store.
-- actor IDs are trusted backend context, not authentication credentials.
CREATE FUNCTION sasms.has_permission(p_actor uuid, p_permission text, p_store uuid DEFAULT NULL)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, sasms AS $$
    SELECT EXISTS (
        SELECT 1 FROM sasms.users u
        JOIN sasms.user_roles ur ON ur.user_id = u.id
        JOIN sasms.role_permissions rp ON rp.role_code = ur.role_code
        WHERE u.id = p_actor AND u.is_active
          AND rp.permission_code = p_permission
          AND (ur.store_id IS NULL OR (p_store IS NOT NULL AND ur.store_id = p_store))
    ) AND (p_store IS NULL OR EXISTS (
        SELECT 1 FROM sasms.stores s WHERE s.id = p_store AND s.is_active
    ));
$$;

CREATE FUNCTION sasms.assert_permission(p_actor uuid, p_permission text, p_store uuid DEFAULT NULL)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, sasms AS $$
BEGIN
    IF NOT sasms.has_permission(p_actor, p_permission, p_store) THEN
        RAISE EXCEPTION 'Permission denied: %', p_permission USING ERRCODE = '42501';
    END IF;
END;
$$;

CREATE FUNCTION sasms.create_product(
    p_actor uuid, p_sku text, p_brand uuid, p_category uuid, p_name text,
    p_specifications jsonb, p_price numeric, p_warranty_months integer
) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, sasms AS $$
DECLARE product_id uuid;
BEGIN
    -- Shared catalog writes require a deliberately global assignment.
    PERFORM sasms.assert_permission(p_actor, 'catalog.write', NULL);
    IF p_price IS NULL OR p_price < 0 OR p_price > 999999999999.99 THEN
        RAISE EXCEPTION 'Product price is out of range' USING ERRCODE = '23514';
    END IF;
    INSERT INTO sasms.products(sku, brand_id, category_id, name, specifications, list_price, warranty_months)
    VALUES (p_sku, p_brand, p_category, p_name, coalesce(p_specifications, '{}'::jsonb), p_price, p_warranty_months)
    RETURNING id INTO product_id;
    INSERT INTO sasms.audit_logs(actor_id, action, entity_type, entity_id, metadata)
    VALUES (p_actor, 'catalog.create', 'product', product_id,
            jsonb_build_object('revision', 0));
    RETURN product_id;
END;
$$;

CREATE FUNCTION sasms.update_product(
    p_actor uuid, p_product uuid, p_expected_revision integer, p_name text,
    p_specifications jsonb, p_price numeric, p_warranty_months integer, p_active boolean
) RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, sasms AS $$
DECLARE current_revision integer; next_revision integer;
BEGIN
    PERFORM sasms.assert_permission(p_actor, 'catalog.write', NULL);
    SELECT revision INTO current_revision FROM sasms.products WHERE id = p_product FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Product not found' USING ERRCODE = 'P0002';
    END IF;
    IF p_expected_revision IS DISTINCT FROM current_revision THEN
        RAISE EXCEPTION 'Product revision changed; reload before updating' USING ERRCODE = '40001';
    END IF;
    IF p_price IS NULL OR p_price < 0 OR p_price > 999999999999.99 THEN
        RAISE EXCEPTION 'Product price is out of range' USING ERRCODE = '23514';
    END IF;
    UPDATE sasms.products
       SET name = p_name, specifications = p_specifications, list_price = p_price,
           warranty_months = p_warranty_months, is_active = p_active
     WHERE id = p_product RETURNING revision INTO next_revision;
    INSERT INTO sasms.audit_logs(actor_id, action, entity_type, entity_id, metadata)
    VALUES (p_actor, 'catalog.update', 'product', p_product,
            jsonb_build_object('from_revision', current_revision, 'revision', next_revision, 'active', p_active));
    RETURN next_revision;
END;
$$;

-- Immutable migration snapshot, not a permission reset. Preserve revocations
-- even on the first upgrade: default grants apply only to previously absent
-- roles. Existing roles keep their exact grants, including deliberately empty
-- sets. The temporary snapshot is removed when the runner transaction commits.
CREATE TEMP TABLE s1_existing_role_codes ON COMMIT DROP AS SELECT code FROM sasms.roles;
INSERT INTO sasms.roles(code, name, description) VALUES
('ADMIN','Quản trị hệ thống','Đề xuất D54; phạm vi toàn hệ thống khi được cấp có chủ ý'),
('STORE_MANAGER','Quản lý cửa hàng','Đề xuất D54; giới hạn cửa hàng được giao'),
('WAREHOUSE','Nhân viên kho','Đề xuất D54; giới hạn kho/cửa hàng được giao'),
('CASHIER','Thu ngân','Đề xuất D54; giao dịch POS trong cửa hàng được giao'),
('SALES','Nhân viên bán hàng','Đề xuất D54; nghiệp vụ được giao'),
('DISPATCHER','Điều phối giao lắp','Đề xuất D54; điều phối trong cửa hàng được giao'),
('TECHNICIAN','Kỹ thuật viên','Đề xuất D54; chỉ nhiệm vụ được phân công'),
('CUSTOMER_SERVICE','Chăm sóc khách hàng','Đề xuất D54; hồ sơ hỗ trợ được giao'),
('ANALYST','Chuyên viên báo cáo','Đề xuất D54; dữ liệu tổng hợp đã che thông tin cá nhân')
ON CONFLICT (code) DO NOTHING;
INSERT INTO sasms.permissions(code, description) VALUES
('catalog.read','Xem danh mục'), ('catalog.write','Quản lý danh mục'),
('inventory.read','Xem tồn kho'), ('inventory.receive','Nhập kho'),
('inventory.adjust','Duyệt điều chỉnh kho'), ('sales.read','Xem đơn hàng'),
('sales.checkout','Chốt bán hàng'), ('sales.approve','Duyệt ngoại lệ bán hàng'),
('payments.read','Xem thanh toán'), ('payments.refund','Duyệt hoàn tiền'),
('logistics.manage','Điều phối giao lắp'), ('logistics.execute','Thực hiện giao lắp được giao'),
('warranty.manage','Xử lý bảo hành được giao'), ('reports.read','Xem báo cáo'),
('iam.manage','Quản lý tài khoản/quyền'), ('audit.read','Xem nhật ký'), ('ai.use','Dùng gợi ý AI')
ON CONFLICT (code) DO NOTHING;
INSERT INTO sasms.role_permissions(role_code, permission_code)
SELECT r.role_code, p.code FROM (VALUES ('ADMIN'),('STORE_MANAGER')) AS r(role_code)
CROSS JOIN sasms.permissions p WHERE p.code IN (
    'catalog.read','catalog.write','inventory.read','inventory.receive','inventory.adjust',
    'sales.read','sales.checkout','sales.approve','payments.read','payments.refund',
    'logistics.manage','logistics.execute','warranty.manage','reports.read','iam.manage','audit.read','ai.use'
) AND (r.role_code = 'ADMIN' OR p.code <> 'iam.manage')
AND NOT EXISTS (SELECT 1 FROM pg_temp.s1_existing_role_codes old WHERE old.code = r.role_code)
ON CONFLICT DO NOTHING;
INSERT INTO sasms.role_permissions(role_code, permission_code)
SELECT role_code, unnest(permission_codes) FROM (VALUES
('WAREHOUSE', ARRAY['catalog.read','inventory.read','inventory.receive','sales.read']),
('CASHIER', ARRAY['catalog.read','inventory.read','sales.read','sales.checkout','payments.read','ai.use']),
('SALES', ARRAY['catalog.read','inventory.read','sales.read','ai.use']),
('DISPATCHER', ARRAY['catalog.read','inventory.read','sales.read','logistics.manage','logistics.execute']),
('TECHNICIAN', ARRAY['catalog.read','inventory.read','sales.read','logistics.execute','warranty.manage']),
('CUSTOMER_SERVICE', ARRAY['catalog.read','inventory.read','sales.read','payments.read','warranty.manage','ai.use']),
('ANALYST', ARRAY['catalog.read','inventory.read','sales.read','payments.read','reports.read'])
) AS grants(role_code, permission_codes)
WHERE NOT EXISTS (SELECT 1 FROM pg_temp.s1_existing_role_codes old WHERE old.code = grants.role_code)
ON CONFLICT DO NOTHING;

-- A trusted backend maps verified JWT issuer/subject to users.auth_subject;
-- this migration creates no active login, JWT, password or role assignment.
REVOKE ALL ON FUNCTION sasms.guard_product_revision() FROM PUBLIC;
REVOKE ALL ON FUNCTION sasms.has_permission(uuid,text,uuid), sasms.assert_permission(uuid,text,uuid),
    sasms.create_product(uuid,text,uuid,uuid,text,jsonb,numeric,integer),
    sasms.update_product(uuid,uuid,integer,text,jsonb,numeric,integer,boolean) FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sasms_app') THEN
        REVOKE INSERT, UPDATE, DELETE ON sasms.products, sasms.brands, sasms.categories,
            sasms.product_images, sasms.users, sasms.roles, sasms.permissions,
            sasms.user_roles, sasms.role_permissions FROM sasms_app;
        GRANT EXECUTE ON FUNCTION sasms.has_permission(uuid,text,uuid), sasms.assert_permission(uuid,text,uuid),
            sasms.create_product(uuid,text,uuid,uuid,text,jsonb,numeric,integer),
            sasms.update_product(uuid,uuid,integer,text,jsonb,numeric,integer,boolean) TO sasms_app;
    END IF;
END;
$$;
