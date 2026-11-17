-- D34 FR-01: canonical nine business roles; retain legacy role rows/references.
ALTER TABLE sasms.roles ADD COLUMN is_legacy boolean NOT NULL DEFAULT false;
UPDATE sasms.roles SET is_legacy=true WHERE code IN ('WAREHOUSE','SALES','ANALYST');
WITH added AS (
 INSERT INTO sasms.roles(code,name,description) VALUES
 ('CATALOG_MANAGER','Quản trị danh mục','D34: SKU, thông số và giá; không sửa giao dịch đã xác nhận'),
 ('WAREHOUSE_STAFF','Nhân viên kho','D34: nhập và tra cứu trong phạm vi được giao'),
 ('SALES_STAFF','Nhân viên bán hàng','D34: tra tồn, thông số và gợi ý; không ghi PAID')
 ON CONFLICT(code) DO NOTHING RETURNING code
)
INSERT INTO sasms.role_permissions(role_code,permission_code)
 SELECT a.code,p.permission_code FROM added a
 JOIN sasms.role_permissions p ON p.role_code=CASE a.code
  WHEN 'WAREHOUSE_STAFF' THEN 'WAREHOUSE' WHEN 'SALES_STAFF' THEN 'SALES' END
 UNION ALL
 SELECT a.code,p FROM added a CROSS JOIN unnest(ARRAY['catalog.read','catalog.write']) p
 WHERE a.code='CATALOG_MANAGER'
 ON CONFLICT DO NOTHING;

-- An ADMIN identity does not automatically approve refunds/returns. Multiple
-- duties require explicit additional business-role assignments. Remove excess
-- old grants; do not restore permissions previously revoked by the DBA.
DELETE FROM sasms.role_permissions rp USING (VALUES
 ('ADMIN',ARRAY['iam.manage','audit.read']),
 ('CATALOG_MANAGER',ARRAY['catalog.read','catalog.write']),
 ('WAREHOUSE_STAFF',ARRAY['catalog.read','inventory.read','inventory.receive','sales.read']),
 ('WAREHOUSE',ARRAY['catalog.read','inventory.read','inventory.receive','sales.read']),
 ('CASHIER',ARRAY['catalog.read','inventory.read','sales.read','sales.checkout','payments.read','ai.use']),
 ('SALES_STAFF',ARRAY['catalog.read','inventory.read','sales.read','ai.use']),
 ('SALES',ARRAY['catalog.read','inventory.read','sales.read','ai.use']),
 ('DISPATCHER',ARRAY['catalog.read','inventory.read','sales.read','logistics.manage']),
 ('TECHNICIAN',ARRAY['catalog.read','inventory.read','sales.read','logistics.execute','warranty.manage']),
 ('CUSTOMER_SERVICE',ARRAY['catalog.read','inventory.read','sales.read','payments.read','warranty.manage','ai.use']),
 ('STORE_MANAGER',ARRAY['catalog.read','inventory.read','inventory.adjust','sales.read','sales.approve','payments.read','payments.refund','warranty.manage','reports.read']),
 ('ANALYST',ARRAY[]::text[])
) allowed(role_code,permissions)
WHERE rp.role_code=allowed.role_code AND NOT rp.permission_code=ANY(allowed.permissions);
COMMENT ON COLUMN sasms.roles.is_legacy IS 'WAREHOUSE/SALES are compatibility aliases; ANALYST is retained without automatic permissions because it is outside D34 canonical roles. Existing assignments are not rewritten.';
