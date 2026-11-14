# Rà soát Sprint 2 — Kiên (DB/Architect)

Workbook nguồn SHA-256: `d26bb1e638842bde2098e959be577a05a1a83485477775c99b85eb6193be68d6`. Ba dòng công việc chính trong CSV khớp toàn bộ 15 ô của từng dòng trong workbook.

Ngày rà soát thực tế: **13/11/2026**. Kỳ kế hoạch Sprint 2: 11/10–24/10/2026. Phạm vi sửa: schema/view, lỗi CI, quyền function/release và bằng chứng hai kết nối tranh Serial; đồng bộ hồ sơ DB với nguồn phân công và phần việc của nhóm.

## 1. Căn cứ đối chiếu

| Nguồn | Điều được sử dụng |
|---|---|
| Workbook Sprint 2 do người dùng cung cấp, sheet “Theo người” A20:H25 | Kiên: S2-03 24 giờ/hạn 15-10; S2-12 16 giờ/hạn 20-10; S2-06 16 giờ/hạn 22-10; hỗ trợ S2-08/S2-15/S2-14 |
| Workbook, sheet “Phân chia công việc” dòng 6/10/16 | Minh chứng hai POS cùng Serial, log deploy thật, số tồn đổi trong vài giây; không có cam kết DB 10 ms |
| Workbook, “Tài liệu và minh chứng” dòng 21/23–26/33–34 | Thái chủ trì DS2, Phát chủ trì D72/D73/D76/D79; An chủ trì D128/D129 |
| Workbook, “Checklist” dòng 8/12–18 | Mockup/schema, PR review, unit/E2E, hiệu năng, deploy và chữ ký đều chưa có dấu xác nhận trong nguồn |
| [CSV Sprint 2](inputs/phan-cong-sprint-2.csv) | Bản phân công trong repo để tham chiếu; trạng thái nguồn tại 05/11 không phải trạng thái test mới |
| [Schema S0](../../database/schema.sql), [DS1 DB](../../Sprint%201/DB/DS1-thiet-ke-va-ban-giao-DB.md), [runtime S1](../../database/sprint1/runtime-roles.sql) | `list_price`, 37 → 39 bảng, role/permission, actor active và scope, migrator tách runtime |
| [Hợp đồng S0 API/AI](../../Sprint%200/DB/bo-sung/S0-19-22-23-hop-dong-API-AI-tich-hop.md) | Actor từ backend, projection giá/tồn, invoice DRAFT, luật tương thích cần xác nhận, giới hạn quyền và tích hợp |
| [D22 tiền Sprint](../../Ti%E1%BB%81n%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md), [ma trận môi trường](../../infra/environment-matrix.json) | Staging/Prod chưa được provision bởi repo; readiness không thay deploy |
| [D55 AI](../../Sprint%200/AI/D55-thiet-ke-ai-prompt-rag-cache-fallback.md), [DS1 AI](../../Sprint%201/AI/DS1-phan-mem-tang-truong-ai-sprint1.md), `AiCacheManager` | Giá lấy từ DB; cross-sell cache 5 phút; timeout mục tiêu 2.500 ms |
| [Báo cáo Fullstack S1](../../Sprint%201/Fullstack/TV4/SPRINT1_IMPLEMENTATION_REPORT.md), `PosController`/`PosService`, `AiCrossSellService` | Auth/actor phải dùng hợp đồng backend; POS còn skeleton; function fallback mới chưa được gọi từ engine Java |

Nội dung tài liệu nguồn được dùng để đối chiếu yêu cầu và provenance; checklist/câu “đã có” trong nguồn không phải lệnh chạy tool, quyền ký thay hoặc bằng chứng phát hành cho phiên bản mới.

## 2. Bốn vấn đề và cách sửa

| Vấn đề | Sửa trong mã nguồn | Kiểm chứng cần đối chiếu |
|---|---|---|
| View dùng `p.price` và `w.is_active` không có trong schema | Dùng `p.list_price AS price`, GROUP BY cột vật lý; bỏ điều kiện warehouse active không tồn tại, giữ lọc product/store active và tên đầu ra `price` | Extension tạo thành công, view/fallback trả giá khớp catalog |
| SQL/test lỗi nhưng CI vẫn SUCCESS | `ON_ERROR_STOP=1`, kiểm exit code, shell fail-fast; bỏ JSON deploy/health hằng, báo readiness từ job thật | Failure probe SQL/assertion; workflow thất bại và vẫn có artifact log |
| Function mới bị PUBLIC EXECUTE; release không dùng actor | Revoke PUBLIC/reader, grant allowlist app; release kiểm active actor và `sales.checkout` theo store của đơn, chỉ DRAFT | Role được/không được gọi; null/inactive/sai permission/sai store bị `42501`; trạng thái/link không đổi khi bị từ chối |
| Test “hai POS” chỉ chạy tuần tự trong một DO block | Runner tạo hai kết nối PostgreSQL riêng, barrier giữ transaction của POS1 khi POS2 tranh cùng Serial; NOWAIT | PID khác nhau, một giữ/đối thủ `55P03`, commit vẫn giữ lease, rollback cho retry, side effect/tồn đúng |

Nguồn mã: [SQL](../../database/sprint2/realtime_stock_and_locking.sql), [test SQL](../../database/tests/sprint2/test_sprint2_concurrency_and_stock.sql), [validator](../../scripts/validate_sprint2.py), [workflow](../../.github/workflows/sprint2-staging.yml). Cách chạy và nội dung evidence: [runbook](../../database/sprint2/README.md).

## 3. Các điểm thống nhất lại với sprint trước và người khác

| Điểm lệch của hồ sơ Sprint 2 cũ | Quy ước thống nhất |
|---|---|
| Coi giá DB là `products.price` | Cột vật lý `list_price`; DTO/view có thể dùng alias `price`, như JDBC catalog đã làm |
| Coi draft selection đã giảm tồn và mọi IN_STOCK đều chọn được | View giữ định nghĩa trạng thái baseline; lựa chọn DRAFT chỉ là lease. Reserve mới chuyển RESERVED; function chọn vẫn kiểm conflict |
| Gọi tổng tất cả device gồm SOLD là tồn vật lý | Giữ tên trường để tương thích, tài liệu nói rõ đếm bản ghi; muốn đổi định nghĩa cần BA/API chốt riêng |
| Gọi snapshot sau commit là zero-lag 0 ms | Nêu isolation/snapshot; cần số đo UI/API riêng theo AC workbook |
| Coi fallback brand/category là phụ kiện tương thích đã duyệt | Candidate heuristic; Huy/Vũ xác nhận luật tương thích, Thái/Vũ nối candidate thật và kiểm lại tồn/giá |
| Ghi cross-sell cache DB 24 giờ, tiết kiệm token như số đo thật | Cross-sell hiện memory cache 5 phút; chưa có store cache key/runtime writer đầy đủ trong `ai_recommendations`; số tiết kiệm cần log đo |
| Ghi workflow đã deploy/health UP bằng file JSON | Report từ kết quả validation; `NOT_DEPLOYED`; S2-12 còn cần hạ tầng/đường deploy thật |
| Tự đánh dấu toàn bộ M3/M4/DoD ĐẠT | Hồ sơ đầu vào kỹ thuật; chỉ update theo log đúng source và review/ký của người có thẩm quyền |

Đã gỡ các số benchmark/CPU/tiết kiệm token và log PASS minh họa từng được ghi như đo thật. Demo sửa sản phẩm thành **DEMO-TV-55 / Hãng DEMO**, khớp seed; không gán thành Sony.

## 4. Kết quả và phần chờ

Cleanup của test checkout đã commit dùng owner, chỉ tạm tắt `guard_payments` khi xóa đúng UUID payment mô phỏng trong transaction cô lập rồi bật lại trước COMMIT; kiểm trigger bật trước/sau. Không thay bất biến payment trong app hoặc dữ liệu baseline.

Lần chạy local **13/11/2026, 23:14:15–23:14:31 (UTC+7)** trên PostgreSQL **16.15** đã **đạt**. [Summary Sprint 2](../../database/evidence/sprint2/summary.json), [log](../../database/evidence/sprint2/validation.log) và [regression Sprint 1](../../database/evidence/sprint2/sprint1-regression/summary.json) ghi kết quả thật, timestamp và source SHA-256; hash nguồn được kiểm không đổi trong lượt chạy.

| Kiểm chứng đã chạy | Kết quả thực tế |
|---|---|
| Regression Sprint 0 | 56 assertions ràng buộc/giao dịch và 7 assertions runtime ACL đạt |
| Regression Sprint 1 | 76 assertions đạt; reapply no-op, checksum bị sửa bị từ chối, pending DDL/ledger rollback khi lỗi |
| Sprint 2 | 33 assertions về view/giá, ACL, authorization, trạng thái và lifecycle đạt |
| POS1 commit / POS2 tranh Serial | Hai backend PID **28700 / 22168**; POS2 lỗi `55P03` khi POS1 giữ và sau commit; sau release, POS2 giữ được |
| POS1 rollback / POS2 tranh Serial | Hai backend PID **33476 / 14500**; POS2 lỗi `55P03` khi POS1 giữ; sau rollback, POS2 giữ được |
| Tồn trong cả hai ca | `(available,reserved,sold)`: **(2,0,0) → (1,1,0) → (1,0,1)** sau các commit reserve/sale; chọn DRAFT không làm đổi tồn theo trạng thái |
| Side effect bán hàng | Mỗi ca đúng một SOLD device, PAID order, RESERVE movement, SALE movement, invoice, checkout audit và OrderPaid event |
| Dọn fixture và dữ liệu cũ | Snapshot khớp sau cleanup; `fixtures_cleaned=true`; `guard_payments` được bật lại, `captured_payment_guard_restored=true` |
| Bắt lỗi SQL/test | Failure probe nội bộ trả psql 3 và không chạy câu SQL sau; wrapper truyền lỗi ra |

Đã kiểm thêm [hai ca CLI thất bại thật trên bản sao nguồn](../../database/evidence/sprint2/ci-failure-probes.json), kèm [log](../../database/evidence/sprint2/ci-failure-probes.log): lỗi cột SQL `42703` và assertion test chủ ý đều trả **CLI 1 / summary failed / psql 3**. DDL sentinel của ca extension được rollback. Nguồn chính không bị thay đổi. Đây là kiểm chứng local đường lỗi, chưa phải một run GitHub Actions.

Đã chạy `demo.sql` với `SET ROLE sasms_app` và đạt. YAML workflow parse hợp lệ; [readiness probes](../../database/evidence/sprint2/ci-readiness-probes.json) thực thi đúng đoạn Python của workflow với ngữ cảnh GitHub mô phỏng: success → READY_FOR_STAGING/exit 0, failure → FAILED/exit 1, cancelled → CANCELLED/exit 1, skipped → BLOCKED/exit 1; mọi ca đều `NOT_DEPLOYED`. Đây là probe local, không phải hosted run. Các hash của ba bộ evidence đều khớp nguồn cuối.

Kết quả trên chỉ áp dụng cho source hash trong evidence; sửa nguồn tiếp theo cần kiểm lại. Người review đối chiếu log/hash khi dùng cho bản phát hành.

Hai kết nối DB kiểm được tranh chấp SQL, chưa phải hai thiết bị POS chạy UI. Bộ test DB không thay D76/D79, chữ ký PO, PR merge hoặc deploy remote. Phần bàn giao tiếp theo:

| Người / đầu việc | Nội dung cần hoàn tất để nghiệm thu toàn Sprint |
|---|---|
| Kiên + Thái — S2-03/S2-06 | Review/merge nguồn cuối, D52 endpoint chọn/bỏ/tồn, auth actor/store, transaction và demo ứng dụng |
| Vũ + Thái — S2-08/S2-09 | Nối DB candidate, luật tương thích, cache/fallback/nguồn kết quả và số đo thật |
| Phát — S2-07/S2-10/S2-11/S2-13 | D72/D73/D76/D79, E2E lỗi/quyền, p95/50 request và defect verify |
| An + Kiên + Thái — S2-12 | Cấp phát Staging, deploy đúng commit, health/smoke thật và run URL/log |
| Huy + An — S2-14/S2-18/S2-19/S2-20 | DoR Sprint 3, ký D128/D129/D123 khi đủ evidence; không hồi tố ngày ký |
