# SASMS Sprint 4 - TV4 Implementation Report

**Người thực hiện:** Vũ Duy Thái (TV4 - Fullstack)

**Nhánh:** `feat/tv4-s4-integration`

**Base:** `origin/main` tại `77d5c310683d77eff2700f734b360c63e1942130`

**Ngày kiểm chứng:** 21/11/2026 (Asia/Ho_Chi_Minh)

## 1. Phạm vi và trạng thái

Không tìm thấy tài liệu phân công Sprint 4 mới hơn hồ sơ refinement S3-14. Hồ sơ này ghi các US-12 đến US-16 chưa đạt đầy đủ Definition of Ready. Vì vậy source chỉ triển khai các phần đọc dữ liệu có căn cứ, để feature flag mặc định OFF và không tự chốt hợp đồng HTTP hoặc nghiệp vụ trả hàng.

| Hạng mục | Trạng thái | Kết quả thực tế | Phần còn thiếu |
|---|---|---|---|
| US-12 - Lịch sử thiết bị | PARTIAL | Đã có API/UI timeline read-only, kiểm quyền tại DB theo store, giới hạn dữ liệu và test độc lập | Hợp đồng HTTP cần Huy/An duyệt; luồng yêu cầu/phê duyệt trả hàng và chính sách đổi trả chưa triển khai |
| US-13 - Dashboard BI | PARTIAL | Đã có tổng doanh thu đơn PAID, số đơn, tồn AVAILABLE/IN_STOCK, biến động kho và top sản phẩm theo store | Net revenue sau refund, xu hướng, AI acceptance rate và định nghĩa thời gian báo cáo còn thiếu hợp đồng/dữ liệu |
| US-14 - E2E | PARTIAL | Regression backend/frontend PASS | PostgreSQL Sprint 4, IdP/browser E2E và bằng chứng thiết bị thật NOT RUN |
| US-15 - Performance/Security | PARTIAL | Unit test quyền, concealment, validation; dependency audit đã chạy | Load/performance, DAST và concurrency trên môi trường staging NOT RUN |
| US-16 - Documentation/Handover | PARTIAL | Báo cáo, lệnh chạy, giới hạn bằng chứng và checklist bàn giao đã lập | Training, QA/PO acceptance và nghiệm thu chưa thực hiện |

Không hạng mục nào trong báo cáo này được ghi là đã nghiệm thu.

## 2. Source đã triển khai

### 2.1 Device History

- Endpoint đề xuất: `GET /api/v1/devices/{deviceId}/history?limit={n}&offset={n}`.
- Controller lấy actor nội bộ từ trusted principal, không nhận actor từ request.
- Header thiết bị chỉ được trả khi actor có `inventory.read` tại store hiện hành.
- Timeline hợp nhất dữ liệu có sẵn từ inventory movements, returns, deliveries và warranties. Mỗi nhánh truy vấn có điều kiện permission/store phía PostgreSQL.
- Không join customer, không trả payment/PII, không ghi business event khi đọc.
- Không có API mutation trả hàng.
- Feature flag: `sasms.features.device-history-api.enabled=false`.

### 2.2 Store Dashboard

- Endpoint đề xuất: `GET /api/v1/reports/store-summary?store_id={uuid}&from={instant}&to={instant}`.
- Yêu cầu actor tin cậy và permission `reports.read` đúng store.
- Truy vấn server-side theo store: gross revenue của đơn PAID, số đơn PAID, số thiết bị khả dụng, số movement RECEIVE/SALE/RETURN và top 5 sản phẩm.
- Trường doanh thu được gọi đúng là **gross paid revenue**, không tự nhận là net revenue khi chưa có hợp đồng thời điểm refund thành công.
- AI acceptance trả `null` với trạng thái `N/A_NO_ACCEPTANCE_EVENTS`; không suy diễn từ recommendation count.
- Khoảng ngày trên UI được đổi thành khoảng nửa mở `[from, to)` theo Asia/Ho_Chi_Minh.
- Feature flag: `sasms.features.store-dashboard-api.enabled=false`.

### 2.3 Frontend

- Màn hình lịch sử thiết bị có loading, empty, forbidden/not-found, API error và hủy request khi auth thay đổi.
- Dashboard không dựng số liệu demo; yêu cầu access token và store hợp lệ, hiển thị N/A khi chưa có dữ liệu AI được duyệt.
- Hai màn hình được nối vào shell hiện tại nhưng backend API vẫn fail-closed do feature flags mặc định OFF.

## 3. Bằng chứng kiểm thử

### Backend

Lệnh:

```powershell
cd backend
.\mvnw.cmd test
```

Kết quả **PASS** ngày 21/11/2026:

- Tests run: 272
- Failures: 0
- Errors: 0
- Skipped: 16
- BUILD SUCCESS

Các test mới kiểm tra SQL scope/permission, không join customer, pagination/order, validation, concealment, trusted actor, denial khi thiếu quyền và exception propagation.

### Frontend

Lệnh:

```powershell
cd frontend
npm ci
npm test
npm run build
npm audit --json
```

Kết quả:

- `npm ci`: **PASS**, 92 packages.
- `npm test`: **PASS**, 59/59; 0 fail; 0 skip.
- `npm run build`: **PASS**, 339 modules.
- Build warning: bundle khoảng 666.58 kB, vượt ngưỡng cảnh báo 500 kB; không làm build thất bại.
- `npm audit`: **WARNING/OPEN**, 1 high và 1 moderate trong toolchain Vite/esbuild. Bản sửa tự động đề xuất Vite 8 là major upgrade nên chưa áp dụng trong Sprint 4.

### PostgreSQL, E2E và performance

Kiểm tra Docker:

```powershell
docker version --format "Server={{.Server.Version}}"
docker ps
```

Kết quả ngày 21/11/2026: Docker Desktop Linux engine không khả dụng (`dockerDesktopLinuxEngine` pipe không tồn tại). Vì không xác minh được DB cô lập nên:

- PostgreSQL integration: **NOT RUN**.
- Browser/IdP E2E: **NOT RUN**.
- Concurrency/rollback trên DB: **NOT RUN**.
- Performance/load theo NFR: **NOT RUN**.

Không dùng kết quả Sprint 1/2/3 để tuyên bố Sprint 4 PASS.

## 4. Ma trận Requirement - Code - Test - Evidence

| Requirement | Code chính | Test | Evidence |
|---|---|---|---|
| Device history store-scoped, read-only | `devicehistory/controller`, `service`, `data` | Controller, service, JDBC unit tests | Backend 272 tests PASS |
| Không lộ customer/payment PII | SQL chỉ chọn device/product/event; test cấm customer join | JDBC unit test | PASS ở mức source/query contract; DB integration NOT RUN |
| Dashboard đúng store và permission | `reporting/controller`, `service`, `data` | Controller/service/JDBC tests | Backend regression PASS |
| AI denominator bằng 0 là N/A | `StoreDashboardService`, DTO/UI | Service/API client tests | Unit/frontend PASS; sự kiện acceptance thật chưa có |
| Responsive UI và trạng thái lỗi | `DeviceHistoryPage`, `StoreDashboardPage`, CSS | API client tests, production build | 59 tests PASS, build PASS; visual QA NOT RUN |
| Fail-closed khi contract chưa duyệt | Hai feature flags mặc định OFF | Context regression | Backend regression PASS |

## 5. Cách chạy và demo có kiểm soát

### Chạy source mặc định

```powershell
cd backend
.\mvnw.cmd spring-boot:run

cd ..\frontend
npm ci
npm run dev
```

Ở cấu hình mặc định, hai API Sprint 4 không được đăng ký. Đây là hành vi mong đợi.

### Demo sau khi hợp đồng được duyệt

Chỉ bật trong môi trường test cô lập, có PostgreSQL schema/runtime role và JWT thật:

```text
sasms.features.device-history-api.enabled=true
sasms.features.store-dashboard-api.enabled=true
```

Demo cần kiểm tra tối thiểu: user active, user inactive, đúng store, cross-store, thiết bị không tồn tại, timeline rỗng, dashboard khoảng ngày không có dữ liệu, AI N/A và token hết hạn. Không dùng token hoặc UUID hardcode trong source.

## 6. Quyết định và phụ thuộc còn mở

| Người/nhóm | Cần xác nhận |
|---|---|
| Huy / BA | Hợp đồng HTTP chính thức; policy và trạng thái return; định nghĩa net revenue/refund; time window/timezone; AI displayed/accepted denominator; acceptance criteria |
| Kiên / DB | Query/index/ACL chính thức; nguồn sự kiện lịch sử; successful-refund timestamp; AI acceptance linkage; migration Sprint 4. Không sửa V001-V007 trong nhánh này |
| Phát / QA | PostgreSQL integration, cross-store/PII, browser E2E, visual/mobile, concurrency/rollback, performance và security evidence |
| An / PM-Architect | Chốt DoR, feature flags, API exposure, dependency policy cho Vite 8, staging/IdP/provider và tiêu chí nghiệm thu |

PR #37 của DB và PR #38 của Sprint 3 chưa merge tại thời điểm bắt đầu nhánh. Nhánh Sprint 4 không cherry-pick hoặc sao chép migration từ các PR đó.

## 7. Checklist trước review

- [x] Backend unit/regression PASS.
- [x] Frontend unit tests và production build PASS.
- [x] API mới mặc định OFF.
- [x] Không hardcode token, secret, actor hoặc dữ liệu dashboard.
- [x] Không sửa migration/database.
- [x] Không tự triển khai return mutation, payment, SMS hoặc AI acceptance.
- [ ] PostgreSQL integration trên DB Sprint 4 cô lập.
- [ ] IdP/browser E2E và visual/mobile QA.
- [ ] Performance/concurrency/security staging tests.
- [ ] Huy/Kiên/An duyệt hợp đồng và bật feature flags.
- [ ] QA/PO acceptance.

## 8. Kết luận

Source đủ điều kiện **Draft Review** cho các proposal read-only, security-scoped và fail-closed. Source **chưa đủ điều kiện Acceptance/Production** vì DoR và hợp đồng còn mở, Docker/PostgreSQL/E2E/performance chưa được kiểm chứng, và feature flags vẫn OFF.

---

## 9. Cập nhật Trạng thái Cuối kỳ (Sprint 4 Closeout Update — 21/11/2026)

- **Trạng thái PR:** PR #39 (`feat/tv4-s4-integration`) đã được **MERGE** vào `main` tại commit `7ac73ec`. PR #43 (DB Sprint 4 V008, Runbooks D101, Cutover D91 của Kiên) đã được **MERGE** vào `main` tại commit `6ff29ea`.
- **Chỉ số kiểm thử tự động toàn diện:**
  - Backend Spring Boot: **322 tests PASS** (0 failures, 0 errors, 17 skipped, BUILD SUCCESS).
  - Frontend React Vitest: **61/61 tests PASS** (0 failures, 0 skipped).
  - Production Build Vite: **Thành công** (335 modules transformed trong ~2.88s).
- **Hoàn thành nhiệm vụ S4-06 (Sửa lỗi P1/P2):**
  - Hoàn tất mã nguồn và unit tests cho BUG-D71-01 (IMEI Luhn MOD-10 checksum - `DeviceIdentifierValidator.java`), BUG-D73-01 (AI telemetry tracking events SHOWN/ACCEPTED - `AiEventRequest.java`), BUG-D74-03 (QR Warranty dates mapping - `QrLookupResponse.java`).
  - Ban hành tài liệu chi tiết: `Sprint 4/Fullstack/TV4/S4-06_P1P2_CLOSEOUT_MATRIX.md`. Trạng thái QA là `BLOCKED_PENDING_QA_EVIDENCE` chờ QA Phát re-test ký đóng trong D80.
- **Hoàn thành nhiệm vụ S4-16 (Quy trình Hypercare & SLA D100):**
  - Ban hành tài liệu chi tiết: `Sprint 4/Fullstack/TV4/D100_HYPERCARE_SLA_TV4.md` (17 mục toàn diện, nhãn `PROPOSED - Chờ PM An duyệt`).
  - Khẳng định trung thực trạng thái Go-Live: `PLAN_READY / EXECUTION_PENDING` (chưa Go-Live thật do D78 chưa đạt E2E).
- **Hồ sơ bàn giao kỹ thuật cuối kỳ:**
  - `Sprint 4/Fullstack/TV4/TV4_WORKLOG_VERIFIED.md` (Nhật ký 32 commits, đối soát giờ thực tế và khoảng trống thời gian).
  - `Sprint 4/Fullstack/TV4/TV4_FINAL_HANDOFF.md` (Báo cáo tổng kết 12 phần, ma trận 16 nhiệm vụ, 10 câu hỏi phản biện kỹ thuật).
  - `Sprint 4/Fullstack/TV4/TV4_EXTERNAL_DEPENDENCIES.md` (Danh mục phụ thuộc bên ngoài phân theo Phát, An, Kiên, Huy, Vũ).
