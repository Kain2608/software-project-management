# DS2 — Thiết kế và bàn giao DB Sprint 2

Phụ trách: **Trần Trung Kiên — Architect / DBA**. Kế hoạch 11/10–24/10/2026; rà soát 13/11/2026. Phần DB cho S2-03, S2-06 và S2-12; hỗ trợ S2-08/S2-15/S2-14. Đây là đóng góp kỹ thuật cho DS2, chưa phải nghiệm thu toàn ứng dụng.

## 1. Baseline và căn cứ

Kế thừa [schema Sprint 0](../../database/schema.sql) gồm 37 bảng và [V001/V002 Sprint 1](../../database/migrations) nâng lên 39 bảng nghiệp vụ. Sprint 2 bổ sung view, index, function và quyền gọi; không đổi tên cột `products.list_price`, không sửa các migration đã ghi checksum. Ledger `sasms_meta.schema_migrations` thuộc runner Sprint 1; file SQL Sprint 2 hiện là extension áp dụng riêng, chưa nằm trong ledger đó.

Hợp đồng quyền theo [DS1](../../Sprint%201/DB/DS1-thiet-ke-va-ban-giao-DB.md) và [D54](../../Sprint%200/DB/D54-bao-mat-phan-quyen.md): backend xác thực token, ánh xạ `users.id`, truyền actor tin cậy; SQL kiểm actor active, permission và scope. `sasms_app` là group runtime của backend, không cấp cho trình duyệt và không thay RLS.

## 2. S2-03 — Chọn và giải phóng Serial

Nguồn thực thi: [realtime_stock_and_locking.sql](../../database/sprint2/realtime_stock_and_locking.sql). Client không được truyền actor tùy ý, ghi trạng thái thiết bị hoặc tự đặt giá bán.

| Hàm | Hợp đồng |
|---|---|
| `find_and_lock_device_for_pos(store uuid, order uuid, item uuid, serial text, actor uuid) → uuid` | Yêu cầu `sales.checkout` tại cửa hàng; đơn DRAFT đúng store/kho, item đúng đơn/sản phẩm; Serial trim/uppercase; khóa hàng thiết bị và gắn lựa chọn active |
| `release_device_lock(order uuid, device uuid, actor uuid) → boolean` | Kiểm `sales.checkout` theo **store của đơn đọc từ DB**; đơn phải DRAFT; trả true khi bỏ lựa chọn active, false khi không có lựa chọn active; không đổi trực tiếp trạng thái RESERVED/SOLD |
| `reserve_order(order uuid, actor uuid) → numeric` | Hàm baseline kiểm tập device active/số lượng và chuyển RESERVED; backend vẫn phải kiểm quyền, scope và đường gọi |
| `complete_sale(order uuid, actor uuid) → uuid` | Hàm baseline yêu cầu thanh toán hợp lệ, chuyển SOLD, ghi movement/audit/outbox và invoice nội bộ DRAFT trong transaction |

Khóa hàng thiết bị dùng `FOR UPDATE NOWAIT`: khi phiên thứ nhất chưa kết thúc transaction, phiên thứ hai bị `55P03`. Sau commit, lựa chọn active của đơn DRAFT còn hạn 15 phút tính từ `sales_orders.created_at` tiếp tục chặn đơn khác. Đây là thời hạn từ lúc tạo đơn, không phải 15 phút kể từ lần quét cuối; chọn vào đơn đã hết hạn bị `23514`. Không có worker tự hủy đơn/thanh toán. Sau rollback của phiên giữ, phiên khác có thể thử lại.

`SELECT FOR UPDATE` chỉ bảo vệ trong transaction hiện tại; liên kết `order_devices` bảo vệ lựa chọn sau commit. Không giữ transaction trong khi thu ngân thao tác UI hoặc gọi AI/payment qua mạng. Mọi đường chỉnh giỏ/giữ/hủy phải theo hợp đồng backend; quyền DML và các hàm POS baseline còn yêu cầu kiểm quyền ở service. Hai function Sprint 2 chưa harden mọi đường ghi của toàn hệ thống.

| SQLSTATE | Ý nghĩa tích hợp |
|---|---|
| `42501` | Actor thiếu quyền/inactive/sai scope hoặc runtime chưa được cấp EXECUTE; phân biệt lỗi người dùng với lỗi cấu hình server |
| `P0002` | Không tìm thấy đơn/item/device phù hợp |
| `23514` | Sai trạng thái, quan hệ, đơn hết hạn chọn hoặc thiết bị không còn bán được |
| `55P03` | Hàng đang bị khóa hoặc Serial thuộc giỏ nháp khác còn hạn; API trả xung đột có ý nghĩa |

Backend dùng bind parameter và trả kết quả sau COMMIT. Nếu mất phản hồi lúc commit, đối soát đơn/lựa chọn theo scope trước retry. Mã HTTP/envelope phải được Thái chốt trong D52; SQLSTATE không phải endpoint HTTP đã triển khai.

## 3. S2-06 — Tồn kho và nguồn giá

View `sasms.v_realtime_inventory` có một hàng cho **sản phẩm active × kho thuộc store active**. Schema không có `warehouses.is_active`; chỉ lọc `products.is_active` và `stores.is_active`. Giá vật lý là **`p.list_price`**, chiếu thành **`price`** để giữ hợp đồng đầu ra view/fallback và DTO giá. API phải lọc store/kho được phép; view không tự lọc theo actor.

| Trường | Ý nghĩa theo SQL hiện có |
|---|---|
| `available_stock` | Số device `IN_STOCK`; lựa chọn giỏ DRAFT chưa gọi reserve vẫn được tính ở đây |
| `reserved_stock` | Số device `RESERVED` |
| `sold_stock` | Số device `SOLD` còn mang warehouse ID đó |
| `total_physical_stock` | Tên trường kế thừa, thực tế là tổng **bản ghi device** tại kho, gồm SOLD/các trạng thái khác; không phải lượng hàng vật lý đang nằm trong kho |

Vì vậy `available_stock > 0` không cam kết mọi Serial đang chọn được: function khóa Serial còn kiểm lựa chọn DRAFT và quyền. Sau `reserve_order`, available giảm/reserved tăng; sau `complete_sale`, reserved giảm/sold tăng. Hai kết nối phải kiểm thay đổi đã commit và rollback không làm lộ thay đổi chưa commit.

Đây là view thường, đọc dữ liệu theo snapshot PostgreSQL. Ở READ COMMITTED, truy vấn mới sau commit thấy thay đổi đã commit; transaction dùng snapshot cũ có thể tiếp tục thấy dữ liệu cũ. Không có phép đo “0 ms”, cam kết độ trễ UI hoặc cache ngoài DB từ đặc tính này. Yêu cầu workbook là số tồn đổi trong vài giây sau bán; QR/POS/AI phải đo toàn đường ứng dụng.

Index theo `(warehouse_id, product_id, status)`, `(serial, warehouse_id)` và lựa chọn active hỗ trợ truy vấn. Hiệu năng phụ thuộc dữ liệu/kế hoạch thực thi; các ngưỡng DB 10–15 ms trong bản cũ chưa có bằng chứng và không được ghi như kết quả đạt.

## 4. Hỗ trợ S2-08 — Candidate fallback cho AI

`get_cross_sell_fallback_rules(product uuid, store uuid, limit integer DEFAULT 5)` trả `recommended_product_id`, `sku`, `product_name`, `price`, `available_stock`, `recommendation_reason`. Function lấy giá từ view, lọc sản phẩm khác source và còn IN_STOCK trong store, xếp ưu tiên cùng brand/tồn/giá. Một sản phẩm có thể xuất hiện ở nhiều kho; consumer phải hiểu phạm vi từng hàng, không giả định product ID duy nhất.

Đây là heuristic candidate, chưa có bảng luật tương thích phụ kiện đã được Huy/Vũ phê duyệt. Cùng brand/category không tự chứng minh cáp, sạc hoặc phụ kiện tương thích. Không mô tả kết quả là “chỉ phụ kiện chính hãng phù hợp”. Dịch vụ/gói bảo hành không có device cũng không tự xuất hiện như hàng kho trong view này.

Theo [D55](../../Sprint%200/AI/D55-thiet-ke-ai-prompt-rag-cache-fallback.md), [DS1 AI](../../Sprint%201/AI/DS1-phan-mem-tang-truong-ai-sprint1.md) và `AiCacheManager`, cache cross-sell hiện dùng **5 phút**, timeout mục tiêu **2.500 ms** trước fallback. TTL 24 giờ trong thiết kế dành cho tóm tắt thông số, không áp dụng mặc định cho cross-sell. Bảng `ai_recommendations` không có đầy đủ store/cache key hoặc quyền ghi runtime để trở thành DB cache đang chạy. Không có số liệu chứng minh giảm 60% token.

`AiCrossSellService`/`AiRuleFallbackEngine` hiện chưa gọi function DB Sprint 2. Thái/Vũ cần nối candidate thật, loại inactive/sai scope, kiểm lại giá/tồn khi hiển thị và khi thêm giỏ, dùng luật tương thích được duyệt và đo nguồn AI/CACHE/RULE_FALLBACK. Không đưa PII/secret hoặc payment vào prompt/cache/log.

## 5. CI, quyền thực thi và kiểm chứng

Owner/migrator áp dụng DDL và ACL. Ba function mới và view thu hồi quyền từ `PUBLIC`/`sasms_reader`; `sasms_app` được EXECUTE/SELECT theo allowlist khi group đã được provision. Chạy runtime Sprint 1 trước extension. Quyền EXECUTE không thay kiểm quyền nghiệp vụ; credential app vẫn thuộc backend tin cậy.

[Validator](../../scripts/validate_sprint2.py) dùng `psql` với `ON_ERROR_STOP=1`, kiểm exit code, ACL/authorization và điều phối hai tiến trình/kết nối có backend PID khác nhau. Ca commit phải có một người giữ và người thua `55P03`; ca rollback cho phép người còn lại giữ sau rollback; kiểm đúng liên kết active và delta tồn sau reserve/sale.

Đối chiếu [summary/log Sprint 2](../../database/evidence/sprint2) với lần chạy và source hash tương ứng. SQL một `DO` block chạy tuần tự không chứng minh hai kết nối đồng thời. Regression Sprint 0/1 bảo vệ schema, migration và nhập kho cũ; evidence của commit cũ không xác nhận code mới.

Workflow và trạng thái Staging xem [D22](D22-ke-hoach-moi-truong-va-CICD-sprint2.md). Kết quả local/CI DB và readiness không phải log deploy remote.

## 6. Bàn giao liên nhóm

| Người chủ trì | Phần cần tiếp nhận / xác nhận |
|---|---|
| Thái — Fullstack | D52 endpoint chọn/bỏ Serial/tồn, actor từ JWT, transaction/commit, mapping lỗi; POS hiện mới có `/api/pos/status` với `SKELETON_READY` |
| Vũ — AI | Candidate/giá/tồn DB, luật tương thích, cache 5 phút, fallback thật và log nguồn/latency; chưa đồng nhất heuristic SQL với engine ứng dụng |
| Phát — QA | D72/D73/D76, quyền và hai kết nối, E2E bán/in/QR; D79 cho p95/50 request |
| Huy — BA/PO | AC/DoR, semantics tồn khả dụng/giỏ DRAFT, phạm vi lease, luật phụ kiện và demo nghiệm thu |
| An — PM | Hạ tầng/secret/phát hành S2-12, biên bản D128/D129, CR/token/EVM; không dùng readiness làm deploy thành công |

PR/merge, demo ứng dụng, báo cáo tải, Staging thật và chữ ký thuộc đầu ra cần bổ sung của người sở hữu. Tài liệu này không ký thay họ.
