# Chạy và kiểm chứng DB Sprint 3

PostgreSQL **16**, Python **3.10+**, client `psql`. Backend giữ Java **25** theo `pom.xml`. Snapshot Sprint 3 có **41 bảng**, V001…V007 và extension Sprint 2. [Phạm vi bàn giao](../../Sprint%203/DB/README.md) · [Hợp đồng SQL](../../Sprint%203/DB/DS3-thiet-ke-va-ban-giao-DB.md) · [SMS](../../Sprint%203/DB/DS3-SMS-adapter-va-ban-giao.md).

## Nâng cấp bản sao DB hiện hành

Dùng owner/migrator sau backup và thử trên bản sao. Không nạp lại schema/seed lên DB đã có dữ liệu. Credential đặt ngoài Git bằng password file/secret; DSN ví dụ không chứa mật khẩu.

```powershell
python scripts/migrate_db.py --dsn $env:DATABASE_URL --status
python scripts/migrate_db.py --dsn $env:DATABASE_URL --target-version 7
psql -X --dbname $env:DATABASE_URL -v ON_ERROR_STOP=1 -f database/sprint3/runtime-roles.sql
```

DB phải đã có runtime role và extension Sprint 2 theo runbook trước đó. V006 từ chối dữ liệu ACTIVE warranty trùng để DBA kiểm tra. Mặc định cờ lắp đặt của SKU/đơn cũ là true do chưa có nguồn xác nhận; rà SKU và ảnh hưởng đơn trước khi tích hợp. Không sửa file migration đã áp; khôi phục backup đã kiểm hoặc thêm migration tiến tới khi cần sửa.

Group `sasms_sms_worker` là NOLOGIN, chỉ được USAGE schema và EXECUTE claim/finish. Tạo login worker riêng qua kênh secret rồi cấp group, không cấp owner/sasms_app cho worker. App nghiệp vụ không cần quyền claim/finish. Role đã tồn tại cần audit membership/quyền cũ. Chạy worker riêng với cấu hình datasource của login này; mặc định worker và adapter đều tắt.

## Kiểm database thử mới

```powershell
createdb -h 127.0.0.1 -U postgres sasms_sprint3_validation
$env:DATABASE_URL = 'host=127.0.0.1 user=postgres dbname=sasms_sprint3_validation'
$s3Evidence = Join-Path $env:TEMP ('sasms-s3-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
python scripts/validate_sprint3.py --dsn $env:DATABASE_URL --apply --evidence-dir $s3Evidence
python scripts/generate_db_artifacts.py --dsn $env:DATABASE_URL --expected-tables 41 --output-dir database/sprint3 --schema-label 'Sprint 3' --check
```

`--apply` yêu cầu DB thử mới; chạy regression S0/S1/S2, rồi nâng V006/V007. Bỏ `--apply` để kiểm lại DB thử đã nâng cấp. Validator cần owner tạo role và fixtures, không dùng trên dữ liệu nghiệp vụ. Nếu `psql` không trên PATH, thêm `--psql 'C:\path\psql.exe'`. Evidence ghi đúng thời gian chạy, source hash và kết quả; dùng thư mục mới cho mỗi lần chạy công bố.

`logistics_and_warranty.sql` rollback toàn bộ fixture. Ba ca concurrency dùng dữ liệu mô phỏng đã commit, sau đó dọn đúng UUID và so snapshot; không DROP schema/DB hoặc disable trigger trong ca mới. Regression S2 giữ cách dọn payment mô phỏng được mô tả trong runbook S2. SQL lỗi làm validator trả nonzero và lưu summary failed.

## Demo và bằng chứng

```powershell
psql -X --dbname $env:DATABASE_URL -v ON_ERROR_STOP=1 -f database/sprint3/demo.sql
```

Demo chỉ đọc metadata/trạng thái tổng hợp rồi rollback. Luồng nghiệp vụ đầy đủ được thực thi bởi bộ SQL test và validator; chạy demo không gửi SMS. Backend test SMS dùng mock HTTP client và adapter MOCK. Kiểm thử provider thật cần chọn provider và gói thử, mapping/template/credential riêng; ACCEPTED không chứng minh máy khách đã nhận tin.

DBML/ERD/từ điển sinh từ catalog: `generate_db_artifacts.py` cùng tham số trên, bỏ `--check` khi chủ động sinh lại sau migration đã review. Snapshot S0/S1/S2 giữ nguyên. Workflow Sprint 3 chuẩn bị chạy trên GitHub; chỉ có log local không chứng minh CI/remote Staging.

Runner `validate_sprint2.py` và `validate_environment.py` cố định nâng cấp tới V005 để giữ ý nghĩa của regression lịch sử. Sprint 3 dùng runner riêng tới V007. Các migration cũ giữ nguyên checksum.
