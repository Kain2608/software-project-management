# Runbook môi trường và sao lưu DB — D22

[D22](../Ti%E1%BB%81n%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md) là kế hoạch Dev/Staging/Prod; [environment-matrix.json](environment-matrix.json) ghi trạng thái và ranh giới môi trường. Staging/Prod chưa được cấp phát bởi repo này. Các lệnh dưới đây dùng PostgreSQL 16 và phải được chạy với đúng danh tính backup/restore đã được cấp cho môi trường tương ứng.

## Sao lưu logical

Yêu cầu Python 3.10+, `psql`, `pg_dump` major 16. Đặt DSN qua biến môi trường/password file hoặc secret; không ghi mật khẩu vào Git. Ví dụ PowerShell, chọn đường dẫn ngoài repo cho archive:

```powershell
$env:DATABASE_URL = 'host=127.0.0.1 port=5432 user=sasms_owner dbname=sasms'
python scripts/db_ops.py backup --dsn $env:DATABASE_URL --output C:\Backups\sasms-before-release.dump
```

Kết quả là custom archive và file `sasms-before-release.dump.manifest.json` chứa SHA-256, phiên bản và thời điểm UTC. Script từ chối nếu archive hoặc manifest đã có; đặt tên mới cho mỗi lần sao lưu. Không đưa dump vào artifact GitHub hoặc repository. File chứa dữ liệu đầy đủ, cần quyền truy cập và mã hóa theo hạ tầng lưu trữ; script này không tự mã hóa hoặc lưu WAL.

## Phục hồi vào database mới

DBA chuẩn bị các PostgreSQL role được ACL archive tham chiếu trên server đích; không dùng credential runtime của ứng dụng cho việc tạo DB. Dùng kết nối maintenance tới `postgres`, không trỏ nhầm vào database đang phục vụ:

```powershell
$env:MAINTENANCE_DSN = 'host=127.0.0.1 port=5432 user=sasms_owner dbname=postgres'
python scripts/db_ops.py restore --maintenance-dsn $env:MAINTENANCE_DSN --target-db sasms_restore_review --input C:\Backups\sasms-before-release.dump
```

Script kiểm SHA-256/manifest trước khi tạo target, từ chối database đã tồn tại hoặc tên hệ thống. Database mới được tạo với `ALLOW_CONNECTIONS false`, thu hồi quyền `PUBLIC`, rồi mới cho owner kết nối để restore. `pg_restore --single-transaction --exit-on-error --no-owner` giữ ACL trong archive và rollback nội dung restore nếu lỗi. Target vẫn riêng tư; không tự cấp CONNECT cho ứng dụng hoặc đổi traffic. Nếu lỗi, giữ database mới cho DBA kiểm tra, không tự DROP/ghi đè database nào.

Sau restore, xác minh dữ liệu/ràng buộc, migration ledger, quyền runtime và các giao dịch mẫu; đối soát payment/outbox và giữ worker tắt trước khi cân nhắc mở traffic. Phục hồi thay thế Production cần quyết định phát hành và quy trình riêng của PM/chủ hệ thống.

Nếu tools không nằm trên PATH, thêm `--psql 'C:\path\psql.exe'`, `--pg-dump 'C:\path\pg_dump.exe'` hoặc `--pg-restore 'C:\path\pg_restore.exe'` theo subcommand. `--help` liệt kê tham số đầy đủ.

## Diễn tập tái lập trên dữ liệu giả

Chuẩn bị **database thử mới và rỗng**, cùng quyền tạo database khác trong cluster thử. Không dùng validator có fixtures trên dữ liệu Production:

```powershell
createdb -h 127.0.0.1 -U postgres pre_sprint_environment
python scripts/validate_environment.py --apply --source-dsn 'host=127.0.0.1 user=postgres dbname=pre_sprint_environment' --maintenance-dsn 'host=127.0.0.1 user=postgres dbname=postgres' --work-dir C:\Backups\pre-sprint-drill
```

Validator dựng baseline và migration, tạo dữ liệu giả, sao lưu và khôi phục target mới; kiểm dữ liệu/ledger/constraints/functions/quyền, replay giao dịch và các nhánh từ chối overwrite/checksum. [Evidence](../Ti%E1%BB%81n%20Sprint/DB/evidence) ghi kết quả thực chạy. [Workflow CI](../.github/workflows/pre-sprint-environments.yml) dùng PostgreSQL 16 tạm thời và chỉ upload báo cáo, không upload archive.

Một lần diễn tập logical restore không chứng minh đạt RPO 15 phút/RTO 4 giờ của Production. Các mục tiêu đó còn chờ phê duyệt và cần base backup + WAL/PITR hoặc giải pháp quản trị tương đương, tải thật và diễn tập đầy đủ.
