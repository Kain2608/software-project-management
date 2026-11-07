# DS1 — Thiết kế và bàn giao DB Sprint 1

| Thuộc tính | Nội dung |
|---|---|
| Phụ trách | Trần Trung Kiên — Architect / DBA |
| Đầu việc | S1-03, S1-06, S1-10; hỗ trợ tám đầu việc theo workbook |
| Phiên bản / ngày thực hiện | 1.0 / 07/11/2026 |
| Nhánh / baseline đầu vào | `Kien`; phần DB Sprint 0 đã có trong repo |
| Phạm vi nghiệm thu | Increment DB của DS1; chờ review kỹ thuật/nghiệp vụ, không đại diện nghiệm thu toàn ứng dụng |

## 1. Căn cứ và phần thay đổi

[Workbook Sprint 1](inputs/phan-cong-sprint-1.xlsx), sheet “Theo người” dòng 22–24, giao Kiên danh mục/SKU, định danh thiết bị duy nhất và nạp users/roles. Mục tiêu chung của Sprint gồm US-01, US-02, US-03 và EN-01; việc hoàn thành DB không tự chứng minh toàn bộ các story đã đạt.

Baseline [D51](../../Sprint%200/DB/D51-thiet-ke-co-so-du-lieu.md) đã có 37 bảng, Serial/IMEI UNIQUE và 9 vai trò đề xuất. Sprint 1 bổ sung migration nâng cấp: quy tắc catalog có thể dùng bởi API, kiểm tra quyền theo user đang hoạt động/phạm vi cửa hàng, quy tắc định danh được quản lý và giao dịch nhập kho theo lô có chống xử lý lặp.

| Phần dữ liệu | Thay đổi và mục đích |
|---|---|
| `products` | Chuẩn hóa SKU, tên, thông số; `revision` và `updated_at` phục vụ cập nhật không ghi đè thay đổi của người khác |
| `users`, `roles`, `permissions`, các bảng gán quyền | Giữ mô hình hiện có; nạp snapshot 9 vai trò/17 permission; kiểm tra active actor và đúng scope |
| `devices` | Giữ khóa duy nhất toàn chuỗi cho Serial, IMEI và token QR; nhập qua đường giao dịch có kiểm tra quyền/quy tắc |
| `identifier_rules` | Lưu quy tắc định danh cùng nguồn và bằng chứng phê duyệt; không tự tạo regex Sony/Samsung/LG/Apple |
| `inventory_import_batches` | Lưu request key, payload đã chuẩn hóa và kết quả nhập để retry không nhân thiết bị |
| Metadata migration riêng | Lưu phiên bản/checksum; không tính vào bảng nghiệp vụ và không trộn với chứng từ kho |

Hai bảng nghiệp vụ bổ sung đưa mô hình nghiệp vụ từ 37 lên 39 bảng; ledger `sasms_meta.schema_migrations` nằm riêng. Tên cột, ràng buộc và quyền thực thi chính thức tra trong [V001](../../database/migrations/V001__catalog_and_iam.sql) và [V002](../../database/migrations/V002__inventory_batches_and_identifiers.sql). [DBML](../../database/sprint1/schema.dbml), [ERD](../../database/sprint1/erd.mmd) và [từ điển dữ liệu Sprint 1](../../database/sprint1/data-dictionary.md) mô tả phiên bản sau nâng cấp. Schema và evidence Sprint 0 giữ nguyên cho việc đối chiếu.

## 2. Danh mục và SKU — S1-03

SKU được bỏ khoảng trắng hai đầu, chuyển chữ hoa, không chứa khoảng trắng bên trong và dài tối đa 64 ký tự. SKU duy nhất theo toàn danh mục; mã mẫu chỉ là dữ liệu demo, không quy định mã của nhà cung cấp. Tên sản phẩm được trim, không rỗng, tối đa 200 ký tự. Thông số phải là JSON object và không vượt 64KiB; trường thông số riêng từng ngành hàng vẫn cần BA chốt.

Giá tiếp tục dùng `numeric`, không âm; thời hạn bảo hành giữ giới hạn của baseline. Brand/category phải tồn tại bằng FK. API dùng soft delete (`is_active=false`) để giữ lịch sử Serial và chứng từ đã tham chiếu sản phẩm. Xóa cứng sản phẩm đang được tham chiếu không phải thao tác được cung cấp cho runtime.

| Hàm SQL | Kết quả / kiểm soát |
|---|---|
| `create_product(actor, sku, brand, category, name, specifications, price, warranty_months) → uuid` | Yêu cầu `catalog.write` toàn hệ thống; tạo revision 0 và audit cùng transaction |
| `update_product(actor, product, expected_revision, name, specifications, price, warranty_months, active) → integer` | Khóa hàng, so revision; cập nhật thành công trả revision mới; không đổi SKU/brand/category qua hợp đồng này |

Sai revision trả SQLSTATE `40001`; API nên báo xung đột phiên bản và tải lại dữ liệu trước khi người dùng quyết định, không tự ghi đè bằng dữ liệu cũ. Sản phẩm không tồn tại trả `P0002`. Lỗi CHECK/FK/UNIQUE cần ánh xạ sang lỗi API có ý nghĩa, không lộ câu SQL hoặc stack trace.

Catalog là dữ liệu dùng chung toàn chuỗi. Vì vậy người chỉ có `catalog.write` trong một cửa hàng không tự được sửa catalog chung; chức năng này đòi assignment global. Nếu BA muốn giá riêng từng cửa hàng, cần mô hình giá theo cửa hàng và quyền tương ứng trước khi mở chức năng.

## 3. Serial/IMEI và nhập lô — S1-06

`devices.serial`, `devices.imei` và `devices.qr_token` tiếp tục được bảo vệ bằng unique index. CHECK/đường nhập dùng Serial canonical; FK ghép giữ đúng sản phẩm của dòng phiếu nhận. Quy tắc kỹ thuật hiện có không được gắn nhãn là định dạng hãng đã được trưởng kho xác nhận; xem [hợp đồng D35](D35-hop-dong-quy-tac-dinh-danh.md).

Hợp đồng chính: `receive_device_batch(receipt_item uuid, request_key uuid, devices jsonb, actor uuid) → uuid[]`. Payload là mảng có **1–500** object `{ "serial": "...", "imei": "..." }`; IMEI có thể NULL khi quy tắc áp dụng cho phép. Backend truyền một request key ổn định cho cùng ý định nhập và truyền actor từ phiên đã xác thực. Kết quả giữ thứ tự phần tử đầu vào.

Trong một transaction, hàm xác định kho/cửa hàng từ phiếu, kiểm tra actor có `inventory.receive`, kiểm tra payload và quy tắc, khóa các hàng nghiệp vụ cần thiết, so định mức phiếu/PO và tạo toàn bộ thiết bị cùng biến động/audit/outbox. Một phần tử không hợp lệ làm rollback toàn lô; không trả thành công cho các phần tử đã chèn trước lỗi.

Cùng request key, actor, dòng phiếu nhận và payload canonical có cùng thứ tự trả lại dãy ID cũ; thay một trong các thành phần này bị từ chối. Request key là duy nhất toàn bảng nhập, không riêng từng user hoặc phiếu. Backend không được tạo key mới cho mỗi lần retry mạng rồi suy diễn thiết bị trùng là yêu cầu mới. Gọi lại `receive_device` một thiết bị vẫn giữ chữ ký baseline nhưng cũng phải qua kiểm tra quyền Sprint 1; dùng hàm batch khi cần retry theo request key.

UNIQUE xử lý tranh chấp thực tế giữa các phiên nhập; kiểm tra “chưa tồn tại” từ UI trước khi chèn không đủ để chống trùng. API ánh xạ `23505` thành xung đột định danh; `23514` thành dữ liệu/quy tắc không hợp lệ và `42501` thành không đủ quyền. Không tiếp tục commit một phần lô sau lỗi DB.

## 4. Users/roles — S1-10 và hỗ trợ EN-01

Chín vai trò giữ theo [D54](../../Sprint%200/DB/D54-bao-mat-phan-quyen.md): `ADMIN`, `STORE_MANAGER`, `WAREHOUSE`, `CASHIER`, `SALES`, `DISPATCHER`, `TECHNICIAN`, `CUSTOMER_SERVICE`, `ANALYST`. Đây vẫn là danh sách đề xuất do nguồn chỉ yêu cầu số lượng chín, chưa có danh sách nghiệp vụ được BA/PM ký.

Migration nạp snapshot role/permission theo allowlist tường minh. Chỉ role chưa tồn tại trước migration nhận các grant mặc định; grant của role đã có, kể cả quyền đã được chủ ý thu hồi, được giữ khi nâng cấp lần đầu. Chạy lại migration đã ghi nhận sẽ bỏ qua phần seed, không tự khôi phục quyền đã thu hồi sau đó. Không tự gán role cho tài khoản demo, không tạo mật khẩu công khai, không tự bật user chưa được cấp quyền. Thay đổi grant tiếp theo phải qua quy trình quản trị/migration được review.

| Hàm | Ý nghĩa |
|---|---|
| `has_permission(actor uuid, permission text, store uuid DEFAULT NULL) → boolean` | User phải active; đúng permission; cửa hàng cụ thể phải active và có assignment cùng cửa hàng hoặc assignment global |
| `assert_permission(actor uuid, permission text, store uuid DEFAULT NULL) → void` | Cùng quy tắc; thiếu quyền trả `42501` |

Khi tham số store là NULL, chỉ assignment global được xét. Role/permission không tồn tại, user inactive hoặc không có assignment hợp lệ đều không được cấp quyền mặc định. Runtime không được trực tiếp ghi role/grant để tự nâng quyền.

`auth_subject` là định danh opaque liên kết tài khoản từ cơ chế xác thực ứng dụng; không chứa password, JWT hay secret. Backend phải xác minh issuer/audience/signature, ánh xạ subject theo namespace an toàn và lấy actor từ phiên đó. SQL kiểm tra active/role/scope bổ sung bảo vệ ở đường hàm, không tự xác minh JWT và không thay thế Spring Security.

`sasms_app` vẫn là credential của backend tin cậy, không phát cho trình duyệt. Không gọi `has_permission` từ client với một actor tùy chọn rồi xem kết quả là chứng minh danh tính. Kiểm tra quyền mới hiện gắn vào catalog/nhập kho; gia cố các hàm POS kế thừa là công việc ngoài increment Sprint 1. Quyền đọc rộng sẵn có và các hàm ngoài phạm vi này vẫn cần service kiểm quyền/lọc đối tượng; không tuyên bố RLS hoặc authorization toàn hệ thống đã được triển khai. [Script runtime Sprint 1](../../database/sprint1/runtime-roles.sql) cấp đường gọi được phép và thu hồi quyền gọi hàm nhập cũ không kiểm quyền.

## 5. Bàn giao tích hợp

| Đầu việc nhận | Hợp đồng và giới hạn |
|---|---|
| S1-04 CRUD API — Thái | Bind tham số SQL; truyền expected revision; xử lý soft delete/lỗi unique; kiểm quyền trước khi trả dữ liệu |
| S1-07 nhập lô — Thái | Giữ nguyên request key cho retry; trả kết quả sau COMMIT; không ghi thẳng `devices` |
| S1-11 đăng nhập/RBAC — Thái | Ánh xạ JWT → `users.id`; kiểm active/permission/scope trên request; tránh tin actor/store từ body |
| S1-08 QR — Thái | Token đề xuất `SASMS:DEVICE:<qr_token>`; parse/validate trước lookup; không chứa PII hoặc credential |
| S1-13 AI — Vũ | Đọc catalog đã lọc; không gửi PII/secret vào LLM; không để AI ghi giá, kho hoặc quyền |
| S1-14 thiết bị — An | Xác nhận scanner đọc được token/Serial và giữ nguyên ký tự; model/driver/biên bản mua thuộc hồ sơ thiết bị |
| S1-02, S1-16 — Huy | Chốt D35, phạm vi uniqueness/IMEI, chín role và các điểm chưa đủ DoR của Sprint 2 |

Độ trễ lookup DB và test nhập đồng thời chỉ chứng minh phần DB. Yêu cầu QR dưới một giây cần video/số đo trên trình duyệt iOS/Android và API thực tế. Tương tự, test SQL không phải báo cáo Postman, E2E, login JWT hoặc endpoint AI 200.

## 6. Migration và triển khai

Nâng cấp qua [migrate_db.py](../../scripts/migrate_db.py), theo thứ tự file trong [migrations](../../database/migrations). Runner lưu checksum và từ chối file đã áp dụng bị sửa. Transaction và khóa migration ngăn hai tiến trình cùng áp dụng schema không nhất quán. Metadata migration tách khỏi bảng nghiệp vụ.

Trước Staging, backup database và lưu phiên bản đang chạy; kiểm tra dữ liệu catalog hiện có đáp ứng ràng buộc mới, actor/quyền của tích hợp đã được cấu hình và credential runtime không có quyền owner. Dữ liệu cũ không đáp ứng CHECK mới làm migration thất bại/rollback; không tự cắt hoặc sửa mã danh mục lịch sử. Chạy upgrade trên bản sao dữ liệu thử, test nghiệp vụ và kiểm đối soát. Staging/Prod chỉ được ghi đạt sau khi có deployment log tương ứng.

Nếu migration lỗi, rollback transaction thay đổi đang áp dụng; không xóa baseline hoặc dữ liệu nghiệp vụ. Sau khi migration đã commit, sửa bằng migration tiến tới đã review. Khôi phục backup phải theo kế hoạch riêng và đối soát payment/outbox; không tự tạo down migration xóa bảng để giả lập rollback an toàn.

## 7. Kiểm chứng và điểm chờ

Lần chạy cuối **07/11/2026, 11:57:57–11:58:12 (UTC+7)** trên PostgreSQL **16.15** đạt các kiểm tra sau; [summary.json](../../database/evidence/sprint1/summary.json) lưu timestamp UTC và SHA-256 nguồn, [validation.log](../../database/evidence/sprint1/validation.log) lưu đầu ra.

| Nhóm kiểm chứng | Kết quả thực tế |
|---|---|
| Regression trước nâng cấp | 56 assertions ràng buộc/giao dịch và 7 assertions ACL baseline đạt |
| Nâng cấp giữ dữ liệu | Giữ 112 dòng trên 37 bảng cũ, gồm thiết bị đã nhập trước upgrade; không phục hồi grant `SALES`/`ai.use` đã chủ ý thu hồi |
| Đường gọi riêng | Quyền gọi hàm nhập cũ không kiểm quyền bị thu hồi, kể cả custom caller đã có grant trước upgrade |
| Runner migration | Hai migration áp dụng; reapply no-op; file đã áp dụng bị đổi checksum bị từ chối; lỗi trong tập migration chờ làm rollback toàn bộ DDL và ledger của tập đó |
| Catalog, IAM, nhập lô | 76 assertions Sprint 1 đạt; gồm revision, active actor/scope, ràng buộc định danh, quy tắc được duyệt, atomic batch và quyền runtime |
| Hai phiên cùng Serial | Hai PO độc lập; một thành công, một lỗi unique `23505`; chỉ một device/batch/RECEIVE/audit/outbox commit |
| Hai phiên cùng IMEI | Hai PO độc lập; một thành công, một lỗi unique `23505`; chỉ một bộ bản ghi nghiệp vụ commit |
| Hai phiên cùng request key | Cả hai nhận thành công cùng ID, chỉ một device và một batch được tạo |
| Provision/demo/cleanup | Script runtime và demo DB đạt; fixtures được dọn theo ID riêng, nội dung 39 bảng khớp snapshot trước test |
| Artifacts | Parser DBML xác nhận 39 bảng/67 FK; ERD Mermaid hợp lệ; xem [evidence parser](../../database/evidence/sprint1/artifacts-validation.json) |

[Demo DB](../../database/sprint1/demo.sql) chạy bằng dữ liệu mô phỏng và rollback, không tạo login/JWT. [Hồ sơ M2](D127-ho-so-review-DB-M2.md) tập hợp các kết quả trên cho người review dự án.

D34/FRS_V2, quy tắc hãng D35 có nguồn xác nhận, danh sách role được duyệt, video QR, PR/merge, deployment Staging và chữ ký PO/PM vẫn cần người sở hữu cung cấp hoặc thực hiện. Không lấy nội dung “đã có/một phần” của workbook làm bằng chứng các việc đó đã diễn ra cho phiên bản mới.
