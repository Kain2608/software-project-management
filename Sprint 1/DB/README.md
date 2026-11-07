# Sprint 1 — Phần DB của Trần Trung Kiên

Nhánh thực hiện: **`Kien`**. Kỳ Sprint trong phân công: **27/09–10/10/2026**. Ngày thực hiện bộ bàn giao này: **07/11/2026**; ngày Sprint không được dùng thay ngày ký hoặc ngày chạy kiểm chứng thực tế.

Phần DB gồm nâng cấp từ baseline Sprint 0, danh mục/SKU, định danh Serial/IMEI, nhập kho theo lô và users/roles. Backend đăng nhập JWT, HTTP CRUD, giao diện và quét camera vẫn thuộc đầu việc ứng dụng tương ứng.

| Đầu việc chính của Kiên | Nội dung | Minh chứng yêu cầu trong workbook |
|---|---|---|
| S1-03 / TSK-01-01 — 16 giờ | DB Schema danh mục điện máy và SKU | PR migration được merge; kết quả chạy Staging |
| S1-06 / TSK-01-04 — 20 giờ | Bảng Serial/IMEI và ràng buộc duy nhất | PR được merge; hai người nhập đồng thời không tạo trùng |
| S1-10 / TSK-01-08 — 8 giờ | Bảng users/roles, nạp 9 vai trò cho EN-01 | PR được merge; script nạp vai trò |

Nguồn đối chiếu: [bản sao nguyên vẹn workbook](inputs/phan-cong-sprint-1.xlsx), sheet “Theo người”, dòng 20–25; sheet “Phân chia công việc”, dòng 7/9/10. Tình trạng cũ trong workbook chỉ là dữ liệu đầu vào, không xác nhận mã hoặc hồ sơ mới đã được nghiệm thu.

SHA-256 bản sao workbook: `806a36a064520472ef9efb71fb8dcd318a1e7f304eea337e22c22a962d6b9576`.

## Bộ bàn giao

- [DS1 — Thiết kế và bàn giao DB](DS1-thiet-ke-va-ban-giao-DB.md): thay đổi dữ liệu, hợp đồng SQL/API và truy vết nhiệm vụ.
- [D35 — Hợp đồng quy tắc định danh](D35-hop-dong-quy-tac-dinh-danh.md): quy tắc kỹ thuật hiện dùng, cơ chế nạp quy tắc đã được duyệt và phần cần BA xác nhận.
- [D127 — Hồ sơ DB cho review M2](D127-ho-so-review-DB-M2.md): checklist bằng chứng, kết quả kỹ thuật và phần chờ người có thẩm quyền.
- [Migration](../../database/migrations) và [runner](../../scripts/migrate_db.py): nâng cấp có thứ tự, checksum và ledger riêng; không dựng lại schema bằng cách xóa dữ liệu.
- [DBML Sprint 1](../../database/sprint1/schema.dbml), [ERD](../../database/sprint1/erd.mmd), [từ điển dữ liệu](../../database/sprint1/data-dictionary.md): mô hình sau nâng cấp, tách khỏi artifact Sprint 0.
- [Cấp quyền runtime](../../database/sprint1/runtime-roles.sql) và [demo DB](../../database/sprint1/demo.sql): cấu hình group role sau nâng cấp và chạy thử dữ liệu mô phỏng trong transaction rollback.
- [Bộ kiểm chứng Sprint 1](../../scripts/validate_sprint1.py) và [evidence](../../database/evidence/sprint1): kiểm tra trên PostgreSQL 16 và log thực tế.

Baseline [D51 Sprint 0](../../Sprint%200/DB/D51-thiet-ke-co-so-du-lieu.md), [D54 Sprint 0](../../Sprint%200/DB/D54-bao-mat-phan-quyen.md), schema và evidence cũ được giữ làm mốc nâng cấp. Bộ mới bổ sung migration thay vì sửa lại lịch sử đã chạy.

## Phần hỗ trợ các đầu việc khác

Theo “Theo người”, dòng 25, Kiên hỗ trợ đúng tám đầu việc:

| Mã / người làm chính | Đóng góp DB bàn giao |
|---|---|
| S1-02 — Huy | D35: cấu hình quy tắc có nguồn/phê duyệt; không đoán định dạng theo tên hãng |
| S1-04 — Thái | Ràng buộc catalog, optimistic revision và hợp đồng hàm tạo/cập nhật sản phẩm |
| S1-14 — An | Quy ước token QR và dữ liệu cần đọc; không thay chứng từ mua/nhận thiết bị |
| S1-13 — Vũ | Danh mục/thông số được phép đọc cho AI; không cấp quyền ghi kho hoặc gửi PII/secret vào prompt |
| S1-07 — Thái | Nhập kho JSON theo lô, rollback toàn lô và request key có đối chiếu payload |
| S1-11 — Thái | Users/roles và kiểm tra quyền theo actor/cửa hàng; xác thực JWT vẫn ở backend |
| S1-08 — Thái | Tra thiết bị bằng token QR, Serial/IMEI; số đo DB không thay video hoặc độ trễ camera |
| S1-16 — Huy | Cung cấp schema, quy tắc tiền/giữ hàng và các điểm mở cho refinement Sprint 2 |

## Chạy và kiểm chứng

Thực hiện theo [hướng dẫn DB](../../database/README.md), trên database thử riêng. Có thể xem tham số runner bằng `python scripts/migrate_db.py --help` và `python scripts/validate_sprint1.py --help`. Mỗi migration đã áp dụng phải khớp checksum; chạy lại không áp dụng lần hai. Không sửa file migration đã áp dụng hoặc dùng schema initial để ghi đè database đang có.

Lần kiểm chứng **07/11/2026, 11:57:57–11:58:12 (UTC+7)** đã đạt trên **PostgreSQL 16.15**: baseline 56 assertions nghiệp vụ + 7 assertions quyền, nâng cấp giữ 112 dòng của 37 bảng cũ và **76 assertions Sprint 1**. Hai migration chạy lại no-op; sửa checksum bị từ chối; lỗi migration chờ áp dụng rollback toàn bộ thay đổi và ledger. Cả ba ca hai phiên nhập đồng thời đạt: cùng Serial hoặc IMEI chỉ một nhập thành công; cùng request key trả cùng ID và chỉ có một batch. Fixtures được cleanup và nội dung 39 bảng khớp snapshot trước test.

[summary.json](../../database/evidence/sprint1/summary.json) lưu hash nguồn/kết quả; [validation.log](../../database/evidence/sprint1/validation.log) lưu đầu ra thực tế. Provision runtime và demo DB đã đạt; [kiểm tra parser](../../database/evidence/sprint1/artifacts-validation.json) xác nhận DBML 39 bảng/67 FK và ERD hợp lệ.

Kiểm tra quyền trong hàm Sprint 1 hiện áp dụng cho **catalog và nhập kho**. Việc gia cố các hàm POS kế thừa không thuộc increment này; backend vẫn phải xác thực JWT và kiểm quyền cho mọi endpoint. Local test không chứng minh đã chạy trên Staging, PR đã merge, HTTP/JWT/E2E đã đạt hoặc PO đã ký. Các điểm thiếu nguồn D34/FRS_V2, D35 theo từng hãng và danh sách 9 business role được ghi trong hồ sơ để BA/PM xác nhận.
