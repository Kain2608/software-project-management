# D56 – Thiết kế tích hợp Hóa đơn điện tử / SMS / Máy in (DRAFT – Sprint 0)

**Đầu ra thiết kế, không phải code tích hợp chạy thật.** Chủ trì TV4; review BA/PO, Architect/DBA, QA và đầu mối nghiệp vụ hóa đơn/bảo hành. Căn cứ D34 FR06, FR12, BR05, BR07, BR09 và kết quả rà soát các đầu vào D35/D40/D51/D54. **Nhà cung cấp cụ thể, quyền truy cập, biểu mẫu và chính sách chưa được chốt**.

## 1. Phạm vi và ranh giới

- **Biên nhận POS**: do ứng dụng tạo từ **đơn PAID đã lưu**, có mã đơn, dòng hàng, số tiền, QR tra cứu, trạng thái in và dấu `BẢN IN LẠI` khi in lại. **Không đồng nhất với hóa đơn điện tử**.
- **Hóa đơn điện tử**: do **adapter nhà cung cấp bên ngoài** phát hành; ứng dụng không tự đóng vai hệ thống phát hành. Demo chỉ dùng sandbox/stub, phải có nhãn rõ ràng không phải chứng từ hợp lệ.
- **SMS bảo hành**: đưa vào outbox khi bảo hành đã được kích hoạt hợp lệ, gửi qua adapter nhà cung cấp. SMS lỗi **không hủy bảo hành**.
- **Máy in POS**: job bất đồng bộ/lệnh gửi máy in; in thất bại **không hủy đơn PAID, không hoàn tồn**.

## 2. Sơ đồ logic được đề xuất

```text
POS UI --(order/receipt read)--> Spring Boot backend --> PostgreSQL (orders, payments, audit)
                                      |      |      |
                               Print adapter  E-invoice adapter  SMS adapter
                                      |      |      |
                                  Máy in    Sandbox/Provider   Sandbox/Provider
                                      |      |      |
                               print_job    invoice_outbox    sms_outbox
```

Sơ đồ chỉ mô tả trách nhiệm; **tên bảng/job và runtime adapter là đề xuất** cần đối chiếu schema 37 bảng Kiên, không được tự thêm bảng vào baseline. API browser không chứa key nhà cung cấp, token, chữ ký hay kết quả callback tùy ý.

## 3. Hợp đồng adapter đề xuất

| Adapter | Input tối thiểu | Output | Idempotency / kiểm tra | Retry |
|---|---|---|---|---|
| `ReceiptPrinter` | order_id, print_job_id, copy_type, dữ liệu đơn PAID | job id, status `QUEUED/SENT/FAILED/UNKNOWN` | Khóa `print_job_id`, không tạo đơn | Chỉ retry job, reprint có cờ riêng |
| `ElectronicInvoice` | order_id, tax/customer fields được phép, payload invoice_version | provider_ref, `PENDING/ISSUED/FAILED/UNKNOWN` | Không phát hành lặp bằng cùng khóa nghiệp vụ; xác minh chữ ký callback | Đối soát provider_ref nếu timeout |
| `WarrantySms` | warranty_id, masked destination/reference, template_id | provider_msg_id, `QUEUED/SENT/FAILED/UNKNOWN` | Một outbox event theo warranty_id + template + event_type | Exponential backoff có giới hạn, DLQ/manual review |

**Không tự tuyên bố DELIVERED** chỉ vì API SMS trả `accepted`. **Không tự tuyên bố PRINTED** chỉ vì driver trả `SENT`. Hóa đơn `ISSUED` chỉ khi nhận dữ liệu xác minh từ provider/sandbox đúng chế độ.

## 4. Luồng thành công

1. Thu ngân tạo/giữ đơn và xác minh PAID từ nguồn đáng tin cậy; transaction nghiệp vụ ghi SOLD + xuất kho đúng một lần.
2. Mở biên nhận đọc từ đơn đã lưu (FR06). Ứng dụng tạo print_job; thành công hay lỗi ghi trạng thái độc lập.
3. Với đơn đủ điều kiện, yêu cầu phát hành hóa đơn điện tử được đưa qua adapter; ghi `provider_ref`, trạng thái, correlation_id và lần thử. Provider callback phải xác thực nguồn, chống lặp và đối soát.
4. Bảo hành được tạo **một lần** theo BR05 (không cần lắp: từ PAID; cần lắp: từ hoàn tất giao/lắp); SMS ghi vào outbox sau commit nghiệp vụ, gửi riêng.

## 4.1 Ma trận trách nhiệm tích hợp

| Tích hợp | Trigger nghiệp vụ đã có căn cứ | Hướng gọi | Nguồn trạng thái có thẩm quyền | Callback/poll | Quyền và dữ liệu | Trạng thái provider |
| --- | --- | --- | --- | --- | --- | --- |
| Hóa đơn điện tử | Yêu cầu phát hành cho đơn đã PAID và đủ dữ liệu theo chính sách được duyệt | Backend/worker → adapter → provider | Bản ghi invoice/provider_ref đã được backend xác minh; không lấy trạng thái từ UI | Callback có xác minh hoặc poll theo provider_ref khi timeout | Chỉ backend giữ secret; quyền phát hành/xem/chỉnh ngoại lệ **PENDING D54/BA** | **PENDING provider/sandbox** |
| SMS bảo hành | Bảo hành đã kích hoạt hợp lệ theo BR05 | Outbox/worker → adapter → provider | Trạng thái outbox và delivery receipt nếu provider hỗ trợ | Callback delivery receipt hoặc poll; `accepted` chưa phải `DELIVERED` | Chỉ gửi PII tối thiểu; quyền resend/xem destination **PENDING D54/BA** | **PENDING provider/sandbox** |
| Máy in POS | Thu ngân xem/in biên nhận của đơn PAID; reprint là thao tác riêng | Backend/UI → print adapter/agent → máy in | Print job/agent acknowledgement; `SENT` chưa phải giấy đã in | Local agent acknowledgement/status hoặc kiểm tra thủ công | Người dùng phải có quyền xem/in đơn trong scope; permission chi tiết **PENDING D54** | **PENDING model/driver/agent** |

Payment provider không nằm trong ba adapter của D56 hiện tại. D52 có payment initiation/status nhưng callback owner chưa được chốt; BA/Architect phải quyết định bổ sung vào D56 hay tài liệu tích hợp thanh toán riêng. Không dùng ma trận trên để mặc định hóa payment contract.

## 4.2 Callback và chống replay

Callback chỉ được tiếp nhận tại backend/adapter, không đi thẳng vào trình duyệt. Hợp đồng cụ thể phụ thuộc provider và đang **PENDING**, nhưng thiết kế review phải bao gồm:

1. Xác định provider/event type và phiên bản payload trước khi xử lý.
2. Xác minh TLS, chữ ký/HMAC theo tài liệu provider, timestamp/nonce hoặc cơ chế chống replay được provider hỗ trợ.
3. Dùng provider event ID hoặc khóa sự kiện tương đương để xử lý idempotent; callback lặp trả lại kết quả an toàn và không phát hành/gửi lần hai.
4. Đối chiếu provider_ref với order/invoice/warranty thuộc đúng scope; không tin order_id, status hoặc amount chỉ vì có trong payload.
5. Chỉ chuyển trạng thái theo mapping đã được BA/Architect duyệt; trạng thái lạ giữ `UNKNOWN` và đưa vào tra soát.
6. Callback sai chữ ký, hết replay window, sai provider_ref hoặc sai payload bị từ chối và audit; không tiết lộ secret hay PII trong lỗi.

Các thông số header chữ ký, thuật toán, replay window, endpoint, response code và mapping event vẫn **PENDING provider/Architect**.

## 4.3 Idempotency retry timeout và recovery

| Tích hợp | Idempotency | Timeout | Retry | Lỗi không retry tự động | Recovery an toàn |
| --- | --- | --- | --- | --- | --- |
| Hóa đơn điện tử | Cùng yêu cầu nghiệp vụ phải giữ cùng khóa; exact key format **PENDING Architect/provider** | Connect/read/overall timeout **PENDING provider**; timeout → `UNKNOWN/PENDING` | Tra cứu provider_ref trước; nếu retry thì dùng cùng key và payload; max attempt/backoff **PENDING** | Payload/tax data sai, unauthorized, signature/config sai | Manual review/đối soát; không phát hành lại bằng key mới khi kết quả chưa rõ |
| SMS | Một outbox event không sinh nhiều provider message ngoài chính sách retry; key theo event nghiệp vụ, format **PENDING Architect** | Request/delivery timeout **PENDING provider**; accepted không phải delivered | Chỉ retry lỗi transient/timeout với cùng event; exponential backoff, max attempt **PENDING** | Template/destination invalid, unauthorized, policy/consent violation | DLQ/manual review sau ngưỡng được duyệt; bảo hành vẫn giữ trạng thái đã commit |
| Máy in | Retry cùng print_job khác với reprint có chủ ý; không tạo order mới | Agent/driver acknowledgement timeout **PENDING model**; timeout → `UNKNOWN` | Retry job khi có căn cứ chưa in; reprint tạo dấu `BẢN IN LẠI` theo BR07 | Dữ liệu receipt không hợp lệ, không có quyền, đơn chưa PAID | Kiểm tra máy/queue, tra job, sau đó retry/reprint có audit; không đổi payment/tồn |

Mọi retry chạy ngoài transaction bán hàng đã commit. Không giữ DB transaction mở trong khi chờ provider, SMS hoặc máy in.

## 4.4 Phân loại lỗi tích hợp

| Nhóm lỗi | Ví dụ | Trạng thái/ghi nhận | Hành động |
| --- | --- | --- | --- |
| Validation/business precondition | Đơn chưa PAID, thiếu dữ liệu bắt buộc, bảo hành chưa ACTIVE | Từ chối trước khi gọi adapter | Trả lỗi có code/correlation_id; không retry tự động |
| Authentication/authorization | Secret provider sai, nhân viên ngoài scope | FAILED/forbidden và audit đã lọc | Không retry cho tới khi cấu hình/quyền được sửa |
| Transient transport | Timeout, DNS/network tạm thời, provider 5xx | PENDING/UNKNOWN hoặc FAILED_RETRYABLE theo mapping được duyệt | Retry có backoff và cùng idempotency key |
| Permanent provider | Payload bị provider từ chối, destination invalid | FAILED không retry tự động | Manual correction/review; không sửa giao dịch gốc |
| Duplicate/replay | Callback hoặc request lặp | Trả kết quả cũ/idempotent | Không tạo invoice/SMS/order/job nghiệp vụ trùng |
| Unknown outcome | Request đã gửi nhưng không nhận được kết quả | UNKNOWN/PENDING | Poll/đối soát trước khi retry |
| Local device | Máy in offline, hết giấy, agent mất kết nối | PRINT_FAILED/UNKNOWN | Sửa thiết bị, tra job, retry/reprint có audit |

Tên enum vật lý và nơi lưu lỗi phải được Architect đối chiếu với D51/schema 37 bảng; bảng này không tự thêm trạng thái hoặc bảng vào baseline.

## 5. Sự cố, timeout và biện pháp

| Sự cố | Xử lý an toàn | Điều tuyệt đối tránh |
|---|---|---|
| Máy in offline/hết giấy | Giữ PAID, đánh dấu `PRINT_FAILED`, cho in lại sau khi xử lý máy | Không hủy order, không tạo xuất kho lần hai |
| Nhấn in hai lần | Phân biệt job retry và reprint; bản in lại giữ order_id | Không sinh order mới |
| Provider hóa đơn trả timeout | Giữ `UNKNOWN/PENDING`, tra cứu theo provider_ref | Không tự tái phát hành hóa đơn với key mới |
| Callback hóa đơn trùng | Kiểm chữ ký, idempotent update và lưu audit | Không tạo hai hóa đơn gốc |
| SMS lỗi | Retry outbox có giới hạn; bảo hành vẫn ACTIVE nếu đã kích hoạt | Không rollback bảo hành vì SMS |
| Provider SMS báo accepted | Hiển thị trạng thái gửi riêng, chờ delivery receipt nếu hỗ trợ | Không tuyên bố khách đã nhận SMS |
| PAID đang PENDING/UNKNOWN | Reconcile, không in biên nhận PAID khi chưa xác minh | Không tự chuyển PAID theo nút trên UI |
| Thiếu quyền khác siêu thị | 403; không rò rỉ thông tin khách | Không dựa vào ẩn nút UI để bảo mật |

## 6. Bảo mật và vận hành

- TLS đối với nhà cung cấp; kiểm chữ ký/HMAC và chống replay callback; không hardcode secret vào frontend hoặc Git.
- Phân quyền theo role + store/warehouse; nội dung biên nhận/SMS chỉ lộ thông tin khách theo phạm vi.
- Audit tối thiểu: actor, order_id/warranty_id, provider_ref, correlation_id, event, thời điểm UTC, số lần retry, kết quả và lỗi đã làm sạch; hiển thị giờ Việt Nam.
- Hạn chế PII trong SMS/QR và log. Không ghi toàn bộ payload thanh toán hoặc thông tin thẻ vào application log.
- Không thay đổi tiền/tồn trong worker in/SMS/hóa đơn. Chỉ transaction bán hàng được phép làm nghiệp vụ tương ứng.
- Chốt phương án vận hành khi thiếu thiết bị in thật: stub/sandbox demo gắn nhãn và mô phỏng lỗi/timeout có bằng chứng QA.

## 7. Ma trận test đề xuất (chưa chạy)

| Mã | Kịch bản | Kết quả kỳ vọng |
|---|---|---|
| D56-T01 | PAID + in thành công | Receipt đúng đơn, print job success, không đổi tồn |
| D56-T02 | PAID + máy in lỗi | Đơn vẫn PAID, có print error, có thể in lại |
| D56-T03 | In lại 2 lần | order_id và dòng hàng không đổi, nhãn reprint |
| D56-T04 | Thanh toán PENDING rồi bấm in | Không tạo biên nhận xác nhận PAID |
| D56-T05 | Hóa đơn adapter timeout | UNKNOWN, tra cứu provider_ref trước khi retry |
| D56-T06 | Callback hóa đơn lặp | Một trạng thái phát hành được ghi nhận |
| D56-T07 | SMS lỗi sau kích hoạt bảo hành | Bảo hành không bị xóa, outbox lưu lỗi/retry |
| D56-T08 | Kích hoạt bảo hành hai lần | Một bảo hành hiệu lực, không phát SMS lặp tùy tiện |
| D56-T09 | Nhân viên khác siêu thị | 403 tại API, không rò dữ liệu |
| D56-T10 | Mất mạng máy in, khởi động lại | Audit/job tra cứu được, không tái tạo đơn |

## 8. Quyết định chưa chốt / nghiệm thu

- [ ] BA/PO xác nhận chính sách thuế, chiết khấu, dữ liệu bắt buộc trên biên nhận và hóa đơn.
- [ ] Architect xác nhận bảng vật lý/outbox, runtime ACL, transaction boundary và phương án retry.
- [ ] Chọn máy in/driver, cơ chế kết nối (USB/network/print agent) và mẫu khổ in thực tế.
- [ ] Chọn provider sandbox/stub cho SMS và hóa đơn, quy tắc callback signature, token và mô hình đối soát.
- [ ] QA kiểm thử T01–T10 và lưu log/ảnh; **chưa có E2E hoặc tích hợp nhà cung cấp thật**.
- [ ] PO ký/xác nhận thiết kế; cập nhật change request khi baseline thay đổi.
