# Sprint 2 — Phần DB của Trần Trung Kiên

Kỳ kế hoạch: **11/10–24/10/2026**, chủ đề POS tại quầy và AI gợi ý; phạm vi US-04, US-05, US-06 (Should), US-07. Mốc kế hoạch M3: 20/10, M4: 24/10. Rà soát tài liệu: **20/11/2026** trên nguồn `main` tại `6544a00`; evidence SQL hiện có là lần chạy **17/11/2026**, không hồi tố ngày thực hiện hoặc chữ ký.

## Phân công và phạm vi bàn giao

Đã đối chiếu workbook `04 - Sprint 2 (11-10 đến 24-10) - Phân chia công việc.xlsx`, sheet **Theo người, dòng 20–25**, với [CSV trong repo](inputs/phan-cong-sprint-2.csv). Kiên có ba việc chính, tổng **56 giờ ước lượng**:

| Mã việc | Trách nhiệm | Hạn kế hoạch | Giờ | Minh chứng cần có |
|---|---|---|---|---|
| S2-03 / TSK-02-02 | Tìm và khóa chọn Serial khi bán | 15/10/2026 | 24 | PR đã merge; hai kết nối POS cùng Serial chỉ một bên giữ được |
| S2-12 | Staging và pipeline deploy tự động | 20/10/2026 | 16 | Sơ đồ môi trường; log GitHub Actions deploy Staging thật |
| S2-06 / TSK-02-05 | Truy vấn tồn kho theo siêu thị | 22/10/2026 | 16 | PR đã merge; số tồn đổi trong vài giây sau bán |

Kiên hỗ trợ S2-08 do **Nguyễn Tiến Vũ** chủ trì (AI), S2-15 do **Trịnh Quốc An** chủ trì (CCB/token/EVM), S2-14 do **Nguyễn Quang Huy** chủ trì (refinement Sprint 3). **Vũ Duy Thái** phối hợp API/ứng dụng; **Phạm Đại Phát** phụ trách QA. Hồ sơ DB là phần đóng góp cho DS2; DS2 toàn ứng dụng, D128/D129 chính thức và ký nghiệm thu vẫn theo người chủ trì trong workbook.

## Hồ sơ và mã nguồn

| Tài liệu / thành phần | Mục đích |
|---|---|
| [DS2 — Thiết kế và bàn giao DB](DS2-thiet-ke-va-ban-giao-DB.md) | SQL/API, quyền, trạng thái tồn và giới hạn tích hợp |
| [D22 — Môi trường và CI/CD](D22-ke-hoach-moi-truong-va-CICD-sprint2.md) | CI báo lỗi đúng; readiness và phần Staging còn chờ |
| [D128 — Đóng góp DB cho M3](D128-ho-so-review-DB-M3.md) | Checklist kỹ thuật; không thay biên bản PM ký |
| [D129 — Đóng góp DB cho M4](D129-ho-so-review-DB-M4.md) | Checklist tồn kho/AI; không thay báo cáo tải D79 |
| [Runbook DB Sprint 2](../../database/sprint2/README.md) | Cài đặt, chạy test, demo và đọc evidence |
| [SQL Sprint 2](../../database/sprint2/realtime_stock_and_locking.sql) | View, khóa/giải phóng Serial, candidate fallback và ACL |
| [Validator Sprint 2](../../scripts/validate_sprint2.py) | Hai kết nối độc lập, kiểm quyền và số tồn |
| [Workflow](../../.github/workflows/sprint2-staging.yml) | Kiểm chứng DB và báo cáo readiness; chưa deploy remote |

## Trạng thái nghiệm thu

Kiểm local **17/11/2026** đã đạt **33 assertions S2 + 43 D34/D35**, regression Sprint 1 **76**, Sprint 0 **56 + 7 ACL**, cùng hai ca tranh Serial commit/rollback trên PostgreSQL **16.16**. Xem [hợp đồng và giới hạn](DS2-thiet-ke-va-ban-giao-DB.md), [summary](../../database/evidence/sprint2/summary.json) và [failure probes CLI](../../database/evidence/sprint2/ci-failure-probes.json). Chưa có run GitHub Actions mới hoặc deploy Staging trong hồ sơ này.

Lịch sử Git đã có merge PR #29 phần DB (`06423f0`), PR #33 phần TV4 (`4f5df6f`) và PR #34 hardening POS/QR (`6544a00`). Báo cáo TV4 mục 19–20 mô tả cash checkout/receipt, payment đồng thời và QR SVG với test roundtrip; đó là kết quả thành phần do TV4 cung cấp, chưa chứng minh browser E2E, QR in/quét thật hoặc PO nghiệm thu. Các vấn đề số lượng nhiều chiếc, IMEI phụ, phụ kiện không Serial và lịch chạy QA được ghi tại [bàn giao tích hợp](DS2-thiet-ke-va-ban-giao-DB.md#bàn-giao-tích-hợp-còn-mở).

Nguồn phân công ghi trạng thái tại **05/11/2026**, không phải kết quả test của phiên bản hiện tại. Dấu chọn DoR/DoD chỉ được cập nhật khi có minh chứng và người xác nhận.

- [x] Nguồn DB và increment TV4 đã được tích hợp vào `main` qua PR #29/#33, kiểm bằng lịch sử Git.
- [ ] Kiên/Thái xác nhận review SQL/hợp đồng API trên phiên bản cuối; BA/QA xác nhận các điểm tích hợp còn mở.
- [ ] Đối chiếu `database/evidence/sprint2/summary.json` và log với nguồn thực tế, gồm hai backend PID khác nhau, conflict và kiểm quyền.
- [ ] Phát kiểm browser E2E; QR p95 <1 giây từ decode thành công tới hiển thị (không gồm lấy nét, 100 lượt Chrome mobile/Wi-Fi ổn định); POS p95 <2 giây theo NFR-02; Vũ/Phát đo AI p95 <3 giây với 50 request đồng thời.
- [ ] Staging được cấp phát, deploy thật, kiểm health và lưu URL run/commit/log.
- [ ] Huy/An và các trưởng bộ phận xác nhận DoR/DoD, M3/M4 và Sprint Review.

Một kết quả SQL đạt chỉ chứng minh phạm vi DB trong log tương ứng. Không dùng nó để xác nhận đã có hai máy POS vật lý, in hóa đơn, AI tích hợp, deploy hoặc chữ ký PO.
