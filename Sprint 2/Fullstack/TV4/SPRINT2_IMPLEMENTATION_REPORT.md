# SASMS Sprint 2 - Báo cáo triển khai TV4

**Người thực hiện:** Vũ Duy Thái (TV4 - Fullstack Lead)
**Ngày kiểm chứng cuối:** 16/11/2026
**Nhánh cập nhật S2-02:** `feat/tv4-s2-pos-cart`
**Nền nhánh cập nhật:** `origin/main` tại `e2db1a7`
**Trạng thái:** Sẵn sàng tạo Draft PR để review giao diện giỏ hàng; chưa đủ điều kiện nghiệm thu POS end-to-end.

## 1. Phân công Sprint 2

Phân công chính thức trong `Sprint 2/DB/inputs/phan-cong-sprint-2.csv` giao TV4 các đầu việc chính và hỗ trợ sau:

| Task | User Story / phạm vi | Vai trò TV4 | Trạng thái thực tế |
|---|---|---|---|
| S2-02 / TSK-02-01 | US-04 - màn hình POS tại quầy | Chính | IMPLEMENTED/UNIT TESTED phần giỏ hàng Catalog + Stock; order/Serial/payment end-to-end vẫn BLOCKED. |
| S2-04 / TSK-02-03 | US-05 - mẫu hóa đơn POS có QR bảo hành | Chính | PARTIAL: API receipt PAID và browser print preview đã có; QR bảo hành/print-job/e-invoice chưa có contract chốt. |
| S2-09 / TSK-02-08 | US-07 - tích hợp AI vào POS | Chính phần tích hợp | Widget đã nhận cart thật qua props và chỉ dùng gợi ý sau khi đối chiếu Catalog/Stock; App chưa có login/cart production nên E2E vẫn NOT TESTED. |
| S2-03 / TSK-02-02 | US-04 - tìm và khóa Serial | Hỗ trợ Kiên | PARTIAL: tầng Data Access/Service nội bộ đã có unit test; DB artifact Sprint 2 hiện không cài được trên schema thật. |
| S2-05 / TSK-02-04 | US-05 - driver/test máy in | Hỗ trợ QA | PARTIAL: CR-TV4-W6-001 chuyển sang browser preview; máy in nhiệt thật NOT TESTED. |
| S2-06 / TSK-02-05 | US-06 - tồn kho real-time | Hỗ trợ Kiên | IMPLEMENTED, UNIT TESTED, POSTGRESQL TESTED trên view baseline `stock_availability`. |
| S2-07, S2-10, S2-13 | Kiểm thử POS, receipt, stock | Hỗ trợ QA | Có unit/controller/integration evidence; chưa có QA/PO sign-off. |
| S2-12 | Staging/CI | Hỗ trợ Kiên | Không thay đổi trong diff TV4; workflow trên `main` chưa được dùng làm bằng chứng deploy Staging của TV4. |
| S2-14, S2-18, S2-20 | Refinement và Quality Gate | Hỗ trợ review | PENDING BA/PM/QA; tài liệu DB không thay thế phê duyệt sản phẩm. |

## 2. Ma trận truy vết yêu cầu

| User Story | Acceptance Criteria chính | Backend | Frontend | DB contract | Tests | Kết luận |
|---|---|---|---|---|---|---|
| US-04 / FR05 | Chọn Serial đúng store; hai POS không bán trùng; giữ/giải phóng an toàn; chỉ PAID khi thanh toán đã xác minh | `PosDeviceSelectionDataAccess` và `PosDeviceSelectionService` gọi đúng function dự kiến; mapping `55P03` an toàn | Chưa mở thao tác cart/lock/payment vì REST contract D52 chưa khớp SQL | SQL Sprint 2 có function dự kiến nhưng artifact cài đặt FAIL và thiếu runtime grant | 8 unit tests PASS; PostgreSQL NOT TESTED | PARTIAL / BLOCKED |
| US-05 / FR06 | Chỉ in receipt của đơn PAID; dữ liệu khớp đơn; lỗi in không hủy PAID; không giảm kho khi in lại | `GET /api/v1/pos/orders/{orderId}/receipt`, `sales.read` theo store, 404 concealment, 409 với đơn chưa PAID | Receipt preview và `window.print()`; không ghi nhận “đã in” | Đọc `sales_orders`, `sales_order_items`, `order_devices`, `devices` | Unit/controller PASS; PostgreSQL PAID/DRAFT/missing/cross-store/no-permission/inactive PASS | IMPLEMENTED trong phạm vi browser preview |
| US-06 / FR07 | Tồn đúng store; AVAILABLE/RESERVED/accounting rõ; không lộ cross-store; không trả stale | `GET /api/v1/inventory/stock`, permission lọc trong SQL | Chưa có màn hình stock riêng trong diff Sprint 2 | Dùng view baseline `sasms.stock_availability`; view Sprint 2 mới chưa cài được | Unit/controller PASS; PostgreSQL available/reserved/sold/cross-store/no-permission/inactive PASS | IMPLEMENTED, chưa chứng minh view Sprint 2 mới |
| US-07 / FR08 | Gợi ý AI tích hợp POS và có fallback | Không sửa module AI của TV5 | Chưa tích hợp widget | Artifact DB fallback cùng file SQL đang FAIL khi cài | NOT TESTED bởi TV4 | BLOCKED |

## 3. Source thực tế

### 3.1 Inventory stock

```text
GET /api/v1/inventory/stock?warehouse_id={uuid}&sku={text}&limit=20&offset=0
Authorization: Bearer {JWT}
```

- Actor chỉ lấy từ `TrustedActorAuthenticationToken`.
- SQL gọi `sasms.has_permission(actor, 'inventory.read', warehouses.store_id)` trước khi trả dòng.
- Kho ngoài scope và người dùng không có role trả danh sách rỗng, không lộ dữ liệu store khác.
- `sellable_count = IN_STOCK`, `reserved_count = RESERVED`, `accounting_stock = sellable + reserved`; SOLD bị loại khỏi accounting stock.
- Mỗi response tạo `snapshot_at` mới sau truy vấn; không cache hoặc fallback stale.
- `limit` giới hạn 1..100, `offset >= 0`, SKU filter được bind parameter.

### 3.2 Receipt và browser print

```text
GET /api/v1/pos/orders/{orderId}/receipt
Authorization: Bearer {JWT}
```

- SQL lọc `sales.read` theo `sales_orders.store_id`.
- Missing và ngoài scope đều trả 404; đơn không PAID trả 409.
- Response không chọn customer, payment, audit; serial chỉ trả `****` cộng bốn ký tự cuối.
- Tiền VND được trả bằng số nguyên VND trong trường DRAFT `total_minor`/`price_minor`. Lỗi cũ nhân 100 ở backend rồi chia 100 ở frontend đã được sửa.
- Browser `window.print()` chỉ mở print dialog; đóng/lỗi dialog không sửa order, payment hoặc inventory.
- Đây không phải hóa đơn điện tử đã phát hành và không phải bằng chứng máy in vật lý đã in.

### 3.3 POS Serial selection nội bộ

Đã tạo tầng nội bộ, chưa mở REST endpoint:

```text
lockDevice(storeId, orderId, orderItemId, serial, actorId) -> deviceId
releaseDevice(orderId, deviceId, actorId) -> boolean
```

- `lockDevice` gọi đúng `sasms.find_and_lock_device_for_pos(uuid, uuid, uuid, text, uuid)` bằng bound parameters.
- Service không trim/uppercase Serial, không tự tạo actor/order và không retry lỗi lock.
- `releaseDevice` bọc lời gọi function bằng điều kiện `has_permission(actor, 'sales.checkout', order.store_id)` trong cùng SQL statement, vì function DB hiện không dùng `p_actor_id` để kiểm quyền.
- Không có Controller/DTO mới vì D52 DRAFT dùng resource reservation/idempotency khác với function SQL order-driven.

## 4. Kiểm toán DB Sprint 2

Đã thử nạp nguyên file `database/sprint2/realtime_stock_and_locking.sql` vào container cô lập `tv4_s1_it_db` trên PostgreSQL 16.15. Kết quả thực tế:

```text
ERROR: column p.price does not exist
LINE 12: p.price,
```

Nguyên nhân: schema thật dùng `products.list_price`, trong khi view `v_realtime_inventory` tham chiếu `p.price`. Vì lỗi xảy ra trước phần tạo function, ba artifact sau không tồn tại trong DB test:

- `sasms.v_realtime_inventory`;
- `sasms.find_and_lock_device_for_pos(uuid,uuid,uuid,text,uuid)`;
- `sasms.release_device_lock(uuid,uuid,uuid)`.

Các vấn đề DB/contract cần Architect xử lý, TV4 không tự sửa:

1. Sửa tên cột bằng change đã review của Kiên và chạy lại script từ đầu trong transaction/migration có version.
2. `database/runtime-roles.sql` chưa grant `SELECT` view mới và `EXECUTE` các function Sprint 2 cho `sasms_app`.
3. `release_device_lock` nhận `p_actor_id` nhưng không dùng để authorization/audit; cần kiểm tra quyền ở DB hoặc duyệt rõ application guard.
4. D34 quy định hold 10 phút, DS2/SQL dùng draft lease 15 phút.
5. Kịch bản SQL hiện gọi hai POS tuần tự trong một transaction; nó kiểm tra conflict theo row đã gắn, chưa phải bằng chứng hai connection tranh chấp đồng thời thật.
6. D52 `/pos/reservations` dùng `reservation_id` và idempotency key, còn SQL dùng order/order-item/serial. Cần API freeze trước khi mở endpoint.

Không sửa migration/schema/SQL của Kiên để ép test PASS. Ba index `CREATE INDEX IF NOT EXISTS` đã được tạo trong DB cô lập trước điểm lỗi; không có bảng/dữ liệu nghiệp vụ bị xóa hoặc reset.

## 5. Security review

- Endpoint stock và receipt không nằm trong permit-list public; phải có JWT hợp lệ.
- JWT được kiểm chữ ký, issuer, audience, thời hạn và map `issuer + subject` sang actor nội bộ; inactive user bị từ chối trước controller.
- Không dùng `X-Actor-Id`, actor từ body/header hoặc role claim chưa kiểm chứng.
- Store scope được thực thi trong SQL, không tải dữ liệu store khác rồi lọc ở Java.
- Receipt không trả customer/payment/audit hoặc Serial đầy đủ.
- `55P03` được map thành HTTP 409 `DEVICE_LOCKED`, retryable; không lộ SQL/stack trace.
- Không hardcode token, password, API key hoặc signing key. Integration password sinh ngẫu nhiên trong process và login tạm bị xóa sau test.
- Login production E2E vẫn NOT TESTED vì chưa có identity provider chính thức.

## 6. Bằng chứng kiểm thử ngày 13/11/2026

### Backend regression

```powershell
cd backend
.\mvnw.cmd clean test
```

Kết quả: `Tests run: 148, Failures: 0, Errors: 0, Skipped: 8`, `BUILD SUCCESS`.

- 7 test trong `Sprint1PostgreSqlIntegrationTest` skip khi biến bật integration không có.
- 1 JDBC smoke test skip theo cơ chế opt-in.
- Cảnh báo còn lại: Mockito dynamic agent trên Java 26; cần xử lý trước khi JDK chặn dynamic agent mặc định.

### PostgreSQL integration cô lập

```powershell
.\scripts\run_s1_it.ps1
```

Môi trường được xác minh trước khi ghi fixture:

- container `tv4_s1_it_db`, PostgreSQL 16.15, healthy;
- loopback `127.0.0.1:15432`;
- volume riêng `tv4_s1_it_pgdata`;
- database `sasms`, V001/V002;
- runtime login tạm chỉ kế thừa `sasms_app`, admin login tạm kế thừa owner; cả hai bị xóa sau test.

Kết quả: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

Đã kiểm chứng Catalog CRUD/revision/duplicate, Inventory batch/idempotency/rollback, QR store scope, stock available/reserved/sold, receipt PAID/DRAFT/missing/cross-store/no-permission/inactive. Cleanup chỉ thao tác UUID/prefix fixture `11111111-...` của TV4 và không reset container/volume.

POS Serial locking trên function Sprint 2: **NOT TESTED / BLOCKED** vì artifact SQL không cài được như mục 4.

### Frontend

```powershell
cd frontend
npm ci
npm test
npm run build
npm audit --json
```

- Tests: `13 passed, 0 failed`.
- Production build: PASS, Vite 5.4.21; bundle JS 619.86 kB, có warning chunk > 500 kB.
- Camera thật/mobile: NOT TESTED trong lần này.
- Browser print dialog/máy in thật: NOT TESTED.
- Accessibility và visual QA trên trình duyệt thật: NOT TESTED.

### npm audit

Còn 2 finding trong development tooling:

- `vite` direct dev dependency: HIGH aggregate.
- `esbuild` transitive qua Vite: MODERATE.

`npm audit` chỉ đưa ra bản sửa bằng Vite 8.3.8 (major). Không chạy `npm audit fix --force` và không tự nâng major ngoài baseline. Dev server chỉ nên bind loopback/trusted network; Architect/PM cần duyệt kế hoạch nâng Vite.

## 7. Lỗi đã sửa trong phạm vi TV4

1. Payload tiền VND bị nhân 100 ở backend và chia 100 ở frontend: sửa về số nguyên VND nhất quán.
2. SQLSTATE `55P03` trước đây rơi vào 503 chung: map thành 409 `DEVICE_LOCKED` retryable.
3. Bổ sung receipt integration cho PAID, DRAFT, missing, cross-store, actor thiếu quyền, user inactive và kiểm tra read-only.
4. Bổ sung stock integration cho store có/không có kết quả, cross-store, thiếu quyền, inactive, RESERVED và SOLD.
5. Fixture stock RESERVED ban đầu làm cleanup bị khóa và để lại outbox event: thêm restore trong `finally` và cleanup exact aggregate UUID; chạy lại 7/7 PASS.
6. Cập nhật nhánh bằng checkpoint `git stash -u`, fast-forward tới `origin/main`, rồi `stash pop`; toàn bộ thay đổi local được bảo toàn, không có duplicate Sprint 1 commit.

## 8. Cách chạy và demo

Backend:

```powershell
cd backend
$env:SASMS_DB_URL='jdbc:postgresql://127.0.0.1:5432/sasms'
$env:SASMS_DB_USERNAME='<runtime-login>'
$env:SASMS_DB_PASSWORD='<secret-from-local-env>'
$env:SASMS_JWT_ISSUER='<approved-issuer>'
$env:SASMS_JWT_AUDIENCE='<approved-audience>'
$env:SASMS_JWT_JWKS_URI='<approved-jwks-uri>'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=postgresql'
```

Frontend:

```powershell
cd frontend
npm ci
$env:VITE_API_BASE_URL='http://localhost:8080/api'
npm run dev
```

Demo an toàn hiện có:

1. Dùng JWT do IdP test/được duyệt cấp, không dán token vào source.
2. Gọi stock với actor có `inventory.read` đúng store và so sánh actor ngoài scope.
3. Gọi receipt của order PAID, DRAFT, missing và cross-store.
4. Mở browser print preview; không tuyên bố máy in thật hoặc e-invoice đã PASS.
5. Chạy `.\scripts\run_s1_it.ps1` để tái hiện 7 integration cases trên DB cô lập.

Chưa thể demo production payment hoặc POS Serial lock qua HTTP cho tới khi các blocker DB/API được xử lý.

## 9. Điểm còn BLOCKED/PENDING

### BLOCKED

- DB Sprint 2 không cài được do `p.price`/`list_price`; function/view chưa tồn tại thực tế.
- Runtime grant cho view/function Sprint 2 chưa có.
- Payment provider, callback signature, verification, idempotency, currency semantics, retry/refund/cancel chưa được BA/Architect/PM duyệt.
- REST contract reservation/order hiện không khớp SQL function; không mở endpoint tạm.
- AI engine không thuộc phạm vi TV4; widget frontend đã nối với cart nhưng chưa có login/store/warehouse production để kiểm thử E2E.

### PENDING review

- BA/Architect: `sku_id` trong D52 đang mang `products.id`; xác nhận tên/semantics.
- BA/Finance: xác nhận trường `*_minor` cho VND là số nguyên VND hoặc đổi tên contract.
- Security/PO: duyệt quy tắc che Serial bốn ký tự cuối.
- Architect/DB: 10 phút hay 15 phút; authorization/audit của release; runtime grants; migration version cho Sprint 2.
- QA: browser/mobile/camera/print/accessibility, true two-connection concurrency, performance POS <2s và Zero Bug P1/P2.
- PM/PO: review/approval các AC và Quality Gate; báo cáo này không tự ghi APPROVED.

## 10. Checklist trước Draft PR

- [x] Nhánh Sprint 2 đã fast-forward lên `origin/main`; Sprint 1 đã có trong main.
- [x] Diff hiện tại chỉ chứa source/test/report TV4 Sprint 2.
- [x] Không sửa migration/schema/seed/DB artifact của Kiên.
- [x] Backend regression PASS 148 tests, 8 skipped theo opt-in.
- [x] PostgreSQL baseline integration PASS 7/7 trên DB cô lập.
- [x] Frontend tests 13/13 và production build PASS.
- [x] Không có secret thật; `target`, `node_modules`, `dist` đều ignored.
- [x] Không commit/push/merge và chưa mở PR.
- [ ] Kiên sửa/duyệt DB Sprint 2 artifact và runtime grants.
- [ ] Huy/An chốt REST/payment/VND/SKU contracts.
- [ ] QA/PO review bằng chứng và demo thật.

**Kết luận:** Có thể tạo **Draft PR nhắm `main`** để review phần US-05, US-06 và POS selection nội bộ. Không được đánh dấu Ready/Accepted hoặc tuyên bố US-04 end-to-end hoàn thành khi DB Sprint 2, payment và REST contract vẫn bị chặn.

## 11. Cập nhật S2-02 - giỏ hàng POS ngày 16/11/2026

### Phạm vi đã triển khai

- `PosCart` tìm sản phẩm active qua Catalog API, sau đó kiểm tra `sellable_count` qua Stock API theo đúng `warehouse_id` trước khi cho thêm hàng.
- Giỏ cục bộ hỗ trợ thêm, xóa, đổi số lượng, giới hạn theo tồn đã biết và mức bảo vệ giao diện 99; tổng dự kiến dùng số nguyên VND, không tự làm tròn.
- Các trạng thái chưa đăng nhập, thiếu kho, loading, empty, forbidden và lỗi API có thông báo riêng; lỗi backend không được đưa thẳng ra màn hình.
- `PosPage` là nơi sở hữu cart cục bộ hoặc nhận cart có kiểm soát qua props. `CrossSellWidget` chỉ nhận `productId` và `sku`; đề xuất AI phải qua lớp đối chiếu Catalog/Stock hiện có trước khi người dùng thêm vào cart.
- Không có lời gọi tạo order, payment, giữ Serial hay ghi tồn kho. Nút thanh toán bị khóa và ghi rõ đây là cart frontend dự kiến.
- CSS có layout desktop/mobile cho quầy; chưa thay thế visual/accessibility QA trên thiết bị thật.

### Hợp đồng và giới hạn

- Catalog/Stock/JWT hiện có trong source là căn cứ triển khai. `sku_id` của Stock hiện mang `products.id`; đây vẫn là tên trường cần BA/Architect xác nhận.
- D52 reservation/order/payment vẫn là DRAFT và chưa khớp hoàn toàn SQL Sprint 2. Frontend không tự tạo hợp đồng thay thế.
- `App.jsx` chưa có login, store, warehouse hoặc cart production. Vì vậy khi chạy app mặc định, tra cứu Catalog/Stock và AI giữ trạng thái khóa an toàn; không hardcode token hoặc fixture demo.
- Chọn/giữ Serial, tạo order nguyên tử, chống tranh chấp hai POS, payment và cập nhật SOLD là **BLOCKED/NOT TESTED**, không được suy ra từ cart frontend.

### Bằng chứng kiểm thử

```powershell
cd frontend
npm ci
npm test
npm run build
```

- `npm ci`: PASS, cài 92 package theo lockfile; npm báo 2 vulnerability (1 moderate, 1 high), chưa dùng `npm audit fix --force` hoặc nâng major.
- `npm test`: PASS, 38/38 test, 0 fail, 0 skip. Test mới phủ thêm/xóa/sửa, dữ liệu không hợp lệ, tồn/giới hạn số lượng, tổng VND, JWT thiếu/hết hạn, HTTP 401/403, lỗi API, scope kho, context switching, stale Catalog/Stock/receipt response và payload cart chuyển sang AI.
- `npm run build`: PASS, Vite 5.4.21 build 331 module. Cảnh báo còn lại: bundle JS 636.11 kB trước gzip, lớn hơn ngưỡng 500 kB; không chặn build.
- `git diff --check`: PASS trước khi tạo commit.
- E2E login/cart, browser responsive thật, thanh toán, Serial locking và ghi tồn: **NOT TESTED**.

### Hardening context trên PR #23

- Cart và kết quả tìm kiếm được gắn với bộ ba session/store/warehouse đã tạo ra dữ liệu. Đổi bất kỳ giá trị nào sẽ hiển thị rỗng ngay và xóa cart cục bộ qua effect; render bình thường với cùng context giữ nguyên dữ liệu.
- Mỗi lượt tìm kiếm hủy request trước bằng `AbortController` và dùng generation gate. Response chỉ được cập nhật UI nếu cả generation lẫn context vẫn hiện hành.
- HTTP 401, bao gồm token hết hạn, xóa cart cục bộ theo chính sách fail-closed. Lỗi thông thường hoặc 403 không tự biến thành thao tác payment/inventory và không giữ kết quả tìm kiếm cũ.
- Receipt request truyền `AbortSignal` xuống HTTP client và dùng cùng generation/context guard. Đổi session/store/warehouse sẽ ẩn receipt cũ ngay, hủy request đang chạy và bỏ qua cả success/error trả về muộn của context trước.

### Cách demo an toàn

1. Cấp `accessToken`, `storeId`, `warehouseId` từ phiên đăng nhập đã xác thực vào `PosPage`; không đưa token vào source.
2. Tìm theo tên/SKU và quan sát request Catalog rồi Stock của đúng warehouse.
3. Thêm, đổi số lượng, xóa sản phẩm; xác nhận không thể vượt `sellable_count` đã đọc.
4. Khi cart có dữ liệu và đủ context, kiểm tra widget AI nhận danh sách sản phẩm. Mọi đề xuất vẫn phải được Catalog/Stock xác minh lại.
5. Không dùng demo này để tuyên bố order/payment/Serial/inventory mutation đã PASS.

## 12. Cập nhật thực thi sau audit ngày 16/11/2026

Phần này là trạng thái mới nhất và được ưu tiên khi có khác biệt với bằng chứng lịch sử ở các mục trước. Không có nội dung nào dưới đây là xác nhận nghiệm thu của QA hoặc PO.

### 12.1 Phạm vi nhánh và bảo toàn D53

- Bảy file giao diện D53 đã được sao lưu ngoài repository bằng bản sao nguyên file và patch trước khi commit.
- Commit D53 local riêng: `4e16d8c feat(d53): refine POS QR and serial reference UI` trên nhánh `feat/tv4-d53-figma-ui`.
- Nhánh cập nhật integration/evidence này được tạo trực tiếp từ `22a5b568df74f8841a0936773b683a457f3d02e5`, vì vậy không chứa commit D53 và không làm thay đổi Draft PR #23.
- D53 chỉ thay shell điều hướng, bố cục và trạng thái placeholder. Không đổi URL API, JWT header, payload, checkout, payment hoặc business rule.

### 12.2 Requirement → Code → Test → Evidence

| Requirement | Code hiện có | Test mới nhất | Evidence và giới hạn |
|---|---|---|---|
| JWT → trusted actor | `SecurityConfiguration`, `JdbcActorIdentityResolver`, `TrustedActorAuthenticationToken` | Backend regression PASS | Có unit/integration cấu hình security; IdP production và login browser thật chưa chạy. |
| Catalog search | `ProductCatalogController`, `ProductCatalogQueryService`, `PosCart` | Backend và frontend PASS | Frontend chỉ gọi khi có token và warehouse hợp lệ; `App.jsx` chưa cấp context production. |
| Stock theo warehouse/store | `InventoryStockController`, `JdbcInventoryStockDataAccess`, `posCartApi.js` | Backend và frontend PASS | SQL kiểm `inventory.read` theo store. PostgreSQL không được chạy lại trong phiên này. |
| Cart POS | `PosPage`, `PosCart`, `posCartModel`, `posCartContext` | Frontend PASS | Thêm/xóa/sửa số lượng, giới hạn tồn đã biết, tổng VND và stale-response guard đã test; chưa tạo order. |
| Receipt PAID | Receipt controller/service/data và `getReceipt` | Backend và frontend PASS | Chỉ đọc receipt có sẵn; chưa có checkout/payment end-to-end để tạo order PAID. |
| QR lookup | `QrCameraScanner`, `qrApi`, QR backend controller/service/data | Frontend và backend PASS | Đã bổ sung AbortSignal cùng generation/auth-context guard; camera/mobile thật chưa test. |
| Serial selection | `PosDeviceSelectionService`, `JdbcPosDeviceSelectionDataAccess` | Unit test PASS | Chưa có REST Controller/DTO được duyệt; chưa nối vào cart. |
| Checkout/payment | Chưa có luồng production hoàn chỉnh | NOT TESTED | D52/SQL/payment provider chưa được chốt; frontend không tự tạo PAID hoặc SOLD. |
| Receipt print | `window.print()` và CSS print | Build PASS | Chỉ là browser print preview; máy in nhiệt 80mm và QR bảo hành chưa test/triển khai. |
| AI cross-sell frontend | `CrossSellWidget`, `aiPosService` | Frontend PASS | Candidate được đối chiếu lại Catalog/Stock. AI backend/provider và security không thuộc thay đổi TV4 này. |

### 12.3 Bằng chứng kiểm thử mới

Thực hiện ngày 16/11/2026 trên nhánh `feat/tv4-s2-integration-evidence`:

```powershell
cd backend
.\mvnw.cmd clean test

cd ..\frontend
npm.cmd test
npm.cmd run build

cd ..
git diff --check
```

- Backend regression: **PASS** — `150` tests, `0` failure, `0` error, `8` skipped, `BUILD SUCCESS`.
- Tám test skipped gồm bảy PostgreSQL integration test opt-in và một JDBC smoke test opt-in; không được tính là integration PASS mới.
- Frontend sau QR context hardening: **PASS** — `40/40`, không fail/skip.
- Production build: **PASS** — Vite `5.4.21`, `331` modules; bundle JS `637.55 kB`, còn cảnh báo chunk lớn hơn `500 kB`.
- `git diff --check`: **PASS**; cảnh báo LF/CRLF không phải whitespace error.
- Visual/mobile browser QA: **NOT RUN** vì phiên Computer Use không mở được trình duyệt; build không thay thế visual QA.

### 12.4 PostgreSQL: bằng chứng lịch sử và trạng thái lần này

- Bằng chứng lưu tại `database/evidence/sprint2/summary.json` ngày 13/11/2026 ghi `status: passed`, `33` assertions và cleanup fixture cô lập.
- Evidence đã ghi hai kết nối POS độc lập, lock conflict, retry sau release, commit/rollback, stock transition và cleanup.
- SQL hiện tại dùng `products.list_price`; nhận định lịch sử rằng artifact vẫn lỗi `p.price` không còn phản ánh source/evidence mới nhất.
- Trong phiên 16/11/2026, Docker daemon không hoạt động nên container `tv4_s1_it_db`, volume `tv4_s1_it_pgdata` và cổng `15432` không thể được xác minh lại. Vì vậy PostgreSQL integration lần này là **NOT RUN**, không ghi PASS mới và không đụng database/volume.

### 12.5 Blocker cần đúng owner xử lý

| Blocker | File/hàm liên quan | Người cần xử lý/quyết định | Điều kiện gỡ blocker |
|---|---|---|---|
| Chưa có Serial REST contract | `PosDeviceSelectionService.lockDevice/releaseDevice`; chưa có controller | Huy/An chốt API, Kiên xác nhận DB, TV4 triển khai sau duyệt | Thống nhất resource, idempotency, status/error và permission. |
| Checkout/order chưa chốt | D52 DRAFT và SQL order/reservation đang khác mô hình | BA/PO/Architect/DB | Chốt request/response, transaction boundary, release/timeout và optimistic conflict. |
| VietQR/payment | Không có provider/callback production trong phạm vi TV4 | BA/Architect/PM và owner payment | Chọn provider, signature verification, idempotency, retry/refund/cancel và nguồn xác nhận PAID. |
| AI API đang có phạm vi public/prototype | `SecurityConfiguration`, `AiCrossSellController`, endpoint simulate | Vũ (owner AI) và Architect/Security | Chốt permission, bảo vệ endpoint mutation và provider/fallback thật. TV4 không sửa module này. |
| Login/context production | `App.jsx` chưa cấp `accessToken`, `storeId`, `warehouseId` | Owner Auth/S1-11, PM/Architect | Có IdP/JWKS/issuer/audience và trusted store/warehouse context. |
| Máy in nhiệt 80mm và QR bảo hành | Receipt UI chỉ gọi `window.print()` | QA/PO/Architect và owner print | Chốt template/payload QR, cơ chế print và test thiết bị thật. |
| D53 visual/mobile acceptance | Manifest/handoff/B10 checklist còn PENDING/NOT RUN | QA và PO | Review frame, mobile C–F, B10, camera và lưu bằng chứng trực quan. |

### 12.6 Hướng dẫn demo an toàn

1. Khởi động backend với profile PostgreSQL và cấu hình JWT đã được duyệt; không ghi secret vào source.
2. Khởi động frontend với `VITE_API_BASE_URL=http://localhost:8080/api`.
3. Cấp `accessToken`, `storeId`, `warehouseId` từ lớp login thật vào `PosPage`; app mặc định không hardcode các giá trị này.
4. Tìm sản phẩm, kiểm tra request Catalog rồi Stock đúng warehouse, thêm/xóa/đổi số lượng và quan sát tổng VND.
5. Đổi token hoặc warehouse để xác nhận cart, kết quả tìm kiếm và receipt cũ bị xóa/ẩn theo fail-closed.
6. Với QR, bắt đầu request rồi đổi token hoặc sửa payload; response cũ phải bị abort hoặc bỏ qua.
7. Chỉ mở receipt của order PAID có sẵn và dùng browser print preview. Không gọi đây là checkout, VietQR, e-invoice hoặc máy in nhiệt đã PASS.

### 12.7 Checklist nghiệm thu còn lại

- [x] Backend regression mới nhất PASS 150 tests, 8 opt-in skipped.
- [x] Frontend tests mới nhất PASS 40/40 và build production PASS.
- [x] Catalog/Stock/Cart/receipt và QR stale-session guard không dùng token/store/fixture hardcode.
- [x] D53 và integration/report nằm ở hai nhánh/commit local riêng.
- [ ] Chạy lại PostgreSQL integration trên đúng container/volume cô lập khi Docker hoạt động.
- [ ] Tích hợp login/IdP production và test JWT → Catalog/Stock → Cart trên browser.
- [ ] Chốt và triển khai Serial REST cùng Checkout/payment contract.
- [ ] Test camera mobile, máy in nhiệt 80mm, QR bảo hành và concurrency qua HTTP.
- [ ] QA/PO review và ghi evidence; không tự đánh dấu ACCEPTED.

**Kết luận cập nhật:** Phần frontend độc lập đã ổn định hơn và có regression evidence mới. Sprint 2 vẫn **PARTIAL/BLOCKED** tại Serial REST, Checkout, VietQR/payment, login production, AI security và thiết bị thật; chưa đủ điều kiện nghiệm thu end-to-end.

## 13. Xác minh nhánh, backup D53 và DB ngày 16/11/2026

### 13.1 Quan hệ nhánh và base PR an toàn

```text
origin/main e2db1a7
└─ feat/tv4-s2-pos-cart 22a5b568
   ├─ feat/tv4-d53-figma-ui 4e16d8c
   └─ feat/tv4-s2-integration-evidence 569c72a
```

- `feat/tv4-s2-pos-cart` có đúng ba commit riêng so với `origin/main`: `09c4f57`, `db5b2c5`, `22a5b568`.
- D53 và integration là hai nhánh anh em, cùng có merge-base `22a5b568`; không nhánh nào chứa commit riêng của nhánh còn lại.
- Khi PR #23 còn mở, base an toàn cho D53 và integration đều là `feat/tv4-s2-pos-cart`. Không dùng D53 làm base của integration hoặc ngược lại.
- Sau khi PR #23 merge, tạo nhánh sạch từ `origin/main` mới và chỉ cherry-pick commit riêng cần thiết. Không mở PR D53/integration nhắm `main` trước khi loại được ba commit PR #23 khỏi diff.

### 13.2 Backup D53 bền vững

Backup mới được lưu ngoài repository tại:

`D:\SASMS_TV4_BACKUPS\D53\4e16d8c_20261116`

| File | SHA-256 |
|---|---|
| `0001-feat-d53-refine-POS-QR-and-serial-reference-UI.patch` | `37B676DA60602AA4ECBD15798C88BC5D3AB5077D00F42B47FE02EACAC0676ED5` |
| `d53-branch-4e16d8c.bundle` | `F664124882380193C0627F1580FD83EF2BEFA93E4A93C4BD5757BEE941E453AD` |
| `d53-source-4e16d8c.zip` | `D34BE0ED59703EDBD013D1BF95DA2C4BB33F80B7538FB1A2E1A538D164ECDD00` |

`git bundle verify` xác nhận bundle chứa đúng ref `feat/tv4-d53-figma-ui` tại `4e16d8c` và có complete history. Backup tạm cũ không bị xóa hoặc ghi đè.

### 13.3 PostgreSQL integration lần kiểm tra này

- Kiểm tra ban đầu ngày 16/11 là **NOT RUN** vì Docker daemon chưa hoạt động; trạng thái này được giữ trong evidence để không làm mất lịch sử.
- Ngày 17/11 đã tạo database mới/rỗng riêng bằng `postgres:16`, container `sasms_s2_tv4_once_20261117`, volume `sasms_s2_tv4_once_pgdata_20261117`, network riêng và loopback port `15434`.
- Trước `--apply`, truy vấn read-only xác nhận database `sasms_sprint2_validation`, owner `sasms_ci`, PostgreSQL `16.15`, `0` bảng ngoài system và chưa có schema `sasms`.
- Validator đúng `scripts/validate_sprint2.py` chạy với repository mount read-only và evidence ngoài repo: **PASS 33 assertions**, Sprint 1 regression bên trong **PASS 76 assertions**.
- Hai ca POS dùng PID `443/446` và `466/469`; overlap conflict `55P03`, retry sau release/rollback, commit/rollback và cleanup đều PASS.
- Hậu kiểm xác nhận không còn user/order/device fixture, payment trigger được bật lại. Container mới dừng sạch `exit=0`; container/volume cũ không bị sử dụng.
- Đây là bằng chứng DB cô lập, không phải HTTP/payment/UI E2E hoặc staging deployment.
- Log/lệnh/provenance chi tiết: `Sprint 2/Fullstack/TV4/evidence/SPRINT2_DB_VERIFICATION_20261116.md`.

### 13.4 Quyết định/liên hệ còn thiếu

| Người phụ trách | Quyết định hoặc bằng chứng cần bổ sung |
|---|---|
| Nguyễn Quang Huy — BA/PO | Chốt AC/DoR, semantics cart DRAFT/tồn khả dụng, lease, Serial REST, QR bảo hành và nghiệm thu demo. |
| Trần Trung Kiên — Architect/DBA | Xác nhận DB Sprint 2/runtime ACL, hợp đồng chọn/bỏ Serial, môi trường cô lập và staging S2-12. |
| Nguyễn Tiến Vũ — AI Lead | Bảo vệ API AI, kiểm `ai.use`/store scope, candidate từ Catalog/Stock thật, provider/fallback và log latency. |
| Trịnh Quốc An — PM | Chốt chiến lược PR, secret/hạ tầng/staging, CR/payment/provider và biên bản Quality Gate M3/M4. |
| Phạm Đại Phát — QA Lead | Chạy D72/D73/D76, E2E POS/QR/receipt, máy in 80mm, mobile/camera, hai POS thật và hiệu năng p95. |

## 14. Cập nhật hoàn thiện Serial REST API, CSS Browser Print 80mm & Test Coverage (17/11/2026)

### 14.1 Hoàn thiện REST API chọn/giải phóng Serial (US-04 / S2-03)

- **Endpoints triển khai**:
  - `POST /api/v1/pos/orders/{orderId}/items/{orderItemId}/devices`: Khóa thiết bị theo Serial cho dòng đơn hàng.
  - `DELETE /api/v1/pos/orders/{orderId}/devices/{deviceId}`: Giải phóng thiết bị đã khóa khỏi giỏ hàng POS.
- **Feature Flag mặc định OFF**:
  - Cấu hình tại `backend/src/main/resources/application.properties`:
    ```properties
    sasms.features.pos-serial-api.enabled=false
    ```
  - `PosDeviceSelectionController` gắn `@ConditionalOnProperty(prefix = "sasms.features.pos-serial-api", name = "enabled", havingValue = "true", matchIfMissing = false)`.
  - Khi chưa bật flag, Bean Controller hoàn toàn không được khởi tạo vào Spring context; request tới endpoint trả về `404 Not Found` theo nguyên tắc an toàn fail-closed.
- **Bảo mật, Identity & Scope**:
  - Không nhận `actor_id` từ client request body hoặc header; danh tính người thực hiện chỉ được giải quyết qua `TrustedActorAccess.actorId(authentication)`.
  - Lời gọi database gắn chặt với quyền `sales.checkout` theo đúng `store_id` của đơn hàng trong hàm DB `find_and_lock_device_for_pos` và câu truy vấn bảo vệ trong `release_device_lock`.
- **Ánh xạ lỗi chuẩn từ PostgreSQL (ApiExceptionHandler)**:
  - `55P03` (lock_not_available) $\rightarrow$ `409 Conflict`, code `DEVICE_LOCKED`, `retryable: true`.
  - `P0002` (no_data_found) $\rightarrow$ `404 Not Found`, code `NOT_FOUND`, `retryable: false`.
  - `23514` (check_violation / constraint) $\rightarrow$ `422 Unprocessable Content`, code `VALIDATION_FAILED`, `retryable: false`.
  - `42501` (insufficient_privilege) $\rightarrow$ `403 Forbidden`, code `FORBIDDEN`, `retryable: false`.
  - Lỗi database chung $\rightarrow$ `503 Service Unavailable`, code `DEPENDENCY_UNAVAILABLE`, `retryable: true`, tuyệt đối không rò rỉ câu lệnh SQL hay connection secret.

### 14.2 Hoàn thiện CSS Browser Print 80mm (US-05 / S2-04)

- **Quy chuẩn khổ in nhiệt 80mm (`@media print`)**:
  - Định dạng `@page { size: 80mm auto; margin: 2mm 4mm; }` phù hợp với máy in bill cuộn nhiệt tiêu chuẩn (chiều rộng in thực tế 72mm).
  - Áp dụng font monospace nét mảnh, tương phản cao, phân đoạn rõ ràng bằng đường kẻ `dashed` và `dotted`.
  - Tránh gãy trang giữa các dòng sản phẩm bằng thuộc tính `break-inside: avoid;`.
  - Ẩn triệt để toàn bộ thành phần không thuộc hóa đơn: Sidebar, Topbar, Header trang, Footer, Giỏ hàng POS, Widget gợi ý AI, Form tra cứu, Thông báo ghi chú nghiệp vụ, Thẻ trạng thái và Cảnh báo lỗi.
- **Giới hạn kiểm thử thiết bị**:
  - Đây là CSS chuẩn hóa cho hộp thoại in trình duyệt (`window.print()`).
  - **KHÔNG** tuyên bố đã kiểm chứng trên máy in nhiệt phần cứng thật (máy in vật lý và cổng giao tiếp máy in vẫn ghi nhận `NOT TESTED`).

### 14.3 Bằng chứng kiểm thử tổng thể mới nhất

```powershell
# Backend Test Suite
cd backend
.\mvnw.cmd test
# Kết quả: Tests run: 229, Failures: 0, Errors: 0, Skipped: 9, BUILD SUCCESS
# (Bao gồm 11 tests cho PosDeviceSelection và 32 tests mới cho PosOrder: PosOrderServiceTest [14 tests], JdbcPosOrderDataAccessTest [3 tests], PosOrderDisabledFeatureFlagIntegrationTest [2 tests], PosOrderSecurityIntegrationTest [13 tests] kiểm chứng cờ tính năng ON/OFF, xác thực JWT, bảo vệ scope, kiểm soát idempotency UUID/string, cạnh tranh đồng thời concurrent race, và ánh xạ mã lỗi SQLSTATE)

# Frontend Test Suite & Build
cd ..\frontend
npm.cmd test
# Kết quả: 47 tests PASS, 0 fail, 0 skip
# (Bao gồm 7 regression tests mới trong authContext.test.js chứng minh loại bỏ hoàn toàn việc đọc token từ URL/Storage, từ chối token giả lập mock/demo, fail-closed khi thiếu IdP, và coi token bộ nhớ là chưa xác thực cho tới khi backend phê chuẩn)
npm.cmd run build
# Kết quả: Vite 5.4.21 production build SUCCESS (332 modules)
```

- Không có lỗi `git diff --check`.
- Không sửa schema/migration/SQL của Kiên.
- Không sửa AI backend của Vũ.
- Không tự đánh dấu đơn hàng thành `PAID` hay `SOLD`.
- Hoàn toàn không đọc JWT/accessToken từ URL query string; không lưu token vào log; không dùng demo-token giả lập xác thực.
- Cú pháp CSS in hóa đơn `@page` được chuẩn hóa theo chuẩn W3C CSS Paged Media Level 3 (`size: auto; margin: 2mm 4mm;`), loại bỏ khai báo không hợp lệ `size: 80mm auto`.

### 14.4 Bảng trạng thái nghiệm thu Sprint 2 TV4

| Hạng mục | Trạng thái kỹ thuật | Người cần phê duyệt | Điều kiện nghiệm thu cuối |
|---|---|---|---|
| **S2-02 (POS Cart UI)** | Hoàn thiện Cart Catalog + Stock | Nguyễn Quang Huy (BA/PO) | Duyệt luồng nghiệp vụ quầy và giao diện giỏ hàng. |
| **S2-03 (Serial REST)** | Hoàn thiện Code + Tests (Flag OFF) | Trần Trung Kiên (DBA), Huy (BA) | Thống nhất hợp đồng D52 và bật feature flag. |
| **S2-04 (Hóa đơn 80mm)** | Hoàn thiện Print CSS 80mm (Browser Preview) | Phạm Đại Phát (QA), Huy (BA) | Kiểm thử trên máy in nhiệt 80mm vật lý thật (hiện ghi nhận `NOT TESTED HARDWARE`). |
| **S2-06 (Realtime Stock)** | Hoàn thiện API + SQL baseline | Trần Trung Kiên (DBA) | Xác nhận view realtime chính thức trên schema mới. |
| **S2-08 (Tích hợp AI)** | Hoàn thiện Widget + Auth Context | Nguyễn Tiến Vũ (AI Lead) | Bảo mật endpoint AI và kiểm thử latency p95. |
| **S2-12 (Staging/CI)** | Giữ nguyên quy trình chuẩn | Trịnh Quốc An (PM) | Review PR, hợp nhất branch và kích hoạt CI staging. |

## 15. Phân tích đối chiếu D34/D35/D40/D52 và V003–V005: Phân định Rõ ràng giữa API Đã Triển khai và Thiết kế Đề xuất

### 15.1 Phân định Hiện trạng Triển khai vs Thiết kế Đề xuất

> [!IMPORTANT]
> Nhằm bảo đảm tính trung thực tuyệt đối của báo cáo kỹ thuật Sprint 2:
> - **Tuyệt đối KHÔNG ghi nhận DONE hoặc E2E PASS** cho toàn bộ chu trình Checkout.
> - Bảng dưới đây phân định rõ các thành phần đã có mã nguồn & test thực tế với các thành phần mới dừng lại ở mức **Thiết kế Đề xuất (Proposed Architecture / Not Implemented)**.

| Thành phần API / Nghiệp vụ | Trạng thái thực tế | Bằng chứng kiểm thử | Ghi chú & Ranh giới |
|---|---|---|---|
| **`POST /api/v1/pos/orders/{orderId}/items/{itemId}/devices`** | **ĐÃ TRIỂN KHAI (Flag OFF)** | MockMvc + Security IT (10 tests PASS khi ON, 1 test 404 khi OFF) | Đã có Controller, DTO, Service, DataAccess, cờ tính năng `sasms.features.pos-serial-api.enabled=false`. |
| **`DELETE /api/v1/pos/orders/{orderId}/devices/{deviceId}`** | **ĐÃ TRIỂN KHAI (Flag OFF)** | MockMvc + Security IT PASS khi ON, 404 khi OFF | Đã hoàn thành giải phóng thiết bị khỏi giỏ hàng POS. |
| **`GET /api/v1/pos/orders/{orderId}/receipt`** | **ĐÃ TRIỂN KHAI** | 3 tests Controller, 2 tests DataAccess, 2 tests Service | Tra cứu biên nhận, che giấu đơn chưa trả tiền hoặc không thuộc quyền (full path đúng source). |
| **`GET /api/v1/inventory/stock`** | **ĐÃ TRIỂN KHAI** | Controller + DataAccess test PASS | Tra cứu tồn kho khả dụng theo từng kho hàng. |
| **`GET /api/v1/catalog/products/search`** | **ĐÃ TRIỂN KHAI** | Controller + DataAccess test PASS | Tra cứu danh mục sản phẩm đang kinh doanh. |
| **CSS Browser Print 80mm** | **ĐÃ TRIỂN KHAI (Preview)** | Vite Build SUCCESS + W3C Paged Media fix | Chuẩn hóa hộp thoại in trình duyệt, **chưa kiểm thử máy in nhiệt vật lý thật**. |
| **Bảo vệ Zero-Trust Auth Client** | **ĐÃ TRIỂN KHAI** | 47 tests frontend PASS | Từ chối token từ URL/Storage, loại bỏ token giả, fail-closed khi thiếu IdP. |
| **`POST /api/v1/pos/orders` (Tạo đơn DRAFT)** | **ĐÃ TRIỂN KHAI (Flag OFF)** | Service (12 tests) + DataAccess (3 tests) + Security IT (4 tests) PASS | Đã có Controller, DTO, Service, DataAccess, cờ tính năng `sasms.features.pos-order-api.enabled=false`. Giá từ DB, xác thực JWT, Idempotency-Key. **E2E NOT RUN**. |
| **`POST /api/v1/pos/orders/{orderId}/reserve`** | **ĐÃ TRIỂN KHAI (Flag OFF)** | Service + Security IT (6 tests: 401, 200, 404, 409, 422) PASS | Đã có Controller, Service, DataAccess gọi `sasms.reserve_order`, cờ tính năng `sasms.features.pos-order-api.enabled=false`. **E2E NOT RUN**. |
| **`POST /api/v1/pos/payments/initiate`** | **THIẾT KẾ ĐỀ XUẤT** | **CHƯA TRIỂN KHAI (NOT RUN)** | Chưa có REST Controller. Hiển thị VietQR không tự chuyển PAID. |
| **`POST /api/v1/pos/payments/webhook`** | **THIẾT KẾ ĐỀ XUẤT** | **CHƯA TRIỂN KHAI (NOT RUN)** | Chưa tích hợp webhook ký số HMAC với ngân hàng thật. |
| **`POST /api/v1/pos/orders/{orderId}/complete`** | **THIẾT KẾ ĐỀ XUẤT** | **CHƯA TRIỂN KHAI (NOT RUN)** | Chưa có REST Controller. Mới có hàm DB `sasms.complete_sale`. |
| **Background Lease Sweeper** | **THIẾT KẾ ĐỀ XUẤT** | **CHƯA TRIỂN KHAI (NOT RUN)** | Chưa cấu hình cron/scheduler để quét `expire_order_reservation`. |
| **Toàn bộ Luồng E2E Checkout** | **CHƯA ĐẠT (NOT E2E PASS)** | Không có HTTP E2E | Các endpoint trên chưa được liên kết thành luồng end-to-end hoàn chỉnh. |

### 15.2 Chi tiết Thiết kế Đề xuất Hợp đồng REST API Order-First

Mô hình Order-First chuẩn hóa đề xuất cho Sprint sau (hoặc giai đoạn hoàn thiện Checkout):

```
[POS Cart] --> (1) Tạo DRAFT Order --> (2) Khóa Serial (10m lease) --> (3) Reserve Order --> (4) Khởi tạo Payment (PENDING) --> (5) Xác nhận Thanh toán (CAPTURED) --> (6) Complete Sale (PAID)
```

1. **`POST /api/v1/pos/orders` (Tạo đơn hàng DRAFT - Đã triển khai dưới Flag OFF)**:
   - **Input**: `{ store_id, warehouse_id, items: [{ product_id, quantity }] }` và header `Idempotency-Key`.
   - **Xử lý**: Kiểm tra quyền `sales.checkout` tại `store_id`, kho thuộc store. Giá sản phẩm bắt buộc lấy từ CSDL Catalog (`products.list_price`), tuyệt đối không nhận giá hoặc tổng tiền từ client. Tạo bản ghi `sales_orders` với `status = 'DRAFT'`, `channel = 'POS'`. Tạo các dòng `sales_order_items`. Lúc này `hold_expires_at` là `NULL` (chưa kích hoạt lease).
   - **Quyền yêu cầu**: `sales.checkout` tại `store_id`.

2. **`POST /api/v1/pos/orders/{orderId}/items/{itemId}/devices` (Khóa thiết bị theo Serial - Đã triển khai dưới Flag OFF)**:
   - **Input**: `{ serial: "SN12345678" }`.
   - **Xử lý**: Gọi hàm CSDL `sasms.find_and_lock_device_for_pos`. Tại lần khóa đầu tiên, CSDL tự động gán `hold_expires_at = clock_timestamp() + 10 minutes`. Kiểm tra thiết bị `IN_STOCK` tại kho của đơn, khóa dòng bằng `FOR UPDATE NOWAIT`, chèn vào `order_devices` với `active = true`.
   - **Mã lỗi chuẩn**: `55P03` $\rightarrow$ 409 `DEVICE_LOCKED`, `P0002` $\rightarrow$ 404 `NOT_FOUND`, `23514` (hết hạn lease) $\rightarrow$ 422 `VALIDATION_FAILED`.

3. **`DELETE /api/v1/pos/orders/{orderId}/devices/{deviceId}` (Giải phóng thiết bị khỏi giỏ - Đã triển khai dưới Flag OFF)**:
   - **Xử lý**: Gọi hàm CSDL `sasms.release_device_lock`. Kiểm tra đơn còn `DRAFT`, cập nhật `order_devices.active = false` để trả thiết bị về trạng thái sẵn sàng cho quầy khác.

4. **`POST /api/v1/pos/orders/{orderId}/reserve` (Khóa tổng thể đơn hàng để chuẩn bị thanh toán - Đã triển khai dưới Flag OFF)**:
   - **Xử lý**: Gọi hàm CSDL `sasms.reserve_order(orderId, actorId)`. Kiểm tra `hold_expires_at > clock_timestamp()`. Chuyển trạng thái đơn sang `RESERVED`, cập nhật trạng thái thiết bị trong bảng `devices` thành `RESERVED`. Không gia hạn thêm thời gian giữ hàng.

5. **Khởi tạo Thanh toán & Xác nhận Thanh toán (Đề xuất)**:
   - **VietQR (Không tiền mặt)**: Khởi tạo thanh toán `PENDING`, hiển thị VietQR. Chờ webhook ký số HMAC từ ngân hàng/cổng thanh toán để chuyển sang `CAPTURED`. **Tuyệt đối không tự động chuyển PAID trên client.**
   - **Phương án Demo Tiền mặt (Cash Payment)**: Xem chi tiết tại Mục 15.3.

6. **`POST /api/v1/pos/orders/{orderId}/complete` (Hoàn tất giao dịch bán lẻ - Đề xuất)**:
   - **Xử lý**: Gọi hàm CSDL `sasms.complete_sale(orderId, actorId)`. Kiểm tra tổng tiền đã thu `sum(amount) == total_amount` (chính xác 100%, không cho phép sai lệch). Chuyển `sales_orders.status = 'PAID'`, cập nhật thiết bị thành `SOLD`, ghi nhận phiếu xuất kho, sinh chứng từ hóa đơn và đẩy event ra outbox.

7. **Cơ chế Hủy / Hết hạn tự động (Sweeper / Expire - Đề xuất)**:
   - Nếu sau 10 phút khách hàng không thanh toán và không có khoản thanh toán nào ở trạng thái `PENDING`/`CAPTURED`, hàm `sasms.expire_order_reservation` hoặc background sweeper sẽ gọi `sasms.cancel_order` để hủy đơn và giải phóng toàn bộ thiết bị về `IN_STOCK`.

### 15.3 Ba (03) Quyết định còn chặn Checkout & Phương án Demo Tiền mặt có xác nhận Server

Để luồng POS Checkout có thể chạy thông suốt từ đầu đến cuối (E2E), hiện tại có **3 rào cản kỹ thuật/nghiệp vụ chính** cần sự đồng thuận của BA (Huy), Architect/DBA (Kiên) và PM (An):

#### Quyết định 1: Chốt hợp đồng Order-First REST API (Tạo đơn DRAFT)
- **Hiện trạng mã nguồn**: TV4 đã chủ động triển khai đầy đủ mã nguồn backend cho API tạo đơn DRAFT (`POST /api/v1/pos/orders`) và khóa đơn hàng (`POST /api/v1/pos/orders/{orderId}/reserve`) theo đúng cấu trúc CSDL V004 của Kiên và D34/D52, kèm bộ unit/integration test toàn diện (27 tests PASS). Hiện tại các endpoint này được đặt dưới cờ tính năng an toàn `sasms.features.pos-order-api.enabled=false` (mặc định OFF).
- **Rào cản còn lại**: Cần sự phê duyệt chính thức từ BA Huy và DBA Kiên về hợp đồng DTO (`store_id`, `warehouse_id`, `items: [{product_id, quantity}]`) và thời điểm kích hoạt cờ tính năng để frontend có thể kết nối giao diện giỏ hàng vào luồng tạo đơn thật (**E2E NOT RUN**).
- **Hành động cần duyệt**: BA Huy và DBA Kiên phê chuẩn hợp đồng DTO và quy trình chuyển đổi đơn hàng sang RESERVED.

#### Quyết định 2: Quy ước Tính tiền & Làm tròn tiền tệ VND (Money Semantics)
- **Vấn đề**: D34 và D52 quy định đơn vị tiền tệ VND là số nguyên (`total_minor`), không có đơn vị lẻ như cent hay hào. Tuy nhiên, quy tắc tính thuế VAT (tính theo từng dòng sản phẩm rồi cộng lại hay tính trên tổng đơn hàng), làm tròn khi áp dụng mã giảm giá/chiết khấu chưa được chuẩn hóa giữa frontend và backend.
- **Rào cản**: Nếu công thức tính tổng tiền giữa frontend giỏ hàng và backend CSDL (`sales_orders.total_amount`) lệch dù chỉ 1 đồng, hàm `complete_sale` sẽ từ chối giao dịch (`sum(amount) < total_amount`).
- **Hành động cần duyệt**: BA Huy và PM An chốt công thức tính VAT và tổng đơn hàng nhất quán trên toàn hệ thống.

#### Quyết định 3: Quy trình Xác nhận Thanh toán & Phương án Demo Tiền mặt (Cash Payment) Server-side
- **Vấn đề**: Quét VietQR chỉ hiển thị mã thanh toán động. Hệ thống ngân hàng/cổng thanh toán thật (webhook IPN có ký số HMAC) chưa được kết nối trên môi trường staging. TV4 tuân thủ nghiêm ngặt nguyên tắc: **Không tự ý giả lập trạng thái PAID hoặc đánh lừa giao dịch thành công trên client**.
- **Phương án Demo Tiền mặt có xác nhận Server-side (Đề xuất BA/Architect/PM duyệt)**:
  - Để demo trọn vẹn luồng bán hàng POS mà không cần kết nối ngân hàng thật và không vi phạm quy tắc bảo mật:
  - **Luồng nghiệp vụ**:
    1. Khách hàng chọn thanh toán bằng **Tiền mặt** tại quầy POS.
    2. Thu ngân nhận tiền thật từ khách và bấm nút "Xác nhận thu tiền mặt" trên giao diện POS.
    3. Frontend gửi request: `POST /api/v1/pos/orders/{orderId}/payments/cash` kèm `{ "amount": totalAmount }`.
    4. **Xác thực Server-side**:
       - Backend trích xuất JWT của Thu ngân, kiểm tra quyền `sales.checkout` tại đúng `store_id` của đơn hàng.
       - Backend tạo bản ghi trong bảng `sasms.payments` với `payment_method = 'CASH'`, `status = 'CAPTURED'`, `amount = totalAmount`, ghi nhận `actor_id` của Thu ngân và thời điểm thu tiền vào audit trail.
       - Backend gọi hàm CSDL `sasms.complete_sale(orderId, actor_id)`. Hàm kiểm tra `captured == total_amount` (chính xác 100%), cập nhật `sales_orders.status = 'PAID'`, chuyển thiết bị trong `devices` từ `RESERVED` sang `SOLD`.
       - Backend trả về kết quả thành công kèm số hóa đơn (`invoice_number`).
    5. Frontend POS nhận phản hồi thành công, hiển thị biên nhận và mở hộp thoại in nhiệt 80mm.
  - **Ưu điểm**: 100% hợp lệ về mặt kiến trúc, tuân thủ chặt chẽ ràng buộc CSDL của Kiên, có bằng chứng server-side đầy đủ, loại bỏ hoàn toàn việc "fake PAID" trên frontend.

### 15.4 Ma trận Quyết định tổng hợp trình duyệt

| STT | Quyết định cần chốt | Các bên liên quan | Tác động kỹ thuật & Khuyến nghị TV4 | Trạng thái đề xuất |
|---|---|---|---|---|
| **DEC-S2-01** | Chuẩn hóa D52 sang **Order-First REST API** | Huy (BA), Kiên (DBA), Thái (Dev) | Chuẩn hóa endpoint tạo đơn DRAFT trước khi chọn Serial. Khớp 100% CSDL V004. | **ĐỀ XUẤT CHẤP THUẬN** |
| **DEC-S2-02** | Chốt thời hạn giữ hàng POS là **10 phút** | Huy (BA), Kiên (DBA) | Tuân thủ D34 BR-03 và hàm `sasms.order_hold_duration('POS')`. Hết 10 phút đơn DRAFT hết hạn. | **ĐỀ XUẤT CHẤP THUẬN** |
| **DEC-S2-03** | Quy ước giá tiền VND là **số nguyên VND** | Huy (BA), An (PM) | Trường `total_minor` / `price_minor` mang giá trị số nguyên VND. Thống nhất công thức tính VAT. | **ĐỀ XUẤT CHẤP THUẬN** |
| **DEC-S2-04** | Duyệt **Phương án Demo Tiền mặt** xác nhận Server | Huy (BA), Kiên (DBA), An (PM) | Cho phép demo hoàn tất đơn hàng POS qua tiền mặt có audit log thu ngân, không giả lập PAID. | **ĐỀ XUẤT CHẤP THUẬN** |
| **DEC-S2-05** | Phê duyệt V003–V005 và cấp quyền runtime role | Kiên (DBA), An (PM) | Chạy migration V003–V005 trên Staging và cấp quyền `GRANT EXECUTE` cho `sasms_app`. | **CHỜ DBA THỰC THI** |

### 15.5 Hướng dẫn Demo Sprint 2 thực tế

1. **Khởi động Backend (Spring Boot)**:
   ```powershell
   cd backend
   # Cấu hình biến môi trường kết nối database và JWT (nếu có)
   $env:SASMS_DB_URL = "jdbc:postgresql://127.0.0.1:5432/sasms"
   $env:SASMS_DB_USERNAME = "sasms_app"
   $env:SASMS_DB_PASSWORD = "..."
   # Để kiểm thử REST chọn Serial, bật cờ tính năng:
   $env:SASMS_FEATURES_POS_SERIAL_API_ENABLED = "true"
   .\mvnw.cmd spring-boot:run
   ```

2. **Khởi động Frontend (React / Vite)**:
   ```powershell
   cd frontend
   npm.cmd run dev
   # Truy cập ứng dụng tại http://localhost:5173
   ```

3. **Kịch bản Demo**:
   - **Kịch bản 1 (Fail-Closed mặc định)**: Mở trình duyệt không cấu hình phiên. Topbar hiển thị `CHƯA KẾT NỐI IdP / KHO (FAIL-CLOSED)`. Mọi thao tác tìm kiếm hay giỏ hàng đều an toàn ở trạng thái chưa xác thực, không phát sinh request trái phép.
   - **Kịch bản 2 (Phiên bộ nhớ chưa xác thực)**: Khi test runner hoặc launcher cung cấp cấu hình session trong bộ nhớ (`authSession`), topbar hiển thị `PHIÊN BỘ NHỚ (CHỜ BACKEND XÁC THỰC)`. **Nguyên tắc Zero-Trust**: Sở hữu token chuỗi phía client không phải là bằng chứng đã xác thực; Spring Security và PostgreSQL trên backend luôn quyết định quyền và phạm vi kho trên từng request. Mọi token cố tình truyền qua URL query parameters hoặc lưu trữ storage đều bị chặn và từ chối.
   - **Kịch bản 3 (Gợi ý AI)**: Khi giỏ có sản phẩm, widget AI hiển thị danh sách gợi ý chéo; người dùng bấm kiểm tra để đối chiếu tồn kho thực trước khi đưa vào giỏ.
   - **Kịch bản 4 (In biên nhận 80mm)**: Tra cứu đơn hàng đã thanh toán (PAID) và bấm "Mở bản xem trước in". Hộp thoại in của trình duyệt hiển thị phiếu in cuộn 80mm chuẩn nhiệt (72mm nội dung, phân cách nét đứt, ẩn mọi thanh điều hướng).

## 16. Triển khai Backend Order-First DRAFT Order & Reserve API (17/11/2026)

### 16.1 Hiện trạng & Ranh giới kỹ thuật
- **Mã nguồn đã triển khai**:
  - `POST /api/v1/pos/orders`: Tạo đơn hàng DRAFT và các dòng `sales_order_items`.
  - `POST /api/v1/pos/orders/{orderId}/reserve`: Khóa tổng thể đơn hàng để chuẩn bị thanh toán qua hàm CSDL `sasms.reserve_order(orderId, actorId)`.
- **Cờ tính năng (Feature Flag) mặc định OFF**:
  - Cấu hình tại `backend/src/main/resources/application.properties`:
    ```properties
    sasms.features.pos-order-api.enabled=false
    ```
  - Khi cờ mang giá trị `false` (hoặc thiếu cấu hình), Bean `PosOrderController` hoàn toàn không được nạp vào Spring context; các request gửi đến trả về `404 Not Found` (fail-closed).
- **Ranh giới nghiệm thu**:
  - **CODE IMPLEMENTED, TESTED, FEATURE FLAG OFF, E2E NOT RUN**.
  - Không triển khai payment CAPTURED/PAID khi chưa có phê duyệt từ BA Huy/PM An.
  - Không sửa migration CSDL của Kiên (V001–V005).
  - Không sửa mã nguồn AI của Vũ.

### 16.2 Nguyên tắc Bảo mật Zero-Trust & Toàn vẹn Dữ liệu
1. **Xác thực JWT Actor tin cậy**:
   - Không nhận `actor_id` từ request body hoặc HTTP headers; trích xuất bắt buộc từ `TrustedActorAccess.actorId(authentication)`. Thiếu token hoặc token không hợp lệ trả về HTTP 401.
2. **Kiểm tra Store Scope & Quyền thu ngân**:
   - Thu ngân phải có quyền `sales.checkout` tại đúng `store_id` của đơn hàng (`has_permission(actorId, 'sales.checkout', storeId)`).
   - Kho hàng `warehouse_id` bắt buộc phải trực thuộc `store_id` (`warehouses.store_id = store_id`).
   - Vi phạm quyền hoặc sai kho trả về HTTP 403 `FORBIDDEN`.
3. **Giá bán Authoritative từ Catalog DB**:
   - Backend tự tra cứu giá bán từ bảng `sasms.products.list_price`. Tuyệt đối không nhận đơn giá (`unit_price`) hay tổng tiền (`total_amount`) từ client.
   - Khi tạo đơn DRAFT, trường `sales_orders.total_amount` mặc định là 0 (tuân thủ phân quyền `runtime-roles.sql`, `sasms_app` không có quyền INSERT `total_amount`). Giá trị tổng tiền chính thức sẽ do hàm DB `sasms.reserve_order` tự động tính toán tổng từ `sales_order_items`.
4. **Idempotency Key & Phòng chống Replay / Xử lý Cạnh tranh Đồng thời**:
   - Header hỗ trợ chuẩn RFC `Idempotency-Key` lẫn header legacy `X-Idempotency-Key` và trường `idempotency_key` trong JSON body. Thứ tự ưu tiên: `Idempotency-Key` > `X-Idempotency-Key` > body `idempotency_key` (header luôn ghi đè body).
   - Kiểm tra ràng buộc: Từ chối chuỗi rỗng/khoảng trắng (422) và từ chối độ dài > 255 ký tự (422).
   - Chuẩn hóa tương thích CSDL: Cột `sales_orders.idempotency_key` là `uuid NOT NULL UNIQUE`. Nếu là UUID hợp lệ thì parse trực tiếp. Nếu là chuỗi tùy biến thì chuyển đổi ánh xạ tất định có phân lập theo quầy và chi nhánh: `UUID.nameUUIDFromBytes(("store:" + storeId + ":actor:" + actorId + ":" + trimmed).getBytes(StandardCharsets.UTF_8))` (RFC 4122 v3 name-UUID). Khắc phục triệt để lỗi ép kiểu CSDL và chống đụng độ key giữa các quầy khác nhau.
   - Replay cùng key và cùng payload: Trả về đơn hàng đã tạo nguyên vẹn (idempotent replay, HTTP 201/200).
   - Tái sử dụng key nhưng khác payload: Trả về HTTP 409 `IDEMPOTENCY_CONFLICT`.
   - Cạnh tranh đồng thời (Race condition) & Khắc phục Transaction Abort: Thay vì catch `DuplicateKeyException` (khiến PostgreSQL đánh dấu transaction bị abort và từ chối mọi câu SELECT tiếp theo trong cùng transaction), hệ thống sử dụng câu lệnh `INSERT INTO sasms.sales_orders (...) VALUES (...) ON CONFLICT (idempotency_key) DO NOTHING`. Nếu `rows == 0`, transaction vẫn nguyên vẹn và an toàn tái truy vấn kiểm tra: nếu trùng khớp payload sẽ trả về đơn hàng đã tạo an toàn; nếu sai lệch payload sẽ ném HTTP 409 conflict.
5. **Giao dịch Nguyên tử (Transactional Atomicity)**:
   - Toàn bộ thao tác tạo đơn và chi tiết đơn hàng được bọc trong `@Transactional(rollbackFor = Exception.class)`. Nếu có bất kỳ dòng sản phẩm nào lỗi hoặc không hợp lệ, toàn bộ giao dịch được rollback sạch sẽ, không để lại đơn hàng rác/mồ côi trong `sales_orders`.

### 16.3 Ánh xạ Mã lỗi Chuẩn
- **401 Unauthorized**: Thiếu JWT hoặc token không hợp lệ / hết hạn.
- **403 Forbidden**: Thu ngân thiếu quyền `sales.checkout` hoặc kho không thuộc chi nhánh của đơn hàng.
- **404 Not Found**: Sản phẩm không tìm thấy / không active; hoặc đơn hàng không tồn tại khi gọi reserve; hoặc cờ tính năng đang tắt (404 Spring MVC).
- **409 Conflict**:
  - `IDEMPOTENCY_CONFLICT`: Tái sử dụng Idempotency-Key với nội dung payload khác.
  - `DEVICE_LOCKED` (SQLSTATE `55P03`): Xung đột khóa thiết bị.
- **422 Unprocessable Content**:
  - `VALIDATION_FAILED`: Đơn hàng không có sản phẩm; số lượng $\le 0$; trùng lặp sản phẩm trong các dòng; Idempotency-Key rỗng hoặc quá dài; hoặc vi phạm ràng buộc CSDL (SQLSTATE `23514`).
- **503 Service Unavailable**:
  - `DEPENDENCY_UNAVAILABLE`: Lỗi kết nối CSDL hoặc timeout, không để lộ câu lệnh SQL hay connection secret.

### 16.4 Bằng chứng Kiểm thử Tổng thể
- **Backend Test Suite**:
  - `mvnw.cmd test` chạy thành công toàn bộ **241 tests** (0 failures, 0 errors, 16 skipped).
  - 39 tests mới chuyên biệt cho Order-First POS:
    - `PosOrderServiceTest` (18 unit tests): Validation logic, duplicate check, cross-store check, warehouse membership, product lookup, idempotency replay vs conflict, reserve status check, key length/blank validation, store/actor isolation, và 2 test cạnh tranh đồng thời concurrent race condition (replay khi match, conflict khi mismatch).
    - `JdbcPosOrderDataAccessTest` (3 unit tests): SQL parameter mapping, permission checks, forward/backward compatibility với schema Kiên V004 (`to_jsonb(so)->>'hold_expires_at'`).
    - `PosOrderDisabledFeatureFlagIntegrationTest` (2 integration tests): 404 Not Found khi cờ tính năng tắt.
    - `PosOrderSecurityIntegrationTest` (14 security integration tests): MockMvc xác thực JWT, 401 khi thiếu auth, 201 tạo đơn thành công, 403 thiếu quyền checkout, 422 lỗi validation, 409 xung đột idempotency, 200 reserve thành công, 401 reserve thiếu auth, 404 reserve đơn không tồn tại, 409 xung đột khóa thiết bị 55P03, 422 vi phạm ràng buộc 23514, và các test xác minh Idempotency-Key header RFC chuẩn UUID, chuỗi tùy biến deterministic UUID, chuỗi trong JSON body, và header ghi đè body.
    - `Sprint2OrderPostgreSqlIntegrationTest` (7 integration tests): Bộ test tích hợp trên PostgreSQL thật với 2 connection độc lập, concurrency race condition, rollback nguyên tử, kiểm tra quyền `sasms_app`, lease 10 phút và tương thích baseline vs V004.
- **Frontend Test Suite & Build**:
  - `npm test`: **47/47 tests PASS** (authContext, QR, POS Cart, AI service).
  - `npm run build`: Vite 5.4.21 production build SUCCESS.
  - `git diff --check`: PASS, không có lỗi whitespace hay format.
- **Hiện trạng Môi trường PostgreSQL**:
  - Docker Desktop daemon trên máy host đang tắt (`open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`).
  - Toàn bộ script provisioning và test suite đã sẵn sàng: `scripts/create_s2_order_container.ps1`, `scripts/provision_s2_order_db.ps1`, `scripts/run_s2_order_it.ps1`.
  - Tuân thủ nguyên tắc không sử dụng database dùng chung hoặc database staging chưa cô lập.

## 17. Kiểm chứng Order-First trên PostgreSQL thật, Khắc phục Transaction Abort & Bộ Tự động hóa Cô lập

### 17.1 Khắc phục triệt để lỗi Transaction Abort trên PostgreSQL
- **Bản chất lỗi trên PostgreSQL**: Khác với một số RDBMS khác, khi PostgreSQL phát sinh vi phạm ràng buộc `UNIQUE` (`SQLSTATE 23505`), transaction hiện hành ngay lập tức bị đánh dấu là bị hủy (`current transaction is aborted, commands ignored until end of transaction block`). Nếu tầng ứng dụng bắt ngoại lệ `DuplicateKeyException` rồi thực hiện `SELECT` trong cùng transaction mà không có Savepoint, PostgreSQL sẽ từ chối thực thi và toàn bộ transaction thất bại.
- **Giải pháp chuẩn kiến trúc**: Chuyển đổi câu lệnh INSERT đơn hàng trong `JdbcPosOrderDataAccess` sang:
  ```sql
  INSERT INTO sasms.sales_orders (
      id, code, idempotency_key, store_id, warehouse_id, customer_id, channel, created_by
  ) VALUES (
      CAST(? AS uuid), CAST(? AS text), CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), 'POS', CAST(? AS uuid)
  )
  ON CONFLICT (idempotency_key) DO NOTHING;
  ```
- **Hành vi xử lý tại `PosOrderService`**:
  - `dataAccess.createOrderIfAbsent(...)` trả về `Optional<SalesOrderRecord>`.
  - Nếu `rows == 1`: Đơn hàng mới được tạo thành công, tiến hành chèn các dòng sản phẩm `sales_order_items`.
  - Nếu `rows == 0` (xung đột trên `idempotency_key`): Transaction **vẫn hợp lệ và không bị PostgreSQL đánh dấu lỗi**. Service thực hiện truy vấn `findOrderByIdempotencyKey(idempotencyKey)` trong cùng transaction và so sánh payload:
    - Nếu khớp payload (cùng store, warehouse, items): Trả về đơn hàng đã tạo nguyên vẹn (idempotent replay thành công).
    - Nếu khác payload: Ném `ResourceConflictException` (HTTP 409 Conflict) với lý do tái sử dụng key nhưng payload sai lệch.
  - Chỉ bắt đúng xung đột trên constraint `idempotency_key`, không nuốt các lỗi UNIQUE hoặc ngoại lệ CSDL khác.

### 17.2 Cơ chế phân giải Idempotency-Key an toàn & Cô lập Quầy/Actor
- **Thứ tự ưu tiên nguồn Key**: Header `Idempotency-Key` (chuẩn RFC) > Header `X-Idempotency-Key` (legacy) > JSON body `idempotency_key`. Header luôn ghi đè body nếu cả hai cùng xuất hiện.
- **Ràng buộc đầu vào**:
  - Chuỗi rỗng hoặc chỉ chứa khoảng trắng: Ném HTTP 422 `PosOrderValidationException("Idempotency key must not be blank", "Idempotency-Key")`.
  - Độ dài vượt quá 255 ký tự: Ném HTTP 422 `PosOrderValidationException("Idempotency key must not exceed 255 characters", "Idempotency-Key")`.
- **Phân giải và Scoping Namespace**:
  - Nếu là chuỗi UUID hợp lệ: Parse trực tiếp `UUID.fromString(trimmed)`.
  - Nếu là chuỗi tùy biến: Ánh xạ tất định có phân lập theo chi nhánh và thu ngân:
    `UUID.nameUUIDFromBytes(("store:" + storeId + ":actor:" + actorId + ":" + trimmed).getBytes(StandardCharsets.UTF_8))`
  - **Lợi ích**: Đảm bảo hai thu ngân tại hai quầy hoặc hai chi nhánh khác nhau dù có cùng đặt tên key theo cùng quy tắc (ví dụ `pos-bill-001`) cũng sinh ra hai UUID hoàn toàn khác nhau trong CSDL, triệt tiêu nguy cơ đụng độ hoặc ghi đè đơn của nhau.

### 17.3 Bộ Kiểm thử Tích hợp PostgreSQL 16 Cô lập (`Sprint2OrderPostgreSqlIntegrationTest`)
Bổ sung test suite tích hợp `Sprint2OrderPostgreSqlIntegrationTest.java` (7 nhóm kiểm thử chuyên sâu trên PostgreSQL thật):
1. **Quyền tối thiểu `sasms_app`**:
   - Chỉ được INSERT các cột được cấp quyền trong `runtime-roles.sql` (`id, code, idempotency_key, store_id, warehouse_id, customer_id, channel, created_by`).
   - Bị từ chối quyền (42501) nếu cố tình INSERT `status = 'PAID'` hoặc `total_amount`.
   - Bị từ chối quyền (42501) nếu cố tình UPDATE `devices.status` trực tiếp.
   - Được phép EXECUTE hàm nghiệp vụ `sasms.reserve_order(uuid, uuid)`.
2. **Replay cùng payload**: Trả về đúng đơn hàng DRAFT đã tạo trước đó, trạng thái 200/201, CSDL chỉ có 1 bản ghi duy nhất.
3. **Concurrency 2 kết nối đồng thời (Two-Connection Race Condition)**: Hai luồng chạy đồng thời cùng mili-giây với cùng key và cùng payload; một luồng thực hiện INSERT, luồng kia nhận `ON CONFLICT DO NOTHING` và tái truy vấn; cả hai luồng đều nhận kết quả thành công và cùng chung `order_id`.
4. **Replay khác payload**: Phát hiện xung đột và trả về HTTP 409 Conflict.
5. **Rollback nguyên tử (Atomicity) & Chứng minh Rollback giữa Transaction**:
   - **Database-level Mid-transaction Rollback**: Chứng minh rollback thực sự tại tầng CSDL thông qua `dataAccess.createOrderIfAbsent` với đơn hàng có nhiều dòng sản phẩm: dòng 1 chèn hợp lệ vào `sales_orders` và `sales_order_items`, dòng 2 cố tình vi phạm khóa ngoại `sales_order_items_product_id_fkey` (`SQLSTATE 23503`). CSDL ném `DataAccessException` khi đang thực thi giữa chừng transaction, và ranh giới `@Transactional` lập tức rollback toàn bộ: truy vấn kiểm tra thực tế trong PostgreSQL xác nhận cả `sales_orders` lẫn `sales_order_items` đều có đúng 0 bản ghi tồn tại (chứng minh rollback thật sự sau lỗi giữa transaction, không chỉ dừng ở việc từ chối input trước khi INSERT).
   - **Controller-level Input Validation**: Đồng thời kiểm chứng tầng Controller từ chối payload có sản phẩm không tồn tại ngay từ khâu thẩm định danh mục với HTTP 404 NOT_FOUND (`ResourceNotFoundException`), không phát sinh đơn hàng rác.
6. **Kiểm tra Store/Warehouse Scope & Quyền `sales.checkout`**:
   - Thu ngân Store B bị chặn khi tạo đơn tại Store A (403 Forbidden).
   - Nhân viên bán hàng thiếu quyền `sales.checkout` bị chặn (403 Forbidden).
   - Kho không trực thuộc Store của đơn hàng bị chặn (422 Unprocessable Content).
   - Người dùng vô hiệu hóa (is_active=false) bị chặn (401 Unauthorized).
7. **Reserve đơn hàng & Quản lý Lease 10 phút**:
   - Gắn thiết bị thực tế qua `order_devices`.
   - Gọi `reserve_order` chuyển trạng thái đơn hàng và thiết bị sang `RESERVED`.
   - Tương thích ngược: Truy vấn `to_jsonb(so)->>'hold_expires_at'` hoạt động trơn tru trên cả schema baseline (chưa có cột) lẫn migration V004 của Kiên (đã có cột `hold_expires_at`).

### 17.4 Bộ Script Tự động hóa Môi trường PostgreSQL 16 Cô lập
1. `scripts/create_s2_order_container.ps1`:
   - Kiểm tra daemon Docker.
   - Tạo volume `tv4_s2_order_it_pgdata` và container `tv4_s2_order_it_db` trên port `127.0.0.1:15433` (loopback strictly).
   - Nạp baseline schema `database/schema.sql` và phân quyền runtime `database/runtime-roles.sql`.
   - Trích xuất và nạp migrations `V003`, `V004`, `V005` trực tiếp từ `origin/DB` bằng Git CLI mà **không merge, cherry-pick hay can thiệp vào working tree của Kiên**.
2. `scripts/provision_s2_order_db.ps1`:
   - Xác thực container, port 15433 và volume độc lập.
   - Tạo tài khoản đăng nhập tạm thời `tv4_s2_app_login` (thuộc group `sasms_app`) và `tv4_s2_admin_login` (thuộc group `sasms_owner`) với mật khẩu ngẫu nhiên 64-hex ký tự.
3. `scripts/run_s2_order_it.ps1`:
   - Thiết lập các biến môi trường kiểm thử `SASMS_S2_PG_IT_ENABLED=true`, `SASMS_DB_URL=jdbc:postgresql://127.0.0.1:15433/sasms`.
   - Thực thi `mvnw.cmd -Dtest=Sprint2OrderPostgreSqlIntegrationTest test`.
   - Dọn dẹp sạch sẽ tài khoản tạm sau khi hoàn tất kiểm thử.

### 17.5 Hướng dẫn Khởi động Docker Desktop & Thực thi
- **Môi trường thực thi**: Container `tv4_s2_order_it_db` trên port `127.0.0.1:15433` (volume `tv4_s2_order_it_pgdata`) được khởi tạo và nạp đầy đủ baseline schema, runtime-roles, cùng toàn bộ migrations V001–V005 từ `origin/DB` thông qua `scripts/create_s2_order_container.ps1`.
- **Thực thi kiểm thử**:
  ```powershell
  powershell -ExecutionPolicy Bypass -File scripts/run_s2_order_it.ps1
  ```

## 18. Xác minh Thực tế trên PostgreSQL 16 Thật, Chuẩn hóa DTO & Bàn giao Draft PR (17/11/2026)

### 18.1 Kết quả Kiểm thử Tích hợp Thực tế trên PostgreSQL 16
- **Kết quả thực thi `run_s2_order_it.ps1`**:
  - Toàn bộ **7/7 tests** trong `Sprint2OrderPostgreSqlIntegrationTest` đều **PASS 100%** (0 failures, 0 errors, 0 skipped, thời gian chạy ~4.3s).
  - Đã xác thực trên PostgreSQL 16 thật:
    1. Quyền tối thiểu `sasms_app` (bị chặn 42501 khi ghi status/total/devices, được phép gọi reserve_order).
    2. Idempotent replay thành công khi trùng payload (200/201, 1 bản ghi duy nhất).
    3. Concurrency 2 kết nối đồng thời chạy song song không sinh deadlock, không abort transaction, cùng nhận đơn hợp lệ.
    4. Xung đột idempotency key khác payload trả về đúng HTTP 409 Conflict.
    5. Chứng minh rollback nguyên tử thực sự giữa transaction: khi chèn đơn nhiều dòng mà dòng con bị lỗi khóa ngoại `sales_order_items_product_id_fkey` (`23503`), cả đơn hàng lẫn các dòng con đều được rollback sạch về 0 bản ghi.
    6. Thực thi nghiêm ngặt phân quyền `sales.checkout` và phạm vi cửa hàng/kho hàng (chặn 401, 403, 422).
    7. Reserve đơn hàng, khóa thiết bị thành RESERVED, quản lý lease 10 phút tương thích ngược schema.

### 18.2 Ranh giới Rõ ràng: MockMvc + PostgreSQL Integration vs HTTP End-to-End Độc lập
> [!IMPORTANT]
> - **Phạm vi đã đạt**: Bộ kiểm thử `Sprint2OrderPostgreSqlIntegrationTest` là **PostgreSQL 16 + MockMvc Integration Test**. Mã nguồn Spring Boot nạp cấu hình thực tế, kết nối live connection pool (HikariCP) tới PostgreSQL 16 thật trong Docker container `tv4_s2_order_it_db`, đồng thời sử dụng mock token do ephemeral JWKS in-memory server ký phát.
> - **Phạm vi CHƯA đạt (NOT RUN / BLOCKED)**: Đây **KHÔNG PHẢI** là kiểm thử **HTTP End-to-End (E2E) độc lập trên toàn bộ mạng**.
> - **Lý do còn thiếu cho E2E độc lập**:
>   1. **Thiếu Identity Provider (IdP) production**: Chưa có server OAuth2/OIDC/JWKS thật cấp phát token cho frontend và backend xác thực chéo qua mạng.
>   2. **Thiếu Cổng thanh toán (Payment Provider & Webhook IPN)**: Các bước thanh toán VietQR / Thẻ / Webhook ngân hàng chưa triển khai REST Controller; đơn hàng mới dừng lại ở trạng thái `RESERVED`, chưa thể chuyển tự động sang `CAPTURED` và `PAID` trên môi trường staging thật.
>   3. **Cờ tính năng mặc định OFF**: Cả hai cờ `sasms.features.pos-serial-api.enabled` và `sasms.features.pos-order-api.enabled` đều đang đặt `false` trong mã nguồn bàn giao.
>   4. **Báo cáo cam kết**: Tuyệt đối không ghi nhận "DONE" hoặc "E2E PASS" cho chu trình bán hàng / thanh toán khi các thành phần trên chưa được tích hợp và kiểm thử qua HTTP thật.

### 18.3 Chuẩn hóa Hợp đồng DTO `PosOrderItemResponse`
- Đã rà soát và chuẩn hóa DTO `PosOrderItemResponse`:
  - Chỉ xuất duy nhất trường `@JsonProperty("order_item_id") UUID orderItemId` theo đúng hợp đồng REST API đã thống nhất với các endpoint thiết bị (`/api/v1/pos/orders/{orderId}/items/{orderItemId}/devices`).
  - Loại bỏ hoàn toàn việc xuất song song hai trường `id` và `order_item_id` gây dư thừa hoặc sai lệch hợp đồng.
  - Cập nhật test suite để đọc và xác minh nhất quán theo trường `order_item_id`.

### 18.4 Kiểm chứng Dọn dẹp Fixture Cô lập (Clean Teardown)
- Phương thức `cleanupFixtures` trong `Sprint2OrderPostgreSqlIntegrationTest`:
  - Chỉ tác động đến các bản ghi mang tiền tố / UUID fixture cô lập của TV4 (`22222222-2222-4222-8222-...`).
  - Trước khi xóa `sales_order_items` và `order_devices`, test tự động chuyển `devices.status` về `IN_STOCK` và `sales_orders.status` về `DRAFT` để thỏa mãn trigger bảo vệ `guard_order_child()`.
  - Bổ sung xóa bảng `sasms.payments` theo `order_id` trước khi xóa `sales_orders`, ngăn ngừa triệt để lỗi khóa ngoại hoặc dữ liệu mồ côi.
  - Hậu kiểm thực tế trong CSDL PostgreSQL qua câu lệnh SQL xác nhận: **0 orphan records** trên tất cả các bảng `sales_orders`, `sales_order_items`, `order_devices`, `payments`, `devices`, `inventory_movements`, `outbox_events`, `audit_logs`, `users`.
  - Không làm suy yếu bất kỳ trigger nào của CSDL và không sửa đổi các file migration của Kiên.

### 18.5 Tổng hợp Kết quả Regression Kiểm thử Toàn diện
- **Backend Test Suite**: `mvnw.cmd test` $\rightarrow$ **241 tests run: 0 failures, 0 errors, 16 skipped** (BUILD SUCCESS).
- **PostgreSQL 16 Integration**: `run_s2_order_it.ps1` $\rightarrow$ **7 tests run: 0 failures, 0 errors, 0 skipped** (BUILD SUCCESS).
- **Frontend Test Suite**: `npm test` $\rightarrow$ **47 tests run: 0 failures, 0 skipped** (PASS 100%).
- **Frontend Production Build**: `npm run build` $\rightarrow$ **Vite build SUCCESS** (332 modules).
- **Code Hygiene**: `git diff --check` $\rightarrow$ Không có lỗi khoảng trắng hay định dạng thừa.
