# DS2 — Thiết kế và bàn giao DB Sprint 2

Trần Trung Kiên — Architect / DBA, rà soát tài liệu **20/11/2026** trên nguồn `main` tại `6544a00`; SQL/evidence hiện có được kiểm ngày **17/11/2026**. Phạm vi S2-03/S2-06, hỗ trợ S2-08/S2-12 theo [phân công](inputs/phan-cong-sprint-2.csv); không ký thay chủ trì DS2/BA/PM/QA.

## Căn cứ liên nhóm

| Chủ trì / nguồn | Hợp đồng áp dụng cho DB |
|---|---|
| Huy — [D34 FRS](../../Sprint%200/BA/D34_FRS_V2_SASMS.docx), [D35](../../Sprint%200/BA/D35_Quy_tac_Serial_IMEI_SASMS.docx) | Chín vai trò, Serial raw/case/128, multi-IMEI/Luhn; POS10m/ONLINE30m và PENDING |
| Thái — [Fullstack S1](../../Sprint%201/Fullstack/TV4/SPRINT1_IMPLEMENTATION_REPORT.md), [S2 mục 19–20](../Fullstack/TV4/SPRINT2_IMPLEMENTATION_REPORT.md), [AI POS](../Fullstack/TV4/S2-09_AI_POS_FRONTEND_INTEGRATION.md) | Đã có order/cash/receipt, QR SVG và hardening payment sau PR #34; báo cáo PostgreSQL + MockMvc không thay browser E2E và các tình huống trong bảng bàn giao dưới đây |
| Vũ — [D55](../../Sprint%200/AI/D55-thiet-ke-ai-prompt-rag-cache-fallback.md), [DS1](../../Sprint%201/AI/DS1-phan-mem-tang-truong-ai-sprint1.md), [D52](../AI/D52-hop-dong-tich-hop-ai-pos-sprint2.md) | Product/giá/tồn thật, không PII; cache cross-sell5m, kiểm kho, không suy tương thích phụ kiện |
| Phát — [D71](../../Sprint%201/Tester/D71_Bo_Test_Case_Kho_QR_SASMS_v1.2.docx), [D75](../../Sprint%201/Tester/D75_Bao_cao_kiem_thu_Sprint_1_SASMS.docx), [D76](../Tester/D76_Bao_Cao_Kiem_Thu_Sprint_2.docx), [D79](../Tester/D79_Bao_Cao_Kiem_Thu_Hieu_Nang_Va_Tai_AI.docx), [D80 Sprint 1–2](../Tester/D80_So_loi_Bug_Log_SASMS_Sprint_1_2.docx) | Unique/checksum/rollback/concurrency; retest đúng SHA và timestamp thực tế, không lấy test DB đóng bug hoặc thay QA/load |
| An — D21/D22, workbook, [D122 dự thảo](../../Sprint%201/BA/D122_Sprint_Review_Retrospective_1_SASMS_DU_THAO.docx) | Tách kiểm kỹ thuật khỏi PR/merge, Staging, baseline và nghiệm thu |

## Mô hình và nâng cấp

Giữ nguyên S0 37 bảng/V001-V002 39 bảng. V003 thêm device_identifiers/product_identifier_profiles; V004 thêm deadline và guard vòng đời; V005 đồng bộ role BA. Hiện hành **41 bảng, 5 migration**; [DBML](../../database/sprint2/schema.dbml), [ERD](../../database/sprint2/erd.mmd), [từ điển](../../database/sprint2/data-dictionary.md) sinh từ catalog thật. Xem [runbook](../../database/sprint2/README.md), [D35 DB](../../Sprint%201/DB/D35-hop-dong-quy-tac-dinh-danh.md).

Owner/migrator chạy toàn bộ migrations, runtime S1 rồi extension S2; không sửa migration/checksum cũ hoặc nạp lại baseline/seed vào DB đã nâng cấp. `--target-version 2` chỉ phục vụ regression lịch sử, không hạ cấp DB.

[D53 Fullstack](../../Sprint%200/Fullstack/TV4/docs/sprint0/d53/D53_DECISION_REGISTER.md) vẫn chứa quyết định cũ về role, uppercase/100 và một IMEI. DB thực thi D34/D35 bằng V003…V005; các lựa chọn cũ không được dùng làm hợp đồng dữ liệu mới. Chủ sở hữu D53/BA/PM cần cập nhật quyết định thay thế và bằng chứng review; triển khai SQL không tự tạo chữ ký phê duyệt nghiệp vụ.

## S2-03 — Chọn, giữ và thanh toán

find_and_lock_device_for_pos giữ chữ ký store/order/item/serial/actor RETURNS uuid. Tham số định danh hiện chỉ là **Serial**, chưa phải lookup IMEI. Serial chỉ trim, phân biệt hoa/thường. Kiểm active actor/sales.checkout/store/warehouse/product/DRAFT, khóa NOWAIT, đối thủ nhận 55P03.

Lần giữ đầu tạo hold_expires_at theo giờ server: **POS10 phút, ONLINE30 phút**; quét lại/reserve không gia hạn. Cart cũ khi nâng cấp giữ deadline bảo thủ từ created_at vì không biết thời điểm quét gốc. Guard kiểm cả ghi order_devices và reserve; cart A hết hạn không thể chiếm lease B mới. DRAFT chưa đổi tồn trạng thái; RESERVED mới giảm IN_STOCK.

release_device_lock chỉ bỏ lựa chọn DRAFT, kiểm quyền theo store của đơn. reserve_order/complete_sale/cancel_order giữ chữ ký và kiểm quyền trước cả retry; tiền CAPTURED phải bằng tổng, audit/movement/outbox/invoice DRAFT không ghi lặp.

expire_order_reservation(order,actor) hủy/giải phóng DRAFT/RESERVED đã quá deadline, **không có PENDING/CAPTURED**; retry không release lần hai. Worker gọi hàm thuộc ứng dụng/vận hành, chưa có scheduler chỉ từ SQL. Trước khởi tạo payment phải có reservation còn hạn. PENDING không tự release theo TTL/manual cancel; callback đã xác minh có thể chốt sau deadline. Adapter đối soát/quản lý sau15 phút thuộc tích hợp, không suy thất bại theo đồng hồ.

V003 giữ raw/case/IMEI phụ/checksum. imei2 hiện là trường JSON SQL; DTO HTTP cần Fullstack cập nhật riêng. Không đổi hãng DEMO hoặc bịa raw lịch sử.

## S2-06 — Tồn và snapshot

v_realtime_inventory theo SKU active/kho/store active, giá products.list_price AS price. available_stock=IN_STOCK, reserved_stock=RESERVED, sold_stock=SOLD; DRAFT chưa giảm số tồn này. Tồn sổ D34=available+reserved. total_physical_stock là tên tương thích đếm toàn bộ bản ghi kể cả SOLD, không dùng làm tồn sổ/kiểm đếm vật lý.

Snapshot PostgreSQL sau commit không chứng minh UI zero-lag. Fullstack đã có API stock dùng stock_availability, order, cash payment và receipt. Báo cáo TV4 mục 19–20 kiểm chu trình/replay/payment đồng thời bằng PostgreSQL + MockMvc; cần browser E2E và QA độc lập trước khi nghiệm thu người dùng. Index hỗ trợ query không thay benchmark toàn đường QR/POS/AI.

## S2-08 — Fallback và AI

get_cross_sell_fallback_rules giữ sáu cột đầu ra; limit1–10, lọc IN_STOCK, **gộp theo product, tổng tồn store**, tie-break product ID trước LIMIT, không lặp SKU vì nhiều warehouse. Consumer kiểm kho cụ thể theo D52, active/giá/tồn trước hiển thị/thêm giỏ/checkout.

Brand/category chỉ là heuristic, không xác nhận phụ kiện tương thích/chính hãng. Backend kiểm ai.use/store/JWT. Engine Java hiện chưa gọi candidate SQL; phần DB không tự sửa adapter/provider. Cache cross-sell5 phút, không coi ai_recommendations là DB cache có writer hoặc báo token saving khi chưa đo.

## Bàn giao tích hợp còn mở

Đợt chỉnh này chuẩn hóa DB hiện có; chưa mở rộng schema tồn theo số lượng cho phụ kiện/dịch vụ không Serial. Các việc dưới đây cần phối hợp đúng người sở hữu trước khi xác nhận phạm vi tương ứng hoàn tất:

| Hạng mục / chủ trì phối hợp | Hợp đồng DB và hiện trạng cần đối chiếu | Điều kiện đóng |
|---|---|---|
| POS nhiều chiếc cùng SKU — Thái, Phát | `reserve_order` yêu cầu số `order_devices.active` bằng `sales_order_items.quantity`. [PosPage](../../frontend/src/features/pos/PosPage.jsx) hiện chỉ giữ một lock/ô nhập cho mỗi dòng, khóa ô sau lần gán đầu | UI quản lý đủ danh sách thiết bị, gán/giải phóng từng chiếc; browser test quantity ≥2 và SQL đủ thiết bị mới reserve |
| Nhập IMEI phụ — Thái, Kiên, Phát | SQL V003 nhận `imei2`; [InventoryBatchService](../../backend/src/main/java/com/smartelectronicsupermarket/inventory/service/InventoryBatchService.java) chỉ cho `serial`/`imei` | Cập nhật HTTP DTO/validation và kiểm nhập/retry/rollback/unique chéo hai slot; giữ nguyên kiểm Luhn DB |
| Chọn bằng IMEI — Thái, Huy | Hàm khóa hiện tìm cột Serial; UI đang ghi “Serial hoặc IMEI” | Đổi nhãn đúng Serial hoặc chốt/triển khai resolve IMEI qua registry có kiểm product/kho/scope rồi khóa; không nới unique/check để né lỗi |
| Phụ kiện/dịch vụ không Serial — Huy, Vũ, Thái, Kiên | D52 AI quy định `is_serialized=false`, nhưng stock/đơn hàng hiện dựa vào `devices`; mọi dòng reserve vẫn cần đủ thiết bị. Category dạng chữ và cờ AI không thay mô hình tồn hay quyền checkout | BA/Fullstack/AI/DB chốt phạm vi và mô hình tồn/phân bổ/dịch vụ; trước đó không xác nhận luồng checkout không Serial đã được DB hỗ trợ, không tạo Serial giả |
| QR trên hóa đơn — Thái, Huy, Phát | DB cung cấp token theo thiết bị. PR #34 đã thêm [WarrantyQrCode](../../frontend/src/features/qr/WarrantyQrCode.jsx) encode SVG và báo cáo test roundtrip; [ReceiptController](../../backend/src/main/java/com/smartelectronicsupermarket/pos/receipt/controller/ReceiptController.java) vẫn lấy token đầu tiên của hóa đơn | Chốt QR từng thiết bị hoặc QR cấp hóa đơn có lookup đúng phạm vi; kiểm đơn nhiều thiết bị, bản in/camera và độ trễ thực. Test ma trận encode/decode không tự nghiệm thu phần cứng |
| Hết hạn và payment PENDING — Thái, vận hành, Kiên | Có hàm `expire_order_reservation`, chưa có scheduler. DB chặn tự release khi có PENDING/CAPTURED | Worker có actor hợp lệ, retry an toàn và đối soát PENDING; kiểm job tự chạy, deadline và callback thay vì chỉ gọi SQL thủ công |
| Event AI shown/accepted — Vũ, Thái, Kiên, Phát | Log `[AI-AUDIT]` và bảng `ai_recommendations` không chứng minh có event hiển thị/chấp nhận được persist; D80 ghi BUG-D73-01 còn OPEN | Chốt event contract, nơi lưu và retention/quyền/dữ liệu tối thiểu; triển khai và QA retest trước khi dùng event tính BR-10 hoặc đóng bug |

Nguồn QA hiện có khác nhau về ngày: D76 ghi dữ liệu đến 23/10 nhưng dẫn kết quả triển khai tháng 11; phần Sprint 2 D80 ghi 23/11, sau ngày rà soát 20/11/2026. Cần Phát xác nhận ngày thực thi, commit SHA, môi trường và log trước khi sử dụng làm bằng chứng nghiệm thu. Không sửa ngày hoặc đóng bug thay QA từ kết quả DB.

## Quyền, kiểm chứng và nghiệm thu

PUBLIC/reader không gọi hàm ghi mới; app dùng LOGIN thuộc sasms_app, không owner. Registry/profile/audit không ghi trực tiếp. V005 có chín role canonical; alias cũ giữ lịch sử, ADMIN không tự duyệt hoàn tiền.

[Validator](../../scripts/validate_sprint2.py) chạy regression S0/S1 tại V002 trước nâng cấp, retry legacy, checksum/no-op, SQL/ACL/D34/D35 và hai kết nối POS commit/rollback. [Summary](../../database/evidence/sprint2/summary.json)/[log](../../database/evidence/sprint2/validation.log) ghi kết quả thật/hash. Trên DB đã nâng cấp, regression S1 cần DB riêng; không dùng test uppercase V002 đánh giá V003.

Lịch sử Git chứng minh nguồn đã merge qua PR #29 DB (`06423f0`), PR #33 TV4 (`4f5df6f`) và PR #34 hardening POS/QR (`6544a00`); merge không thay review/nghiệm thu. Test DB không thay video/browser POS, HTTP qua mạng thật, D76/D79/load, Staging hoặc chữ ký PO. NFR-01 đo QR p95 <1 giây từ decode thành công đến hiển thị, không gồm lấy nét, 100 lượt Chrome mobile/Wi-Fi ổn định; NFR-02 đo POS p95 <2 giây từ xác nhận nội bộ đến phản hồi PAID/xuất kho, loại thời gian nhập liệu/chờ gateway ngoài; AI p95 <3 giây dưới 50 request đồng thời theo NFR-03. Readiness vẫn NOT_DEPLOYED đến khi có deploy thật và người sở hữu xác nhận.
