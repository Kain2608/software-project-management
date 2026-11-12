# Sprint 2 Database Artifacts — SASMS

Thư mục chứa các thành phần CSDL được bổ sung và hoàn thiện trong **Sprint 2 (11/10/2026 – 24/10/2026)** do **Trần Trung Kiên (Architect / DBA)** phụ trách:

## 1. Thành phần chính
- `realtime_stock_and_locking.sql`: Định nghĩa View tồn kho real-time (`sasms.v_realtime_inventory`), Stored function khóa chọn Serial cho POS (`sasms.find_and_lock_device_for_pos`, `sasms.release_device_lock`), hàm gợi ý phụ kiện bán kèm dự phòng (`sasms.get_cross_sell_fallback_rules`) và các chỉ mục tối ưu hóa tốc độ phản hồi < 10ms.
- `demo.sql`: Script demo chạy thử các chức năng truy vấn tồn kho real-time và gợi ý cross-sell trong transaction rollback an toàn.

## 2. Kiểm chứng kỹ thuật (Tests)
- `../tests/sprint2/test_sprint2_concurrency_and_stock.sql`: Kịch bản kiểm chứng tự động:
  1. Kiểm tra 2 máy POS đồng thời tranh chấp 1 Serial: đúng 1 máy giữ được, máy thứ hai bị từ chối với mã lỗi `55P03`.
  2. Kiểm tra cập nhật tồn kho tức thì: tồn khả dụng giảm ngay khi giữ hàng (`RESERVED`) và xuất bán (`complete_sale`).
  3. Kiểm tra hàm gợi ý bán kèm fallback phục vụ M4 Quality Gate.

