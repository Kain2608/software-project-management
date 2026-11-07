# D53 – Mẫu theo dõi xác nhận PO từng màn hình

**File Figma:** https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55

| Nhóm | Số frame | Link review | Trạng thái | Người xác nhận | Ngày | Minh chứng |
|---|---:|---|---|---|---|---|
| A – Tồn kho | 7 | [A01](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=3-2) | **PENDING** | – | – | – |
| B – Nhập kho / Serial | 10 | [B01](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=8-2) | **PENDING** | – | – | – |
| C – Quét QR | 10 | [C01](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=13-2) | **PENDING** | – | – | – |
| D – POS chọn hàng | 9 | [D01](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-2) | **PENDING** | – | – | – |
| E – Thanh toán | 8 | [E01](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-416) | **PENDING** | – | – | – |
| F – Biên nhận | 8 | [F01](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-784) | **PENDING** | – | – | – |

**Hướng dẫn PO:** Với từng nhóm, xem trạng thái default/error/empty/forbidden/pending và mobile; ghi comment trực tiếp đúng frame, quyết định sửa hoặc duyệt kèm ngày/phiên bản. Chỉ thay trạng thái khi có bằng chứng review thật. Không gắn tên PO/ảnh chữ ký giả. Các điểm chưa chốt nghiệp vụ phải dẫn đến `docs/sprint0/d53/D53_DECISION_REGISTER.md` đã có trong worktree.

**Checklist QA Figma:** text dễ đọc ở 100%, không chồng lớp, kích thước mobile, nút không bị che, không giả tạo dữ liệu, thống nhất UI; **đặc biệt kiểm tra B10 – mobile** (có nguy cơ tràn chiều cao vì lệnh chỉnh sửa cuối bị từ chối bởi giới hạn MCP Starter).
