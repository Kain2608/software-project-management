# SASMS — Hồ sơ bàn giao Sprint 0 của TV4 (Vũ Duy Thái — Fullstack Lead)

| Mục | Thông tin |
|---|---|
| **Dự án** | Smart Electronic Supermarket Management System (SASMS / Vendora) |
| **Giai đoạn** | Sprint 0 (S0-19, S0-20, S0-23, S0-24; hỗ trợ S0-17, S0-21) |
| **Người thực hiện** | Vũ Duy Thái (TV4 — Fullstack Lead) |
| **Repository đích** | `D:\software-project-management-an` (nhánh `feat/tv4-sprint0-handoff`) |
| **Repository nguồn tham chiếu** | `D:\smart-electronic-supermarket-sprint0` (nhánh `feat/tv4-sprint0-postgresql`, commit `12918f7`) |
| **Tờ trình quyết định kiến trúc** | [TV4_ARCHITECTURE_DECISIONS_PENDING.md](TV4_ARCHITECTURE_DECISIONS_PENDING.md) (`TV4-S0-ARCH-01..03`: PM An đã đồng ý `A1, B1, C2` theo xác nhận người dùng cung cấp; Architect/DBA Kiên `PENDING`) |
| **Báo cáo tích hợp source Phase 2** | [TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md](TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md) (`30` file source + `2` `.gitignore` nội bộ; Maven & Vite build `PASS` tại repo đích) |
| **Minh chứng thực thi (`evidence/`)** | [evidence/summary.json](evidence/summary.json), [evidence/phase3-precheck.log](evidence/phase3-precheck.log), [evidence/backend-smoke-test-skipped.log](evidence/backend-smoke-test-skipped.log), [evidence/surefire-summary.txt](evidence/surefire-summary.txt), [evidence/frontend-build.log](evidence/frontend-build.log), [evidence/d52-openapi-lint.log](evidence/d52-openapi-lint.log) |
| **Trạng thái bàn giao** | **PHASE 3 — ĐÃ TÍCH HỢP SOURCE & LƯU LOG MINH CHỨNG / DB SMOKE TEST `PENDING DBA APPROVAL` (CHỜ KIÊN CẤP RUNTIME LOGIN THUỘC `sasms_app`, CHƯA `APPROVED`)** |

---

## 1. Danh mục 15 tài liệu TV4 đã có trong repo đích

Toàn bộ 15 file đặt tại `Sprint 0/Fullstack/TV4/docs/sprint0/` (cùng 3 tài liệu điều phối bàn giao tại `Sprint 0/Fullstack/TV4/`: `README.md`, [TV4_ARCHITECTURE_DECISIONS_PENDING.md](TV4_ARCHITECTURE_DECISIONS_PENDING.md) và [TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md](TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md)):

| # | Mã | Tài liệu | Nội dung chính | Trạng thái |
|---|---|---|---|---|
| 1 | **D63** | [docs/sprint0/D63_CODING_GIT_PR_CONVENTIONS.md](docs/sprint0/D63_CODING_GIT_PR_CONVENTIONS.md) | Quy ước tổ chức repo (`Sprint N/<Vai trò>/`), branch, commit, PR, code review, Java/Spring Boot, React, test, secrets, schema, DoD | `PENDING` PM/Architect duyệt |
| 2 | **D64** | [docs/sprint0/D64_REPOSITORY_SOURCE_SKELETON.md](docs/sprint0/D64_REPOSITORY_SOURCE_SKELETON.md) | Mô tả khung source Spring Boot + React/Vite + PostgreSQL 16 đã tích hợp vào `backend/` và `frontend/` ở root repo An (Phase 2) theo đồng ý `A1, B1, C2` của PM An, giữ nguyên baseline DB của Kiên | PM An đã đồng ý `A1, B1, C2`; `PENDING` Kiên (Architect/DBA) & QA duyệt |
| 3 | **D65** | [docs/sprint0/D65_DEVELOPMENT_ENVIRONMENT.md](docs/sprint0/D65_DEVELOPMENT_ENVIRONMENT.md) | Hướng dẫn môi trường Windows Dev, kết quả chạy `mvnw.cmd -o test` và `npm ci` + `npm run build` tại repo An (Phase 2), cách ly Docker bằng `-p tv4_s0_check` và trạng thái chờ cấp login thuộc `sasms_app` | PM An đã đồng ý `B1, C2`; `PENDING` Kiên (Architect/DBA) & QA duyệt |
| 4 | **D52** | [docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml](docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml) | Đặc tả OpenAPI 3.0.3 (12 path, 22 schema) cho Inventory, QR, POS, AI | Redocly `PASS` (0 error, 2 warning); `PENDING` BA/Architect/QA duyệt |
| 5 | **D52** | [docs/sprint0/d52/D52_API_DESIGN_NOTES.md](docs/sprint0/d52/D52_API_DESIGN_NOTES.md) | Ghi chú ràng buộc bất biến, ma trận 12 quyết định API (`API-P01..P12`) và kết quả Redocly | `PENDING` BA/Architect/QA duyệt |
| 6 | **D53** | [docs/sprint0/d53/D53_SCREEN_SPEC.md](docs/sprint0/d53/D53_SCREEN_SPEC.md) | Đặc tả màn hình và trạng thái 6 nhóm A–F (Inventory, Receiving, QR, POS, Payment, Receipt) | `PENDING` BA/PO duyệt |
| 7 | **D53** | [docs/sprint0/d53/D53_DECISION_REGISTER.md](docs/sprint0/d53/D53_DECISION_REGISTER.md) | Sổ đăng ký 10 xung đột/quyết định nghiệp vụ (`DEC-01..DEC-10`) giữa D34, D35, D40, D51 | `PENDING` BA/PO + Architect chốt |
| 8 | **D53** | [docs/sprint0/d53/D53_FIGMA_HANDOFF.md](docs/sprint0/d53/D53_FIGMA_HANDOFF.md) | Cấu trúc 3 page Figma, design token, component và luồng prototype P1–P4 | `PENDING` QA/PO review |
| 9 | **D56** | [docs/sprint0/d56/D56_INTEGRATION_DESIGN_EINVOICE_SMS_PRINTER.md](docs/sprint0/d56/D56_INTEGRATION_DESIGN_EINVOICE_SMS_PRINTER.md) | Thiết kế tích hợp Hóa đơn điện tử, SMS bảo hành, Máy in POS (idempotency, timeout, retry, callback, PII, test matrix `D56-T01..T10`) | `PENDING` chọn provider & BA/PO/Architect/QA duyệt |
| 10 | **D53 Handoff** | [docs/sprint0/handoff/D53_FIGMA_FRAME_MANIFEST.md](docs/sprint0/handoff/D53_FIGMA_FRAME_MANIFEST.md) | Danh mục 52 frame Figma (A01–A07, B01–B10, C01–C10, D01–D09, E01–E08, F01–F08) kèm URL từng node | Visual/prototype `NOT VERIFIED` |
| 11 | **D53 Handoff** | [docs/sprint0/handoff/D53_B10_MOBILE_QA_CHECKLIST.md](docs/sprint0/handoff/D53_B10_MOBILE_QA_CHECKLIST.md) | Checklist QA trực quan riêng cho frame `B10` (Nhập kho mobile — nguy cơ tràn bố cục cuối trang) | `NOT VERIFIED` / `PENDING` |
| 12 | **D53 Handoff** | [docs/sprint0/handoff/D53_PO_REVIEW_EVIDENCE_TEMPLATE.md](docs/sprint0/handoff/D53_PO_REVIEW_EVIDENCE_TEMPLATE.md) | Mẫu ghi nhận kết quả PO review cho 6 nhóm màn hình A–F | Toàn bộ 6 nhóm `PENDING` |
| 13 | **Audit** | [docs/sprint0/TV4_SPRINT0_FINAL_TECHNICAL_AUDIT.md](docs/sprint0/TV4_SPRINT0_FINAL_TECHNICAL_AUDIT.md) | Báo cáo kiểm toán kỹ thuật ban đầu tại repo nguồn (ảnh chụp lịch sử trước khi khắc phục) | Bản ghi lịch sử |
| 14 | **Remediation** | [docs/sprint0/TV4_SPRINT0_REMEDIATION_REPORT.md](docs/sprint0/TV4_SPRINT0_REMEDIATION_REPORT.md) | Báo cáo khắc phục sau kiểm toán tại repo nguồn (Redocly 0 error, `npm ci` + Vite build PASS, JDBC smoke PASS) | Bản ghi lịch sử |
| 15 | **Review Pkg** | [docs/sprint0/handoff/TV4_SPRINT0_REVIEW_PACKAGE.md](docs/sprint0/handoff/TV4_SPRINT0_REVIEW_PACKAGE.md) | Hồ sơ tổng hợp trình review (so sánh JDK 17 vs Java 26, checklist BA/PO/Architect/QA, danh sách `PENDING`) | `DRAFT - NOT APPROVED` |

> File `docs/sprint0/handoff/TV4_SPRINT0_STATUS_AND_APPROVAL.md` tại worktree nguồn là file untracked cũ (trước khi có `TV4_SPRINT0_REVIEW_PACKAGE.md`) nên được phân loại `EXCLUDE`, không sao chép sang repo đích.

---

## 2. Link Figma D53

- **File thiết kế Figma:** https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55
- **Cấu trúc 3 page (giới hạn gói Figma Starter):**
  1. `01 · Foundations & Traceability`
  2. `02 · Inventory & QR`
  3. `03 · POS & Prototype`
- **Số lượng frame trong manifest:** 52 frame thuộc 6 nhóm A–F (chi tiết link từng node xem [D53_FIGMA_FRAME_MANIFEST.md](docs/sprint0/handoff/D53_FIGMA_FRAME_MANIFEST.md)).
- **Giới hạn hiện tại:** Đã ghi nhận 52 link node, nhưng **chưa có QA trực quan độc lập (`NOT VERIFIED`)**, frame `B10` cần kiểm tra tràn bố cục cuối trang, các nhóm C–F cần rà soát độ phủ mobile theo NFR-07 và **chưa có xác nhận/comment phê duyệt của PO (`PENDING`)**.

---

## 3. Trạng thái source skeleton và cơ sở dữ liệu

### 3.1 Những gì ĐÃ CÓ SẴN tại repo đích của An (`ALREADY EXISTS`)

Bộ cơ sở dữ liệu PostgreSQL 16 do **Trần Trung Kiên** bàn giao trong commit `ca24ffe` đã có đầy đủ ở root repository đích và **không bị TV4 chỉnh sửa hay ghi đè**:

- `database/schema.sql` (SHA-256 `66AACDA1101C20ED0CC6C80062D93EF917660B4F6E825FA124F536DC2A1FCD96` — trùng 100% với bản TV4 dùng ở repo nguồn)
- `database/seed.sql` (SHA-256 `8ED00DA989CCB6700867636D7D5616F0E90F2422086205BDDFBE79FCD783D385` — trùng 100%)
- `docker-compose.yml` (SHA-256 `31092C806DCBD4290AF8D7924EC53FA5EFCDCAE09AD67FBA03AF4CCB687BBC3C` — trùng 100%) và `.env.example`
- Các artifact mở rộng của Kiên: `database/runtime-roles.sql`, `database/checks.sql`, `database/schema.dbml`, `database/erd.mmd`, `database/tests/`, `database/evidence/`, `scripts/validate_db.py`, `scripts/generate_db_artifacts.py`, `.github/workflows/database.yml` và `database/README.md`.

### 3.2 Những gì ĐÃ TÍCH HỢP vào repo đích ở Phase 2 (`backend/` & `frontend/`)

Sau khi PM Trịnh Quốc An đồng ý các phương án **A1** (`TV4-S0-ARCH-01`: đặt `backend/` và `frontend/` tại root), **B1** (`TV4-S0-ARCH-02`: giữ `Java 26 + Spring Boot 4.1.1`) và **C2** (`TV4-S0-ARCH-03`: runtime backend dùng DB login thuộc nhóm `sasms_app`, không dùng `sasms_owner`), toàn bộ **30 file tracked** của khung mã nguồn ứng dụng cùng **2 file `.gitignore` nội bộ** đã được tích hợp sang root `D:\software-project-management-an` (chi tiết đối chiếu SHA-256 xem [TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md](TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md)):

- **Base skeleton** (từ commit `8026553` / `84a4558` tại repo nguồn, khớp 100% SHA-256):
  - `backend/` (`16` file source + `backend/.gitignore`): Maven Wrapper (`.mvn/`, `mvnw`, `mvnw.cmd`), `pom.xml` (Spring Boot `4.1.1`, Java `26`), `SmartElectronicSupermarketApplication.java`, `HealthController` (`/api/health`), `PosController`/`PosService`/`ModuleStatusResponse` (`/api/pos/status`), `SerialImeiController`/`SerialImeiService`/`SerialImeiStatusResponse` (`/api/serial-imei/status`), `application.properties`, `SmartElectronicSupermarketApplicationTests.java`.
  - `frontend/` (`14` file source + `frontend/.gitignore`): `package.json`, `package-lock.json`, `vite.config.js`, `index.html`, `.env.example` và `src/` (`main.jsx`, `app/App.jsx`, `app/app.css`, `features/pos/PosPage.jsx`, `features/pos/posApi.js`, `features/serialImei/SerialImeiPanel.jsx`, `features/serialImei/serialImeiApi.js`, `shared/api/httpClient.js`, `shared/components/StatusCard.jsx`).
- **Delta PostgreSQL Sprint 0** (từ commit `12918f7` tại repo nguồn, khớp 100% SHA-256):
  - `backend/pom.xml` (thêm dependency `org.postgresql:postgresql` scope `runtime`).
  - `backend/src/main/resources/application-postgresql.properties` (`SASMS_DB_URL`, `SASMS_DB_USERNAME`, `SASMS_DB_PASSWORD`, `spring.sql.init.mode=never` — không có default tài khoản `sasms_owner`).
  - `backend/src/test/java/com/smartelectronicsupermarket/PostgreSqlConnectionSmokeTest.java` (read-only transaction, khóa chỉ cho loopback port `5432` và DB `/sasms`, kiểm tra PostgreSQL `16.x`, 37 bảng, 9 roles, 2 products rồi rollback).

---

## 4. Kết quả kiểm thử (tại repo nguồn vs tại repo đích) và hạn chế

| Hạng mục kiểm tra | Kết quả tại repo nguồn (`12918f7`, 07/11/2026) | Kết quả tại repo đích (`feat/tv4-sprint0-handoff`, Phase 2 & 3 — 07/11/2026) | Minh chứng tại `Sprint 0/Fullstack/TV4/evidence/` & Giới hạn |
|---|---|---|---|
| **D52 YAML parse & `$ref` / `operationId` / `401`** | `PASS` (12 paths, 22 schemas, 97 `$ref`, 12 `operationId`, 0 thiếu `401`) | **`PASS` (đã chạy lại trực tiếp trên file ở repo An)** | Chỉ xác nhận đặc tả hợp lệ; việc cài đặt controller `/api/v1/*` là **`DEFERRED TO FUTURE SPRINT`** |
| **D52 Redocly OpenAPI lint** | `PASS` (`0 errors, 2 warnings`) | **`PASS` (`0 errors, 2 warnings`, Redocly CLI `2.76.0`)** | [evidence/d52-openapi-lint.log](evidence/d52-openapi-lint.log) — còn 2 warning có chủ ý (`info-license`, `localhost` server) |
| **Backend unit & context test (`mvnw.cmd -o test`)** | `PASS` (`BUILD SUCCESS`) | **`PASS` (`BUILD SUCCESS`, `Tests run: 2, Failures: 0, Errors: 0, Skipped: 1`, `4.472 s`)** | [evidence/surefire-summary.txt](evidence/surefire-summary.txt) — `SmartElectronicSupermarketApplicationTests` `PASS` |
| **Backend smoke test guard (`mvnw.cmd -o "-Dtest=PostgreSqlConnectionSmokeTest" test`)** | `SKIPPED` (khi tắt env) | **`SKIPPED` (`Tests run: 1, Failures: 0, Errors: 0, Skipped: 1`)** | [evidence/backend-smoke-test-skipped.log](evidence/backend-smoke-test-skipped.log) — tự động skip an toàn khi không bật `SASMS_PG_SMOKE_ENABLED` |
| **PostgreSQL JDBC smoke test với DB thật (`PostgreSqlConnectionSmokeTest`)** | `PASS` (`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`) | **`NOT RUN — PENDING DBA APPROVAL`** | [evidence/phase3-precheck.log](evidence/phase3-precheck.log) — `sasms_app` đủ quyền `SELECT` 37 bảng nhưng là `NOLOGIN`; chờ DBA Kiên cấp runtime login kế thừa `sasms_app` (không dùng `sasms_owner`) |
| **Backend startup với profile `postgresql`** | `PASS` (3 endpoint skeleton trả HTTP `200`) | **`NOT RUN — PENDING DBA APPROVAL`** | Chờ DBA Kiên cấp login thuộc `sasms_app`; ba endpoint chỉ trả `SKELETON_READY`, không truy vấn DB |
| **Frontend install & build (`npm.cmd ci --offline` + `npm.cmd run build`)** | `PASS` (88 packages, Vite `5.4.21`, 92 modules, 899 ms) | **`PASS` (88 packages, `0 vulnerabilities`, Vite `5.4.21`, 92 modules)** | [evidence/frontend-build.log](evidence/frontend-build.log) — chưa có script unit/UI test (`DEFERRED TO FUTURE SPRINT`) |
| **PostgreSQL 16 schema/seed runtime** | `PASS` trên project cô lập `sasms_s0_check` | Đã có `database/evidence/validation.log` của Kiên; TV4 **`NOT RUN — PENDING DBA APPROVAL`** | Không chạy hoặc thay đổi PostgreSQL khi chưa có phê duyệt của DBA Kiên |
| **Kho/POS/QR/AI CRUD & tích hợp D56** | `NOT RUN` | **`DEFERRED TO FUTURE SPRINT`** | Ngoài phạm vi Sprint 0 (chỉ thực hiện từ Sprint 1+) |

---

## 5. Công việc cần các thành viên review và phê duyệt

> **Tài liệu đề xuất quyết định kỹ thuật trọng tâm:** Xem chi tiết phân tích hiện trạng, các phương án, ưu/nhược điểm, ảnh hưởng tới source/build/CI/DB và bảng ký duyệt tại **[TV4_ARCHITECTURE_DECISIONS_PENDING.md](TV4_ARCHITECTURE_DECISIONS_PENDING.md)** và báo cáo kết quả tích hợp Phase 2 tại **[TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md](TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md)**.

### 5.1 Trịnh Quốc An (PM / Scrum Master)

- [x] **Vị trí tích hợp source (`TV4-S0-ARCH-01` — Phương án A1):** Đã đồng ý đặt `backend/` và `frontend/` tại root `D:\software-project-management-an` kèm `.gitignore` nội bộ (theo xác nhận người dùng cung cấp ở Phase 2).
- [x] **Baseline công nghệ (`TV4-S0-ARCH-02` — Phương án B1):** Đã đồng ý giữ **Java 26 + Spring Boot 4.1.1** theo source hiện có (theo xác nhận người dùng cung cấp ở Phase 2; chờ Architect Kiên đồng xác nhận vào `ARC-06` trong D50).
- [x] **Chính sách tài khoản DB runtime (`TV4-S0-ARCH-03` — Phương án C2):** Đã đồng ý runtime backend sử dụng DB login thuộc nhóm quyền `sasms_app` (theo xác nhận người dùng cung cấp ở Phase 2; chờ DBA Kiên cấp phát login).
- [ ] **Quy ước làm việc (`D63`) & `info.license` (`D52`) & phạm vi `D56`:** Duyệt cú pháp đặt tên branch/commit, số approval tối thiểu, quy trình merge PR, quy ước thư mục `Sprint N/<Vai trò>/`, `info.license` cho D52 và chủ sở hữu payment adapter/callback trong D56.

### 5.2 Trần Trung Kiên (Architect / DBA) — `PARTIALLY APPROVED`

- [x] **Đồng xác nhận kiến trúc & migration (`TV4-S0-ARCH-01`, `TV4-S0-ARCH-02` / `D50` `ARC-06` / `D63` / `D64`):** Đã phê duyệt cấu trúc `backend/` + `frontend/` tại root repository (A1), giữ nguyên baseline Java 26 + Spring Boot 4.1.1 (B1) và cập nhật chính thức vào D50 v1.1 (mục 5.1, 5.2 và `ARC-06`) ngày 11/11/2026.
- [x] **Xác nhận chính sách tài khoản DB runtime thuộc `sasms_app` (`TV4-S0-ARCH-03` / `D54` / `D50` mục 5.3):** Xác nhận bắt buộc dùng login kế thừa group role `sasms_app`, nghiêm cấm cấu hình `sasms_owner` làm tài khoản chạy ứng dụng; cơ chế cấp phát tài khoản login test thực hiện qua script (như `scripts/provision_s1_db.ps1`) hoặc bí mật môi trường cục bộ.
- [ ] **Review hợp đồng OpenAPI (`S0-19` / `D52`):** Đối chiếu 12 operation trong `D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml` với 37 bảng PostgreSQL và 4 hàm giao dịch (`receive_device`, `reserve_order`, `complete_sale`, `cancel_order`); chốt `API-P01..P07`, `API-P10`.

### 5.3 Nguyễn Quang Huy (BA / PO) — `PENDING`

- [ ] **Chốt 10 xung đột nghiệp vụ (`D53_DECISION_REGISTER.md` & `D52_API_DESIGN_NOTES.md`):**
  - `DEC-01`: Mapping tên role giữa D34 và 9 role trong D54/seed.
  - `DEC-02`: Tên trạng thái tồn kho (`AVAILABLE` trong D34 so với `IN_STOCK` trong D51/schema).
  - `DEC-03`: Quy tắc chuẩn hóa Serial (case-sensitive giữ khoảng trắng theo D35 hay uppercase/trim theo D51).
  - `DEC-04`: Hỗ trợ `IMEI2` trên giao diện và bảng `devices`.
  - `DEC-05`: Thời hạn giữ hàng (`TTL` 10 phút POS / 30 phút online / `PAYMENT_PENDING`).
  - `DEC-06`: Nhập kho nguyên phiếu hay nhận từng phần.
  - `DEC-07`: Định dạng payload/token của mã QR.
  - `DEC-08`: Phương thức thanh toán và xử lý trạng thái `PENDING/UNKNOWN`.
  - `DEC-09`: Thuế, giảm giá, làm tròn tiền tệ và nội dung biên nhận 80 mm.
  - `DEC-10`: Trường chất lượng `GOOD/DEFECTIVE` khi nhập kho và phạm vi `FR-09` (AI tóm tắt thông số).
- [ ] **PO review từng nhóm màn hình Figma (`S0-20` / `D53`):** Mở từng nhóm A–F từ [D53_FIGMA_FRAME_MANIFEST.md](docs/sprint0/handoff/D53_FIGMA_FRAME_MANIFEST.md), ghi nhận xét và điền kết quả vào [D53_PO_REVIEW_EVIDENCE_TEMPLATE.md](docs/sprint0/handoff/D53_PO_REVIEW_EVIDENCE_TEMPLATE.md).

### 5.4 Phạm Đại Phát (QA / Documentation Lead) — `PENDING`

- [ ] **QA trực quan Figma D53:** Kiểm tra 52 frame theo [D53_FIGMA_FRAME_MANIFEST.md](docs/sprint0/handoff/D53_FIGMA_FRAME_MANIFEST.md), ưu tiên kiểm tra frame `B10` theo [D53_B10_MOBILE_QA_CHECKLIST.md](docs/sprint0/handoff/D53_B10_MOBILE_QA_CHECKLIST.md), kiểm tra độ phủ responsive mobile cho các nhóm C–F và luồng prototype P1–P4.
- [ ] **Review khả năng kiểm thử D52 & D56:** Rà soát các mã lỗi HTTP (`400/401/403/404/409/422/503`) trong D52 và ma trận 10 kịch bản kiểm thử tích hợp `D56-T01..T10` trong D56.
- [ ] **Xác nhận bằng chứng môi trường D64/D65:** Nghiệm thu báo cáo [TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md](TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md) và kết quả chạy JDBC smoke test bằng tài khoản thuộc `sasms_app` ở Phase 3.

---

## 6. Trạng thái các hạng mục sau Phase 3 (Chuẩn bị DB Smoke Test & Lưu minh chứng)

| Nhóm hạng mục | Trạng thái sau Phase 3 | Bước tiếp theo / điều kiện thực hiện |
|---|---|---|
| **Backend skeleton (`16` file + `backend/.gitignore`) & Frontend skeleton (`14` file + `frontend/.gitignore`)** | **ĐÃ TÍCH HỢP (`INTEGRATED`)** tại root `D:\software-project-management-an`; build/test offline đều `PASS` | Chờ Kiên (Architect) & Phát (QA) review trên nhánh `feat/tv4-sprint0-handoff` |
| **Log minh chứng tại repo đích (`Sprint 0/Fullstack/TV4/evidence/`)** | **ĐÃ LƯU (`6` file)** gồm `summary.json`, `phase3-precheck.log`, `backend-smoke-test-skipped.log`, `surefire-summary.txt`, `frontend-build.log`, `d52-openapi-lint.log` | Bổ sung log JDBC smoke test với DB thật sau khi DBA Kiên cấp login thuộc `sasms_app` |
| **DB login runtime thuộc group role `sasms_app` & live JDBC smoke test tại repo đích** | **`PENDING DBA APPROVAL`** — `database/runtime-roles.sql` tạo `sasms_app` có đủ quyền `USAGE` + `SELECT` 37 bảng nhưng mang thuộc tính `NOLOGIN` | Kiên (DBA) phê duyệt và cấp phát runtime login kế thừa `sasms_app`; sau đó chạy `PostgreSqlConnectionSmokeTest` trên Docker project cô lập `-p tv4_s0_check` |
| **Bằng chứng PO duyệt từng nhóm màn hình D53 & QA frame B10** | **`PENDING`** trong `Sprint 0/Fullstack/TV4/docs/sprint0/handoff/` | Huy (PO) và Phát (QA) thực hiện review trên Figma và cập nhật vào `D53_PO_REVIEW_EVIDENCE_TEMPLATE.md` / `D53_B10_MOBILE_QA_CHECKLIST.md` |
| **Tài liệu provider / cấu hình sandbox D56** | **`PENDING`** (chưa chọn nhà cung cấp e-invoice/SMS/máy in) | An + Huy + Kiên chốt nhà cung cấp hoặc xác nhận dùng mock/stub cho MVP |
| **Triển khai API CRUD `/api/v1/*`, JPA/Flyway, nghiệp vụ Kho/QR/POS/AI, tích hợp thật D56** | **`DEFERRED TO FUTURE SPRINT`** | Nằm ngoài phạm vi Sprint 0; chỉ triển khai từ Sprint 1+ sau khi D52, D53, D56 và `DEC-01..DEC-10` được ký duyệt |
