# D54 — Bảo mật và phân quyền DB

Trần Trung Kiên — Architect / DBA; cập nhật **17/11/2026** theo [D34 FRS BA](../BA/D34_FRS_V2_SASMS.docx), FR-01 và “Tác nhân và quyền được chốt”. SQL S0 là snapshot lịch sử; hiện hành bổ sung qua V001/V004/[V005](../../database/migrations/V005__frs_business_roles.sql).

## Chín vai trò nghiệp vụ

| Vai trò | Cho phép | Giới hạn |
|---|---|---|
| ADMIN | Cấp/khóa tài khoản, gán quyền, audit quản trị | Không tự duyệt đổi trả/hoàn tiền hoặc checkout; cần gán thêm vai trò nghiệp vụ có chủ đích |
| CATALOG_MANAGER | SKU, thông số, giá và profile định danh | Không sửa giao dịch xác nhận hoặc xác nhận tiền |
| WAREHOUSE_STAFF | Nhập/tra thiết bị trong phạm vi được giao | Không sửa thanh toán hoặc bán hàng |
| CASHIER | POS, tiền mặt, in/in lại, gợi ý | Không duyệt hoàn/đổi hoặc cấp quyền |
| SALES_STAFF | Tra danh mục/tồn và tư vấn | Không ghi PAID |
| DISPATCHER | Lập/đổi lịch đơn PAID | Không sửa thu tiền hoặc thay kỹ thuật viên ghi kết quả |
| TECHNICIAN | Việc được phân công, kết quả và bảo hành đủ điều kiện | Không duyệt đổi trả hoặc thay lịch ngoài quyền |
| CUSTOMER_SERVICE | Tra hồ sơ, nhận yêu cầu, bảo hành | Không tự duyệt hoàn tiền |
| STORE_MANAGER | Duyệt ngoại lệ/đổi trả, mở bán lại sau kiểm tra, báo cáo | Theo siêu thị được giao; không mặc định quản trị IAM/catalog toàn chuỗi |

V005 giữ role/assignment cũ để không mất lịch sử. WAREHOUSE/SALES là alias is_legacy của WAREHOUSE_STAFF/SALES_STAFF; quyền cũ chỉ thu hẹp theo allowlist, không phục hồi grant đã thu hồi. ANALYST nằm ngoài chín role D34, giữ lịch sử nhưng không có quyền tự động; quản trị review và gán lại có chủ đích. Ba mã legacy không phải ba business role BA mới phê duyệt.

## Scope và credential

has_permission(actor,permission,store) yêu cầu user active, permission và store hợp lệ. Scope NULL chỉ nhận assignment toàn chuỗi; không nới helper vì consumer thiếu store. Catalog API cần hợp đồng đọc phù hợp người dùng cửa hàng.

Hàm catalog/nhập lô/chọn/release/reserve/complete/cancel/expire kiểm quyền SQL hiện hành. Backend vẫn xác thực JWT/session, ánh xạ trusted actor và lọc phạm vi đối tượng/DTO. DB không xác thực JWT hoặc có RLS toàn hệ thống chỉ bằng seed role. UUID actor do client gửi không chứng minh danh tính.

- sasms_app/sasms_reader là group NOLOGIN, không superuser/CREATEDB/CREATEROLE/BYPASSRLS. Backend dùng LOGIN riêng thuộc sasms_app; không dùng owner hoặc đưa credential vào browser.
- Owner/migrator áp DDL sau backup/thử nâng cấp. App dùng allowlist; không trực tiếp sửa stock/status/total, registry/profile hoặc audit.
- PUBLIC bị thu hồi EXECUTE; reader chỉ đọc catalog/tồn tổng hợp, không customer/payment/audit. Runtime không tự phê duyệt profile.
- CAPTURED bất biến, tổng tiền xác nhận bằng tổng đơn; dùng quy trình hoàn tiền riêng. PENDING không tự giải phóng theo TTL, phải đối soát theo D34 BR-03.
- Audit/movement/outbox cùng transaction. Có quyền/bảng không chứng minh đã hoàn thành workflow hậu bán/phân công.

Kiểm [ACL baseline](../../database/tests/runtime_acl.sql), [SQL Sprint 2](../../database/tests/sprint2/test_sprint2_concurrency_and_stock.sql), [D34/D35 regression](../../database/tests/sprint2/identifiers_and_leases.sql) và [summary](../../database/evidence/sprint2/summary.json). Có ca riêng cho chín role canonical, ADMIN không tự duyệt hoàn tiền và CATALOG_MANAGER không checkout. Test DB không thay JWT/HTTP/E2E hoặc chữ ký PO.

Các kiểm soát thiết kế dưới đây tiếp tục là yêu cầu bàn giao cho ứng dụng/vận hành; không ghi chúng thành tính năng đã triển khai chỉ từ migration DB.

## Xác thực, dữ liệu và bí mật

- Mật khẩu lưu hash chậm có salt do thư viện ứng dụng phù hợp tạo; đề xuất Argon2id, thông số đo theo hạ tầng và review trước triển khai. Không dùng MD5/SHA đơn thuần hoặc mật khẩu rõ. Seed không tạo tài khoản đăng nhập dùng chung với mật khẩu công khai.
- Session phía server hoặc token có thời hạn ngắn; refresh/revocation và logout phải có thiết kế rõ ở tầng ứng dụng. Cookie dùng `HttpOnly`, `Secure`, `SameSite` phù hợp; chống CSRF nếu xác thực bằng cookie. Đổi mật khẩu/khóa tài khoản thu hồi phiên.
- Giới hạn thử đăng nhập theo tài khoản và nguồn, thông báo không lộ tài khoản có tồn tại. Tài khoản quản trị đề xuất MFA; khôi phục tài khoản phải có kênh xác minh và audit.
- TLS cho client/API và kết nối DB khi qua mạng; DB chỉ mở mạng nội bộ, allowlist nguồn ứng dụng/bảo trì. Môi trường Dev loopback không thay thế cấu hình Prod.
- Secrets đặt ở biến môi trường cục bộ hoặc secret manager; file `.env` thật bị bỏ qua bởi Git. Khóa nhà cung cấp chỉ ở server, có phân quyền và lịch quay vòng; webhook xác minh bằng cơ chế chính thức của provider đã chọn.
- Chỉ lưu PII cần cho liên hệ/giao hàng/bảo hành; hạn chế đọc điện thoại, email, địa chỉ. Không lưu số thẻ/CVV hoặc gửi PII vào LLM. AI nhận ID sản phẩm, thông số và dữ liệu đã lọc.
- Ảnh/chứng từ tải lên có giới hạn dung lượng, loại nội dung và quyền truy cập; kiểm tra nội dung thực tế, tên đối tượng do server sinh. URL private có thời hạn nếu chứa chứng từ/PII.
- Backup, ổ đĩa và object storage mã hóa theo hạ tầng đã chọn; quyền quản trị khóa tách với người đọc dữ liệu. Thời hạn lưu/xóa cần khách hàng/PM xác nhận theo KS36, chưa tự đặt thành nghĩa vụ pháp lý.

SQL dùng tham số binding, không ghép chuỗi từ input. Mã QR/Serial được chuẩn hóa theo D51 trước so sánh; dữ liệu trong QR vẫn là input không tin cậy. Provider error và nội dung LLM không được thực thi thành SQL, mã lệnh hoặc lệnh quản trị.

## Audit và chống sửa sai giao dịch

Audit nghiệp vụ ghi actor, hành động, loại/ID tài nguyên, thời điểm, correlation ID và phần thay đổi đã lọc khi thực hiện đăng nhập nhạy cảm, thay đổi quyền, nhập/xuất/điều chỉnh kho, checkout, hủy, hoàn tiền, sửa hồ sơ và xuất dữ liệu. Nên ghi cùng transaction của thay đổi để tránh thay đổi đã commit nhưng audit mất.

Không ghi password/hash, access token, secret, số thẻ hoặc toàn bộ payload chứa địa chỉ/số điện thoại. Ảnh chụp trước/sau phải lọc theo allowlist. Audit chỉ thêm với runtime, không sửa/xóa; DBA có đặc quyền vẫn có thể thay đổi DB, vì vậy khi cần chống can thiệp quản trị phải đẩy log sang nơi lưu riêng có cơ chế giữ bất biến. Phân quyền SQL đơn lẻ không chứng minh log không thể bị sửa bởi mọi đối tượng.

`inventory_movements` là lịch sử biến động; số tồn suy ra theo trạng thái/liên kết thiết bị hoặc projection có đối soát, tránh cho phép sửa tổng tồn độc lập không lưu nguồn. Chống bán trùng bằng transaction/khóa và ràng buộc DB; chống callback/checkout lặp bằng idempotency có kiểm tra payload, không chỉ nút bấm UI.

## Rủi ro và cách kiểm chứng

| Rủi ro | Kiểm soát thiết kế | Kiểm chứng cần có |
| --- | --- | --- |
| Truy cập đơn/Serial cửa hàng khác qua ID | Scope ở từng truy vấn và DTO hạn chế trường | Hai tài khoản khác cửa hàng, thử list/detail/export/update; đều từ chối ngoài phạm vi |
| Thu ngân tự hoàn tiền hoặc cấp quyền | Mặc định từ chối, quyền riêng và người duyệt khác người lập | Gọi trực tiếp endpoint bằng role thiếu quyền; kiểm tra không thay đổi dữ liệu |
| Bán cùng một thiết bị đồng thời | Row lock, unique/FK, kiểm tra trạng thái trong transaction | Hai connection cùng checkout, chỉ một thao tác thành công |
| Webhook/checkout bị gửi lại | Định danh chống lặp, xác minh provider và payload | Lặp cùng request; thử đổi payload/tiền/transaction ID, không ghi nhận tiền lần hai |
| Rò rỉ secret/PII | Secret ngoài repo, redaction, quyền đọc hạn chế | Rà soát Git/log/backup/DTO; thử quyền reporting/runtime |
| Sửa hoặc mất audit | Append-only cho runtime, ghi cùng transaction, lưu riêng nếu cần | Runtime thử UPDATE/DELETE bị chặn; lỗi transaction không để audit sai |
| Khôi phục DB phát sinh gửi lặp | Tắt worker, đối soát payment/outbox trước mở lại | Diễn tập restore ở môi trường cô lập và ghi kết quả |

Các kiểm thử DB thực hiện được nằm trong bộ D57 và log chạy thật. Các hàng liên quan API, provider, mã hóa hạ tầng, MFA, RLS, restore Prod hoặc scope nhiều cửa hàng là tiêu chí bàn giao cho đội triển khai, chưa được xác nhận chỉ bằng schema/seed.
