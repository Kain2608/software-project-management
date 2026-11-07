# D51 — Bổ sung truy vết và rà soát thiết kế DB

| Thuộc tính | Nội dung |
|---|---|
| Đầu việc | S0-18; Trần Trung Kiên — Architect / DBA |
| Ngày bổ sung thực tế | 07/11/2026, trên nhánh `DB` |
| Mục đích | Hoàn thiện phần bàn giao/review còn thiếu của D51 được workbook ghi **MỘT PHẦN** |
| Trạng thái | Đã đối chiếu nguồn và bằng chứng kỹ thuật; chờ người phụ trách review/chốt nghiệp vụ |

## 1. Phần được giữ và phần được bổ sung

[D51 thiết kế](../D51-thiet-ke-co-so-du-lieu.md) đã có mô hình 37 bảng, giả định DB-A01–09, chuẩn hóa, giao dịch và điểm chờ duyệt. [Từ điển vật lý](../D51-tu-dien-du-lieu.md), [DBML](../../../database/schema.dbml), [ERD](../../../database/erd.mmd) và [D57](../D57-luoc-do-va-kiem-thu.md) đã có; giữ nguyên các tài liệu này cùng SQL, test và evidence. Không dựng lại baseline hoặc viết đè bản đã hoàn thiện.

Phần bổ sung này nối căn cứ nghiệp vụ với đối tượng DB, cơ chế bảo vệ và **đúng nhãn test đang có**; làm rõ cardinality, khoảng trống hậu bán, vòng đời dữ liệu và ranh giới Sprint 0/Sprint 1 để người review không suy ra chức năng hoàn chỉnh chỉ từ tên bảng. [CSV truy vết](D51-truy-vet-yeu-cau-DB.csv) là bản có thể lọc theo nguồn, bảng, kiểm chứng hoặc người chốt. Mã `DB-TRACE-*` chỉ là mã dòng trong tài liệu này, không phải mã FRS chính thức.

Nguồn [D05](../../PM/D05.docx) trong repo là **Scope Statement**, [D06](../../PM/D06.docx) là **Project Charter**; giữ tên nguồn dù workbook gọi D05 là Charter. Các số đoạn trong CSV tính trên tất cả phần tử `w:p` theo thứ tự trong `word/document.xml`, kể cả nội dung bảng; đó không phải số trang Word. D05 §1/§3 và D06 §2/§3 xác lập phạm vi/mục tiêu, chưa thay thế FRS chi tiết. [D33](../../BA/D33.docx), đoạn 5, 117 và 141, tự ghi đây là chủ đề khảo sát chưa có câu trả lời; KS41–46 là câu hỏi còn mở. Không dùng các câu hỏi đó làm yêu cầu đã được khách hàng xác nhận.

## 2. Ánh xạ đủ 37 bảng theo mức hiện thực

Danh sách tên **đủ 37 bảng** giữ tại [D51 §3](../D51-thiet-ke-co-so-du-lieu.md#3-ranh-giới-và-danh-mục-37-bảng); định nghĩa từng bảng giữ tại từ điển. Bảng sau bổ sung cách đọc phạm vi và kiểm chứng của toàn bộ tám nhóm, không tạo thêm bảng.

| Nhóm trong D51 | Số bảng | Điểm nối quan trọng | Mức kiểm chứng / giới hạn |
|---|---:|---|---|
| IAM | 6 | `user_roles` nối user–role–store; `role_permissions` nối role–permission | Seed 9 vai trò/17 quyền, FK và ACL SQL được kiểm; chưa có đăng nhập/JWT hay toàn bộ RBAC người dùng cuối |
| Danh mục | 6 | `products` là SKU; `warehouses` gắn cửa hàng; ảnh gắn SKU | Giá/ảnh và FK được kiểm; ảnh thật, API upload và phạm vi cửa hàng ở API chưa hiện thực |
| Nhập kho | 4 | PO → các phiếu nhận → dòng nhận → thiết bị | Hàm nhập kiểm PO duyệt, phiếu mở, số lượng và nguyên tử; chưa có API duyệt PO |
| Kho thiết bị | 4 | `devices` là đơn vị vật lý; biến động tham chiếu device | Nhận/giữ/bán/trả giữ đã kiểm; kiểm kê chỉ có bảng/CHECK, chưa có workflow điều chỉnh tồn |
| POS / CRM | 6 | Dòng đơn theo SKU → `order_devices`; payment/invoice theo đơn | Reserve/checkout/cancel/retry đã kiểm ở SQL; chưa có webhook, E2E hoặc hóa đơn điện tử phát hành thật |
| Logistics | 3 | Đơn → chuyến giao → device thuộc lựa chọn của đơn; phân công user | Có FK/CHECK vật lý; chưa có transaction giao/lắp đặt và test nghiệp vụ đầy đủ |
| Hậu bán | 5 | Device → warranty/policy/claim; đơn → return → device | Có mô hình và ràng buộc cơ bản; chưa có kích hoạt, duyệt/hoàn tiền hay đổi máy |
| AI / nền tảng | 3 | Gợi ý theo SKU; audit và outbox cùng transaction lõi | Atomic/retry audit–outbox đã kiểm; chưa có LLM/provider hoặc worker publish |
| **Tổng** | **37** | **37 PK và 63 FK trong nguồn SQL** | Không bao gồm hai bảng bổ sung của Sprint 1 |

## 3. Những điểm cần đọc đúng khi review ERD

1. **SKU khác thiết bị.** `products.sku` định danh mẫu hàng; `devices.serial` định danh từng máy và UNIQUE trên toàn bảng, kể cả khác SKU. Một SKU có 0..n device. Serial global, chuẩn hóa trim/uppercase và IMEI tùy chọn 15 số là lựa chọn DB-A01–03 cần Huy chốt với mẫu thật. D05 xác lập yêu cầu Serial unique, chưa tự xác nhận mọi chi tiết định dạng theo hãng hoặc phụ kiện bán theo số lượng.
2. **Header có thể chưa có dòng.** SQL cho phép PO/receipt/order nháp chưa có line. `reserve_order` mới bắt buộc đơn có line và số device active bằng quantity từng line. Ký hiệu 1..n trong sơ đồ lõi biểu diễn đơn hợp lệ lúc reserve, không phải ràng buộc tối thiểu một dòng ngay khi INSERT header.
3. **Giỏ hàng chưa giữ tồn.** Cùng device có thể được chọn ở nhiều giỏ DRAFT. UNIQUE `(order_id, device_id)` chỉ chống lặp trong một đơn; quyền giữ tồn ở `devices.reserved_order_id` sau khóa dòng. Khi hủy, các lựa chọn vẫn được giữ làm lịch sử; không đếm chúng thành tồn bị giữ.
4. **Hóa đơn 0..1, payment 0..n.** `invoices.order_id` UNIQUE nên một đơn có tối đa một bản ghi hóa đơn trong baseline; `payments.order_id` không UNIQUE. Checkout trả UUID **đơn bán**, tạo invoice `DRAFT` nội bộ; không trả số hóa đơn điện tử đã được provider phát hành.
5. **Giao từng phần mới có cấu trúc.** Một đơn có thể có nhiều `deliveries`; FK ghép của `delivery_items` bảo đảm device được chọn đúng đơn. PK chỉ duy nhất trong một chuyến, chưa cấm device xuất hiện ở nhiều chuyến khác nhau và chưa kiểm đơn PAID/device SOLD, giao đủ số lượng hay lịch kỹ thuật viên. Không bật tác nghiệp giao hàng chỉ dựa vào các FK này.
6. **Bảo hành và đổi trả còn thiếu liên kết nghiệp vụ.** Warranty duy nhất theo `(device_id, policy_id)`, cho phép nhiều policy khác nhau trên một device; chưa kiểm customer là người mua, ngày bắt đầu theo lần bán hay snapshot điều khoản. Return item trỏ về lựa chọn của đúng đơn nhưng chưa kiểm đã bán, thời hạn đổi trả, tổng hoàn ≤ số đã thu hay chống hoàn lại cùng device qua nhiều phiếu. Duyệt khác người lập có CHECK; quy trình hoàn tiền và bù trừ tồn chưa hiện thực.
7. **Chuẩn hóa có chủ đích.** Header/line và bảng nối tách quan hệ nhiều–nhiều. Các cột lặp trong FK ghép bảo vệ cùng PO/SKU/đơn; giá, thuế, chiết khấu và địa chỉ giao là snapshot giao dịch. `line_total` sinh từ số lượng/giá/chiết khấu/thuế; `total_amount` chốt lúc reserve. JSONB thông số không thay bảng nối device/payment. FK cha của category chặn tự trỏ trực tiếp, chưa chặn chu trình nhiều cấp; cây danh mục phải được kiểm thêm trước mở API quản trị tương ứng.

Những giới hạn trên là phát hiện khi đọc nguồn, chưa phải quyết định thêm nghiệp vụ hoặc yêu cầu sửa SQL trong đợt hoàn thiện hồ sơ này.

## 4. Bất biến → cơ chế SQL → kiểm chứng đang có

Nhãn dưới đây tìm nguyên văn trong [constraints.sql](../../../database/tests/constraints.sql) hoặc [runtime_acl.sql](../../../database/tests/runtime_acl.sql). Các cơ chế được mô tả trong [schema.sql](../../../database/schema.sql); toàn bộ tên constraint/index giữ trong từ điển vật lý.

| Bất biến / đối tượng | PK, FK, UNIQUE, CHECK, index hoặc transaction thực tế | Nhãn kiểm chứng / khoảng trống |
|---|---|---|
| PK và tham chiếu IAM | 37 bảng đều có PK; `user_roles.role_code` FK sang role | `duplicate primary key rejected`; `primary key cannot be null`; `unknown role foreign key rejected` — là ví dụ đại diện, không khẳng định đã cố tình phá từng PK/FK |
| Không lẫn SKU hoặc kho | FK ghép receipt–PO–product, device–receipt product, order warehouse–store, allocation–line/device product | `receipt device product composite FK`; `receipt warehouse must match purchase order`; `sales warehouse belongs to store`; `allocated device must match order line product` |
| Serial/IMEI định danh | UNIQUE Serial/IMEI/QR; CHECK Serial canonical/không whitespace/1–100 ký tự; IMEI regex 15 số | `normalized serial globally unique`; `serial unique across products`; `IMEI unique`; `IMEI must contain fifteen digits`; `direct inserts must use canonical serial`. QR index đã có; lookup QR không tương đương test camera |
| Dữ liệu số/ảnh hợp lệ | quantity > 0; giá ≥ 0; image position 1–3 và UNIQUE theo SKU/position; size ≤ 2MiB | `purchase quantity positive`; `product price nonnegative`; `at most three image positions`; `image size at most two MiB` |
| Nhập không vượt định mức | `receive_device` khóa PO header → receipt → receipt line → PO line; đếm đã nhận theo line và PO line | `closed receipt cannot receive devices`; `unapproved purchase order cannot receive devices`; `receiving cannot exceed declared quantity`. Test cuối kích hoạt giới hạn receipt line; chưa tách riêng ca nhiều receipt cùng vượt một PO line |
| Đúng và đủ device lúc reserve | FK ghép + kiểm lượng active; order lock rồi device lock theo UUID tăng dần | `empty cart cannot reserve`; `allocation count equals quantity`; `reserve calculates authoritative total`; ca hai phiên giữ chung một device trong validator |
| Tiền và dòng đơn đã chốt | `line_total` generated; CHECK chiết khấu/thuế; trigger khóa order cha và chặn đổi line/selection sau DRAFT | `line total applies discount then tax with numeric rounding`; `discount cannot exceed gross line amount`; `tax rate bounded`; `reserved cart line frozen`; `reserved device selections frozen`; `rows cannot move between orders` |
| Chỉ bán khi tiền xác nhận đủ | `complete_sale` cộng CAPTURED, so **bằng đúng** tổng; khóa order/device, kiểm chủ giữ | `checkout requires confirmed payment`; `underpayment rejected`; `overpayment rejected pending reconciliation`. API phải xác thực tiền thật trước khi ghi CAPTURED |
| Không viết lại tiền đã nhận | Trigger chặn UPDATE/DELETE CAPTURED; chặn payment mutation trên đơn đã kết thúc | `captured funds cannot be rewritten`; `late payment callback cannot mutate paid order`. Không có workflow refund được chứng minh |
| Retry lõi không ghi lặp | Kiểm status; UNIQUE invoice/order và outbox/aggregate/event; `uq_core_movement` chỉ cho RECEIVE/RESERVE/RELEASE/SALE, `NULLS NOT DISTINCT` | `reserve retry produces one audit and one outbox event`; `checkout retry sells once and retains internal invoice draft`; `cancellation retry releases once` |
| Hủy không nuốt tiền | `cancel_order` từ chối PAID hoặc có CAPTURED; chỉ trả device đang được chính đơn giữ | `completed sale cannot cancel`; `captured funds prevent cancellation`; `cancelled order cannot reserve` |
| Lỗi rollback nguyên tử | Hàm cùng transaction; FK actor lỗi ở các bước ghi sau làm rollback thay đổi trước | `failed receive rolls back inserted device`; `failed reserve rolls back stock movement and outbox`; `failed checkout keeps reservation and creates no invoice`; `failed cancellation retains reservation` |
| Quyền runtime | Thu hồi PUBLIC EXECUTE; grant theo cột, không cấp trực tiếp sửa tồn/status/total/audit; reader giới hạn catalog | `PUBLIC cannot execute SASMS functions`; `actual sasms_app UPDATE stock rejected`; `actual sasms_app UPDATE total rejected`; `actual sasms_app INSERT status rejected`; `actual sasms_app INSERT audit rejected`; `reader can query catalog and stock aggregates but cannot query customer data` |
| Đường truy vấn | UNIQUE index Serial/IMEI/QR; `ix_devices_stock`, `ix_payments_order`, `ix_outbox_pending`, `ix_audit_entity_date` và các index trong từ điển | Benchmark 100.000 device có Index Scan cho ba lookup định danh; chưa đo tải cho mọi index, mọi FK, outbox backlog hoặc toàn bộ giao dịch HTTP |

`uq_payments_provider_reference` chống lặp `(method, provider_reference)` khi reference khác NULL; nó không xác thực webhook, đối soát provider hoặc so sánh payload retry. Baseline nhận một device chưa có request key riêng: Serial trùng trả lỗi UNIQUE. Cùng UUID header cũng chưa chứng minh nội dung request trùng nhau.

## 5. Gắn bằng chứng với đúng phiên bản

[summary.json](../../../database/evidence/summary.json) và [validation.log](../../../database/evidence/validation.log) ghi lần chạy thực ngày 07/11/2026, PostgreSQL **16.15**: **56** assertions ràng buộc/lifecycle, **7** assertions ACL, hai phiên giữ hàng cho một thành công/một `23514`, lookup 100.000 device và cleanup fixtures. [Parser evidence](../../../database/evidence/artifacts-validation.json) xác nhận DBML 37 bảng/63 FK và cú pháp ERD. Lượt bổ sung tài liệu này chỉ kiểm hash/đường dẫn/truy vết; không tạo log DB mới hoặc thay kết quả cũ.

Các SHA256 dưới đây đã so lại với byte file hiện tại và khớp `source_sha256` trong summary:

| Nguồn | SHA256 |
|---|---|
| `database/schema.sql` | `66aacda1101c20ed0cc6c80062d93ef917660b4f6e825fa124f536dc2a1fcd96` |
| `database/tests/constraints.sql` | `9c40c7285ea48c206158e1db7ba4909261e52eac4ee57548c9dbe241719dcd7e` |
| `database/tests/runtime_acl.sql` | `bcbacf0f12fb194aaa9df58ade1cad029d53a5455ddd75ad8ca4cf2597866d08` |
| `scripts/validate_db.py` | `79d28cbd7859615551eae68f80c4c781449f35064737d038b7ea3c9f391b3841` |

Hash của seed, runtime provisioning và benchmark cũng khớp summary; xem đủ bảy nguồn tại file đó. Hash giúp gắn chứng cứ vào nội dung đã kiểm, không chứng minh phê duyệt nghiệp vụ.

[Snapshot CI đã lưu](../../../Tiền%20Sprint/DB/evidence/ci-runs.json) ghi [baseline run 42374581875](https://github.com/Kain2608/software-project-management/actions/runs/42374581875) thành công cho commit `a839b00a3c988a7959b158169f670c6655da7b6b`. Đây là kết quả của SHA đó, không tự coi là run của commit bổ sung tài liệu hiện tại. Không đổi ngày kiểm tháng 11 thành bằng chứng chạy/ký trong tháng 9.

## 6. Vòng đời dữ liệu và ranh giới vận hành

| Chủ đề | Hiện có trong baseline | Việc phải chốt trước sử dụng thật |
|---|---|---|
| Tồn và hủy | IN_STOCK → RESERVED → SOLD; hủy hợp lệ trả RESERVED → IN_STOCK; giữ lịch sử lựa chọn/biến động | TTL giữ hàng và worker hết hạn chưa có; không tự hủy đơn đã có CAPTURED |
| Chứng từ và xóa | FK NO ACTION; runtime không có quyền xóa thiết bị/biến động/audit/hóa đơn; danh mục có cờ active | NO ACTION chỉ bảo vệ quan hệ đang tham chiếu, không phải chính sách cấm owner xóa; cần quy trình lưu giữ/sửa sai và audit quản trị |
| Tiền | Nhiều khoản thu; CAPTURED bất biến; checkout không nhận thiếu/thừa | Chốt provider, webhook, idempotency và đối soát; hoàn tiền bằng giao dịch bù trừ có review, không sửa số tiền gốc |
| PII | User/customer/supplier chứa thông tin liên hệ; delivery có snapshot địa chỉ; reader không được đọc customer | Huy/PM chốt mục đích và thời hạn lưu/ẩn danh; Thái kiểm scope/DTO/export/log. `sasms_app` đọc rộng, chưa có RLS; không tuyên bố đã cách ly mọi PII theo cửa hàng |
| Audit/outbox | Ghi cùng transaction lõi, backend không sửa/xóa audit; event payload lõi chỉ có ID/tổng tiền | Chưa có worker/retention/dead letter, chưa bảo đảm chống sửa bởi owner; không đưa PII/secret vào metadata tùy ý |
| Backup/restore | Đã có kế hoạch và diễn tập riêng được bổ sung ở tiền Sprint | Đọc [D22 hiện có](../../../Tiền%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md) và [evidence môi trường](../../../Tiền%20Sprint/DB/evidence/environment-summary.json); đây là kiểm chứng Dev/CI tháng 11, chưa chứng minh Prod/Staging, mã hóa backup, PITR hoặc đạt RPO/RTO |

D50/D57 gốc ghi D22 hoặc diễn tập chưa có tại thời điểm soạn. D22 nay đã có trong repo; dẫn chiếu hiện tại này bổ sung đầu vào review, giữ lịch sử tài liệu gốc và không tự biến kế hoạch hạ tầng thành triển khai tháng 9 đã được duyệt.

## 7. Hợp đồng phiên bản Sprint 0 → Sprint 1

Baseline Sprint 0 là [schema.sql](../../../database/schema.sql) **37 bảng**, dùng trên database mới; không nạp lại để ghi đè DB đã tồn tại. Các thay đổi sau đó nằm trong [migration Sprint 1](../../../database/migrations) qua [runner](../../../scripts/migrate_db.py), có checksum và ledger `sasms_meta.schema_migrations` riêng. Không sửa migration đã áp dụng hoặc dùng seed baseline để cấp lại quyền đã thu hồi.

| Phiên bản | Khả năng thuộc đúng phiên bản | Bàn giao / kiểm chứng |
|---|---|---|
| Sprint 0 | Bốn hàm lõi, receive một máy; actor do backend tin cậy cung cấp; 37 bảng/63 FK | [D57](../D57-luoc-do-va-kiem-thu.md); không ghi nhập lô, request key theo payload hay kiểm active/scope vào thành quả S0 |
| Sprint 1 V001 | Chuẩn hóa/cập nhật catalog, revision, helper IAM active/permission/scope | [Runbook nâng cấp](../../../database/sprint1/README.md); dữ liệu cũ không đạt CHECK phải rollback và được rà soát trước chạy lại |
| Sprint 1 V002 | Hai bảng `identifier_rules`, `inventory_import_batches`; batch 1–500, request key so payload, wrapper nhập có quyền | [Hợp đồng định danh](../../../Sprint%201/DB/D35-hop-dong-quy-tac-dinh-danh.md); 39 bảng/67 FK ở bộ artifact riêng, không thay ERD S0 |

[Evidence Sprint 1](../../../database/evidence/sprint1/summary.json) ghi 76 assertions và kiểm nâng cấp giữ 112 dòng của 37 bảng cũ. Đó là bằng chứng riêng cho phiên bản nâng cấp, không tăng số assertions của S0. Helper quyền Sprint 1 áp dụng cho catalog/nhập kho trong phạm vi hiện thực; các hàm POS gốc vẫn cần authorization người dùng cuối ở backend. D35 Sprint 1 là hợp đồng/cấu hình chờ nguồn hãng, không phải FRS/rule hãng đã được BA ký.

## 8. Phiếu review và các quyết định còn mở

Tất cả dòng dưới đây là checklist để người có tên review, **chưa có chữ ký hoặc quyết định thực tế**. Việc chuẩn bị đủ tài liệu không thay quyền ký của Kiên/Huy/Thái/PM/Sponsor.

| Mã | Người chốt | Nội dung cần quyết định / bằng chứng cần bổ sung | Trạng thái |
|---|---|---|---|
| D51-R01 | Huy + Kiên | Xác nhận DB-A01–03: Serial toàn chuỗi, phụ kiện theo số lượng, một/nhiều IMEI, nguồn quy tắc hãng và cách xử lý dữ liệu thực không hợp lệ | Chờ review |
| D51-R02 | Huy + Thái | Thuế/chiết khấu/làm tròn, thanh toán thiếu/thừa, provider, refund và TTL; trả lời KS44/KS46 rồi nối với FRS có phiên bản | Chờ review |
| D51-R03 | Kiên + Thái | Review toàn bộ PK/FK/index/transaction; xác định cần thêm ca riêng vượt PO qua nhiều receipt và tải giữ hàng nhiều device trên Staging | Chờ review |
| D51-R04 | Huy + Thái | Chốt giao từng phần/chống giao trùng, người nhận, warranty snapshot, nhiều policy, đổi trả và tổng tiền hoàn; chỉ thiết kế migration khi phạm vi được chốt | Chờ review |
| D51-R05 | Huy + PM + Thái | Chốt retention/ẩn danh PII, quyền đọc theo cửa hàng, audit đặc quyền và dữ liệu được gửi AI; chưa có thời hạn pháp lý được tự giả định | Chờ review |
| D51-R06 | Kiên + Thái | Phê duyệt cách nâng cấp S0→S1 và phân biệt artifact; chốt công cụ/host/secret/backup theo D22 trước phát hành thật | Chờ review |
| D51-R07 | PM + Sponsor + Huy | Xác nhận baseline D51 và các ngoại lệ còn mở; lập biên bản/ký M1 thật theo [hồ sơ DB D126](../D126-ho-so-review-M1.md) | Chờ review / chưa ký |

Tiêu chí hồ sơ D51 đã được chuẩn bị để review: mô hình/từ điển có sẵn; truy vết nguồn và test đã bổ sung; hash nguồn đã đối chiếu; khoảng trống ngoài SQL có người chốt rõ. Nghiệm thu nghiệp vụ, Staging và Quality Gate vẫn theo bằng chứng và người có thẩm quyền thực tế.
