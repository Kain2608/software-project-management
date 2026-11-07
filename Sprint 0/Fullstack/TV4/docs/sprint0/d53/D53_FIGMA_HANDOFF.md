# D53 Hướng dẫn bàn giao mockup Figma

## 1. Mục đích

Tài liệu này tổ chức nội dung cần dựng trong Figma và cách PO review từng frame. Link Figma đã được ghi nhận trong manifest, nhưng nội dung trực tuyến, prototype và chất lượng trực quan chưa được xác minh độc lập. Các tên page/frame dưới đây là kế hoạch truy vết, không phải bằng chứng đã QA hoặc PO duyệt.

## 2. Cấu trúc nội dung và page

Figma Starter đang được ghi nhận có ba page. Các nhóm logic dự kiến ban đầu được gom vào ba page thực tế như sau:

| Page thực tế được ghi nhận | Nhóm nội dung logic |
| --- | --- |
| `01 · Foundations & Traceability` | Cover, nguồn D34/D35/D40, legend BASELINE/PENDING, design tokens và traceability |
| `02 · Inventory & QR` | Inventory Overview, Goods Receiving, QR Scanner/Lookup và các trạng thái liên quan |
| `03 · POS & Prototype` | POS Selection/Cart, Payment, Receipt, prototype và vùng review |

Việc gom page không thay đổi danh sách frame A-F. Manifest là nguồn ghi link node; tài liệu này là nguồn yêu cầu/variant để review độ phủ.

## 3. Danh sách frame cần dựng

### 3.1 Inventory Overview

- `A-01 Desktop Default`
- `A-02 Desktop Filtered`
- `A-03 Loading`
- `A-04 Empty`
- `A-05 Forbidden`
- `A-06 Error With Stale Data Warning`
- `A-07 Mobile Default`

### 3.2 Goods Receiving

- `B-01 Desktop Empty Draft`
- `B-02 Desktop Draft With Devices`
- `B-03 Serial IMEI Validation Errors`
- `B-04 Duplicate Blocks Whole Confirmation`
- `B-05 Confirming`
- `B-06 Unknown Submit Outcome Retry Safe`
- `B-07 Receipt Confirmed`
- `B-08 Forbidden`
- `B-09 Mobile Add Device`
- `B-10 Mobile Draft Review`

### 3.3 QR Scanner và Device Lookup

- `C-01 Request Camera Permission`
- `C-02 Scanning`
- `C-03 Lookup Loading`
- `C-04 Device Found`
- `C-05 Invalid QR`
- `C-06 Device Not Found`
- `C-07 Forbidden Device`
- `C-08 Camera Denied Manual Input`
- `C-09 Camera Error`
- `C-10 Mobile Found`

### 3.4 POS Selection và Cart

- `D-01 Desktop Product Selection`
- `D-02 Serial Selection`
- `D-03 Cart Ready`
- `D-04 No Available Device`
- `D-05 Reservation Conflict`
- `D-06 Reservation Uncertain`
- `D-07 Forbidden Checkout`
- `D-08 Mobile Product To Cart`
- `D-09 Hold Countdown` — **PENDING BA/ARCHITECT**

### 3.5 Payment và Transaction Status

- `E-01 Ready To Pay`
- `E-02 Processing`
- `E-03 Paid`
- `E-04 Failed`
- `E-05 Pending Unknown`
- `E-06 Reconcile Error`
- `E-07 Forbidden`
- `E-08 Mobile Status`

### 3.6 Receipt và Print Reprint

- `F-01 Receipt Preview`
- `F-02 Printing`
- `F-03 Print Success`
- `F-04 Print Failed Order Still Paid`
- `F-05 Reprint Marked Copy`
- `F-06 Forbidden`
- `F-07 Mobile Receipt`
- `F-08 80mm Print Preview` — **PENDING BA/ARCHITECT**

## 4. Design tokens và layout

Các token dưới đây là đề xuất để mockup nhất quán; không thay đổi kiến trúc hay nghiệp vụ.

### 4.1 Token cơ sở

- Spacing: `4, 8, 12, 16, 24, 32 px`.
- Border radius: `4 px` cho input/table, `8 px` cho card/dialog.
- Grid desktop: 12 cột, nội dung tối đa khoảng `1280 px`, gutter `24 px`.
- Grid mobile: 4 cột, lề `16 px`, gutter `12 px`.
- Breakpoint trình bày: desktop `>= 1024 px`; mobile mục tiêu `360-430 px`. Tablet có thể nội suy sau review.
- Typography: một font sans-serif hỗ trợ tiếng Việt; cấp rõ cho page title, section title, body, label và helper/error text.
- Màu trạng thái phải đi kèm text/icon, không dùng màu làm tín hiệu duy nhất.

### 4.2 Component cần có

- App header, module navigation, store/warehouse context.
- Text field, search, select, radio, checkbox, textarea và field error.
- Primary/secondary/destructive button; disabled/loading button.
- Table desktop và card list mobile.
- Status chip cho device/order/payment/print.
- Product card, device/Serial option, cart line và money summary.
- Camera frame, permission prompt, manual input và scan result.
- Loading skeleton, empty state, forbidden state, error state và retry action.
- Confirmation dialog, unsaved draft warning và unknown outcome warning.
- `PENDING BA/ARCHITECT` chip cho trường/quy tắc chưa chốt.

### 4.3 Quy tắc bố cục

- Thao tác chính đặt cuối luồng đọc; không để nút xác nhận che lỗi trên mobile.
- Lỗi validation nằm cạnh trường và có summary ở đầu form khi nhiều dòng lỗi.
- Mã đơn/phiếu, trạng thái và tổng tiền luôn nằm trong vùng nhìn chính ở Payment/Receipt.
- Trên mobile, bảng nhiều cột chuyển thành card; không bắt người dùng cuộn ngang để xem trạng thái hoặc thao tác chính.
- Forbidden state không được render dữ liệu nghiệp vụ phía sau.

## 5. Variant cần thể hiện

| Nhóm | Variant tối thiểu |
| --- | --- |
| Field | default, focus, filled, disabled, read-only, error, helper |
| Button | default, hover/focus, disabled, loading |
| Data view | loading, data, empty, forbidden, error, stale-warning |
| Device | available/in-stock pending naming, reserved, sold, not saleable |
| Receiving row | valid, required, format, length, duplicate, checksum, forbidden |
| Camera | permission, scanning, decoded, denied, unsupported/error |
| Reservation | ready, conflict, uncertain, expired pending decision |
| Payment | ready, processing, paid, failed, pending/unknown, reconcile error |
| Print | ready, printing, success, failed, reprint |
| Responsive | desktop và mobile cho frame chính của A-F |

Các variant liên quan tên trạng thái thiết bị, TTL, thuế/giảm giá, payment provider, QR payload, dual IMEI và quality phải mang nhãn **PENDING BA/ARCHITECT**.

## 6. Prototype từ Kho tới POS

### Flow P1 Tra cứu tồn và QR

`A-01 Inventory Overview` → mở scanner → `C-02 Scanning` → `C-03 Lookup Loading` → `C-04 Device Found` → quay lại `A-01`.

Nhánh phụ: từ chối camera → `C-08 Manual Input`; QR sai → `C-05`; ngoài phạm vi → `C-07`.

### Flow P2 Nhập kho

`B-01 Empty Draft` → thêm thiết bị → `B-02 Draft` → lỗi D35 `B-03/B-04` → sửa → `B-05 Confirming` → `B-07 Confirmed` → `A-01 Overview`.

Nhánh không rõ kết quả: `B-05` → `B-06`; giữ draft và retry an toàn.

### Flow P3 POS và thanh toán

`D-01 Product Selection` → `D-02 Serial Selection` → `D-03 Cart Ready` → `E-01 Ready` → `E-02 Processing`.

- Thành công: `E-03 Paid` → `F-01 Receipt` → `F-02 Printing` → `F-03 Success`.
- Thất bại: `E-04 Failed` → quay lại phương thức thanh toán hoặc hủy theo quyền.
- Chưa rõ: `E-05 Pending/Unknown` → kiểm tra lại/đối soát; không mở Receipt khi chưa `PAID`.
- In lỗi: `F-04 Print Failed` → `F-05 Reprint`; đơn vẫn `PAID`.

### Flow P4 Minh họa vòng đời Kho tới POS

`B-07 Receipt Confirmed` → `A-01 Inventory Overview` → `C-04 Device Found` → `D-02 Serial Selection` → `E-03 Paid` → `F-01 Receipt`.

Flow này chỉ chứng minh tính liên tục của thông tin thiết bị trong mockup. Nó không cho phép một actor tự đổi role hoặc bỏ qua API authorization.

## 7. Ma trận kiểm tra từng frame

Mỗi frame trong Figma cần có metadata hoặc mục tương ứng trong page `07 Prototype & Review`:

| Trường kiểm tra | Nội dung cần ghi |
| --- | --- |
| Frame ID | Mã A-01 đến F-08 |
| Tên màn hình/trạng thái | Tên ngắn, phân biệt default/error/pending |
| Truy vết | FR/BR/SER/D40 step liên quan |
| Actor/permission | Actor theo D34; permission đề xuất ghi pending nếu chưa duyệt |
| Dữ liệu hiển thị | Không chứa secret/PII không cần thiết; dữ liệu ví dụ có nhãn demo |
| Hành động chính/phụ | Có thứ tự, disabled/loading khi cần |
| Validation | Lỗi field và thông báo đúng mã đã nêu |
| State coverage | loading/empty/forbidden/error hoặc lý do không áp dụng |
| Responsive | Desktop/mobile đã kiểm tra |
| Decision dependency | Mã DEC-01 đến DEC-10 hoặc `None` |
| PO review | `NOT REVIEWED`, `CHANGES REQUESTED` hoặc `APPROVED` kèm bằng chứng |

## 8. Checklist PO theo nhóm màn hình

| Nhóm | Câu hỏi xác nhận | Trạng thái ban đầu | Link bằng chứng |
| --- | --- | --- | --- |
| A Inventory Overview | Số tồn, scope và thời điểm cập nhật có dễ hiểu không? | NOT REVIEWED | Chưa có |
| B Goods Receiving | Các bước nhập, lỗi D35 và xác nhận nguyên tử có đúng không? | NOT REVIEWED | Chưa có |
| C QR Lookup | Camera fallback, an toàn QR và kết quả tra cứu có đủ không? | NOT REVIEWED | Chưa có |
| D POS Selection/Cart | Chọn đúng thiết bị, tranh chấp và giỏ hàng có rõ không? | NOT REVIEWED | Chưa có |
| E Payment | Có phân biệt rõ processing/paid/failed/pending không? | NOT REVIEWED | Chưa có |
| F Receipt | Nội dung chứng từ, lỗi in và in lại có đúng không? | NOT REVIEWED | Chưa có |

Không đổi trạng thái sang `APPROVED` nếu chưa có ảnh/comment/biên bản hoặc đường dẫn xác nhận của PO cho đúng nhóm màn hình.

## 9. Vị trí ghi link và bằng chứng

### Link Figma

- File URL: `https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55`
- Trạng thái link: `RECORDED - NOT VISUALLY VERIFIED`
- Prototype URL/trạng thái: `CHƯA XÁC MINH`
- Version/last updated: `CHƯA CÓ BẰNG CHỨNG`
- Owner: `Vũ Duy Thái - TV4`

### Bằng chứng PO

| Màn hình | Người xác nhận | Ngày | Kết quả | Link ảnh comment biên bản |
| --- | --- | --- | --- | --- |
| A Inventory Overview | Chưa có | Chưa có | NOT REVIEWED | Chưa có |
| B Goods Receiving | Chưa có | Chưa có | NOT REVIEWED | Chưa có |
| C QR Scanner/Lookup | Chưa có | Chưa có | NOT REVIEWED | Chưa có |
| D POS Selection/Cart | Chưa có | Chưa có | NOT REVIEWED | Chưa có |
| E Payment/Status | Chưa có | Chưa có | NOT REVIEWED | Chưa có |
| F Receipt/Print | Chưa có | Chưa có | NOT REVIEWED | Chưa có |

## 10. Điều kiện bàn giao

- Đủ frame và variant nêu trong tài liệu này hoặc có ghi lý do thay đổi đã được PO đồng ý.
- Prototype chạy được ba flow P1-P3; P4 dùng để review traceability liên module.
- Mọi frame có actor, truy vết yêu cầu và decision dependency.
- Không còn lỗi chính tả, text tràn, nút bị che hoặc frame mobile phải cuộn ngang cho thao tác chính.
- Các mục chưa chốt vẫn có nhãn **PENDING BA/ARCHITECT**.
- Link Figma và bằng chứng PO được điền bằng dữ liệu thật; không tự ghi đã duyệt.
