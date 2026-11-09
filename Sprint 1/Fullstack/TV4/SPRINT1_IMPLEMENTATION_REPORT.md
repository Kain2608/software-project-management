# Báo cáo triển khai Sprint 1 – TV4

Ngày kiểm tra cuối: **09/11/2026**

Nhánh làm việc: `feat/tv4-s1-catalog-api`

## 1. Phạm vi TV4

| Task | Phạm vi đã thực hiện | Trạng thái kỹ thuật |
|---|---|---|
| S1-04 | Catalog DAO, Service, REST Controller; create/read/list/update/soft delete | PARTIAL – unit/controller test PASS; PostgreSQL integration và hợp đồng HTTP còn chờ |
| S1-07 | Nhập thiết bị theo lô qua `sasms.receive_device_batch` và REST Controller | PARTIAL – unit/controller test PASS; idempotency/rollback trên PostgreSQL thật chưa chạy |
| S1-08 | QR parser, lookup có kiểm quyền theo store, REST API, camera scanner React | PARTIAL – backend/frontend test và build PASS; camera/JWT/E2E thật chưa chạy |
| S1-11 | JWT Resource Server, trusted actor, identity resolver, permission checker | PARTIAL – security test bằng RSA test key PASS; provider/JWKS production chưa được chọn hoặc kiểm thử |

Không coi trạng thái PARTIAL là nghiệm thu Sprint 1. Không có bằng chứng QA/PO/PM đã duyệt các hợp đồng provisional.

## 2. Source chính đã tạo hoặc thay đổi

- `backend/pom.xml`: Spring JDBC, Spring Security Resource Server/JWT và test dependencies theo dependency management của Spring Boot.
- `backend/src/main/resources/application.properties`: biến môi trường JWT; mặc định fail closed.
- `backend/src/main/java/com/smartelectronicsupermarket/catalog/`: DAO, Service, Controller và DTO Catalog.
- `backend/src/main/java/com/smartelectronicsupermarket/inventory/`: DAO, Service, Controller và DTO nhập lô thiết bị.
- `backend/src/main/java/com/smartelectronicsupermarket/qr/`: parser, lookup DAO/Service, Controller và DTO QR.
- `backend/src/main/java/com/smartelectronicsupermarket/security/`: identity contracts, JWT validation, trusted authentication và permission checker.
- `backend/src/main/java/com/smartelectronicsupermarket/common/web/`: actor extraction và error contract an toàn.
- Các package tương ứng dưới `backend/src/test/java/`: unit, MVC và security integration tests.
- `frontend/src/features/qr/`: camera scanner, payload validation, API client, UI states và tests.
- `frontend/src/shared/api/httpClient.js`: base URL dùng được cả Vite và Node test.
- `frontend/package.json`, `frontend/package-lock.json`: khóa `@zxing/browser` ở `0.1.5` để tương thích Node 22; `@zxing/library` là `0.21.3` (Node `>=10.4.0`).

Không sửa `database/`, migration V001/V002 hoặc tài liệu DB của Kiên.

## 3. REST API đang triển khai

Tất cả endpoint nghiệp vụ dưới đây bắt buộc Bearer JWT hợp lệ và actor nội bộ phải ánh xạ được tới đúng một `sasms.users` đang active.

| Method | Path | Quyền/hành vi chính | Trạng thái hợp đồng |
|---|---|---|---|
| POST | `/api/v1/products` | `catalog.write`; gọi `sasms.create_product` | Provisional – D52 chưa có Catalog HTTP contract |
| GET | `/api/v1/products/{productId}` | `catalog.read` global | Provisional |
| GET | `/api/v1/products` | `catalog.read`; query/active/limit/offset; sort ổn định | Provisional |
| PUT | `/api/v1/products/{productId}` | `catalog.write`; giữ `expected_revision`; gọi `sasms.update_product` | Provisional |
| DELETE | `/api/v1/products/{productId}` | Soft delete bằng `is_active=false`, không DELETE vật lý | Provisional |
| POST | `/api/v1/inventory/receipt-items/{receiptItemId}/device-batches` | Header `Idempotency-Key` là UUID canonical; gọi `sasms.receive_device_batch` | Provisional – khác path/body trong D52 |
| POST | `/api/v1/devices/qr-lookup` | Parse `SASMS:DEVICE:<uuid>`; `inventory.read` theo `warehouses.store_id` | Bám path D52; response tạm dùng `product_id` thay `sku_id` |

Public chỉ gồm các status endpoint baseline: `GET /api/health`, `GET /api/pos/status`, `GET /api/serial-imei/status`. Các endpoint khác fail closed khi thiếu JWT.

## 4. Kiến trúc bảo mật

Luồng thực tế:

`Bearer JWT -> kiểm chữ ký/JWKS + exp/nbf + issuer + audience -> issuer/subject -> users.auth_subject -> kiểm user tồn tại và active -> TrustedActorAuthenticationToken -> actor UUID nội bộ -> has_permission/SQL function -> Service -> DAO`

- Không đọc actor từ header `X-Actor-Id`, request body hoặc role claim do client gửi.
- Không phát JWT, không hardcode secret và không tạo admin/user mặc định.
- `JdbcPermissionChecker` gọi bound SQL `sasms.has_permission(uuid,text,uuid)`; `FALSE`, `NULL` hoặc lỗi DB không cấp quyền.
- QR query kiểm `inventory.read` ngay trong SQL với store của warehouse; không lấy toàn bộ record rồi lọc trong Java.
- Khi không tìm thấy hoặc ngoài scope, QR public response cùng là 404 để hạn chế dò dữ liệu.
- Lỗi authentication/authorization trả JSON an toàn theo D52 với `code`, `message`, `correlation_id`, `retryable`; không trả exception, SQL hoặc token. Phản hồi 401 vẫn có `WWW-Authenticate: Bearer`.

## 5. Cấu hình và chạy backend

Yêu cầu baseline: Java 26, Spring Boot 4.1.1, Maven Wrapper và PostgreSQL 16 đã áp dụng đúng migration Sprint 1.

Biến môi trường bắt buộc cho profile PostgreSQL:

```powershell
$env:SASMS_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/sasms'
$env:SASMS_DB_USERNAME = '<runtime-login-thuoc-sasms_app>'
$env:SASMS_DB_PASSWORD = '<lay-tu-secret-store-local>'
```

Biến môi trường JWT phải cấu hình đủ cả ba hoặc để trống cả ba:

```powershell
$env:SASMS_JWT_ISSUER = 'https://<issuer-da-duyet>'
$env:SASMS_JWT_AUDIENCE = '<audience-da-duyet>'
$env:SASMS_JWT_JWKS_URI = 'https://<provider>/.well-known/jwks.json'
```

Chạy:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgresql"
```

Thiếu cấu hình JWT không mở endpoint nghiệp vụ: status endpoint vẫn public, phần còn lại trả 401. Cấu hình JWT dở dang làm ứng dụng từ chối khởi động.

## 6. Cấu hình và chạy frontend

```powershell
cd frontend
npm ci
$env:VITE_API_BASE_URL = 'http://127.0.0.1:8080/api'
npm run dev
```

`QrCameraScanner` nhận `accessToken` từ lớp đăng nhập tương lai. App hiện chưa có identity-provider/login flow, vì vậy màn hình mặc định chỉ quét và validate cục bộ, hiển thị “cần xác thực” và không gọi lookup API. Không lưu hoặc tự tạo token trong frontend.

Camera cần secure context (`https` hoặc `localhost`) và quyền người dùng. Frame camera chỉ xử lý trong trình duyệt, không gửi lên backend; camera được dừng khi hoàn tất hoặc unmount.

### Demo camera trên Windows/mobile

1. Cấu hình backend PostgreSQL và JWT bằng các biến môi trường ở mục 5; dùng user active có `inventory.read` tại store chứa thiết bị thử.
2. Cấp access token từ identity provider đã được duyệt cho lớp đăng nhập frontend; không dán token vào source hoặc commit `.env`.
3. Chạy frontend bằng HTTPS dev proxy hoặc `localhost`. Điện thoại thật cần URL HTTPS mà thiết bị truy cập được; HTTP qua địa chỉ LAN thường không được cấp camera.
4. Mở màn hình QR, cấp quyền camera, quét `SASMS:DEVICE:<qr_token>` rồi kiểm tra loading và dữ liệu tối thiểu `device_id`, `product_id`, `warehouse_id`, `status`.
5. Thử payload sai, thiết bị ngoài store, token hết hạn và user thiếu quyền. Kỳ vọng lần lượt là validation error, cùng trạng thái 404 không lộ dữ liệu, 401 và 404/403 theo lớp kiểm soát tương ứng.
6. Bấm dừng, chuyển trang và quay lại để xác nhận camera track đã được giải phóng; lặp trên ít nhất Chrome Android và một trình duyệt desktop.

Các bước này là hướng dẫn chạy lại, không phải bằng chứng camera đã được kiểm thử trong lượt audit.

## 7. Cách chạy kiểm thử

```powershell
cd backend
.\mvnw.cmd clean test

cd ..\frontend
npm ci
npm run test:qr
npm run build
```

PostgreSQL smoke test chỉ chạy khi có bộ biến môi trường test riêng đã được xác nhận an toàn; không bật test này trên DB dùng chung.

```powershell
$env:SASMS_PG_SMOKE_ENABLED = 'true'
$env:SASMS_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/sasms'
$env:SASMS_DB_USERNAME = '<runtime-test-login>'
$env:SASMS_DB_PASSWORD = '<secret-local>'
.\mvnw.cmd -Dtest=PostgreSqlConnectionSmokeTest test
```

Test tự giới hạn host loopback, cổng 5432, database `sasms`, mở transaction read-only và rollback trước khi đóng kết nối. Việc đáp ứng các điều kiện kỹ thuật này vẫn không thay thế bước xác nhận đó là DB thử cô lập.

## 8. Minh chứng kỹ thuật ngày 09/11/2026

| Kiểm tra | Kết quả thực tế |
|---|---|
| Backend `mvnw clean test` | PASS – 114 tests, 0 failures, 0 errors, 1 skipped, BUILD SUCCESS |
| PostgreSQL smoke test trong lượt này | SKIPPED – Docker daemon không chạy, không có biến DB test và chưa xác nhận DB cô lập |
| Frontend `npm ci` | PASS – cài đúng lockfile |
| Frontend `npm run test:qr` | PASS – 11 tests, 0 fail |
| Frontend `npm run build` | PASS – Vite 5.4.21, 321 modules; có cảnh báo chunk 616.99 kB > 500 kB |
| Frontend `npm audit` | 2 findings còn lại: 1 moderate, 1 high; không có critical |
| Git/content audit | PASS – đã đọc/scan 75 file thay đổi (7 tracked, 68 untracked); không phát hiện secret, private key, generated artifact hoặc trailing whitespace |
| JWT verification | PASS ở automated tests bằng RSA key chỉ tồn tại trong test |
| Provider/JWKS production | NOT TESTED |
| Camera thật trên desktop/mobile | NOT TESTED |
| HTTP + JWT + PostgreSQL end-to-end | NOT TESTED |

Backend còn Mockito dynamic-agent warning trên Java 26; không làm test fail nhưng cần theo dõi khi nâng framework/JDK. Cảnh báo deprecation `HttpStatus.UNPROCESSABLE_ENTITY` đã được sửa bằng API Spring hiện hành.

### Lỗi kỹ thuật đã sửa trong final audit

- SQLSTATE `23503` từ V001/V002 (product/receipt item không tồn tại) trước đây rơi vào 503; hiện được ánh xạ an toàn thành 404. `23502`/`23514` trả 422 và không lộ chi tiết DB.
- Error DTO trước đây serialize `correlationId`, lệch D52; hiện trả `correlation_id`.
- 401/403 ở Spring Security trước đây không dùng JSON error contract chung; hiện trả nội dung tối thiểu, có correlation ID, không trả stack trace/token/SQL. 401 giữ header `WWW-Authenticate: Bearer`.
- Đã thay hằng `HttpStatus` deprecated, bổ sung ba regression tests cho DB error mapping và mở rộng security integration assertions.
- Đã vá hai transitive dependency frontend có bản tương thích; chi tiết ở bảng npm audit bên dưới.

### npm audit sau xử lý

| Dependency | Đường phụ thuộc | Phạm vi | Xử lý/kết luận |
|---|---|---|---|
| `follow-redirects` 1.16.0 | `axios -> follow-redirects` | Production dependency của HTTP client khi chạy trong Node | Đã cập nhật lockfile lên 1.16.3; finding high được loại bỏ, không đổi Axios/React/Vite major |
| `source-map-js` 1.2.1 | `vite -> postcss -> source-map-js` | Development/build | Đã cập nhật lockfile lên 1.2.2; finding high được loại bỏ |
| `esbuild` 0.21.5 | `vite -> esbuild` | Development server/build | Còn moderate GHSA-67mh-4wv8-2f99. npm chỉ đưa phương án qua Vite 8; không tự nâng major. Không expose Vite dev server ra mạng không tin cậy |
| `vite` 5.4.21 | Direct dev dependency | Development server/build | Còn high tổng hợp từ advisory Vite/launch-editor. Bản vá npm đề xuất là Vite 8.3.7, vượt baseline major nên PENDING Architect/PM review |

Không chạy `npm audit fix --force`. `@zxing/browser` giữ ở 0.1.5 và `@zxing/library` 0.21.3 để tương thích Node 22; QR tests và production build vẫn PASS sau cập nhật lockfile.

## 9. Những hành vi đã được test tự động

- JWT thiếu/sai chữ ký/hết hạn/sai issuer/sai audience bị từ chối; trusted actor chỉ tạo sau khi user active được resolve.
- Cấu hình JWT thiếu một phần fail startup; không có bypass khi cấu hình rỗng.
- Phản hồi 401 dùng error contract `correlation_id`; actor/role giả từ client không được tin cậy.
- Catalog validation, create/update, expected revision propagation, quyền read/write, soft-delete không dùng hard delete và safe error mapping.
- Inventory validation 1..500 thiết bị, schema `serial`/`imei`, request key được giữ nguyên, không retry hoặc trả success giả khi DAO lỗi.
- QR canonical payload, query theo `qr_token`, permission theo store, response không chứa serial/IMEI và 404 không phân biệt missing/out-of-scope.
- Frontend QR validation và ánh xạ 401/403/404/error sang thông báo không lộ chi tiết server.

Mock JDBC/MVC không chứng minh atomic rollback, uniqueness, idempotency hoặc store scope thực sự trên PostgreSQL.

## 10. Chênh lệch D34, D52 và DB Sprint 1

1. D34 yêu cầu `model`, `needs_install`, `policy_version`; V001 và các function Catalog hiện không cung cấp đủ. Không thêm cột/migration; API chỉ trả dữ liệu DB thực có.
2. D52 chưa định nghĩa Catalog REST endpoints. Các path/DTO Catalog hiện là provisional, cần BA/Architect duyệt.
3. D52 nhập kho dùng `/inventory/receipts`, `warehouse_id`, `source_ref`, item `sku_id`; V002 chỉ nhận `receipt_item_id` thật. Endpoint hiện bám V002 để không tạo receipt giả.
4. D52 cho `Idempotency-Key` là string 8..128; V002 dùng UUID. Mapping hiện yêu cầu UUID canonical và giữ nguyên qua retry.
5. D52 QR response ghi `sku_id`; schema hiện có `devices.product_id`. Response hiện dùng `product_id`, chưa tự đổi tên thành SKU.
6. Quy tắc canonical Serial/IMEI theo hãng và yêu cầu IMEI theo SKU chưa đủ để Java tự chuẩn hóa; Service giữ payload nguyên vẹn, DB kiểm tra cuối.
7. Provider, issuer, audience, JWKS/key lifecycle và cách frontend nhận token production chưa được phê duyệt.

## 11. Phần chưa kiểm thử/nghiệm thu

- Chưa chạy migration hoặc integration test trên PostgreSQL test cô lập bằng runtime login thuộc `sasms_app`.
- Chưa chứng minh bằng DB thật: revision conflict, duplicate SKU, duplicate Serial/IMEI, same-key retry, changed-payload conflict, rollback toàn batch, active/inactive actor và cross-store permission.
- Chưa có login/provider production nên chưa demo browser -> JWT -> backend -> PostgreSQL.
- Chưa kiểm thử camera thật, quyền camera, mobile browser và hiệu năng QR dưới 1 giây.
- Chưa có BA duyệt các HTTP contract provisional; chưa có QA/PO nghiệm thu UI hoặc luồng nghiệp vụ.
- Chưa xử lý hai advisory dev-tool còn lại vì phương án npm đưa ra yêu cầu nâng Vite major; cần Architect/PM duyệt và regression riêng.

## 12. Checklist demo/QA đề xuất

- [ ] BA/Architect duyệt Catalog endpoints/DTO và các gap D34.
- [ ] BA/DB duyệt Inventory path và quy tắc UUID `Idempotency-Key`.
- [ ] BA/DB duyệt `product_id` so với `sku_id` trong QR response.
- [ ] Architect/PM chọn provider, issuer, audience, JWKS và token delivery cho frontend.
- [ ] DBA cấp DB test cô lập và runtime login; QA chạy ma trận integration ở mục 11.
- [ ] QA thử JWT âm/dương, user inactive, thiếu quyền và cross-store.
- [ ] QA thử QR camera trên desktop/mobile, manual payload, 401/403/404 và cleanup camera.
- [ ] PO review nghiệp vụ Catalog, nhập kho và QR; không đánh dấu APPROVED trước khi có bằng chứng.
- [ ] Architect/PM quyết định kế hoạch nâng Vite để xử lý hai audit findings dev-tool còn lại.
- [ ] Review bundle size 616.99 kB và Mockito agent warning trước production hardening.
- [ ] Xác nhận 7 file tracked đã sửa và 68 file untracked đều thuộc phạm vi TV4 trước `git add`; không thêm `target`, `dist`, `node_modules` hoặc dữ liệu DB runtime.

## 13. Lệnh Git/Draft PR đề xuất

Chỉ chạy sau khi review toàn bộ `git status`, secret scan và xác nhận file thuộc TV4:

```powershell
git add backend/pom.xml backend/src/main backend/src/test frontend/package.json frontend/package-lock.json frontend/src "Sprint 1/Fullstack/TV4/SPRINT1_IMPLEMENTATION_REPORT.md"
git diff --cached --check
git diff --cached --stat
git commit -m "feat(sprint1): add secured catalog inventory and qr flows"
git push -u origin feat/tv4-s1-catalog-api
```

Tên Draft PR đề xuất: `Sprint 1 TV4: secured Catalog, Inventory Batch, QR lookup and JWT core`

PR phải ghi rõ PostgreSQL integration, production identity provider, camera E2E và BA/QA/PO approval vẫn PENDING; không mô tả các phần đó là đã hoàn thành.
