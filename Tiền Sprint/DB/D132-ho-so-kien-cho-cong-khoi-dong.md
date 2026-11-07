# D132 — Hồ sơ Kiên đóng góp cho Cổng khởi động

**Phân loại:** tập hồ sơ kỹ thuật để PM/Sponsor review. Đây chưa phải biên bản họp, quyết định cho phép triển khai hoặc kết luận Cổng khởi động đã thông qua.

| Thuộc tính | Nội dung |
|---|---|
| Người chuẩn bị phần Kiên | Trần Trung Kiên — Architect / DBA |
| Giai đoạn trong phân công | 05/09–18/09/2026; cổng dự kiến 18/09/2026 |
| Ngày chuẩn bị bản bổ sung | 07/11/2026 |
| Nhánh | `Kien` |
| Trạng thái ký | Chờ review và chữ ký thật cho phiên bản mới |

## 1. Phạm vi review

[Workbook tiền Sprint](inputs/phan-cong-tien-sprint.xlsx), “Theo người” dòng 24–27, giao Kiên KD-10 và hỗ trợ KD-02/04/09. “Phân chia công việc” dòng 18 giao PM làm chính D132; Kiên cung cấp bằng chứng phần kiến trúc/dữ liệu, không thay PM tổ chức gate. Các đầu ra Charter, Scope, stakeholder, lịch/chi phí/nguồn lực/RACI/rủi ro thuộc người sở hữu ghi trong workbook.

## 2. Gói bằng chứng phần Kiên

| Hạng mục | Người review mở artifact | Điều kiện chấp nhận và trạng thái |
|---|---|---|
| KD-10 / D21 | [Kế hoạch mua sắm](D21-ke-hoach-mua-sam-thiet-bi.md) | Có nhu cầu/tiêu chí/nguồn ngân sách/kiểm nhận rõ; PM/Sponsor duyệt trước chi. Chưa suy ra có mua hoặc nhận thiết bị |
| KD-10 / D22 | [Môi trường và CI/CD](D22-ke-hoach-moi-truong-va-CICD.md) | Có sơ đồ, quyền/config, build/test/release, backup/restore và trách nhiệm; triển khai thực tế cần log tương ứng |
| Hỗ trợ KD-02 | [Mục Charter/phạm vi](KD-02-04-09-dong-gop-kien-truc-va-du-lieu.md#1-kd-02--nội-dung-kỹ-thuật-cho-charter-và-phạm-vi) | PM/Huy xác nhận ranh giới dữ liệu, NFR và phần còn thiếu trước baseline |
| Hỗ trợ KD-04 | [Mục khả thi](KD-02-04-09-dong-gop-kien-truc-va-du-lieu.md#2-kd-04--đánh-giá-khả-thi-và-đối-chiếu-business-case) | Hòa giải ngân sách 500 triệu/5.220h và mô hình Lean; không tạo kết quả ROI hoặc báo giá giả |
| Hỗ trợ KD-09 | [Mục CCB/PMP](KD-02-04-09-dong-gop-kien-truc-va-du-lieu.md#3-kd-09--đóng-góp-cho-ccb-và-pmp) | Có phiếu đánh giá dữ liệu/migration/quyền/khôi phục để gắn vào CR; PM quyết định theo thẩm quyền |
| Minh chứng kỹ thuật | [Evidence](evidence) | Đối chiếu commit, hash nguồn, thời gian và kết quả CI/diễn tập. Chỉ ghi đạt theo lần chạy thật |

## 3. Sổ kiểm chứng kỹ thuật

| Nội dung | Bằng chứng / giới hạn cần đối chiếu |
|---|---|
| CI baseline và Sprint 1 đã có | Commit `d28c95a`; run [42340988289](https://github.com/Kain2608/software-project-management/actions/runs/42340988289) và [42340988364](https://github.com/Kain2608/software-project-management/actions/runs/42340988364) hoàn thành thành công ngày 07/11/2026; snapshot API trong evidence cần ghi thời điểm lấy |
| Bản bổ sung tiền Sprint | [Snapshot GitHub CI](evidence/ci-runs.json) ghi đúng SHA và trạng thái từng workflow; đối chiếu mã được review với nguồn đã kiểm, không hồi tố run tháng 11 thành ngày tháng 9 |
| Backup/restore | [Summary](evidence/environment-summary.json), [log](evidence/environment-validation.log): ngày 07/11/2026, 19:49:43–19:49:49 (UTC+7), PostgreSQL 16.15, 40 kiểm tra đạt; hash nguồn khớp bộ bàn giao |
| Dữ liệu và quyền sau restore | 39 bảng nghiệp vụ + ledger khớp; 255 cột, 229 constraints, 106 indexes, 14 routines, 6 triggers, 1 view và ACL được đối chiếu; target mới không có PUBLIC CONNECT |
| Hành vi và nhánh từ chối | Runtime replay trả UUID cũ/không nhân bản; chặn ghi đè archive, restore vào target đã có và archive sai checksum; dữ liệu nguồn không đổi, fixtures nguồn được dọn |
| Staging/Prod | Cần host, cấu hình secret/quyền, release log và kiểm nhận môi trường thật; CI/restore local không chứng minh triển khai remote |
| Thiết bị | Cần báo giá/đơn mua/biên bản nhận và test thực tế nếu được duyệt; D21 là kế hoạch, chưa thay các chứng từ này |
| NFR / gate | Số đo DB không chứng minh QR camera, API, SLA uptime hoặc PO ký nghiệm thu |

Ngày kiểm chứng tháng 11 không được đổi thành ngày 18/09/2026. Kết quả do công cụ thực thi không phải chữ ký của Kiên, PM hoặc Sponsor.

## 4. Các quyết định chờ trước khi đóng gate

| Mã | Điểm cần quyết định | Người sở hữu |
|---|---|---|
| KO-01 | Danh tính Sponsor và quyền ký; không dùng “Nguyễn Văn A” như danh tính đã xác minh | An / Sponsor |
| KO-02 | Danh mục mã D05/D06 thống nhất với nội dung Scope/Charter thực tế | An |
| KO-03 | Baseline 500 triệu VNĐ/5.220 giờ hay mô hình Lean $100–150/540 giờ; nguồn chi thiết bị riêng | An / Sponsor, Kiên và Vũ tư vấn phần liên quan |
| KO-04 | Phạm vi tiền Sprint tháng 9 so với cập nhật CR máy in tháng 10 và demo hiện tại | An / Huy / Thái |
| KO-05 | D21 thiết bị, phương án dùng lại/mượn/mua và trần ngân sách được duyệt | An / Sponsor; Kiên tư vấn tương thích |
| KO-06 | D22 host, owner vận hành, credential, backup ngoài máy, RPO/RTO và cửa sổ phát hành | Kiên / Thái / An / chủ hệ thống |
| KO-07 | FRS/quy tắc định danh, phạm vi quyền và kế hoạch E2E đủ cơ sở đóng baseline | Huy / Thái / Phát |
| KO-08 | Phân biệt Cổng khởi động 18/09 và M1 25/09; cập nhật lịch chính thức nếu cần | An |

## 5. Mẫu ghi nhận review và ký — chưa điền quyết định

| Thông tin cuộc review | Chờ người chủ trì ghi |
|---|---|
| Ngày/giờ, địa điểm/hình thức | Chưa ghi nhận |
| Người tham dự thực tế | Chờ xác nhận; không điền sẵn 6/6 thành viên |
| Phiên bản tài liệu/commit được review | Chờ xác nhận tại cuộc review |
| Ý kiến và điều kiện phải sửa | Chờ review |
| Quyết định cho D21/D22 | Chưa có quyết định ký duyệt bản mới |
| Quyết định Cổng khởi động | PM/Sponsor ghi trong biên bản chính thức |
| Người xử lý, hạn và bằng chứng đóng điều kiện | Chờ ghi theo quyết định thật |

| Phạm vi ký / xác nhận | Người dự kiến theo vai trò | Trạng thái |
|---|---|---|
| Tính phù hợp thiết bị/môi trường/dữ liệu | Kiên; Thái review khả năng tích hợp | Chờ review dự án |
| Phạm vi và tiêu chí nghiệp vụ | Huy / PO | Chờ xác nhận |
| Kiểm nhận/test và hồ sơ chất lượng | Phát / QA | Chờ xác nhận |
| Tích hợp kế hoạch và trình gate | An / PM | Chờ review/ký |
| Cho phép triển khai và ngân sách | Sponsor có danh tính được xác minh | Chờ quyết định/ký |

PM đính kèm các Charter/Scope, lịch, chi phí, nguồn lực, RACI và rủi ro đã ký khi ban hành D132. Hồ sơ Kiên không tự thay thế các chữ ký đó hoặc tạo một sự kiện họp lịch sử.
