# S3-14 — Đầu vào Architect/DB cho refinement Sprint 4

Ngày soạn **21/11/2026**; Kiên hỗ trợ, Huy chủ trì cập nhật D31 và chốt DoR. Nguồn story: `Tiền Sprint/BA/D31_Product_Backlog_SASMS.docx`. Không ghi rằng đã diễn ra họp hoặc Planning Poker mới.

| Story / điểm kế hoạch D31 | Đầu vào DB đã có | Việc cần làm trước khi nhận vào Sprint 4 |
|---|---|---|
| US-12 Lịch sử Serial và đổi trả / 5 Should | Device/order/payment/movement/warranty/audit, dữ liệu trạng thái SOLD | Chốt chính sách trả/đổi, actor duyệt, RETURN_INSPECTION, thiết bị thay thế/chênh lệch; migration và API hoàn tiền riêng; không mở ghi bảng bằng app để vượt quy trình |
| US-13 Dashboard BI / 8 Must | Đơn PAID, movement và AI recommendation | Định nghĩa mẫu số AI, N/A khi không có gợi ý, kỳ báo cáo/timezone, store scope và đối soát nguồn; query plan/benchmark trên tải đại diện |
| US-14 Kiểm chứng E2E / 8 Must | Chuỗi device_id → sale → delivery_items → warranty → outbox; validator DB | Nối Web/QR/SMS; fixture xuyên module; ghi build, người chạy, input/output, nhánh lỗi và provider sandbox; SQL không thay video/browser |
| US-15 Hiệu năng và bảo mật / 5 Must | D81, JWT/quyền và các ca hai kết nối | Tải đại diện QR/POS/AI, p95 theo NFR; SCA/CVE hiện hành, rate limit và TLS/IdP thật; xác minh không còn P1/P2 ở build release |
| US-16 Đào tạo và bàn giao / 3 Must | Runbook migration, backup/restore và worker, source/evidence | Tập huấn 9 role/5 nhóm theo D32; kiểm đếm source/tài liệu/tài khoản qua secret manager, restore drill, đầu mối/hạn xử lý thiếu sót và chữ ký người nhận |

Sáu tiêu chí DoR trong workbook được rà như sau: cấu trúc story và ít nhất ba AC đã có trong D31; INVEST cần Huy/nhóm xác nhận; mockup/API và schema trả hàng/BI chưa chốt; điểm trên là kế hoạch mô phỏng đã có, chưa là biên bản Poker mới; còn phụ thuộc Web/API, provider SMS, Staging và các mục D81. Chưa đủ căn cứ đánh dấu toàn bộ US-12…16 đạt DoR.

Đề xuất người xử lý: Huy chốt chính sách/AC; Thái nối API/UI và callback; Kiên review migration/query/ACL; Phát giữ test và bằng chứng lỗi; An điều phối provider, môi trường và quyết định nhận scope. Hạn thực tế cần nhóm chốt khi lên kế hoạch mới, không hồi tố hạn trong workbook.
