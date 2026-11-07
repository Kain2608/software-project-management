# S0-14 / S0-15 — Bàn giao yêu cầu và luồng dữ liệu của Kiên

| Thuộc tính | Nội dung |
|---|---|
| Người đóng góp | Trần Trung Kiên — Architect / DBA |
| Người nhận | Nguyễn Quang Huy — chủ trì FRS/D35/BPMN; Phạm Đại Phát — QA; Vũ Duy Thái — Dev Lead |
| Căn cứ phân công | [Workbook Sprint 0](../inputs/phan-cong-sprint-0.xlsx), sheet “Theo người” dòng 22; sheet “Phân chia công việc” dòng 7–8 |
| Ngày / nhánh | 07/11/2026; `DB` |
| Trạng thái | Đã soạn phần hỗ trợ DB để review; chưa có xác nhận nghiệp vụ hoặc chữ ký |

Tài liệu bổ sung phần bàn giao cụ thể còn thiếu, giữ nguyên [D51](../D51-thiet-ke-co-so-du-lieu.md), [D57](../D57-luoc-do-va-kiem-thu.md) và SQL đã có. D34/FRS_V2 và D40–D42/BPMN chưa có file trong repo để hoàn thiện trực tiếp. Các yêu cầu dưới đây là đề xuất kỹ thuật có truy vết, để Huy đưa vào tài liệu do Huy phụ trách; không đổi trạng thái workbook thành đã duyệt.

## 1. Nội dung review FRS từ phía DB

Mã `DB-REQ-*` chỉ dùng trong bản bàn giao này. Nguồn [D05](../../PM/D05.docx) có nội dung Scope Statement; [D06](../../PM/D06.docx) có nội dung Charter, dù tên mã được mô tả khác ở một số bảng phân công. Số đoạn dưới đây đếm các đoạn trong `word/document.xml`, gồm đoạn trong bảng.

| Mã / căn cứ | Đề xuất yêu cầu để Huy review | Đã có trong DB Sprint 0 | Tiêu chí và quyết định cần xác nhận |
|---|---|---|---|
| DB-REQ-01 — D05 đoạn 3, 14; D06 đoạn 4 | Phân biệt mẫu hàng `products`/SKU với từng chiếc `devices`/Serial. Serial canonical duy nhất toàn chuỗi; IMEI nếu có cũng duy nhất | Serial trim + uppercase, dài 1–100, không khoảng trắng; IMEI NULL hoặc 15 chữ số; QR là UUID riêng; UNIQUE bảo vệ khi nhập đồng thời | Nhập lại Serial hoặc IMEI không NULL phải bị từ chối, không để lại movement/audit/event dở dang. Huy/kho chốt phạm vi uniqueness, máy hai SIM và hàng phụ kiện không có Serial; không tự bịa regex hãng |
| DB-REQ-02 — D05 đoạn 3; D06 đoạn 4 | Mỗi thiết bị nhận đúng sản phẩm, dòng phiếu, PO và kho; không nhận quá định mức | FK ghép; `receive_device` khóa PO/phiếu/dòng; chỉ PO APPROVED và phiếu OPEN | Phiếu hoặc PO hết định mức làm rollback lời gọi. Quy trình tạo/duyệt PO và xử lý hàng thừa/hỏng cần Huy xác nhận; baseline chưa cấp thao tác này cho runtime app |
| DB-REQ-03 — D05 đoạn 3, 22 | Giữ hàng và bán theo thiết bị cụ thể; một chiếc chỉ thuộc một đơn đang giữ | `reserve_order`, `complete_sale`; khóa thiết bị theo UUID tăng dần; freeze dòng giá/thuế/chiết khấu và lựa chọn thiết bị sau giữ | Hai quầy giữ cùng chiếc chỉ một đơn thành công. Backend xác định giá/chiết khấu được phép; DB chỉ kiểm số và đóng băng snapshot. Chốt chính sách sửa đơn sau giữ, thời gian giữ và đơn online |
| DB-REQ-04 — D05 đoạn 3; D33 KS44, KS46 | Hoàn tất bán khi tiền CAPTURED bằng đúng tổng; ảnh QR và PENDING không xác nhận đã nhận tiền | `payments`; khóa đơn khi sửa khoản tiền; khoản CAPTURED không sửa/xóa; `complete_sale` so tổng tiền chính xác | Thiếu/thừa tiền đều từ chối; không hủy đơn đã nhận tiền bằng `cancel_order`. Thu ngân/BA chốt ai xác nhận tiền mặt/chuyển khoản và quy trình tiền chưa rõ kết quả, đối soát, hoàn tiền |
| DB-REQ-05 — D05 đoạn 14, 18, 22 | QR/Serial lookup có index; dữ liệu mục tiêu 100.000 chiếc; mục tiêu scan <1 giây, POS <2 giây, uptime 99,9% | UNIQUE index Serial/IMEI/QR; có thử lookup 100.000 thiết bị giả | Log SQL chỉ chứng minh đường truy vấn DB. QA cần đo toàn luồng camera → API → DB và tải ứng dụng; PM chốt phương pháp đo uptime, tải và môi trường |
| DB-REQ-06 — D06 đoạn 24; workbook S0-21 | Backend xác thực actor, kiểm quyền và cửa hàng trước mọi thao tác | IAM 9 vai trò đề xuất; hai group role DB; runtime không trực tiếp sửa tồn/trạng thái đơn/audit/outbox | Actor từ phiên đã xác thực; không tin actor/store/role trong body. S0 chưa kiểm active/RBAC trong bốn hàm và chưa có RLS; Huy/PM chốt ma trận 9 vai trò trong [D54](../D54-bao-mat-phan-quyen.md) |
| DB-REQ-07 — D05 đoạn 3 | Giữ lịch sử giao lắp, bảo hành/đổi trả để mở rộng | Có bảng quan hệ và trạng thái khung | Có bảng không đồng nghĩa có workflow. Chưa có transaction giao lắp, kích hoạt bảo hành, refund, điều chuyển hoặc duyệt điều chỉnh đủ để vận hành; Huy xác nhận D41/D42 trước phát triển |
| DB-REQ-08 — D05 đoạn 3; D06 đoạn 24 | Mỗi thay đổi kho/bán cốt lõi có actor, audit và sự kiện cùng transaction | `inventory_movements`, `audit_logs`, `outbox_events` được ghi bởi các hàm | Rollback không để lại một phần dữ liệu. App không sửa lịch sử trực tiếp; owner DB vẫn là bên tin cậy. Việc gửi SMS/e-invoice cần worker riêng, không suy ra từ hàng outbox |

[D33 — khảo sát](../../BA/D33.docx) là mẫu câu hỏi chưa có trả lời xác nhận. Các câu KS41–KS46 về payload QR, xuất kho ngoài bán, khóa kiểm kê, xác nhận tiền, model máy in và giữ hàng là đầu vào cần hỏi tiếp; không coi câu hỏi là quy tắc đã chốt. Quy tắc định danh hãng đang chờ nguồn được mô tả tại [bàn giao D35 Sprint 1](../../../Sprint%201/DB/D35-hop-dong-quy-tac-dinh-danh.md).

## 2. Luồng nhập kho để gắn vào D40

Đây là ánh xạ bước nghiệp vụ sang transaction và dữ liệu, không phải sơ đồ BPMN đã được trưởng kho xác nhận. SQL nguồn là [schema.sql](../../../database/schema.sql).

| Bước / người thực hiện dự kiến | Đọc và điều kiện | Ghi / ranh giới transaction | Nhánh lỗi và bàn giao cho BA/Dev |
|---|---|---|---|
| 1. Kho chọn PO, phiếu và dòng nhận | `purchase_orders`, `purchase_order_items`, `goods_receipts`, `goods_receipt_items`, `warehouses`; API lọc cửa hàng được phép | Chọn dữ liệu chưa đổi tồn. Tạo/duyệt PO và mở/đóng phiếu là workflow cần làm riêng; `sasms_app` hiện chưa có quyền ghi các bảng này | Không tìm thấy trong phạm vi: không tiết lộ phiếu ngoài quyền. Không dùng owner DB làm credential app để lấp phần workflow thiếu |
| 2. Nhân viên quét/nhập Serial, IMEI | Backend kiểm UUID, kiểu dữ liệu, canonical Serial và IMEI theo hợp đồng | Chưa gọi provider hay thay đổi tồn. Tạo QR từ token DB sau khi nhập thành công | Không phân tích chuỗi QR tùy ý thành câu SQL. Chuẩn QR `SASMS:DEVICE:<uuid>` là đề xuất cần Huy/Thái chốt |
| 3. Backend nhận một chiếc | Gọi `sasms.receive_device(p_receipt_item uuid, p_serial text, p_imei text, p_actor uuid) RETURNS uuid`; actor tin cậy | Một transaction: khóa PO → phiếu → dòng nhận → dòng PO; kiểm trạng thái/định mức; thêm device IN_STOCK + RECEIVE + audit + DeviceReceived | Trùng Serial/IMEI `23505`; sai trạng thái/định mức `23514`; dòng không tồn tại `P0002`. Rollback cả lần gọi; không trả UUID thành công trước COMMIT |
| 4. Kho nhận phản hồi / tra tồn | Đọc `devices` bằng ID/Serial/QR và `stock_availability` theo kho được phép | Chỉ sau COMMIT trả ID/token; tồn khả dụng chỉ đếm IN_STOCK | Mất kết nối khi commit: đối soát kết quả, không tạo Serial mới để né trùng. S0 chưa lưu payload retry của nhập đơn chiếc |
| 5. Hoàn tất phiếu / xử lý ngoại lệ | So lượng theo dòng/PO và các chiếc đã nhận | Chưa có hàm runtime đóng phiếu, từ chối chất lượng hay bút toán bù trong baseline | Huy mô tả riêng bước kiểm chất lượng, hàng thiếu/thừa, người duyệt; Kiên thiết kế migration sau quy tắc xác nhận |

Sau [V002 Sprint 1](../../../database/migrations/V002__inventory_batches_and_identifiers.sql), backend có thể dùng `receive_device_batch(receipt_item uuid, request_key uuid, devices jsonb, actor uuid) RETURNS uuid[]`: 1–500 chiếc, một lô atomic, cùng key/actor/phiếu/payload canonical có thứ tự trả cùng ID. Hàm `receive_device` vẫn cùng chữ ký nhưng kiểm `inventory.receive` theo user active/cửa hàng và tạo key mới mỗi lần. Hai tính chất này thuộc database đã nâng cấp, không gán ngược cho schema 37 bảng S0.

## 3. Luồng POS để gắn vào D40

| Bước / người thực hiện dự kiến | Đọc và điều kiện | Ghi / transaction | Nhánh lỗi / kết quả |
|---|---|---|---|
| 1. Bán hàng/thu ngân lập giỏ | Catalog active theo chính sách API; kho thuộc cửa hàng; giá/khuyến mại do backend xác định | `sales_orders` dùng allowlist cột, mặc định DRAFT; `sales_order_items` giữ snapshot số lượng/giá/chiết khấu/thuế; `order_devices` gắn đúng product qua FK ghép | Cùng sản phẩm một dòng/đơn; đủ số chiếc active bằng quantity trước khi giữ. Hai giỏ DRAFT có thể chọn cùng chiếc; giỏ nháp chưa sở hữu hàng |
| 2. Backend giữ hàng | `sasms.reserve_order(p_order uuid, p_actor uuid) RETURNS numeric`; DRAFT không rỗng; toàn bộ chiếc IN_STOCK đúng kho | Khóa đơn, khóa chiếc theo UUID; IN_STOCK → RESERVED, gắn `reserved_order_id`; ghi RESERVE/audit/OrderReserved và tổng tính từ các `line_total` | Một chiếc không khả dụng làm rollback toàn lần giữ. RESERVED/PAID gọi lại trả tổng cũ; không thay payload giỏ đã freeze bằng cùng retry |
| 3. Thu ngân/provider xác nhận tiền | Backend kiểm quyền, nguồn xác thực thanh toán và đúng đơn/số tiền | Transaction ghi `payments`; trigger khóa đơn và chỉ cho DRAFT/RESERVED; CAPTURED có `confirmed_by`/`captured_at` | Ảnh VietQR/popup/HTTP từ browser không đủ căn cứ CAPTURED. Không chờ provider trong transaction giữ khóa |
| 4. Backend hoàn tất bán | `sasms.complete_sale(p_order uuid, p_actor uuid) RETURNS uuid`; RESERVED và tổng CAPTURED bằng đúng tổng đơn | Cùng transaction: RESERVED → SOLD, đơn → PAID, SALE + audit + OrderPaid; tạo một invoice nội bộ DRAFT | PAID gọi lại trả cùng order ID, không tạo invoice/event lần hai. Invoice DRAFT chưa phải hóa đơn điện tử đã phát hành |
| 5. Thu ngân xem/in chứng từ | Đọc đơn/invoice đã commit, API lọc/che thông tin khách theo quyền | Hiển thị HTML/PDF do app xây; gửi e-invoice/SMS cần worker/idempotency riêng | Chưa có app hoặc nhà cung cấp được thử trong repo. D19 mô tả hoãn kết nối máy in vật lý; không coi lệnh DB là in thành công |
| Nhánh hủy trước bán | `sasms.cancel_order(p_order uuid, p_actor uuid) RETURNS void`; DRAFT/RESERVED và không khoản CAPTURED | Trả chính những chiếc đang giữ cho đơn về IN_STOCK; RELEASE/audit/OrderCancelled; đơn CANCELLED; giữ lịch sử lựa chọn | CANCELLED gọi lại no-op. PAID hoặc đã CAPTURED bị từ chối; hủy không thực hiện refund. Chưa có worker TTL tự giải phóng giữ hàng |

Tạo header/dòng/lựa chọn và giữ hàng có thể nằm trong cùng transaction để tránh giỏ dở dang. Mỗi phản hồi thành công chờ COMMIT. Nếu bước tiền đã commit riêng mà checkout lỗi, giữ trạng thái tiền để đối soát; không ghi lại tiền hoặc giải phóng thiết bị tùy ý. Giá/tổng tiền trình duyệt gửi không phải giá trị được tin cậy.

## 4. D41 / D42 và các bước cần thiết kế tiếp

| Quy trình Huy chủ trì | Dữ liệu nền hiện có | Phần phải chốt trước khi Kiên mở đường ghi |
|---|---|---|
| Đặt online, giao và lắp | `sales_orders.channel=ONLINE`, `deliveries`, `delivery_items`, `technician_assignments` | Tách thời điểm giữ/thu tiền/giao; người được xem địa chỉ; chống giao trùng; chuyển kho; giao thất bại; lịch và phân công kỹ thuật viên. Chưa có hàm workflow runtime |
| Kích hoạt/tiếp nhận bảo hành | `warranty_policies`, `warranties`, `warranty_claims` | Mốc bắt đầu bảo hành, Serial đủ điều kiện, đơn vị chịu trách nhiệm, quyền tra lịch sử và thay thế máy; chưa có tự động kích hoạt hay SMS |
| Đổi trả / hoàn tiền | `returns`, `return_items`, liên kết đơn và thiết bị | Thời hạn/điều kiện, người đề nghị và duyệt, tình trạng thiết bị, bút toán hoàn tiền/đối soát provider; không cập nhật ngược khoản CAPTURED hoặc dùng `cancel_order` |
| Kiểm kê / xuất ngoài bán | `stocktakes`, `stocktake_items`, enum movement mở rộng | KS42/KS43: khóa hay snapshot trong lúc đếm, người duyệt chênh lệch, lý do và chứng từ xuất; enum hiện có chưa phải transaction đã hiện thực |

## 5. Bộ kiểm tra và đầu vào người sở hữu cần bổ sung

[Evidence S0](../../../database/evidence/summary.json) và [D57](../D57-luoc-do-va-kiem-thu.md) đã có kết quả 56 assertions ràng buộc/lifecycle, 7 ACL, tranh chấp giữ hàng hai phiên và lookup 100.000 thiết bị. Chúng kiểm SQL hiện có; chưa thay test HTTP/BPMN hoặc xác nhận người dùng. Phân biệt [evidence S1](../../../database/evidence/sprint1/summary.json) với S0 khi review nhập lô/RBAC mới.

| Người nhận | Nội dung dùng được ngay | Đầu vào cần cung cấp để đóng tài liệu chính |
|---|---|---|
| Huy | 8 đề xuất yêu cầu và các bảng bước → dữ liệu/transaction ở trên | D34/FRS_V2 thực tế; D35 có nguồn hãng/kho; BPMN D40–D42 và trả lời KS41–KS46 |
| Thái | Bốn chữ ký SQL, trình tự gọi, đường đọc/ghi và nhánh lỗi | Endpoint/DTO được chốt, auth/phạm vi, giá/chiết khấu, provider và cơ chế retry; xem [bàn giao API/AI/tích hợp](S0-19-22-23-hop-dong-API-AI-tich-hop.md) |
| Phát | Các tiêu chí SQL có thể tái lập và lỗi cần thử ở API | Kịch bản E2E trên app/camera/provider thật, gồm mất phản hồi khi commit và thanh toán chưa rõ kết quả |
| An / người duyệt | Danh sách phần kỹ thuật đã có và phần chưa xác nhận | Tổ chức review, quyết định phạm vi và ký hồ sơ; bản này không tự thông qua M1 |
