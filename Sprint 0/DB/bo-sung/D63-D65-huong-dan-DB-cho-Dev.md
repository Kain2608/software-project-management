# D63–D65 — Bổ sung quy ước và môi trường DB cho Dev

| Thuộc tính | Nội dung |
|---|---|
| Đầu việc | Kiên hỗ trợ Thái ở S0-24; phần DB của D63/D64/D65 |
| Căn cứ | [Workbook Sprint 0](../inputs/phan-cong-sprint-0.xlsx), sheet “Theo người” dòng 22; “Phân chia công việc” dòng 5; “Tài liệu và minh chứng” dòng 27–29 |
| Ngày / nhánh làm việc | 07/11/2026; `DB` |
| Trạng thái | Bàn giao DB bổ sung để review; scaffold/SQL/CI DB đã có được giữ nguyên |

Workbook ghi D63/D65 chưa có file và D64 một phần. Repo hiện có scaffold DB, script kiểm tra và CI; bản này bổ sung quy ước và cách chạy còn thiếu từ phía Kiên. Thái chủ trì bộ D63–D65 toàn dự án, gồm khung frontend/backend và phiên bản ứng dụng. Không tạo thêm ứng dụng để thay phần việc của Thái.

## 1. D63 — Nhánh và quy ước thay đổi DB

Theo yêu cầu hiện tại của Kiên, công việc này làm và push trên [nhánh DB](https://github.com/Kain2608/software-project-management/tree/DB). Chủ repo/nhóm phụ trách review và merge vào main khi cần. Các tài liệu trước ghi `Kien` phản ánh nhánh lịch sử; không đổi chúng thành bằng chứng rằng đã làm hoặc đã được duyệt trên DB từ tháng 9.

Chạy từ thư mục repo; trước khi đổi nhánh/pull, xem và giữ các thay đổi chưa commit của mình. Nếu pull không fast-forward thì dừng cập nhật để giải quyết lịch sử, không reset/force để xóa thay đổi:

```powershell
git status --short --branch
git fetch origin
git switch DB
git pull --ff-only origin DB
git branch --show-current
```

Trước push, rà diff và chỉ stage tệp đúng phạm vi DB/tài liệu Kiên. `git add` theo đường dẫn cụ thể; không stage `.env`, dump, PII hoặc log chứa secret. Ví dụ khi đã có commit cần gửi:

```powershell
git diff --check
git diff --stat
git status --short --branch
git push origin DB
```

Lệnh push ở trên chỉ gửi DB; không push main, force-push hoặc tự merge main. Chủ repo quyết định PR/merge và bảo vệ main. Yêu cầu workbook về “boilerplate merge main”/“PR được Kiên duyệt” vẫn cần người sở hữu thực hiện; tài liệu hoặc một lần push không tự chứng minh review, cấu hình bảo vệ nhánh hoặc gate đã thông qua.

| Quy ước SQL / migration | Áp dụng khi review |
|---|---|
| Nguồn triển khai | SQL là nguồn; DBML/ERD/từ điển sinh từ catalog. Không xuất ngược SQL từ DBML để thay trigger/partial index/quyền |
| Định danh / kiểu | Giữ `snake_case`, schema-qualify `sasms.*` trong code gọi DB; UUID cho ID, numeric cho tiền, timestamptz cho thời điểm, FK cho tham chiếu; không dùng float cho tiền |
| API / query | Bind tham số; actor từ phiên đã xác thực; kiểm active/quyền/cửa hàng; chỉ SELECT cột DTO cần dùng. Không ghép SQL từ Serial hoặc đưa owner credential vào app |
| Transaction / khóa | Mọi chuyển trạng thái cốt lõi cùng movement/audit/outbox trong transaction. Giữ thứ tự khóa đã có: nhận hàng PO → phiếu → dòng; POS khóa đơn rồi thiết bị UUID tăng dần. Không gọi mạng trong transaction đang giữ khóa |
| Lịch sử / tiền | Không sửa/xóa CAPTURED qua app, không sửa tồn bằng UPDATE tùy ý; nghiệp vụ bù cần thiết kế riêng. Không dùng xóa bảng/disable trigger để “sửa lỗi test” |
| Phiên bản | Baseline S0 37 bảng giữ nguyên. V001/V002 đã áp dụng là bất biến; sửa tiếp bằng migration version mới. Không đổi checksum cũ hoặc chạy cùng migration bằng hai runner |
| Quyền / seed | Mỗi đường ghi mới cần quyền tối thiểu và review ACL; không GRANT ALL/PUBLIC/owner cho app. Seed baseline dùng dữ liệu giả, không dùng reseed để khôi phục quyền đã chủ ý thu hồi trên DB S1 |
| Artifacts / evidence | Sinh vào đúng phiên bản; S1 dùng `database/sprint1`, không ghi đè artifact S0. Log mới ghi riêng và gắn hash/commit, không thay log cũ để mô phỏng lần chạy mới |

Checklist review cho thay đổi SQL thực sự:

- Truy vết quy tắc được thay, dữ liệu cũ bị ảnh hưởng, backfill và trường hợp NULL/unique/FK/tiền; không tự sửa mã hoặc cắt dữ liệu lịch sử để vượt CHECK.
- Kiểm transaction, khóa, retry/commit chưa rõ kết quả, hai phiên cùng thiết bị/key và bảo toàn dữ liệu khi nâng cấp.
- Kiểm actor/scope, quyền EXECUTE/DML, search_path cố định của SECURITY DEFINER và việc đường gọi cũ có thể bỏ qua bảo vệ mới.
- Chạy bộ kiểm tra phù hợp trên database thử; kiểm artifacts đúng số bảng/phiên bản và đối chiếu snapshot dữ liệu/quyền trước–sau.
- Nêu rủi ro triển khai, backup/restore và phương án sửa tiếp; sau commit migration dùng migration mới hoặc quy trình khôi phục đã review, không mặc định down migration xóa dữ liệu là an toàn.

Thay đổi chỉ bổ sung văn bản không cần chạy lại toàn bộ DB nếu SQL/script không đổi. Các bước trên dành cho thay đổi DB có tác động, không phải checklist tự xác nhận mọi lần sửa chữ.

## 2. D64 — Scaffold DB đang có

| Vị trí | Nội dung dùng lại |
|---|---|
| [database/schema.sql](../../../database/schema.sql), [seed.sql](../../../database/seed.sql), [runtime-roles.sql](../../../database/runtime-roles.sql) | Baseline 37 bảng, fixtures và hai group role NOLOGIN |
| [database/README.md](../../../database/README.md), [Compose](../../../docker-compose.yml), [.env.example](../../../.env.example) | PostgreSQL local; port bind 127.0.0.1; không có secret thật |
| [validate_db.py](../../../scripts/validate_db.py), [constraints.sql](../../../database/tests/constraints.sql), [runtime_acl.sql](../../../database/tests/runtime_acl.sql) | Ràng buộc/lifecycle, ACL, tranh chấp giữ hàng, benchmark tùy chọn |
| [generate_db_artifacts.py](../../../scripts/generate_db_artifacts.py) | Kiểm/sinh DBML, ERD và từ điển từ PostgreSQL thật |
| [migrations](../../../database/migrations), [hướng dẫn S1](../../../database/sprint1/README.md), [validate_sprint1.py](../../../scripts/validate_sprint1.py) | Upgrade riêng, kiểm giữ dữ liệu/quyền và nhập lô; không ghi đè baseline |
| [CI S0](../../../.github/workflows/database.yml), [CI S1](../../../.github/workflows/sprint1-database.yml), [CI môi trường](../../../.github/workflows/pre-sprint-environments.yml) | Job kiểm DB và diễn tập backup/restore; chỉ coi run đạt khi có kết quả tương ứng đúng commit |
| [D22 môi trường](../../../Ti%E1%BB%81n%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md), [infra](../../../infra/README.md) | Kế hoạch Dev/Staging/Prod, bí mật, migration và vận hành; chưa có host Staging/Prod thật |

Repo DB không có cấu hình build ứng dụng Java/Node hay endpoint HTTP hoàn chỉnh. Việc scaffold DB chạy được chưa chứng minh toàn ứng dụng đã build, đăng nhập hoặc deploy.

## 3. D65 — Toolchain và thông tin kết nối

| Thành phần | Phiên bản / sử dụng trong phần DB |
|---|---|
| PostgreSQL server / client | Major 16; client `psql`, `createdb`; backup/restore dùng bộ công cụ PG16 theo D22. Evidence đã có dùng server 16.15; tag Compose `postgres:16-alpine` không khóa patch nên ghi phiên bản thực tế khi chạy |
| Python | 3.10+ theo script hiện có; CI cấu hình 3.13. Validator dùng stdlib và `psql`, không cần cài package từ requirements |
| Docker | Tùy chọn cung cấp server local; cần Compose hỗ trợ `up --wait`. Host chạy validator vẫn cần Python và `psql` trên PATH |
| JDK / Node | Workbook D65 nêu JDK 17, Node 18+, Python 3.10 như cấu hình kế hoạch. Thái chốt toolchain app theo mã nguồn ứng dụng; phần DB không cài hoặc chạy build JDK/Node giả |
| Credential | Database thử riêng, owner có quyền tạo role phục vụ test. App dùng LOGIN bí mật thuộc group phù hợp, không dùng owner. Browser không nhận credential DB |

Lệnh dưới chạy từ gốc repo. DSN chỉ có host/port/user/dbname; mật khẩu lấy từ password file hoặc secret local. Không ghi mật khẩu vào Git, câu lệnh/log hoặc evidence. Windows có thể dùng password file `%APPDATA%\postgresql\pgpass.conf`; Linux dùng `~/.pgpass` với quyền 0600. Tạo/chỉnh file bằng trình soạn thảo riêng với dòng theo định dạng libpq `host:port:database:user:password`.

### Windows / PowerShell: dựng S0 trên database thử mới

Trước hết kiểm Python, client và Docker đã có. Nếu chưa có `psql` trên PATH, thêm thư mục `bin` PostgreSQL 16 vào PATH cho phiên shell; không sửa PATH hệ thống trong tài liệu này.

```powershell
python --version
psql --version
docker compose version
if (-not (Test-Path -LiteralPath '.env')) {
    Copy-Item -LiteralPath '.env.example' -Destination '.env'
}
```

Chỉnh `POSTGRES_PASSWORD` trong `.env` thành mật khẩu local riêng; cấu hình cùng mật khẩu trong password file. Compose chỉ dùng mật khẩu khởi tạo khi volume được tạo lần đầu; sửa `.env` không tự đổi mật khẩu của cluster đã tồn tại. Sau đó:

```powershell
docker compose up -d --wait
docker compose exec -T db createdb -U sasms_owner sasms_s0_review
$env:PGPASSFILE = Join-Path $env:APPDATA 'postgresql\pgpass.conf'
$dbReviewDsn = 'host=127.0.0.1 port=5432 user=sasms_owner dbname=sasms_s0_review'
psql -X --dbname $dbReviewDsn -c 'SHOW server_version;'
python scripts/validate_db.py --dsn $dbReviewDsn --apply --benchmark --evidence-dir database/.local/s0-review
python scripts/generate_db_artifacts.py --dsn $dbReviewDsn --check
psql -X --dbname $dbReviewDsn -v ON_ERROR_STOP=1 -f database/checks.sql
```

`createdb` phải tạo database mới chưa có `sasms`; nếu tên đã tồn tại, chọn tên thử khác và cập nhật DSN thay vì xóa. `--apply` từ chối schema đã có. Trên DB S0 đã nạp, bỏ `--apply` khi cần kiểm lại; **không dùng validator S0 trên DB đã nâng cấp S1**. Evidence local ở `database/.local/` được gitignore, giữ nguyên evidence đã bàn giao.

Nếu client ngoài PATH, ví dụ `$dbPsql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'`, gọi `& $dbPsql ...` thay cho `psql` và thêm `--psql $dbPsql` cho từng script Python. Đường dẫn là ví dụ, cần trỏ tới vị trí cài thực tế.

### Linux: cùng quy trình trên database thử mới

Chuẩn bị Python 3.10+, client PG16 và Docker Compose trong máy Dev; không dùng các lệnh này để cài lên máy Prod. Chỉ sao chép `.env.example` khi chưa có `.env`, chỉnh mật khẩu local và tạo password file trước khi kết nối từ host:

```bash
python3 --version
psql --version
docker compose version
if [ ! -f .env ]; then cp .env.example .env; fi
```

Sau khi chỉnh `.env` và `~/.pgpass` bằng trình soạn thảo riêng:

```bash
chmod 600 .env "$HOME/.pgpass"
export PGPASSFILE="$HOME/.pgpass"
docker compose up -d --wait
docker compose exec -T db createdb -U sasms_owner sasms_s0_review
export DATABASE_URL='host=127.0.0.1 port=5432 user=sasms_owner dbname=sasms_s0_review'
psql -X --dbname "$DATABASE_URL" -c 'SHOW server_version;'
python3 scripts/validate_db.py --dsn "$DATABASE_URL" --apply --benchmark --evidence-dir database/.local/s0-review
python3 scripts/generate_db_artifacts.py --dsn "$DATABASE_URL" --check
psql -X --dbname "$DATABASE_URL" -v ON_ERROR_STOP=1 -f database/checks.sql
```

Nếu đã có PostgreSQL 16 local thay vì Docker, tạo database thử bằng `createdb` của server đó và đổi DSN, bỏ các lệnh Compose. Nếu cổng 5432 đang dùng, chọn server/port riêng và giữ DSN/client đồng bộ. Kết thúc phiên có thể dùng `docker compose stop` để dừng server và giữ dữ liệu; không cần xóa volume.

### Nâng cấp S1 là đường riêng

Nếu muốn đối chiếu increment đã có, tạo database thử khác rồi chạy `validate_sprint1.py --apply`; xem [hướng dẫn S1](../../../database/sprint1/README.md). Trên database S0 cần nâng cấp thật, dùng `migrate_db.py --status`, `migrate_db.py`, rồi `database/sprint1/runtime-roles.sql`. Trước đó backup và thử trên bản sao. Không nạp lại `schema.sql`, reseed quyền baseline hoặc sửa V001/V002 đã áp dụng. S1 có 39 bảng nghiệp vụ và ledger `sasms_meta.schema_migrations` riêng.

## 4. Cách đọc kết quả và giới hạn

[Summary S0](../../../database/evidence/summary.json) đã ghi phiên bản, hash nguồn và kết quả thực tế: 56 assertions ràng buộc/lifecycle, 7 ACL, hai phiên tranh giữ hàng và lookup 100.000 thiết bị dùng index. [Summary S1](../../../database/evidence/sprint1/summary.json) là kết quả upgrade riêng. [Evidence môi trường](../../../Ti%E1%BB%81n%20Sprint/DB/evidence/environment-summary.json) là diễn tập DB thử riêng; không đổi thành bằng chứng triển khai Staging/Prod.

Các lệnh hướng dẫn ở trên chưa tự được ghi là một lượt chạy mới trong phần bổ sung văn bản. Khi Dev chạy, xem exit code, summary/log và hash nguồn đúng lượt; `checks.sql` dự kiến không có dòng sai lệch ở truy vấn đối soát. Workflow dùng path filter DB/script nên một commit chỉ sửa tài liệu có thể không kích hoạt lại CI DB; không suy ra CI fail hoặc đã có run mới từ việc không thấy job cho commit tài liệu.

Các phần ngoài DB còn chờ Thái/chủ repo: boilerplate ứng dụng, toolchain/build app, cấu hình bảo vệ main và review/merge; host/domain/secret để Staging/Prod và deployment log thật theo D22. Tài liệu này hoàn thành phần hỗ trợ DB có thể soạn, không thay việc người sở hữu xác nhận hoặc ký duyệt.
