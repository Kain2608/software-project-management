# D50 / D54 — Bổ sung căn cứ và đối chiếu kiểm soát

Bản D50/D54 đầu tiên được tạo trên `Kien`, phần DB đã được tích hợp vào `main`. Rà soát ngày **20/11/2026** cập nhật căn cứ đầu vào, ranh giới phiên bản và việc cần người review xác nhận; giữ SQL/ERD/DBML S0 làm snapshot. Hash tài liệu trong bằng chứng parser cũ thuộc phiên bản trước, không tự chứng nhận D50/D54 đã sửa.

## 1. D50: đối chiếu đầu vào hiện có

| Nội dung trong bản trước | Tình trạng repo khi đối chiếu lần này | Cách sử dụng |
|---|---|---|
| D22 chưa có, ARC-01 cần bổ sung kế hoạch môi trường | Đã có [D22 tiền Sprint](../../../Ti%E1%BB%81n%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md), [ma trận môi trường](../../../infra/environment-matrix.json) và [runbook](../../../infra/README.md) | Dùng làm căn cứ triển khai Dev, review Staging/Prod và quy trình phát hành; không tiếp tục ghi D22 là tài liệu chưa tìm thấy |
| Backup/restore cần diễn tập | [Summary logical restore](../../../Ti%E1%BB%81n%20Sprint/DB/evidence/environment-summary.json) đang lưu 41 kiểm tra, PostgreSQL 16.16, 41 bảng/5 migration ngày 17/11; [snapshot CI](../../../Ti%E1%BB%81n%20Sprint/DB/evidence/ci-runs.json) có SHA riêng | Phân biệt lượt restore 17/11 với run CI lịch sử; không dùng summary hiện tại chứng minh lượt cũ 07/11 hoặc M1 tháng 9 |
| D34/FRS_V2 | Đã có D34 và D35 BA | Dùng BR-01/03, SER-01…10 và chín vai trò BA; V003…V005 hiện thực phần DB, không suy regex toàn hãng |
| Microservices và môi trường triển khai | D50 đã có sơ đồ bối cảnh, thành phần, sequence checkout và lộ trình tách dịch vụ | Giữ nội dung; PostgreSQL dùng chung ở nền DB, các dịch vụ độc lập vẫn là kiến trúc mục tiêu |
| Môi trường FE/BE và cấu trúc source | Đã có source và API/POS tại root `backend/` và `frontend/` | Source hiện hành Java 25/Spring Boot 4.1.1, React/Vite; hồ sơ TV4 cũ Java 26 cần đồng bộ, không hồi tố phê duyệt B1 hoặc suy build ứng dụng từ test DB |
| Tài khoản DB kết nối ứng dụng backend | [database/runtime-roles.sql](../../../database/runtime-roles.sql) tạo group role `sasms_app` | Kiên xác nhận bắt buộc dùng login thuộc `sasms_app` (C2), tuyệt đối không dùng `sasms_owner` cho runtime |

Các đường dẫn ở phần Sprint 0 thể hiện baseline **37 bảng**. Sprint 1 dùng V001/V002 để nâng lên **39 bảng**; V003…V005 đưa mô hình hiện hành lên **41 bảng**. [Migration](../../../database/migrations) có lịch sử/checksum riêng; không thay ERD/SQL Sprint 0 bằng mô hình mới rồi gắn nhãn là bản gốc.

Môi trường Dev có cấu hình thực thi và CI có run thật. Ma trận môi trường ghi Staging/Prod `provisioned=false`; chưa có host, credential hoặc deployment log remote. Mục tiêu RPO 15 phút/RTO 4 giờ vẫn cần người sở hữu duyệt và một phương án WAL/PITR tương ứng. Logical dump/restore không thay phương án đó.

## 2. D54: giữ ma trận, làm rõ lớp thực thi

| Lớp | Baseline Sprint 0 đã có | Bổ sung hoặc giới hạn cần người nhận hiểu |
|---|---|---|
| Chín vai trò nghiệp vụ | D54 và seed có 9 role/17 permission, phạm vi cửa hàng/nhiệm vụ, nguyên tắc từ chối mặc định | D34 đã chốt chín vai trò; V005 thêm CATALOG_MANAGER/WAREHOUSE_STAFF/SALES_STAFF và giữ alias cũ; ADMIN không tự có quyền nghiệp vụ |
| Quyền PostgreSQL | `sasms_app` và `sasms_reader` là group role NOLOGIN; runtime không trực tiếp sửa tồn, tổng/trạng thái đơn hoặc audit | Credential thuộc backend tin cậy; quyền đọc rộng của backend không tự lọc người dùng cuối theo cửa hàng |
| Hàm Sprint 0 | Bốn hàm nhập/giữ/bán/hủy cùng transaction và audit; actor phải tham chiếu user tồn tại | Actor là ngữ cảnh backend đã xác thực; bản này chưa tự kiểm active user/RBAC cho toàn bộ nghiệp vụ |
| Nâng cấp Sprint 1 | Có helper `has_permission`/`assert_permission`, catalog revision và batch import kiểm active/scope | Đây là phần hiện thực sau Sprint 0; V004 kiểm active/scope cho reserve/complete/cancel/expire; backend tiếp tục xác thực JWT và scope request |
| Dấu vết và tích hợp | Audit/movement/outbox ghi cùng transaction; runtime không được sửa/xóa các bảng được bảo vệ | Worker/provider, correlation ID xuyên API và log ngoài DB chưa thành dịch vụ hoàn chỉnh chỉ từ schema |

Việc gán/thu hồi quyền thực tế phải do người có thẩm quyền thực hiện, audit và kiểm phạm vi. D57 có source thực thi S0 được giữ nguyên; trạng thái “ĐÃ CÓ” trong workbook không xác nhận bản tài liệu đã cập nhật hoặc thay chữ ký review.

## 3. Danh mục review còn cần quyết định

| Câu hỏi | Người phối hợp | Bằng chứng cần bổ sung khi có |
|---|---|---|
| D34/D35 đã có; profile SKU và đường nhập IMEI phụ đã được cấu hình/kiểm tích hợp chưa? | Huy, Kiên, Thái, đại diện kho | Hồ sơ profile đã duyệt, kiểm HTTP/UI và đối chiếu tem; không suy quy tắc toàn hãng |
| Chín role D34/V005 đã khớp assignment thực và phiên bản quyết định liên nhóm chưa? | Huy, Kiên, PM | Review mapping/scope, quyền giảm giá/hoàn tiền và cập nhật D53 cũ; không thay alias lịch sử cho role canonical |
| Module nào thuộc baseline thiết kế, module nào chỉ cần demo hiện hành? | PM, Huy, Thái | Quyết định phạm vi theo phiên bản, tách kế hoạch tháng 9 và CR tháng 10 |
| Khi nào có Staging/Prod và phương án WAL/backup ngoài máy? | Kiên, Thái, PM/chủ hệ thống | Cấu hình và log triển khai/diễn tập trên môi trường thật |
| Kiến trúc và bảo mật bản nào được ký? | Dev Lead, Kiên, PM/Sponsor | Hồ sơ TV4 ghi lịch sử xác nhận A1/B1/C2; D50 v1.2/D54 sửa ngày 20/11 cần review theo source hiện hành, không dùng ghi nhận cũ làm chữ ký mới |

Đầu ra của phụ lục là căn cứ review D50/D54 hiện có. Các sơ đồ và evidence cũ chỉ được tái sử dụng trong phạm vi phiên bản/hash thực sự đã kiểm; rà soát tài liệu không tạo thêm kết quả SQL, build hoặc deploy.
