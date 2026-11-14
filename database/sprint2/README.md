# DB Sprint 2 — Serial POS, tồn kho và quyền runtime

Phần DB của Kiên cho S2-03/S2-06, hỗ trợ AI S2-08 và kiểm chứng S2-12. Kế thừa PostgreSQL **16**, Python **3.10+**, client `psql`, baseline Sprint 0 và V001/V002 Sprint 1. Xem [DS2 DB](../../Sprint%202/DB/DS2-thiet-ke-va-ban-giao-DB.md) và [rà soát liên nhóm](../../Sprint%202/DB/RA-SOAT-SPRINT2.md).

## Nâng cấp database đã có Sprint 1

Dùng owner/migrator sau backup và thử trên bản sao; app dùng login thuộc `sasms_app`. DSN lấy từ `DATABASE_URL`/password file/secret ngoài Git.

```powershell
python scripts/migrate_db.py --dsn $env:DATABASE_URL --status
python scripts/migrate_db.py --dsn $env:DATABASE_URL
psql -X --dbname $env:DATABASE_URL -v ON_ERROR_STOP=1 -f database/sprint1/runtime-roles.sql
psql -X --dbname $env:DATABASE_URL -v ON_ERROR_STOP=1 -f database/sprint2/realtime_stock_and_locking.sql
```

File Sprint 2 bọc DDL/ACL trong transaction và chạy lại được; không ghi vào ledger V001/V002. Giữ source hash đã kiểm cho lần phát hành; không sửa migration cũ đã áp dụng. Không nạp lại `schema.sql`/`seed.sql` trên DB có dữ liệu/nâng cấp. Group runtime phải được provision trước extension để nhận grant allowlist; nếu thêm group sau đó, chạy lại extension bằng owner.

## Kiểm chứng trên database thử mới, cô lập

```powershell
createdb -h 127.0.0.1 -U postgres sasms_sprint2_validation
$env:DATABASE_URL = 'host=127.0.0.1 user=postgres dbname=sasms_sprint2_validation'
python scripts/validate_sprint2.py --dsn $env:DATABASE_URL --apply --evidence-dir database/evidence/sprint2
psql -X --dbname $env:DATABASE_URL -v ON_ERROR_STOP=1 -f database/sprint2/demo.sql
```

`--apply` chỉ dùng với **DB thử mới**: runner dựng và regression Sprint 0/1, provision runtime rồi áp dụng Sprint 2. Trên DB thử đã nâng cấp, bỏ `--apply` để chạy lại. Validator dùng owner tạo/dọn fixture; không chạy trên DB nghiệp vụ. Ca checkout có payment CAPTURED đã commit: cleanup chỉ trên đúng UUID fixture, tạm DISABLE riêng trigger `guard_payments` để xóa payment mô phỏng rồi ENABLE lại trong cùng transaction trước COMMIT. Nếu cleanup lỗi, transaction rollback cả thay đổi trigger; runner kiểm trigger bật trước/sau. Quy tắc bất biến payment của ứng dụng không bị thay đổi. Nếu client ngoài PATH, thêm `--psql 'C:\path\psql.exe'` cho Python và dùng đường dẫn đầy đủ cho lệnh client.

Runner chỉ cần thư viện chuẩn Python và `psql`, không cần driver Python hay `dblink`. Mọi lệnh SQL dùng `ON_ERROR_STOP=1`, kiểm return code. Có ca lỗi SQL/assertion chủ ý để chứng minh fail propagation; lỗi được mong đợi đó không phải SQL sản phẩm thất bại.

| Kiểm chứng | Bằng chứng cần đọc |
|---|---|
| Regression S0/S1 | `sprint1-regression/`, gồm baseline, migration safety và test nhập kho |
| Extension | Áp dụng và reapply không đổi dữ liệu nghiệp vụ cũ |
| Giá/view | `products.list_price AS price` và projection fallback chạy được |
| ACL/authorization | `PUBLIC`/`sasms_reader` không được gọi function mới; `sasms_app` được gọi; release kiểm active actor/checkout/store và DRAFT |
| Hai kết nối POS | PID khác nhau; POS1 giữ hàng tại barrier, POS2 cùng Serial lỗi `55P03`; kiểm sau commit và rollback |
| Tồn/checkout | Một liên kết active; delta IN_STOCK → RESERVED → SOLD; side effect commit duy nhất; dọn đúng fixture và so snapshot |
| CI failure | Lỗi SQL/assertion trả nonzero, câu SQL phía sau không chạy |

`summary.json` lưu trạng thái `passed`/`failed`, source SHA-256, assertions và các ca concurrency; `validation.log` lưu đầu ra. Phiên bản PostgreSQL được ghi trong `sprint1-regression/summary.json`. Chỉ dùng kết quả của nguồn khớp hash. Workflow lưu artifact cả khi lỗi; readiness ghi `NOT_DEPLOYED`, không phải deploy Staging.

File [test_sprint2_concurrency_and_stock.sql](../tests/sprint2/test_sprint2_concurrency_and_stock.sql) kiểm ACL/luồng nghiệp vụ trong một phiên và rollback; **chạy file SQL này riêng chưa kiểm hai kết nối đồng thời**. Dùng validator để thực hiện contention.

## Hợp đồng và giới hạn

- View có một hàng theo product active/kho thuộc store active; bảng warehouse không có cột `is_active`. `price` là alias của cột vật lý `list_price`; không thêm cột `products.price`.
- `available_stock` đếm IN_STOCK, gồm Serial được chọn trong DRAFT nhưng chưa reserve. `reserved_stock` đếm RESERVED, `sold_stock` đếm SOLD. `total_physical_stock` là tổng bản ghi device kể cả SOLD, không phải lượng hàng vật lý tại kho.
- Chọn Serial dùng NOWAIT và lease DRAFT 15 phút từ lúc tạo đơn; release lấy store từ đơn để kiểm `sales.checkout`. Backend xác thực/ánh xạ actor và kiểm scope mọi đường đọc/ghi; SQL không xác thực JWT hay có RLS toàn hệ thống.
- Fallback SQL là heuristic candidate còn IN_STOCK theo store, chưa là luật tương thích phụ kiện đã duyệt, chưa nối vào engine Java. Cache cross-sell hiện 5 phút theo D55/`AiCacheManager`.
- [Demo](demo.sql) chỉ đọc dữ liệu mô phỏng baseline trong transaction rồi rollback; không tạo phiên POS, payment, in hóa đơn hoặc gọi LLM.
- Tồn được đọc theo snapshot; kiểm query sau commit không chứng minh UI zero-lag hoặc p95 AI/50 request. POS backend hiện còn status skeleton.
