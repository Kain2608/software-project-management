# D128 — Đóng góp kỹ thuật DB cho Quality Gate M3

Mốc kế hoạch: **20/10/2026, 15:30–16:30**. Người chuẩn bị phần DB: **Trần Trung Kiên**. Rà soát: 13/11/2026. Đây là hồ sơ đầu vào; **biên bản D128 chính thức do Trịnh Quốc An chủ trì**, có trưởng bộ phận liên quan ký theo workbook.

## Phạm vi và minh chứng

| Tiêu chí M3 / đầu việc | Kiểm chứng DB hoặc phần cần bổ sung | Trạng thái nghiệm thu |
|---|---|---|
| S2-03: hai POS tranh Serial | [Validator](../../scripts/validate_sprint2.py): hai PID, một transaction đang giữ, người thua `55P03`, commit và rollback; xem [evidence](../../database/evidence/sprint2) đúng source hash | DB local đạt: ca commit/rollback dùng hai PID khác nhau; chưa phải demo hai máy vật lý |
| Function được gọi đúng quyền | `PUBLIC` không có EXECUTE, runtime được cấp; `release_device_lock` kiểm actor active/checkout/store từ đơn | 33 assertions Sprint 2 đạt, gồm ACL/authorization; chờ review của người phụ trách |
| QR < 1 giây / POS < 2 giây | Số đo DB không thay camera/API/UI; Phát/Thái cần D72/D76 và demo | Chưa có bằng chứng ứng dụng Sprint 2 trong hồ sơ DB |
| Thanh toán/hóa đơn/QR | Baseline `reserve_order`/`complete_sale` có transaction, movement và invoice nội bộ DRAFT; driver/PDF/e-invoice/QR UI thuộc tích hợp | Chưa nghiệm thu E2E/in hóa đơn |
| S2-12: Staging tự động | Workflow kiểm DB và readiness; báo `NOT_DEPLOYED` | Chưa có deploy/health thật |
| Không còn P1/P2 | Phát tổng hợp D76/D80 theo lần verify | Không suy từ vài test DB thành toàn ứng dụng không lỗi |

Lần kiểm local **13/11/2026, 23:14:15–23:14:31 (UTC+7), PostgreSQL 16.15**: Sprint 2 **33 assertions đạt**, regression Sprint 1 **76**, Sprint 0 **56 + 7 ACL**. Hai ca tranh Serial có cặp PID **28700/22168** và **33476/14500**; đối thủ bị `55P03` khi transaction giữ còn mở, sau release/rollback giữ lại được. [Summary](../../database/evidence/sprint2/summary.json) và [log](../../database/evidence/sprint2/validation.log) có source hash và kết quả cleanup/trigger được khôi phục.

[Failure probes CLI](../../database/evidence/sprint2/ci-failure-probes.json) xác nhận lỗi SQL và assertion đều trả nonzero/summary failed; chưa có run CI remote hoặc deployment mới.

Mã nguồn và hợp đồng đọc tại [DS2 DB](DS2-thiet-ke-va-ban-giao-DB.md); phạm vi CI tại [D22](D22-ke-hoach-moi-truong-va-CICD-sprint2.md). Evidence của Sprint 0/1 giữ nguyên provenance, không dùng để ký đạt Sprint 2.

## Các phát biểu đã hiệu chỉnh

Bản trước ghi log PASS mẫu, số benchmark và trạng thái Staging/ĐẠT như kết quả thực tế. Không có log/run gắn đúng phiên bản để xác nhận các số đó; chúng đã được bỏ. Khi có kết quả thật, gắn summary/log, timestamp, phiên bản PostgreSQL và source hash; không chép chuỗi PASS dự kiến vào mục kết quả.

Việc qua/không qua M3, người ký, ngày ký và điều kiện còn mở do PM/PO/QA và người có thẩm quyền xác nhận. Hồ sơ này chưa ghi chữ ký thay họ.
