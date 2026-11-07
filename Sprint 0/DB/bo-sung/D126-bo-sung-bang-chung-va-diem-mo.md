# D126 — Bổ sung đối chiếu hồ sơ M1 của Kiên

Đây là đóng góp của Kiên cho S0-27 do PM làm chính. Bản [hồ sơ M1 trước](../D126-ho-so-review-M1.md) được giữ nguyên; tài liệu này cập nhật danh mục đầu vào và bằng chứng hiện có để người review dùng đúng phiên bản. Không tạo biên bản họp, chữ ký hoặc quyết định gate thay PM/Sponsor.

## 1. Tình trạng tài liệu để review

| Hạng mục | Bản hiện có / phần bổ sung | Kết luận kỹ thuật |
|---|---|---|
| D50 kiến trúc | [D50](../D50-kien-truc-he-thong.md), [căn cứ bổ sung](D50-D54-bo-sung-can-cu-va-kiem-soat.md) | Giữ sơ đồ/thiết kế đã có; D22 hiện đã tìm thấy và được dẫn liên kết |
| D51 thiết kế DB | [D51](../D51-thiet-ke-co-so-du-lieu.md), [rà soát bổ sung](D51-bo-sung-ra-soat-thiet-ke.md) | Dùng lại bản 37 bảng; hoàn thiện truy vết nguồn–ràng buộc–test và điểm quyết định |
| D57 SQL/DBML | [SQL](../../../database/schema.sql), [DBML](../../../database/schema.dbml), [ERD](../../../database/erd.mmd), [summary](../../../database/evidence/summary.json) | “ĐÃ CÓ” theo workbook; giữ nguyên mã, test và log cũ |
| D54 bảo mật | [D54](../D54-bao-mat-phan-quyen.md), [đối chiếu phiên bản](D50-D54-bo-sung-can-cu-va-kiem-soat.md) | Giữ ma trận đã soạn; cần BA/PM quyết định danh sách role và quyền nghiệp vụ |
| D22 môi trường | [D22 tiền Sprint](../../../Ti%E1%BB%81n%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md) | Đã có hồ sơ và công cụ; không còn là đầu vào thất lạc |
| D34/FRS_V2 | Chưa tìm thấy trong repo cập nhật | Cần Huy cung cấp để xác nhận đủ yêu cầu trước ký baseline |
| D35 định dạng hãng | [Hợp đồng kỹ thuật Sprint 1](../../../Sprint%201/DB/D35-hop-dong-quy-tac-dinh-danh.md) chỉ là đề xuất | Cần nguồn/quy tắc được BA/kho xác nhận; không coi hợp đồng kỹ thuật là quy tắc hãng đã duyệt |
| D70 và các phần QA/PO | Chưa có artifact đầy đủ thuộc người làm chính trong gói DB | PM/QA/PO đối chiếu phần của mình; không ghi đạt thay họ |

## 2. Cách đọc bằng chứng

Baseline Sprint 0 đã có 56 assertions ràng buộc/lifecycle, 7 assertions quyền SQL, seed lặp trên 37 bảng, tranh chấp hai phiên giữ cùng thiết bị và lookup dữ liệu giả 100.000 thiết bị. Lượt đối chiếu hiện tại xác minh hash nguồn vẫn khớp [summary](../../../database/evidence/summary.json); không chạy lại hay thay log thành một lượt test mới khi nguồn không đổi.

Bằng chứng restore 40 kiểm tra và các run CI trong [hồ sơ tiền Sprint](../../../Ti%E1%BB%81n%20Sprint/DB/evidence) được bổ sung sau đó. Chúng chứng minh những source hash/commit tương ứng, không phải biên bản M1 hoặc số đo camera/HTTP/availability. Các đầu ra Sprint 1 được tách riêng khỏi baseline Sprint 0.

[Biên mục đối chiếu](doi-chieu-sprint-0.json) lưu hash các artifact được giữ, workbook dùng để quyết định, nhánh `DB` và những file bổ sung. Người review có thể kiểm lại chính sách giữ/bổ sung theo từng mục.

## 3. Điều kiện ký và chuyển giao

Kiên bàn giao phần kỹ thuật hiện có và các bổ sung cho Thái/Huy/PM. Huy xác nhận yêu cầu/rule/role; Thái review hợp đồng API và khả năng triển khai; QA kiểm bao phủ test của ứng dụng; PM/Sponsor ghi quyết định M1. Tên Sponsor, phiên bản ký, ngày review và người tham dự phải lấy từ sự kiện thật.

Các quyết định về FRS, phương thức thanh toán/đối soát, quy tắc hãng và phạm vi bảo hành/đổi trả không được tự đóng chỉ vì schema có bảng. Những điểm này có người sở hữu và artifact cần xác nhận, tách với phần tài liệu DB đã hoàn thiện.
