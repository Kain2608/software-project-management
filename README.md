# SASMS — Quản lý siêu thị điện máy thông minh

DB hiện hành theo D34/D35 BA: **41 bảng, V001…V007**, Serial raw/phân biệt hoa-thường, IMEI phụ/Luhn, giữ hàng POS10m/ONLINE30m và chín role canonical. [Chạy nâng cấp/kiểm chứng](database/sprint3/README.md), [hợp đồng DB Sprint 3](Sprint%203/DB/DS3-thiet-ke-va-ban-giao-DB.md). Snapshot S0/S1/S2 giữ nguyên để truy vết.


Phần Architect / DBA của Kiên cho Tiền Sprint, Sprint 0, Sprint 1 và Sprint 2 đã được tích hợp vào `main`; `DB` là nhánh phát triển phần DB. Nhãn `Kien` trong các hồ sơ trước ghi nguồn gốc của bản ban đầu. Lượt rà soát tài liệu **20/11/2026** phân biệt kết quả kiểm kỹ thuật đã lưu, giới hạn tích hợp và phần chờ BA/PM/QA xác nhận.

Các quy tắc DB hiện hành theo D34/D35 được mô tả trong [D35 DB](Sprint%201/DB/D35-hop-dong-quy-tac-dinh-danh.md) và [DS2 DB](Sprint%202/DB/DS2-thiet-ke-va-ban-giao-DB.md). Một số quyết định trong D53 Fullstack còn dùng quy tắc V002 cũ; không lấy nhãn APPROVED ở đó làm căn cứ thay thế V003…V005 hoặc chữ ký nghiệp vụ. Mô hình hiện tại phục vụ hàng có Serial; phụ kiện/dịch vụ không Serial cần BA/Fullstack chốt hợp đồng riêng trước khi tích hợp.

**Tiền Sprint:** KD-10 — kế hoạch thiết bị D21, môi trường và CI/CD D22; đóng góp kỹ thuật cho KD-02/04/09.

- [Bộ bàn giao tiền Sprint](Ti%E1%BB%81n%20Sprint/DB/README.md)
- [D21 — Kế hoạch thiết bị](Ti%E1%BB%81n%20Sprint/DB/D21-ke-hoach-mua-sam-thiet-bi.md) · [Dự toán Excel](Ti%E1%BB%81n%20Sprint/DB/D21-du-toan-thiet-bi.xlsx)
- [D22 — Môi trường và CI/CD](Ti%E1%BB%81n%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md) · [Runbook sao lưu/khôi phục](infra/README.md)
- [Bằng chứng kỹ thuật tiền Sprint](Ti%E1%BB%81n%20Sprint/DB/evidence)

**Sprint 3:** adapter/worker SMS, l?ch k? thu?t vi?n v?i ??m 30 ph?t, k?ch ho?t b?o h?nh WAR-12 ch?ng l?p v? b?o c?o b?o m?t D81. [B? b?n giao DB](Sprint%203/DB/README.md) ? [N?ng c?p v? ki?m th?](database/sprint3/README.md). SMS th?t, Web/QR E2E v? nghi?m thu c?n b?ng ch?ng t?ch h?p ri?ng.

**Sprint 3:** adapter/worker SMS, lịch kỹ thuật viên với đệm 30 phút, kích hoạt bảo hành WAR-12 chống lặp và báo cáo bảo mật D81. [Bộ bàn giao DB](Sprint%203/DB/README.md) · [Nâng cấp và kiểm thử](database/sprint3/README.md). Theo lựa chọn của người dùng, SMS dùng mô phỏng cho lần bàn giao này.

**Sprint 2:** khóa chọn/giải phóng Serial POS, tồn theo kho/cửa hàng, quyền function và hai kết nối tranh Serial. CI kiểm DB và báo readiness; Staging thật còn chờ triển khai.

- [Bộ bàn giao DB Sprint 2](Sprint%202/DB/README.md) · [Đối chiếu sprint trước và hồ sơ nhóm](Sprint%202/DB/DS2-thiet-ke-va-ban-giao-DB.md)
- [Chạy nâng cấp, kiểm thử và demo Sprint 2](database/sprint2/README.md)

**Sprint 1:** migration danh mục/SKU, Serial/IMEI, nhập kho theo lô có chống xử lý lặp và kiểm quyền user/role theo cửa hàng.

- [Bộ bàn giao DB Sprint 1](Sprint%201/DB/README.md)
- [Nâng cấp DB, kiểm thử và demo SQL](database/sprint1/README.md)
- [Migration có phiên bản](database/migrations) · [DBML sau nâng cấp](database/sprint1/schema.dbml) · [ERD](database/sprint1/erd.mmd)
- [Log kiểm chứng Sprint 1](database/evidence/sprint1/validation.log)

**Sprint 0:** kiến trúc D50, thiết kế DB D51, bảo mật D54, SQL/DBML D57 và hồ sơ review M1. Baseline 37 bảng và bằng chứng cũ được giữ để đối chiếu.

- [Bộ bàn giao Sprint 0 của Kiên](Sprint%200/DB/README.md)
- [Đối chiếu và bổ sung phần còn thiếu Sprint 0](Sprint%200/DB/bo-sung/README.md)
- [Thiết kế cơ sở dữ liệu và ERD](Sprint%200/DB/D51-thiet-ke-co-so-du-lieu.md)
- [Chạy PostgreSQL và kiểm tra DB](database/README.md)
- [SQL schema 37 bảng](database/schema.sql) · [DBML](database/schema.dbml) · [ERD Mermaid](database/erd.mmd)
- [Log kiểm chứng PostgreSQL](database/evidence/validation.log)

Tài liệu nguồn PM/BA được giữ trong `Tiền Sprint/PM`, `Tiền Sprint/BA`, `Sprint 0/PM` và `Sprint 0/BA`. Bộ DB không thay thế phần ứng dụng/API của Dev hoặc việc phê duyệt của BA/PM/Sponsor.
