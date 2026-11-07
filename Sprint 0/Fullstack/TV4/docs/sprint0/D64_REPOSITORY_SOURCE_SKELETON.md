# D64 — Repository và khung source Spring Boot + React/Vite + PostgreSQL 16

**Trạng thái bàn giao tại repo đích:** **PHASE 2 — ĐÃ TÍCH HỢP SOURCE SKELETON (`backend/` & `frontend/`) TẠI ROOT REPO AN / CHỜ ARCHITECT & DBA REVIEW**.
- Theo xác nhận của PM **Trịnh Quốc An**: đã đồng ý **A1** (đặt `backend/` và `frontend/` tại root `D:\software-project-management-an\`, mỗi thư mục có `.gitignore` riêng để không sửa `.gitignore` root), **B1** (giữ nguyên `Java 26` + `Spring Boot 4.1.1`) và **C2** (runtime backend dùng DB login thuộc nhóm quyền `sasms_app`, không dùng `sasms_owner`).
- Toàn bộ 30 file source skeleton (16 file `backend/` + 14 file `frontend/`) đã được sao chép nguyên bản từ repo nguồn (`12918f7`, khớp SHA-256 100%) kèm `backend/.gitignore` và `frontend/.gitignore`.
- Phần review/phê duyệt của Architect/DBA (**Trần Trung Kiên**) và việc provision login thực tế thuộc `sasms_app` trên môi trường kiểm thử vẫn giữ trạng thái **`PENDING`** (không tự đánh dấu `APPROVED`).

## 1. Mục đích và phạm vi

D64 mô tả khung repository cho SASMS Sprint 0 gồm skeleton Spring Boot, React/Vite, PostgreSQL JDBC driver và một profile cấu hình tách biệt đặt cạnh bộ PostgreSQL 16 của Trần Trung Kiên.

Tài liệu liên quan: [D63 — Coding, Git và Pull Request](D63_CODING_GIT_PR_CONVENTIONS.md) và [D65 — Môi trường phát triển](D65_DEVELOPMENT_ENVIRONMENT.md).

Phạm vi chỉ là repository/source skeleton. D64 không triển khai nghiệp vụ Kho/POS, không tạo Entity/Repository cho 37 bảng và không thực hiện migration dữ liệu (`DEFERRED TO FUTURE SPRINT`). Tại repo đích, Maven compile/context-load test (`mvnw.cmd -o test`) và Frontend install/build (`npm.cmd ci`, `npm.cmd run build`) đã được chạy và đạt `PASS`; riêng `PostgreSqlConnectionSmokeTest` được giữ ở trạng thái `SKIPPED / NOT RUN` tại repo đích cho đến khi môi trường PostgreSQL cô lập và login thuộc `sasms_app` được xác nhận an toàn ở Phase 3.

## 2. Repository và nhánh làm việc

- **Repository đích (hiện tại):** `D:\software-project-management-an`, nhánh `feat/tv4-sprint0-handoff` (tạo từ `a2c8b64`).
- **Repository nguồn tham chiếu:** `D:\smart-electronic-supermarket-sprint0`, nhánh `feat/tv4-sprint0-postgresql`, commit `12918f7`.
- Tại repo đích, đã tích hợp:
  - 15 tài liệu TV4 tại `Sprint 0/Fullstack/TV4/docs/sprint0/`, kèm `README.md`, `TV4_ARCHITECTURE_DECISIONS_PENDING.md` và `TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md` tại `Sprint 0/Fullstack/TV4/`.
  - `backend/` (16 file nguồn + `backend/.gitignore`) và `frontend/` (14 file nguồn + `frontend/.gitignore`) tại root repo đích.
- Không sửa bất kỳ file nào của Kiên/An (`database/`, `docker-compose.yml`, `scripts/`, `.github/`, `.gitignore` root, `README.md` root, `Sprint 0/DB/`, `Sprint 0/PM/`, `Sprint 0/BA/`). Không cherry-pick, commit, push hoặc merge.

## 3. Công nghệ sử dụng

| Thành phần | Baseline trong source | Trạng thái tại repo đích (`D:\software-project-management-an`) |
|---|---|---|
| Backend | Spring Boot `4.1.1`, Java `26`, Maven Wrapper, Spring Web MVC | **Đã tích hợp tại `backend/`**; PM An đã đồng ý giữ `Java 26` + `Spring Boot 4.1.1` (`B1`); chờ Architect Kiên review (`PENDING`) |
| Backend test | `spring-boot-starter-test` | **Đã tích hợp**; `SmartElectronicSupermarketApplicationTests` chạy `PASS` tại repo đích |
| Database driver | PostgreSQL JDBC driver do Spring Boot dependency management quản lý, scope `runtime` | **Đã tích hợp** trong `backend/pom.xml` |
| Frontend | React `18.3.1`, React DOM `18.3.1`, Vite `5.4.10`, Axios `1.7.7` | **Đã tích hợp tại `frontend/`**; `npm.cmd ci` và `npm.cmd run build` chạy `PASS` tại repo đích |
| Database | PostgreSQL `16`, schema `sasms`, UTF-8, `timestamptz` | **Đã có sẵn** tại `database/` từ nhánh Kiên (`ca24ffe`) |
| Local DB | `postgres:16-alpine` qua `docker-compose.yml`, tùy chọn | **Đã có sẵn** ở root từ nhánh Kiên (`ca24ffe`) |

## 4. Cấu trúc thư mục tại repository đích (`D:\software-project-management-an`)

```text
D:\software-project-management-an\
├── .github/workflows/
│   └── database.yml                        # CI kiểm chứng PostgreSQL 16 của Kiên (GIỮ NGUYÊN)
├── backend/                                # Backend skeleton TV4 (ĐÃ TÍCH HỢP Ở PHASE 2)
│   ├── .gitignore                          # Ignore target/, *.log, IDE, .env* nội bộ
│   ├── .mvn/wrapper/
│   │   └── maven-wrapper.properties
│   ├── mvnw · mvnw.cmd
│   ├── pom.xml                             # Spring Boot 4.1.1, Java 26, PostgreSQL driver
│   └── src/
│       ├── main/
│       │   ├── java/com/smartelectronicsupermarket/
│       │   │   ├── SmartElectronicSupermarketApplication.java
│       │   │   ├── common/controller/HealthController.java
│       │   │   ├── pos/{controller,dto,service}/
│       │   │   └── serialimei/{controller,dto,service}/
│       │   └── resources/
│       │       ├── application.properties
│       │       └── application-postgresql.properties
│       └── test/java/com/smartelectronicsupermarket/
│           ├── PostgreSqlConnectionSmokeTest.java
│           └── SmartElectronicSupermarketApplicationTests.java
├── frontend/                               # Frontend skeleton TV4 (ĐÃ TÍCH HỢP Ở PHASE 2)
│   ├── .gitignore                          # Ignore node_modules/, dist/, build/, .env* nội bộ
│   ├── .env.example
│   ├── index.html
│   ├── package.json · package-lock.json
│   ├── vite.config.js
│   └── src/
│       ├── main.jsx
│       ├── app/{App.jsx,app.css}
│       ├── features/{pos,serialImei}/
│       └── shared/{api,components}/
├── database/                               # Baseline DB của Kiên (GIỮ NGUYÊN)
│   ├── evidence/ · tests/
│   ├── README.md · checks.sql · erd.mmd · runtime-roles.sql · schema.dbml · schema.sql · seed.sql
├── scripts/                                # Validator & generator của Kiên (GIỮ NGUYÊN)
├── Sprint 0/
│   ├── BA/ · DB/ · PM/ · AI/ · Tester/
│   └── Fullstack/
│       └── TV4/
│           ├── README.md
│           ├── TV4_ARCHITECTURE_DECISIONS_PENDING.md
│           ├── TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md
│           └── docs/sprint0/               # 15 tài liệu TV4
├── Sprint 1/ · Tiền Sprint/
├── .env.example · .gitattributes · .gitignore · docker-compose.yml · README.md
```

`docker-compose.yml` và `.env.example` ở root repo đích là các file do Kiên bàn giao trong commit `ca24ffe`. TV4 không ghi đè hai file này và không sửa `.gitignore` ở root (sử dụng `backend/.gitignore` và `frontend/.gitignore` để loại trừ `target/`, `node_modules/`, `dist/`).

## 5. Các module trong skeleton (đã tích hợp tại root repo đích)

Backend tại `D:\software-project-management-an\backend` hiện có:

- `common/controller`: health endpoint (`GET /api/health`);
- `pos`: controller, DTO và service cho trạng thái module POS (`GET /api/pos/status`);
- `serialimei`: controller, DTO và service cho trạng thái Serial/IMEI (`GET /api/serial-imei/status`);
- application entry point, một context-load test (`SmartElectronicSupermarketApplicationTests`) và `PostgreSqlConnectionSmokeTest`.

Frontend tại `D:\software-project-management-an\frontend` hiện có:

- `app`: application shell và CSS;
- `features/pos`: trang POS và API adapter;
- `features/serialImei`: panel Serial/IMEI và API adapter;
- `shared/api`: HTTP client dùng Axios;
- `shared/components`: component trạng thái dùng chung.

D64 giữ nguyên hành vi skeleton, không thêm CRUD/business logic của Sprint 1+ (`DEFERRED TO FUTURE SPRINT`).

## 6. PostgreSQL 16 và bộ schema của Kiên tại repo đích

Trong repository đích `D:\software-project-management-an`, bộ cơ sở dữ liệu của Kiên (commit `ca24ffe`, tài liệu tại `Sprint 0/DB/`) **đã có đầy đủ**:

- `database/schema.sql`: đúng 37 lệnh `CREATE TABLE`, schema `sasms`, không có lệnh `DROP`; chứa view tồn kho, constraint/index/trigger và 4 hàm `receive_device`, `reserve_order`, `complete_sale`, `cancel_order`.
- `database/seed.sql`: 9 role, 17 permission và dữ liệu DEMO; không có password/login thật.
- `database/runtime-roles.sql`: tạo group role `sasms_app` và `sasms_reader` (`NOLOGIN`) cùng phân quyền runtime tối thiểu.
- `database/checks.sql`: truy vấn đối soát chỉ đọc.
- `database/schema.dbml` và `database/erd.mmd`: DBML và sơ đồ ERD Mermaid sinh từ catalog thực tế.
- `database/tests/`: `constraints.sql`, `runtime_acl.sql`, `benchmark.sql`.
- `database/evidence/`: `validation.log`, `summary.json`, `artifacts-validation.json`.
- `scripts/validate_db.py` và `scripts/generate_db_artifacts.py`: validator và script đối soát/sinh DBML, ERD.
- `.github/workflows/database.yml`: workflow CI kiểm chứng PostgreSQL 16.
- `docker-compose.yml` và `.env.example`: dùng `postgres:16-alpine`, bind `127.0.0.1:5432`, yêu cầu password qua biến môi trường và mount `./database` read-only.

Đối chiếu SHA-256 giữa repo nguồn của TV4 và repo đích của An:

| File | SHA-256 | Trạng thái đối chiếu |
|---|---|---|
| `database/schema.sql` | `66AACDA1101C20ED0CC6C80062D93EF917660B4F6E825FA124F536DC2A1FCD96` | Trùng khớp 100% giữa hai repo |
| `database/seed.sql` | `8ED00DA989CCB6700867636D7D5616F0E90F2422086205BDDFBE79FCD783D385` | Trùng khớp 100% giữa hai repo |
| `docker-compose.yml` | `31092C806DCBD4290AF8D7924EC53FA5EFCDCAE09AD67FBA03AF4CCB687BBC3C` | Trùng khớp 100% giữa hai repo |

> **Ghi chú lịch sử:** Khi lập D64 ban đầu trên repo nguồn `D:\smart-electronic-supermarket-sprint0`, gói INPUT dùng để dựng nhánh chỉ có `schema.sql`, `seed.sql`, `docker-compose.yml` và `env.example`. Tại repo đích của An, toàn bộ `runtime-roles.sql`, `checks.sql`, `schema.dbml`, `erd.mmd`, `scripts/*.py`, `database/tests/` và `database/evidence/` **đã tồn tại đầy đủ** và thuộc quyền sở hữu của Kiên. Bản `database/README.md` rút gọn ở repo nguồn được loại bỏ (`EXCLUDE`), giữ nguyên `database/README.md` của Kiên tại repo đích.

## 7. Phần source có thể tái sử dụng khi tích hợp sang repo đích

- Entry point, health endpoint và context-load test của Spring Boot.
- Ranh giới controller/service/DTO hiện tại của POS và Serial/IMEI.
- Cấu hình `application-postgresql.properties` và `PostgreSqlConnectionSmokeTest` (sau khi thống nhất runtime role/DB target với thiết kế của Kiên).
- React application shell, các feature POS/Serial-IMEI, Axios client và component dùng chung.
- Maven Wrapper, Vite scripts và cấu hình proxy/build hiện có.

D64 không nhập module MySQL cũ, không chuyển SQL MySQL sang PostgreSQL và không thay đổi quy tắc nghiệp vụ.

## 8. Phần đã tích hợp ở Phase 2 và phần chưa triển khai (`DEFERRED TO FUTURE SPRINT`)

- **Đã hoàn thành trong Phase 2 tại repo đích (`D:\software-project-management-an`):**
  - Sao chép đầy đủ 16 file `backend/` và 14 file `frontend/` vào root repo đích theo phương án `A1`, giữ nguyên `Java 26` + `Spring Boot 4.1.1` theo `B1` và cấu hình biến môi trường DB theo `C2` (do PM An đồng ý).
  - Bổ sung `backend/.gitignore` và `frontend/.gitignore` nội bộ (không sửa `.gitignore` root).
  - Chạy kiểm chứng thành công tại repo đích: `.\mvnw.cmd -o test` (`BUILD SUCCESS`, context test `PASS`, JDBC smoke `SKIPPED` vì không bật DB) và `npm.cmd ci --offline` + `npm.cmd run build` (`PASS`).
- **Chưa chạy tại repo đích (chuyển sang Phase 3 khi có kết nối kiểm thử an toàn):**
  - Provision login local kế thừa `sasms_app` trên container cô lập (`-p tv4_s0_check`) và chạy lại `PostgreSqlConnectionSmokeTest` cũng như `spring-boot:run` với profile `postgresql` tại repo đích (`PENDING` Kiên/Architect review).
- **Ngoài phạm vi Sprint 0 (`DEFERRED TO FUTURE SPRINT` — Sprint 1+):**
  - Kết nối runtime từ service Kho/POS tới PostgreSQL.
  - Entity, Repository, mapping hoặc API CRUD thực tế cho 37 bảng và lời gọi 4 hàm giao dịch (`receive_device`, `reserve_order`, `complete_sale`, `cancel_order`).
  - Cơ chế authentication/RBAC ở tầng backend.
  - Migration tool/versioned migrations và quy trình nâng cấp database có dữ liệu.
  - Các module QR/POS/AI nghiệp vụ, logistics, hậu mãi, analytics, outbox worker và tích hợp nhà cung cấp.

## 9. Cách build và kiểm tra tại repository đích

> Khi kiểm tra PostgreSQL bằng Docker trên máy đang có môi trường của thành viên khác, phải dùng tên project Compose tách biệt (ví dụ `-p tv4_s0_check`) và kiểm tra cổng `5432` trước theo hướng dẫn tại [D65](D65_DEVELOPMENT_ENVIRONMENT.md) để không chạm vào dữ liệu/volume của Kiên.

Backend (tại `D:\software-project-management-an\backend`):

```powershell
cd backend
.\mvnw.cmd -o test
```

Frontend (tại `D:\software-project-management-an\frontend`):

```powershell
cd frontend
npm.cmd ci
npm.cmd run build
```

Kiểm tra DB tùy chọn trên project Compose cô lập (chỉ khi cổng `127.0.0.1:5432` đang trống và đã được xác nhận an toàn):

```powershell
Copy-Item .env.example .env
docker compose -p tv4_s0_check up -d --wait
docker compose -p tv4_s0_check exec -T db psql -X -U sasms_owner -d sasms -v ON_ERROR_STOP=1 -f /work/database/schema.sql
docker compose -p tv4_s0_check exec -T db psql -X -U sasms_owner -d sasms -v ON_ERROR_STOP=1 -f /work/database/seed.sql
docker compose -p tv4_s0_check exec -T db psql -X -U sasms_owner -d sasms -v ON_ERROR_STOP=1 -f /work/database/runtime-roles.sql
```

Profile backend `postgresql` không bật mặc định. Khi chạy hoặc kiểm chứng ở Phase 3, dùng biến môi trường `SASMS_DB_URL`, `SASMS_DB_USERNAME` (bắt buộc là login thuộc nhóm quyền `sasms_app`, không dùng `sasms_owner`), `SASMS_DB_PASSWORD`; không ghi credential thật vào source.

## 10. Bằng chứng thực hiện (Repo nguồn vs Repo đích sau Phase 2)

| Kiểm tra | Trạng thái tại repo nguồn (`12918f7`) | Trạng thái tại repo đích (`feat/tv4-sprint0-handoff`, Phase 2) | Bằng chứng / giới hạn |
|---|---|---|---|
| Hồ sơ tài liệu TV4 (15 file + README + Decisions + Phase 2 report) | PASS | **PASS** | Đặt tại `Sprint 0/Fullstack/TV4/` |
| SQL & Compose của Kiên (`schema.sql`, `seed.sql`, `docker-compose.yml`) | PASS | **PASS (ALREADY EXISTS)** | SHA-256 trùng khớp 100%; giữ nguyên bản của Kiên tại repo đích |
| Bộ DB mở rộng (`runtime-roles.sql`, `checks.sql`, DBML, ERD, validator, evidence) | Không có trong INPUT nguồn | **PASS (ALREADY EXISTS)** | Đã có sẵn đầy đủ tại `database/`, `scripts/`, `.github/workflows/database.yml` của Kiên |
| Có đúng 37 bảng trong schema | PASS | **PASS** | Đếm tĩnh 37 dòng `CREATE TABLE` trong `database/schema.sql` |
| Secret & build output bị loại khỏi Git | PASS | **PASS** | Root `.gitignore` chặn `.env`; `backend/.gitignore` chặn `target/`; `frontend/.gitignore` chặn `node_modules/` và `dist/` |
| Tích hợp thư mục `backend/` và `frontend/` | Có mặt tại repo nguồn | **PASS (30/30 file copied, SHA-256 100% match)** | Đặt tại root theo đồng ý `A1` của PM An |
| Backend Maven compile & context test (`.\mvnw.cmd -o test`) | PASS | **PASS tại repo đích (07/11/2026)** | `BUILD SUCCESS`; `Tests run: 2, Failures: 0, Errors: 0, Skipped: 1` (`SmartElectronicSupermarketApplicationTests` PASS; smoke test SKIPPED) |
| Frontend dependency install (`npm.cmd ci --offline`) | PASS | **PASS tại repo đích (07/11/2026)** | Dùng `package-lock.json`, cài 88 package, `0 vulnerabilities`, không đổi version |
| Frontend production build (`npm.cmd run build`) | PASS | **PASS tại repo đích (07/11/2026)** | Vite `5.4.21`, `92 modules transformed`, tạo `dist/` trong `835 ms` |
| Docker Compose / PostgreSQL 16 runtime | PASS (07/11/2026, `sasms_s0_check`) | **NOT RUN bởi TV4 trong Phase 2** | Không tự khởi động Docker/DB trong Phase 2 theo quy tắc an toàn; repo đích đã có `database/evidence/validation.log` của Kiên |
| Backend khởi động với profile `postgresql` | PASS (07/11/2026) | **NOT RUN tại repo đích** | Chờ provision login thuộc `sasms_app` ở Phase 3 |
| PostgreSQL JDBC smoke test (với DB thật) | PASS (07/11/2026 tại repo nguồn) | **SKIPPED / NOT RUN tại repo đích** | Không bật `SASMS_PG_SMOKE_ENABLED` khi chưa có login `sasms_app` được provision an toàn |
| Repository/JPA & Kho/POS end-to-end trên PostgreSQL | NOT RUN | **DEFERRED TO FUTURE SPRINT** | Thuộc phạm vi Sprint 1+, không thực hiện ở Sprint 0 |

### Lệnh minh chứng đã chạy tại repo nguồn ngày 07/11/2026 (lịch sử tham chiếu)

Các lệnh dưới đây đã được chạy tại `D:\smart-electronic-supermarket-sprint0` (không kèm secret):

```powershell
docker compose -p sasms_s0_check ps
docker compose -p sasms_s0_check exec -T db psql -X -U sasms_owner -d sasms -tAc "SHOW server_version;"
docker compose -p sasms_s0_check exec -T db psql -X -U sasms_owner -d sasms -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'sasms' AND table_type = 'BASE TABLE';"
docker compose -p sasms_s0_check exec -T db psql -X -U sasms_owner -d sasms -tAc "SELECT count(*) FROM sasms.roles; SELECT count(*) FROM sasms.permissions; SELECT count(*) FROM sasms.products; SELECT count(*) FROM sasms.stores;"

cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgresql"
Invoke-WebRequest http://127.0.0.1:8080/api/health
Invoke-WebRequest http://127.0.0.1:8080/api/pos/status
Invoke-WebRequest http://127.0.0.1:8080/api/serial-imei/status

$env:SASMS_PG_SMOKE_ENABLED = 'true'
$env:SASMS_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/sasms'
$env:SASMS_DB_USERNAME = '<configured-local-user>'
$env:SASMS_DB_PASSWORD = '<configured-secret>'
.\mvnw.cmd "-Dtest=PostgreSqlConnectionSmokeTest" test
```

Giới hạn bằng chứng: JDBC PASS tại repo nguồn xác nhận Java mở được kết nối read-only và đọc đúng các giá trị kiểm tra. Backend khởi động PASS chỉ xác nhận web skeleton/profile. Hai kết quả này không chứng minh service Kho/POS đang đọc/ghi PostgreSQL, không chứng minh transaction nghiệp vụ và không phải biên bản nghiệm thu.

## 11. Trạng thái các quyết định kỹ thuật liên quan đến D64

1. **Vị trí đặt source (`TV4-S0-ARCH-01` — Phương án A1):** PM **Trịnh Quốc An** đã đồng ý đặt `backend/` và `frontend/` ở root repository (kèm `.gitignore` nội bộ); đã thực hiện ở Phase 2. Chờ **Trần Trung Kiên** (Architect) review xác nhận (`PENDING`).
2. **Baseline công nghệ (`TV4-S0-ARCH-02` / `ARC-06` — Phương án B1):** PM **Trịnh Quốc An** đã đồng ý giữ `Spring Boot 4.1.1` + `Java 26` cho Sprint 0. Chờ **Trần Trung Kiên** (Architect) review xác nhận (`PENDING`).
3. **Runtime database login cho backend (`TV4-S0-ARCH-03` — Phương án C2):** PM **Trịnh Quốc An** đã đồng ý nguyên tắc runtime backend dùng login thuộc nhóm quyền `sasms_app` (không dùng `sasms_owner`). Việc provision login thực tế thuộc `sasms_app` trên môi trường kiểm thử và xác nhận của **Trần Trung Kiên** (DBA) đang ở trạng thái **`PENDING`**.
4. **Chiến lược migration, thiết kế data-access Kho/POS và API CRUD nghiệp vụ:** **`DEFERRED TO FUTURE SPRINT`** (chờ review kiến trúc và triển khai ở Sprint 1+).
5. **Nghiệm thu nghiệp vụ và schema (Huy + An + Kiên):** **`PENDING`** theo hồ sơ M1 (`Sprint 0/DB/D126-ho-so-review-M1.md`).
