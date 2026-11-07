# DB Sprint 1 — nâng cấp từ Sprint 0

Phần DB của Kiên: S1-03 (danh mục/SKU), S1-06 (Serial/IMEI) và S1-10 (users/roles). Xem [bộ bàn giao](../../Sprint%201/DB/README.md) để đối chiếu phân công và phần hỗ trợ Dev/BA.

## Nâng cấp database hiện có

Yêu cầu PostgreSQL 16, Python 3.10+, client `psql` và kết nối owner/migrator. Sao lưu và thử migration trên bản sao trước khi triển khai thật. Đặt `DATABASE_URL` bằng DSN không ghi mật khẩu trong repo; dùng password file hoặc secret của môi trường.

```powershell
# Database này đã có schema.sql, seed.sql và runtime-roles.sql của Sprint 0.
python scripts/migrate_db.py --dsn $env:DATABASE_URL --status
python scripts/migrate_db.py --dsn $env:DATABASE_URL
psql -X --dbname $env:DATABASE_URL -v ON_ERROR_STOP=1 -f database/sprint1/runtime-roles.sql
python scripts/migrate_db.py --dsn $env:DATABASE_URL --status
```

Nếu `psql` ngoài PATH, thêm `--psql 'C:\path\psql.exe'` cho script Python và dùng đường dẫn đầy đủ khi gọi client. Script runtime hỗ trợ cấp group role sau migration; không tạo login/password hoặc gán role cho user thực. Trên DB mới, nạp baseline theo [hướng dẫn Sprint 0](../README.md), sau đó chạy các lệnh trên.

Runner khóa migration bằng advisory lock, kiểm checksum và bọc **toàn bộ migration còn thiếu** trong một transaction. Chạy lại không thay dữ liệu/quyền. Sửa file đã áp dụng, thiếu lịch sử hoặc chèn version cũ đều bị từ chối; thêm migration version mới để sửa tiếp. `sasms_meta.schema_migrations` là ledger của runner này, không phải lịch sử Flyway. Không chạy cùng SQL bằng một migration engine thứ hai.

V001 giữ dữ liệu cũ, bổ sung kiểm tra SKU/name/specification, revision và helper quyền. Nếu dữ liệu cũ không đạt CHECK mới, toàn lần nâng cấp rollback; làm sạch có review rồi thử lại. Quyền đã thu hồi của role hiện có được giữ nguyên ngay từ lần đầu nâng cấp; chỉ role mới được nhận quyền mặc định. Không dùng lại `database/seed.sql` để quản lý quyền sau nâng cấp vì đó là seed baseline, có thể cấp lại quyền đã thu hồi.

V002 thêm hai bảng `identifier_rules`, `inventory_import_batches`: tổng 39 bảng nghiệp vụ, ledger ở schema riêng. SQL/ERD Sprint 0 được giữ nguyên để truy vết phiên bản.

## Kiểm chứng tái lập trên database thử mới

```powershell
createdb -h 127.0.0.1 -U postgres sasms_sprint1_validation
$env:DATABASE_URL = 'host=127.0.0.1 user=postgres dbname=sasms_sprint1_validation'
python scripts/validate_sprint1.py --dsn $env:DATABASE_URL --apply
python scripts/generate_db_artifacts.py --dsn $env:DATABASE_URL --expected-tables 39 --output-dir database/sprint1 --check
psql -X --dbname $env:DATABASE_URL -v ON_ERROR_STOP=1 -f database/sprint1/demo.sql
```

Validator dùng owner và fixtures riêng, chạy regression Sprint 0 trước nâng cấp, rồi kiểm tra bảo toàn dữ liệu, rollback/checksum, quyền, catalog, quy tắc định danh, batch và các phiên nhập đồng thời. Chỉ chạy trên DB thử cô lập. [Log](../evidence/sprint1/validation.log) và [summary](../evidence/sprint1/summary.json) lưu kết quả thực tế; chúng không chứng minh Staging hoặc PO đã nghiệm thu.

Demo chỉ thao tác SQL, tạo danh mục và hai thiết bị giả, retry cùng request key rồi rollback toàn bộ. Không đăng nhập ứng dụng, không dùng camera hoặc provider thật.

## Hợp đồng tích hợp

- `create_product`/`update_product` yêu cầu `catalog.write` toàn hệ thống; role quản lý chỉ có scope một cửa hàng không được sửa catalog chung. Cập nhật dùng revision hiện tại; revision cũ lỗi `40001` để API yêu cầu tải lại.
- `receive_device_batch(receipt_item, request_key, devices, actor)` nhận 1–500 object `{serial, imei?}`. Serial trim/uppercase; IMEI trim, rỗng thành NULL, nếu có đúng 15 chữ số. Thứ tự phần tử là một phần của payload.
- Cùng key, actor, receipt và payload đã chuẩn hóa trả lại đúng UUID cũ; đổi một thành phần bị từ chối. Quyền active/scope luôn kiểm tra lại trước khi trả retry. Cả lô lỗi thì không để thiết bị, movement, audit hoặc batch record dở dang.
- `receive_device` đơn lẻ vẫn hoạt động nhưng đã kiểm quyền giống batch. Đường nhập cũ không kiểm quyền đã chuyển thành hàm private và thu hồi EXECUTE của mọi caller ngoài owner.
- Rule riêng theo hãng chỉ hoạt động khi có source/approver/thời điểm xác nhận; không seed regex Sony/Samsung/LG/Apple khi D35 chưa được cung cấp. Đây là workflow metadata cho DBA, chưa có endpoint duyệt rule. D35 còn chờ BA/kho xác nhận.
- `has_permission` là kiểm quyền theo actor tin cậy, **không xác thực JWT**. API phải xác thực token, ánh xạ user, không tin actor/role do trình duyệt gửi. Các hàm POS của Sprint 0 chưa thuộc phạm vi hardening RBAC Sprint 1 này.

Artifacts [DBML](schema.dbml), [ERD](erd.mmd) và [từ điển](data-dictionary.md) được sinh từ catalog đã nâng cấp. Muốn cập nhật sau migration mới, chạy generator không có `--check`; kiểm tra lại trước khi commit.
