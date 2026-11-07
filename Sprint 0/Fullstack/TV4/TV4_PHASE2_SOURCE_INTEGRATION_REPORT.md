# SASMS Sprint 0 — Báo cáo Phase 2: Tích hợp Backend & Frontend Skeleton sang Repo PM An

| Mục | Nội dung |
|---|---|
| **Dự án** | Smart Electronic Supermarket Management System (SASMS / Vendora) |
| **Giai đoạn** | Sprint 0 — Phase 2: Integrate Backend/Frontend Skeleton |
| **Người thực hiện** | Vũ Duy Thái (TV4 — Fullstack Lead) |
| **Ngày thực hiện** | 07/11/2026 |
| **Repository nguồn** | `D:\smart-electronic-supermarket-sprint0` (nhánh `feat/tv4-sprint0-postgresql`, commit `12918f7`) |
| **Repository đích** | `D:\software-project-management-an` (nhánh `feat/tv4-sprint0-handoff`, từ `a2c8b64`) |
| **Xác nhận từ PM Trịnh Quốc An** | Đã đồng ý **A1** (đặt `backend/` & `frontend/` tại root), **B1** (giữ `Java 26` + `Spring Boot 4.1.1`) và **C2** (runtime backend dùng DB login thuộc nhóm quyền `sasms_app`, không dùng `sasms_owner`) theo thông tin người dùng cung cấp |
| **Trạng thái Architect / DBA (Trần Trung Kiên)** | **`PENDING`** (chưa có bằng chứng phê duyệt riêng của Kiên; tuyệt đối không tự ghi `APPROVED`) |

---

## 1. Kiểm tra an toàn trước khi sao chép (Bước 1 & Bước 3)

1. **Kiểm tra xung đột tại repo đích (`D:\software-project-management-an`):**
   - Trước khi copy, cả hai đường dẫn `D:\software-project-management-an\backend` và `D:\software-project-management-an\frontend` đều **chưa tồn tại** (`False`).
   - Không có bất kỳ nguy cơ ghi đè file nào của Kiên, An hay các thành viên khác.
2. **Bảo vệ tuyệt đối tài nguyên của repo An:**
   - Không chạm vào `database/`, `docker-compose.yml`, `.env.example`, `scripts/`, `.github/`, `.gitignore` (root), `.gitattributes`, `README.md` (root), `Sprint 0/DB/`, `Sprint 0/PM/`, `Sprint 0/BA/`, `Sprint 0/AI/`, `Sprint 0/Tester/`, `Sprint 1/`, `Tiền Sprint/`.
   - Không thực hiện `git cherry-pick 12918f7`.
3. **Phạm vi Sprint 0 nghiêm ngặt:**
   - Chỉ chuyển nguyên trạng bộ khung source skeleton có sẵn và kiểm tra khả năng build.
   - Không viết thêm CRUD/API nghiệp vụ, không triển khai tính năng QR/POS/AI của Sprint 1+, không mở rộng schema/kiến trúc (`DEFERRED TO FUTURE SPRINT`).

---

## 2. Danh sách 30 file source skeleton đã chuyển (Bước 2)

Toàn bộ **30/30 file được Git track** trong `backend/` (16 file) và `frontend/` (14 file) tại commit `12918f7` của repo nguồn đã được sao chép sang root `D:\software-project-management-an\`. Đã đối chiếu SHA-256 từng file giữa hai repo: **0 file lệch (`SHA256 mismatch count = 0`)**.

### 2.1. Thư mục `backend/` (16 file nguồn + 1 file `.gitignore` nội bộ mới)

| # | Đường dẫn tại repo đích | Nguồn gốc | Kiểm tra SHA-256 so với nguồn |
|---|---|---|---|
| 1 | `backend/.mvn/wrapper/maven-wrapper.properties` | Base skeleton (`8026553`) | Khớp 100% |
| 2 | `backend/mvnw` | Base skeleton (`8026553`) | Khớp 100% |
| 3 | `backend/mvnw.cmd` | Base skeleton (`8026553`) | Khớp 100% |
| 4 | `backend/pom.xml` | Base + Delta PostgreSQL (`12918f7`, Java `26`, Spring Boot `4.1.1`) | Khớp 100% |
| 5 | `backend/src/main/java/com/smartelectronicsupermarket/SmartElectronicSupermarketApplication.java` | Base skeleton (`8026553`) | Khớp 100% |
| 6 | `backend/src/main/java/com/smartelectronicsupermarket/common/controller/HealthController.java` | Base skeleton (`84a4558`) | Khớp 100% |
| 7 | `backend/src/main/java/com/smartelectronicsupermarket/pos/controller/PosController.java` | Base skeleton (`84a4558`) | Khớp 100% |
| 8 | `backend/src/main/java/com/smartelectronicsupermarket/pos/dto/ModuleStatusResponse.java` | Base skeleton (`84a4558`) | Khớp 100% |
| 9 | `backend/src/main/java/com/smartelectronicsupermarket/pos/service/PosService.java` | Base skeleton (`84a4558`) | Khớp 100% |
| 10 | `backend/src/main/java/com/smartelectronicsupermarket/serialimei/controller/SerialImeiController.java` | Base skeleton (`84a4558`) | Khớp 100% |
| 11 | `backend/src/main/java/com/smartelectronicsupermarket/serialimei/dto/SerialImeiStatusResponse.java` | Base skeleton (`84a4558`) | Khớp 100% |
| 12 | `backend/src/main/java/com/smartelectronicsupermarket/serialimei/service/SerialImeiService.java` | Base skeleton (`84a4558`) | Khớp 100% |
| 13 | `backend/src/main/resources/application.properties` | Base skeleton (`8026553`) | Khớp 100% |
| 14 | `backend/src/main/resources/application-postgresql.properties` | Delta PostgreSQL (`12918f7`) | Khớp 100% |
| 15 | `backend/src/test/java/com/smartelectronicsupermarket/SmartElectronicSupermarketApplicationTests.java` | Base skeleton (`8026553`) | Khớp 100% |
| 16 | `backend/src/test/java/com/smartelectronicsupermarket/PostgreSqlConnectionSmokeTest.java` | Delta PostgreSQL (`12918f7`) | Khớp 100% |
| 17 | `backend/.gitignore` *(mới)* | Bổ sung ở Phase 2 để chặn `target/`, `*.log`, IDE, `.env*` mà không sửa `.gitignore` root | File mới |

### 2.2. Thư mục `frontend/` (14 file nguồn + 1 file `.gitignore` nội bộ mới)

| # | Đường dẫn tại repo đích | Nguồn gốc | Kiểm tra SHA-256 so với nguồn |
|---|---|---|---|
| 1 | `frontend/.env.example` | Base skeleton (`84a4558`) | Khớp 100% |
| 2 | `frontend/index.html` | Base skeleton (`8026553`) | Khớp 100% |
| 3 | `frontend/package.json` | Base skeleton (`8026553`, React `^18.3.1`, Vite `^5.4.10`, Axios `^1.7.7`) | Khớp 100% |
| 4 | `frontend/package-lock.json` | Base skeleton (`84a4558`) | Khớp 100% |
| 5 | `frontend/vite.config.js` | Base skeleton (`8026553`) | Khớp 100% |
| 6 | `frontend/src/main.jsx` | Base skeleton (`8026553`) | Khớp 100% |
| 7 | `frontend/src/app/App.jsx` | Base skeleton (`84a4558`) | Khớp 100% |
| 8 | `frontend/src/app/app.css` | Base skeleton (`84a4558`) | Khớp 100% |
| 9 | `frontend/src/features/pos/PosPage.jsx` | Base skeleton (`84a4558`) | Khớp 100% |
| 10 | `frontend/src/features/pos/posApi.js` | Base skeleton (`84a4558`) | Khớp 100% |
| 11 | `frontend/src/features/serialImei/SerialImeiPanel.jsx` | Base skeleton (`84a4558`) | Khớp 100% |
| 12 | `frontend/src/features/serialImei/serialImeiApi.js` | Base skeleton (`84a4558`) | Khớp 100% |
| 13 | `frontend/src/shared/api/httpClient.js` | Base skeleton (`84a4558`) | Khớp 100% |
| 14 | `frontend/src/shared/components/StatusCard.jsx` | Base skeleton (`84a4558`) | Khớp 100% |
| 15 | `frontend/.gitignore` *(mới)* | Bổ sung ở Phase 2 để chặn `node_modules/`, `dist/`, `build/`, `.env*` (giữ `!.env.example`) | File mới |

**Các mục đã loại trừ không sao chép:** `.env` thật, `backend/target/` từ repo nguồn, `frontend/node_modules/` từ repo nguồn, `frontend/dist/`, logs, IDE cache, `database/README.md` bản rút gọn của TV4 và file untracked cũ `TV4_SPRINT0_STATUS_AND_APPROVAL.md`.

---

## 3. Đối chiếu cấu hình runtime & bảo mật DB (Bước 4)

- **Java & Spring Boot (`B1`):** `backend/pom.xml` giữ nguyên `<java.version>26</java.version>` và `spring-boot-starter-parent` `4.1.1`.
- **Cấu hình kết nối PostgreSQL (`C2`):** `backend/src/main/resources/application-postgresql.properties` đọc hoàn toàn từ biến môi trường:
  - `spring.datasource.url=${SASMS_DB_URL:jdbc:postgresql://127.0.0.1:5432/sasms}`
  - `spring.datasource.username=${SASMS_DB_USERNAME}` (không có giá trị mặc định `sasms_owner`)
  - `spring.datasource.password=${SASMS_DB_PASSWORD}` (không hardcode mật khẩu)
  - `spring.sql.init.mode=never` (không tự động chạy DDL hay seed)
- **Đối chiếu `database/runtime-roles.sql` và `D54` của Kiên:**
  - `runtime-roles.sql` tạo group role `sasms_app` (`NOLOGIN`, có `SELECT` trên toàn bộ bảng/view trong schema `sasms`, quyền `INSERT` giới hạn cột trên `sales_orders`, `INSERT/UPDATE/DELETE` trên `sales_order_items`/`order_devices`/`payments` và `EXECUTE` trên 4 hàm giao dịch).
  - Vì một login cụ thể kế thừa `sasms_app` chưa được provision sẵn trong script gốc của Kiên, việc tạo login runtime thuộc `sasms_app` được giữ ở trạng thái **`PENDING`** (không tự ý sửa `runtime-roles.sql` hay chạy lệnh vào DB trong Phase 2).

---

## 4. Kết quả build và kiểm thử an toàn tại repo đích (Bước 5)

| Hạng mục | Lệnh thực thi tại `D:\software-project-management-an` | Kết quả thực tế | Chi tiết / Giới hạn |
|---|---|---|---|
| **Backend Maven compile & test** | `cd backend; .\mvnw.cmd -o test` | **`PASS` (`BUILD SUCCESS`, exit code `0`)** | • Biên dịch 8 class chính và 2 class test bằng Java `26.0.2.1`.<br>• `SmartElectronicSupermarketApplicationTests.contextLoads`: **`PASS`** (Spring Boot `4.1.1` context khởi tạo thành công trong `1.124 s`).<br>• `PostgreSqlConnectionSmokeTest`: **`SKIPPED`** (`Tests run: 2, Failures: 0, Errors: 0, Skipped: 1`) do biến `SASMS_PG_SMOKE_ENABLED` không bật theo đúng quy tắc an toàn. |
| **Frontend cài đặt dependency tái lập** | `cd frontend; npm.cmd ci --offline` | **`PASS` (exit code `0`)** | • Cài đặt chính xác 88 package từ `frontend/package-lock.json` trong `7 s`, `found 0 vulnerabilities`.<br>• Không thay đổi `package.json` hoặc `package-lock.json`. |
| **Frontend production build** | `cd frontend; npm.cmd run build` | **`PASS` (exit code `0`)** | • Vite `5.4.21` transform `92 modules`, sinh `dist/index.html` và bundle JS/CSS trong `835 ms`.<br>• Thư mục `node_modules/` và `dist/` được `frontend/.gitignore` loại trừ hoàn toàn khỏi `git status`. |
| **D52 OpenAPI YAML parse & Redocly lint** | `npx.cmd --offline -y @redocly/cli lint "Sprint 0/Fullstack/TV4/docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml"` | **`PASS` (exit code `0`)** | • `0 errors, 2 warnings` (2 warning `info-license` và `localhost` server đang `PENDING`). |
| **Docker / PostgreSQL runtime & JDBC smoke test với DB thật** | Không chạy trong Phase 2 | **`NOT RUN` (An toàn)** | • Tuân thủ yêu cầu Bước 5: không tự khởi động Docker/database và không chạy JDBC smoke test khi chưa có kết nối kiểm thử cô lập với login thuộc `sasms_app` được xác nhận. |

---

## 5. Tổng hợp các file đã tạo mới và cập nhật trong Phase 2 (Bước 6)

### 5.1. File mã nguồn & `.gitignore` nội bộ mới tại root (`32` file)
- `backend/**`: 16 file source skeleton sao chép nguyên bản + `backend/.gitignore`.
- `frontend/**`: 14 file source skeleton sao chép nguyên bản + `frontend/.gitignore`.

### 5.2. File hồ sơ trong `Sprint 0/Fullstack/TV4/` đã cập nhật / tạo mới (`5` file)
1. [Sprint 0/Fullstack/TV4/TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md](TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md) *(mới — báo cáo này)*
2. [Sprint 0/Fullstack/TV4/README.md](README.md) *(cập nhật trạng thái Phase 2, kết quả build/test tại repo đích và link tới báo cáo Phase 2)*
3. [Sprint 0/Fullstack/TV4/TV4_ARCHITECTURE_DECISIONS_PENDING.md](TV4_ARCHITECTURE_DECISIONS_PENDING.md) *(ghi nhận PM An đã đồng ý `A1`, `B1`, `C2`; giữ Architect/DBA Kiên ở trạng thái `PENDING`)*
4. [Sprint 0/Fullstack/TV4/docs/sprint0/D64_REPOSITORY_SOURCE_SKELETON.md](docs/sprint0/D64_REPOSITORY_SOURCE_SKELETON.md) *(cập nhật cấu trúc repo đích đã có `backend/` & `frontend/` và kết quả build/test Phase 2)*
5. [Sprint 0/Fullstack/TV4/docs/sprint0/D65_DEVELOPMENT_ENVIRONMENT.md](docs/sprint0/D65_DEVELOPMENT_ENVIRONMENT.md) *(cập nhật trạng thái đã copy source, nguyên tắc `C2` dùng login thuộc `sasms_app` và kết quả kiểm chứng Phase 2)*

---

## 6. Các vấn đề còn `PENDING` và mục chuyển sang Sprint sau (`DEFERRED TO FUTURE SPRINT`)

### 6.1. Còn `PENDING` trong phạm vi bàn giao Sprint 0
1. **Architect / DBA review (`Trần Trung Kiên` — `PENDING`):**
   - Xác nhận đồng thuận đối với 3 quyết định `TV4-S0-ARCH-01` (`A1`), `TV4-S0-ARCH-02` (`B1`), `TV4-S0-ARCH-03` (`C2`) mà PM An đã đồng ý.
   - Review hợp đồng OpenAPI D52 (`S0-19`) đối chiếu với 37 bảng PostgreSQL và 4 hàm giao dịch.
2. **Provision login thuộc `sasms_app` & chạy JDBC Smoke Test tại repo đích (`PENDING` Phase 3):**
   - Thống nhất câu lệnh/cách thức provision một login local kế thừa `sasms_app` trên project Docker cô lập (`-p tv4_s0_check`) để chạy `PostgreSqlConnectionSmokeTest` tại repo đích mà không dùng `sasms_owner`.
3. **BA / PO & QA review đối với D52, D53, D56 (`Nguyễn Quang Huy`, `Phạm Đại Phát` — `PENDING`):**
   - Chốt 10 xung đột nghiệp vụ `DEC-01..DEC-10` và `API-P01..API-P12`.
   - QA trực quan 52 frame Figma (đặc biệt frame `B10` mobile và responsive nhóm C–F) và PO điền xác nhận vào `D53_PO_REVIEW_EVIDENCE_TEMPLATE.md`.
   - Chốt nhà cung cấp / phạm vi sandbox cho D56.

### 6.2. Ngoài phạm vi Sprint 0 (`DEFERRED TO FUTURE SPRINT` — Sprint 1+)
- Triển khai Entity/Repository/data-access thực tế và gọi 4 hàm giao dịch PostgreSQL (`receive_device`, `reserve_order`, `complete_sale`, `cancel_order`).
- Triển khai các API CRUD nghiệp vụ `/api/v1/*` trong D52 cho Kho, QR, POS, Thanh toán, Biên nhận và AI.
- Triển khai giao diện nghiệp vụ React theo 52 frame Figma D53.
- Tích hợp công cụ migration versioned (Flyway/Liquibase), authentication/RBAC filter và worker/adapter tích hợp D56.

---

## 7. Các bước đề xuất cho Phase 3

1. **Kiên (Architect/DBA) review** cấu trúc `backend/`, `frontend/` vừa tích hợp và xác nhận cách tạo login test kế thừa `sasms_app` trên container cô lập.
2. **Thực thi kiểm chứng kết nối DB cô lập (khi được phép chạy Docker ở Phase 3):**
   - Kiểm tra port `5432`, khởi động `docker compose -p tv4_s0_check up -d --wait`, nạp `schema.sql`, `seed.sql`, `runtime-roles.sql`.
   - Tạo login local thuộc nhóm `sasms_app` (trong process/container test, không sửa file SQL của Kiên) và chạy `.\mvnw.cmd -o "-Dtest=PostgreSqlConnectionSmokeTest" test` với `SASMS_PG_SMOKE_ENABLED=true`.
   - Lưu raw log kiểm chứng (đã che secret) vào `Sprint 0/Fullstack/TV4/evidence/`.
3. **Thu thập phản hồi review D52, D53, D56, D63–D65** từ An, Kiên, Huy, Phát; sau khi được phép mới thực hiện `git add`, `git commit` và mở PR trên nhánh `feat/tv4-sprint0-handoff`.
