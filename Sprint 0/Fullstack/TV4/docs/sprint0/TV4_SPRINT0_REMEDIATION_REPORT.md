# SASMS Sprint 0 Báo cáo khắc phục sau kiểm toán TV4

> **Ghi chú bàn giao sang repository PM An (`D:\software-project-management-an`, nhánh `feat/tv4-sprint0-handoff`):** Báo cáo này ghi lại đợt khắc phục kỹ thuật ngày 07/11/2026 trên **repository nguồn** `D:\smart-electronic-supermarket-sprint0` (nhánh `feat/tv4-sprint0-postgresql`, commit `12918f7`). Kết quả kiểm thử lịch sử được giữ nguyên; trong Phase 2 & Phase 3 tại repo của An, `backend/` và `frontend/` đã được tích hợp vào root và xác nhận `PASS` đối với `D52 Redocly lint`, `mvnw.cmd -o test`, `npm.cmd ci --offline` và `npm.cmd run build` (log lưu tại [../../evidence/](../../evidence/)). Riêng phần `PostgreSQL runtime` và `JDBC smoke test` với DB thật tại repo An đang ở trạng thái **`NOT RUN — PENDING DBA APPROVAL`** do chờ DBA Kiên cấp phát runtime login thuộc group role `sasms_app`.

**Ngày thực hiện:** 07/11/2026

**Worktree lịch sử khi thực hiện:** `D:\smart-electronic-supermarket-sprint0` (đã đồng bộ tài liệu sang `D:\software-project-management-an\Sprint 0\Fullstack\TV4\docs\sprint0`)

**Phạm vi:** Khắc phục lỗi kỹ thuật có thể tự xử lý trong D52, frontend và PostgreSQL smoke test; cập nhật hồ sơ D52/D63/D64/D65. Không thay đổi business rule, schema, seed hoặc backend nghiệp vụ.

## 1. Kết quả tổng hợp

| Hạng mục | Trước khắc phục | Sau khắc phục | Kết quả |
| --- | --- | --- | --- |
| D52 YAML parse | PASS | PASS | PASS |
| D52 Redocly | FAIL, 3 error/6 warning | PASS, 0 error/2 warning | PASS WITH PENDING WARNINGS |
| D52 `$ref` | 91 local ref resolve | 97 local ref resolve | PASS |
| D52 `operationId` | 12/12, không trùng | 12/12, không trùng | PASS |
| D52 security response | 6 operation thiếu `401` | 0 operation thiếu `401` | PASS |
| Frontend dependency | Thiếu `node_modules`/Vite | `npm ci` cài 88 package từ lockfile | PASS |
| Frontend build | FAIL | Vite build thành công | PASS |
| PostgreSQL runtime | Historical PASS | Container healthy, PostgreSQL 16.15, 37 bảng | PASS |
| JDBC smoke test | SKIPPED trong lần audit | 1 test, 0 fail/error/skip, BUILD SUCCESS | PASS |
| Kho/POS E2E | NOT RUN | NOT RUN | Không thuộc phạm vi khắc phục |

## 2. D52 OpenAPI

### 2.1 Ba lỗi ban đầu

Redocly ban đầu báo ba structural error trong `docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml`:

| Vị trí ban đầu | Lỗi | Nguyên nhân | Cách xử lý |
| --- | --- | --- | --- |
| Dòng 225, `components.responses.Conflict` | Property `concurrent reservation or idempotency mismatch` không hợp lệ | Description có dấu phẩy trong YAML flow mapping nhưng chưa quote | Quote toàn bộ description; không đổi HTTP 409 hoặc nghĩa conflict |
| Dòng 153, response `202` của `initiatePayment` | Property `status may be PENDING` không hợp lệ | Description có dấu phẩy nhưng chưa quote | Quote description; giữ nguyên `202` và trạng thái PENDING |
| Dòng 340, `PaymentResult.source` | Property `SANDBOX_CALLBACK etc; pending D56` không hợp lệ | Description có dấu phẩy nhưng chưa quote | Quote description; giữ nguyên field và nội dung PENDING D56 |

Đây là lỗi cú pháp biểu diễn YAML/OpenAPI, không phải quyết định nghiệp vụ.

### 2.2 Sáu cảnh báo ban đầu

- Bốn tag `Inventory`, `QR`, `POS`, `AI` thiếu description: đã bổ sung mô tả theo đúng phạm vi operation hiện có, không thêm API hoặc business rule.
- `info.license` thiếu: giữ **PENDING PM/Architect** vì không có căn cứ tự chọn license.
- Server là `http://localhost:8080`: giữ nguyên vì đây là DRAFT phục vụ local development; server ngoài local đang **PENDING Architect**.

Sau sửa còn hai warning có chủ ý nói trên. Redocly exit code 0 và xác nhận API description hợp lệ.

### 2.3 Security và hợp đồng lỗi

Global `bearerAuth` đã có từ trước. Đã bổ sung response `401` dùng `$ref` chung cho sáu operation còn thiếu:

- `createPosOrder`
- `getPosOrder`
- `initiatePayment`
- `reconcilePayment`
- `getPosReceipt`
- `printReceipt`

Không đổi quyền role/scope, path, request body, response schema, enum hoặc trạng thái nghiệp vụ.

### 2.4 Lệnh và kết quả

Chạy tại thư mục gốc repository đích `D:\software-project-management-an` (tại repo nguồn dùng đường dẫn `docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml`):

```powershell
npx.cmd --offline -y @redocly/cli lint "Sprint 0/Fullstack/TV4/docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml"
```

Kết quả:

```text
API description is valid
0 errors
2 warnings
```

Kiểm tra bổ sung:

```text
YAML_PARSE=PASS
paths=12
schemas=22
refs=97, bad refs=0
operations=12, missing operationId=0, duplicate operationId=0
operations missing 401=0
```

### 2.5 Nội dung vẫn PENDING

- License của tài liệu/API.
- Server/Staging/Production URL.
- Mapping role, trạng thái thiết bị, Serial/IMEI2, TTL, nhận hàng, QR payload, provider/payment, thuế/giảm giá và quality.
- Callback endpoint/security và owner của payment integration.
- Phạm vi FR-09 AI summary.

## 3. Frontend

### 3.1 Nguyên nhân

- `frontend/package.json` có Vite trong `devDependencies`.
- `frontend/package-lock.json` tồn tại và phù hợp để cài tái lập.
- `frontend/node_modules` chưa tồn tại tại thời điểm audit, nên script không tìm thấy executable `vite`.
- Không có lỗi logic giao diện được chứng minh từ lần build thất bại ban đầu.

### 3.2 Cài dependency và build

```powershell
cd frontend
npm.cmd ci
npm.cmd run build
```

Kết quả:

```text
npm ci: PASS, added 88 packages
Vite: 5.4.21
92 modules transformed
dist/index.html và assets được tạo
build completed in 899 ms
```

Không sửa `package.json`, `package-lock.json`, package version hoặc logic React. `node_modules` và `dist` là artifact sinh tự động đã được `.gitignore` loại trừ.

D65 được cập nhật dùng `npm ci` thay cho `npm install` khi lockfile hiện có và hợp lệ.

## 4. PostgreSQL JDBC smoke test

### 4.1 Kiểm tra an toàn trước khi chạy

Không reset database, không nạp lại schema/seed, không xóa volume và không chạy `docker compose down -v`.

```powershell
docker compose -p sasms_s0_check ps
docker compose -p sasms_s0_check exec -T db pg_isready -U sasms_owner -d sasms
docker compose -p sasms_s0_check exec -T db psql -X -U sasms_owner -d sasms -tAc "SELECT current_database(), current_setting('server_version'), (SELECT count(*) FROM information_schema.tables WHERE table_schema='sasms' AND table_type='BASE TABLE');"
```

Kết quả:

```text
container: healthy
pg_isready: accepting connections
database: sasms
PostgreSQL: 16.15
schema sasms: 37 tables
```

### 4.2 Điều kiện bật và kết quả

Test chỉ được bật trong process bằng `SASMS_PG_SMOKE_ENABLED=true`. URL bị test giới hạn ở PostgreSQL loopback, port 5432 và database `/sasms`; transaction được đặt read-only. Credential local được đọc vào biến process, không in ra log và được xóa khỏi environment khi lệnh kết thúc.

```powershell
cd backend
.\mvnw.cmd -o "-Dtest=PostgreSqlConnectionSmokeTest" test
```

Kết quả:

```text
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

Kết quả chỉ xác nhận JDBC read-only tới DB `sasms`, PostgreSQL 16, 37 bảng và dữ liệu seed mà test kiểm tra. Không chứng minh Repository/JPA hoặc Kho/POS E2E.

## 5. Hồ sơ đã cập nhật

| File | Nội dung cập nhật |
| --- | --- |
| `docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml` | Sửa 3 lỗi flow mapping; bổ sung tag descriptions và 401 nhất quán |
| `docs/sprint0/d52/D52_API_DESIGN_NOTES.md` | Ghi Redocly PASS 0 error/2 warning và phạm vi PENDING |
| `docs/sprint0/D63_CODING_GIT_PR_CONVENTIONS.md` | Sửa thông tin lỗi thời: JDBC driver đã có, migration tool chưa có |
| `docs/sprint0/D64_REPOSITORY_SOURCE_SKELETON.md` | Cập nhật `npm ci` và frontend build PASS |
| `docs/sprint0/D65_DEVELOPMENT_ENVIRONMENT.md` | Dùng `npm ci`; cập nhật dependency/build PASS |
| `docs/sprint0/TV4_SPRINT0_REMEDIATION_REPORT.md` | Báo cáo lệnh, kết quả và giới hạn khắc phục |

D56 được kiểm tra nhưng không sửa vì provider/sandbox/callback cần BA/PO/Architect. D53 không sửa vì visual QA, B10 và PO approval không phải lỗi có thể tự quyết định bằng source local.

## 6. Nội dung còn chờ xác nhận

1. **PM/Architect:** JDK 17 theo workbook hay Java 26/Spring Boot 4.1.1 theo source.
2. **BA/Architect:** Các quyết định API/business còn PENDING và contract callback/payment.
3. **BA/PO/Architect:** Provider, sandbox, callback signature, timeout/retry và máy in của D56.
4. **PO/QA:** Review 52 frame D53, đặc biệt B10 mobile và coverage mobile C-F.
5. **Architect/DBA:** Runtime DB role, ACL và migration baseline.

## 7. Kết luận

Các lỗi kỹ thuật trực tiếp có thể tự sửa đã chuyển trạng thái:

- D52 Redocly: **FAIL → PASS**.
- Frontend dependency/build: **FAIL → PASS**.
- JDBC smoke test trong phiên kiểm tra: **SKIPPED → PASS**.
- Thông tin lỗi thời D63 và bằng chứng D64/D65 đã được cập nhật.

Toàn bộ Sprint 0 vẫn chưa được coi là nghiệm thu hoặc sẵn sàng merge vì D53, D56, baseline môi trường và các quyết định BA/PO/Architect còn PENDING.
