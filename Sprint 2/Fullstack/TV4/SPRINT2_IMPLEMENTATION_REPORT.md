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
