# D50 / D54 — Bổ sung căn cứ và đối chiếu kiểm soát

Nhánh tiếp tục công việc: **`DB`**. Bản D50/D54 trước được tạo trên `Kien`; giữ nguyên để bảo toàn lịch sử và hash trong bằng chứng parser. Phụ lục này chỉ cập nhật căn cứ đầu vào, ranh giới phiên bản và việc cần người review xác nhận.

## 1. D50: đối chiếu đầu vào hiện có

| Nội dung trong bản trước | Tình trạng repo khi đối chiếu lần này | Cách sử dụng |
|---|---|---|
| D22 chưa có, ARC-01 cần bổ sung kế hoạch môi trường | Đã có [D22 tiền Sprint](../../../Ti%E1%BB%81n%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md), [ma trận môi trường](../../../infra/environment-matrix.json) và [runbook](../../../infra/README.md) | Dùng làm căn cứ triển khai Dev, review Staging/Prod và quy trình phát hành; không tiếp tục ghi D22 là tài liệu chưa tìm thấy |
| Backup/restore cần diễn tập | Đã có [40 kiểm tra logical restore](../../../Ti%E1%BB%81n%20Sprint/DB/evidence/environment-summary.json) và [snapshot CI](../../../Ti%E1%BB%81n%20Sprint/DB/evidence/ci-runs.json) ở lần bổ sung sau | Đây là bằng chứng bổ sung tháng 11, không đổi thành kết quả đã có ở M1 tháng 9 |
| D34/FRS_V2 chưa có | Vẫn không tìm thấy bản FRS trong repo sau khi cập nhật | Dùng D05/D06 và các quyết định thiết kế đã đánh dấu đề xuất; BA đối chiếu FRS trước ký baseline |
| Microservices và môi trường triển khai | D50 đã có sơ đồ bối cảnh, thành phần, sequence checkout và lộ trình tách dịch vụ | Giữ nội dung; PostgreSQL dùng chung ở nền DB, các dịch vụ độc lập vẫn là kiến trúc mục tiêu |
| Môi trường FE/BE và cấu trúc source | Đã có source skeleton tại root `backend/` và `frontend/` | Kiên xác nhận đặt tại root (A1), giữ baseline Java 26 + Spring Boot 4.1.1 (B1), FE React/Vite |
| Tài khoản DB kết nối ứng dụng backend | [database/runtime-roles.sql](../../../database/runtime-roles.sql) tạo group role `sasms_app` | Kiên xác nhận bắt buộc dùng login thuộc `sasms_app` (C2), tuyệt đối không dùng `sasms_owner` cho runtime |

Các đường dẫn ở phần Sprint 0 thể hiện baseline **37 bảng**. Sprint 1 dùng [hai migration](../../../database/migrations) để nâng lên **39 bảng**; không thay ERD/SQL Sprint 0 bằng mô hình mới rồi gắn nhãn là bản gốc.

Môi trường Dev có cấu hình thực thi và CI có run thật. Ma trận môi trường ghi Staging/Prod `provisioned=false`; chưa có host, credential hoặc deployment log remote. Mục tiêu RPO 15 phút/RTO 4 giờ vẫn cần người sở hữu duyệt và một phương án WAL/PITR tương ứng. Logical dump/restore không thay phương án đó.

## 2. D54: giữ ma trận, làm rõ lớp thực thi

| Lớp | Baseline Sprint 0 đã có | Bổ sung hoặc giới hạn cần người nhận hiểu |
|---|---|---|
| Chín vai trò nghiệp vụ | D54 và seed có 9 role/17 permission, phạm vi cửa hàng/nhiệm vụ, nguyên tắc từ chối mặc định | Danh sách vẫn là đề xuất vì nguồn yêu cầu số lượng 9 nhưng chưa cung cấp danh sách được BA/PM ký |
| Quyền PostgreSQL | `sasms_app` và `sasms_reader` là group role NOLOGIN; runtime không trực tiếp sửa tồn, tổng/trạng thái đơn hoặc audit | Credential thuộc backend tin cậy; quyền đọc rộng của backend không tự lọc người dùng cuối theo cửa hàng |
| Hàm Sprint 0 | Bốn hàm nhập/giữ/bán/hủy cùng transaction và audit; actor phải tham chiếu user tồn tại | Actor là ngữ cảnh backend đã xác thực; bản này chưa tự kiểm active user/RBAC cho toàn bộ nghiệp vụ |
| Nâng cấp Sprint 1 | Có helper `has_permission`/`assert_permission`, catalog revision và batch import kiểm active/scope | Đây là phần hiện thực sau Sprint 0; các hàm POS ngoài phạm vi hardening đó vẫn cần backend kiểm quyền |
| Dấu vết và tích hợp | Audit/movement/outbox ghi cùng transaction; runtime không được sửa/xóa các bảng được bảo vệ | Worker/provider, correlation ID xuyên API và log ngoài DB chưa thành dịch vụ hoàn chỉnh chỉ từ schema |

Không đổi ma trận bằng việc thêm quyền mặc định hoặc cấp tài khoản đăng nhập để làm đẹp checklist. Việc gán/thu hồi quyền thực tế phải do người có thẩm quyền thực hiện, audit và kiểm phạm vi. D57 được giữ nguyên theo trạng thái “ĐÃ CÓ” trong workbook.

## 3. Danh mục review còn cần quyết định

| Câu hỏi | Người phối hợp | Bằng chứng cần bổ sung khi có |
|---|---|---|
| D34 và quy tắc Serial/IMEI hãng có khớp mô hình hiện có không? | Huy, Kiên, đại diện kho | FRS/rule có nguồn, phiên bản và xác nhận thực tế |
| Danh sách 9 role, scope global/cửa hàng và quyền giảm giá/hoàn tiền có được chấp thuận không? | Huy, Kiên, PM | Ma trận được review; các thay đổi cần migration/API tương ứng |
| Module nào thuộc baseline thiết kế, module nào chỉ cần demo hiện hành? | PM, Huy, Thái | Quyết định phạm vi theo phiên bản, tách kế hoạch tháng 9 và CR tháng 10 |
| Khi nào có Staging/Prod và phương án WAL/backup ngoài máy? | Kiên, Thái, PM/chủ hệ thống | Cấu hình và log triển khai/diễn tập trên môi trường thật |
| Kiến trúc và bảo mật bản nào được ký? | Dev Lead, Kiên, PM/Sponsor | Kiên đã ký/xác nhận D50 v1.1 (baseline FE/BE, root source và sasms_app runtime) ngày 11/11/2026; PM An đã chấp thuận A1, B1, C2 |

Đầu ra của phụ lục là phần căn cứ còn thiếu để review D50/D54 hiện có. Các sơ đồ, ma trận và bằng chứng đã hoàn chỉnh được tái sử dụng nguyên vẹn.
