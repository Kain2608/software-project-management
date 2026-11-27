# SASMS — QUYẾT ĐỊNH KIẾN TRÚC SPRINT 0–3, CHÍNH SÁCH FEATURE FLAG STAGING SPRINT 4, PHÊ DUYỆT D100 HYPERCARE SLA VÀ KẾT LUẬN ĐỐI CHIẾU HỒ SƠ BÀN GIAO TV4

| Thuộc tính | Nội dung |
|---|---|
| **Dự án** | Hệ thống Quản trị Bán lẻ Điện máy Thông minh SASMS (INT1450) |
| **Mã hồ sơ quản trị** | `PM-ARCH-DECISION-S0-S4-TV4-CLOSEOUT` |
| **Chủ trì quyết định** | **Trịnh Quốc An** — Project Manager / System Architect (TV1) |
| **Phối hợp chuyên môn** | **Trần Trung Kiên** (TV2 — Architect / DBA), **Phạm Đại Phát** (TV3 — QA / Documentation Lead), **Vũ Duy Thái** (TV4 — Fullstack Lead), **Nguyễn Quang Huy** (TV5 — BA Lead / Product Owner), **Nguyễn Tiến Vũ** (TV6 — Data / AI Lead) |
| **Ngày ban hành** | 23/11/2026 (Asia/Ho_Chi_Minh) |
| **Phạm vi xử lý** | (1) Chốt quyết định kiến trúc còn mở Sprint 0–3 (Customer Identity, JWT/OIDC, RBAC & Store-Scope); (2) Điều kiện bật riêng Feature Flag trên Staging Sprint 4; (3) Review & Phê duyệt kèm yêu cầu hiệu chỉnh cụ thể cho D100 Hypercare SLA; (4) Đối chiếu hồ sơ bàn giao TV4 với Backlog, PR, Test Logs, Quality Gates và chốt trạng thái nghiệm thu cùng Huy (PO) & Phát (QA). |
| **Tuyên bố ranh giới hiệu lực** | **VIỆC BAN HÀNH CÁC QUYẾT ĐỊNH KỸ THUẬT, DUYỆT D100 VÀ KẾT LUẬN HỒ SƠ BÀN GIAO TV4 TRONG VĂN BẢN NÀY KHÔNG ĐỒNG NGHĨA TỰ ĐỘNG PHÊ DUYỆT GO-LIVE PRODUCTION.** Quyết định Go-Live Production vẫn giữ trạng thái **`NO-GO / EXECUTION_PENDING`** cho tới khi hoàn tất kiểm thử E2E trên Staging (D78 đạt $\ge 95\%$, Zero P1/P2 OPEN trên D80) và UAT (D83) được ký nghiệm thu thực tế. |

---

## PHẦN 1. CHỐT CÁC QUYẾT ĐỊNH KIẾN TRÚC CÒN MỞ SPRINT 0 – SPRINT 3

Căn cứ các điểm mở trong [D50](../../Sprint%200/DB/D50-kien-truc-he-thong.md), [D54](../../Sprint%200/DB/D54-bao-mat-phan-quyen.md), [D81 Sprint 3](../../Sprint%203/DB/D81-bao-cao-ra-soat-bao-mat.md), [SPRINT3_IMPLEMENTATION_REPORT.md](../../Sprint%203/Fullstack/TV4/SPRINT3_IMPLEMENTATION_REPORT.md) và [TV4_EXTERNAL_DEPENDENCIES.md](../Fullstack/TV4/TV4_EXTERNAL_DEPENDENCIES.md), PM / System Architect chính thức chốt các quyết định sau để tháo gỡ toàn bộ điểm chặn về hợp đồng kiến trúc:

### 1.1. Quyết định `ARCH-DEC-S03-01`: Kiến trúc Định danh Khách hàng (Customer Identity Mapping) và Tách biệt với Nhân viên Nội bộ

1. **Nguyên tắc tách biệt miền định danh (Identity Domain Separation):**
   - **`sasms.users` (`users.auth_subject`)**: Chỉ dành riêng cho **nhân viên nội bộ** của chuỗi siêu thị (gán với 9 vai trò nghiệp vụ canonical trong `user_roles`).
   - **`sasms.customers` (`customers.id`)**: Dành cho **khách hàng mua sắm (POS / Online)**. Khách hàng trực tuyến tuyệt đối không được tạo bản ghi trong `sasms.users` và không được gán bất kỳ quyền nhân viên nội bộ nào.
2. **Cơ chế ánh xạ Customer Identity cho kênh Online (`S3-05` / `BUG-D74-01`):**
   - **Tách Audience / Principal:** Token của khách hàng từ Customer IdP (OIDC Customer Realm / Cognito / Firebase) phải có `aud = sasms-storefront-api` (hoặc claim `principal_type = CUSTOMER`), tách biệt hoàn toàn với `aud = sasms-internal-api` của nhân viên nội bộ.
   - **Ánh xạ sang `customers.id`:** Backend BFF / Online Order Service thực hiện giải quyết định danh khách hàng qua bảng/cơ chế liên kết danh tính ngoài (`customer_issuer + customer_sub` $\rightarrow$ `customers.id`, hoặc xác thực OTP số điện thoại đã xác minh trong phiên đặt hàng) thay vì đi qua `JdbcActorIdentityResolver` của nhân viên.
   - **Cấm cấp quyền `sales.checkout` cho khách hàng:** Khách hàng mua online **tuyệt đối không** được cấp quyền `sales.checkout` (quyền này chỉ thuộc `CASHIER` tại quầy POS).
   - **Cơ chế gọi DB cho Online Checkout:** Luồng giữ chỗ tồn kho 30 phút (`channel = 'ONLINE'`) và tạo đơn online phải được thực thi thông qua **Controlled Service Actor / Dedicated DB Function cho kênh Online** (kiểm tra quyền sở hữu `sales_orders.customer_id = authenticated_customer_id`, giới hạn số lượng giữ chỗ mỗi khách hàng và rate-limit theo IP/Account), hoặc chỉ mở khi BA Huy hoàn tất hợp đồng OpenAPI Online D52 và DBA Kiên ban hành hàm DB riêng cho storefront.
3. **Quyết định vận hành hiện tại cho `S3-05` (Online Checkout):**
   - Trong phạm vi Release Candidate hiện tại, giữ nguyên thiết kế **Fail-Closed (`checkout disabled`, `sasms.features.online-order.enabled=false`)** đối với thao tác thanh toán đặt hàng của khách vãng lai trên nhánh `main` cho tới khi: (a) hoàn tất tích hợp Customer IdP riêng và (b) có cổng thanh toán Sandbox xác minh chữ ký webhook.
   - Giao diện duyệt Catalog và Giỏ hàng Online theo kho (`S3-02`) được công nhận hoàn thành đúng chuẩn bảo mật.

### 1.2. Quyết định `ARCH-DEC-S03-02`: Chính sách Xác thực JWT / OIDC Toàn Hệ thống (Client & Resource Server)

1. **Chuẩn xác thực tại Backend (`SasmsJwtProperties` & `SasmsJwtDecoderFactory`):**
   - Thuật toán chữ ký bắt buộc: **`RS256`** thông qua JWKS URI của IdP tin cậy.
   - Ràng buộc giao thức JWKS & Issuer (`SEC-S3-01` — Đã khắc phục): Bắt buộc dùng **`https://`**, có host hợp lệ, cấm tuyệt đối `userinfo`, `query` hoặc `fragment` trong URI. Giao thức `http://` chỉ được phép duy nhất cho địa chỉ loopback (`localhost`, `127.0.0.1`, `[::1]`) trong môi trường kiểm thử cục bộ.
   - Ràng buộc Claims bắt buộc (`SEC-S3-02` — Đã khắc phục): Mọi JWT phải có đầy đủ `iss` (khớp cấu hình `SASMS_JWT_ISSUER`), `aud` (chứa `SASMS_JWT_AUDIENCE`), `sub` (ánh xạ 1-1 sang `sasms.users.auth_subject` đang có `is_active = true`), và **`exp` bắt buộc không được rỗng (`setAllowEmptyExpiryClaim(false)`)**. Clock skew tối đa 60 giây.
   - Cơ chế khởi động Fail-Closed: Nếu cả 3 biến `SASMS_JWT_ISSUER`, `SASMS_JWT_AUDIENCE`, `SASMS_JWT_JWKS_URI` đều trống, hệ thống chỉ mở 4 endpoint trạng thái (`GET /api/health`, `/api/pos/status`, `/api/serial-imei/status`, `/api/v1/ai/status`) và trả `401 Unauthorized` cho toàn bộ API nghiệp vụ. Nếu chỉ cấu hình một phần (thiếu 1 trong 3 biến), ứng dụng từ chối khởi động (`IllegalStateException`).
2. **Chuẩn xác thực tại Frontend (`frontend/src/app/authContext.js`):**
   - **Zero-Trust Client Token:** Chỉ chấp nhận session lưu trong bộ nhớ RAM (`in-memory`) từ OIDC/PKCE adapter; **nghiêm cấm** đọc/ghi JWT từ URL query/hash (`?token=`, `?access_token=`, `?jwt=...`) hoặc từ `localStorage`/`sessionStorage`.
   - **Cấm Token giả lập:** Từ chối ngay ở tầng client mọi chuỗi token khớp mẫu `mock-jwt`, `demo-token`, `fake-token`, `test-token`, `dummy-token`.
   - **Không tự tuyên bố xác thực ở Client:** `resolveAuthSession()` luôn gán `isBackendVerified: false`. Khi đổi `accessToken`, `storeId` hoặc `warehouseId`, UI bắt buộc hủy các HTTP request đang chạy bằng `AbortController`, tăng sequence guard và xóa sạch dữ liệu phiên cũ (giỏ hàng POS, giỏ hàng Online, biên nhận, báo cáo Dashboard).

### 1.3. Quyết định `ARCH-DEC-S03-03`: Chính sách Phân quyền RBAC 9 Vai trò Canonical, Store-Scope và DB Runtime Role

1. **Thống nhất 9 Vai trò Nghiệp vụ Canonical (Đóng `DEC-01`, `ARC-04`, `M1-03`, `M2-DB03`):**
   - Hệ thống áp dụng duy nhất **9 vai trò nghiệp vụ canonical** theo D34 FRS và Migration `V005__frs_business_roles.sql`:
     1. `ADMIN`: Quản trị tài khoản, gán quyền, tra cứu audit IAM. **Không** có quyền tự duyệt hoàn tiền/đổi trả và **không** tự động có quyền `sales.checkout` nếu không được gán vai trò nghiệp vụ tương ứng.
     2. `CATALOG_MANAGER`: Quản lý danh mục sản phẩm, SKU, giá niêm yết (`list_price`), cấu hình profile định danh Serial/IMEI.
     3. `WAREHOUSE_STAFF`: Nhập kho theo lô (`inventory.manage`), tra cứu tồn kho và quét QR thiết bị (`inventory.read`) trong phạm vi kho/siêu thị được giao.
     4. `CASHIER`: Bán hàng POS tại quầy, khóa chọn Serial, tạo đơn DRAFT, giữ chỗ RESERVE, thu tiền mặt CASH (`sales.checkout`), xem/in hóa đơn (`sales.read`) và sử dụng gợi ý AI (`ai.use`).
     5. `SALES_STAFF`: Tư vấn bán hàng, tra cứu danh mục/tồn kho và gợi ý AI; không được xác nhận thu tiền (`PAID`).
     6. `DISPATCHER`: Lập lịch và điều phối giao hàng/lắp đặt cho đơn `PAID` (`logistics.manage`) đúng cửa hàng.
     7. `TECHNICIAN`: Xem lịch được phân công, cập nhật kết quả giao/lắp (`logistics.execute` — `COMPLETED`/`FAILED` kèm bằng chứng) và kích hoạt bảo hành đủ điều kiện.
     8. `CUSTOMER_SERVICE`: Tra cứu lịch sử thiết bị/bảo hành, tiếp nhận yêu cầu hỗ trợ/đổi trả; không tự duyệt hoàn tiền.
     9. `STORE_MANAGER`: Quản lý siêu thị được giao, xem báo cáo Dashboard BI (`reports.read`), duyệt ngoại lệ/đổi trả trong phạm vi cửa hàng.
   - Các mã cũ (`WAREHOUSE`, `SALES`, `ANALYST`) chỉ giữ trong DB với cờ `is_legacy` để bảo toàn lịch sử migration; mọi tài liệu, test case và cấu hình mới bắt buộc dùng 9 mã canonical ở trên.
2. **Thực thi Store-Scope và Che giấu Dữ liệu (Data Concealment):**
   - Mọi thao tác đọc/ghi theo cửa hàng phải kiểm tra `sasms.has_permission(actor_id, permission_code, store_id)` ngay trong câu truy vấn SQL/hàm PostgreSQL.
   - Không nhận `actor_id` từ header (`X-Actor-Id`), query hay body của client; chỉ lấy từ `TrustedActorAuthenticationToken` đã xác thực qua JWT.
   - Chính sách mã lỗi HTTP đối với truy cập trái phép chéo cửa hàng (Cross-store):
     - Các API tra cứu theo định danh đơn lẻ (`GET /api/v1/pos/orders/{orderId}/receipt`, `POST /api/v1/devices/qr-lookup`, `GET /api/v1/devices/{deviceId}/history`) trả về **`404 Not Found`** khi đối tượng thuộc cửa hàng khác để chống dò quét định danh (ID enumeration).
     - API báo cáo tổng hợp nhận trực tiếp `store_id` (`GET /api/v1/reports/store-summary?store_id=...`) trả về **`403 Forbidden`** khi người dùng không có quyền `reports.read` tại `store_id` đó.
3. **Phân tách Tài khoản Kết nối CSDL (`TV4-S0-ARCH-03` — Đóng hoàn toàn):**
   - `sasms_owner` chỉ dùng cho pipeline khởi tạo schema và chạy migration (`V001` $\rightarrow$ `V008`).
   - Backend runtime bắt buộc kết nối bằng tài khoản `LOGIN` riêng (ví dụ `tv4_staging_app_user` trên Staging do script `scripts/provision_staging_e2e.sql` cấp phát), kế thừa group role `sasms_app` (`NOSUPERUSER`, không có quyền `DDL` hay `TRUNCATE`).

### 1.4. Quyết định `ARCH-DEC-S03-04`: Chốt các Hợp đồng Nghiệp vụ & Tích hợp Sprint 0–3 còn lại

| Mã quyết định | Nội dung chốt chính thức | Căn cứ kỹ thuật trong Repo |
|---|---|---|
| **Định danh Serial/IMEI & QR (`DEC-03`, `DEC-04`, `DEC-07`)** | - Serial lưu canonical `upper(btrim(serial))` (1–100 ký tự), giữ raw payload trong batch.<br>- IMEI-15 bắt buộc kiểm tra thuật toán **Luhn MOD-10** ở cả Backend (`DeviceIdentifierValidator`) và PostgreSQL (`sasms.valid_imei` trong `V003`).<br>- QR thiết bị dùng định dạng chuẩn `SASMS:DEVICE:<qr_token>` (UUID), không chứa PII. | `V003__scoped_device_identifiers.sql`, `DeviceIdentifierValidator.java` |
| **Mô hình POS Order-First & Lease (`DEC-05`, `DEC-08`)** | - Chuẩn hóa luồng POS: Tạo đơn `DRAFT` (`POST /api/v1/pos/orders`) $\rightarrow$ Chọn/khóa Serial (`POST .../devices`) $\rightarrow$ Giữ chỗ nguyên tử `RESERVE` (`POST .../reserve`) $\rightarrow$ Thu tiền mặt `CASH` & hoàn tất đơn `PAID` (`POST .../payments/cash`).<br>- Thời hạn giữ chỗ (Lease TTL): POS 10–15 phút theo hàm DB `V004`, Online 30 phút; trạng thái `PENDING` thanh toán không tự động hủy mà phải qua đối soát.<br>- VietQR trong phạm vi hiện tại chỉ là phương thức hiển thị hướng dẫn chuyển khoản; chưa mở webhook tự động gạch nợ khi chưa có hợp đồng ngân hàng sandbox. | `V004__pos_lease_lifecycle.sql`, `PosOrderService.java`, `PosPaymentService.java` |
| **Giá, Tiền tệ & Hóa đơn 80mm (`DEC-09`, `ARC-03`)** | - Mọi đơn giá lấy từ `products.list_price` trong CSDL tại thời điểm tạo/reserve đơn, tính bằng số nguyên **VND** (không nhân/chia 100).<br>- Hóa đơn POS sử dụng chuẩn CSS in trình duyệt khổ cuộn nhiệt 80mm (`pos-receipt.css`, `@media print`), che Serial chỉ hiển thị `****` + 4 ký tự cuối. Việc in qua hộp thoại trình duyệt không làm thay đổi trạng thái đơn `PAID` hoặc trừ kho lần hai. | `pos-receipt.css`, `ReceiptController.java`, `PosPaymentService.java` |
| **Outbox SMS & LLM AI (`S3-08`, `S3-09`, `S4-03`)** | - Kích hoạt bảo hành chỉ ghi sự kiện vào `sasms.outbox_events` trong cùng transaction (`V007`). `SmsOutboxWorker` gửi ngoài transaction; mặc định ở chế độ `DISABLED`/`MOCK` khi chưa cấp secret nhà mạng thật. Trạng thái `MOCK_ACCEPTED` chỉ dùng cho kiểm thử kỹ thuật, không báo cáo là tin nhắn đã tới điện thoại thật.<br>- AI Cross-sell và Spec Summary hoạt động với cơ chế SingleFlight, Cache, Sanitizer, Validator và Fallback Rule Engine (< 1.5s timeout tại UI); chế độ mặc định dùng Mock/Rule-based an toàn, không lộ PII sang LLM. | `V007__sms_outbox.sql`, `SmsOutboxWorker.java`, `AiCrossSellService.java`, `AiSpecSummaryService.java` |

---

## PHẦN 2. SPRINT 4 — QUYẾT ĐỊNH ĐIỀU KIỆN BẬT RIÊNG FEATURE FLAG TRÊN STAGING

Hiện tại trong [`backend/src/main/resources/application.properties`](../../backend/src/main/resources/application.properties) và cấu hình module, toàn bộ các cờ tính năng (Feature Flags) đang đặt mặc định là **`false` (Fail-Closed by Default)** để bảo vệ nhánh `main`.

PM / System Architect ban hành **Quyết định Điều hành `FF-STAGING-POLICY-S4`** quy định rõ danh sách các cờ **ĐƯỢC PHÉP BẬT RIÊNG TRÊN MÔI TRƯỜNG STAGING CÔ LẬP** và các cờ **TIẾP TỤC GIỮ TẮT (`false`)**:

### 2.1. Ma trận Quyết định Bật/Tắt Feature Flags trên Môi trường Staging

| Tên Feature Flag (Property / Env Var) | Mặc định trên `main` | Quyết định trên **Staging E2E** | Điều kiện tiên quyết bắt buộc để bật trên Staging | Điều kiện để mở lên Production (Sau Staging) |
|---|:---:|:---:|---|---|
| `sasms.features.pos-serial-api.enabled`<br>(`SASMS_FEATURES_POS_SERIAL_API_ENABLED`) | `false` | **CHO PHÉP BẬT (`true`)** | 1. Kết nối vào container Staging cô lập (`sasms_staging_e2e_db`, cổng `15435`, đã nạp `V001..V008` và `realtime_stock_and_locking.sql`).<br>2. Dùng tài khoản runtime `tv4_staging_app_user` thuộc nhóm `sasms_app`.<br>3. Đã cấu hình đầy đủ 3 biến `SASMS_JWT_ISSUER`, `SASMS_JWT_AUDIENCE`, `SASMS_JWT_JWKS_URI`. | QA Phát chạy pass bộ test D72 (POS chọn/nhả Serial, 2 phiên tranh chấp trả `409 DEVICE_LOCKED`) và PO Huy ký xác nhận. |
| `sasms.features.pos-order-api.enabled`<br>(`SASMS_FEATURES_POS_ORDER_API_ENABLED`) | `false` | **CHO PHÉP BẬT (`true`)** | Như trên (chung môi trường Staging POS E2E). Đảm bảo header `Idempotency-Key` được kiểm tra đầy đủ. | QA Phát chạy pass luồng tạo đơn `DRAFT` $\rightarrow$ `RESERVE` trên D72/D78 và xác nhận rollback nguyên tử. |
| `sasms.features.pos-cash-payment-api.enabled`<br>(`SASMS_FEATURES_POS_CASH_PAYMENT_API_ENABLED`) | `false` | **CHO PHÉP BẬT (`true`)** | Như trên. Chỉ áp dụng cho thanh toán tiền mặt (`CASH`) tại quầy thu ngân có quyền `sales.checkout`. | QA Phát xác nhận đơn chuyển sang `PAID`, trừ kho `SOLD`, xuất được hóa đơn 80mm kèm QR bảo hành. |
| `sasms.features.logistics-api.enabled`<br>(`SASMS_FEATURES_LOGISTICS_API_ENABLED`) | `false` | **CHO PHÉP BẬT (`true`) (Chỉ phục vụ API E2E)** | DB Staging đã nạp `V006__logistics_and_warranty.sql`; kiểm thử bằng JWT của `DISPATCHER` (`logistics.manage`) và `TECHNICIAN` (`logistics.execute`). | Hoàn thiện thêm giao diện UI điều phối/kỹ thuật viên (`BUG-D74-02`) và QA Phát verify các ca `TC-11-01..TC-11-14`. |
| `sasms.features.device-history-api.enabled`<br>(`SASMS_FEATURES_DEVICE_HISTORY_API_ENABLED`) | `false` | **CHO PHÉP BẬT (`true`)** | DB Staging có đủ `V001..V008`; kiểm thử bằng JWT có quyền `inventory.read` đúng `store_id`. Xác nhận response không chứa PII khách hàng. | QA Phát kiểm thử isolation chéo cửa hàng (trả `404` khi khác store) và PO Huy nghiệm thu US-12 (phần tra cứu lịch sử). |
| `sasms.features.store-dashboard-api.enabled`<br>(`SASMS_FEATURES_STORE_DASHBOARD_API_ENABLED`) | `false` | **CHO PHÉP BẬT (`true`)** | 1. DB Staging đã nạp `V008__sprint4_query_optimization.sql`.<br>2. Chạy script kiểm thử `scripts/run_dashboard_http_e2e.ps1` với JWT của `STORE_MANAGER` (`200 OK` đúng store, `403` khác store) và `CASHIER` (`403`).<br>3. Ghi nhận rõ doanh thu hiển thị là **Gross Paid Revenue**. | QA Phát xác nhận kiểm thử trình duyệt thực tế theo checklist tại `TV4_DASHBOARD_E2E_CLOSEOUT.md` Mục D và PO Huy ký UAT-08. |
| `sasms.features.online-order.enabled`<br>(Luồng Online Checkout khách vãng lai) | `false` | **GIỮ TẮT (`false`)** | **KHÔNG BẬT** cho tới khi hoàn tất hợp đồng Customer IdP mapping (`ARCH-DEC-S03-01`), hàm DB storefront riêng không dùng `sales.checkout`, và cổng thanh toán Sandbox. | Phải có RFC Customer Auth + Payment Webhook được duyệt và vượt qua kiểm thử bảo mật D81. |
| `sasms.sms.mode`<br>(`SASMS_SMS_MODE`) | `DISABLED` | **CHỈ BẬT `MOCK` TRÊN STAGING** | Tuyệt đối không cấu hình `HTTP` gọi nhà mạng thật nếu chưa có tài khoản Sandbox được Sponsor cấp phép và nạp qua biến môi trường ngoài Git. | Có hợp đồng SMS Brandname/Sandbox thật và QA Phát verify `BUG-D74-04`. |

### 2.2. Quy tắc Bắt buộc Khi Vận hành Staging với Feature Flag Bật

1. **Không sửa giá trị mặc định `false` trong `application.properties` trên nhánh `main`:** Việc bật các cờ trên Staging chỉ được truyền qua **biến môi trường của tiến trình chạy Staging** (Environment Variables) hoặc profile cấu hình Staging cô lập.
2. **Bảo mật thông tin xác thực:** Mật khẩu DB staging và JWT test chỉ truyền trong phiên PowerShell/CI runner, không in Authorization header ra log/ảnh chụp màn hình và không commit vào repository.
3. **Cơ chế ngắt khẩn cấp (Kill-Switch):** Nếu bất kỳ API nào vừa bật flag trên Staging phát sinh lỗi bảo mật (lộ dữ liệu chéo cửa hàng) hoặc treo kết nối DB, lập tức đưa biến môi trường của flag đó về `false` và khởi động lại dịch vụ trong vòng < 2 phút.

---

## PHẦN 3. REVIEW TÀI LIỆU D100 HYPERCARE SLA, LỊCH TRỰC, RACI VÀ ĐIỀU KIỆN ROLLBACK

PM / System Architect Trịnh Quốc An đã tiến hành rà soát toàn diện tài liệu [`Sprint 4/Fullstack/TV4/D100_HYPERCARE_SLA_TV4.md`](../Fullstack/TV4/D100_HYPERCARE_SLA_TV4.md) của Vũ Duy Thái (TV4), đối chiếu chéo với [`Sprint 4/DB/D90-ke-hoach-phat-hanh.md`](../DB/D90-ke-hoach-phat-hanh.md), [`Sprint 4/DB/D91-ke-hoach-cutover-va-rollback.md`](../DB/D91-ke-hoach-cutover-va-rollback.md), [`Sprint 4/DB/D101-tai-lieu-van-hanh-sao-luu-va-bao-tri.md`](../DB/D101-tai-lieu-van-hanh-sao-luu-va-bao-tri.md) và biên bản kỹ thuật của DBA Trần Trung Kiên [`Sprint 4/DB/TV4-S4-REVIEW-APPROVAL-KIEN.md`](../DB/TV4-S4-REVIEW-APPROVAL-KIEN.md).

### 3.1. Kết quả Thẩm định Từng Hạng mục Trọng tâm của D100

1. **Về Trạng thái Hiện thực và Cam kết SLA (Mục 2, Mục 4, Mục 5 & Mục 14):**
   - **Đánh giá:** **ĐẠT & TRUNG THỰC**. Tài liệu D100 đã xác định đúng trạng thái thực tế là **`PLAN_READY / EXECUTION_PENDING`** (chưa diễn ra Go-Live thực tế do D78 đang NO-GO và D83 chưa ký).
   - **Khung SLA 3 ngày Hypercare:** Phê duyệt chính thức bảng chỉ số cam kết SLA tại Mục 5:
     - **P1 (Critical):** Phản hồi $\le 15$ phút | Cập nhật mỗi 30 phút | Workaround $\le 1$ giờ | Giải quyết triệt để $\le 4$ giờ.
     - **P2 (Major):** Phản hồi $\le 30$ phút | Cập nhật mỗi 1 giờ | Workaround $\le 2$ giờ | Giải quyết triệt để $\le 8$ giờ.
     - **P3 (Minor):** Phản hồi $\le 2$ giờ | Cập nhật mỗi 4 giờ | Workaround $\le 12$ giờ | Giải quyết triệt để $\le 24$ giờ.
     - **P4 (Cosmetic):** Phản hồi $\le 8$ giờ | Cập nhật hàng ngày | Chuyển sang backlog bảo trì.
   - **Tiêu chí thoát Hypercare (Exit Criteria - Mục 14):** Thống nhất 5 tiêu chí (0 P1/P2 mở, Uptime $\ge 99.5\%$ trong 72h, POS API p95 $\le 1.5$s, khớp 100% đối soát tồn kho/doanh thu, đủ chữ ký các bên).

2. **Về Lịch trực Hypercare 3 Ngày (Mục 3 & Mục 16):**
   - **Đánh giá:** **ĐẠT & ĐÃ ĐÍNH CHÍNH ĐÚNG NHÂN SỰ 6 THÀNH VIÊN**.
   - Lịch trực 3 ngày (Ca Sáng 07:00–15:30, Ca Chiều 15:00–23:00, Ca Đêm 23:00–07:00 On-call) phân bổ hợp lý cho đúng 6 thành viên chính thức của nhóm: TV1 An (PM/Architect), TV2 Kiên (Architect/DBA), TV3 Phát (QA), TV4 Thái (Fullstack Lead - On-site Lead), TV5 Huy (BA/PO), TV6 Vũ (Data/AI).
   - DBA Kiên đã xác nhận đồng thuận lịch trực On-call 24/7 cho sự cố CSDL P1/P2 tại `TV4-S4-REVIEW-APPROVAL-KIEN.md` Mục 4.4.

3. **Về Ma trận Trách nhiệm RACI (Mục 11):**
   - **Đánh giá:** **ĐẠT KÈM LÀM RÕ RANH GIỚI ROLLBACK**.
   - Ma trận RACI phân định chuẩn xác:
     - L1 Triage: TV3 Phát & TV5 Huy (**R**).
     - L2 Ứng dụng (POS/QR/In/UI): TV4 Thái (**R**), TV2 Kiên (**C**), TV1 An (**A**).
     - L2 Cơ sở dữ liệu (Lock/Deadlock/Query/Restore): TV2 Kiên (**R**), TV4 Thái (**C**), TV1 An (**A**).
     - Giám sát AI & Đối soát BI: TV6 Vũ (**R**), TV1 An (**A**).
     - Quyết định kích hoạt Rollback: TV1 An (**A**), tham vấn TV4 Thái (**C**) và TV2 Kiên (**C**).
   - **Làm rõ ranh giới thực thi Rollback trong RACI:**
     - **Rollback Cấp độ 1 (Feature Flag Kill-Switch, < 2 phút) & Cấp độ 2 (Container Backend/Frontend Application, < 10 phút):** **TV4 Vũ Duy Thái** là **R (Responsible)**, TV2 Kiên là **C**, TV1 An là **A**.
     - **Rollback Cấp độ 3 (Khôi phục CSDL PostgreSQL từ bản `pg_dump` trước Cutover, 15–45 phút theo D91):** **TV2 Trần Trung Kiên** là **R (Responsible)**, TV4 Thái là **C**, TV1 An là **A**.

4. **Về Điều kiện Kích hoạt Rollback và Đồng bộ D91/D101 (Mục 8):**
   - **Đánh giá:** **ĐẠT & ĐỒNG BỘ 100% VỚI D91/D101**.
   - Ba điều kiện kích hoạt Rollback hoàn toàn thống nhất:
     1. Sự cố P1 kéo dài quá **02 giờ liên tục** trong vận hành Hypercare (hoặc quá **30 phút** trong cửa sổ Cutover theo D91) mà không có giải pháp khắc phục/workaround.
     2. Phát sinh lỗi sai lệch hoặc hỏng dữ liệu tài chính/tồn kho không thể hoàn nguyên (`irrecoverable data corruption`).
     3. Tỷ lệ lỗi giao dịch thanh toán POS vượt quá **15%** trên toàn hệ thống trong 01 giờ cao điểm.
   - Trình tự phối hợp Rollback bắt buộc: (1) PM An phát lệnh Rollback $\rightarrow$ (2) TV4 Thái dừng container/ngắt kết nối ghi từ Backend vào DB $\rightarrow$ (3) TV2 Kiên đưa DB về single-user mode và chạy `python scripts/db_ops.py restore` từ snapshot trước Cutover (đã diễn tập thực tế đạt 0.48s, 34/34 checks pass) $\rightarrow$ (4) TV2 Kiên & TV6 Vũ đối soát bảng `payments` và `outbox_events` trước khi bật lại `SmsOutboxWorker` để chống gửi/thu tiền lặp $\rightarrow$ (5) TV4 Thái khởi động lại bản Backend/Frontend ổn định liền trước và cùng QA Phát chạy Smoke Test.

### 3.2. Yêu cầu Chỉnh sửa Cụ thể Đã Thực hiện Ngay trong D100 (Technical Corrections Applied)

Căn cứ phát hiện kỹ thuật của DBA Trần Trung Kiên tại `TV4-S4-REVIEW-APPROVAL-KIEN.md` Mục 3 và rà soát của PM:
1. **Đính chính mã SQLSTATE trong D100 (Mục 4) và các tài liệu liên quan của TV4 (`S4-06_P1P2_CLOSEOUT_MATRIX.md`, `TV4_FINAL_HANDOFF.md`, `TV4_EXTERNAL_DEPENDENCIES.md`):**
   - Trong PostgreSQL, mã **`P0002`** là `no_data_found` (phát sinh khi `SELECT ... INTO STRICT` không tìm thấy bản ghi, ví dụ trong discrepancy của test harness `BUG-D74-05`), **không phải** là mã lỗi Deadlock.
   - Mã lỗi Deadlock thực sự của PostgreSQL là **`40P01` (`deadlock_detected`)**, và lỗi không lấy được khóa do tranh chấp là **`55P03` (`lock_not_available`)**.
   - Hàm `sasms.reserve_order` của Kiên đã có `ORDER BY d.id FOR UPDATE OF d` để chống deadlock `40P01` khi khóa thiết bị của đơn hàng.
2. **Cập nhật trạng thái phê duyệt của D100:**
   - Chuyển trạng thái tài liệu `D100_HYPERCARE_SLA_TV4.md` từ `DỰ THẢO / CHỜ DUYỆT (PROPOSED)` sang **`APPROVED FOR HYPERCARE READINESS (PHÊ DUYỆT KẾ HOẠCH SẴN SÀNG — CHỜ LỆNH KÍCH HOẠT SAU KHI ĐẠT GATE E2E D78 & UAT D83)`**, cập nhật chữ ký phê duyệt kỹ thuật của Trần Trung Kiên (TV2) và Trịnh Quốc An (TV1) ngày 23/11/2026.

---

## PHẦN 4. ĐỐI CHIẾU BÁO CÁO TV4 VỚI BACKLOG, PR, LOG KIỂM THỬ VÀ CÁC QUALITY GATE; CHỐT TRẠNG THÁI NGHIỆM THU

PM Trịnh Quốc An đã đối chiếu toàn bộ bộ hồ sơ bàn giao của Vũ Duy Thái (TV4) gồm:
- Báo cáo Sprint 0–4: `TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md`, `SPRINT1_IMPLEMENTATION_REPORT.md`, `SPRINT2_IMPLEMENTATION_REPORT.md`, `SPRINT3_IMPLEMENTATION_REPORT.md`, `SPRINT4_IMPLEMENTATION_REPORT.md`.
- Hồ sơ tổng kết cuối kỳ: `TV4_FINAL_HANDOFF.md`, `TV4_WORKLOG_VERIFIED.md`, `S4-06_P1P2_CLOSEOUT_MATRIX.md`, `TV4_DASHBOARD_E2E_CLOSEOUT.md`, `TV4_EXTERNAL_DEPENDENCIES.md`, `D100_HYPERCARE_SLA_TV4.md`.

### 4.1. Đối chiếu với Product Backlog, Phân công Công việc và Lịch sử Pull Requests / Git Log

1. **Đối chiếu Lịch sử Git & Pull Requests trên `origin/main`:**
   - Toàn bộ các nhánh và Pull Request của TV4 nêu trong báo cáo đã được kiểm tra xác thực trên lịch sử Git của repository:
     - **Sprint 0 & Sprint 1:** Commits `c0f5f48`, `51ea4c5`, `5c93a54`, `02e759f` (Khung Spring Boot + React Vite, Catalog CRUD, Inventory Batch Receive, QR Lookup, Spring Security JWT Filter).
     - **Sprint 2:** **PR #28** (`c1912d3`), **PR #33** (`4f5df6f`), **PR #34** (`6544a00`) — Hoàn thiện POS Cart, Serial Selection API, Order-First (`DRAFT` $\rightarrow$ `RESERVE`), Cash Payment (`PAID`), In hóa đơn nhiệt 80mm kèm QR bảo hành SVG, tích hợp Widget AI Cross-sell và 7/7 PostgreSQL isolated integration tests.
     - **Sprint 3:** **PR #38** (`b30888c`) — Giao diện Online Catalog/Cart fail-closed (`S3-02`/`S3-05`), bộ 3 tầng Logistics Delivery API kết nối `V006` (`S3-06`), báo cáo refinement `S3-14`.
     - **Sprint 4:** **PR #39** (`7ac73ec`) — Device History Timeline API/UI (`US-12` read-only, store-scoped, không lộ PII), Store Summary BI Dashboard API/UI (`US-13`/`S4-03`).
     - **Sprint 4 Closeout & Hardening:** **PR #46** (`71e7794` từ `fix/tv4-s4-closeout-evidence`), **PR #48** (`98e5e33`), **PR #49** (`9bcf355`), **PR #50** (`6056367`) — Khắc phục `BUG-D71-01` (IMEI Luhn MOD-10), `BUG-D73-01` (AI Telemetry `SHOWN`/`ACCEPTED`), `BUG-D74-03` (QR Warranty dates), vá 6 lỗi an toàn trạng thái UI Dashboard (`DASH-S4-01..06`), hoàn thiện D100 Hypercare SLA và đính chính chuẩn xác nhân sự 6 thành viên.
   - **Đánh giá tính trung thực của Worklog (`TV4_WORKLOG_VERIFIED.md`):** 100% commit hash và PR khớp thực tế Git. Các trường giờ thực tế không có hệ thống chấm công tự động được ghi rõ là `UNKNOWN_PENDING_CONFIRMATION` (không bịa đặt số liệu), giải trình rõ các khoảng trống thời gian chờ migration DB.

2. **Đối chiếu với Kết quả Kiểm thử Tự động (Test Logs) và Sổ lỗi QA (D80 / D78):**
   - **Kiểm thử tự động tầng Kỹ thuật (Unit / Component / Isolated DB Integration):**
     - Backend Maven (`mvnw.cmd test`): Đạt **350+ tests PASS / 0 FAIL / 0 ERROR** (tăng từ 322 tests ở PR #46 lên 350 PASS / 18 SKIPPED sau khi hợp nhất AI Sprint 3–4 ở PR #47).
     - Frontend Vitest (`npm test`) & Vite Build (`npm run build`): Đạt **65/65 tests PASS**, build production thành công **100%**.
     - Bảo mật & Phụ thuộc (`Sprint 4/DB/D81-bao-cao-ra-soat-bao-mat.md`): **0 CVE** trên 91 gói Maven Backend, **0 lỗ hổng** trên `npm audit` Frontend, **0 rò rỉ secret** trên 300 files.
   - **Đối chiếu với Báo cáo Kiểm thử Hệ thống của QA Phát (`D78`, `D80`):**
     - Báo cáo `D78_Bao_Cao_Kiem_Thu_E2E_Toan_He_Thong_SASMS.docx` của QA Phát được tổng hợp trên mốc dữ liệu trước khi các bản vá Sprint 4 của TV4 (PR #46, #48, #49) và script môi trường Staging của DBA Kiên (PR #52 — `create_staging_e2e_container.ps1`, `provision_staging_e2e.sql`) được merge vào `main`.
     - Vì QA Phát chưa chạy lại lượt kiểm thử E2E trên môi trường Staging sau PR #46..#52, trạng thái các lỗi `BUG-D71-01`, `BUG-D73-01`, `BUG-D74-03` được TV4 ghi nhận là **`RESOLVED_BY_DEV / BLOCKED_PENDING_QA_EVIDENCE`** là **hoàn toàn chuẩn xác, tuân thủ đúng thẩm quyền QA** (Dev không tự ý đóng lỗi sang `VERIFIED_CLOSED` thay QA).

3. **Đối chiếu và Phân định Rõ Giữa Hồ sơ Kỹ thuật Thực tế và Hồ sơ Biểu mẫu PMBOK (Quality Gates M1–M6):**
   - Trong kho lưu trữ hiện tồn tại song song 2 nhóm tài liệu Quality Gate:
     1. **Nhóm Biểu mẫu Quản trị PMBOK (`Sprint 0..4/PM/D126..D131_*.docx`):** Là bộ hồ sơ mô phỏng quy trình quản trị dự án học thuật theo chuẩn PMBOK.
     2. **Nhóm Hồ sơ Kỹ thuật & Kiểm thử Thực chứng (`Sprint 0..4/DB/D126..D131*.md`, `Sprint 4/Test/D78*.docx`, `Sprint 4/BA/D83*.docx`, `Sprint 4/Fullstack/TV4/*.md`):** Phản ánh trạng thái mã nguồn, cơ sở dữ liệu và kết quả chạy kiểm thử thực tế trên máy/CI.
   - **Quyết định nguyên tắc đối soát:** Khi xét duyệt kỹ thuật, nghiệm thu tính năng và quyết định vận hành (Feature Flags, Hypercare, Go-Live), **nhóm Hồ sơ Kỹ thuật & Kiểm thử Thực chứng (`D78`, `D80`, `D83`, báo cáo TV4 và DB evidence) là nguồn chân lý kỹ thuật (Technical Source of Truth)**. Tuyệt đối không dùng văn bản biểu mẫu PMBOK để tuyên bố hệ thống đã vượt qua kiểm thử E2E hay tự động phê duyệt Go-Live khi `D78` và `D83` thực tế chưa ký hoàn tất.

### 4.2. Bảng Chốt Trạng thái Nghiệm thu Từng Hạng mục Bàn giao của TV4 (Phối hợp PM An – PO Huy – QA Phát – DBA Kiên)

| Mã Task / Hạng mục | Tên hạng mục bàn giao TV4 | Trạng thái Kỹ thuật (Dev & DB) | Trạng thái Nghiệm thu Phối hợp (PM / PO Huy / QA Phát) | Điều kiện còn lại để chuyển sang `FINAL_ACCEPTED` |
|---|---|:---:|:---:|---|
| **S0-24, S0-19, S0-20, S0-23** | Khung Source FE/BE, D63–D65, OpenAPI D52, Mockup D53, Thiết kế tích hợp D56 | `DONE_VERIFIED` (S0-24) / `PARTIAL` (D52, D53, D56) | **ACCEPTED_FOR_BASELINE** (Đạt chuẩn nền tảng Sprint 0) | Không chặn lập trình; các phần mở rộng Online/Return đã được đóng khung fail-closed. |
| **S1-04, S1-05, S1-07, S1-08, S1-11** | Catalog API, Nhập kho Serial/IMEI theo lô, Sinh/Quét QR, RBAC JWT Resource Server | `DONE_VERIFIED` (Unit + PG16 Isolated IT 5/5 PASS) | **TECHNICAL_ACCEPTED / PENDING_STAGING_E2E** | QA Phát chạy lại các ca `BLOCKED/PARTIAL` của D71 trên container Staging cổng `15435` với JWT test. |
| **S2-02, S2-03, S2-04, S2-06, S2-09, S2-15** | POS Order-First (`DRAFT` $\rightarrow$ `RESERVE` $\rightarrow$ `CASH PAID`), Khóa Serial, Hóa đơn nhiệt 80mm + QR, Tồn kho Realtime, Widget AI POS | `DONE_VERIFIED` (Unit + PG16 Isolated IT 7/7 PASS) | **TECHNICAL_ACCEPTED / PENDING_STAGING_E2E** | Bật 3 cờ POS (`pos-serial-api`, `pos-order-api`, `pos-cash-payment-api`) trên Staging để QA Phát thực thi bộ test D72 & D73 và PO Huy ký UAT-02, UAT-03, UAT-04. |
| **S3-02, S3-05** | Giao diện Catalog/Cart Online (`S3-02`) & Luồng Đặt hàng Online 30m (`S3-05`) | `DONE_VERIFIED` (S3-02) / `BLOCKED_FAIL_CLOSED` (S3-05 Checkout) | **ACCEPTED_AS_FAIL_CLOSED (SCOPE_DEFERRED)** | Theo quyết định `ARCH-DEC-S03-01`: Nghiệm thu phần UI Catalog/Cart an toàn; chấp thuận giữ `online-order.enabled=false` (ngoại lệ phạm vi có kiểm soát, không tính là lỗi code của TV4). |
| **S3-06** | API Điều phối Giao hàng & Cập nhật Kết quả Lắp đặt (`LogisticsDeliveryController`) | `IMPLEMENTED_NOT_VERIFIED` (10 unit tests PASS, gọi đúng `V006`) | **TECHNICAL_ACCEPTED_BACKEND / UI_DEFERRED** | Cho phép bật `logistics-api.enabled=true` trên Staging để QA Phát test API; phần UI dành cho kỹ thuật viên hiện trường được ghi nhận là giới hạn phạm vi (`BUG-D74-02`). |
| **S3-14** | Báo cáo Refinement & Ranh giới Hợp đồng Sprint 3 | `DONE_VERIFIED` | **ACCEPTED** | Đã hoàn tất minh bạch hóa toàn bộ ranh giới hợp đồng. |
| **S4-03 (Hỗ trợ), US-12, US-13** | API/UI Lịch sử Thiết bị (`US-12` read-only) & Store Summary BI Dashboard (`US-13`), vá lỗi `DASH-S4-01..06` | `DONE_VERIFIED` (Unit + SQL Reconciliation 35/35 PASS) | **TECHNICAL_ACCEPTED / PENDING_BROWSER_E2E** | Bật `device-history-api` và `store-dashboard-api` trên Staging; chạy `run_dashboard_http_e2e.ps1` và QA Phát kiểm tra trên trình duyệt theo checklist. Phần mutation Đổi trả (`FR-14`) được xác nhận ngoài phạm vi đã duyệt DoR. |
| **S4-06** | Sửa lỗi P1/P2 (`BUG-D71-01` Luhn IMEI, `BUG-D73-01` AI Telemetry, `BUG-D74-03` QR Warranty Dates) | `RESOLVED_BY_DEV` (Đã có code fix + unit tests 100% PASS) | **DEV_COMPLETE / PENDING_QA_RETEST** | QA Phát thực hiện re-test trên Staging để chuyển trạng thái trong D80 từ `BLOCKED_PENDING_QA_EVIDENCE` sang `VERIFIED_CLOSED`. |
| **S4-16** | Quy trình Vận hành Hypercare 3 Ngày & Cam kết SLA (`D100_HYPERCARE_SLA_TV4.md`) | `DONE_VERIFIED` (Đã cập nhật SQLSTATE `40P01`/`55P03`/`P0002` và RACI) | **APPROVED_FOR_READINESS** (PM An & DBA Kiên đã duyệt) | Sẵn sàng 100% về quy trình; chỉ kích hoạt thực thi sau khi có lệnh Go-Live chính thức. |
| **S4-18** | Hồ sơ Bàn giao Cuối kỳ của TV4 (`TV4_FINAL_HANDOFF.md`, `TV4_WORKLOG_VERIFIED.md`, `TV4_EXTERNAL_DEPENDENCIES.md`) | `DONE_VERIFIED` | **HANDOFF_DOSSIER_ACCEPTED** | Hồ sơ kỹ thuật của TV4 được kết luận **ĐẠT YÊU CẦU BÀN GIAO KỸ THUẬT**; sẵn sàng nạp vào Biên bản Bàn giao Hệ thống D95. |

---

## PHẦN 5. KẾT LUẬN TỔNG THỂ VÀ LỘ TRÌNH HÀNH ĐỘNG TIẾP THEO (NEXT STEPS FOR HUY & PHÁT)

### 5.1. Kết luận Chính thức đối với Hồ sơ Bàn giao TV4

1. **Tất cả các vấn đề kiến trúc còn chặn từ Sprint 0 đến Sprint 3** (Customer Identity mapping, chính sách JWT/OIDC, RBAC 9 vai trò canonical, Store-Scope concealment, DB runtime role `sasms_app`, chuẩn hóa Serial/IMEI Luhn, POS Order-First, Outbox SMS) **ĐÃ CÓ QUYẾT ĐỊNH RÕ RÀNG VÀ DỨT ĐIỂM** tại Phần 1 của văn bản này.
2. **Chính sách bật riêng Feature Flag trên Staging Sprint 4** đã được phê duyệt rõ ràng tại Phần 2: cho phép bật 6 cờ tính năng đã có đủ Backend + DB (`pos-serial-api`, `pos-order-api`, `pos-cash-payment-api`, `logistics-api`, `device-history-api`, `store-dashboard-api`) qua biến môi trường trên Staging cô lập (cổng `15435`, user `tv4_staging_app_user`), trong khi tiếp tục khóa an toàn (`false`) đối với Online Checkout của khách vãng lai và giữ SMS ở chế độ `MOCK`.
3. **Tài liệu `D100_HYPERCARE_SLA_TV4.md`** đã được review chi tiết, hiệu chỉnh chính xác mã lỗi PostgreSQL (`40P01` deadlock, `55P03` lock contention, `P0002` no data found), làm rõ ranh giới RACI Rollback ứng dụng (TV4) vs CSDL (TV2), và được **CHÍNH THỨC PHÊ DUYỆT SẴN SÀNG (`APPROVED FOR HYPERCARE READINESS`)**.
4. **Hồ sơ bàn giao kỹ thuật của Vũ Duy Thái (TV4)** được kết luận: **Đầy đủ, trung thực, truy vết 100% tới commit/PR/test log, tuân thủ nghiêm ngặt kỷ luật bảo mật (Zero-Trust, Fail-Closed) và ĐƯỢC CHẤP THUẬN NGHIỆM THU Ở CẤP ĐỘ KỸ THUẬT PHÂN HỆ (`TECHNICAL HANDOFF ACCEPTED`)**.

### 5.2. Đầu việc Phối hợp Ngay cho Phạm Đại Phát (QA Lead) và Nguyễn Quang Huy (BA/PO Lead)

Để tiến tới đóng cổng nghiệm thu E2E/UAT trên môi trường Staging (không tự động coi là đã Go-Live):
1. **Phạm Đại Phát (TV3 — QA Lead):**
   - Khởi chạy môi trường Staging cô lập bằng script `.\scripts\create_staging_e2e_container.ps1` (PostgreSQL 16 trên cổng `15435`, user `tv4_staging_app_user`) và bật 6 biến môi trường Feature Flag theo Mục 2.1.
   - Thực hiện re-test và xác nhận đóng 3 lỗi đã được TV4 sửa (`BUG-D71-01`, `BUG-D73-01`, `BUG-D74-03`) cùng lỗi discrepancy `BUG-D74-05` trong Sổ lỗi **D80 Sprint 4**.
   - Chạy thực thi bộ test **D72 (POS/Receipt/Stock)**, **D73 (AI Cross-sell)** và script **`.\scripts\run_dashboard_http_e2e.ps1`**, cập nhật số liệu thực thi mới vào báo cáo **D78 v1.1**.
2. **Nguyễn Quang Huy (TV5 — BA Lead / PO):**
   - Dựa trên quyết định kiến trúc tại Phần 1 và môi trường Staging đã bật 6 cờ tính năng, chủ trì chạy nghiệm thu các kịch bản **UAT-01, UAT-02, UAT-03, UAT-04, UAT-08, UAT-09** trong biểu mẫu **D83**.
   - Ghi nhận chính thức vào **D83** và **D95** rằng các kịch bản Online Checkout khách vãng lai (`S3-05`), UI Kỹ thuật viên giao lắp (`BUG-D74-02`), Đổi trả hoàn tiền (`FR-14`) và SMS nhà mạng thật (`BUG-D74-04`) là **các hạng mục giới hạn phạm vi có kiểm soát (Controlled Scope Limitations / Fail-Closed)** để minh bạch hóa hoàn toàn trước Hội đồng bảo vệ đồ án.

---

## PHẦN 6. XÁC NHẬN VÀ PHÊ DUYỆT (DECISION SIGN-OFF BLOCK)

| Vai trò | Họ và tên | Nội dung xác nhận | Trạng thái | Ngày xác nhận |
|---|---|---|:---:|:---:|
| **Project Manager / System Architect** | **Trịnh Quốc An (TV1)** | Chốt quyết định kiến trúc Sprint 0–3, chính sách Feature Flag Staging Sprint 4, phê duyệt D100 Hypercare SLA và chấp thuận hồ sơ bàn giao kỹ thuật TV4. Không tự động phê duyệt Go-Live Production. | **APPROVED** | 23/11/2026 |
| **Architect / DBA Lead** | **Trần Trung Kiên (TV2)** | Đã review và phê duyệt kỹ thuật tại `TV4-S4-REVIEW-APPROVAL-KIEN.md`; xác nhận cấp `tv4_staging_app_user`, hiệu năng `V008` và đồng bộ D91/D101 với D100. | **APPROVED WITH TECHNICAL NOTES** | 23/11/2026 |
| **Fullstack Lead** | **Vũ Duy Thái (TV4)** | Đã hoàn tất 100% mã nguồn, unit/integration tests, bản vá P1/P2 và cập nhật đính chính kỹ thuật SQLSTATE/RACI trong hồ sơ bàn giao và D100. | **CONFIRMED & HANDED OVER** | 23/11/2026 |
| **BA Lead / Product Owner** | **Nguyễn Quang Huy (TV5)** | Tiếp nhận quyết định kiến trúc, ranh giới phạm vi fail-closed và lịch chạy UAT D83 trên Staging. | **ACKNOWLEDGED / PENDING UAT SIGN-OFF (D83)** | `[Chờ ký sau lượt chạy UAT]` |
| **QA / Documentation Lead** | **Phạm Đại Phát (TV3)** | Đã thực hiện lượt retest độc lập ngày 24/11/2026 (PR #55–#57): `BUG-D71-01` VERIFIED; `BUG-D73-01` và `BUG-D74-03` REOPENED; ghi nhận thêm lỗi mới tại D80 Sprint 1–3. | **RETESTED 24/11/2026 (PENDING FIX & D78 RE-RUN)** | 24/11/2026 |

---

## PHẦN 7. PHỤ LỤC CẬP NHẬT ĐỐI CHIẾU SAU ĐỢT RETEST QA NGÀY 24/11/2026 (28/11/2026)

Căn cứ 04 PR mới nhất đã hợp nhất vào `origin/main` (`d65c530`: PR #54–#57), PM Trịnh Quốc An cập nhật trạng thái đối chiếu thực tế như sau để đồng bộ tuyệt đối với **D94 (Go/No-Go)**, **D120 (RAID Log)**, **D131 (Gate M6)** và **D144 (Quyết toán 1 tỷ VNĐ)**:

1. **Kết quả retest các lỗi TV4 báo đã sửa (`S4-06`):**
   - **`BUG-D71-01` (IMEI Luhn MOD-10):** Đã chuyển sang **`VERIFIED (PASS 24/11/2026)`** trên `TC-03-10`.
   - **`BUG-D73-01` (Lưu sự kiện hiển thị/chấp nhận AI):** QA retest ngày 24/11/2026 **FAIL $\rightarrow$ `REOPENED`** (phối hợp TV4 & TV6 sửa dứt điểm).
   - **`BUG-D74-03` (Bảo hành QR):** QA retest ngày 24/11/2026 **FAIL $\rightarrow$ `REOPENED`** (TV4 xử lý).
2. **Các lỗi mới phát hiện trong lượt retest độc lập ngày 24/11/2026 (chặn Go-Live):**
   - **Sprint 1 (`D80`):** Thêm 3 lỗi Medium `OPEN` (`BUG-D71-R2-01..03`) và `BUG-D71-RT-01`.
   - **Sprint 2 (`D80_Bug_Log_Sprint2_D72_D73_20261124.docx`):** Thêm **`BUG-D72-R1-01` (High – giữ chỗ hết hạn không giải phóng, kẹt `RESERVED`)**, **`BUG-D72-R1-02` (High – vai trò theo cửa hàng bị `403` đọc Catalog)**, `BUG-D72-R1-03` (Medium – QR nhiều thiết bị trên hóa đơn / hiển thị UUID), và 3 câu hỏi nghiệp vụ `Q-S2-01..03` chờ BA/PO Huy chốt.
   - **Sprint 3 (`D80`):** Tổng kết D74 đạt **15/42 PASS** (FR-11 tăng lên 12/14 PASS khi bật Logistics API), còn 4 P1 OPEN và phát sinh thêm `BUG-D74-R1-01` (High), `BUG-D74-R1-02`, `BUG-D74-R1-03` (Medium).
3. **Cảnh báo môi trường & bảo mật bổ sung từ `TV4_HUONG_DAN_TRUY_CAP_VA_KIEM_THU_AI.md` (PR #54):**
   - Yêu cầu DBA Trần Trung Kiên (TV2) rà soát và loại bỏ mật khẩu mặc định / quyền rộng trong script provisioning PR #52 trước khi dùng cho Staging dùng chung.
   - Yêu cầu Vũ Duy Thái (TV4) tích hợp luồng đăng nhập OIDC (`authSession`) vào `frontend/src/main.jsx` để mở khóa kiểm thử E2E trình duyệt và UAT `D83`.
4. **Kết luận điều chỉnh hiệu lực (28/11/2026):**
   - Giữ nguyên quyết định **`NO-GO PRODUCTION`** tại [D94_Bien_Ban_Go_No_Go_SASMS.docx](D94_Bien_Ban_Go_No_Go_SASMS.docx) và trạng thái **Nghiệm thu bàn giao Giai đoạn 1 có điều kiện** tại [D131_Bien_Ban_Quality_Gate_M6_Final_Handover.docx](D131_Bien_Ban_Quality_Gate_M6_Final_Handover.docx) & [D142_Bien_Ban_Nghiem_Thu_Va_Dong_Du_An.docx](D142_Bien_Ban_Nghiem_Thu_Va_Dong_Du_An.docx).
   - Toàn bộ ngân sách và quyết toán PM được đồng bộ 100% theo đường cơ sở **1.000.000.000 VNĐ / 380 Man-Days** của `Du_Toan_Ngay_Cong_SASMS.docx` tại [D12](../../Ti%E1%BB%81n%20Sprint/PM/D12_Ke_Hoach_Quan_Ly_Chi_Phi_SASMS.xlsx), [D121](D121_Kiem_Soat_Nguon_Luc_Chi_Phi_Va_KPI_EVM.xlsx) và [D144](D144_Quyet_Toan_Chi_Phi_Va_EVM_Cuoi_Du_An.docx) (EV = 780tr, AC = 745tr, bảo lưu 255tr chờ Go-Live).
