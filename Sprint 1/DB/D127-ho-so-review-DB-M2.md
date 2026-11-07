# D127 — Hồ sơ phần DB cho review M2

| Thuộc tính | Nội dung |
|---|---|
| Phụ trách phần hồ sơ | Trần Trung Kiên — Architect / DBA |
| Sprint / mốc trong nguồn | Sprint 1, 27/09–10/10/2026; M2 dự kiến 10/10/2026 |
| Ngày chuẩn bị hồ sơ | 07/11/2026 |
| Nhánh | `Kien` |
| Trạng thái | Hồ sơ kỹ thuật để review; chưa phải quyết định thông qua M2 của PM/PO |

## 1. Phạm vi và căn cứ

[Workbook Sprint 1](inputs/phan-cong-sprint-1.xlsx), sheet “Theo người” dòng 22–24, giao Kiên ba đầu việc chính S1-03/S1-06/S1-10; dòng 25 liệt kê tám đầu việc hỗ trợ. M2 trong sheet “Checklist”, dòng 17, yêu cầu Serial nhập chuẩn và QR dưới một giây. Bộ hồ sơ này tập hợp phần DB để người có thẩm quyền review cùng ứng dụng và kết quả QA.

## 2. Truy vết bằng chứng

| Yêu cầu / đầu việc | Artifact bàn giao | Bằng chứng kỹ thuật cần đối chiếu | Phần cần người khác xác nhận |
|---|---|---|---|
| S1-03 danh mục/SKU | [V001 catalog/IAM](../../database/migrations/V001__catalog_and_iam.sql), [DS1](DS1-thiet-ke-va-ban-giao-DB.md) | FK/UNIQUE/CHECK, chuẩn hóa SKU, revision, audit; upgrade giữ dữ liệu cũ | PR/merge và chạy Staging; Thái review API tích hợp |
| S1-06 Serial/IMEI | [Migrations](../../database/migrations), [D35 contract](D35-hop-dong-quy-tac-dinh-danh.md) | Chống trùng giữa phiên, trong lô; rollback nguyên tử; request key đúng payload | Huy/kho xác nhận định dạng hãng và mức đủ M2 |
| S1-10 users/roles | [V001](../../database/migrations/V001__catalog_and_iam.sql), [DS1 mục 4](DS1-thiet-ke-va-ban-giao-DB.md#4-usersroles--s1-10-và-hỗ-trợ-en-01) | 9 vai trò/17 permission; active user; global/cửa hàng; không tự cấp quyền | BA/PM xác nhận danh sách role; Thái kiểm JWT/session/endpoint |
| Nâng cấp/retry | [migrate_db.py](../../scripts/migrate_db.py) | Ledger/checksum; apply lần hai no-op; thay file cũ bị từ chối; lỗi không commit dở | DBA/Dev Lead xác nhận quy trình phát hành |
| Kiểm chứng PostgreSQL | [validate_sprint1.py](../../scripts/validate_sprint1.py), [evidence](../../database/evidence/sprint1) | Phiên bản, lệnh, source hash và kết quả thực tế | QA review độc lập và tích hợp vào D75 |
| M2 toàn hệ thống | [DS1](DS1-thiet-ke-va-ban-giao-DB.md), ứng dụng/QA do chủ sở hữu cung cấp | DB hỗ trợ dữ liệu chính xác và lookup | Video QR iOS/Android, số đo <1s, E2E, PO/PM ký gate |

Đường dẫn script không tự chứng minh đã chạy. Chỉ ghi đạt theo log thực tế cùng nguồn đã được hash; local test không đổi thành deployment Staging hoặc PR đã merge.

## 3. Phiếu kết quả thực tế

| Mục | Kết quả cần đối chiếu |
|---|---|
| PostgreSQL / môi trường | 16.15, Windows x64, database kiểm thử cô lập; phạm vi DB, không có claim HTTP/camera/provider |
| Hash nguồn và thời gian chạy | 07/11/2026, 11:57:57–11:58:12 (UTC+7); timestamp UTC và SHA-256 tại [summary.json](../../database/evidence/sprint1/summary.json) |
| Regression baseline | 56 assertions ràng buộc/lifecycle và 7 assertions ACL đạt trước nâng cấp |
| Nạp mới và nâng cấp dữ liệu có sẵn | Đạt: dựng baseline rồi nâng cấp khi có dữ liệu; giữ 112 dòng của 37 bảng cũ, gồm thiết bị nhập trước upgrade |
| Chạy migration lần hai / checksum | Hai migration áp dụng; reapply no-op; checksum bị đổi bị từ chối; ca lỗi làm rollback toàn bộ DDL và ledger của các migration chờ |
| Catalog, revision, IAM và nhập lô | 76 assertions Sprint 1 đạt, gồm tạo/sửa/soft delete, stale revision, active actor và scope |
| Hai phiên cùng Serial / cùng IMEI | Mỗi ca dùng hai PO độc lập: một thành công, một lỗi `23505`; đúng một device/batch/RECEIVE/audit/outbox commit |
| Hai phiên cùng request key | Cả hai thành công và nhận cùng ID; đúng một device và một batch |
| Thu hồi quyền / đường gọi riêng | Grant `SALES`/`ai.use` đã thu hồi trước upgrade vẫn vắng; custom caller không còn gọi được hàm nhập cũ không kiểm quyền |
| Runtime, demo và fixtures | [Provision runtime](../../database/sprint1/runtime-roles.sql) và [demo DB](../../database/sprint1/demo.sql) đạt; dọn fixtures theo ID riêng, snapshot toàn bộ 39 bảng khớp trước test |
| Mô hình sinh | Parser DBML/ERD đạt, 39 bảng/67 FK; [log parser](../../database/evidence/sprint1/artifacts-validation.json) lưu hash/phiên bản công cụ |
| Staging / PR / mobile QR | Chưa có bằng chứng triển khai, merge hoặc camera trong hồ sơ DB |
| Người review dự án | Chờ Kiên/Thái/Huy/QA/PM xác nhận theo vai trò |

[validation.log](../../database/evidence/sprint1/validation.log) lưu đầu ra thực tế tương ứng. Các kết quả do công cụ chạy không thay review độc lập hoặc chữ ký dự án. Helper/quyền trong hàm được áp dụng cho catalog và nhập kho của Sprint 1; các hàm POS kế thừa chưa được gia cố authorization trong increment này. Backend vẫn chịu trách nhiệm xác thực JWT và kiểm quyền/scope cho mọi endpoint.

## 4. Các điểm mở trước khi ký

| Mã | Nội dung cần chốt | Chủ sở hữu cần xác nhận |
|---|---|---|
| M2-DB01 | D34/FRS_V2 và tiêu chí chấp nhận chính thức cho US-01..03/EN-01 chưa có trong repo | Huy / PO |
| M2-DB02 | Quy tắc hãng D35 có nguồn/xác nhận chưa có; fallback chỉ là kiểm tra kỹ thuật | Huy, đại diện kho, PM |
| M2-DB03 | Tên/quyền của 9 business role là phương án đề xuất; global catalog cần assignment global | Huy, Kiên, PM |
| M2-DB04 | Actor JWT phải được backend xác thực; không tin actor hoặc scope từ request body | Thái |
| M2-DB05 | Chưa có chứng cứ Staging/PR merge cho phiên bản mới | Kiên, Thái, người quản trị môi trường |
| M2-DB06 | Test DB không thay báo cáo 35 case kho/QR, E2E hoặc số đo camera <1s | Phát, Thái, Huy |
| M2-DB07 | Phương án scanner/printer, chứng từ mua và biên bản nhận thiết bị thuộc S1-14 | An |

## 5. Review và ký duyệt

| Nội dung | Người nhận / review | Trạng thái |
|---|---|---|
| SQL, migration, integrity và evidence DB | Kiên, Thái | Chờ review dự án |
| D35 và quy tắc nghiệp vụ | Huy, đại diện kho | Chờ nguồn/quyết định |
| Test tích hợp và D75 | Phát, Thái | Chờ kiểm chứng ứng dụng |
| Nghiệm thu story và M2 | Huy / PO, PM và các vai trò theo phân công | Chưa có biên bản hoặc chữ ký cho bản này |

PM bổ sung ngày/hình thức review, người tham dự thực tế, phiên bản artifact, ý kiến, lỗi cần sửa, quyết định và chữ ký sau buổi review. Hồ sơ không tự ghi có cuộc họp, PO nghiệm thu hoặc M2 đã qua khi chưa có bằng chứng.
