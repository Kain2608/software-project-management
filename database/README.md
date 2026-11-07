# Chạy DB SASMS — PostgreSQL 16

Đây là DB baseline Sprint 0 của Kiên: 37 bảng, dữ liệu mô phỏng, 4 giao dịch DB, bộ kiểm tra và artifacts ERD. Xem [D51](../Sprint%200/DB/D51-thiet-ke-co-so-du-lieu.md) và [D57](../Sprint%200/DB/D57-luoc-do-va-kiem-thu.md) trước khi tích hợp.

## 1. Chuẩn bị

Cần PostgreSQL **16** server, client `psql` và Python 3.10+. Dùng database thử riêng do tài khoản owner sở hữu. Schema không cài extension, không có mật khẩu mặc định và không tự DROP dữ liệu. `gen_random_uuid()` có sẵn trong PostgreSQL 16.

Chạy từ thư mục gốc repo. Trên PowerShell, dùng đường dẫn đầy đủ tới `psql.exe` nếu chưa có trên PATH. Credential thật dùng `PGPASSFILE`/password file hoặc secret của môi trường; không commit `.env` hoặc ghi URI có mật khẩu vào evidence.

Ví dụ tạo DB mới qua client PostgreSQL:

```powershell
createdb -h 127.0.0.1 -p 5432 -U postgres sasms_validation
$env:DATABASE_URL = 'host=127.0.0.1 port=5432 user=postgres dbname=sasms_validation'
python scripts/validate_db.py --dsn $env:DATABASE_URL --apply --benchmark
```

Lệnh validator có quyền tạo role thử và dữ liệu fixtures nên chỉ dùng trên môi trường thử cô lập. `--apply` từ chối nếu đã có schema `sasms`; để kiểm lại schema đã nạp, bỏ `--apply` theo trợ giúp `--help`. Bộ test rollback/cleanup fixtures theo ID riêng, không xóa schema/database.

## 2. Server local bằng Docker (tùy chọn)

```powershell
Copy-Item .env.example .env
# Thay POSTGRES_PASSWORD trong .env bằng mật khẩu local riêng.
docker compose up -d --wait
docker compose exec -T db psql -X -U sasms_owner -d sasms -v ON_ERROR_STOP=1 -f /work/database/schema.sql
docker compose exec -T db psql -X -U sasms_owner -d sasms -v ON_ERROR_STOP=1 -f /work/database/seed.sql
docker compose exec -T db psql -X -U sasms_owner -d sasms -v ON_ERROR_STOP=1 -f /work/database/runtime-roles.sql
```

Port chỉ bind `127.0.0.1:5432`; volume giữ dữ liệu qua các lần restart. Docker ở đây cung cấp server. Muốn chạy toàn bộ validator Python từ host, vẫn cần client `psql` và `DATABASE_URL` trỏ đến DB Docker. Chạy validator không có `--apply` nếu đã nạp schema bằng ba lệnh trên. Group role `sasms_app`/`sasms_reader` là NOLOGIN; DBA tạo credential riêng và cấp group role qua công cụ secret, không cấp login owner cho ứng dụng.

## 3. Tái lập và đồng bộ artifacts

```powershell
python scripts/validate_db.py --dsn $env:DATABASE_URL
python scripts/generate_db_artifacts.py --dsn $env:DATABASE_URL --check
# Khi chủ động đổi schema bằng migration đã review:
python scripts/generate_db_artifacts.py --dsn $env:DATABASE_URL
psql --dbname $env:DATABASE_URL -X -v ON_ERROR_STOP=1 -f database/checks.sql
```

Nếu client nằm ngoài PATH, truyền `--psql 'C:\path\to\psql.exe'` cho cả hai script. `checks.sql` là đối soát chỉ đọc, mỗi truy vấn dự kiến không có dòng lỗi ở các luồng cốt lõi.

`schema.sql` là nguồn triển khai chính. Script sinh artifact đọc catalog thật, bao gồm FK ghép, CHECK và index; `--check` dùng để phát hiện file sinh bị cũ. DBML không mã hóa trọn trigger, partial index hoặc runtime ACL nên không dùng SQL xuất ngược từ DBML để triển khai.

CI dùng PostgreSQL 16 và cùng validator trong [database.yml](../.github/workflows/database.yml). Cấu hình workflow được bàn giao trong nhánh; log local không chứng minh workflow đã chạy trên GitHub.

## 4. Seed và thao tác mẫu

Seed chỉ thêm role/permission và dữ liệu DEMO. Không cấp login hoặc role cho user demo; `is_active=false`, không có mật khẩu/auth subject. Chạy seed lặp không tạo thêm dòng và không tự cấp permission mới ngoài allowlist. Không reseed để thay thế migration thu hồi quyền.

Phiếu DEMO có định mức 100.005 tivi nhằm phục vụ test quy mô, **không có thiết bị tồn trước khi gọi hàm nhập**. Đây không phải số liệu kinh doanh. Ví dụ tạo một thiết bị demo qua owner/trusted backend:

```sql
SELECT sasms.receive_device(
  '00000000-0000-4000-8000-000000000061',
  'DEMO-TV-SERIAL-001', NULL,
  '00000000-0000-4000-8000-000000000001'
);
SELECT * FROM sasms.stock_availability;
```

Các hàm tin actor từ backend; việc user demo không active không bị hàm DB dùng như kiểm tra đăng nhập. Backend thực phải từ chối user chưa active trước khi gọi. API tích hợp cần tạo `sales_orders`, các dòng `sales_order_items`, chọn thiết bị trong `order_devices`, gọi `reserve_order`, xác nhận tiền ở `payments`, rồi `complete_sale`. Chi tiết tiền, retry và hủy nằm trong [hợp đồng D57](../Sprint%200/DB/D57-luoc-do-va-kiem-thu.md).

## 5. Ranh giới bảo mật và vận hành

`sasms_app` chỉ dành cho backend tin cậy, có quyền đọc rộng để phục vụ nghiệp vụ; RBAC 9 role và phạm vi cửa hàng do backend kiểm tra. `sasms_reader` chỉ đọc catalog/tồn tổng hợp. App không thể trực tiếp sửa tồn, status/total đơn, audit hoặc outbox, và không thể sửa/xóa khoản CAPTURED. Provision script chỉ dùng trên cluster mới/cô lập; nếu tái sử dụng role, DBA phải audit cả membership và quyền đã cấp từ trước.

Không tự sinh bằng chứng đã nhận tiền từ ảnh VietQR. Không có provider sandbox, API app hoặc worker outbox/TTL trong bộ Sprint 0 này. Những bảng hậu bán là nền thiết kế, chưa cho phép tác nghiệp hoàn tiền/đổi trả/điều chuyển qua runtime role. Review chữ ký và xác nhận nghiệp vụ xem [hồ sơ M1](../Sprint%200/DB/D126-ho-so-review-M1.md).
