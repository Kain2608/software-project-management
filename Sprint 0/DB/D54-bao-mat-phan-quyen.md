# D54 — Thiết kế bảo mật và ma trận phân quyền

| Thuộc tính | Giá trị |
| --- | --- |
| Công việc | S0-21 — Bảo mật và phân quyền theo 9 vai trò |
| Phụ trách | Trần Trung Kiên — Architect / DBA |
| Phiên bản / ngày lập thực tế | 1.0 / 07/11/2026 |
| Trạng thái | Đề xuất để review; chưa có chữ ký Kiên/PM |
| Liên quan | [D50](D50-kien-truc-he-thong.md), D51, D57; D33 KS35–KS36 |

## 1. Cơ sở và mức độ hoàn thành

Bảng phân công yêu cầu ma trận **9 vai trò**, nhưng repo chưa có D34 FRS, danh sách chính thức 9 vai trò hoặc kết quả phỏng vấn KS35–KS36. Chín vai trò bên dưới là **phương án đề xuất**, dùng để thiết kế và tạo seed có thể review. Chưa có ứng dụng/API thực thi quyền trong đầu ra Sprint 0 DB; seed RBAC không tự trở thành cơ chế kiểm soát truy cập.

Các bảng `users`, `roles`, `permissions`, `user_roles`, `role_permissions` mô hình hóa quyền nghiệp vụ. PostgreSQL login/runtime role là lớp khác; không tạo chín PostgreSQL superuser hoặc chia sẻ tài khoản DB với nhân viên.

## 2. Chín vai trò nghiệp vụ đề xuất

| Mã | Vai trò | Phạm vi mặc định đề xuất |
| --- | --- | --- |
| `ADMIN` | Quản trị hệ thống | Cấu hình tài khoản/quyền toàn hệ thống; dùng tài khoản riêng cho việc quản trị |
| `STORE_MANAGER` | Quản lý cửa hàng | Dữ liệu thuộc cửa hàng được giao; duyệt điều chỉnh và hoàn tiền |
| `WAREHOUSE` | Nhân viên kho | Kho được giao thuộc cửa hàng; nhập kho, tra cứu thiết bị, lập kiểm kê |
| `CASHIER` | Thu ngân | Đơn và thanh toán ở cửa hàng làm việc; checkout; không tự duyệt hoàn tiền |
| `SALES` | Nhân viên bán hàng | Danh mục, tư vấn và đơn nháp tại cửa hàng; không xác nhận tiền |
| `DISPATCHER` | Điều phối giao/lắp | Đơn cần giao ở cửa hàng phụ trách; lịch, phân công và trạng thái giao |
| `TECHNICIAN` | Kỹ thuật viên | Chỉ nhiệm vụ được phân công và thông tin liên hệ cần thiết |
| `CUSTOMER_SERVICE` | Chăm sóc khách hàng / bảo hành | Hồ sơ bảo hành/đổi trả được giao hoặc cửa hàng phụ trách |
| `ANALYST` | Phân tích / báo cáo | Chỉ số tổng hợp và dữ liệu đã che định danh trong phạm vi được giao |

Nhân viên có thể được gán nhiều vai trò sau review; quyền là hợp các quyền được cấp trong phạm vi hợp lệ, không được cộng dồn để mở rộng cửa hàng ngoài phân công. Tài khoản không hoạt động hoặc không có vai trò hợp lệ luôn bị từ chối.

## 3. Ma trận hành động

Ký hiệu: `✓` = quyền trong phạm vi đã gán; `D` = được lập yêu cầu/nháp, không được duyệt; `A` = chỉ tài nguyên được phân công; `R` = chỉ dữ liệu đọc được giới hạn; `—` = từ chối. Cột quản trị bao gồm quyền kỹ thuật đề xuất; thao tác nghiệp vụ nhạy cảm vẫn phải có audit và review độc lập, không dùng quyền quản trị hằng ngày để bỏ qua kiểm soát.

| Hành động / mã quyền đề xuất | ADMIN | STORE_MANAGER | WAREHOUSE | CASHIER | SALES | DISPATCHER | TECHNICIAN | CUSTOMER_SERVICE | ANALYST |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Xem danh mục — `catalog.read` | ✓ | ✓ | ✓ | ✓ | ✓ | R | R | ✓ | R |
| Sửa danh mục / giá — `catalog.write` | ✓ | ✓ | — | — | — | — | — | — | — |
| Xem tồn / Serial — `inventory.read` | ✓ | ✓ | ✓ | ✓ | ✓ | R | A | R | R |
| Nhận hàng — `inventory.receive` | ✓ | ✓ | ✓ | — | — | — | — | — | — |
| Lập kiểm kê / yêu cầu điều chỉnh | ✓ | ✓ | D | — | — | — | — | — | — |
| Duyệt điều chỉnh — `inventory.adjust` | ✓ | ✓ | — | — | — | — | — | — | — |
| Xem đơn — `sales.read` | ✓ | ✓ | R | ✓ | ✓ | A | A | R | R |
| Lập đơn nháp | ✓ | ✓ | — | ✓ | D | — | — | — | — |
| Checkout — `sales.checkout` | ✓ | ✓ | — | ✓ | — | — | — | — | — |
| Duyệt hủy/ngoại lệ — `sales.approve` | ✓ | ✓ | — | — | — | — | — | — | — |
| Tra soát thanh toán — `payments.read` | ✓ | ✓ | — | ✓ | — | — | — | R | R |
| Đề nghị hoàn tiền | ✓ | ✓ | — | D | — | — | — | D | — |
| Duyệt/thực hiện hoàn — `payments.refund` | ✓ | ✓ | — | — | — | — | — | — | — |
| Điều phối giao/lắp — `logistics.manage` | ✓ | ✓ | — | — | — | ✓ | — | — | — |
| Cập nhật nhiệm vụ — `logistics.execute` | ✓ | ✓ | — | — | — | ✓ | A | — | — |
| Tiếp nhận bảo hành — `warranty.manage` | ✓ | ✓ | — | — | — | — | A | ✓ | — |
| Xem báo cáo — `reports.read` | ✓ | ✓ | — | — | — | — | — | — | R |
| Gợi ý AI — `ai.use` | ✓ | ✓ | — | ✓ | ✓ | — | — | ✓ | — |
| Quản trị tài khoản/quyền — `iam.manage` | ✓ | — | — | — | — | — | — | — | — |
| Đọc audit — `audit.read` | ✓ | R | — | — | — | — | — | — | — |
| Xóa/sửa audit đã ghi | — | — | — | — | — | — | — | — | — |

Các dòng lập nháp/yêu cầu là hợp đồng ứng dụng đề xuất, cần thêm permission riêng nếu được tách endpoint; không hiểu quyền đọc là quyền tạo nháp. Tập mã seed cuối cùng cần đối chiếu D57 khi triển khai. Phân quyền đọc có `R`/`A` cần DTO/view che trường và bộ lọc đối tượng; không cấp trực tiếp toàn bảng cho vai trò nghiệp vụ.

### Kiểm tra phạm vi ở mỗi request

Backend lấy user và vai trò từ session/token đã xác minh; không tin `role`, `store_id` hay `user_id` do client gửi như bằng chứng quyền. Với tài nguyên có ID, truy vấn phải bao gồm cửa hàng/kho/nhiệm vụ được phép, kể cả API chi tiết, tìm kiếm, xuất file và tác vụ nền. Không tìm thấy trong phạm vi trả phản hồi không tiết lộ dữ liệu ngoài phạm vi.

`user_roles.store_id` cho phép gán vai trò theo từng cửa hàng và nhiều cửa hàng cho một user. Giá trị `NULL` được đề xuất chỉ cho quyền toàn hệ thống đã được quản trị cấp có chủ ý; backend phải quy định rõ vai trò nào được dùng scope này, không mặc định mọi `NULL` đều được xem toàn chuỗi. Schema chưa mô hình hóa phân công chi tiết đến từng kho hoặc thời hạn ủy quyền; các nhu cầu đó cần migration sau review. Chưa có chính sách RLS hoặc API kiểm tra phạm vi thì không tuyên bố chống truy cập chéo cửa hàng đã hoàn thành.

Tạo/sửa vai trò chỉ cho `ADMIN`, có audit người cấp, người nhận, quyền thay đổi và lý do. Thu hồi quyền phải làm mất hiệu lực cache/session theo thiết kế ứng dụng. Quyền mới mặc định chưa cấp cho bất kỳ vai trò nào cho đến khi ma trận được review.

### Phân tách trách nhiệm

Người lập yêu cầu điều chỉnh/hoàn tiền không tự duyệt chính yêu cầu đó. Ghi `requested_by`, `approved_by`, thời điểm và lý do trong mô hình workflow trước khi bật chức năng này; nếu D57 chưa có đầy đủ trường/ràng buộc thì đây là yêu cầu Sprint sau, không phải bảo đảm DB hiện có. Nhà quản trị không được trực tiếp sửa payment đã hoàn tất để “sửa số”; dùng bút toán/trạng thái bù trừ có liên kết giao dịch gốc.

## 4. Tài khoản DB và ranh giới tin cậy

| Tài khoản / group role đề xuất | Quyền | Cách sử dụng |
| --- | --- | --- |
| Owner/migrator | Tạo schema, DDL, migration trong database SASMS | Chỉ pipeline migration/DBA; không gắn vào ứng dụng |
| Runtime ứng dụng | Kết nối; `USAGE` schema; DML cần thiết; gọi hàm nghiệp vụ được cho phép | Không superuser, không `CREATEDB`/`CREATEROLE`, không owner |
| Worker | Đọc/cập nhật hàng outbox được claim; ghi kết quả tích hợp theo nhu cầu | Tách credential khi có worker độc lập |
| Reporting | Chỉ đọc view báo cáo đã lọc PII | Không đọc password hash, raw audit payload hay bảng khách hàng đầy đủ |
| Backup | Quyền cần cho công cụ backup đã chọn | Secret riêng, hạn chế nguồn kết nối |

Tên group role/quyền SQL thực tế lấy từ script cấp quyền D57. Không chạy lệnh `GRANT ALL` cho `PUBLIC`; thu hồi quyền tạo ở schema và quyền thực thi hàm nhạy cảm không cần thiết. Thiết lập `search_path` cố định trong hàm đặc quyền; ưu tiên invoker, chỉ dùng definer khi đã review và khóa owner/schema. Không đặt mật khẩu mẫu thật hoặc thông tin kết nối Prod trong repo.

Chỉ backend tin cậy giữ credential runtime. Khi runtime có DML rộng trong một schema, DB chưa thể tự biết nhân viên nào được gọi bảng nào; authorization phải do backend thực hiện và được kiểm thử. Để tăng bảo vệ, có thể thu hẹp sang hàm nghiệp vụ hoặc RLS với cơ chế đặt ngữ cảnh đáng tin cậy sau review; không bật RLS hình thức rồi mặc định rằng connection pool đã an toàn.

### Quyền SQL hiện thực trong Sprint 0

[database/runtime-roles.sql](../../database/runtime-roles.sql) là bước provisioning tùy chọn cho DBA trên cluster thử nghiệm cô lập; schema/seed không tạo tài khoản đăng nhập. Script tạo hai group role `NOLOGIN`, không có superuser, tạo database/role, replication hoặc bypass RLS:

| Group role | Quyền hiện có | Giới hạn |
| --- | --- | --- |
| `sasms_app` | Đọc bảng/view SASMS; thêm đơn nháp bằng danh sách cột cho phép; sửa dòng đơn, liên kết thiết bị và payment; gọi bốn hàm nhận/giữ/bán/hủy | Không được trực tiếp ghi thiết bị, biến động, audit, outbox, hóa đơn hoặc trạng thái/tổng tiền đơn; các hàm đặc quyền ghi các dữ liệu này trong transaction |
| `sasms_reader` | Đọc `brands`, `categories`, `products`, `product_images`, `stock_availability` | Chỉ danh mục và số tồn tổng hợp; không phải role báo cáo đầy đủ, không được đọc bảng khách hàng/payment/audit |

Quyền `INSERT` vào `sales_orders` chỉ cấp cho `id`, `code`, `idempotency_key`, `store_id`, `warehouse_id`, `customer_id`, `channel`, `created_by`. Runtime không được truyền `status`, `total_amount`, `paid_at` hoặc `created_at`; trạng thái khởi tạo dùng mặc định `DRAFT`, số tiền/trạng thái kết thúc chỉ do hàm nghiệp vụ thay đổi. Không thay bằng quyền `INSERT` toàn bảng vì có thể tạo đơn giả ở trạng thái `RESERVED`/`PAID` để bỏ qua bước giữ hàng.

DBA cấp group role phù hợp cho LOGIN bí mật riêng của backend; LOGIN đó không được đồng thời là owner/migrator hoặc thuộc role khác có quyền ghi rộng. Quyền PostgreSQL được cộng dồn: script này không thu hồi mọi quyền/membership cũ của role đã tồn tại. Trước khi tái sử dụng tên role ở cluster khác, DBA phải rà soát các quyền hiện hữu; không xem script là công cụ tự làm sạch quyền cũ. Worker chưa có role triển khai trong baseline, cần migration riêng khi được xây dựng.

Các hàm nhận `p_actor` do backend truyền; chúng bảo đảm FK actor tồn tại và ghi dấu vết, chưa xác minh user đang đăng nhập, trạng thái hoạt động, vai trò hoặc scope. Backend phải xác minh các điều kiện đó trước khi gọi. Nhân viên và trình duyệt không được giữ credential `sasms_app`.

## 5. Xác thực, dữ liệu và bí mật

- Mật khẩu lưu hash chậm có salt do thư viện ứng dụng phù hợp tạo; đề xuất Argon2id, thông số đo theo hạ tầng và review trước triển khai. Không dùng MD5/SHA đơn thuần hoặc mật khẩu rõ. Seed không tạo tài khoản đăng nhập dùng chung với mật khẩu công khai.
- Session phía server hoặc token có thời hạn ngắn; refresh/revocation và logout phải có thiết kế rõ ở tầng ứng dụng. Cookie dùng `HttpOnly`, `Secure`, `SameSite` phù hợp; chống CSRF nếu xác thực bằng cookie. Đổi mật khẩu/khóa tài khoản thu hồi phiên.
- Giới hạn thử đăng nhập theo tài khoản và nguồn, thông báo không lộ tài khoản có tồn tại. Tài khoản quản trị đề xuất MFA; khôi phục tài khoản phải có kênh xác minh và audit.
- TLS cho client/API và kết nối DB khi qua mạng; DB chỉ mở mạng nội bộ, allowlist nguồn ứng dụng/bảo trì. Môi trường Dev loopback không thay thế cấu hình Prod.
- Secrets đặt ở biến môi trường cục bộ hoặc secret manager; file `.env` thật bị bỏ qua bởi Git. Khóa nhà cung cấp chỉ ở server, có phân quyền và lịch quay vòng; webhook xác minh bằng cơ chế chính thức của provider đã chọn.
- Chỉ lưu PII cần cho liên hệ/giao hàng/bảo hành; hạn chế đọc điện thoại, email, địa chỉ. Không lưu số thẻ/CVV hoặc gửi PII vào LLM. AI nhận ID sản phẩm, thông số và dữ liệu đã lọc.
- Ảnh/chứng từ tải lên có giới hạn dung lượng, loại nội dung và quyền truy cập; kiểm tra nội dung thực tế, tên đối tượng do server sinh. URL private có thời hạn nếu chứa chứng từ/PII.
- Backup, ổ đĩa và object storage mã hóa theo hạ tầng đã chọn; quyền quản trị khóa tách với người đọc dữ liệu. Thời hạn lưu/xóa cần khách hàng/PM xác nhận theo KS36, chưa tự đặt thành nghĩa vụ pháp lý.

SQL dùng tham số binding, không ghép chuỗi từ input. Mã QR/Serial được chuẩn hóa theo D51 trước so sánh; dữ liệu trong QR vẫn là input không tin cậy. Provider error và nội dung LLM không được thực thi thành SQL, mã lệnh hoặc lệnh quản trị.

## 6. Audit và chống sửa sai giao dịch

Audit nghiệp vụ ghi actor, hành động, loại/ID tài nguyên, thời điểm, correlation ID và phần thay đổi đã lọc khi thực hiện đăng nhập nhạy cảm, thay đổi quyền, nhập/xuất/điều chỉnh kho, checkout, hủy, hoàn tiền, sửa hồ sơ và xuất dữ liệu. Nên ghi cùng transaction của thay đổi để tránh thay đổi đã commit nhưng audit mất.

Không ghi password/hash, access token, secret, số thẻ hoặc toàn bộ payload chứa địa chỉ/số điện thoại. Ảnh chụp trước/sau phải lọc theo allowlist. Audit chỉ thêm với runtime, không sửa/xóa; DBA có đặc quyền vẫn có thể thay đổi DB, vì vậy khi cần chống can thiệp quản trị phải đẩy log sang nơi lưu riêng có cơ chế giữ bất biến. Phân quyền SQL đơn lẻ không chứng minh log không thể bị sửa bởi mọi đối tượng.

`inventory_movements` là lịch sử biến động; số tồn suy ra theo trạng thái/liên kết thiết bị hoặc projection có đối soát, tránh cho phép sửa tổng tồn độc lập không lưu nguồn. Chống bán trùng bằng transaction/khóa và ràng buộc DB; chống callback/checkout lặp bằng idempotency có kiểm tra payload, không chỉ nút bấm UI.

## 7. Rủi ro và cách kiểm chứng

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

## 8. Checklist review và ký duyệt

| Hạng mục | Trạng thái tại lúc lập |
| --- | --- |
| Ma trận đủ 9 vai trò để review | Đã soạn, tên vai trò đề xuất |
| Phân biệt RBAC nghiệp vụ và SQL role | Đã mô tả |
| D34 và xác nhận KS35–KS36 | Chưa có trong repo |
| So khớp permission seed D57 | Đã đối chiếu 9 vai trò / 17 mã quyền với ma trận; scope/che trường vẫn do backend thực hiện |
| Phân quyền endpoint/scope, MFA, provider webhook | Chưa có ứng dụng để kiểm chứng |
| Review chính sách dữ liệu và lưu giữ | Chờ BA/PM/chủ dữ liệu |
| Chữ ký Kiên và PM | Chưa có |

| Người review / ký | Nội dung | Trạng thái | Ngày / bằng chứng |
| --- | --- | --- | --- |
| Trần Trung Kiên | Schema, quyền DB, ranh giới bảo mật | Chờ review/ký | Chưa có |
| Vũ Duy Thái | Thực thi authorization, session, API | Chờ review | Chưa có |
| Nguyễn Quang Huy | Xác nhận vai trò và nghiệp vụ | Chờ review | Chưa có |
| Trịnh Quốc An — PM | Ma trận quyền và phân tách trách nhiệm | Chờ review/ký | Chưa có |
