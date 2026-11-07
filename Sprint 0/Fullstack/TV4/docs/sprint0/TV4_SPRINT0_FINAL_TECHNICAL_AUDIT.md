# SASMS Sprint 0 Báo cáo kiểm toán kỹ thuật phần việc TV4

> **Ghi chú bàn giao sang repository PM An (`D:\software-project-management-an`, nhánh `feat/tv4-sprint0-handoff`):** Báo cáo này là **bản ghi kiểm toán lịch sử** được thực hiện ngày 07/11/2026 trên **repository nguồn** `D:\smart-electronic-supermarket-sprint0` (nhánh `feat/tv4-sprint0-postgresql`, trước khi chạy bước khắc phục trong [TV4_SPRINT0_REMEDIATION_REPORT.md](TV4_SPRINT0_REMEDIATION_REPORT.md)). Toàn bộ kết quả kiểm thử trong tài liệu được giữ nguyên theo đúng lịch sử kiểm toán tại repo nguồn; kết quả tích hợp `backend/` & `frontend/` và log kiểm chứng mới nhất tại repo của An được ghi nhận tại [../../TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md](../../TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md) và thư mục [../../evidence/](../../evidence/). Khi chạy các lệnh kiểm tra tài liệu từ thư mục gốc repo An, thay tiền tố `docs/sprint0/` bằng `"Sprint 0/Fullstack/TV4/docs/sprint0/"`.

**Ngày kiểm toán:** 07/11/2026

**Worktree lịch sử khi kiểm toán:** `D:\smart-electronic-supermarket-sprint0`

**Nhánh lịch sử khi kiểm toán:** `feat/tv4-sprint0-postgresql`

**Phạm vi:** D52, D53, D56, D63, D64, D65 và source/config liên quan

**Kết luận:** **CHƯA SẴN SÀNG tạo PR bàn giao ở trạng thái Ready for Review.** Có thể mở Draft PR để lấy ý kiến sớm, nhưng phải ghi rõ các blocker trong báo cáo này.

## 1. Nguồn đã kiểm tra

- Toàn bộ workbook phân công Sprint 0, gồm các sheet `Phân chia công việc`, `Theo người`, `Tài liệu và minh chứng`, `Nghi thức và mốc`, `Checklist`.
- D23 Working Agreement.
- D34 FRS V2; D35 quy tắc Serial/IMEI; D40 BPMN Kho/POS.
- D50 kiến trúc; D51 thiết kế PostgreSQL 37 bảng; D54 bảo mật/RBAC.
- Toàn bộ file D52, D53, D56, D63, D64, D65 và source/config hiện có trong worktree.
- Link Figma được ghi nhận: `https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55`. Nội dung trực tuyến không trả về dữ liệu có thể kiểm tra trong lần audit này, nên trạng thái visual/prototype là **NOT VERIFIED**.

Không sửa code, SQL, Docker, OpenAPI hay business rule trong quá trình kiểm toán.

## 2. Đầu việc chính xác của TV4

Workbook giao TV4 bốn công việc chính, tổng 104 giờ:

| Mã việc | Công việc | Đầu ra | Giờ | Minh chứng bắt buộc theo phân công |
| --- | --- | --- | ---: | --- |
| S0-24 | Khung source, coding/Git, môi trường Dev | D63, D64, D65 | 24 | Quy ước; branch protection/CI nếu thực sự có; repo/boilerplate; phiên bản môi trường và chạy thử |
| S0-20 | Mockup Kho/QR/POS | D53 | 40 | Link Figma và PO xác nhận từng màn hình |
| S0-19 | OpenAPI Kho/POS/AI | D52 | 24 | `openapi.yaml` và review của Kiên |
| S0-23 | Thiết kế tích hợp e-invoice/SMS/máy in | D56 | 16 | Tài liệu provider và tài khoản sandbox |

TV4 còn hỗ trợ S0-17/D50 và S0-21/D54. D23 giao TV4 phát triển Module Kho/QR/Serial/IMEI và POS, nhưng phân công Sprint 0 là nguồn xác định đầu ra cụ thể. D23 giao Architect trách nhiệm tổng quát về OpenAPI/review code; vì vậy D52 cần Kiên review, không làm thay đổi chủ sở hữu chính là TV4 trong workbook.

## 3. Tóm tắt trạng thái sáu nhóm sản phẩm

| Sản phẩm | File/hiện trạng | Kết quả audit | Trạng thái bàn giao |
| --- | --- | --- | --- |
| D52 | YAML OpenAPI 3.0.3 + design notes | YAML parse PASS; Redocly FAIL 3 lỗi/6 cảnh báo; 91 `$ref` nội bộ resolve; 12 `operationId` đủ và không trùng; chưa triển khai API nghiệp vụ | **BLOCKED** |
| D53 | 3 file đặc tả + manifest 52 frame + mẫu PO evidence + link Figma | Đặc tả có căn cứ; manifest đủ số lượng 52 nhưng lệch frame plan, thiếu coverage mobile/forbidden ở nhiều nhóm; B10 chưa QA; PO evidence toàn bộ PENDING | **BLOCKED** |
| D56 | Tài liệu design draft | Có ranh giới an toàn, idempotency/retry/timeout ở mức định hướng; chưa có provider/device, contract callback chi tiết, sandbox evidence hoặc test | **PARTIAL** |
| D63 | Coding/Git/PR conventions | Đủ 14 nội dung chính, phân biệt đề xuất; có một thông tin source đã lỗi thời và quy tắc branch chưa khớp nhánh hiện tại | **NEEDS UPDATE/APPROVAL** |
| D64 | Skeleton Spring Boot/React/PostgreSQL | SQL giữ nguyên, JDBC driver/profile đúng, backend unit/context test PASS; frontend build FAIL; không có tầng dữ liệu nghiệp vụ | **PASS WITH LIMITATIONS** |
| D65 | Hướng dẫn Windows Dev | Cấu hình và phiên bản khớp source/máy audit; Compose config PASS; chưa chứng minh setup mới end-to-end; baseline JDK còn xung đột workbook | **NEEDS BASELINE APPROVAL** |

Không sản phẩm nào trong bảng trên được coi là đã nghiệm thu Sprint 0.

## 4. D52 OpenAPI Inventory POS AI

### 4.1 Kiểm tra cú pháp và validator

Lệnh parse (tại thư mục gốc repo An `D:\software-project-management-an`):

```powershell
python -c "import yaml; yaml.safe_load(open('Sprint 0/Fullstack/TV4/docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml', encoding='utf-8'))"
```

Kết quả: **PASS** ở mức YAML. Cấu trúc đọc được gồm 12 path, 22 schema, 7 response dùng chung và 7 parameter dùng chung.

Lệnh validator chuyên dụng (tại thư mục gốc repo An `D:\software-project-management-an`; khi kiểm toán gốc tại repo nguồn dùng đường dẫn `docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml`):

```powershell
npx.cmd --offline -y @redocly/cli lint "Sprint 0/Fullstack/TV4/docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml"
```

Kết quả: **FAIL - 3 errors, 6 warnings**.

Ba lỗi cấu trúc đều do chuỗi có dấu phẩy được đặt trong flow mapping nhưng không quote:

1. `components.responses.Conflict`: phần `concurrent reservation or idempotency mismatch` bị parser hiểu thành property lạ.
2. Response `202` của `initiatePayment`: phần `status may be PENDING` bị hiểu thành property lạ.
3. `PaymentResult.properties.source`: phần `SANDBOX_CALLBACK etc; pending D56` bị hiểu thành property lạ.

Sáu cảnh báo gồm thiếu `info.license`, server dùng localhost và bốn tag chưa có description. Localhost phù hợp tài liệu Dev/DRAFT nhưng vẫn phải quyết định cấu hình lint hoặc mô tả server chính thức; không được bỏ qua ba error.

### 4.2 `$ref`, operationId và security

- 91 `$ref` đều là local reference và resolve được bằng kiểm tra độc lập.
- Có 12 operation, tất cả có `operationId`; không trùng `operationId`.
- `bearerAuth` được đặt ở cấp global, vì vậy toàn bộ operation kế thừa security.
- Sáu operation không mô tả response `401`: `createPosOrder`, `getPosOrder`, `initiatePayment`, `reconcilePayment`, `getPosReceipt`, `printReceipt`. Đây là khoảng trống hợp đồng vì security đang áp dụng global.
- Có response dùng chung cho 400/401/403/404/409/422/503 và `ApiError` có `code`, `message`, `correlation_id`, phù hợp hướng NFR của D34. Tuy nhiên ba lỗi flow mapping khiến một số contract hiện không hợp lệ theo OpenAPI.
- Chưa có security scheme/contract riêng cho callback provider bằng HMAC/signature; bearer của nhân viên không thể mặc định dùng cho webhook.

### 4.3 Request response và độ phủ nghiệp vụ

- Inventory stock, receiving, QR lookup, reservation, order, payment initiation/status, receipt/print và AI cross-sell đã có draft request/response.
- D35 được phản ánh ở Serial 1-128 ký tự, IMEI1/IMEI2 và Luhn trong mô tả, nhưng mapping DB vẫn được ghi pending đúng thực tế.
- Payment giữ riêng `PENDING/PAID/FAILED/UNKNOWN`; receipt chỉ dành cho đơn PAID; print job không làm đổi tồn. Đây là hướng đúng D34/D40.
- Không có path/callback object cho inbound payment/provider callback mặc dù D52 chuyển trách nhiệm xác minh callback sang backend/D56.
- D56 hiện không thiết kế payment adapter; vì vậy có lỗ hổng giao trách nhiệm giữa D52 và D56.
- Phạm vi AI mới có FR-08 cross-sell. D34 còn FR-09 tóm tắt thông số; BA/PO cần xác nhận FR-09 nằm ngoài D52 hay đang thiếu contract.
- Nhiều enum/path/ID vẫn là đề xuất, đúng với nhãn DRAFT và 10 quyết định pending; chưa được dùng làm API baseline.

### 4.4 API đã triển khai và API mới đặc tả

Source backend hiện chỉ có:

- `GET /api/health`
- `GET /api/pos/status`
- `GET /api/serial-imei/status`

Không có controller cho bất kỳ path `/api/v1/*` nào trong D52. Toàn bộ 12 operation D52 là **API mới đặc tả, chưa triển khai**. Ba endpoint hiện tại chỉ là skeleton/status và không chứng minh Kho/POS/AI hoạt động.

## 5. D53 Figma Kho QR POS

### 5.1 Hồ sơ cục bộ

Đã có:

- `docs/sprint0/d53/D53_SCREEN_SPEC.md`
- `docs/sprint0/d53/D53_DECISION_REGISTER.md`
- `docs/sprint0/d53/D53_FIGMA_HANDOFF.md`
- `docs/sprint0/handoff/D53_FIGMA_FRAME_MANIFEST.md`
- `docs/sprint0/handoff/D53_PO_REVIEW_EVIDENCE_TEMPLATE.md`

Decision register có đủ 10 vấn đề, đều PENDING. Mẫu PO review giữ toàn bộ sáu nhóm ở trạng thái PENDING. Điều này đúng yêu cầu không tự nhận đã duyệt.

### 5.2 Đối chiếu manifest 52 frame

Manifest ghi 52 link: A=7, B=10, C=10, D=9, E=8, F=8. Số lượng khớp tổng kế hoạch, nhưng **ID/variant không khớp hoàn toàn với D53_FIGMA_HANDOFF**:

| Nhóm | Kết quả đối chiếu |
| --- | --- |
| A Inventory | Có đủ default/filter/loading/empty/forbidden/error/mobile nhưng mã A02-A06 đổi thứ tự so với handoff; cần thống nhất traceability |
| B Receiving | Chỉ có một frame mobile B10 thay vì hai frame mobile dự kiến; B10 được chính manifest ghi nguy cơ tràn bố cục và chưa QA; các ID validation/duplicate/confirm/unknown lệch handoff |
| C QR | Manifest không có `Forbidden Device` và `Mobile Found` như handoff; thay bằng `No camera`, `Invalid token`, `Network error` |
| D POS | Thiếu frame mobile, populated cart/`Cart Ready` và `Reservation Uncertain`; có `Reservation expired` dù TTL đang PENDING, cùng frame AI fallback ngoài lõi chọn hàng |
| E Payment | Không có mobile và forbidden theo handoff; bổ sung separate Unknown/Reconciliation/Idempotent retry |
| F Receipt | Không có mobile, print-success và 80 mm pending frame như handoff; bổ sung e-invoice pending, order details và forbidden |

D34 NFR-07 yêu cầu responsive web trên desktop/mobile và kiểm tra camera mobile. Manifest chỉ nêu rõ mobile ở A07 và B10. Không thể lấy tổng số 52 để thay thế kiểm tra coverage.

### 5.3 B10 và trạng thái Figma

- B10 `Mobile - cần kiểm tra tràn bố cục` là blocker QA trực quan.
- Manifest nói giới hạn MCP Starter làm lần chỉnh chiều cao cuối không thực hiện được. Đây là ghi nhận rủi ro, không phải PASS.
- Link Figma được ghi nhận nhưng nội dung trực tuyến không được đọc/QA độc lập trong lần audit này: **NOT VERIFIED**.
- Chưa có bằng chứng prototype hoạt động, ảnh QA toàn bộ frame hoặc comment/ảnh xác nhận PO từng màn hình.
- Kết luận: không được tuyên bố 52 frame đã QA, đã PO duyệt hoặc D53 đã hoàn thành.

## 6. D56 Integration Design

### 6.1 Phần phù hợp D34

- Phân biệt biên nhận POS với hóa đơn điện tử theo BR-07.
- Chỉ in receipt từ đơn PAID; lỗi in không hủy PAID/SOLD; in lại giữ order ID theo FR-06/D40.
- SMS đi sau khi bảo hành đã kích hoạt; SMS lỗi không rollback bảo hành theo FR-12/BR-05.
- Provider dùng sandbox/stub và không tuyên bố giao dịch thật theo BR-09.
- Có nguyên tắc idempotency cho print/e-invoice/SMS, timeout thành UNKNOWN/PENDING, retry có giới hạn, callback chống lặp, audit, PII và kiểm tra scope.

### 6.2 Khoảng trống cần bổ sung

- D52 nói policy payment provider/callback chờ D56, nhưng D56 chỉ thiết kế e-invoice/SMS/printer, không có payment adapter hoặc đường dẫn ownership rõ ràng. Cần BA/Architect chỉ định tài liệu sở hữu payment integration.
- Chưa có timeout cụ thể, số lần retry, backoff, điều kiện DLQ/manual review hoặc circuit-breaker.
- Chưa có callback contract cụ thể: endpoint, header chữ ký, canonical payload, timestamp/replay window, mapping trạng thái và HTTP response khi callback lặp/sai chữ ký.
- Chưa có permission cụ thể cho issue/reprint invoice, resend SMS, reconcile UNKNOWN hoặc xem PII; mới dừng ở role + scope tổng quát.
- Tên bảng/job/outbox được ghi là đề xuất; chưa đối chiếu/duyệt với 37 bảng và runtime ACL.
- Chưa chọn provider e-invoice/SMS, máy in/driver/kết nối, chưa có tài liệu provider hoặc tài khoản sandbox như minh chứng workbook yêu cầu.
- Ma trận D56-T01 đến T10 là test design, chưa chạy.

Kết luận D56: tài liệu draft có ranh giới an toàn, nhưng chưa đủ để triển khai adapter và chưa đủ bằng chứng bàn giao.

## 7. D63 Coding Git PR Conventions

### 7.1 Điểm đạt

- Có đủ mục đích, repository layout, branch, commit, PR, review, Java, React, test, secrets, schema, checklist và Definition of Done.
- Các quy tắc chưa được nhóm xác nhận đều được gắn `Đề xuất - chờ duyệt`.
- Không tự nhận branch protection hoặc CI đã bật.
- Phân biệt review kỹ thuật, QA và thay đổi schema; không cho tự thêm migration/destructive change.

### 7.2 Vấn đề

- Mục 11 nói `pom.xml` chưa có PostgreSQL driver. Source hiện tại đã có `org.postgresql:postgresql` scope runtime và `spring-boot-starter-test`. Câu này đã lỗi thời sau D64 và phải sửa trước PR.
- Quy tắc branch đề xuất dùng `feature/...` và task ID, trong khi nhánh hiện tại là `feat/tv4-sprint0-postgresql`. Vì quy tắc chưa duyệt nên chưa thể gọi là vi phạm, nhưng PM/Architect phải chọn một chuẩn trước bàn giao.
- D63 ghi Java 26/Spring Boot 4.1.1 đúng source, nhưng workbook D65 yêu cầu JDK 17. Đây là xung đột baseline, không phải vấn đề formatting.
- Branch protection, required checks, merge strategy, approval count, linter/formatter và coverage vẫn chưa có bằng chứng/duyệt.

## 8. D64 Source Skeleton

### 8.1 Cấu hình thực tế

- Backend: Spring Boot 4.1.1, Java 26, Web MVC, PostgreSQL JDBC runtime, starter test.
- Profile `postgresql` đọc `SASMS_DB_URL`, `SASMS_DB_USERNAME`, `SASMS_DB_PASSWORD`; `spring.sql.init.mode=never`.
- Không có JPA/Entity/Repository/data-access nghiệp vụ; không có `ddl-auto=create/update`.
- Frontend: React 18.3.1, Vite 5.4.10, Axios; chỉ có `dev`, `build`, `preview`.
- Compose dùng `postgres:16-alpine`, bind `127.0.0.1:5432`, mount `./database:/work/database:ro`, password từ `.env`.

### 8.2 PostgreSQL 37 bảng

- `database/schema.sql` có đúng 37 `CREATE TABLE`.
- SHA-256 schema trong repo khớp INPUT: `66AACDA1101C20ED0CC6C80062D93EF917660B4F6E825FA124F536DC2A1FCD96`.
- SHA-256 seed trong repo khớp INPUT: `8ED00DA989CCB6700867636D7D5616F0E90F2422086205BDDFBE79FCD783D385`.
- `docker-compose.yml` cũng khớp file INPUT.
- Không phát hiện thay đổi schema/seed trong audit.

### 8.3 Kiểm tra hiện tại

Backend:

```powershell
cd backend
.\mvnw.cmd -o test
```

Kết quả: **PASS - BUILD SUCCESS**; `Tests run: 2, Failures: 0, Errors: 0, Skipped: 1`.

- Context-load test: PASS.
- `PostgreSqlConnectionSmokeTest`: SKIPPED vì `SASMS_PG_SMOKE_ENABLED` không bật trong lần audit này.
- Bằng chứng JDBC chạy thật ngày 07/11/2026 đã được ghi trong D64/D65: 1 test, 0 fail/error/skip, nhưng audit hiện tại không tái chạy với credential.

Frontend:

```powershell
cd frontend
npm.cmd run build
```

Kết quả: **FAIL** vì không có `frontend/node_modules`, không tìm thấy `vite`. Không chạy cài dependency trong audit. Có `package-lock.json`, nhưng D65 đang hướng dẫn `npm install` thay vì `npm ci`; nên cân nhắc chuẩn hóa lệnh dựng máy mới sau khi PM/Architect duyệt.

Compose:

```powershell
$env:POSTGRES_PASSWORD='audit-placeholder-not-secret'
docker compose config --quiet
```

Kết quả: **PASS** exit code 0; Docker CLI cảnh báo không đọc được config người dùng trong sandbox, không ảnh hưởng parse Compose.

### 8.4 Giới hạn

Skeleton tồn tại và cấu hình PostgreSQL an toàn ở mức hiện tại. Không có bằng chứng Repository/JPA, API nghiệp vụ hoặc Kho/POS E2E. Không được dùng JDBC smoke test để kết luận nghiệp vụ đã hoạt động.

## 9. D65 Development Environment

### 9.1 Phiên bản xác minh lại

| Công cụ | Kết quả hiện tại |
| --- | --- |
| Java | 26.0.2.1 |
| Maven Wrapper | 3.9.16 |
| Node | 22.23.1 |
| npm | 10.9.8 |
| Docker CLI | 29.6.2 |
| Docker Compose | 5.3.1 |
| Python | 3.13.5 |

Các số này khớp D65 và chỉ là môi trường đã kiểm tra, chưa phải baseline được phê duyệt.

### 9.2 Vấn đề bàn giao môi trường

- Workbook yêu cầu minh chứng JDK 17, Node 18+, Python 3.10; source hiện tại ghim Java 26 và D65 ghi Python không cần cho luồng chính. PM/Architect phải chốt baseline chính thức.
- D50 vẫn ghi backend/framework chưa chốt; D64/D65 đúng khi không tự coi Spring Boot 4.1.1/Java 26 là baseline đã duyệt.
- Chưa có runtime DB role ít quyền/provisioning chính thức. Profile có thể dùng owner nếu cấu hình sai; cần Kiên xác nhận credential/ACL.
- Hướng dẫn không tự nạp schema trên DB đã có dữ liệu, không drop volume và không hardcode secret: đạt.
- Chưa tái hiện toàn bộ quy trình trên một máy Windows mới sạch; frontend build hiện chưa chạy được vì dependency chưa có.

## 10. Kết quả kiểm tra tổng hợp

| Kiểm tra | Kết quả | Giới hạn |
| --- | --- | --- |
| Đọc toàn bộ workbook phân công | PASS | Xác định 4 việc/6 đầu ra TV4, 104 giờ |
| YAML parse D52 | PASS | Không thay thế semantic validation |
| Redocly OpenAPI lint | FAIL | 3 error, 6 warning |
| `$ref` nội bộ | PASS | 91/91 resolve |
| `operationId` | PASS | 12/12 có, không trùng |
| API D52 đã triển khai | NOT IMPLEMENTED | Chỉ có 3 endpoint skeleton khác path D52 |
| Figma link | RECORDED / NOT VERIFIED | Không có content/visual QA độc lập |
| 52 frame manifest | COUNT PASS / COVERAGE FAIL | Lệch handoff; mobile/forbidden thiếu; B10 chưa QA |
| PO approval D53 | NOT RUN/PENDING | Mẫu evidence chưa có người/ngày/minh chứng |
| D56 provider/sandbox | NOT AVAILABLE | Chưa chọn provider/device, chưa có account evidence |
| Backend Maven test | PASS | 2 test, JDBC smoke skipped |
| Frontend build | FAIL | Thiếu node_modules/vite |
| Compose config | PASS | Parse config; không chứng minh container đang chạy |
| Schema/seed checksum | PASS | Khớp INPUT, 37 bảng tĩnh |
| JDBC runtime hiện tại | NOT RUN | Historical evidence PASS có trong D64/D65 |
| Kho/POS E2E | NOT RUN | Chưa có data-access/API nghiệp vụ |
| `git diff --check` | PASS | Chỉ kiểm tracked diff; phần lớn tài liệu đang untracked |
| Secret scan cơ bản | PASS | Chỉ thấy placeholder/env reference; không đọc `.env` thật |

## 11. Lỗi và mức ưu tiên

### P0 Blocker

1. **D52 không hợp lệ theo Redocly:** 3 structural error. Không được tạo PR Ready for Review khi validator chuyên dụng còn FAIL.
2. **D53 chưa đủ bằng chứng bàn giao:** 52 frame chưa được visual QA/PO duyệt; B10 có rủi ro overflow; link/prototype không được xác minh.
3. **D53 frame coverage không khớp đặc tả:** thiếu mobile cho C-F và một số forbidden/success/uncertain state; tổng số frame không chứng minh đủ D34/NFR-07.
4. **Baseline môi trường chưa chốt:** workbook nêu JDK 17 nhưng source dùng Java 26/Spring Boot 4.1.1; D50 còn ghi framework pending.

### P1 High

5. **D52 authentication/error contract chưa đồng đều:** 6 operation bảo vệ bằng global bearerAuth nhưng thiếu response 401.
6. **Khoảng trống payment callback:** D52 giao sang D56, D56 không sở hữu payment adapter; không có callback security contract.
7. **D56 thiếu provider/device/sandbox evidence:** chưa đáp ứng minh chứng workbook và chưa đủ chi tiết timeout/retry/callback để code.
8. **D63 có thông tin lỗi thời:** vẫn ghi `pom.xml` chưa có PostgreSQL driver.
9. **Frontend build không chạy:** dependency chưa cài; chưa có bằng chứng build xanh tại audit cuối.

### P2 Medium

10. D52 chưa xác định FR-09 AI summary nằm ngoài hay thiếu phạm vi.
11. D53 ID/variant giữa screen spec, Figma handoff và manifest không đồng nhất, làm yếu traceability PO/QA.
12. D63 branch convention đề xuất không khớp tên nhánh thực tế; các rule vẫn chờ duyệt.
13. D65 dùng `npm install` dù có lockfile; cần chốt `npm ci` cho setup tái lập.
14. Chưa có runtime DB role ít quyền, migration policy/tool và fresh-machine setup evidence.

## 12. Việc còn thiếu để bàn giao

1. Sửa ba lỗi D52, chạy lại Redocly đến khi không còn error; quyết định cách xử lý warning.
2. Bổ sung 401/error contract, callback ownership/security và xác nhận scope FR-09; BA/Architect review path/schema/enum.
3. Rà soát trực quan toàn bộ Figma; sửa B10; reconcile frame manifest với handoff; bổ sung/ghi rõ mobile C-F.
4. PO review từng nhóm A-F và lưu comment/ảnh/ngày/phiên bản; QA xác nhận visual/prototype.
5. Chốt payment integration owner; bổ sung chi tiết D56 và bằng chứng provider/sandbox/device hoặc ghi phạm vi demo/stub được duyệt.
6. Cập nhật câu lỗi thời trong D63; PM/Architect duyệt branch/commit/merge/check/CI conventions.
7. PM/Architect chốt Java/Spring/Node/Python/Docker baseline. Nếu chọn khác source, thực hiện qua task/CR riêng, không sửa trong audit.
8. Cài dependency từ nguồn được phép và chạy lại frontend build; lưu log.
9. Kiên chốt runtime DB role/ACL, migration baseline và mapping 37 bảng; không thay schema/seed trong bước này.
10. Tách rõ evidence lịch sử và evidence chạy trong PR hiện tại; không ghi PASS cho JDBC/E2E nếu không chạy.

## 13. Quyết định cần xác nhận

| Người/nhóm | Quyết định cần có |
| --- | --- |
| BA/PO | 10 decision D53; terminology trạng thái; Serial/IMEI2; TTL; nhận hàng; QR payload; payment; tiền/thuế; quality; FR-09 scope |
| PO | Duyệt từng nhóm frame D53, prototype và nội dung receipt/payment; không duyệt bằng tổng số frame |
| Architect/DBA | Java/Spring baseline; OpenAPI contract; state/API-DB mapping; runtime role/ACL; callback trust boundary; migration; outbox/job mapping |
| PM | Baseline môi trường, branch/PR/CI policy, provider/sandbox/device budget/scope và ngoại lệ bàn giao |
| QA | Redocly/contract test; visual/responsive Figma; D56 T01-T10; frontend build; integration/E2E acceptance |

## 14. Kết luận PR

**NO-GO cho PR bàn giao Ready for Review.** Lý do trực tiếp là D52 validator FAIL, D53 chưa QA/PO approval và lệch coverage, baseline Java chưa duyệt, D56 chưa có provider/sandbox evidence, frontend build FAIL.

Nếu cần lấy review sớm, có thể mở **Draft PR** với điều kiện:

- gắn nhãn `DRAFT/NOT ACCEPTED`;
- đính kèm báo cáo này và toàn bộ P0/P1;
- không bật auto-merge, không tuyên bố CI/branch protection;
- không mô tả API/Figma/integration là đã triển khai hoặc nghiệm thu.

## 15. Git status tại cuối lần đọc trước khi tạo báo cáo

```text
## feat/tv4-sprint0-postgresql...origin/main
 M .gitignore
 M backend/pom.xml
?? .env.example
?? backend/src/main/resources/application-postgresql.properties
?? backend/src/test/java/com/smartelectronicsupermarket/PostgreSqlConnectionSmokeTest.java
?? database/
?? docker-compose.yml
?? docs/sprint0/D63_CODING_GIT_PR_CONVENTIONS.md
?? docs/sprint0/D64_REPOSITORY_SOURCE_SKELETON.md
?? docs/sprint0/D65_DEVELOPMENT_ENVIRONMENT.md
?? docs/sprint0/d52/
?? docs/sprint0/d53/
?? docs/sprint0/d56/
?? docs/sprint0/handoff/
```

Phần lớn đầu ra vẫn là file untracked; chưa có commit/push/merge. Báo cáo này là file duy nhất được tạo bởi lần kiểm toán.
