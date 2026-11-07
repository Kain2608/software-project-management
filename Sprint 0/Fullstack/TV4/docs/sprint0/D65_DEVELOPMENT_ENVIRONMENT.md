# D65 — Hướng dẫn cài đặt và chạy môi trường phát triển

## 1. Mục đích và phạm vi

Tài liệu này hướng dẫn chuẩn bị máy Windows mới để chạy skeleton SASMS gồm Spring Boot, React/Vite và PostgreSQL 16. Các lệnh được viết cho PowerShell và chạy từ thư mục gốc repository đích `D:\software-project-management-an` nếu không ghi khác.

> **Lưu ý trạng thái tích hợp & chuẩn bị DB Smoke Test tại repo đích (Phase 2 & Phase 3):** Tại `D:\software-project-management-an` (nhánh `feat/tv4-sprint0-handoff`), phần cơ sở dữ liệu PostgreSQL 16 của Trần Trung Kiên (`database/`, `scripts/`, `docker-compose.yml`, `.env.example`) **đã có sẵn ở root**, và ở Phase 2 hai thư mục `backend/` cùng `frontend/` **đã được sao chép vào root repo đích** theo xác nhận của PM **Trịnh Quốc An** (**A1**: đặt tại root kèm `.gitignore` nội bộ; **B1**: giữ `Java 26` + `Spring Boot 4.1.1`; **C2**: runtime backend dùng DB login thuộc nhóm quyền `sasms_app`, không dùng `sasms_owner`). Lệnh build/test tĩnh (`mvnw.cmd -o test`, `mvnw.cmd -o "-Dtest=PostgreSqlConnectionSmokeTest" test`, `npm.cmd ci --offline`, `npm.cmd run build`) đã chạy trực tiếp tại repo đích và lưu log thật tại `Sprint 0/Fullstack/TV4/evidence/`. Trong **Phase 3 (Database Smoke Test Preparation)**, kiểm tra `database/runtime-roles.sql` và `database/tests/runtime_acl.sql` cho thấy `sasms_app` có đầy đủ quyền `USAGE ON SCHEMA sasms` và `SELECT ON ALL TABLES IN SCHEMA sasms` (đọc được đủ 37 bảng, 9 roles, 2 products theo yêu cầu của `PostgreSqlConnectionSmokeTest`), nhưng `sasms_app` được tạo với thuộc tính `NOLOGIN` và **chưa có runtime login nào kế thừa `sasms_app` được provision**. Do đó, việc khởi động PostgreSQL và chạy `PostgreSqlConnectionSmokeTest` với DB thật được giữ ở trạng thái **`NOT RUN — PENDING DBA APPROVAL`** (không tự tạo user, không sửa SQL, không chạy/thay đổi PostgreSQL khi chưa có phê duyệt của DBA Trần Trung Kiên).

Tài liệu liên quan: [D63 — Coding, Git và Pull Request](D63_CODING_GIT_PR_CONVENTIONS.md) và [D64 — Repository/source skeleton](D64_REPOSITORY_SOURCE_SKELETON.md).

D65 chỉ hướng dẫn môi trường phát triển. Tài liệu không tạo migration, không sửa schema/seed của Trần Trung Kiên, không tự ý chạy đè lên volume/database đang dùng của thành viên khác và không xác nhận nghiệp vụ Kho/POS đã tích hợp với database (`DEFERRED TO FUTURE SPRINT`).

## 2. Phiên bản công cụ

### 2.1 Baseline từ source và trạng thái phê duyệt

| Công cụ/thành phần | Yêu cầu từ source / repo đích | Trạng thái phê duyệt |
|---|---|---|
| Java | `26` theo `backend/pom.xml` (workbook Sprint 0 ghi `JDK 17`) | **PM An đã đồng ý giữ `Java 26` (`B1`)**; chờ Architect Kiên xác nhận (**`PENDING` Architect review**) |
| Spring Boot | `4.1.1` (`backend/pom.xml`) | **PM An đã đồng ý giữ `Spring Boot 4.1.1` (`B1`)**; chờ Architect Kiên xác nhận (**`PENDING` Architect review**) |
| Maven | Dùng Maven Wrapper `backend/mvnw.cmd`; source không yêu cầu Maven global | Wrapper là cách chạy chuẩn tại `D:\software-project-management-an\backend` |
| Node.js/npm | `package.json` không khai báo `engines` (workbook ghi Node 18+) | **PENDING (Đề xuất - chờ duyệt):** chốt Node/npm baseline chính thức |
| React/Vite | React `^18.3.1`, Vite `^5.4.10` | Được khai báo trong `frontend/package.json` |
| PostgreSQL | Major version `16` | Được xác nhận bởi `docker-compose.yml` và bộ schema của Kiên |
| Docker | Source không ghim phiên bản Docker Engine/Compose | **PENDING (Đề xuất - chờ duyệt):** chốt phiên bản tối thiểu được hỗ trợ |
| Python | `3.10+` (theo `database/README.md` của Kiên; CI `.github/workflows/database.yml` dùng `3.13`) | Đã có `scripts/validate_db.py` và `scripts/generate_db_artifacts.py` của Kiên tại repo đích; không bắt buộc cho luồng build backend/frontend cơ bản |
| Database user cho backend | `application-postgresql.properties` đọc `SASMS_DB_USERNAME` / `SASMS_DB_PASSWORD` | **PM An đã đồng ý nguyên tắc `C2`** (dùng login thuộc nhóm quyền `sasms_app`, không dùng `sasms_owner`); **việc provision login cụ thể thuộc `sasms_app` và xác nhận của DBA Kiên đang `PENDING`** |

### 2.2 Phiên bản đã kiểm tra trên máy thực hiện D65 (tại repo nguồn)

| Công cụ | Phiên bản/trạng thái |
|---|---|
| Java | Oracle JDK `26.0.2.1` |
| Maven Wrapper | Apache Maven `3.9.16` |
| Node.js | `22.23.1` |
| npm | `10.9.8` |
| Docker Engine CLI | `29.6.2` |
| Docker Compose | `5.3.1` |
| Python | `3.13.5` |
| `psql` trên host | Không có trên PATH mặc định (gọi qua `docker compose exec` trong container) |
| PostgreSQL runtime | PostgreSQL `16.15`, chạy healthy bằng Docker Compose project cô lập `sasms_s0_check` tại repo nguồn vào ngày 07/11/2026 |

Các phiên bản “đã kiểm tra” chỉ là bằng chứng môi trường khi lập D65, không tự trở thành baseline dự án.

## 3. Chuẩn bị máy Windows mới

1. Cài JDK theo major version được nhóm duyệt (hiện `pom.xml` ở repo nguồn yêu cầu JDK `26`; đang **PENDING** quyết định giữa JDK 17 và Java 26) và cấu hình `JAVA_HOME`.
2. Không cần cài Maven global; dùng `backend\mvnw.cmd` (sau khi `backend/` có mặt).
3. Cài Node.js kèm npm. Vì source chưa ghim Node/npm, dùng phiên bản được PM/Architect duyệt; Node `22.23.1` và npm `10.9.8` là tổ hợp đã nhận diện trên máy D65, chưa phải baseline.
4. Cài Docker Desktop có Docker Compose và bảo đảm Docker Engine đang chạy.
5. PostgreSQL client (`psql`) trên host là tùy chọn cho luồng Docker cơ bản vì có thể gọi `psql` bên trong container; tuy nhiên nếu chạy `scripts/validate_db.py` hoặc `scripts/generate_db_artifacts.py` của Kiên từ host thì cần `psql` và Python `3.10+` theo `database/README.md`.

Kiểm tra công cụ từ `D:\software-project-management-an`:

```powershell
java -version
node --version
npm.cmd --version
docker --version
docker compose version
python --version
# Chỉ chạy 3 dòng dưới sau khi thư mục backend/ đã được tích hợp vào repo đích:
cd backend
.\mvnw.cmd -v
cd ..
```

Trên PowerShell có execution policy chặn `npm.ps1`, dùng `npm.cmd` như các lệnh trong tài liệu này.

Việc tải Maven/npm dependency hoặc Docker image trên máy mới cần nguồn nội bộ/registry được dự án cho phép. D65 không tự tải dependency và không xác nhận cấu hình proxy/registry.

## 4. Cấu hình `.env` an toàn

`docker-compose.yml` ở root đọc biến `POSTGRES_PASSWORD` từ file `.env` cùng thư mục. Nếu chưa có `.env`, tạo file local từ mẫu:

```powershell
Copy-Item .env.example .env
notepad .env
```

Thay placeholder bằng mật khẩu local dài, riêng cho môi trường phát triển:

```dotenv
POSTGRES_PASSWORD=<local-password-khong-commit>
```

Quy tắc:

- Không dùng lại mật khẩu thật của Staging/Production.
- Không commit, chụp màn hình hoặc đưa nội dung `.env` vào log/PR.
- `.env.example` của Kiên chỉ giữ placeholder, không chỉnh sửa file mẫu này.
- Không đưa `SASMS_DB_PASSWORD` vào file source.
- **Nếu `.env` đã tồn tại trên máy, tuyệt đối không ghi đè** trước khi xác nhận đó không phải môi trường đang chạy của Kiên hoặc thành viên khác.

Kiểm tra cú pháp Compose mà không khởi động dịch vụ:

```powershell
docker compose config --quiet
```

Lệnh phải trả exit code `0`. Lỗi `POSTGRES_PASSWORD` nghĩa là `.env` thiếu, sai vị trí hoặc vẫn chưa có biến bắt buộc.

## 5. Khởi động PostgreSQL 16 cô lập (tránh chạm dữ liệu của Kiên)

### 5.1 Kiểm tra `docker-compose.yml` và tài nguyên đang chạy trước khi khởi động

`docker-compose.yml` ở root repo An có cấu hình:

- `service`: `db` (`image: postgres:16-alpine`), **không đặt `container_name` cứng** nên tên container/network/volume sẽ được tiền tố hóa theo tên Compose project (`<project>_db_1`, `<project>_default`, `<project>_sasms_pgdata`).
- `ports`: bind cố định vào host `127.0.0.1:5432:5432`.
- `volumes`: named volume `sasms_pgdata:/var/lib/postgresql/data` và bind mount `./database:/work/database:ro`.

Vì nếu chạy `docker compose up` không có cờ `-p`, Docker Compose sẽ lấy tên thư mục làm project mặc định và có thể dùng chung volume `<default>_sasms_pgdata` hoặc đụng cổng `127.0.0.1:5432` của Kiên, **bắt buộc kiểm tra container, volume, network và host port 5432 trước**:

```powershell
docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
docker volume ls
docker network ls
Get-NetTCPConnection -LocalPort 5432 -ErrorAction SilentlyContinue
```

- Nếu cổng `127.0.0.1:5432` đang có tiến trình `postgres` hoặc container khác lắng nghe: **dừng lại, không chạy `docker compose up`** và không dừng/xóa container hay volume của người khác.
- Khi cổng `5432` hoàn toàn trống và muốn tạo môi trường kiểm tra riêng cho TV4, **luôn dùng cờ `-p tv4_s0_check`** để tách riêng container, network và volume `tv4_s0_check_sasms_pgdata`, không mặc định chạy lên dữ liệu DB của Kiên.

### 5.2 Khởi động bằng project riêng `-p tv4_s0_check`

```powershell
docker compose -p tv4_s0_check up -d --wait
docker compose -p tv4_s0_check ps
docker compose -p tv4_s0_check exec -T db pg_isready -U sasms_owner -d sasms
```

Chỉ ghi **PostgreSQL khởi động thành công** khi:

- container `db` trong project `tv4_s0_check` ở trạng thái running/healthy;
- `pg_isready` báo nhận kết nối;
- không có lỗi khởi tạo trong log cần xử lý.

Xem log khi cần:

```powershell
docker compose -p tv4_s0_check logs --tail 100 db
```

Không dùng `docker compose down -v` trên project mặc định, không xóa volume của Kiên và không reset database theo hướng dẫn D65.

## 6. Tạo schema, nạp seed và thiết lập runtime roles

### 6.1 Kiểm tra database mới/cô lập

`schema.sql` tạo schema `sasms` và sẽ chủ động dừng nếu schema đã tồn tại. Trước khi nạp trên project `tv4_s0_check`, kiểm tra:

```powershell
docker compose -p tv4_s0_check exec -T db psql -X -U sasms_owner -d sasms -tAc "SELECT 1 FROM pg_namespace WHERE nspname = 'sasms';"
```

- Không có kết quả: có thể tiếp tục trên database mới/cô lập.
- Kết quả `1`: dừng. Không chạy lại schema/seed, không drop schema và không xóa volume. Cần Kiên/An xác nhận cách nâng cấp hoặc dùng project/database phát triển mới.

### 6.2 Nạp schema

Chỉ thực hiện sau khi kiểm tra database mới:

```powershell
docker compose -p tv4_s0_check exec -T db psql -X -U sasms_owner -d sasms -v ON_ERROR_STOP=1 -f /work/database/schema.sql
```

Kiểm tra số bảng:

```powershell
docker compose -p tv4_s0_check exec -T db psql -X -U sasms_owner -d sasms -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'sasms' AND table_type = 'BASE TABLE';"
```

Chỉ ghi **nạp schema thành công** khi lệnh nạp trả exit code `0` và truy vấn trả đúng `37`. Container healthy không chứng minh schema đã được nạp.

### 6.3 Nạp seed và `runtime-roles.sql`

Chỉ nạp seed ngay sau khi schema mới đã thành công và đã xác nhận database không chứa dữ liệu cũ:

```powershell
docker compose -p tv4_s0_check exec -T db psql -X -U sasms_owner -d sasms -v ON_ERROR_STOP=1 -f /work/database/seed.sql
docker compose -p tv4_s0_check exec -T db psql -X -U sasms_owner -d sasms -v ON_ERROR_STOP=1 -f /work/database/runtime-roles.sql
```

Kiểm tra dữ liệu tham chiếu:

```powershell
docker compose -p tv4_s0_check exec -T db psql -X -U sasms_owner -d sasms -tAc "SELECT count(*) FROM sasms.roles;"
docker compose -p tv4_s0_check exec -T db psql -X -U sasms_owner -d sasms -tAc "SELECT count(*) FROM sasms.permissions;"
```

Bộ seed hiện tại dự kiến có 9 role và 17 permission. Đây là dữ liệu DEMO/tham chiếu (`is_active=false`, không có mật khẩu), không phải bằng chứng nghiệp vụ Kho/POS chạy thành công.

## 7. Runtime role, validator và đường dẫn artifact tại repo đích

Tại repository đích `D:\software-project-management-an`, toàn bộ bộ artifact DB của Kiên **đã tồn tại đầy đủ**:

- `docker-compose.yml` và `.env.example` ở root;
- `./database` được mount read-only thành `/work/database`;
- `/work/database/schema.sql`, `/work/database/seed.sql`, `/work/database/runtime-roles.sql` và `/work/database/checks.sql` tồn tại;
- `database/schema.dbml`, `database/erd.mmd`, `database/tests/*.sql` và `database/evidence/*` (`validation.log`, `summary.json`, `artifacts-validation.json`) tồn tại;
- `scripts/validate_db.py`, `scripts/generate_db_artifacts.py` và `.github/workflows/database.yml` tồn tại (chi tiết cách chạy xem `database/README.md` của Kiên).

Phần cấu hình và mã nguồn backend/frontend tại repo đích (Phase 2 & Phase 3):

- `backend/` và `frontend/` (bao gồm `backend/src/main/resources/application-postgresql.properties` và `PostgreSqlConnectionSmokeTest.java`) **đã được sao chép vào root repo đích** (`D:\software-project-management-an\backend` và `...\frontend`) theo đồng ý `A1`, `B1`, `C2` của PM Trịnh Quốc An.
- **Kết quả kiểm tra quyền `sasms_app` trong Phase 3 (`database/runtime-roles.sql` & `database/tests/runtime_acl.sql`):**
  1. **Đã có runtime login chưa?** **Chưa có (`NO`).** `database/runtime-roles.sql` tạo hai group role `sasms_app` và `sasms_reader` với thuộc tính `NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS`, kèm chặn cứng `RAISE EXCEPTION 'Existing runtime roles have unsafe attributes; DBA review required'` nếu `rolcanlogin = true`. Trong `database/tests/runtime_acl.sql`, Kiên kiểm thử bằng cách đăng nhập qua owner rồi gọi `SET LOCAL ROLE sasms_app;`, chưa tạo user có thuộc tính `LOGIN`.
  2. **Login có quyền kết nối DB không?** Bản thân `sasms_app` là `NOLOGIN` nên không thể dùng trực tiếp làm `SASMS_DB_USERNAME` qua JDBC (`FATAL: role "sasms_app" is not permitted to log in`). Không được dùng `sasms_owner` làm runtime user theo quyết định `C2` và mục 4 D54.
  3. **Group role `sasms_app` có đọc được các bảng cần thiết và đáp ứng điều kiện `PostgreSqlConnectionSmokeTest` không?** **Đáp ứng 100% về mặt quyền truy vấn (`YES`):** `runtime-roles.sql` đã cấp `GRANT USAGE ON SCHEMA sasms TO sasms_app` và `GRANT SELECT ON ALL TABLES IN SCHEMA sasms TO sasms_app`. Khi có một login kế thừa (`INHERIT`) `sasms_app`, login đó thực hiện được toàn bộ 6 kiểm tra của `PostgreSqlConnectionSmokeTest`: `SHOW transaction_read_only` (`on`), `SHOW server_version` (`16.x`), `SELECT current_database()` (`sasms`), đếm `information_schema.tables` trong schema `sasms` (`37` base tables — vì có quyền `SELECT` trên cả 37 bảng), đếm `sasms.roles` (`9`) và `sasms.products` (`2`) trong transaction read-only rồi `rollback()`.
  4. **Trạng thái hiện tại:** **`PENDING DBA APPROVAL`** (chờ Trần Trung Kiên — DBA/Architect phê duyệt và cấp phát runtime login kế thừa `sasms_app`; TV4 không tự tạo user, không sửa SQL và không chạy/thay đổi PostgreSQL).

## 8. Cấu hình và chạy backend tại repo đích

Profile `postgresql` đọc:

| Biến | Ý nghĩa | Giá trị mặc định | Trạng thái |
|---|---|---|---|
| `SASMS_DB_URL` | JDBC URL | `jdbc:postgresql://127.0.0.1:5432/sasms` | Khớp cổng Compose local |
| `SASMS_DB_USERNAME` | Runtime database login | Không có (không có mặc định `sasms_owner`) | **PM An đã đồng ý `C2`** (dùng login thuộc group `sasms_app`, không dùng `sasms_owner`); **provision login cụ thể đang `PENDING` DBA Kiên** |
| `SASMS_DB_PASSWORD` | Runtime database password | Không có | Đặt trong process local, không hardcode/commit |

Profile đặt `spring.sql.init.mode=never`, vì vậy backend không tự tạo, update hoặc drop schema và không tự nạp seed.

### 8.1 Chạy skeleton không bật PostgreSQL profile

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Lệnh này chỉ xác nhận skeleton backend có thể khởi động. Nó không chứng minh kết nối PostgreSQL.

### 8.2 Chạy với PostgreSQL profile (chờ provision login thuộc `sasms_app`)

Chỉ thực hiện ở Phase 3 sau khi login thuộc `sasms_app` được provision trên môi trường kiểm thử cô lập:

```powershell
$env:SASMS_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/sasms'
$env:SASMS_DB_USERNAME = '<login-thuoc-sasms_app-pending-provisioning>'
$env:SASMS_DB_PASSWORD = '<runtime-password>'
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgresql"
```

Không ghi các giá trị thật vào `application-postgresql.properties` và không dùng `sasms_owner` làm tài khoản runtime.

Skeleton hiện chỉ có PostgreSQL JDBC driver, chưa có Entity/Repository/data-access integration (`DEFERRED TO FUTURE SPRINT`). Vì vậy, chỉ riêng việc process backend khởi động với profile chưa chứng minh kết nối DB.

Tại repo nguồn vào ngày 07/11/2026, `PostgreSqlConnectionSmokeTest` đã mở kết nối thật bằng `DriverManager`, dùng transaction read-only và kiểm tra PostgreSQL 16, database `sasms`, 37 bảng cùng dữ liệu seed. Tại repo đích trong Phase 2, test này được giữ ở trạng thái `SKIPPED / NOT RUN` (do không tự bật Docker/DB khi chưa provision login `sasms_app`).

## 9. Chạy frontend tại repo đích

Trên máy mới, dependency phải được cung cấp từ registry/cache được phép. Repository đích đã có `frontend/package-lock.json`, vì vậy dùng cài đặt tái lập:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Dùng URL mà Vite in ra trong console. Build production:

```powershell
npm.cmd run build
```

`package.json` hiện chỉ có `dev`, `build`, `preview`; chưa có script `test` hoặc `lint`. Không ghi frontend test PASS nếu chỉ chạy build.

Frontend khởi động thành công cũng không chứng minh backend kết nối DB hoặc nghiệp vụ Kho/POS hoạt động.

## 10. Chạy kiểm thử tại repo đích

Backend (tại `D:\software-project-management-an\backend`):

```powershell
cd backend
.\mvnw.cmd -o test
```

Smoke test JDBC được khóa bằng biến bật riêng (`SASMS_PG_SMOKE_ENABLED=true`) và chỉ chạy read-only với database local `sasms` trên loopback port `5432`. Chỉ chạy lệnh dưới ở Phase 3 khi đã có kết nối kiểm thử cô lập và login thuộc `sasms_app` được xác nhận an toàn:

```powershell
$env:SASMS_PG_SMOKE_ENABLED = 'true'
$env:SASMS_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/sasms'
$env:SASMS_DB_USERNAME = '<login-thuoc-sasms_app>'
$env:SASMS_DB_PASSWORD = '<configured-secret>'
.\mvnw.cmd -o "-Dtest=PostgreSqlConnectionSmokeTest" test
```

Không đưa giá trị thật của username/password vào tài liệu hoặc log. Khi không bật `SASMS_PG_SMOKE_ENABLED` (như trong Phase 2), test tự động ở trạng thái `SKIPPED` (`NOT RUN` đối với kết nối DB thật).

Frontend (tại `D:\software-project-management-an\frontend`):

```powershell
cd frontend
npm.cmd ci --offline
npm.cmd run build
```

Database (trên project cô lập `-p tv4_s0_check` — chỉ chạy khi được xác nhận an toàn):

- `docker compose config --quiet`: chỉ kiểm tra cấu hình Compose.
- `pg_isready`: chỉ kiểm tra PostgreSQL nhận kết nối.
- Nạp `schema.sql` và xác nhận 37 bảng: kiểm tra schema trên database mới.
- Nạp `seed.sql` và `runtime-roles.sql`: kiểm tra seed và tạo group role runtime (`sasms_app`, `sasms_reader`).
- Đối soát đầy đủ bằng `scripts/validate_db.py` và `database/checks.sql`: thực hiện theo hướng dẫn tại `database/README.md` của Kiên trên database thử cô lập (`sasms_validation`), không chạy đè lên DB đang dùng.

Mỗi kết quả phải ghi lệnh, exit code, nơi chạy (repo nguồn hay repo đích) và phạm vi đã kiểm tra. Không suy rộng một kiểm tra sang tầng khác.

## 11. Bốn mức xác nhận độc lập

| Mức | Điều kiện xác nhận | Không chứng minh | Trạng thái tại repo nguồn (`12918f7`) | Trạng thái tại repo đích (`feat/tv4-sprint0-handoff`, Phase 2) |
|---|---|---|---|---|
| 1. PostgreSQL khởi động | Container healthy và `pg_isready` thành công | Schema tồn tại, backend kết nối, nghiệp vụ chạy | PASS (`sasms_s0_check`, 07/11/2026) | Đã có evidence riêng của Kiên (`database/evidence/`); TV4 **NOT RUN** lại trong Phase 2 (không tự bật Docker) |
| 2. Schema/seed được nạp | Lệnh `psql` exit `0`, có đúng 37 bảng; seed kiểm tra đúng dữ liệu tham chiếu | Backend dùng được DB hoặc quyền runtime đúng | PASS (`sasms_s0_check`, 07/11/2026) | Đã có `database/evidence/validation.log` của Kiên; TV4 **NOT RUN** lại trong Phase 2 |
| 3. Backend kết nối DB | Có kiểm tra thực sự mở connection/truy vấn bằng runtime credential | Luồng Kho/POS đúng nghiệp vụ | PASS kỹ thuật ở mức JDBC read-only (`PostgreSqlConnectionSmokeTest`) | Compile & context test **PASS** tại repo đích; kết nối JDBC thật **SKIPPED / NOT RUN** (chờ provision login thuộc `sasms_app` ở Phase 3) |
| 4. Kho/POS hoạt động | API/UI, transaction, Serial/IMEI và POS có integration/E2E test đạt | Các module ngoài phạm vi cũng hoàn thành | **NOT RUN** | **DEFERRED TO FUTURE SPRINT** (Sprint 1+) |

Backend vẫn chưa có Repository/JPA/data-access nghiệp vụ, nên chưa thể kết luận service Kho/POS sử dụng DB.

## 12. Kiểm tra kết nối và xử lý lỗi

### Docker không chạy hoặc không có image

- Kiểm tra Docker Desktop/Engine.
- Chạy `docker compose version` và `docker compose config --quiet`.
- Nếu image chưa có và máy offline, ghi `NOT RUN`; không nhận là PostgreSQL đã khởi động.

### Cổng 5432 hoặc volume đã được sử dụng

```powershell
Get-NetTCPConnection -LocalPort 5432 -ErrorAction SilentlyContinue
docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
docker volume ls
```

Không dừng dịch vụ hoặc xóa volume khi chưa xác định chủ sở hữu. Luôn dùng `-p tv4_s0_check` khi tạo môi trường thử riêng khi cổng `5432` đang trống. Việc đổi cổng cần Architect xác nhận và phải cập nhật đồng bộ JDBC URL.

### `.env` thiếu hoặc sai

- Đặt `.env` cạnh `docker-compose.yml` tại `D:\software-project-management-an\.env`.
- Bảo đảm có `POSTGRES_PASSWORD` và không còn placeholder.
- Không ghi đè `.env` đang có sẵn của thành viên khác; không in password bằng `Get-Content .env` vào evidence hoặc chat.

### Schema đã tồn tại

- Dừng lệnh nạp.
- Không chạy seed trên dữ liệu cũ.
- Không dùng `DROP SCHEMA`, `docker compose down -v` hoặc tự tạo migration.
- Yêu cầu Kiên/An quyết định project/database mới hay migration đã review.

### Backend khởi động nhưng chưa chứng minh DB connection

- Kiểm tra profile `postgresql` có được kích hoạt hay không.
- Kiểm tra ba biến `SASMS_DB_*` trong process, không ghi giá trị bí mật vào log.
- Xác nhận runtime login đã được cấp thuộc group `sasms_app` và có quyền đúng.
- Chạy `PostgreSqlConnectionSmokeTest` với biến môi trường hợp lệ để kiểm tra JDBC; chỉ thấy backend startup log là chưa đủ.
- Smoke test JDBC PASS vẫn chưa chứng minh service Kho/POS có Repository/JPA hoặc đã chạy end-to-end.

### Frontend báo không tìm thấy `vite`

- `frontend/node_modules` chưa có hoặc dependency chưa được cài.
- Khi `package-lock.json` có sẵn và khớp `package.json`, chạy `npm.cmd ci` từ registry/cache được phép. Không tự đổi version package để né lỗi.
- Không tự đổi version Vite/React để né lỗi môi trường.

## 13. Kết quả kiểm chứng tại repo nguồn và kết quả thực tế sau Phase 2 & Phase 3 tại repo đích

Toàn bộ log thật của các lần kiểm tra tại repo đích được lưu tại thư mục `Sprint 0/Fullstack/TV4/evidence/` (không chứa bất kỳ secret hay mật khẩu nào):
- [../../evidence/phase3-precheck.log](../../evidence/phase3-precheck.log): Log kiểm tra môi trường, `docker-compose.yml`, trạng thái cổng `5432`, phân tích `runtime-roles.sql` / `runtime_acl.sql` và điều kiện `PostgreSqlConnectionSmokeTest`.
- [../../evidence/backend-smoke-test-skipped.log](../../evidence/backend-smoke-test-skipped.log): Log chạy `.\mvnw.cmd -o "-Dtest=PostgreSqlConnectionSmokeTest" test` (xác nhận trạng thái `SKIPPED` an toàn khi chưa có runtime login thuộc `sasms_app`).
- [../../evidence/surefire-summary.txt](../../evidence/surefire-summary.txt): Tóm tắt kết quả JUnit Surefire của `SmartElectronicSupermarketApplicationTests` (`PASS`) và `PostgreSqlConnectionSmokeTest` (`SKIPPED`).
- [../../evidence/frontend-build.log](../../evidence/frontend-build.log): Log chạy `npm.cmd run build` (`PASS`, Vite `5.4.21`).
- [../../evidence/d52-openapi-lint.log](../../evidence/d52-openapi-lint.log): Log chạy Redocly lint cho D52 OpenAPI (`PASS`, `0 errors, 2 warnings`).
- [../../evidence/summary.json](../../evidence/summary.json): Bảng tổng hợp JSON các kiểm tra và SHA-256 của các file nguồn/cấu hình tại repo đích.

| Kiểm tra | Kết quả tại repo nguồn (07/11/2026) | Trạng thái tại repo đích (`D:\software-project-management-an`, Phase 2 & 3) | Bằng chứng/giới hạn |
|---|---|---|---|
| Đọc phiên bản Java/Maven Wrapper | PASS | **PASS** (`backend/mvnw.cmd` đã tích hợp) | Java `26.0.2.1`, Maven `3.9.16`; PM An đã đồng ý giữ `Java 26` (`B1`), chờ Architect Kiên xác nhận (`PENDING`) |
| Đọc phiên bản Node/npm | PASS | **PASS** | Node `22.23.1`, npm `10.9.8` (`phase3-precheck.log`) |
| Đọc phiên bản Docker/Compose | PASS | **PASS** | Docker `29.6.2`, Compose `5.3.1` |
| Đọc phiên bản Python & validator DB | PASS (Python `3.13.5`) | **PASS (ALREADY EXISTS)** | Repo đích đã có `scripts/validate_db.py`, `scripts/generate_db_artifacts.py` và `database/evidence/` của Kiên |
| Kiểm tra cấu hình Compose & cổng `5432` | PASS | **PASS (Precheck Phase 3)** | `docker-compose.yml` khớp SHA-256 nguồn; cổng `127.0.0.1:5432` không bị chiếm; `.env` chưa tạo (`phase3-precheck.log`) |
| Kiểm tra biến profile backend & quyền `sasms_app` | PASS | **PASS (Cấu hình & Quyền bảng) / `PENDING DBA APPROVAL` (Login)** | `sasms_app` có đủ `USAGE` + `SELECT` trên 37 bảng nhưng là `NOLOGIN`; chưa có runtime login kế thừa `sasms_app` (`phase3-precheck.log`) |
| Kiểm tra frontend scripts | PASS | **PASS** (`frontend/` đã tích hợp) | Có `dev`, `build`, `preview`; không có `test`, `lint` |
| Kiểm tra đường dẫn SQL & artifact DB | PASS | **PASS** | Repo đích có đủ `schema.sql`, `seed.sql`, `runtime-roles.sql`, `checks.sql`, `schema.dbml`, `erd.mmd` |
| Kiểm tra tĩnh schema | PASS | **PASS** | Có 37 `CREATE TABLE`; không có lệnh `DROP` |
| Backend Maven compile & context test (`.\mvnw.cmd -o test`) | PASS | **PASS tại repo đích (07/11/2026)** | `BUILD SUCCESS`; `Tests run: 2, Failures: 0, Errors: 0, Skipped: 1` (`surefire-summary.txt`) |
| Backend smoke test guard (`.\mvnw.cmd -o "-Dtest=PostgreSqlConnectionSmokeTest" test`) | SKIPPED (khi tắt env) | **`SKIPPED` tại repo đích (07/11/2026)** | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 1` (`backend-smoke-test-skipped.log`) |
| Frontend dependency install (`npm.cmd ci --offline`) | PASS | **PASS tại repo đích (07/11/2026)** | Dùng `package-lock.json`, cài 88 package, `0 vulnerabilities`; không đổi version |
| Frontend build (`npm.cmd run build`) | PASS | **PASS tại repo đích (07/11/2026)** | Vite `5.4.21`, `92 modules transformed`, tạo `dist/` (`frontend-build.log`) |
| Backend khởi động với profile `postgresql` | PASS | **`NOT RUN — PENDING DBA APPROVAL`** | Chờ DBA Kiên cấp login thuộc `sasms_app`; không dùng `sasms_owner` |
| Endpoint skeleton (`/api/health`, `/api/pos/status`, `/api/serial-imei/status`) | PASS | **NOT RUN runtime HTTP tại repo đích** | Code controller/service khớp 100% nguồn; Spring context load test đã PASS |
| PostgreSQL 16 runtime & nạp schema/seed (cô lập) | PASS (`-p sasms_s0_check`) | **`NOT RUN — PENDING DBA APPROVAL`** | Không chạy hoặc thay đổi PostgreSQL khi chưa có login runtime được DBA phê duyệt; repo đích đã có `database/evidence/validation.log` của Kiên |
| PostgreSQL JDBC smoke test (với DB thật) | PASS (`Tests run: 1, Failures: 0`) | **`NOT RUN — PENDING DBA APPROVAL` (Guard test: `SKIPPED`)** | Group role `sasms_app` đủ quyền `SELECT` 37 bảng nhưng chưa có role `LOGIN` kế thừa `sasms_app` |
| Repository/JPA & Nghiệp vụ Kho/POS | NOT RUN | **DEFERRED TO FUTURE SPRINT** | Thuộc phạm vi Sprint 1+, không thực hiện ở Sprint 0 |

### 13.1 Lệnh minh chứng đã chạy tại repo đích ngày 07/11/2026 (Phase 2 & Phase 3)

```powershell
# Thực thi trực tiếp tại D:\software-project-management-an:
cd backend
.\mvnw.cmd -o test
# -> Tests run: 2, Failures: 0, Errors: 0, Skipped: 1 — BUILD SUCCESS (Sprint 0/Fullstack/TV4/evidence/surefire-summary.txt)

.\mvnw.cmd -o "-Dtest=PostgreSqlConnectionSmokeTest" test
# -> Tests run: 1, Failures: 0, Errors: 0, Skipped: 1 — BUILD SUCCESS (Sprint 0/Fullstack/TV4/evidence/backend-smoke-test-skipped.log)

cd ..\frontend
npm.cmd ci --offline
# -> added 88 packages, and audited 89 packages in 7s, found 0 vulnerabilities
npm.cmd run build
# -> vite v5.4.21, 92 modules transformed (Sprint 0/Fullstack/TV4/evidence/frontend-build.log)

cd ..
npx.cmd --offline -y @redocly/cli lint "Sprint 0/Fullstack/TV4/docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml"
# -> Woohoo! Your API description is valid. 0 errors, 2 warnings (Sprint 0/Fullstack/TV4/evidence/d52-openapi-lint.log)
```

### 13.2 Lệnh minh chứng đã chạy tại repo nguồn ngày 07/11/2026 (lịch sử tham chiếu)

```powershell
# Đã thực thi tại D:\smart-electronic-supermarket-sprint0:
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

Giới hạn: bằng chứng xác nhận PostgreSQL, schema/seed, web skeleton và một JDBC connection read-only. Nó không xác nhận Repository/JPA, persistence nghiệp vụ, bốn hàm giao dịch được gọi từ backend hoặc luồng Kho/POS end-to-end; không được dùng như biên bản nghiệm thu.

## 14. Quyết định đã có xác nhận của PM An và các mục còn PENDING

1. **Vị trí đặt source (`TV4-S0-ARCH-01` — Phương án A1):** PM **Trịnh Quốc An** đã đồng ý đặt `backend/` và `frontend/` ở root repository (đã thực hiện ở Phase 2). Chờ **Trần Trung Kiên** (Architect) review xác nhận (`PENDING`).
2. **Java & Spring Boot baseline (`TV4-S0-ARCH-02` — Phương án B1):** PM **Trịnh Quốc An** đã đồng ý giữ `Java 26` + `Spring Boot 4.1.1`. Chờ **Trần Trung Kiên** (Architect) review xác nhận (`PENDING`).
3. **Database user cho backend (`TV4-S0-ARCH-03` — Phương án C2):** PM **Trịnh Quốc An** đã đồng ý nguyên tắc runtime backend dùng login thuộc nhóm quyền `sasms_app` (không dùng `sasms_owner`). **Việc provision login thực tế thuộc `sasms_app` trên môi trường kiểm thử và xác nhận của Trần Trung Kiên (DBA) đang `PENDING`** (sẽ thực hiện ở Phase 3 trước khi chạy `PostgreSqlConnectionSmokeTest`).
4. **Quy trình cho database đã có schema/dữ liệu (`PENDING` — Kiên, An):** chốt công cụ migration và cách nâng cấp (`DEFERRED TO FUTURE SPRINT`); D65 bắt buộc dùng project cô lập (ví dụ `-p tv4_s0_check`), không drop/reset dữ liệu đang có.
5. **Tiêu chí kiểm thử tích hợp (`PENDING` — Huy, Phát, Kiên, An):** xác nhận ranh giới giữa smoke test JDBC hiện tại với kiểm thử Repository/service và E2E Kho/POS ở các sprint tiếp theo (`DEFERRED TO FUTURE SPRINT`).
