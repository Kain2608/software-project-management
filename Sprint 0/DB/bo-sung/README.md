# Hợp đồng bàn giao DB bổ sung Sprint 0

Các đầu ra này bổ sung trực tiếp thiết kế DB của Trần Trung Kiên, đối chiếu tài liệu BA/PM/Tester/AI/Fullstack. Không thay tài liệu hoặc chữ ký của các chủ trì khác.

| Nội dung | Bàn giao |
|---|---|
| Kiến trúc/bảo mật | [Căn cứ D50/D54](D50-D54-bo-sung-can-cu-va-kiem-soat.md), [D54](../D54-bao-mat-phan-quyen.md) theo D34 và role hiện hành |
| Mô hình và truy vết | [D51](../D51-thiet-ke-co-so-du-lieu.md), [CSV yêu cầu–bảng–test](D51-truy-vet-yeu-cau-DB.csv) |
| Luồng BA | [Yêu cầu và luồng dữ liệu](S0-14-15-ban-giao-yeu-cau-va-luong-du-lieu.md) đối chiếu D34/D35/D40–D42 |
| API/AI/tích hợp | [Hợp đồng SQL](S0-19-22-23-hop-dong-API-AI-tich-hop.md), phân biệt snapshot S0/V002 với hiện hành V003…V005 |
| Dev | [Hướng dẫn DB D63–D65](D63-D65-huong-dan-DB-cho-Dev.md) và runbook Sprint2 |
| M1 | [Phần bàn giao D126](D126-bo-sung-bang-chung-va-diem-mo.md), không thay quyết định gate PM |

Giữ SQL/DBML/ERD baseline S0 37 bảng và snapshot V002 39 bảng. Hiện hành V003…V005 có41 bảng, raw/case/Luhn/multi-IMEI, lease/PENDING và vai trò BA; artifact mới ở database/sprint2. Ngày triển khai/kiểm chứng thực tế17/11/2026 không được hồi tố thành ngày ký trong kế hoạch tháng9. Chỉ dùng summary/log có hash nguồn khớp; test DB không thay HTTP/UI/QA hoặc deployment.
