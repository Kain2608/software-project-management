# SASMS Sprint 2 — DB verification record 16–17/11/2026

## 1. Phạm vi và provenance

- Nhánh: `feat/tv4-s2-integration-evidence`
- Commit được kiểm tra: `569c72a15c2616d77ded159a11a1a72c15a89a17`
- Thời điểm kiểm tra môi trường: `2026-11-16T23:16:44.0173522+07:00`
- Mục tiêu: chỉ chạy validator Sprint 2 khi xác minh được daemon, container `tv4_s1_it_db`, volume `tv4_s1_it_pgdata`, loopback port `15432` và database `sasms` là môi trường cô lập.
- Không chạy `docker down -v`, `prune`, reset volume, migration hoặc chỉnh sửa SQL.

## 2. Lệnh kiểm tra Docker đã chạy

```powershell
docker version --format "Client={{.Client.Version}} Server={{.Server.Version}}"
docker inspect tv4_s1_it_db
docker volume inspect tv4_s1_it_pgdata
```

Kết quả thực tế:

```text
Client=29.6.2 Server=
failed to connect to the docker API at
npipe:////./pipe/dockerDesktopLinuxEngine:
The system cannot find the file specified.
```

| Kiểm tra | Kết quả |
|---|---|
| Docker client | PASS — phiên bản `29.6.2` |
| Docker daemon Linux | FAIL — named pipe không tồn tại |
| Container `tv4_s1_it_db` | NOT VERIFIED |
| Container healthy | NOT VERIFIED |
| Image PostgreSQL 16 | NOT VERIFIED |
| Port `127.0.0.1:15432 -> 5432/tcp` | NOT VERIFIED |
| Volume `tv4_s1_it_pgdata` | NOT VERIFIED |
| Database `sasms` thực sự được kết nối | NOT RUN |

Vì không xác minh được môi trường cô lập, toàn bộ DB test Sprint 2 được dừng trước khi kết nối. Không có SQL, fixture, cleanup hoặc mutation nào được chạy trong phiên này.

## 3. Bộ integration test Sprint 2 đúng trong source

- Runner hai kết nối thật: `scripts/validate_sprint2.py`.
- SQL assertion/ACL/stock lifecycle: `database/tests/sprint2/test_sprint2_concurrency_and_stock.sql`.
- Extension được kiểm: `database/sprint2/realtime_stock_and_locking.sql`.
- `backend/src/test/java/com/smartelectronicsupermarket/Sprint1PostgreSqlIntegrationTest.java` là regression Sprint 1 và không được dùng để tuyên bố Sprint 2 Serial locking PASS.
- Chạy file SQL Sprint 2 riêng không chứng minh hai POS đồng thời; phải dùng validator Python.

Validator Sprint 2 có các ca riêng:

1. Hai PostgreSQL backend PID khác nhau.
2. POS1 giữ row lock tại barrier; POS2 nhận SQLSTATE `55P03`.
3. Retry sau release của ca commit.
4. Retry sau rollback của ca rollback.
5. Delta tồn `IN_STOCK -> RESERVED -> SOLD`.
6. Chỉ một tập side effect checkout được commit.
7. Cleanup theo đúng UUID fixture và xác nhận trigger payment được bật lại.

## 4. Đối chiếu source hash với evidence lịch sử

Các hash hiện tại khớp `database/evidence/sprint2/summary.json` ngày 13/11/2026:

| Source | SHA-256 |
|---|---|
| `scripts/validate_sprint2.py` | `B31C398CD5D9EDC028B6A5E1BDAC628D9A7D579C763BA9BF21C500AB3C7566F0` |
| `scripts/validate_db.py` | `79D28CBD7859615551EAE68F80C4C781449F35064737D038B7EA3C9F391B3841` |
| `scripts/validate_sprint1.py` | `05ABAA691781FFF21B95ABC13E534B46385CC8846F12D5F373541A6CFE5ECDEA` |
| `database/sprint2/realtime_stock_and_locking.sql` | `E2B93FD3115EF2C5432B5FC5898DC96A8C561D83AAE639566F3050FDA93FFDF5` |
| `database/tests/sprint2/test_sprint2_concurrency_and_stock.sql` | `DB4CDB3FF9004191310E1FDC1509AB5FCD405DA119FE8A4AA27A7F4AA6849BFC` |

Điều này xác nhận provenance của evidence lịch sử, không biến phiên 16/11 thành PASS. Kết quả phiên hiện tại vẫn là **NOT RUN**.

## 5. Lệnh chạy lại sau khi Docker hoạt động

Trước hết chỉ đọc metadata:

```powershell
docker version
docker inspect tv4_s1_it_db
docker volume inspect tv4_s1_it_pgdata
docker port tv4_s1_it_db 5432/tcp
docker exec tv4_s1_it_db psql -U postgres -d sasms -X -Atc "SELECT current_database(), current_user, current_setting('server_version');"
```

Chỉ tiếp tục nếu container healthy, PostgreSQL major 16, mount đúng volume, port chỉ bind `127.0.0.1:15432`, database là `sasms` và không phải DB dùng chung.

Đặt owner DSN bằng secret local, không ghi vào source hoặc log, rồi chạy trên DB đã được nâng cấp; không dùng `--apply`:

```powershell
$env:DATABASE_URL = '<owner DSN for isolated 127.0.0.1:15432/sasms>'
python scripts/validate_sprint2.py `
  --dsn $env:DATABASE_URL `
  --evidence-dir database/evidence/sprint2/runs/20261116-recheck
```

Trạng thái của lần kiểm tra ban đầu ngày 16/11:

- Sprint 2 PostgreSQL integration: **NOT RUN**.
- Serial locking/concurrency mới: **NOT RUN**.
- Cleanup mới: **NOT RUN**.
- Evidence lịch sử đúng source hash: **AVAILABLE, không phải lần chạy mới**.

## 6. Lần chạy mới trên database dùng một lần — 17/11/2026

### 6.1 Môi trường được tạo riêng

Mô hình bám `.github/workflows/sprint2-staging.yml`, không dùng container hoặc volume Sprint 1:

| Thành phần | Giá trị đã xác minh |
|---|---|
| Container | `sasms_s2_tv4_once_20261117` |
| Image | `postgres:16` |
| PostgreSQL | `16.15 (Debian 16.15-1.pgdg13+2)` |
| Database mới | `sasms_sprint2_validation` |
| Owner | `sasms_ci` |
| Host binding | `127.0.0.1:15434 -> 5432/tcp` |
| Volume riêng | `sasms_s2_tv4_once_pgdata_20261117` |
| Network riêng | `sasms_s2_tv4_once_net_20261117` |
| Health trước test | `running/healthy` |
| Số bảng ngoài system trước test | `0` |
| Schema `sasms` trước test | `0` |

Container cũ `tv4_s1_it_db` vẫn ở trạng thái `exited` và volume `tv4_s1_it_pgdata` không được mount vào môi trường mới.

### 6.2 Lệnh chạy

Repository được mount read-only. Evidence được mount read-write từ thư mục ngoài repository. Password test cô lập được truyền qua environment và không ghi vào báo cáo/source.

```powershell
docker run --rm `
  --name sasms_s2_validator_once_20261117 `
  --network sasms_s2_tv4_once_net_20261117 `
  --workdir /workspace `
  --env PYTHONDONTWRITEBYTECODE=1 `
  --env PGHOST=sasms_s2_tv4_once_20261117 `
  --env PGPORT=5432 `
  --env PGUSER=sasms_ci `
  --env PGPASSWORD='<isolated-test-password>' `
  --env PGDATABASE=sasms_sprint2_validation `
  --mount 'type=bind,source=D:\software-project-management-an,target=/workspace,readonly' `
  --mount 'type=bind,source=D:\SASMS_TV4_BACKUPS\Sprint2\DB_VALIDATION_20261117_001,target=/evidence' `
  python:3.13-bookworm bash -lc `
  'apt-get update -qq && apt-get install -y -qq postgresql-client && python scripts/validate_sprint2.py --apply --evidence-dir /evidence'
```

`--apply` chỉ được dùng sau khi truy vấn read-only xác nhận database mới có `0` bảng nghiệp vụ và chưa có schema `sasms`.

### 6.3 Kết quả thực tế

- Bắt đầu: `2026-11-17 00:04:25` UTC+7.
- Kết thúc: `2026-11-17 00:04:34` UTC+7.
- Sprint 2 validator: **PASS** — `33` assertions.
- Regression Sprint 1 bên trong validator: **PASS** — `76` assertions.
- Hai ca POS độc lập: **PASS**.
  - Commit case PID: `443` và `446`.
  - Rollback case PID: `466` và `469`.
  - Mỗi ca có đúng một overlap conflict SQLSTATE `55P03`.
  - Retry sau release/rollback: `true`.
- Fixture cleanup: **PASS**.
- Payment guard được bật lại: **PASS**.
- Hậu kiểm read-only: `race_users=0`, `race_orders=0`, `race_devices=0`.
- Database sau test có `39` bảng trong schema `sasms`, chỉ thuộc baseline/validator dùng một lần.

Container mới đã được dừng sạch sau test với `exit=0`; container và volume được giữ nguyên để review. Không chạy `down -v`, `prune`, reset hoặc xóa volume.

### 6.4 Evidence bền vững

Thư mục ngoài repository:

`D:\SASMS_TV4_BACKUPS\Sprint2\DB_VALIDATION_20261117_001`

| File | SHA-256 |
|---|---|
| `summary.json` | `7695ADC3FAEB670C2D5C4F0619EB306C6C933EC77F1BCBA18427A3AAF10C365D` |
| `validation.log` | `F26A33C38234A2AAC4D7ABF51ACB6AA637A5AA5083C0537C15970C268D0E488C` |

Evidence còn chứa đầy đủ `sprint1-regression/summary.json`, `validation.log` và baseline regression. Kết quả PASS này chỉ chứng minh DB Sprint 2/ACL/two-session concurrency trên môi trường PostgreSQL 16 cô lập; không chứng minh HTTP E2E, payment provider, browser, máy in hoặc staging deployment.
