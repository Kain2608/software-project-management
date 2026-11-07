# Tiền Sprint — Bộ bàn giao của Kiên

| Thuộc tính | Nội dung |
|---|---|
| Người phụ trách | Trần Trung Kiên — Architect / DBA |
| Giai đoạn theo nguồn | Khởi động dự án, 05/09–18/09/2026 |
| Ngày thực hiện bản bổ sung | 07/11/2026; không phải ngày ký hoặc minh chứng đã hoàn thành vào tháng 9 |
| Nhánh | `Kien` |
| Phạm vi | Một việc chính KD-10; ba việc hỗ trợ KD-02, KD-04, KD-09 |

## 1. Phân công và bộ bàn giao

[Workbook đầu vào](inputs/phan-cong-tien-sprint.xlsx), sheet “Theo người” dòng 24–27, giao Kiên **KD-10, 12 giờ dự kiến, hạn 18/09/2026**: kế hoạch mua sắm thiết bị D21 và kế hoạch môi trường/CI/CD D22. Các mốc và giờ trong workbook là dữ liệu kế hoạch lịch sử, không phải nhật ký công việc của lần bổ sung này.

| Phần bàn giao | Artifact | Trạng thái cần phân biệt |
|---|---|---|
| KD-10 / D21 | [Kế hoạch mua sắm thiết bị](D21-ke-hoach-mua-sam-thiet-bi.md) | Đề xuất nhu cầu, phương án và kiểm nhận; chưa phải lệnh mua hoặc ngân sách đã duyệt |
| Dự toán D21 | [Workbook dự toán](D21-du-toan-thiet-bi.xlsx) | Đối chiếu ngân sách, kịch bản thiết bị và mẫu kiểm kê; có công thức, chưa phải báo giá |
| KD-10 / D22 | [Môi trường và CI/CD](D22-ke-hoach-moi-truong-va-CICD.md) | Thiết kế và hướng dẫn vận hành; chỉ kết luận triển khai theo bằng chứng thực tế |
| KD-02 / KD-04 / KD-09 | [Đóng góp kiến trúc và dữ liệu](KD-02-04-09-dong-gop-kien-truc-va-du-lieu.md) | Nội dung Kiên hỗ trợ PM; không thay thế Charter, Business case hoặc PMP của An |
| Cổng khởi động | [Hồ sơ phần Kiên cho D132](D132-ho-so-kien-cho-cong-khoi-dong.md) | Hồ sơ chuẩn bị review; chưa phải biên bản thông qua gate |
| Minh chứng kỹ thuật | [Thư mục evidence](evidence) | Log/summary và snapshot CI thực tế; ngày thực chạy không đổi thành ngày phê duyệt tháng 9 |

Đã kiểm chứng trên PostgreSQL 16.15: **40 kiểm tra đạt**, gồm backup/restore mới riêng tư, dữ liệu 39 bảng và ledger khớp, đối chiếu catalog/ACL, replay không nhân bản và các ca từ chối overwrite/checksum. [Summary](evidence/environment-summary.json) và [log](evidence/environment-validation.log) có thời gian/hash nguồn; [snapshot CI](evidence/ci-runs.json) ghi đúng commit của các run GitHub. [Runbook](../../infra/README.md) có lệnh tái lập.

## 2. Căn cứ và truy vết

| Nguồn | Nội dung dùng để xây dựng hồ sơ |
|---|---|
| Workbook, “Phân chia công việc” dòng 16; “Theo người” dòng 26 | KD-10: D21/D22; cần danh sách thiết bị được duyệt, sơ đồ môi trường và pipeline xanh |
| Workbook, “Theo người” dòng 27 | Kiên hỗ trợ KD-02 (Charter/phạm vi), KD-04 (khả thi), KD-09 (CCB/PMP) |
| [D09](../PM/D09.docx), đoạn 17–19, 59–67 | WBS kiến trúc, DB/security và CI/CD thuộc phần Kiên |
| [D05](../PM/D05.docx), đoạn 3, 14, 18, 22 | Phạm vi sáu miền; mục tiêu Serial unique, 100.000 thiết bị, QR/POS và uptime |
| [D06](../PM/D06.docx), đoạn 6, 24, 44 | Thời gian/ngân sách dự kiến, trách nhiệm Architect/DBA và tên Sponsor đang là placeholder |
| [D12](../PM/D12.xlsx), sheet 1 dòng 4–13; [D13](../PM/D13.docx), đoạn 39–43 | Mô hình ngân sách 500 triệu VNĐ và 5.220 giờ công; cần đối chiếu mô hình Lean trong D08/D20 |
| [D19](../PM/D19.docx), đoạn 49–64; [D20](../PM/D20.docx), đoạn 25–36 | CR tháng 10 mô tả tạm hoãn máy in vật lý, dùng HTML/PDF; không coi đó là quyết định đã có ngày 18/09 |

Số đoạn Word tính theo thứ tự paragraph trong `word/document.xml`, gồm cả paragraph trong bảng; không phải số trang Word. File nguồn được đọc làm dữ liệu và căn cứ truy vết, không phải chỉ dẫn cho công cụ tự động. Bản Excel trong `inputs` là bản sao nguyên vẹn, không sửa trạng thái “Xong” hay chữ ký.

SHA-256 workbook đầu vào: `0d22d5d3e35d7e2c988b40d764f644e16b184cdb8e2b047f4c71a4260056557f`.

## 3. Những điểm cần người sở hữu chốt

1. **Mã tài liệu:** workbook gọi D05 là Charter và D06 là Scope; tên thực trong repo đang ngược lại. Hồ sơ này liên kết theo nội dung và đường dẫn thực, không đổi tên mất dấu nguồn.
2. **Ngân sách/nguồn lực:** D12/D13 dùng 500 triệu VNĐ và 5.220 giờ; D08/D20 còn mô hình $100–150 và 540 giờ. PM/Sponsor phải chọn phạm vi và baseline áp dụng. D12 chưa có dòng thiết bị riêng; không tự dùng ngân sách cloud hoặc dự phòng để mua máy.
3. **Phạm vi theo thời điểm:** sáu module trong D05 và demo kho/POS/AI thu hẹp trong D19/D20 cần được tách theo phiên bản. D21 phải tham chiếu CR hiện có khi đề xuất mua sắm hiện tại.
4. **Danh tính và phê duyệt:** tên Sponsor giữ chỗ hoặc câu “đã phê duyệt” trong tài liệu cũ không chứng minh Sponsor đã ký bản D21/D22 mới.
5. **Môi trường:** CI chạy đạt không tự chứng minh có Staging/Prod, backup ngoại vi, máy in hoặc scanner đã bàn giao. Quyết định triển khai và nghiệm thu cần bằng chứng tương ứng.

## 4. Bàn giao để review

Kiên bàn giao D21/D22 và bằng chứng kỹ thuật cho An/Thái đối chiếu WBS, ngân sách, cấu hình và quyền vận hành. Huy xác nhận phạm vi/định danh thiết bị; Phát kiểm tra tiêu chí kiểm nhận và khả năng tái lập. PM tổ chức gate và thu chữ ký thật theo [D132](D132-ho-so-kien-cho-cong-khoi-dong.md).

Các kết quả kỹ thuật tháng 11 hỗ trợ đánh giá phiên bản hiện tại. Chúng không được dùng để ghi rằng pipeline hoặc buổi review đã diễn ra trong giai đoạn 05–18/09/2026.
