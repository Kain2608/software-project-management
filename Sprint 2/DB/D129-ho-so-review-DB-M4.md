# D129 — Đóng góp kỹ thuật DB cho Quality Gate M4

Mốc kế hoạch: **24/10/2026, 15:30–16:30**. Người chuẩn bị phần DB: **Trần Trung Kiên**. Rà soát: 13/11/2026. Biên bản M4 chính thức do **Trịnh Quốc An** chủ trì; Vũ/Thái/Phát cung cấp bằng chứng ứng dụng và tải.

## Phạm vi và minh chứng

| Tiêu chí / đầu việc | Phần DB cung cấp | Giới hạn / bằng chứng còn cần |
|---|---|---|
| S2-06: tồn sau giữ/bán | View thường đọc device theo snapshot; kiểm delta IN_STOCK → RESERVED → SOLD qua [validator](../../scripts/validate_sprint2.py) và [evidence](../../database/evidence/sprint2) | Truy vấn mới READ COMMITTED sau commit thấy dữ liệu mới; cần đo vài giây ở UI/API, không khẳng định 0 ms |
| S2-08: fallback | `get_cross_sell_fallback_rules` lọc candidate còn IN_STOCK theo store, giá từ `list_price AS price` | Heuristic brand/category chưa là luật phụ kiện đã duyệt; engine Java chưa gọi function này |
| AI p95 < 3 giây | DB là nguồn candidate/giá/tồn | Vũ/Phát phải đo LLM/CACHE/RULE_FALLBACK toàn đường; chưa có chứng cứ mới trong DB |
| 50 request đồng thời | Test hai kết nối chứng minh một trường hợp Serial | D79 cần test tải riêng; không suy ra CPU < 15% hoặc không deadlock ở mọi tải |
| Cache và chi phí token | Giữ nguồn giá DB và candidate không có PII | Cross-sell cache ứng dụng 5 phút; bảng `ai_recommendations` chưa là DB cache theo store; không có số liệu giảm 60% token |
| Chống bán trùng / quyền | Khóa hàng, draft selection, ACL và scoped authorization được kiểm riêng | Phát/Thái vẫn phải kiểm E2E, service auth và các đường ghi baseline |

Lần kiểm local **13/11/2026, 23:14:15–23:14:31 (UTC+7), PostgreSQL 16.15** đạt **33 assertions Sprint 2** và regression S0/S1. Trong cả ca commit và rollback với hai kết nối độc lập, tồn `(available,reserved,sold)` đổi **(2,0,0) → (1,1,0) → (1,0,1)** sau commit reserve/sale. Mỗi ca có đúng một PAID order/invoice/SALE movement/RESERVE movement/audit/OrderPaid event; snapshot dữ liệu khớp sau cleanup và payment guard được khôi phục. [Summary](../../database/evidence/sprint2/summary.json), [log](../../database/evidence/sprint2/validation.log) lưu source hash; [rà soát](RA-SOAT-SPRINT2.md) ghi chi tiết PID và failure probes. Kết quả này xác nhận phần DB, chưa chứng minh các mục AI/hiệu năng ứng dụng trong bảng.

Định nghĩa `available_stock` là IN_STOCK, gồm thiết bị mới chọn trong DRAFT trước reserve; `total_physical_stock` hiện đếm bản ghi kể cả SOLD, không phải số hàng vật lý tại kho. Chi tiết [DS2](DS2-thiet-ke-va-ban-giao-DB.md) và [biên bản rà soát](RA-SOAT-SPRINT2.md).

## Điều kiện review M4

- Đối chiếu log/summary đúng mã nguồn cho SQL, quyền, concurrency và tồn; bổ sung PR/merge.
- Vũ/Huy thống nhất luật tương thích, TTL và invalidation; Thái nối candidate DB và kiểm lại giá/tồn khi thêm giỏ.
- Phát cung cấp D73/D76/D79 có luồng timeout/cache lỗi, p95 và 50 request; Thái cung cấp demo tích hợp POS.
- An tổ chức ký qua/không qua M4; Huy ký nghiệm thu từng story theo D123 khi đủ bằng chứng.

Bản cũ ghi số benchmark, tiết kiệm token, mức CPU và toàn bộ tiêu chí ĐẠT như kết quả thực tế. Các phát biểu này không có evidence tương ứng nên đã được gỡ. Hồ sơ hiện chưa xác nhận M4 đã qua hoặc Sprint 3 đã đủ DoR.
