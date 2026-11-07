# D126 — Hồ sơ Kiên đóng góp cho review Quality Gate M1

**Phân loại:** hồ sơ chuẩn bị review và bàn giao phần Kiến trúc / DB / Bảo mật. **Chưa phải biên bản họp hoặc quyết định thông qua Quality Gate do PM ban hành.**

| Thuộc tính | Nội dung |
|---|---|
| Dự án | SASMS / Vendora |
| Sprint theo phân công | Sprint 0, 12/09–25/09/2026 |
| Mốc dự kiến trong nguồn | M1 — 25/09/2026 |
| Người phụ trách phần hồ sơ | Trần Trung Kiên — Architect / DBA |
| Nhánh công việc | `Kien` |
| Trạng thái | Chờ review; chưa có quyết định gate hoặc chữ ký cho phiên bản dựng lại |

Ngày của mốc trong nguồn không phải ngày ký hồ sơ mới. Không suy ra có cuộc họp, người tham dự hoặc việc phê duyệt mới từ trạng thái “đã phê duyệt” trong các tài liệu PM trước đây.

## 1. Phạm vi review và căn cứ

Theo workbook `02 - Sprint 0 (12-09 đến 25-09) - Phân chia công việc.xlsx`, sheet “Theo người”, dòng 19–22, Kiên làm chính S0-17, S0-18, S0-21 và hỗ trợ S0-24/14/15/22/19/23/27. Sheet “Tài liệu và minh chứng” dòng 19, 20, 23, 26 quy định sơ đồ, review thiết kế, ma trận 9 vai trò và log PostgreSQL 16.

[D11](../PM/D11.docx), phần M1, yêu cầu Charter, Scope, FRS_V2 và DB Schema Serial/IMEI được phê duyệt. Hồ sơ này chỉ tập hợp phần Kiên cung cấp; các phần còn lại cần chủ sở hữu tương ứng và PM đối chiếu.

## 2. Danh mục bằng chứng cần người review mở và kiểm tra

| Mục | Artifact / bằng chứng | Điều kiện chấp nhận | Trạng thái trong hồ sơ chuẩn bị |
|---|---|---|---|
| S0-17 / D50 | [D50](D50-kien-truc-he-thong.md) | Sơ đồ, ranh giới dịch vụ, môi trường và trách nhiệm dữ liệu rõ; Dev Lead/PM xác nhận | Chờ review |
| S0-18 / D51 | [D51](D51-thiet-ke-co-so-du-lieu.md) | ERD, từ điển dữ liệu, chuẩn hóa và quy tắc nghiệp vụ được đối chiếu yêu cầu | Chờ review |
| S0-18 / D57 | [SQL](../../database/schema.sql), [DBML](../../database/schema.dbml), [ERD](../../database/erd.mmd), [D57](D57-luoc-do-va-kiem-thu.md) | SQL/DBML/ERD nhất quán; chạy được trên PostgreSQL 16; quy trình thiết lập được mô tả | Cần đối chiếu artifact và log thực tế |
| Dữ liệu phát triển | [seed.sql](../../database/seed.sql) | Dữ liệu mô phỏng rõ ràng; không có bí mật hoặc dữ liệu khách hàng thật | Cần kiểm tra nội dung |
| Kiểm thử DB | [constraints.sql](../../database/tests/constraints.sql), [validate_db.py](../../scripts/validate_db.py) | Có ca hợp lệ và ca bị từ chối, rollback và kiểm tra tính toàn vẹn | Cần đối chiếu ca kiểm tra và kết quả |
| Kết quả thực thi | [validation.log](../../database/evidence/validation.log) | Có phiên bản PostgreSQL và đầu ra thực tế; không bỏ qua lỗi | Chỉ ghi đạt nếu log chứng minh đạt |
| S0-21 / D54 | [D54](D54-bao-mat-phan-quyen.md) | Ma trận 9 vai trò, quyền và phạm vi dữ liệu; kiểm soát ứng dụng/DB rõ ràng | Vai trò đề xuất, chờ BA/PM xác nhận |
| Bàn giao môi trường | [database/README.md](../../database/README.md) | Người khác dựng lại môi trường và chạy kiểm tra theo hướng dẫn | Chờ người nhận xác nhận |
| Hỗ trợ các đầu việc khác | [README phần DB](README.md) | Giao dịch API, quyền, thanh toán và điểm mở được chuyển đúng người sở hữu | Đóng góp của Kiên; chưa thay thế tài liệu của người làm chính |

Các artifact và log đã có trong phiên bản này. Một liên kết, một script hoặc nội dung kế hoạch không thay thế bằng chứng thực thi. Kết quả kỹ thuật dưới đây lấy từ lần chạy thực tế; người review vẫn phải xác nhận độc lập trước khi ký.

## 3. Phiếu ghi kết quả kiểm chứng thực tế

| Thông tin | Giá trị do người kiểm chứng điền |
|---|---|
| Ngày/giờ thực chạy | 07/11/2026, 11:27:13–11:27:30 (UTC+7); timestamp UTC gốc trong summary.json |
| Công cụ chạy / người review | Codex thực thi script; người review dự án còn chờ xác nhận |
| Commit / phiên bản artifact | Nhánh Kien; SHA-256 từng nguồn tại database/evidence/summary.json |
| Phiên bản PostgreSQL | 16.15, Windows x64; cluster local riêng chỉ bind loopback |
| Lệnh chạy / môi trường | `python scripts/validate_db.py --apply --benchmark --psql <psql16> --dsn <DB-thử-riêng>`; DSN không được ghi vào log |
| Nạp schema và seed | Đạt trên database mới; seed chạy hai lần giữ nguyên nội dung 37 bảng |
| Ràng buộc và giao dịch | 56 assertions đạt; 7 assertions ACL đạt; kiểm tra fixtures không làm thay đổi dữ liệu nền |
| Kiểm tra tranh chấp / xử lý lặp | Hai phiên tranh cùng thiết bị: một thành công, một bị chặn; retry không nhân bản SALE/invoice/event |
| Đường dẫn bằng chứng | `database/evidence/validation.log` và artifact liên quan |
| Lỗi / ngoại lệ / hạn chế còn lại | Chưa có test E2E/provider/backup-restore; kiểm tra 100.000 thiết bị chỉ đo lookup DB; những điểm mở ở mục 4 vẫn cần quyết định |

Các chỉ tiêu tải 100.000 thiết bị, độ trễ POS/QR và availability trong nguồn là mục tiêu thiết kế. Kiểm tra ràng buộc DB không chứng minh đạt hiệu năng hoặc SLA toàn hệ thống; cần bộ đo và môi trường tương ứng để kết luận.

## 4. Câu hỏi phải được quyết định trước khi ký baseline

| Mã | Nội dung | Người cần xác nhận | Trạng thái |
|---|---|---|---|
| M1-01 | Bổ sung FRS_V2/D34 và D22 còn thiếu; đối chiếu với thiết kế được dựng lại | Huy, Kiên, Thái | Mở |
| M1-02 | Chấp thuận baseline 37 bảng mới: đây là thiết kế dựng lại, không phải bản nháp cũ đã khôi phục | Kiên, Huy, Sponsor | Mở |
| M1-03 | Chốt tên, quyền và phạm vi dữ liệu của 9 vai trò; số lượng 9 đã có trong workbook nhưng danh sách chưa có | Huy, Kiên, PM | Mở |
| M1-04 | Phân biệt phạm vi 6 module toàn hệ thống trong D05/D11 với phạm vi demo thu hẹp trong D19/D20; xác nhận các chức năng chỉ có khung schema | PM, Huy, Thái | Mở |
| M1-05 | Thống nhất ngày M1 25/09 trong workbook/D11 với ngày sign-off 18/09 trong D20 | PM | Mở |
| M1-06 | Chốt phạm vi unique Serial/IMEI, quy tắc hãng và nội dung QR; D33 là câu hỏi khảo sát, chưa phải câu trả lời | Huy, đại diện kho | Mở |
| M1-07 | Chốt thời hạn giữ hàng, khóa kiểm kê và đối soát thanh toán chưa rõ kết quả | Huy, Thái, đại diện vận hành | Mở |
| M1-08 | Chốt căn cứ xác nhận tiền: VietQR chỉ là phương tiện chỉ dẫn thanh toán, không chứng minh tiền đã về | Huy, Thái, PM | Mở |
| M1-09 | Xác nhận D52/OpenAPI đầy đủ do Thái sở hữu; hợp đồng giao dịch DB chỉ là phần Kiên hỗ trợ | Thái, Kiên | Mở |
| M1-10 | Chốt backup, phục hồi, quyền truy cập và chính sách lưu giữ dữ liệu trước triển khai thật | Kiên, PM, chủ hệ thống | Mở |

Xuất kho ngoài xuất bán, đổi trả/hoàn tiền và các quy trình nâng cao hiện chỉ thuộc khung dữ liệu khi được mô tả như vậy trong thiết kế. Không ghi các chức năng này là đã triển khai chỉ vì có bảng liên quan.

## 5. Nội dung bàn giao liên nhóm để review

- **BA / BPMN (S0-14, S0-15):** đối chiếu bước nghiệp vụ với `receive_device`, `reserve_order`, `complete_sale`, `cancel_order`; xác định nhánh lỗi, quyền, điều kiện chuyển trạng thái và rollback.
- **Dev Lead / API (S0-19, S0-24):** dùng chữ ký SQL thực tế, kiểm tra actor/RBAC tại backend, commit xong mới trả thành công, xử lý tranh chấp và request lặp; Thái hoàn thiện D52 và cấu hình môi trường ứng dụng.
- **AI (S0-22):** chỉ dùng catalog/quy tắc được phép cho RAG; loại secret, token và PII thô khỏi prompt, embedding, cache và log; AI không có quyền hoàn tất thanh toán hoặc sửa kho.
- **Tích hợp (S0-23):** xác minh sự kiện thanh toán qua nguồn có thẩm quyền; không suy diễn nhận tiền từ ảnh QR hoặc trạng thái UI; không gọi dịch vụ mạng bên ngoài trong transaction đang giữ khóa kho.
- **PM / Gate (S0-27):** nhận artifact, kết quả thực chạy và điểm mở; tổ chức review, ghi quyết định và thu chữ ký thật theo quy trình nhóm.

## 6. Mẫu ghi nhận review — chưa điền kết quả

| Nội dung | Chờ người chủ trì ghi |
|---|---|
| Ngày/giờ và hình thức review | Chưa tổ chức / chưa ghi nhận |
| Người tham dự thực tế | Chờ xác nhận |
| Phiên bản tài liệu được review | Chờ xác nhận |
| Ý kiến và lỗi cần sửa | Chờ review |
| Quyết định cho từng artifact | Chưa có quyết định |
| Quyết định M1 của PM/Sponsor | Chưa có quyết định; hồ sơ này không tự thông qua gate |
| Điều kiện / người xử lý / hạn xử lý | Chờ review |
| Liên kết biên bản chính thức | PM bổ sung sau khi ban hành |

## 7. Mẫu xác nhận — toàn bộ chữ ký đang chờ

| Phạm vi xác nhận | Vai trò / người xác nhận dự kiến | Trạng thái / ngày / chữ ký |
|---|---|---|
| Thiết kế kiến trúc D50 | Vũ Duy Thái — Dev Lead; Trịnh Quốc An — PM | Chờ review và ký; chưa có ngày |
| Thiết kế DB và SQL D51/D57 | Trần Trung Kiên; Nguyễn Quang Huy; Sponsor theo phân công | Chờ review và ký; chưa có ngày |
| Bảo mật và phân quyền D54 | Trần Trung Kiên; Trịnh Quốc An | Chờ review và ký; chưa có ngày |
| Quality Gate M1 chính thức | Huy, Kiên, PM, Sponsor theo bảng phân công | Chờ cuộc review và biên bản chính thức |

Không điền chữ ký thay người khác, không ghi thành viên đã dự họp khi chưa có bằng chứng, và không dùng lời phê duyệt trong hồ sơ cũ để phê duyệt ngầm cho phiên bản mới.
