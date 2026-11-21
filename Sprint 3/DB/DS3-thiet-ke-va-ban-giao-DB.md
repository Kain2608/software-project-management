# DS3 — Thiết kế và bàn giao phần DB

Ngày thực hiện: **21/11/2026**. Kiên phụ trách kiến trúc/DB; Huy chủ trì D41/refinement, Thái chủ trì Web/API, Phát chủ trì QR bảo hành và kiểm thử. Tài liệu là đóng góp DB cho DS3, chưa thay chữ ký nghiệm thu toàn ứng dụng.

## Căn cứ và nâng cấp

Đối chiếu D34/D35 hiện hành, `Sprint 0/BA/D41_BPMN_Online_Giao_Lap_SASMS.docx`, `D42_BPMN_Bao_hanh_Doi_tra_SASMS.docx`, D56 của Fullstack và workbook Sprint 3. D41 yêu cầu PAID trước điều phối, đệm lịch 30 phút; D42 yêu cầu hàng không lắp sau PAID, hàng cần lắp sau COMPLETED, chính sách WAR-12 và không phát SMS trùng khi kích hoạt lại.

V006/V007 thêm cột, index và function trên 41 bảng có sẵn; ledger checksum quản lý migration. Trên dữ liệu lịch sử, `requires_installation=true` là mặc định bảo thủ vì chưa có thông tin SKU. BA/catalog cần xác nhận SKU nào không cần lắp. Dòng đơn lưu snapshot lúc tạo/thay sản phẩm; đổi cấu hình SKU không sửa điều kiện của đơn đã chốt. Các dòng đơn cũ giữ `true`, không tự suy diễn lịch sử.

## Hợp đồng gọi từ backend tin cậy

| Hàm SQL | Điều kiện và kết quả |
|---|---|
| `schedule_delivery(order, technician, start, end, address, actor)` → delivery UUID | Đơn PAID; actor có `logistics.manage` đúng store; technician active, có role TECHNICIAN và quyền tại store; cửa sổ hữu hạn trong tương lai; một lịch hoạt động/hoàn tất mỗi đơn |
| `record_delivery_result(delivery, result, evidence, actor)` → delivery UUID | `COMPLETED` hoặc `FAILED`; actor là technician được giao hoặc có `logistics.manage`; evidence là tham chiếu bắt buộc; gửi lại cùng kết quả trả ID cũ |
| `activate_warranty(device, actor)` → warranty UUID | Device SOLD thuộc đúng đơn PAID; quyền `warranty.manage`; có customer; hàng cần lắp có delivery_items khớp device/order và nhiệm vụ COMPLETED |
| `requeue_failed_sms(event, actor)` | Chỉ FAILED, đúng quyền/store, tối đa ba lần claim; giữ event UUID; UNKNOWN/PROCESSING không tự gửi lại |

`schedule_delivery` khóa header đơn rồi dòng user của technician. Các đơn khác nhau cùng technician được tuần tự hóa, kiểm giao nhau với đệm 30 phút ngay trong transaction. Lịch bắt đầu đúng 30 phút sau khi lịch trước kết thúc được phép. Lịch cũ thiếu giờ kết thúc chặn đặt thêm để người điều phối kiểm tra. Runtime không được ghi trực tiếp bảng delivery/assignment/warranty/outbox.

`COMPLETED` ánh xạ trạng thái vật lý `technician_assignments.COMPLETED` và `deliveries.DELIVERED`; `FAILED` giữ thiết bị SOLD và tiền/đơn PAID. Không hoàn tồn hoặc hoàn tiền ngầm. Việc hủy/đổi lịch và chính sách giao từng phần chưa có API riêng trong phần này.

WAR-12 được kiểm duration 12 tháng và lưu `policy_version`, `activation_basis`. Ngày bắt đầu lấy ngày bán thiết bị hoặc ngày hoàn tất lắp theo múi giờ `Asia/Ho_Chi_Minh`; ngày hết hạn cộng **12 tháng lịch**, gồm xử lý 29/02. Kích hoạt muộn không tự gia hạn. Bảo hành hiện hữu được trả lại cùng ID; trạng thái VOID/EXPIRED không tự tạo bảo hành mới. Khóa order/device và unique index ngăn hai kích hoạt tạo hai bản ghi ACTIVE. Bảo hành, audit và event `WarrantyActivated` được commit nguyên tử.

## Đồng bộ online và POS

Kế thừa V004 và extension Sprint 2: POS giữ 10 phút, ONLINE 30 phút từ lần giữ đầu; quét/reserve không gia hạn. PENDING/CAPTURED bảo vệ giữ chỗ cho tới khi đối soát. Cả Web và POS dùng cùng devices/order_devices và các hàm reserve/checkout, không có kho online riêng. Kiểm lượng thiết bị phải bằng số lượng dòng hàng; chưa hỗ trợ phụ kiện không Serial.

Các function hiện có nhận actor nhân viên từ backend. Khách online không được truyền actor UUID để tự có quyền `sales.checkout`. Fullstack phải kiểm subject/customer ownership ở API, lấy giá từ DB, ánh xạ service actor đã cấp quyền và xác minh callback thanh toán trước khi CAPTURED. Chưa có API khách online hoặc callback provider trong bộ DB này; không coi kiểm SQL là bằng chứng Web đặt hàng E2E.

## SMS và quyền

`DeliveryScheduled` dùng aggregate delivery UUID, `WarrantyActivated` dùng warranty UUID. Outbox lưu ID và template; không lưu số điện thoại, địa chỉ hoặc secret provider trong payload. Worker claim lấy số từ customer, chuẩn hóa số Việt Nam 10 chữ số đầu 0 thành +84; adapter kiểm E.164. Với lịch đã hoàn tất/thất bại hoặc giờ bắt đầu đã qua, claim ghi `SMS_STALE_EVENT` và không gọi provider.

Luồng trạng thái và cấu hình ở [DS3 SMS](DS3-SMS-adapter-va-ban-giao.md). `ACCEPTED` là provider nhận yêu cầu, chưa phải delivery receipt; `MOCK_ACCEPTED` không ghi `published_at`. Claim và finish dùng hai transaction riêng, HTTP ở ngoài transaction. Lỗi SMS không rollback bảo hành. Sau crash giữa gửi và finish, giữ PROCESSING và đối soát để tránh gửi lặp.

Chỉ group `sasms_sms_worker` gọi claim/finish; `sasms_app` không có quyền này. Group là NOLOGIN, cần credential riêng do vận hành cấp ngoài Git. `SECURITY DEFINER` có search_path cố định, PUBLIC EXECUTE bị thu hồi; actor/store được kiểm trong hàm nghiệp vụ. DB tin backend, chưa có RLS xác thực JWT cho toàn bộ bảng.

## Kiểm chứng và bàn giao

[Validator](../../scripts/validate_sprint3.py) dựng regression trước nâng cấp, kiểm checksum/noop, chạy SQL rollback và ba ca hai kết nối: tranh lịch technician, kích hoạt cùng thiết bị, claim cùng event. So snapshot trước/sau và dọn đúng UUID fixture. Fixture concurrency tạo trạng thái PAID bằng owner để cô lập bài toán đồng thời; không chứng minh thanh toán HTTP/provider. [Evidence DB](../../database/evidence/sprint3/summary.json) chứa kết quả và source hash thực tế; [D81](D81-bao-cao-ra-soat-bao-mat.md) chứa kiểm quyền/JWT và các phần còn chờ.

Fullstack cần nối API/Web, QR parser `SASMS:DEVICE:<uuid>` hiện có tới `activate_warranty`, trả trạng thái SMS riêng với bảo hành, và kiểm scope ở các đường đọc. QA cần lưu demo/browser, thử provider sandbox, nhận SMS thật và kiểm lỗi/kết quả chưa rõ. PM/PO đánh giá M5 sau khi đủ bằng chứng này.
