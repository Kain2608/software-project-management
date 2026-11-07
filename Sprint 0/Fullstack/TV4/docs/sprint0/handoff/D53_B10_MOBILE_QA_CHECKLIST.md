# D53 B10 Checklist QA mobile nhập kho

## 1. Phạm vi và trạng thái

- Frame: `B10 - Mobile - cần kiểm tra tràn bố cục`.
- Link: `https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=17-31`.
- Mục tiêu: kiểm tra trực quan bố cục mobile của Goods Receiving, không kiểm thử backend.
- Trạng thái ban đầu: **NOT VERIFIED**.
- PO approval: **PENDING**.

Không đánh dấu PASS chỉ từ manifest hoặc ảnh thumbnail. Cần mở đúng node, xem ở 100% và lưu bằng chứng phiên bản được kiểm tra.

## 2. Thiết bị và viewport cần kiểm tra

| Mục | Giá trị kiểm tra | Kết quả | Bằng chứng |
| --- | --- | --- | --- |
| Viewport nhỏ | 360 px chiều rộng | NOT RUN | Chưa có |
| Viewport chuẩn | 390 px chiều rộng | NOT RUN | Chưa có |
| Viewport lớn | 430 px chiều rộng | NOT RUN | Chưa có |
| Chiều cao frame | Bao hết nội dung, không cắt phần cuối | NOT RUN | Chưa có |
| Zoom review | 100% | NOT RUN | Chưa có |

## 3. Checklist bố cục

- [ ] Không có layer tràn ra ngoài chiều rộng frame.
- [ ] Không có cuộn ngang để xem trường, lỗi hoặc nút chính.
- [ ] Header, mã phiếu và trạng thái không chồng nhau.
- [ ] Trường kho/PO/SKU/Serial/IMEI có label đọc được và không bị cắt.
- [ ] Serial, IMEI1 và IMEI2 xuống dòng hợp lý; giữ được số 0 đầu trong dữ liệu ví dụ.
- [ ] Danh sách thiết bị chuyển sang card hoặc bố cục mobile; không ép bảng desktop vào chiều rộng nhỏ.
- [ ] Error summary và lỗi tại trường không chồng lên input hoặc card kế tiếp.
- [ ] Các lỗi required/format/length/duplicate/checksum/forbidden có chỗ hiển thị rõ.
- [ ] Khu tổng kết số dòng hợp lệ/lỗi nhìn thấy trước thao tác xác nhận.
- [ ] Nút thêm thiết bị, sửa/xóa dòng và xác nhận không bị che.
- [ ] Nút xác nhận không che thông báo lỗi cuối trang.
- [ ] Khoảng cách vùng bấm đủ rõ; không có hai thao tác nguy hiểm đặt sát nhau.
- [ ] Loading/submitting không làm thay đổi layout gây nhảy hoặc che dữ liệu.
- [ ] Unknown outcome giữ được nội dung draft và hiển thị cảnh báo retry an toàn.
- [ ] Forbidden state không để lộ dữ liệu phiếu phía sau.
- [ ] Nội dung `PENDING BA/ARCHITECT` không được trình bày như rule đã duyệt.

## 4. Checklist nội dung nghiệp vụ hiển thị

- [ ] Có actor/phạm vi phù hợp Goods Receiving; tên role còn mâu thuẫn phải giữ PENDING.
- [ ] Không thể hiện nhận từng phần là rule đã chốt.
- [ ] Không tự đổi `AVAILABLE/IN_STOCK` khi mapping chưa được duyệt.
- [ ] Không trình bày `GOOD/DEFECTIVE` như baseline khi DEC-10 còn PENDING.
- [ ] Thành công, lỗi và unknown outcome được phân biệt; lỗi không hiển thị tăng tồn thành công.
- [ ] Bản mobile không bỏ mất thông tin có trên frame desktop bắt buộc theo D34/D35/D40.

## 5. Bằng chứng và kết luận QA

| Trường | Giá trị |
| --- | --- |
| Người kiểm tra | Chưa có |
| Ngày/giờ | Chưa có |
| Figma version/last updated | Chưa có |
| Viewport đã kiểm | Chưa có |
| Link ảnh/video/comment | Chưa có |
| Kết quả | NOT VERIFIED |
| Lỗi cần sửa | Chưa có |
| Người xác nhận sửa | Chưa có |

Chỉ đổi kết quả thành `PASS` khi toàn bộ mục bắt buộc đạt hoặc có exception được QA/PO ghi rõ. PO approval được ghi riêng sau QA, không suy ra tự động từ QA PASS.
