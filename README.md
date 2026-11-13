# SASMS — Quản lý siêu thị điện máy thông minh

Nhánh làm việc hiện hành **`DB`** chứa phần Architect / DBA của Kiên cho tiền Sprint, Sprint 0, Sprint 1 và Sprint 2. Nhãn `Kien` trong các hồ sơ trước là provenance của lần tạo bản gốc. Các giả định nghiệp vụ, kết quả chạy thật và phần chờ BA/PM ký được ghi riêng trong hồ sơ bàn giao.

**Tiền Sprint:** KD-10 — kế hoạch thiết bị D21, môi trường và CI/CD D22; đóng góp kỹ thuật cho KD-02/04/09.

- [Bộ bàn giao tiền Sprint](Ti%E1%BB%81n%20Sprint/DB/README.md)
- [D21 — Kế hoạch thiết bị](Ti%E1%BB%81n%20Sprint/DB/D21-ke-hoach-mua-sam-thiet-bi.md) · [Dự toán Excel](Ti%E1%BB%81n%20Sprint/DB/D21-du-toan-thiet-bi.xlsx)
- [D22 — Môi trường và CI/CD](Ti%E1%BB%81n%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md) · [Runbook sao lưu/khôi phục](infra/README.md)
- [Bằng chứng kỹ thuật tiền Sprint](Ti%E1%BB%81n%20Sprint/DB/evidence)

**Sprint 2:** khóa chọn/giải phóng Serial POS, tồn theo kho/cửa hàng, quyền function và hai kết nối tranh Serial. CI kiểm DB và báo readiness; Staging thật còn chờ triển khai.

- [Bộ bàn giao DB Sprint 2](Sprint%202/DB/README.md) · [Đối chiếu sprint trước và hồ sơ nhóm](Sprint%202/DB/RA-SOAT-SPRINT2.md)
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
