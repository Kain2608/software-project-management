-- Idempotent reference data and synthetic demo catalog only. No login/password.
\set ON_ERROR_STOP on
BEGIN;
SET search_path = sasms, pg_catalog;
INSERT INTO roles(code, name, description) VALUES
('ADMIN','Quản trị hệ thống','Đề xuất; chờ BA/PM xác nhận; quyền quản trị phải được audit'),
('STORE_MANAGER','Quản lý cửa hàng','Đề xuất; giới hạn cửa hàng được giao'),
('WAREHOUSE','Nhân viên kho','Đề xuất; giới hạn kho/cửa hàng được giao'),
('CASHIER','Thu ngân','Đề xuất; giao dịch POS trong cửa hàng được giao'),
('SALES','Nhân viên bán hàng','Đề xuất; chỉ xem nghiệp vụ trong cửa hàng'),
('DISPATCHER','Điều phối giao lắp','Đề xuất; điều phối trong cửa hàng được giao'),
('TECHNICIAN','Kỹ thuật viên','Đề xuất; chỉ nhiệm vụ được phân công'),
('CUSTOMER_SERVICE','Chăm sóc khách hàng','Đề xuất; hồ sơ hỗ trợ được giao'),
('ANALYST','Chuyên viên báo cáo','Đề xuất; dữ liệu tổng hợp/đã che thông tin cá nhân')
ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(code, description) VALUES
('catalog.read','Xem danh mục'), ('catalog.write','Quản lý danh mục'),
('inventory.read','Xem tồn kho'), ('inventory.receive','Nhập kho'),
('inventory.adjust','Duyệt điều chỉnh kho'), ('sales.read','Xem đơn hàng'),
('sales.checkout','Chốt bán hàng'), ('sales.approve','Duyệt ngoại lệ bán hàng'),
('payments.read','Xem thanh toán'), ('payments.refund','Duyệt hoàn tiền'),
('logistics.manage','Điều phối giao lắp'), ('logistics.execute','Thực hiện giao lắp được giao'),
('warranty.manage','Xử lý bảo hành được giao'), ('reports.read','Xem báo cáo'),
('iam.manage','Quản lý tài khoản/quyền'), ('audit.read','Xem nhật ký'), ('ai.use','Dùng gợi ý AI')
ON CONFLICT (code) DO NOTHING;
-- Explicit allowlist: a permission added later is not silently granted on reseed.
INSERT INTO role_permissions(role_code, permission_code)
SELECT r.role_code, p.code FROM (VALUES ('ADMIN'),('STORE_MANAGER')) AS r(role_code)
CROSS JOIN permissions p WHERE p.code IN (
    'catalog.read','catalog.write','inventory.read','inventory.receive','inventory.adjust',
    'sales.read','sales.checkout','sales.approve','payments.read','payments.refund',
    'logistics.manage','logistics.execute','warranty.manage','reports.read','iam.manage','audit.read','ai.use'
) AND (r.role_code = 'ADMIN' OR p.code <> 'iam.manage') ON CONFLICT DO NOTHING;
INSERT INTO role_permissions(role_code, permission_code)
SELECT role_code, unnest(permission_codes) FROM (VALUES
('WAREHOUSE', ARRAY['catalog.read','inventory.read','inventory.receive','sales.read']),
('CASHIER', ARRAY['catalog.read','inventory.read','sales.read','sales.checkout','payments.read','ai.use']),
('SALES', ARRAY['catalog.read','inventory.read','sales.read','ai.use']),
('DISPATCHER', ARRAY['catalog.read','inventory.read','sales.read','logistics.manage','logistics.execute']),
('TECHNICIAN', ARRAY['catalog.read','inventory.read','sales.read','logistics.execute','warranty.manage']),
('CUSTOMER_SERVICE', ARRAY['catalog.read','inventory.read','sales.read','payments.read','warranty.manage','ai.use']),
('ANALYST', ARRAY['catalog.read','inventory.read','sales.read','payments.read','reports.read'])
) AS grants(role_code, permission_codes) ON CONFLICT DO NOTHING;

INSERT INTO users(id, email, full_name, is_active) VALUES
('00000000-0000-4000-8000-000000000001','demo.operator@example.invalid','Nhân viên DEMO (không có đăng nhập)',false)
ON CONFLICT DO NOTHING;
INSERT INTO stores(id, code, name, address) VALUES
('00000000-0000-4000-8000-000000000010','DEMO-HN','Cửa hàng dữ liệu mô phỏng','Địa chỉ mô phỏng')
ON CONFLICT DO NOTHING;
INSERT INTO warehouses(id, store_id, code, name) VALUES
('00000000-0000-4000-8000-000000000011','00000000-0000-4000-8000-000000000010','DEMO-HN-KHO','Kho DEMO')
ON CONFLICT DO NOTHING;
INSERT INTO brands(id, name) VALUES
('00000000-0000-4000-8000-000000000020','Hãng DEMO') ON CONFLICT DO NOTHING;
INSERT INTO categories(id, name) VALUES
('00000000-0000-4000-8000-000000000021','Tivi DEMO'),
('00000000-0000-4000-8000-000000000022','Phụ kiện DEMO') ON CONFLICT DO NOTHING;
INSERT INTO products(id, sku, brand_id, category_id, name, specifications, list_price, warranty_months) VALUES
('00000000-0000-4000-8000-000000000030','DEMO-TV-55','00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000021','Tivi DEMO 55 inch','{"screen_inches":55,"resolution":"4K"}',12000000,24),
('00000000-0000-4000-8000-000000000031','DEMO-HDMI','00000000-0000-4000-8000-000000000020','00000000-0000-4000-8000-000000000022','Cáp HDMI DEMO','{"length_m":2}',150000,12)
ON CONFLICT DO NOTHING;
INSERT INTO suppliers(id, code, name) VALUES
('00000000-0000-4000-8000-000000000040','DEMO-SUP','Nhà cung cấp mô phỏng') ON CONFLICT DO NOTHING;
INSERT INTO purchase_orders(id, code, supplier_id, warehouse_id, status, created_by) VALUES
('00000000-0000-4000-8000-000000000050','DEMO-PO-001','00000000-0000-4000-8000-000000000040','00000000-0000-4000-8000-000000000011','APPROVED','00000000-0000-4000-8000-000000000001') ON CONFLICT DO NOTHING;
INSERT INTO purchase_order_items(id, purchase_order_id, product_id, quantity, unit_cost) VALUES
('00000000-0000-4000-8000-000000000051','00000000-0000-4000-8000-000000000050','00000000-0000-4000-8000-000000000030',100005,10000000),
('00000000-0000-4000-8000-000000000052','00000000-0000-4000-8000-000000000050','00000000-0000-4000-8000-000000000031',100,100000) ON CONFLICT DO NOTHING;
INSERT INTO goods_receipts(id, code, purchase_order_id, warehouse_id, received_by) VALUES
('00000000-0000-4000-8000-000000000060','DEMO-GR-001','00000000-0000-4000-8000-000000000050','00000000-0000-4000-8000-000000000011','00000000-0000-4000-8000-000000000001') ON CONFLICT DO NOTHING;
INSERT INTO goods_receipt_items(id, receipt_id, purchase_order_id, purchase_order_item_id, product_id, quantity) VALUES
('00000000-0000-4000-8000-000000000061','00000000-0000-4000-8000-000000000060','00000000-0000-4000-8000-000000000050','00000000-0000-4000-8000-000000000051','00000000-0000-4000-8000-000000000030',100005),
('00000000-0000-4000-8000-000000000062','00000000-0000-4000-8000-000000000060','00000000-0000-4000-8000-000000000050','00000000-0000-4000-8000-000000000052','00000000-0000-4000-8000-000000000031',100)
ON CONFLICT DO NOTHING;
-- Quantity above is a test capacity, not an assertion of real received stock.
-- Devices are created only by receive_device; fresh seed stock is zero.
COMMIT;
