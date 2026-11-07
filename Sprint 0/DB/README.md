# Sprint 0 — Hồ sơ Kiến trúc / Cơ sở dữ liệu của Trần Trung Kiên

Nhánh thực hiện: `Kien`. Phạm vi: làm lại toàn bộ đầu ra Kiên phụ trách trong Sprint 0, gồm S0-17 (D50), S0-18 (D51, D57), S0-21 (D54), và phần bàn giao DB hỗ trợ các đầu việc liên quan. Hồ sơ được dựng lại từ nguồn có trong repo và bảng phân công do người dùng cung cấp; trạng thái “đã có”, “một phần” hoặc “đã phê duyệt” trong nguồn cũ không đồng nghĩa phiên bản mới đã được duyệt.

[Bảng phân công đầu vào](inputs/phan-cong-sprint-0.xlsx) là bản sao nguyên vẹn file Excel người dùng cung cấp; SHA-256 `327d95b1e160948d65d0b448c2e8cb0edeb678a0f41f0491f2923f327473df7a`. Không sửa trạng thái hoặc chữ ký trong nguồn gốc để mô phỏng nghiệm thu.

Sprint theo bảng phân công: **12/09–25/09/2026**; mốc M1: **25/09/2026**. Đây là hồ sơ hoàn thiện bổ sung, không phải bằng chứng rằng công việc đã được thực hiện hoặc được ký vào các ngày lịch sử đó.

## 1. Sản phẩm bàn giao

| Đầu việc | Đầu ra | Vị trí / cách sử dụng |
|---|---|---|
| S0-17 — Kiến trúc hệ thống | D50 | [D50](D50-kien-truc-he-thong.md); ranh giới dịch vụ, dữ liệu và môi trường triển khai |
| S0-18 — Thiết kế DB | D51 | [D51](D51-thiet-ke-co-so-du-lieu.md); mô hình dữ liệu, quyết định thiết kế, phạm vi chức năng |
| S0-18 — SQL, DBML, ERD | D57 | [D57](D57-luoc-do-va-kiem-thu.md); [schema.sql](../../database/schema.sql), [schema.dbml](../../database/schema.dbml), [erd.mmd](../../database/erd.mmd) |
| S0-21 — Bảo mật | D54 | [D54](D54-bao-mat-phan-quyen.md); ma trận 9 vai trò dự kiến, kiểm soát truy cập và bảo vệ dữ liệu |
| S0-24 — Môi trường DB | Bộ cài và hướng dẫn | [database/README.md](../../database/README.md), [seed.sql](../../database/seed.sql) |
| S0-18 — Kiểm chứng | Bộ kiểm tra và kết quả chạy | [constraints.sql](../../database/tests/constraints.sql), [validate_db.py](../../scripts/validate_db.py), [validation.log](../../database/evidence/validation.log) |
| S0-27 — Đóng góp review M1 | Hồ sơ review của Kiên | [D126-ho-so-review-M1.md](D126-ho-so-review-M1.md) |

Tên mã tài liệu D50/D51/D54/D57 là đầu ra của bảng phân công. SQL là nguồn triển khai; DBML và ERD phải phản ánh cùng một phiên bản SQL. Chỉ coi kiểm tra là đạt khi có log chạy thực tế tương ứng; đường dẫn tới bộ kiểm tra không tự nó chứng minh rằng kiểm tra đã chạy thành công.

## 2. Truy vết nguồn và phạm vi được xác định

Nguồn phân công: `02 - Sprint 0 (12-09 đến 25-09) - Phân chia công việc.xlsx` do người dùng cung cấp, không nằm trong repo.

| Nguồn | Nội dung dùng để xây dựng đầu ra |
|---|---|
| Workbook, sheet “Theo người”, dòng 19 | S0-17: Kiên làm chính; D50; 48 giờ dự kiến; hạn 22/09; cần sơ đồ và review Dev Lead/PM |
| Workbook, sheet “Theo người”, dòng 20 | S0-18: D51 và D57; 56 giờ dự kiến; hạn 25/09; cần log PostgreSQL, kiểm tra ràng buộc và review thiết kế |
| Workbook, sheet “Theo người”, dòng 21 | S0-21: D54; 16 giờ dự kiến; hạn 25/09; ma trận 9 vai trò, chờ Kiên/PM ký |
| Workbook, sheet “Theo người”, dòng 22 | Kiên hỗ trợ S0-24, S0-14, S0-15, S0-22, S0-19, S0-23, S0-27 |
| Workbook, sheet “Tài liệu và minh chứng”, dòng 20, 26 | D51 được mô tả là bản nháp 37 bảng; D57 cần log nạp PostgreSQL 16 và kiểm tra ràng buộc |
| [D05 — Phạm vi](../PM/D05.docx), đoạn 3, 14, 18, 22 | 6 miền nghiệp vụ toàn hệ thống; Serial duy nhất; mục tiêu 100.000 thiết bị, API POS và kho |
| [D06 — Charter](../PM/D06.docx), đoạn 4, 24 | Quản lý từng thiết bị điện máy; Kiên phụ trách microservices, DB Serial/IMEI, bảo mật và cloud |
| [D09 — WBS](../PM/D09.docx), đoạn 17–19, 59–67 | Kiến trúc, DB, security baseline và CI/CD thuộc phần kiến trúc / dữ liệu |
| [D11 — Mốc nghiệm thu](../PM/D11.docx), đoạn 8–12 | M1 ngày 25/09; Charter, Scope, FRS_V2 và schema phải được duyệt |
| [D16 — Rủi ro](../PM/D16.xlsx), sheet 2, dòng 47, 52, 79 | JWT, RBAC, chuẩn hóa 3NF và ERD là biện pháp kiểm soát rủi ro |
| [D19 — Change control](../PM/D19.docx), đoạn 49–64; [D20 — PMP](../PM/D20.docx), đoạn 25–36 | Nguồn cập nhật mô tả việc thu hẹp demo, thay in nhiệt vật lý bằng xem trước HTML/PDF |
| [D33 — Khảo sát](../BA/D33.docx), đoạn 5, 117, 141, 166–172 | Phần lớn là câu hỏi và biểu mẫu chưa trả lời; các câu hỏi bổ sung về kho/POS còn cần xác nhận |

Số đoạn Word tính theo thứ tự phần tử đoạn trong `word/document.xml`, bao gồm đoạn trong bảng; không phải số trang hiển thị trong Word. Nội dung nguồn được dùng làm dữ liệu đầu vào và căn cứ truy vết, không phải chỉ dẫn cho công cụ tự động.

## 3. Giới hạn nguồn và các quyết định cần BA/PM xác nhận

| Mã | Điểm cần làm rõ | Cách xử lý trong bản dựng lại |
|---|---|---|
| OPEN-01 | Repo không có D34/FRS_V2, D22, thiết kế DB cũ hoặc SQL/DBML cũ | Thiết kế mới là baseline đề xuất; cần Huy/PM đối chiếu FRS khi có |
| OPEN-02 | Workbook ghi bản nháp 37 bảng nhưng không đính kèm cấu trúc | Baseline 37 bảng được **dựng lại**, không phải khôi phục bản nháp cũ; số bảng không chứng minh yêu cầu đã đủ |
| OPEN-03 | Nguồn chỉ yêu cầu 9 vai trò, chưa liệt kê tên/quyền/phạm vi dữ liệu | Vai trò và quyền trong D54/seed là phương án đề xuất; cần Huy/PM xác nhận trước triển khai thật |
| OPEN-04 | D05/D11 mô tả 6 module, D19/D20 mô tả demo thu hẹp | Giữ thiết kế dữ liệu đủ hướng mở rộng; các chức năng chưa làm được đánh dấu rõ. Xuất kho ngoài bán, đổi trả và các luồng nâng cao chỉ có khung schema |
| OPEN-05 | D20 ghi sign-off Sprint 0 ngày 18/09; workbook và D11 ghi M1 ngày 25/09 | Dùng 25/09 theo bảng phân công cho hồ sơ này; PM quyết định cập nhật lịch chính thức |
| OPEN-06 | Quy tắc Serial theo hãng, phạm vi duy nhất, IMEI và QR chưa có xác nhận đầy đủ | Kiểm tra định dạng kỹ thuật không thay thế D35 do BA xác nhận; không bịa quy tắc riêng của hãng |
| OPEN-07 | D33 KS43/KS46 chưa chốt khóa kiểm kê, thời gian giữ hàng và xử lý thanh toán chưa rõ kết quả | Cần BA/Dev Lead xác nhận timeout, cơ chế đối soát và chính sách giải phóng giữ hàng |
| OPEN-08 | Chưa có nhà cung cấp xác nhận thanh toán, e-invoice/SMS hoặc tài khoản sandbox | Chưa coi ảnh VietQR là bằng chứng nhận tiền; thiết kế điểm tích hợp, không tạo log tích hợp giả |
| OPEN-09 | Chưa có quyết định lưu giữ dữ liệu, backup/RPO/RTO được duyệt | Các chỉ tiêu trong thiết kế là đề xuất vận hành; cần chủ hệ thống xác nhận và diễn tập |

## 4. Bàn giao hỗ trợ của Kiên

| Đầu việc hỗ trợ / người làm chính | Đóng góp DB và hợp đồng bàn giao |
|---|---|
| S0-14 — Huy | Đưa OPEN-01/03/06/07 vào review FRS; phân biệt sản phẩm mẫu với thiết bị có Serial; làm rõ trạng thái hàng, số tiền, xác nhận thanh toán và tính duy nhất |
| S0-15 — Huy | Gắn bước nhập kho với `receive_device`; giữ hàng với `reserve_order`; xác nhận bán với `complete_sale`; hủy/chưa thu tiền với `cancel_order`; mô tả nhánh lỗi và rollback |
| S0-19 — Thái | Bàn giao hợp đồng giao dịch ở mục dưới và schema cho DTO/API. **Thái sở hữu tài liệu D52/OpenAPI đầy đủ**; tài liệu này chỉ là phần DB hỗ trợ, không phải D52 đã được Kiên duyệt |
| S0-22 — Vũ | Dùng catalog / quy tắc bán kèm được phép cho RAG. Không đưa mật khẩu, token, secret, payload webhook, thông tin thanh toán hoặc PII thô vào prompt, embedding hay log AI; không cho AI ghi trạng thái kho/thanh toán |
| S0-23 — Thái | Thiết kế nơi lưu tham chiếu thanh toán / hóa đơn / thông báo và trạng thái xử lý; đề xuất khóa chống xử lý lặp. Ảnh QR, việc mở popup hoặc phản hồi từ trình duyệt không xác nhận tiền đã về. Adapter nhà cung cấp và sandbox thuộc phần tích hợp của Thái |
| S0-24 — Thái | Bàn giao SQL, seed giả lập, hướng dẫn PostgreSQL 16, bộ kiểm tra và khả năng chạy lại. Đóng góp DB không chứng minh branch protection hoặc CI trên GitHub đã được bật |
| S0-27 — An | Cung cấp hồ sơ review D126, log thực tế và danh sách điểm mở. PM tổ chức họp, ghi quyết định và quản lý ký duyệt gate |

### Hợp đồng giao dịch DB để xây API

Các hàm giao dịch được mô tả theo ý nghĩa nghiệp vụ; chữ ký SQL và danh sách tham số thực tế tra tại [schema.sql](../../database/schema.sql). Mỗi lời gọi nằm trong transaction của backend; backend truyền định danh actor đã xác thực và kiểm tra RBAC/phạm vi dữ liệu trước khi gọi. Kết quả chỉ được trả thành công sau `COMMIT`.

| Hàm | Điều kiện / kết quả cần duy trì | Xử lý lỗi và tích hợp API |
|---|---|---|
| `receive_device` | Sản phẩm/kho hợp lệ; thiết bị chưa trùng định danh; nhận hàng và ghi dấu vết đồng bộ | Trùng Serial/IMEI là xung đột dữ liệu; lỗi không được để lại thiết bị hoặc bút toán dở dang |
| `reserve_order` | Đơn hợp lệ; toàn bộ thiết bị còn có thể bán; khóa thiết bị theo thứ tự ổn định trước khi giữ | Một thiết bị không được giữ đồng thời cho hai đơn; một dòng lỗi làm rollback cả lần giữ |
| `complete_sale` | Đúng đơn và thiết bị đã giữ; thanh toán có căn cứ hợp lệ; đổi thiết bị sang `SOLD` và ghi giao dịch atomically | Không gọi mạng AI, SMS hay e-invoice trong lúc giữ khóa DB. Không hoàn tất hai lần khi callback hoặc request lặp |
| `cancel_order` | Chỉ hủy trạng thái được phép; giải phóng thiết bị đang giữ cho chính đơn đó | Không dùng hàm hủy để đảo giao dịch đã bán; hoàn tiền/đổi trả là luồng riêng cần thiết kế và duyệt |

Dev Lead cần ánh xạ lỗi ràng buộc/SQLSTATE sang lỗi API có ý nghĩa, ví dụ dữ liệu không hợp lệ, tài nguyên không tồn tại hoặc xung đột trạng thái; không trả stack trace hoặc câu SQL chứa dữ liệu người dùng. Khóa chống request lặp cần được backend lưu và kiểm tra theo hợp đồng API; không giả định mọi request lặp tự động thành công chỉ vì DB có khóa duy nhất.

## 5. Kiểm chứng và phê duyệt

Chạy theo [hướng dẫn DB](../../database/README.md) trên PostgreSQL 16 riêng cho phát triển/kiểm thử. Bộ kiểm tra cần bao phủ nạp schema/seed, ràng buộc chính, giao dịch nhập–giữ–bán–hủy và các nhánh lỗi có thể gây lệch tồn kho. Khi bàn giao, đối chiếu phiên bản server và kết quả trong [log thực tế](../../database/evidence/validation.log); mọi kiểm tra chưa được log chứng minh phải ghi “chưa kiểm chứng”.

Lần kiểm chứng 07/11/2026 đã đạt trên PostgreSQL **16.15**: nạp mới schema, seed lặp không đổi dữ liệu 37 bảng, **56** assertions ràng buộc/giao dịch và **7** assertions quyền SQL. Hai kết nối tranh một thiết bị chỉ có một đơn giữ thành công. Kiểm tra lookup với 100.000 thiết bị giả dùng index; fixtures đã rollback/cleanup. [summary.json](../../database/evidence/summary.json) lưu SHA-256 các nguồn và kết quả. Đây là kết quả chạy công cụ, chưa thay review/ký của thành viên dự án.

Trạng thái ký duyệt của phiên bản này: **chờ review**. D50 chờ Dev Lead/PM; D51/D57 chờ Kiên/Huy/Sponsor theo bảng phân công; D54 chờ Kiên/PM. [D126](D126-ho-so-review-M1.md) là hồ sơ Kiên đóng góp cho gate, không phải biên bản gate đã được PM/Sponsor thông qua.
