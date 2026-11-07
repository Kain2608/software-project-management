# S0-19 / S0-22 / S0-23 — Hợp đồng bàn giao DB cho API, AI và tích hợp

| Thuộc tính | Nội dung |
|---|---|
| Người đóng góp | Trần Trung Kiên — Architect / DBA |
| Người nhận / chủ trì | Thái: D52/OpenAPI, D56/tích hợp; Vũ: D55/AI |
| Nguồn phân công | [Workbook Sprint 0](../inputs/phan-cong-sprint-0.xlsx), sheet “Theo người” dòng 22; sheet “Phân chia công việc” dòng 12, 15, 17 |
| Ngày / nhánh | 07/11/2026; `DB` |
| Phạm vi | Phần hợp đồng DB bổ sung; chưa phải OpenAPI đầy đủ, adapter provider hoặc log gọi AI |

[Schema S0](../../../database/schema.sql) và [D57](../D57-luoc-do-va-kiem-thu.md) đã có được giữ nguyên. Các đường dẫn HTTP dưới đây là đề xuất để Thái đưa vào D52, chưa có endpoint ứng dụng tương ứng trong repo. Không suy ra việc gửi SMS, phát hành hóa đơn, xác nhận tiền hoặc gọi LLM đã chạy từ thiết kế này.

## 1. Hợp đồng cho D52 / API kho và POS

Backend xác thực phiên/JWT, ánh xạ sang `users.id` và kiểm quyền/phạm vi tài nguyên trước khi dùng credential `sasms_app`. Không lấy actor, vai trò hoặc quyền cửa hàng từ body làm bằng chứng. Cả đọc theo ID, tra Serial và trả lại kết quả retry đều phải kiểm phạm vi. Sprint 0 chưa tự kiểm user active/RBAC trong các hàm và chưa có RLS.

| Thao tác HTTP đề xuất | Dữ liệu client được gửi | Cách truy cập DB và phản hồi sau COMMIT |
|---|---|---|
| `POST /inventory/receipt-items/{id}/devices` | `{serial, imei?}`; receipt item lấy từ path | `SELECT sasms.receive_device($1::uuid,$2::text,$3::text,$4::uuid)`; `$4` từ backend; trả device ID và QR token nếu người gọi được phép xem |
| `GET /inventory/devices?serial=...` hoặc tra QR | Một định danh đã parse; kho/cửa hàng là bộ lọc được kiểm quyền | Truy vấn bind tham số; join warehouse/store trong scope; chỉ trả trường nghiệp vụ cần thiết, không xuất thẳng hàng DB |
| `POST /sales/orders` | Store/warehouse, customer tùy chọn, channel và dòng `{productId, quantity, deviceIds}`; request key ổn định | INSERT header bằng allowlist, dòng và liên kết thiết bị trong transaction. Backend xác định giá/chiết khấu/thuế; không nhận `status`, `total_amount`, `paid_at`, `created_by` hoặc số tiền tin cậy từ browser |
| `POST /sales/orders/{id}/reserve` | Không nhận lại giỏ/giá đã freeze | `SELECT sasms.reserve_order($1::uuid,$2::uuid)`; trả tổng numeric từ DB, trạng thái thực tế và order ID |
| `POST /sales/orders/{id}/complete` | Ý định hoàn tất; không nhận cờ `paid=true` làm bằng chứng tiền | Sau kiểm thanh toán, gọi `sasms.complete_sale($1::uuid,$2::uuid)`; trả order ID và trạng thái; invoice nội bộ mới chỉ DRAFT |
| `POST /sales/orders/{id}/cancel` | Ý định hủy; lý do/audit mở rộng cần chốt nếu muốn lưu thêm | `SELECT sasms.cancel_order($1::uuid,$2::uuid)`; đọc lại trạng thái trong scope; không thực hiện refund |

`sasms_app` hiện chỉ có INSERT header đơn trên `id, code, idempotency_key, store_id, warehouse_id, customer_id, channel, created_by`; có DML cho dòng đơn/lựa chọn/payment qua trigger và EXECUTE bốn hàm. Role này không có đường tạo/duyệt PO, sửa tồn trực tiếp hoặc phát hành hóa đơn. D52 phải nêu rõ phần API cần migration/đường ghi mới, không dùng tài khoản owner trong app để bỏ qua ranh giới này. Chi tiết ở [runtime-roles.sql](../../../database/runtime-roles.sql).

### DTO và lỗi cho Dev/QA

| Trường / quy tắc | Kiểm tại API trước gọi DB | Kiểm cuối trong S0 |
|---|---|---|
| ID | UUID đúng định dạng; tài nguyên thuộc scope | PK/FK và FK ghép bảo vệ đúng quan hệ sản phẩm–phiếu–PO–kho–đơn |
| Serial / IMEI | Serial string, trim + uppercase, 1–100 ký tự và không khoảng trắng; IMEI string hoặc NULL, trim rỗng thành NULL, nếu có 15 chữ số | CHECK và UNIQUE toàn bảng `devices`; generic format chưa phải quy tắc hãng đã được BA xác nhận |
| Số lượng / thiết bị | Integer >0; mỗi chiếc chỉ liệt kê một lần trong đơn; product ID phải khớp dòng | `reserve_order` kiểm số thiết bị active bằng quantity, đúng kho và trạng thái IN_STOCK; khóa khi chuyển quyền sở hữu |
| Tiền / thuế | Dùng decimal, không dùng float; giá/chiết khấu từ backend; tiền `numeric(14,2)`, thuế 0–100 | CHECK không âm; chiết khấu ≤ quantity × unit_price; `line_total` tính và làm tròn 2 số; tổng đơn lấy từ các dòng. API nên từ chối độ chính xác/độ lớn vượt hợp đồng thay vì để DB tự làm tròn |
| Trạng thái | Không cho client gán trạng thái kho/đơn/tổng; chỉ mở các action được phép | Hàm nghiệp vụ và trigger freeze dòng sau giữ; khoản CAPTURED bất biến qua đường app |
| Actor / quyền | Backend lấy actor từ phiên đã xác thực, kiểm active/permission/store và giá trị trả về trong scope | S0 chỉ kiểm FK actor. S1 mới thêm một phần kiểm active/RBAC ở catalog/nhập kho; POS S0 vẫn cần service kiểm |

| SQLSTATE / trường hợp | Phản hồi API đề xuất | Xử lý |
|---|---|---|
| `22P02`, `23502`, `22003` | 400 cho kiểu/thiếu trường; 422 cho số vượt giới hạn | DTO nên chặn trước; rollback, không lộ SQL/DSN/stack trace |
| `23505` — trùng Serial/IMEI/SKU hoặc khóa duy nhất | 409, mã lỗi nghiệp vụ theo constraint đã biết | Không đổi thành thành công chỉ vì bị trùng. Với khóa retry/provider cần đối soát đúng actor/scope/đơn/payload rồi mới trả kết quả cũ |
| `23503` / `P0002` — FK hoặc không có dòng | 404 nếu tài nguyên không tồn tại trong scope; 422 nếu quan hệ đầu vào không hợp lệ | Không dùng lỗi FK để tiết lộ dữ liệu ngoài quyền. S0 `INTO STRICT` có thể trả `P0002`; S1 missing receipt có thể trả `23503` |
| `23514` — CHECK hoặc quy tắc nghiệp vụ | 422 cho định dạng/giá trị; 409 cho đơn đã freeze, hàng không còn, quá định mức, tiền chưa khớp hoặc key dùng sai payload | Dựa vào action, constraint và danh mục lỗi đã chốt; không phân loại mọi `23514` như một lỗi giống nhau |
| `42501` | 403 với lỗi thiếu quyền nghiệp vụ đã nhận diện | Nếu lỗi do credential/provisioning sai, xử lý lỗi cấu hình phía server; không đổ thành lỗi quyền của người dùng |
| `40001` / `40P01` | Retry transaction có giới hạn khi serialization/deadlock; hết retry trả lỗi tạm thời theo D52 | S1 `update_product` cũng chủ ý trả `40001` khi revision cũ: trường hợp đó trả 409 và yêu cầu tải lại, không tự ghi đè |
| `55P03`, timeout, lỗi kết nối `08...` | Lỗi tạm thời hoặc kết quả chưa rõ | Rollback khi còn kết nối; nếu COMMIT có thể đã xảy ra, đối soát bằng khóa nghiệp vụ trước retry. Không tự gửi lại khoản tiền |

Mã HTTP và envelope lỗi là đề xuất cho Thái chốt trong D52, không phải tính năng SQL đã cung cấp. Log dùng request/correlation ID, loại action và SQLSTATE đã lọc; phản hồi không chứa thông tin khách hàng hoặc câu SQL thô.

### Idempotency và ranh giới phiên bản

| Thao tác | S0 đã thực hiện | Quy tắc API và phần chưa hiện thực |
|---|---|---|
| Nhập một chiếc | `receive_device(receipt_item uuid, serial text, imei text, actor uuid) RETURNS uuid`; gọi lại cùng Serial bị UNIQUE | Chưa có bảng lưu request/payload của API S0. Mất phản hồi phải đối soát trong scope; không báo mọi xung đột Serial là retry thành công |
| Tạo đơn | `sales_orders.idempotency_key` UNIQUE UUID | UNIQUE chưa so payload. API cần so cả actor/scope/giỏ canonical với ý định tạo ban đầu và lưu snapshot/hash bằng migration được review trước khi hứa payload idempotency; không có ledger đó trong S0 |
| Giữ đơn | `reserve_order(order uuid, actor uuid) RETURNS numeric`; RESERVED/PAID trả tổng cũ | Replay theo cùng order, không cho sửa giỏ đã freeze; không bảo đảm cùng body tạo đơn nếu chỉ tái dùng key header |
| Hoàn tất bán | `complete_sale(order uuid, actor uuid) RETURNS uuid`; PAID trả order ID cũ | Không tạo lại payment trên mỗi lần complete; kiểm lại auth/scope trước mọi replay; không hạ trạng thái PAID khi mất phản hồi |
| Hủy đơn | `cancel_order(order uuid, actor uuid) RETURNS void`; CANCELLED no-op | Đơn PAID hoặc có CAPTURED bị từ chối; refund là luồng khác |
| Nhập lô sau nâng cấp S1 | V002 thêm `receive_device_batch(receipt_item uuid, request_key uuid, devices jsonb, actor uuid) RETURNS uuid[]` | 1–500 object chỉ gồm serial/imei; cùng key + actor + receipt + payload canonical **cùng thứ tự** trả cùng ID; khác thành phần bị `23514`; luôn kiểm quyền lại. Hàm đơn chiếc S1 dùng key mới mỗi lần nên không có tính retry theo key của batch |

Canonical batch S1 trim/uppercase Serial, trim IMEI và chuyển rỗng thành NULL. Key là duy nhất toàn bảng nhập. Backend giữ key cho cùng ý định, không đổi key khi retry mạng. V001/V002 cũng thêm helper quyền/cập nhật revision và đưa DB lên 39 bảng; xem [bàn giao S1](../../../Sprint%201/DB/DS1-thiet-ke-va-ban-giao-DB.md). Không nạp `schema.sql` hoặc seed baseline lên database đã nâng cấp.

## 2. Dữ liệu cho D55 / AI và fallback

Vũ nhận phần chiếu dữ liệu và giới hạn truy cập dưới đây; prompt hoàn chỉnh, model, Redis, bộ đo latency và log provider do Vũ chủ trì. Repo chưa có pipeline RAG/cache/LLM đang chạy. AI chỉ gợi ý từ catalog hợp lệ, không ghi giá/tồn/quyền hoặc xác nhận đơn đã trả tiền.

Ví dụ SELECT có thể dùng cho adapter catalog do backend kiểm soát, với product ID bind từ request đã kiểm tra:

```sql
SELECT p.id, p.sku, p.name, b.name AS brand, c.name AS category,
       p.list_price, p.warranty_months
FROM sasms.products AS p
JOIN sasms.brands AS b ON b.id = p.brand_id
JOIN sasms.categories AS c ON c.id = p.category_id
WHERE p.is_active AND p.id = $1::uuid;
```

| Dữ liệu / kiểm soát | Hợp đồng bàn giao |
|---|---|
| Trường được chiếu mặc định | Product ID, SKU, tên/brand/category hiển thị, giá niêm yết và bảo hành từ catalog. Dùng dữ liệu giả trong demo; nội dung text vẫn phải giới hạn độ dài và coi là dữ liệu không đáng tin khi ghép prompt |
| Thông số / mô tả | `specifications` là JSON object nhưng DB chưa có allowlist theo ngành hàng; `description` là text tùy ý. Chỉ thêm key/thông tin đã được BA cho phép vào DTO đã lọc; không gửi nguyên JSON/text hoặc `SELECT *` sang model |
| Tồn | Backend kiểm kho/cửa hàng được phép và dùng `stock_availability.available > 0` để lọc candidate. Không cần gửi Serial, QR token, địa chỉ kho hoặc danh sách từng chiếc cho LLM |
| Dữ liệu bị loại | users/auth_subject/email, customers/địa chỉ/điện thoại, supplier contacts, payments/provider payload, audit, token/secret và chứng từ chứa PII; không đưa vào prompt, embedding, cache key hoặc log AI |
| Credential | Backend giữ credential đọc; `sasms_reader` chỉ được SELECT catalog và view tồn, không đọc khách hàng/payment/audit. Role này chưa tự lọc inactive hoặc key JSON: adapter phải chiếu đúng DTO; không cho model sinh SQL hoặc giữ credential |
| Kết quả model | Chỉ chấp nhận ID thuộc tập candidate server đã truy xuất; kiểm lại active, giá và khả dụng trước hiển thị/checkout; nội dung catalog/model là dữ liệu, không phải chỉ dẫn để gọi tool hoặc bỏ kiểm quyền |

Fallback đề xuất: tra luật bán kèm đã được Huy/nhóm sản phẩm xác nhận theo source product/category → candidate product IDs; lọc sản phẩm active và khả dụng tại kho hợp lệ; xếp hạng theo luật cố định, tie-break theo product ID. Không có luật/candidate hợp lệ thì trả danh sách rỗng và trạng thái `RULE`/không có gợi ý; không bịa tương thích cáp, giá treo hoặc phụ kiện. Luật này là cấu hình cần Vũ/Huy bổ sung, chưa có bảng luật bán kèm đã duyệt trong schema. Gói bảo hành/phụ kiện bán theo số lượng cần chốt mô hình vì baseline đang chọn thiết bị cho mọi dòng bán.

Cache đề xuất do Vũ triển khai: key `ai:crosssell:v1:<catalogFingerprint>:<rulesVersion>:<modelVersion>:<warehouseId>:<sourceProductId>:<locale>:<limit>`, chỉ ID/phiên bản không có PII. Fingerprint lấy từ projection canonical của source và candidates; S0 chưa có catalog revision, S1 có revision sản phẩm nhưng vẫn phải tính đến thay đổi toàn tập candidate/luật. TTL khởi điểm 5 phút để review, không phải TTL đang hoạt động. Kiểm lại giá/active/tồn khi trả cache và khi checkout; thay phiên bản key hoặc invalidate khi catalog/luật đổi. Cache lỗi, timeout model hoặc output sai thì chuyển rules fallback; không giữ transaction DB khi gọi mạng.

`ai_recommendations` hiện chứa source/recommended product, source `RULE|LLM|CACHE`, score 0–1, model_version và thời điểm hết hạn; có CHECK không tự gợi ý cùng sản phẩm. Bảng chưa bảo đảm một kết quả duy nhất theo cache key và `sasms_reader`/`sasms_app` chưa có quyền INSERT kết quả. Nếu cần lưu, Kiên/Thái thiết kế hàm/worker role hẹp và migration riêng; không cấp AI quyền owner hoặc ghi tùy ý. Chưa có evidence gọi LLM/Redis, độ trễ AI hay endpoint AI 200 trong phần bổ sung này.

## 3. Dữ liệu cho D56 / thanh toán, hóa đơn và thông báo

| Điểm tích hợp | Hợp đồng DB đã có | Trách nhiệm adapter / phần cần bổ sung |
|---|---|---|
| Tiền mặt | `payments.method=CASH`; CAPTURED cần `confirmed_by`, `captured_at`; trigger khóa đơn | Backend kiểm quyền thu ngân, số tiền và chứng từ; không nhận actor hoặc cờ CAPTURED trực tiếp từ browser |
| Bank/card / webhook | `method=BANK_TRANSFER|CARD`; UNIQUE `(method, provider_reference)` khi reference không NULL; amount >0 | Kiểm chữ ký/timestamp/replay theo tài liệu provider, đúng merchant/đơn/tiền/đơn vị tiền; kiểm phía server khi cần. Schema chưa lưu currency hoặc provider namespace riêng: chốt hợp đồng trước tích hợp nhiều provider; chuẩn hóa reference có namespace nếu cần theo thiết kế được review |
| Webhook hoặc request lặp | Reference duy nhất chặn trùng, CAPTURED không sửa/xóa | Khi trùng, đối chiếu payment cũ đúng order/method/amount/nguồn; trùng reference khác đơn/khác tiền là xung đột để đối soát. Không dùng `ON CONFLICT DO NOTHING` rồi mặc định đơn mới đã trả tiền |
| Không rõ kết quả / callback muộn | PENDING không đủ điều kiện hoàn tất; `cancel_order` không chạy khi đã có CAPTURED | Đối soát provider ngoài transaction rồi khóa/kiểm lại đơn trước cập nhật. TTL không tự giải phóng đơn còn tiền chưa rõ; tiền về cho đơn đã hủy cần luồng ngoại lệ/refund riêng, chưa hiện thực |
| Hóa đơn | `complete_sale` tạo một invoice/order, số nội bộ `RECEIPT-<order.code>`, status DRAFT; có provider_reference UNIQUE và issued_at | DRAFT không phải e-invoice hợp lệ. Adapter phát hành sau commit, lưu reference/thời gian và ISSUED qua hàm/role mới; không tự đặt ISSUED từ UI. Credentials và API sandbox hiện chưa có |
| SMS / thông báo | Outbox mang ID/total, không mang số điện thoại/địa chỉ | Worker lấy liên hệ cần thiết sau kiểm quyền/mục đích; provider message ID và trạng thái gửi cần delivery ledger mới. Không đưa PII thô vào outbox/log và không tuyên bố gửi thành công từ hàng event |
| Máy in | DB cung cấp chứng từ nội bộ và dữ liệu hiển thị đã lọc | [D19](../../PM/D19.docx), đoạn 49–64, mô tả CR-TV4-W6-001 ngày 14/10 và quyết định PM 18/10 hoãn máy in 80mm, chuyển HTML/PDF. Đây là thay đổi nguồn tháng 10 so với kế hoạch tháng 9; repo hiện chưa có UI in/driver hoặc biên bản thử máy |

Không lưu payload webhook thô nếu có PII/secret; cần trường allowlist và chính sách lưu giữ khi thêm receipt ledger. Xác nhận tiền ở DB không tự kiểm chữ ký provider. Không gọi AI, SMS, bank hoặc e-invoice trong transaction đang khóa đơn/thiết bị. Sau tiền đã commit, lỗi gửi hóa đơn/SMS không đảo ngược giao dịch bán bằng cách sửa CAPTURED.

### Outbox: phần đã có và hợp đồng worker cần xây

| Event hiện có | Aggregate / payload hiện có | Thời điểm ghi |
|---|---|---|
| `DeviceReceived` | device ID / `{device_id}` | Cùng transaction nhập chiếc |
| `OrderReserved` | order ID / `{order_id,total}` | Cùng transaction giữ hàng |
| `OrderPaid` | order ID / `{order_id,total}` | Cùng transaction hoàn tất bán |
| `OrderCancelled` | order ID / `{order_id}` | Cùng transaction hủy |

Baseline có `id`, aggregate/event, payload, `created_at`, `published_at`, `attempts`; UNIQUE `(aggregate_id,event_type)` chống lặp bốn event hiện tại. Nó chưa phù hợp để ghi nhiều lần cùng event type cho một aggregate trong các workflow tương lai nếu chưa thay thiết kế. App không có quyền UPDATE outbox, chưa có worker role, lease, retry schedule hoặc delivery ledger.

Hợp đồng triển khai tiếp: worker dùng role hẹp và `event.id` làm khóa chống lặp phía consumer/provider; claim trong transaction ngắn với `FOR UPDATE SKIP LOCKED` và lease/claim fields được bổ sung bằng migration; COMMIT claim trước gọi mạng; sau đó lưu kết quả, số lần thử và mốc retry trong transaction khác. Mất tiến trình sau provider nhận nhưng trước lưu kết quả sẽ gửi lại: consumer cần chống lặp hoặc đối soát reference, không tuyên bố exactly-once. Chỉ đánh dấu `published_at` theo định nghĩa đích nhận đã chấp nhận; nếu một event có nhiều đích SMS/e-invoice thì lưu trạng thái riêng từng đích. Thiết lập backoff, giới hạn retry và hàng chờ xử lý thủ công sau khi Thái chốt provider; chưa có các cơ chế này trong SQL S0.

## 4. Điều kiện bàn giao cho người chủ trì

Kiên đã cung cấp mapping API → SQL, DTO/SQLSTATE, giới hạn retry, projection AI và ranh giới tích hợp DB. Thái cần bổ sung D52/OpenAPI, auth/DTO/HTTP tests, tài liệu provider, tài khoản sandbox, worker và log chạy; Vũ cần bổ sung D55, luật được xác nhận, prompt/RAG/cache và số đo thật. PR review/ký vẫn do thành viên có thẩm quyền thực hiện. [Evidence S0](../../../database/evidence/summary.json) và [S1](../../../database/evidence/sprint1/summary.json) chỉ chứng minh DB theo đúng phiên bản nguồn được lưu trong từng summary.
