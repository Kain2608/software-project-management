# Đóng góp kiến trúc và dữ liệu — KD-02, KD-04, KD-09

**Trần Trung Kiên · Architect / DBA · bản hỗ trợ PM ngày 07/11/2026.** Workbook tiền Sprint, sheet “Theo người” dòng 27, giao Kiên hỗ trợ ba đầu việc này. Trịnh Quốc An vẫn là người làm chính của các tài liệu PM; nội dung dưới đây là phần đánh giá và dữ liệu để An tích hợp, không thay chữ ký hoặc quyết định của PM/Sponsor.

## 1. KD-02 — Nội dung kỹ thuật cho Charter và phạm vi

Căn cứ [D05 Scope](../PM/D05.docx) và [D06 Charter](../PM/D06.docx). Workbook đánh mã Charter/Scope ngược tên thực của hai file; cần PM thống nhất danh mục trước khi đóng baseline.

| Nội dung để PM đưa vào baseline | Đề xuất / điều kiện xác nhận |
|---|---|
| Ranh giới dữ liệu lõi | Một SKU là mẫu hàng; từng thiết bị là bản ghi riêng với Serial/IMEI, vị trí và lịch sử giao dịch. Không dùng một số tồn nhập tay độc lập làm nguồn chân lý thứ hai |
| Tính duy nhất | Bảo vệ định danh bằng UNIQUE/FK tại DB; BA xác nhận phạm vi global, quy tắc hãng và trường hợp nhiều IMEI. Kiểm tra “chưa có” trên UI không đủ chống nhập đồng thời |
| Giao dịch quan trọng | Nhập kho/giữ hàng/chốt bán/hủy hợp lệ phải có transaction và kiểm tra trạng thái; lỗi không để chứng từ, tồn và audit dở dang |
| Chia trách nhiệm | Kiên sở hữu thiết kế dữ liệu, quyền DB, migration, môi trường và khôi phục; Thái sở hữu API/UI/JWT; Huy sở hữu FRS/quy tắc nghiệp vụ; Vũ sở hữu AI; Phát sở hữu QA/E2E; An tổng hợp kế hoạch/gate |
| Nguồn quyền và tích hợp | Credential DB chỉ ở backend tin cậy; actor/scope phải do API đã xác thực cung cấp. AI và scanner không được tự đặt trạng thái thanh toán hoặc chỉnh quyền |
| Ngoài phạm vi mặc định | Không huấn luyện model riêng, không native mobile, không tự xây nền tảng hóa đơn điện tử; tích hợp nhà cung cấp chỉ mở khi có hợp đồng/sandbox và owner |

### Mục tiêu phi chức năng và bằng chứng cần có

| Mục tiêu nêu trong D05/D06 | Cách đo cần được thống nhất | Giới hạn kết luận |
|---|---|---|
| Serial unique; không lệch tồn do cạnh tranh | Hai phiên độc lập nhập cùng Serial/IMEI, kiểm tra một bản ghi và không có side effect thừa | Là bằng chứng DB, không thay khảo sát định dạng từng hãng |
| 100.000 thiết bị | Dataset giả có số lượng xác nhận, query plan/lookup và tải đồng thời trên cấu hình ghi rõ | Lookup nhanh không tự chứng minh khả năng xử lý camera/API |
| QR <1s; POS <2s; AI <3s | Định nghĩa điểm bắt đầu/kết thúc, thiết bị/mạng, tỷ lệ lỗi và percentile; QA đo E2E | SQL test không đủ chứng minh độ trễ toàn hệ thống |
| Uptime 99,9% | Cửa sổ đo, nguồn giám sát, cách tính downtime, lịch bảo trì và người xử lý sự cố | Chỉ là mục tiêu nếu chưa có dữ liệu vận hành |
| Khôi phục dữ liệu | RPO/RTO được chủ hệ thống duyệt; diễn tập backup/restore và đối soát | Logical dump hằng ngày không tự đạt RPO 15 phút |

Kiến trúc mục tiêu có ranh giới dịch vụ rõ, nhưng triển khai DB chung ở giai đoạn đầu giúp giữ transaction kho/POS cục bộ. Tách database/dịch vụ chỉ sau khi có hợp đồng sự kiện, idempotency và cơ chế bù trừ đã thử lỗi; không giả định transaction phân tán tồn tại vì tài liệu dùng từ “microservices”. Chi tiết hiện thực các Sprint sau có tại [D50](../../Sprint%200/DB/D50-kien-truc-he-thong.md), [D51](../../Sprint%200/DB/D51-thiet-ke-co-so-du-lieu.md) và [DS1 DB](../../Sprint%201/DB/DS1-thiet-ke-va-ban-giao-DB.md).

## 2. KD-04 — Đánh giá khả thi và đối chiếu business case

### Những khả năng có thể kiểm chứng

| Khía cạnh | Cơ sở đánh giá | Việc cần hoàn thành trước khi kết luận |
|---|---|---|
| Dữ liệu | PostgreSQL 16 cung cấp transaction, khóa, UNIQUE/FK và migration có kiểm soát; repo hiện có mã/schema thực thi | Chạy trên môi trường mục tiêu, kiểm tra nâng cấp với dữ liệu hiện hữu và tải đã chốt |
| Phát triển | Repo có baseline và migration DB; toolchain DB dùng Python/psql độc lập với ứng dụng | Thái chốt backend/framework cùng D22; không triển khai đồng thời mọi phương án Java/Node được nêu trong D08 |
| Tích hợp | AI/provider ở adapter, có timeout/fallback và request identity; không gọi mạng khi giữ khóa DB | Có thông tin provider/sandbox, xác nhận tiền, hợp đồng dữ liệu và test lỗi mạng |
| Vận hành | Có kế hoạch tách credential, sao lưu, restore và theo dõi lỗi/khóa/dung lượng | Chọn host, owner, secret manager, lưu backup ngoại vi và diễn tập thật trước Prod |
| Thiết bị | Browser camera/scan, token QR và in HTML/PDF hỗ trợ hướng demo hiện tại | Đối chiếu model/driver và tiêu chí D21 nếu mua hoặc mượn thiết bị thật |

### Bảng hòa giải ngân sách và năng lực

| Nguồn | Số liệu trong nguồn | Đánh giá của Kiên |
|---|---|---|
| [D12](../PM/D12.xlsx), sheet 1 E4:E12 | Nhân sự 460,8 triệu; cloud 15 triệu; AI 8 triệu; dự phòng 16,2 triệu; tổng 500 triệu VNĐ | Tổng các dòng khớp 500 triệu. Chưa có dòng phần cứng riêng; chi thiết bị cần được PM/Sponsor quyết định nguồn và cập nhật baseline |
| [D09](../PM/D09.docx), mục WBS; [D13](../PM/D13.docx), đoạn 39–43 | 5.220 giờ công; Kiến trúc/DB 720 giờ | Là mô hình các phòng ban/nhóm chuyên viên được đại diện; không mặc định sáu cá nhân có thể làm đủ số giờ này |
| [D08](../PM/D08.docx), mục 3.2; [D20](../PM/D20.docx), đoạn 77 | Mô hình Lean $100–150, sáu thành viên, 540 giờ | Là quy mô/tập chi phí khác; không cộng trực tiếp USD vào VNĐ hoặc coi 540 giờ bằng 5.220 giờ |
| Workbook tiền Sprint, “Theo người” dòng 26 | Kiên 12 giờ cho KD-10 | Là ước lượng nhiệm vụ chuẩn bị D21/D22 của trưởng bộ phận; không thay tổng effort DB/cloud toàn dự án |

PM cần chọn mô hình ngân sách và nguồn lực áp dụng cho bản nghiệm thu, ghi rõ các khoản thực chi và chi phí mô phỏng. Việc có bảng 500 triệu không chứng minh tài khoản dự án đã được cấp tiền. Không tính ROI, tỷ giá hoặc báo giá nhà cung cấp khi chưa có dữ liệu gốc; tỷ lệ lợi ích tăng 15%/giảm sai sót trong Charter vẫn là giả thuyết cần pilot đo.

### Các giả định ảnh hưởng quyết định đầu tư

| Mã | Giả định / phụ thuộc | Nếu không đúng |
|---|---|---|
| FEAS-01 | FRS và quy tắc định danh được BA chốt trước khi khóa schema | Dùng cấu hình có version và ghi điểm mở; không gắn regex phỏng đoán với nhãn đã duyệt |
| FEAS-02 | Có người vận hành và ngân sách hạ tầng phù hợp RPO/RTO | Giảm phạm vi môi trường/HA theo quyết định PM, giữ rõ rủi ro thay vì hứa SLA không có nguồn lực |
| FEAS-03 | Demo không phụ thuộc phần cứng bị tạm hoãn | D21 hiện hành ưu tiên dùng lại/mượn và HTML/PDF theo D19/D20; mua máy in trở lại phải đánh giá CR |
| FEAS-04 | Dữ liệu thử là giả hoặc đã ẩn danh | Không chuyển dữ liệu khách thật vào Dev/CI/LLM; xác định chủ dữ liệu và quyền truy cập trước import |

## 3. KD-09 — Đóng góp cho CCB và PMP

[D19](../PM/D19.docx) quy định PM chủ trì CCB; Kiên đánh giá ảnh hưởng ERD/DB/API/toàn vẹn. D19 mô tả biểu quyết 3/5 có PM đồng thuận và quyền quyết định của PM; hồ sơ mới không tự chứng minh CCB đã họp hoặc biểu quyết. [D20](../PM/D20.docx) là tài liệu An tổng hợp và trình Sponsor.

### Phiếu đánh giá kỹ thuật để gắn vào mỗi CR

| Trường cần điền | Nội dung Kiên phải cung cấp |
|---|---|
| CR / trigger / phiên bản | ID yêu cầu, nguyên nhân, commit/schema/môi trường đang bị ảnh hưởng; phân biệt lỗi triển khai với đổi yêu cầu |
| Scope | Bảng, field, rule định danh, quyền, API contract và workflow nào đổi; phần nào giữ tương thích |
| Dữ liệu | Khối lượng và chất lượng dữ liệu hiện hữu, nullable/default/backfill, FK/unique, thời gian khóa dự kiến; dữ liệu nhạy cảm liên quan |
| Tiến độ / nguồn lực | Thời gian migration, test, restore drill, phối hợp Dev/QA và cửa sổ phát hành; ghi cơ sở ước lượng |
| Chi phí | Hạ tầng/backup/quota/thiết bị tăng thêm, nguồn dự toán và khoản dự phòng đề nghị; chưa được coi là đã duyệt |
| Chất lượng / rủi ro | Ca regression và cạnh tranh, nguy cơ mất dữ liệu hoặc cấp quyền rộng, cách phát hiện và người xử lý |
| Phương án và khả năng đảo ngược | Không đổi, phương án ít ảnh hưởng, phương án đề xuất; rollback transaction trước commit, forward fix sau commit, restore backup theo kế hoạch riêng |
| Tiêu chí đóng | Migration đã review/checksum, test đủ nhánh lỗi, snapshot/đối soát, kết quả môi trường mục tiêu và QA verify; quyết định của PM/CCB và liên kết bằng chứng |

Migration đã áp dụng phải được giữ nguyên, thay đổi tiếp đi bằng version mới. Không dùng down migration xóa dữ liệu để gọi là rollback an toàn. Trước restore phải ngừng ghi, tách worker/provider, xác nhận điểm khôi phục và đối soát payment/outbox trước mở lại; người có thẩm quyền xác nhận phần dữ liệu có thể mất trong giới hạn RPO.

### Dữ liệu PM cần tích hợp vào PMP

An dùng D21 cho phụ thuộc thiết bị và nguồn chi; D22 cho môi trường, credential, CI, release, backup và sự cố. Gắn owner chính cho từng hạng mục: Kiên thực hiện dữ liệu/môi trường; Thái thực hiện code ứng dụng/deploy adapter; Phát xác nhận test; Huy xác nhận nghiệp vụ; PM/Sponsor chịu quyết định scope/ngân sách/gate theo thẩm quyền. Mỗi hạng mục có một A do ma trận RACI chính thức xác lập; không tự thay RACI toàn dự án bằng bảng hỗ trợ này.

## 4. Xung đột thời điểm và câu hỏi còn mở

- Kỳ tiền Sprint kết thúc 18/09/2026; D19 đoạn 54–64 mô tả CR ngày 14/10 và quyết định 18/10. Quyết định đó là cập nhật sau kỳ, không thể dùng làm chữ ký hoặc quyết định mua sắm đã có tháng 9.
- D20 đoạn 44–46 gọi 18/09 là “Sprint 0 Baseline Sign-off”; [D11](../PM/D11.docx), đoạn 8–12, đặt M1 ngày 25/09. Workbook tách Cổng khởi động 18/09 khỏi M1; PM cần giữ rõ hai cổng.
- D06 có Sponsor “Nguyễn Văn A”; D20 dùng “Giảng viên phụ trách / Vendora Retail Group”. Cần danh tính và quyền ký thật, không tự chọn một tên để lấp ô chữ ký.
- Tài liệu PM có lời “đã phê duyệt”, nhưng chưa có bằng chứng ký cho D21/D22 và phần bổ sung ngày 07/11. [Hồ sơ D132](D132-ho-so-kien-cho-cong-khoi-dong.md) giữ các ô review/quyết định chờ người có thẩm quyền.
