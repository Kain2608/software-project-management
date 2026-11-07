# D53 Đặc tả màn hình mockup Kho QR POS

## 1. Mục đích và phạm vi

Tài liệu này mô tả nội dung cần thể hiện trên mockup Figma của D53. Phạm vi gồm sáu nhóm màn hình: xem tồn kho, nhập kho theo Serial/IMEI, quét QR và tra cứu thiết bị, chọn hàng tại POS, thanh toán, biên nhận và in lại.

Đây là đặc tả để thiết kế và review, chưa phải giao diện đã triển khai. Các quy tắc lấy trực tiếp từ D34, D35 và D40 được dùng làm baseline. Nội dung còn mâu thuẫn với D50, D51 hoặc D54 được gắn nhãn **PENDING BA/ARCHITECT** và không được thể hiện như quyết định đã duyệt.

## 2. Quy ước trạng thái yêu cầu

- **BASELINE:** Có yêu cầu rõ trong D34, D35 hoặc D40.
- **PENDING BA/ARCHITECT:** Có mâu thuẫn hoặc tài liệu kỹ thuật mới dừng ở mức đề xuất.
- Mockup chỉ minh họa hành vi giao diện. Việc kiểm tra quyền, đồng thời, idempotency và transaction vẫn phải do backend thực hiện.

## 3. Ma trận màn hình FR và actor

| Mã | Màn hình | Yêu cầu liên quan | Actor theo D34 | Ghi chú quyền |
| --- | --- | --- | --- | --- |
| A | Inventory Overview | FR-07, BR-02, BR-04 | `SALES_STAFF`, `STORE_MANAGER` | D54 đề xuất thêm các role có `inventory.read`; **PENDING BA/ARCHITECT** về tên role và phạm vi |
| B | Goods Receiving + Serial/IMEI | FR-03, BR-01, BR-04, D35, D40A | `WAREHOUSE_STAFF` | D54 dùng tên `WAREHOUSE` và đề xuất Manager/Admin cũng nhận hàng; **PENDING BA/ARCHITECT** |
| C | QR Scanner + Device Lookup | FR-04, D35 SER-10 | `WAREHOUSE_STAFF`, `CASHIER` | Kết quả phải giới hạn theo kho/cửa hàng được cấp |
| D | POS Product/Serial Selection + Cart | FR-05, BR-02, BR-03, D40B T1-T3 | `CASHIER` | Không cho role chỉ có quyền đọc thực hiện checkout |
| E | Payment + Transaction Status | FR-05, BR-03, BR-04, D40B T4-T7 | `CASHIER` | Quyền tra soát/xử lý PENDING của Manager đang **PENDING BA/ARCHITECT** |
| F | Receipt + Print/Reprint | FR-06, BR-07, D40B T8 | `CASHIER` | Chỉ mở khi đơn đã `PAID` và người dùng có quyền xem/in |

## 4. A Inventory Overview

### 4.1 Mục tiêu

Giúp nhân viên xem tồn của SKU theo siêu thị/kho được cấp quyền, phân biệt số lượng có thể bán và số lượng đang giữ, đồng thời biết thời điểm dữ liệu được cập nhật.

### 4.2 Trường hiển thị

- Siêu thị và kho đang xem.
- Bộ lọc: từ khóa SKU/tên sản phẩm, kho, trạng thái.
- SKU, tên sản phẩm, hãng/model nếu dữ liệu có sẵn.
- Số lượng `AVAILABLE`, `RESERVED` và tồn sổ `AVAILABLE + RESERVED` theo D34.
- Thời điểm cập nhật, hiển thị theo giờ Việt Nam.
- **PENDING BA/ARCHITECT:** nhãn `AVAILABLE` hay `IN_STOCK` trên UI.

Không đưa giá trị `SOLD`, `RETURN_INSPECTION` hoặc `QUARANTINED` vào tồn có thể bán.

### 4.3 Thao tác và validation

- Áp dụng/xóa bộ lọc; đổi kho trong phạm vi được cấp.
- Mở luồng quét QR để tìm thiết bị cụ thể.
- Không cho chọn kho ngoài phạm vi. Không hiển thị dữ liệu cũ như kết quả mới khi lần tải gần nhất lỗi.

Thông báo chính:

- Không có kết quả: `Không tìm thấy sản phẩm phù hợp với bộ lọc.`
- Ngoài phạm vi: `Bạn không có quyền xem kho này.`
- Lỗi tải: `Không tải được dữ liệu tồn kho. Dữ liệu trước đó không được coi là dữ liệu mới.`

### 4.4 Trạng thái và layout

- `Loading`: skeleton cho bộ lọc và bảng; không hiện số tồn giả.
- `Empty`: giải thích bộ lọc không có kết quả, có nút xóa bộ lọc.
- `Forbidden`: không hiển thị số liệu kho.
- `Error`: hiện lỗi và nút thử lại; nếu còn dữ liệu cũ phải gắn rõ thời điểm và trạng thái cũ.
- Desktop: thanh lọc phía trên, bảng tồn bên dưới, cột số lượng căn phải.
- Mobile: bộ lọc thu gọn; mỗi SKU thành card, ba số tồn vẫn nhìn thấy không cần cuộn ngang.

### 4.5 Điều kiện PO review

- Phân biệt rõ tồn có thể bán, đang giữ và tồn sổ.
- Có scope kho, thời điểm cập nhật và đủ bốn trạng thái loading/empty/forbidden/error.
- Không dùng nhãn trạng thái đang chờ quyết định như quy tắc chính thức.

## 5. B Goods Receiving và Serial IMEI

### 5.1 Mục tiêu

Cho phép thủ kho tạo phiếu nháp, thêm từng thiết bị theo SKU và các mã định danh, sửa lỗi rồi xác nhận một lần. Xác nhận hợp lệ tạo phiếu, thiết bị và tăng tồn nguyên tử; lỗi phải giữ bản nháp để sửa.

### 5.2 Trường hiển thị

- Kho nhận, nguồn hàng và tham chiếu PO nếu luồng sử dụng PO.
- Mã phiếu nháp, trạng thái phiếu, người tạo.
- SKU, loại/profile sản phẩm, cờ `requires_imei`.
- Mỗi thiết bị: Serial, IMEI 1, IMEI 2 nếu áp dụng, giá trị raw và giá trị sau trim để người dùng rà soát.
- Danh sách thiết bị chờ nhập, số dòng hợp lệ/lỗi và lỗi theo từng trường.
- Idempotency/request key chỉ là thông tin kỹ thuật ẩn; UI chỉ cần bảo đảm nút xác nhận không gửi lặp và thông báo kết quả chưa rõ.

### 5.3 Validation và thông báo

Áp dụng D35:

- `ERR_REQUIRED`: thiếu Serial; thiếu IMEI chính khi SKU yêu cầu IMEI.
- `ERR_FORMAT`: ký tự điều khiển; Sony TV profile không đúng 7 chữ số; IMEI không đúng 15 chữ số.
- `ERR_LENGTH`: Serial sau trim dài hơn 128 ký tự.
- `ERR_DUPLICATE`: trùng trong phiếu hoặc toàn hệ thống; toàn bộ lần xác nhận bị chặn.
- `ERR_CHECKSUM`: IMEI không đạt Luhn.
- `ERR_FORBIDDEN`: không có quyền nhập kho hoặc sửa mã đã có giao dịch.

Phải giữ số 0 đầu, phân biệt hoa/thường và không tự xóa dấu gạch/khoảng trắng bên trong. Hiển thị cảnh báo khi raw khác giá trị sau trim nhưng không tự đổi mã sang số.

### 5.4 Thao tác

- Chọn kho/nguồn, chọn SKU, nhập hoặc quét mã, thêm dòng, sửa/xóa dòng nháp.
- Kiểm tra phiếu; đặt focus vào trường lỗi đầu tiên.
- Xác nhận phiếu; khi đang xử lý phải khóa thao tác gửi lặp.
- Khi kết quả chưa rõ, giữ nguyên draft và cho thử lại với cùng yêu cầu.
- Sau thành công, hiển thị mã phiếu, số thiết bị và tùy chọn đi tới Inventory Overview hoặc QR Lookup.

### 5.5 Trạng thái và layout

- `Loading`: tải kho/SKU/PO; không xóa dữ liệu người dùng đang nhập.
- `Empty draft`: hướng dẫn thêm thiết bị đầu tiên.
- `Validation error`: lỗi ở đầu phiếu và ngay tại trường/dòng tương ứng.
- `Forbidden`: không cho mở form nhập.
- `Submit error`: giữ nguyên phiếu nháp, không báo tăng tồn.
- `Success`: phiếu đã xác nhận, các trường chuyển sang chỉ đọc.
- Desktop: thông tin phiếu bên trái hoặc phía trên; danh sách thiết bị dạng bảng; khu tổng kết/xác nhận cố định bên phải hoặc cuối trang.
- Mobile: form thêm mã trước, danh sách dòng dạng card; nút xác nhận không che lỗi.

### 5.6 PENDING BA ARCHITECT

- Nhập nguyên phiếu hay cho phép nhận từng phần.
- Mô hình IMEI 1/2 trong PostgreSQL và API.
- Trạng thái thiết bị sau nhập là `AVAILABLE` hay `IN_STOCK`.
- Có dùng trạng thái chất lượng `GOOD/DEFECTIVE` hay không.

### 5.7 Điều kiện PO review

- Có đủ draft, lỗi từng dòng, lỗi trùng toàn phiếu, đang xác nhận, kết quả chưa rõ và thành công.
- Thể hiện rõ một thiết bị có thể có Serial + IMEI1 + IMEI2, nhưng gắn nhãn pending nếu mô hình chưa chốt.
- Không trình bày nhập một phần hoặc trạng thái chất lượng như quyết định đã duyệt.

## 6. C QR Scanner và Device Lookup

### 6.1 Mục tiêu

Đọc QR do hệ thống cấp, kiểm tra cấu trúc an toàn, tra cứu thiết bị trong phạm vi quyền và hiển thị SKU, mã thiết bị, trạng thái. Quét đơn thuần không thay đổi tồn kho.

### 6.2 Trường và thao tác

- Khung camera, trạng thái quyền camera, nút bật/dừng camera.
- Nhập tay `device_id` hoặc token nội bộ khi camera không dùng được.
- Kết quả: SKU, device ID, Serial/IMEI được phép hiển thị, kho và trạng thái.
- Nút quét lại; nút quay về màn hình gọi luồng.
- Không tự mở URL trong nội dung QR, không hiển thị dữ liệu khách hàng.

### 6.3 Validation và thông báo

- Từ chối quyền camera: `Không có quyền dùng camera. Bạn có thể nhập mã thiết bị thủ công.`
- Trình duyệt không hỗ trợ/không có secure context: hướng dẫn dùng nhập tay.
- Sai cấu trúc: `Mã QR không đúng định dạng SASMS.`
- Không tồn tại: `Không tìm thấy thiết bị tương ứng.`
- Ngoài phạm vi: `Bạn không có quyền xem thiết bị này.`
- QR giống URL: hiển thị dưới dạng text và không điều hướng.
- **PENDING BA/ARCHITECT:** chuỗi QR chính thức dùng `device_id`, token hay `SASMS:DEVICE:<token>`.

### 6.4 Trạng thái và layout

- `Requesting permission`, `Scanning`, `Decoded/loading lookup`, `Found`, `Invalid`, `Not found`, `Forbidden`, `Camera error`.
- Desktop: camera và kết quả đặt cạnh nhau; nhập tay dưới camera.
- Mobile: camera toàn chiều rộng, thao tác chính nằm trong vùng ngón tay; kết quả xuất hiện dưới khung quét.

### 6.5 Điều kiện PO review

- Có đường thoát khi từ chối camera.
- Không tự mở URL và không làm thay đổi tồn.
- Phân biệt rõ sai cấu trúc, không tồn tại và không có quyền.

## 7. D POS Product Serial Selection và Cart

### 7.1 Mục tiêu

Cho thu ngân tìm/quét sản phẩm, chọn đúng thiết bị còn khả dụng trong kho được cấp, giữ thiết bị nguyên tử và đưa vào giỏ mà không bán trùng.

### 7.2 Trường hiển thị

- Cửa hàng, kho và phiên bán hàng hiện tại.
- Tìm kiếm/quét SKU hoặc thiết bị.
- Sản phẩm: SKU, tên, giá hiện hành, số thiết bị có thể bán.
- Thiết bị: device ID, Serial/IMEI, trạng thái khả dụng.
- Giỏ hàng: dòng hàng, thiết bị đã chọn, đơn giá, giảm giá được phép, thành tiền và tổng.
- Trạng thái giữ chỗ và thời gian còn lại chỉ hiển thị sau khi TTL được chốt.

### 7.3 Thao tác và validation

- Tìm/quét, chọn sản phẩm, chọn Serial, thêm/xóa khỏi giỏ.
- Khi thêm, hệ thống phải kiểm tra lại và giữ chỗ nguyên tử.
- Nếu thiết bị vừa được quầy khác giữ: `Thiết bị không còn khả dụng. Vui lòng chọn Serial/IMEI khác.`
- Không cho thêm cùng thiết bị hai lần vào giỏ.
- Không cho sang thanh toán khi giỏ trống, phiên bán hàng chưa mở hoặc khóa giữ không hợp lệ.
- Giá và giảm giá phải được backend kiểm tra lại; UI không tự quyết định quyền giảm giá.

### 7.4 Trạng thái và layout

- `Loading products`, `Empty result`, `No available device`, `Reservation conflict`, `Forbidden`, `Cart ready`, `Reservation uncertain`.
- Desktop: danh sách sản phẩm/thiết bị ở trái; giỏ và tổng tiền cố định ở phải.
- Mobile: sản phẩm, thiết bị và giỏ theo từng bước; thanh tổng tiền cố định nhưng không che thông báo lỗi.

### 7.5 PENDING BA ARCHITECT

- TTL giữ hàng và cách trình bày khi `PENDING`.
- `AVAILABLE` hay `IN_STOCK`.
- Thuế, chiết khấu, làm tròn và quyền áp dụng giảm giá.

### 7.6 Điều kiện PO review

- Thể hiện được chọn đúng một thiết bị vật lý cho mỗi dòng cần Serial.
- Có nhánh tranh chấp hai quầy và không còn hàng.
- Các giá trị tiền hoặc TTL chưa chốt phải có nhãn pending.

## 8. E Payment và Transaction Status

### 8.1 Mục tiêu

Thể hiện riêng trạng thái thanh toán và trạng thái đơn. Chỉ chuyển đơn sang `PAID`, thiết bị sang `SOLD` và ghi xuất kho khi có kết quả thu tiền đáng tin cậy.

### 8.2 Trường và thao tác

- Mã đơn, tổng cần thu, phương thức thanh toán, mã tham chiếu giao dịch nếu có.
- Trạng thái thanh toán: sẵn sàng, đang xử lý, `PAID`, `FAILED`, `PENDING/UNKNOWN`.
- Trạng thái đơn và thiết bị hiển thị riêng với trạng thái thanh toán.
- Tiền mặt: thao tác xác nhận thu của thu ngân theo D40.
- Sandbox/provider: chờ phản hồi tin cậy; không dùng nút `Đã chuyển` hoặc việc hiển thị VietQR làm bằng chứng đã thu tiền.
- Với `PENDING/UNKNOWN`: giữ ngữ cảnh đơn, không cho thanh toán lại mù quáng; cung cấp thao tác kiểm tra lại trạng thái nếu được phê duyệt.

### 8.3 Thông báo

- `Đang xác minh kết quả thanh toán. Không đóng hoặc tạo giao dịch mới cho đơn này.`
- `Thanh toán thất bại. Đơn chưa được ghi nhận PAID.`
- `Thanh toán thành công. Đơn đã PAID và thiết bị đã SOLD.`
- Không hiển thị thành công nếu chỉ tạo QR hoặc gửi yêu cầu thanh toán.

### 8.4 Trạng thái và layout

- `Ready`, `Processing`, `Paid`, `Failed`, `Pending/Unknown`, `Reconcile error`, `Forbidden`.
- Desktop/mobile đều phải giữ mã đơn, tổng tiền và trạng thái trong vùng nhìn chính.
- Mọi nút retry/đối soát phải kèm cảnh báo chống ghi nhận lặp.

### 8.5 PENDING BA ARCHITECT

- Phương thức demo: tiền mặt thủ công, sandbox hay provider cụ thể.
- Ai được đối soát/xử lý `PENDING`, thời điểm 15 phút và hành động cho phép.
- Xử lý tiền thiếu/thừa, webhook, chữ ký và hoàn tiền.

### 8.6 Điều kiện PO review

- PO phân biệt được `Processing`, `PAID`, `FAILED` và `PENDING/UNKNOWN`.
- Màn hình không đồng nhất việc hiển thị QR với thanh toán thành công.
- Nhánh lỗi không giải phóng giữ chỗ khi kết quả thanh toán chưa rõ.

## 9. F Receipt và Print Reprint

### 9.1 Mục tiêu

Cho thu ngân xem trước và in chứng từ bán hàng trong ứng dụng sau khi đơn đã `PAID`. Lỗi in không hủy đơn; in lại không tạo đơn hoặc xuất kho thêm.

### 9.2 Trường và thao tác

- Mã đơn, cửa hàng, thời điểm, thu ngân.
- Dòng hàng: SKU/tên, Serial/IMEI, đơn giá, giảm giá/thuế nếu đã được chốt, thành tiền.
- Tổng tiền và trạng thái `PAID`.
- QR tra cứu đơn theo cấu trúc sẽ được phê duyệt.
- Xem trước, in, in lại; bản in lại có dấu `BẢN IN LẠI`.
- Trạng thái in và số lần/thời điểm in nếu dữ liệu được cung cấp.

### 9.3 Trạng thái và thông báo

- `Loading receipt`, `Ready to print`, `Printing`, `Print success`, `Print failed`, `Reprint`.
- Lỗi in: `In không thành công. Đơn vẫn PAID; bạn có thể thử in lại.`
- Không có quyền: không hiển thị dữ liệu chứng từ.
- Không có đơn PAID: không cho tạo nội dung in.

### 9.4 Layout

- Desktop: thông tin đơn và bản xem trước đặt cạnh nhau; hành động in nổi bật.
- Mobile: thông tin đơn trước, bản xem trước thu gọn; in/in lại vẫn phân biệt rõ.
- **PENDING BA/ARCHITECT:** khổ in 80 mm hay HTML/PDF và cấu trúc QR tra cứu.

### 9.5 Điều kiện PO review

- Nội dung khớp với đơn và thiết bị đã bán.
- Có frame in lỗi và in lại.
- Ghi rõ đây là biên nhận POS trong ứng dụng, không tự nhận là hóa đơn điện tử đã phát hành.

## 10. Luồng chuyển màn hình

1. Inventory Overview → QR Scanner → kết quả thiết bị → quay lại Inventory Overview.
2. Inventory Overview → Goods Receiving → kiểm tra lỗi → xác nhận → kết quả nhập → Inventory Overview hoặc QR Lookup.
3. POS Selection → chọn Serial/giữ chỗ → Cart → Payment.
4. Payment `PAID` → Receipt Preview → Print/Reprint.
5. Payment `FAILED` → quay lại Payment hoặc hủy theo quyền; Payment `PENDING/UNKNOWN` ở lại màn hình trạng thái, không mở đơn mới cho cùng giao dịch.

Prototype nối Kho tới POS chỉ minh họa vòng đời cùng một sản phẩm/thiết bị. Nó không hàm ý một người dùng được tự chuyển role hoặc bỏ qua kiểm tra quyền.

## 11. Điều kiện hoàn thành D53 để PO review

- Có đủ sáu nhóm màn hình A-F và frame desktop/mobile chính.
- Có loading, empty, forbidden và error cho từng nơi phù hợp; có PENDING/UNKNOWN riêng cho thanh toán.
- Các frame nhập kho thể hiện đủ lỗi D35 và giữ bản nháp khi lỗi.
- Các frame POS thể hiện tranh chấp thiết bị, chống gửi lặp và tách thanh toán khỏi in.
- Tất cả nội dung thuộc decision register mang nhãn **PENDING BA/ARCHITECT**.
- Mỗi frame có mã, tên, FR liên quan, actor, phiên bản và ô trạng thái PO review.
- Có prototype cho các luồng tại mục 10.
- Có vị trí lưu link Figma và bằng chứng PO xác nhận từng màn hình; không ghi `APPROVED` khi chưa có bằng chứng.

## 12. Tài liệu đối chiếu

- D34 Đặc tả yêu cầu SASMS: FR-03 đến FR-07, BR-01 đến BR-04, BR-07, BR-11 và NFR-07.
- D35 Quy tắc Serial và IMEI: SER-01 đến SER-10 và SER-T01 đến SER-T16.
- D40 Quy trình nghiệp vụ: D40A nhập kho và D40B bán hàng POS.
- D50 Kiến trúc hệ thống: ranh giới Web/API, transaction và tích hợp thanh toán/in.
- D51 Thiết kế cơ sở dữ liệu: 37 bảng, trạng thái thiết bị và các giả định DB-A01 đến DB-A09.
- D54 Thiết kế bảo mật và phân quyền: ma trận role/permission đang chờ review.
