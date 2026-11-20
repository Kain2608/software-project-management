# D126 — Bổ sung đối chiếu hồ sơ M1 của Kiên

Đây là đóng góp của Kiên cho S0-27 do PM làm chính. Bản [hồ sơ M1](../D126-ho-so-review-M1.md) và phụ lục được rà soát ngày **20/11/2026** để thống nhất đầu vào, phiên bản và evidence. Giữ nguyên SQL, artifact và log lịch sử; rà soát tài liệu không tạo biên bản họp, chữ ký hoặc quyết định gate thay PM/Sponsor.

## 1. Tình trạng tài liệu để review

| Hạng mục | Bản hiện có / phần bổ sung | Kết luận kỹ thuật |
|---|---|---|
| D50 kiến trúc | [D50](../D50-kien-truc-he-thong.md), [căn cứ bổ sung](D50-D54-bo-sung-can-cu-va-kiem-soat.md) | Giữ sơ đồ/thiết kế đã có; D22 hiện đã tìm thấy và được dẫn liên kết |
| D51 thiết kế DB | [D51](../D51-thiet-ke-co-so-du-lieu.md), [thiết kế và truy vết](../D51-thiet-ke-co-so-du-lieu.md) | Dùng lại bản 37 bảng; hoàn thiện truy vết nguồn–ràng buộc–test và điểm quyết định |
| D57 SQL/DBML | [SQL](../../../database/schema.sql), [DBML](../../../database/schema.dbml), [ERD](../../../database/erd.mmd), [summary](../../../database/evidence/summary.json) | “ĐÃ CÓ” theo workbook; giữ nguyên mã, test và log cũ |
| D54 bảo mật | [D54](../D54-bao-mat-phan-quyen.md), [đối chiếu phiên bản](D50-D54-bo-sung-can-cu-va-kiem-soat.md) | D34 đã chốt chín role; D54/V005 phản ánh quyền, alias lịch sử và scope; ký nghiệm thu vẫn thuộc người có thẩm quyền |
| D22 môi trường | [D22 tiền Sprint](../../../Ti%E1%BB%81n%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md) | Đã có hồ sơ và công cụ; không còn là đầu vào thất lạc |
| D34/FRS_V2 | Đã có D34_FRS_V2_SASMS.docx của BA | Đối chiếu FR-01, BR-01/03 và AC; hoàn thành DB không tự chứng minh API/E2E hoặc chữ ký gate |
| D35 định danh | D35 BA và [hợp đồng DB](../../../Sprint%201/DB/D35-hop-dong-quy-tac-dinh-danh.md) | V003 hiện thực raw/case/128, Luhn và IMEI phụ; profile chỉ theo SKU được duyệt, không suy regex toàn hãng |
| D70 và các phần QA/PO | [D70 Test Plan](../../Tester/D70_Ke_hoach_Kiem_thu_Test_Plan_SASMS_v1.0.docx) đã có; evidence chạy QA/gate cần người sở hữu đối chiếu | Test Plan không thay kết quả thực thi; PM/QA/PO xác nhận phạm vi của mình |

## 2. Cách đọc bằng chứng

Baseline Sprint 0 đã có 56 assertions ràng buộc/lifecycle, 7 assertions quyền SQL, seed lặp trên 37 bảng, tranh chấp hai phiên giữ cùng thiết bị và lookup dữ liệu giả 100.000 thiết bị. Lượt đối chiếu hiện tại xác minh hash nguồn vẫn khớp [summary](../../../database/evidence/summary.json); không chạy lại hay thay log thành một lượt test mới khi nguồn không đổi.

Bằng chứng restore hiện hành 41 kiểm tra trên schema41 bảng và các run CI trong [hồ sơ tiền Sprint](../../../Ti%E1%BB%81n%20Sprint/DB/evidence) được bổ sung sau đó. Chúng chứng minh những source hash/commit tương ứng, không phải biên bản M1 hoặc số đo camera/HTTP/availability. Các đầu ra Sprint 1 được tách riêng khỏi baseline Sprint 0.

Truy vết yêu cầu giữ trong D51/CSV; hash SQL và kết quả thực chạy nằm trong summary của từng phiên bản. Không dùng biên mục rà soát riêng thay hợp đồng và bằng chứng bàn giao.

## 3. Điều kiện ký và chuyển giao

Kiên bàn giao phần kỹ thuật hiện có và các bổ sung cho Thái/Huy/PM. Huy xác nhận yêu cầu/rule/role; Thái review hợp đồng API và khả năng triển khai; QA kiểm bao phủ test của ứng dụng; PM/Sponsor ghi quyết định M1. Tên Sponsor, phiên bản ký, ngày review và người tham dự phải lấy từ sự kiện thật.

Các quyết định về FRS, phương thức thanh toán/đối soát, quy tắc hãng và phạm vi bảo hành/đổi trả không được tự đóng chỉ vì schema có bảng. Những điểm này có người sở hữu và artifact cần xác nhận, tách với phần tài liệu DB đã hoàn thiện.
