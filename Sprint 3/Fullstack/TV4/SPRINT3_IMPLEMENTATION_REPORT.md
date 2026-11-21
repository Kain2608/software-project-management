# SPRINT 3 IMPLEMENTATION REPORT — TV4

**Người thực hiện:** Vũ Duy Thái

**Nhánh:** `feat/tv4-s3-online-logistics`

**Baseline:** `origin/main` tại `77d5c310683d77eff2700f734b360c63e1942130`

**Ngày kiểm tra:** 21/11/2026 (Asia/Saigon)

## 1. Phạm vi chính thức

| Task | Phạm vi | Trạng thái | Bằng chứng |
|---|---|---|---|
| S3-05 | Web đặt hàng online | PARTIAL / BLOCKED | UI Catalog–Stock–Cart đã có và fail-closed; chưa gửi đơn hoặc thanh toán |
| S3-06 | Phân công kỹ thuật viên, ghi kết quả giao/lắp | IMPLEMENTED, FEATURE OFF | Controller/Service/JDBC gọi đúng V006; unit test PASS |
| S3-07 | Hỗ trợ QR bảo hành | SUPPORT / READY FOR DB MERGE | QR hiện có; `activate_warranty` thuộc V006 do Kiên/Phát phụ trách, không tạo API ngoài hợp đồng |
| S3-08 | Hỗ trợ SMS adapter | SUPPORT / BLOCKED | Không gọi provider; V007/worker thuộc PR #37, mock không được coi là gửi thật |
| S3-14 | Refinement | PARTIAL | Ghi rõ contract gaps, feature flag và evidence; chờ BA/Architect/QA |

## 2. Đối chiếu nguồn

- D34 FR-10 yêu cầu giỏ hàng hợp lệ, liên hệ/địa chỉ, hold 30 phút, thanh toán sandbox được xác minh và không tạo đơn trùng.
- D34 FR-11 cùng D41 yêu cầu chỉ đơn `PAID` được lập lịch, kỹ thuật viên đúng store và khoảng đệm 30 phút.
- D34 FR-12 cùng D42 tách trạng thái bảo hành khỏi SMS; lỗi SMS không được xóa bảo hành.
- D52 hiện là OpenAPI DRAFT Inventory/POS/AI, chưa có HTTP contract Online/Logistics.
- PR #37 (`DB`, head `c25346b`) đang OPEN và chứa V006/V007. Nhánh này không cherry-pick, không sửa migration.

## 3. S3-05 — Online order

### Đã triển khai

- Màn hình responsive tra cứu Catalog và tồn kho theo đúng `warehouseId` bằng API đang có.
- Giỏ hàng thêm/xóa/đổi số lượng và tính tạm tính VND bằng model đã được kiểm thử từ POS.
- Khi `accessToken`, `storeId` hoặc `warehouseId` thay đổi: hủy request cũ, tăng generation và xóa kết quả/giỏ cũ.
- Checkout luôn disabled. Không hardcode JWT, store, warehouse, customer, sản phẩm hoặc kết quả thanh toán.

### Chưa thể triển khai an toàn

1. Chưa có mapping JWT khách hàng → `customers.id`; `users.auth_subject` hiện dành cho nhân viên.
2. Hàm DB hiện yêu cầu actor có `sales.checkout`; không được cấp quyền này cho khách hàng.
3. D52 chưa định nghĩa Online Order HTTP request/response/error/idempotency.
4. Chưa chốt payment provider, callback verification và ownership khi đọc đơn.

Do đó việc tạo đơn `ONLINE`, reserve 30 phút, callback PAID và theo dõi đơn là **BLOCKED**, không được mô phỏng thành production.

## 4. S3-06 — Logistics API đề xuất

Feature flag `sasms.features.logistics-api.enabled=false` mặc định.

| Endpoint đề xuất | DB operation | Bảo mật |
|---|---|---|
| `POST /api/v1/logistics/deliveries` | `sasms.schedule_delivery(uuid,uuid,timestamptz,timestamptz,text,uuid)` | actor chỉ lấy từ verified principal; V006 kiểm `logistics.manage`, store, PAID, technician, conflict |
| `GET /api/v1/logistics/deliveries/{id}` | SELECT tối thiểu delivery/assignment | SQL kiểm `logistics.manage` hoặc đúng technician có `logistics.execute`; không trả PII/payment |
| `POST /api/v1/logistics/deliveries/{id}/result` | `sasms.record_delivery_result(uuid,text,text,uuid)` | actor chỉ lấy từ verified principal; chỉ `COMPLETED`/`FAILED` và evidence 1..2000 |

Implementation không INSERT/UPDATE logistics trực tiếp, không retry, không nuốt lỗi và không tự gửi SMS. DB V006 vẫn là authority cuối cho trạng thái, buffer 30 phút, scope và atomicity.

## 5. Kết quả kiểm thử mới

| Kiểm tra | Kết quả thực tế |
|---|---|
| Backend `backend\\mvnw.cmd test` | **PASS** — 268 tests, 0 failure, 0 error, 16 skipped, BUILD SUCCESS |
| Logistics unit/HTTP tests | **PASS** — 10 tests mới (JDBC contract, validation, trusted actor, safe HTTP error, exception/no retry) |
| Frontend `npm ci` | **PASS** — lockfile được dùng, 92 packages |
| Frontend `npm test` | **PASS** — 56/56, 0 fail/skip |
| Frontend `npm run build` | **PASS** — 336 modules; cảnh báo chunk 664.28 kB không chặn build |
| `npm audit --json` | **FAIL (dependency advisory)** — 1 high, 1 moderate; Vite/esbuild dev tooling; bản sửa npm đề xuất là Vite 8 major nên không tự nâng |
| PostgreSQL V006/V007 integration | **NOT RUN** — PR #37 vẫn OPEN, migration chưa có trên `origin/main`; Docker daemon không chạy (`dockerDesktopLinuxEngine` pipe không tồn tại), nên không thể xác minh DB cô lập và không dùng DB chung/kết quả Sprint trước |
| JWT/IdP customer E2E | **NOT TESTED** — chưa có identity contract/provider test |
| Payment/SMS provider E2E | **NOT TESTED** — provider/callback chưa chốt |

## 6. Cách chạy

Backend unit/regression:

```powershell
cd backend
.\mvnw.cmd test
```

Frontend:

```powershell
cd frontend
npm ci
npm test
npm run build
```

Sau khi PR #37 merge và có DB PostgreSQL 16 dùng một lần, owner-controlled: áp migration theo pipeline DB, dùng runtime role `sasms_app`, bật riêng `sasms.features.logistics-api.enabled=true`, rồi chạy integration bằng fixture có namespace riêng. Không chạy validator ghi dữ liệu trên DB chung.

## 7. Quyết định/phối hợp còn thiếu

| Chủ thể | Cần xác nhận |
|---|---|
| Huy / BA | Online + Logistics OpenAPI, error/status contract, customer ownership, địa chỉ/liên hệ, quy tắc hủy/lập lại lịch |
| Kiên / DB | Merge PR #37; xác nhận V006/V007 signature và runtime grants; cung cấp fixture integration cô lập |
| An / Architect/PM | Mô hình customer identity/service actor; feature flag rollout; dependency Vite major; payment/SMS provider |
| Vũ | SMS provider adapter/callback/secret management và nghĩa của ACCEPTED/UNKNOWN |
| Phát | Luồng kích hoạt bảo hành QR, quyền `warranty.manage` và UI/API ownership |
| QA/PO | Mobile/browser visual QA, negative authorization/cross-store, nghiệm thu theo từng AC |

## 8. Checklist bàn giao

- [x] Không sửa migration/schema/database.
- [x] Không hardcode token, secret, customer/store/warehouse.
- [x] Không mở endpoint logistics mặc định.
- [x] Không gán mock SMS/payment là thành công thật.
- [x] Backend/frontend regression và production build PASS.
- [ ] Merge và kiểm thử V006/V007 trên DB cô lập.
- [ ] Duyệt D52 cho Online/Logistics và mô hình customer identity.
- [ ] Payment/SMS/JWT customer E2E.
- [ ] QA/PO acceptance.

## 9. Kết luận

Source hiện **READY FOR DRAFT REVIEW** cho phần độc lập và contract proposal. Sprint 3 **chưa ACCEPTANCE**: S3-05 production checkout, integration V006/V007, SMS/payment/JWT customer và QA/PO còn BLOCKED/NOT TESTED.
