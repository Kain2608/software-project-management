# DS2 — Thiết kế và bàn giao DB Sprint 2

Trần Trung Kiên — Architect / DBA, cập nhật **17/11/2026**, nhánh DB. Phạm vi S2-03/S2-06, hỗ trợ S2-08/S2-12 theo [phân công](inputs/phan-cong-sprint-2.csv); không ký thay chủ trì DS2/BA/PM/QA.

## Căn cứ liên nhóm

| Chủ trì / nguồn | Hợp đồng áp dụng cho DB |
|---|---|
| Huy — [D34 FRS](../../Sprint%200/BA/D34_FRS_V2_SASMS.docx), [D35](../../Sprint%200/BA/D35_Quy_tac_Serial_IMEI_SASMS.docx) | Chín vai trò, Serial raw/case/128, multi-IMEI/Luhn; POS10m/ONLINE30m và PENDING |
| Thái — [Fullstack S1](../../Sprint%201/Fullstack/TV4/SPRINT1_IMPLEMENTATION_REPORT.md), [S2](../Fullstack/TV4/SPRINT2_IMPLEMENTATION_REPORT.md), [AI POS](../Fullstack/TV4/S2-09_AI_POS_FRONTEND_INTEGRATION.md) | Giữ chữ ký SQL, trusted actor, transaction, mapping SQLSTATE; không tự coi cart/payment/lock REST E2E đã xong |
| Vũ — [D55](../../Sprint%200/AI/D55-thiet-ke-ai-prompt-rag-cache-fallback.md), [DS1](../../Sprint%201/AI/DS1-phan-mem-tang-truong-ai-sprint1.md), [D52](../AI/D52-hop-dong-tich-hop-ai-pos-sprint2.md) | Product/giá/tồn thật, không PII; cache cross-sell5m, kiểm kho, không suy tương thích phụ kiện |
| Phát — [D71](../../Sprint%201/Tester/D71_Bo_Test_Case_Kho_QR_SASMS_v1.2.docx), [D75](../../Sprint%201/Tester/D75_Bao_cao_kiem_thu_Sprint_1_SASMS.docx), [D80](../../Sprint%201/Tester/D80_So_loi_Bug_Log_SASMS.docx) | Unique/checksum/rollback/concurrency; retest thực tế, không lấy test DB đóng bug hoặc thay D76/D79 |
| An — D21/D22, workbook, [D122 dự thảo](../../Sprint%201/BA/D122_Sprint_Review_Retrospective_1_SASMS_DU_THAO.docx) | Tách kiểm kỹ thuật khỏi PR/merge, Staging, baseline và nghiệm thu |

## Mô hình và nâng cấp

Giữ nguyên S0 37 bảng/V001-V002 39 bảng. V003 thêm device_identifiers/product_identifier_profiles; V004 thêm deadline và guard vòng đời; V005 đồng bộ role BA. Hiện hành **41 bảng, 5 migration**; [DBML](../../database/sprint2/schema.dbml), [ERD](../../database/sprint2/erd.mmd), [từ điển](../../database/sprint2/data-dictionary.md) sinh từ catalog thật. Xem [runbook](../../database/sprint2/README.md), [D35 DB](../../Sprint%201/DB/D35-hop-dong-quy-tac-dinh-danh.md).

Owner/migrator chạy toàn bộ migrations, runtime S1 rồi extension S2; không sửa migration/checksum cũ hoặc nạp lại baseline/seed vào DB đã nâng cấp. `--target-version 2` chỉ phục vụ regression lịch sử, không hạ cấp DB.

## S2-03 — Chọn, giữ và thanh toán

find_and_lock_device_for_pos giữ chữ ký store/order/item/serial/actor RETURNS uuid. Serial chỉ trim, phân biệt hoa/thường. Kiểm active actor/sales.checkout/store/warehouse/product/DRAFT, khóa NOWAIT, đối thủ nhận 55P03.

Lần giữ đầu tạo hold_expires_at theo giờ server: **POS10 phút, ONLINE30 phút**; quét lại/reserve không gia hạn. Cart cũ khi nâng cấp giữ deadline bảo thủ từ created_at vì không biết thời điểm quét gốc. Guard kiểm cả ghi order_devices và reserve; cart A hết hạn không thể chiếm lease B mới. DRAFT chưa đổi tồn trạng thái; RESERVED mới giảm IN_STOCK.

release_device_lock chỉ bỏ lựa chọn DRAFT, kiểm quyền theo store của đơn. reserve_order/complete_sale/cancel_order giữ chữ ký và kiểm quyền trước cả retry; tiền CAPTURED phải bằng tổng, audit/movement/outbox/invoice DRAFT không ghi lặp.

expire_order_reservation(order,actor) hủy/giải phóng DRAFT/RESERVED đã quá deadline, **không có PENDING/CAPTURED**; retry không release lần hai. Worker gọi hàm thuộc ứng dụng/vận hành, chưa có scheduler chỉ từ SQL. Trước khởi tạo payment phải có reservation còn hạn. PENDING không tự release theo TTL/manual cancel; callback đã xác minh có thể chốt sau deadline. Adapter đối soát/quản lý sau15 phút thuộc tích hợp, không suy thất bại theo đồng hồ.

V003 giữ raw/case/IMEI phụ/checksum. imei2 hiện là trường JSON SQL; DTO HTTP cần Fullstack cập nhật riêng. Không đổi hãng DEMO hoặc bịa raw lịch sử.

## S2-06 — Tồn và snapshot

v_realtime_inventory theo SKU active/kho/store active, giá products.list_price AS price. available_stock=IN_STOCK, reserved_stock=RESERVED, sold_stock=SOLD; DRAFT chưa giảm số tồn này. Tồn sổ D34=available+reserved. total_physical_stock là tên tương thích đếm toàn bộ bản ghi kể cả SOLD, không dùng làm tồn sổ/kiểm đếm vật lý.

Snapshot PostgreSQL sau commit không chứng minh UI zero-lag. Fullstack đã có API stock dùng stock_availability và receipt; DB không tự đổi controller hoặc coi cart/payment E2E đã hoàn thành. Index hỗ trợ query không thay benchmark toàn đường QR/POS/AI.

## S2-08 — Fallback và AI

get_cross_sell_fallback_rules giữ sáu cột đầu ra; limit1–10, lọc IN_STOCK, **gộp theo product, tổng tồn store**, tie-break product ID trước LIMIT, không lặp SKU vì nhiều warehouse. Consumer kiểm kho cụ thể theo D52, active/giá/tồn trước hiển thị/thêm giỏ/checkout.

Brand/category chỉ là heuristic, không xác nhận phụ kiện tương thích/chính hãng. Backend kiểm ai.use/store/JWT. Engine Java hiện chưa gọi candidate SQL; phần DB không tự sửa adapter/provider. Cache cross-sell5 phút, không coi ai_recommendations là DB cache có writer hoặc báo token saving khi chưa đo.

## Quyền, kiểm chứng và nghiệm thu

PUBLIC/reader không gọi hàm ghi mới; app dùng LOGIN thuộc sasms_app, không owner. Registry/profile/audit không ghi trực tiếp. V005 có chín role canonical; alias cũ giữ lịch sử, ADMIN không tự duyệt hoàn tiền.

[Validator](../../scripts/validate_sprint2.py) chạy regression S0/S1 tại V002 trước nâng cấp, retry legacy, checksum/no-op, SQL/ACL/D34/D35 và hai kết nối POS commit/rollback. [Summary](../../database/evidence/sprint2/summary.json)/[log](../../database/evidence/sprint2/validation.log) ghi kết quả thật/hash. Trên DB đã nâng cấp, regression S1 cần DB riêng; không dùng test uppercase V002 đánh giá V003.

Test DB không thay video POS, JWT/HTTP, D76/D79/load, Staging, PR/merge hoặc chữ ký PO. Readiness vẫn NOT_DEPLOYED đến khi có deploy thật và người sở hữu xác nhận.
