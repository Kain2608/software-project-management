# D35 — Hợp đồng DB cho quy tắc Serial/IMEI

**Phần Kiên hỗ trợ S1-02 của BA Huy; phiên bản đề xuất 1.0, ngày 07/11/2026.** Tài liệu mô tả cách DB lưu/áp dụng quy tắc, không thay bảng định dạng hãng có nguồn và xác nhận của trưởng kho.

## 1. Nguồn và tình trạng

[Workbook](inputs/phan-cong-sprint-1.xlsx), sheet “Phân chia công việc” dòng 6 và “Theo người” dòng 15/25, giao Huy chốt quy tắc riêng Sony, Samsung, LG, Apple… với nguồn hãng/đại lý và xác nhận trưởng kho; Kiên hỗ trợ. Repo chưa có bộ quy tắc theo từng dòng sản phẩm đã được xác nhận. Tên hãng trong workbook không đủ để suy ra độ dài, tập ký tự hoặc tính bắt buộc IMEI.

Nguồn [D33](../../Sprint%200/BA/D33.docx) chứa câu hỏi khảo sát, không phải bộ câu trả lời đã duyệt. Baseline [D51](../../Sprint%200/DB/D51-thiet-ke-co-so-du-lieu.md) ghi rõ các giả định kỹ thuật cần chốt.

## 2. Quy tắc kỹ thuật chung hiện dùng

| Thuộc tính | Xử lý DB | Phần chưa được suy diễn |
|---|---|---|
| Serial | Trim hai đầu, chữ hoa, không khoảng trắng bên trong, dài 1–100; duy nhất toàn chuỗi | Không coi đây là regex chính thức của từng hãng hoặc model |
| IMEI | Có thể NULL theo quy tắc áp dụng; nếu có, đúng 15 chữ số và duy nhất | Không tự xác nhận checksum, số IMEI trên một máy hoặc mọi mặt hàng đều có IMEI |
| SKU | Mã catalog riêng, trim/chữ hoa, tối đa 64, không khoảng trắng; duy nhất | Không dùng SKU thay Serial của từng thiết bị |
| Token QR | UUID định danh của thiết bị; payload đề xuất `SASMS:DEVICE:<qr_token>` | Không xem mọi QR của nhà cung cấp đều có cùng payload; không chứa PII/secret |

Canonical hóa phải diễn ra trước kiểm tra trùng và trước lưu payload của batch. Serial chỉ khác hoa/thường hoặc khoảng trắng hai đầu không trở thành hai thiết bị. Phần tử sai hoặc trùng trong cùng một lô làm rollback toàn lô.

## 3. Cấu hình theo hãng

`identifier_rules` trong [V002](../../database/migrations/V002__inventory_batches_and_identifiers.sql) lưu quy tắc theo hãng và phiên bản, mặc định chưa active. Rule chỉ được active khi có `source_reference`, `approved_by`, `approved_at` theo các ràng buộc SQL. Những trường này ghi nguồn và việc phê duyệt được cung cấp, không tự chứng minh tài liệu nguồn chính thức; BA và trưởng kho chịu trách nhiệm xác nhận nội dung.

Phạm vi hiện tại là hãng. Không kích hoạt một quy tắc chỉ đúng một model cho toàn hãng; nếu nguồn chỉ áp dụng một nhóm/model, cần mở rộng phạm vi bằng migration được review hoặc giữ rule chưa active cho tới khi thống nhất cách áp dụng.

Không nạp sẵn regex suy đoán từ ví dụ tìm trên mạng, tên hãng hoặc một Serial duy nhất. Dữ liệu rule dùng trong test phải ghi rõ là fixture mô phỏng và rollback/cleanup sau chạy; không biến fixture thành quy tắc sản xuất.

Khi chưa có rule đã được phê duyệt cho phạm vi áp dụng, hàm dùng kiểm tra kỹ thuật chung hiện có. Kết quả “nhập thành công” trong trường hợp đó chỉ xác nhận hợp lệ theo fallback kỹ thuật, không xác nhận Serial đúng chuẩn hãng. Trước nghiệm thu M2 với tiêu chí “100% Serial nhập chuẩn”, BA/PM phải quyết định rõ fallback có đủ để demo hay cần chặn hãng chưa có rule.

## 4. Phiếu BA cần hoàn thiện

| Hãng / phạm vi thực tế | Tài liệu nguồn và phiên bản | Quy tắc Serial | IMEI bắt buộc / định dạng | Người/ngày xác nhận | Trạng thái |
|---|---|---|---|---|---|
| Sony / BA xác định sản phẩm/model | Chưa cung cấp | Chưa chốt | Chưa chốt | Chưa có | Chờ BA/kho |
| Samsung / BA xác định sản phẩm/model | Chưa cung cấp | Chưa chốt | Chưa chốt | Chưa có | Chờ BA/kho |
| LG / BA xác định sản phẩm/model | Chưa cung cấp | Chưa chốt | Chưa chốt | Chưa có | Chờ BA/kho |
| Apple / BA xác định sản phẩm/model | Chưa cung cấp | Chưa chốt | Chưa chốt | Chưa có | Chờ BA/kho |

Với mỗi rule, BA cần cung cấp mẫu hợp lệ, sai độ dài/ký tự, trường hợp không có IMEI hoặc nhiều IMEI, phạm vi uniqueness và cách xử lý thiết bị cũ đã nhập trước khi thay đổi rule. Không sửa lại Serial lịch sử hàng loạt mà thiếu đối soát và phương án migration.

## 5. Hợp đồng với Dev / QA

Thái dùng hàm nhập DB thay vì chỉ kiểm regex tại UI. Client có thể kiểm sớm để hỗ trợ nhập liệu nhưng DB kiểm tra lại trước COMMIT. Mọi request lấy actor từ phiên được xác thực và đúng quyền kho/cửa hàng. API cần trả lỗi theo trường và vị trí phần tử mà không lộ SQL, dữ liệu ngoài phạm vi hoặc secret.

QA kiểm thử tối thiểu: canonical hóa, Serial/IMEI trùng giữa SKU, trùng trong cùng lô, hai phiên nhập cùng định danh, giá trị thiếu/sai kiểu, rule chưa duyệt không được áp dụng, rule đã duyệt từ chối sai định dạng và toàn lô rollback khi một phần tử lỗi. Đây là đóng góp DB cho D71/D75, không thay việc QA/PO xác nhận bao phủ FRS hoặc bộ 35 case toàn kho/QR.

Lần chạy **07/11/2026** trên PostgreSQL **16.15** đã kiểm chứng các trường hợp này trong bộ **76 assertions Sprint 1** và ca hai phiên cùng Serial/IMEI trên hai PO độc lập: mỗi ca chỉ một nhập thành công, phiên còn lại bị unique `23505`. Fixture rule mô phỏng xác nhận rule draft chưa áp dụng, rule active thiếu nguồn/phê duyệt bị từ chối, rule đã duyệt kiểm định dạng/IMEI và không cho sửa hoặc xóa định nghĩa đã duyệt. Chỉ một rule active theo hãng được phép; deactive trả các lần nhập tương lai về fallback chung. [Log thực tế](../../database/evidence/sprint1/validation.log) và [summary](../../database/evidence/sprint1/summary.json) là bằng chứng kỹ thuật; không cung cấp nguồn quy tắc Sony/Samsung/LG/Apple hoặc chữ ký nghiệp vụ.

| Xác nhận | Người cần review | Trạng thái |
|---|---|---|
| Nội dung và nguồn quy tắc hãng | Huy, trưởng kho/đại diện nghiệp vụ | Chưa có xác nhận |
| Cách cấu hình và kiểm tra DB | Kiên, Thái | Chờ review |
| Mức đủ để nghiệm thu M2 | Huy, PM | Chưa có quyết định |
