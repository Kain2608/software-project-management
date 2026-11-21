# Sprint 3 — DB / Architect, Trần Trung Kiên

Kỳ kế hoạch **25/10–07/11/2026**; thực hiện và kiểm chứng bổ sung ngày **21/11/2026**. Nguồn phân công là workbook người dùng cung cấp, sheet “Theo người” dòng 21–25; [bản trích 7 việc](inputs/phan-cong-sprint-3.csv) và [SHA-256 nguồn](inputs/source.json) giữ nguyên trạng thái lịch tại 05/11. Giờ kế hoạch của hai việc chính là **32 giờ**, không phải giờ thực tế đã ghi nhận.

| Việc | Vai trò Kiên | Phần bàn giao |
|---|---|---|
| S3-08 | Chính, 16 giờ kế hoạch | Adapter SMS, worker outbox, mock/HTTP, kiểm thử và [DS3 SMS](DS3-SMS-adapter-va-ban-giao.md) |
| S3-11 | Chính, 16 giờ kế hoạch | [D81 bảo mật](D81-bao-cao-ra-soat-bao-mat.md), sửa JWT và evidence |
| S3-02 / S3-05 | Hỗ trợ Huy / Thái | [Hợp đồng DB](DS3-thiet-ke-va-ban-giao-DB.md): ONLINE 30 phút, cùng nguồn tồn với POS, ranh giới actor/khách |
| S3-06 | Hỗ trợ Thái | Hàm lập lịch, khóa kỹ thuật viên và đệm 30 phút; kết quả công việc và thông báo |
| S3-07 | Hỗ trợ Phát | Kích hoạt bảo hành theo device, chống lặp, SMS tách transaction nghiệp vụ |
| S3-14 | Hỗ trợ Huy | [Đầu vào refinement Sprint 4](S3-14-dau-vao-refinement-sprint4.md) |

Mã nguồn ở [V006](../../database/migrations/V006__logistics_and_warranty.sql), [V007](../../database/migrations/V007__sms_outbox.sql), [SMS backend](../../backend/src/main/java/com/smartelectronicsupermarket/sms). DB giữ **41 bảng**, nâng cấp tới **V007**; không sửa V001…V005 hoặc snapshot Sprint 0/1/2.

Chạy lại theo [runbook DB](../../database/sprint3/README.md); [validator](../../scripts/validate_sprint3.py) ghi source hash, assertions, kết nối cạnh tranh và kiểm dọn fixture. Kết quả thực tế đọc tại [summary DB](../../database/evidence/sprint3/summary.json) và [summary bảo mật](evidence/security/summary.json). Workflow [Sprint 3](../../.github/workflows/sprint3.yml) chuẩn bị kiểm tra DB/backend trên GitHub; tệp workflow không tự chứng minh một lượt CI đã chạy.

Kết quả ngày 21/11/2026: **46 kiểm tra DB và ba ca đồng thời đạt**; bộ backend **263 ca đạt, 16 bỏ qua, 0 lỗi**, gồm ca Java → PostgreSQL → SMS MOCK; **7 ca frontend auth đạt**. DBML/ERD/từ điển khớp catalog 41 bảng. Regression backup/restore của snapshot cũ đạt **41 kiểm tra**; runner môi trường và Sprint 2 được cố định V005 để migration mới không làm sai snapshot lịch sử. Java local dùng JDK 24 với override; kiểm JDK 25 trong CI còn chờ. [Bảng kết quả tổng hợp](evidence/verification-summary.json).

Theo lựa chọn của người dùng ngày 21/11/2026, **SMS dùng mô phỏng để bàn giao trước**. Bộ này cung cấp MOCK và hợp đồng gateway HTTP chung; khi triển khai thật cần chọn provider/gói thử, ánh xạ API và lưu kết quả thử thật. Web/API giao lắp, quét QR kích hoạt, browser E2E, Staging, PR merge và chữ ký PO/PM là các bằng chứng tích hợp/nghiệm thu cần nhóm bổ sung. Không ghi các hạng mục này là đã hoàn thành chỉ từ test DB.
