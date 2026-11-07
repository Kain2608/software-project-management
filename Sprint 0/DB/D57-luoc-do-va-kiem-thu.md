# D57 — Lược đồ SQL, DBML và kiểm chứng

**S0-18 — Trần Trung Kiên · PostgreSQL 16 · thực hiện 07/11/2026 · chờ review.**

## 1. Bộ bàn giao

| Tệp | Vai trò |
|---|---|
| [schema.sql](../../database/schema.sql) | Nguồn thực thi 37 bảng, view tồn, constraints/index, trigger và 4 hàm giao dịch |
| [seed.sql](../../database/seed.sql) | 9 vai trò đề xuất, 17 permission, danh mục và phiếu nhập giả lập; chạy lại không tăng bản ghi |
| [runtime-roles.sql](../../database/runtime-roles.sql) | Provision hai group role NOLOGIN trên cluster thử riêng; không có password |
| [schema.dbml](../../database/schema.dbml) | Mô hình vật lý sinh từ catalog, nhập vào công cụ hỗ trợ DBML để xem |
| [erd.mmd](../../database/erd.mmd) | ERD Mermaid đủ 37 bảng và quan hệ FK |
| [D51 từ điển](D51-tu-dien-du-lieu.md) | Toàn bộ cột, khóa, CHECK và index sinh từ catalog |
| [validate_db.py](../../scripts/validate_db.py) | Nạp SQL, test ràng buộc/luồng, quyền SQL, cạnh tranh và truy vấn quy mô |
| [generate_db_artifacts.py](../../scripts/generate_db_artifacts.py) | Xuất lại DBML/ERD/từ điển; `--check` phát hiện sai lệch so catalog |
| [Hướng dẫn chạy](../../database/README.md) | Thiết lập Dev và lệnh kiểm tra tái lập |

DBML ghi CHECK, partial indexes, NULLS NOT DISTINCT và generated expression trong ghi chú; không đại diện đầy đủ trigger/hàm/quyền. Luôn triển khai từ `schema.sql`, không thay bằng SQL xuất ngược từ DBML.

## 2. Phạm vi kiểm chứng

Các test xác minh SQL thật, không dùng SQLite hoặc mock. Bộ fixtures nghiệp vụ chạy trong transaction và rollback. Kiểm tra cạnh tranh dùng hai phiên kết nối độc lập cùng tranh một thiết bị. Bộ test cần database riêng, role owner và PostgreSQL 16; không chạy trên dữ liệu sản xuất.

Nhóm kiểm tra gồm nạp schema nguyên tử, số bảng/role/permission, seed lặp, PK/FK và FK ghép, giá/số lượng, Serial/IMEI, giới hạn nhận hàng, tính nguyên tử, freeze đơn/tiền, thiếu/thừa tiền, hủy đơn/trả giữ hàng, retry không nhân bản sự kiện, quyền runtime tối thiểu và cạnh tranh giữ hàng. Kiểm tra 100.000 thiết bị và EXPLAIN chỉ đánh giá truy vấn DB trong môi trường thử.

Kết quả và môi trường chạy thực tế nằm trong [log kiểm chứng](../../database/evidence/validation.log). Log là minh chứng kỹ thuật do công cụ tạo; không thay chữ ký review hoặc biên bản M1. CI được cấu hình trong repo; chỉ coi remote CI đạt khi có run thực tế tương ứng trên GitHub.

**Kết quả đã chạy:** ngày 07/11/2026 lúc 11:27 (UTC+7), PostgreSQL **16.15**, database thử mới và cô lập. Nạp schema đạt; seed hai lần giữ nguyên nội dung 37 bảng; **56** assertions ràng buộc/lifecycle và **7** assertions ACL đều đạt. Hai phiên độc lập giữ cùng thiết bị cho đúng một thành công và một lỗi nghiệp vụ `23514`. Checkout của đơn thắng kiểm tra có đúng một SOLD device/PAID order/SALE movement/invoice trong transaction rollback. Dữ liệu fixtures được dọn theo UUID và nội dung 37 bảng khớp snapshot ban đầu.

Lookup Serial, IMEI và QR trên 100.000 thiết bị mô phỏng đều dùng Index Scan; số đo/query plan thực tế nằm trong [summary.json](../../database/evidence/summary.json), cùng hash nguồn kiểm thử. Không suy ra độ trễ camera/API hoặc availability từ những số đo này. `generate_db_artifacts.py --check` cũng khớp catalog PostgreSQL cuối.

[Log parser artifacts](../../database/evidence/artifacts-validation.json) ghi kiểm tra cú pháp bằng `@dbml/core` 11.1.0 và Mermaid 12.3.0: DBML 37 bảng/63 FK, ERD đầy đủ, ba sơ đồ D50 và ERD lõi D51 đều đạt. Quan hệ một đơn có tối đa một hóa đơn đã khớp UNIQUE trong SQL. Log chứa phiên bản công cụ, cách tái lập và hash file nguồn.

## 3. Hợp đồng hàm cho Dev / QA

| Hàm | Điều kiện và kết quả | Retry / lỗi |
|---|---|---|
| `receive_device(receipt_item uuid, serial text, imei text, actor uuid) → uuid` | PO APPROVED, phiếu OPEN, còn định mức; tạo device IN_STOCK, RECEIVE, audit, DeviceReceived | Serial trùng lỗi unique; API cần idempotency theo payload riêng |
| `reserve_order(order uuid, actor uuid) → numeric` | Đơn DRAFT có dòng và đủ thiết bị đúng kho; khóa thiết bị, gắn đơn, tính tổng và chuyển RESERVED | RESERVED/PAID trả tổng cũ; thiết bị đã bị giữ/bán lỗi CHECK |
| `complete_sale(order uuid, actor uuid) → uuid` | RESERVED và CAPTURED bằng đúng tổng; SOLD, PAID, draft receipt, SALE, audit, OrderPaid cùng transaction | PAID trả id cũ; mọi lỗi rollback cả lời gọi |
| `cancel_order(order uuid, actor uuid) → void` | DRAFT/RESERVED chưa CAPTURED; trả thiết bị đang giữ, chuyển CANCELLED, audit/event | CANCELLED không tạo bản ghi mới; PAID hoặc đã có tiền từ chối |

API service dùng parameter binding. Bọc tạo header/dòng/lựa chọn và gọi reserve trong transaction khi cần; không gọi AI/provider ngoài DB trong lúc giữ khóa. Khóa nhiều đơn theo UUID tăng dần nếu một nghiệp vụ tương lai buộc xử lý nhiều đơn. Lỗi SQLSTATE `23505` (unique), `23503` (FK), `23514` (quy tắc/CHECK) cần được ánh xạ thành thông báo nghiệp vụ không lộ SQL/PII; deadlock/serialization failure phải rollback và retry có giới hạn.

Thanh toán CAPTURED chỉ do backend ghi sau xác thực tiền. `confirmed_by` và `captured_at` bắt buộc nhưng không thay thế kiểm chữ ký/provider reference ở API. Các hàm nhận actor tin cậy, không có JWT/session/RBAC backend trong Sprint 0 này.

## 4. Những phần chưa thể nghiệm thu bằng SQL

- FRS_V2 và định dạng từng hãng còn thiếu; 9 business role là phương án đề xuất.
- Chưa có kiểm thử HTTP/E2E, VietQR provider/webhook, AI thực, thiết bị camera hoặc máy in.
- Chưa có worker TTL giữ hàng/outbox, hoàn tiền, điều chuyển kho, duyệt điều chỉnh, định tuyến giao lắp hoặc vòng đời bảo hành đầy đủ.
- Backup/restore và RPO/RTO trong D50 là kế hoạch cần diễn tập; không coi load schema thành công là đã phục hồi backup.
- Review/ký duyệt của Kiên, Huy, Thái, PM/Sponsor và quyết định Quality Gate M1 vẫn chờ người có thẩm quyền.
