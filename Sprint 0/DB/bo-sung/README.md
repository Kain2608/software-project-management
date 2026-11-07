# Sprint 0 — Đối chiếu và hoàn thiện phần còn thiếu

Thực hiện trên nhánh **`DB`** theo yêu cầu mới: giữ đầu ra đã hoàn chỉnh; với mục “MỘT PHẦN”, tìm bản hiện có để bổ sung; chỉ dựng từ đầu khi không tìm thấy; mục “CHƯA CÓ” được tạo phần còn thiếu trong trách nhiệm Kiên. Workbook là ảnh chụp trạng thái cũ, không tự mô tả đủ những file đã được bổ sung sau đó.

## Quyết định theo artifact

| Việc / đầu ra | Trạng thái workbook | Tìm thấy trong repo | Cách xử lý lần này |
|---|---|---|---|
| S0-17 / D50 | CHƯA CÓ | Có thiết kế và sơ đồ hoàn chỉnh từ lượt trước | Giữ nguyên; bổ sung căn cứ D22 và giới hạn môi trường trong [phụ lục](D50-D54-bo-sung-can-cu-va-kiem-soat.md) |
| S0-18 / D51 | MỘT PHẦN | Có thiết kế 37 bảng và từ điển chi tiết | Dùng lại bản đó; bổ sung [rà soát/truy vết](D51-bo-sung-ra-soat-thiet-ke.md), không dựng một thiết kế cạnh tranh |
| S0-18 / D57 | **ĐÃ CÓ** | Có SQL, DBML, ERD, seed, test, log và hash nguồn | **Giữ nguyên toàn bộ**; không sửa migration hoặc làm lại SQL |
| S0-21 / D54 | CHƯA CÓ | Có ma trận 9 role, 17 permission và quyền runtime | Giữ nguyên; làm rõ căn cứ/ràng buộc phiên bản ở phụ lục D50/D54 |
| Hỗ trợ S0-14/15 | MỘT PHẦN / CHƯA CÓ | Có mô tả giao dịch chung; chưa có FRS/BPMN đầy đủ của BA | Tạo [bàn giao yêu cầu và luồng dữ liệu DB](S0-14-15-ban-giao-yeu-cau-va-luong-du-lieu.md) |
| Hỗ trợ S0-19/22/23 | CHƯA CÓ | Có schema/hàm nền, chưa có hợp đồng bàn giao chi tiết | Tạo [hợp đồng DB cho API/AI/tích hợp](S0-19-22-23-hop-dong-API-AI-tich-hop.md) |
| Hỗ trợ S0-24 | MỘT PHẦN | Có khung DB, toolchain và CI; thiếu bản hướng dẫn bàn giao DB riêng | Tạo [đóng góp D63/D65 cho Dev](D63-D65-huong-dan-DB-cho-Dev.md) |
| Hỗ trợ S0-27 / D126 | CHƯA CÓ | Có hồ sơ chuẩn bị review, chưa có biên bản/ký thực | Bổ sung [danh mục bằng chứng/điểm mở](D126-bo-sung-bang-chung-va-diem-mo.md); không tạo phê duyệt giả |

Kiên làm chính ba việc S0-17/18/21; bảy việc hỗ trợ thuộc người làm chính trong workbook. Các bản bàn giao DB không thay thế FRS/BPMN của Huy, OpenAPI/app của Thái, thiết kế AI/log provider của Vũ hoặc biên bản gate do An ban hành.

## Phần giữ nguyên và cách kiểm lại

Các tài liệu D50/D51/D54, từ điển và D57 hiện có được giữ nguyên để bảo toàn provenance và bằng chứng parser. Nhãn `Kien` bên trong chúng là nhánh tạo bản trước; lần tiếp tục này dùng `DB`.

[Biên mục JSON](doi-chieu-sprint-0.json) ghi hash workbook và từng artifact được giữ. Các hash baseline trong `database/evidence/summary.json` vẫn khớp nguồn hiện tại. Việc kiểm hash là kiểm tính nhất quán của bằng chứng cũ, không phải một lượt kiểm thử PostgreSQL mới.

D22 trước bị ghi thiếu trong hồ sơ cũ nay đã có ở [tiền Sprint](../../../Ti%E1%BB%81n%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md). D34/FRS và nguồn định dạng hãng vẫn cần BA cung cấp; các chữ ký không được tự điền thay người có thẩm quyền.
