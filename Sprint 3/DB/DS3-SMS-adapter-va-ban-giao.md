# DS3 — SMS adapter và bàn giao tích hợp

Phần S3-08 của Trần Trung Kiên, hỗ trợ S3-06/S3-07. Ngày thực hiện: **21/11/2026**; kỳ kế hoạch Sprint 3: 25/10–07/11/2026. Căn cứ [D56](../../Sprint%200/Fullstack/TV4/docs/sprint0/d56/D56_INTEGRATION_DESIGN_EINVOICE_SMS_PRINTER.md). D56 chưa chọn nhà cung cấp SMS, tài khoản sandbox hoặc Brandname. Mã hiện có chạy được ở chế độ mô phỏng và cung cấp hợp đồng HTTP gateway chung; chưa chứng minh tương thích API của một nhà cung cấp Brandname cụ thể, thuê bao đã nhận tin hoặc nghiệm thu DS3/M5.

## Thành phần bàn giao

| Thành phần | Trách nhiệm |
|---|---|
| [SmsAdapter](../../backend/src/main/java/com/smartelectronicsupermarket/sms/SmsAdapter.java), [SmsRequest](../../backend/src/main/java/com/smartelectronicsupermarket/sms/SmsRequest.java), [SmsResult](../../backend/src/main/java/com/smartelectronicsupermarket/sms/SmsResult.java) | Ranh giới gọi nội bộ từ worker, input có UUID sự kiện ổn định, destination E.164 và template/message |
| [SmsConfiguration](../../backend/src/main/java/com/smartelectronicsupermarket/sms/SmsConfiguration.java), [SmsProperties](../../backend/src/main/java/com/smartelectronicsupermarket/sms/SmsProperties.java) | Mặc định DISABLED; MOCK và HTTP phải bật tường minh; kiểm cấu hình khi khởi động |
| [HttpSmsAdapter](../../backend/src/main/java/com/smartelectronicsupermarket/sms/HttpSmsAdapter.java) | POST HTTPS, timeout, giới hạn phản hồi 16 KiB, không theo redirect, mỗi lần gọi đúng một attempt |
| [SmsOutboxWorker](../../backend/src/main/java/com/smartelectronicsupermarket/sms/SmsOutboxWorker.java), [SmsWorkerConfiguration](../../backend/src/main/java/com/smartelectronicsupermarket/sms/SmsWorkerConfiguration.java) | Poll tối đa 10 sự kiện/lượt, claim và finish ở hai transaction riêng; worker mặc định tắt |
| [V007](../../database/migrations/V007__sms_outbox.sql) | Trạng thái gửi riêng trên outbox; claim `SKIP LOCKED`, kiểm attempt khi finish; retry FAILED cần actor đúng quyền |
| [Runtime role](../../database/sprint3/runtime-roles.sql) | Group NOLOGIN `sasms_sms_worker` chỉ được gọi claim/finish |

Không có endpoint HTTP công khai để gửi SMS tùy ý. Backend nghiệp vụ ghi sự kiện `WarrantyActivated` hoặc `DeliveryScheduled` cùng transaction bảo hành/lập lịch; outbox không chứa số điện thoại hay provider secret. Claim chỉ đọc số điện thoại khách tại thời điểm gửi và dựng thông điệp tối thiểu từ dữ liệu đã lưu. Số Việt Nam dạng `0xxxxxxxxx` được SQL chuyển sang `+84xxxxxxxxx`; adapter kiểm E.164. Không đưa địa chỉ giao hàng, tên khách hay toàn bộ hồ sơ bảo hành vào nội dung.

## Cấu hình

Spring Boot hỗ trợ property trong secret/config bên ngoài Git hoặc biến môi trường tương ứng. Không cần sửa `application.properties` để đổi chế độ.

| Property | Mặc định / yêu cầu |
|---|---|
| `sasms.sms.mode` / `SASMS_SMS_MODE` | `DISABLED`; chọn rõ `MOCK` hoặc `HTTP` |
| `sasms.sms.worker.enabled` / `SASMS_SMS_WORKER_ENABLED` | `false`; `true` chỉ khi đã provision DB V001…V007 và worker role |
| `sasms.sms.worker.poll-delay-ms` / `SASMS_SMS_WORKER_POLLDELAYMS` | 5000 ms, fixed delay; tối đa 10 sự kiện mỗi poll |
| `sasms.sms.endpoint` / `SASMS_SMS_ENDPOINT` | HTTP mode bắt buộc; URL HTTPS tuyệt đối, không userinfo/query/fragment |
| `sasms.sms.api-key` / `SASMS_SMS_APIKEY` | HTTP mode bắt buộc; secret runtime 1–512 ký tự ASCII hiển thị, không khoảng trắng/control character |
| `sasms.sms.brandname` / `SASMS_SMS_BRANDNAME` | HTTP mode bắt buộc; 1–32 ký tự chữ/số/khoảng trắng/`_`/`-`; giá trị phải do nhà cung cấp cấp phép |
| `sasms.sms.connect-timeout` / `SASMS_SMS_CONNECTTIMEOUT` | `2s`, lớn hơn 0 và không quá 30s |
| `sasms.sms.request-timeout` / `SASMS_SMS_REQUESTTIMEOUT` | `5s`, lớn hơn 0 và không quá 30s |

Ví dụ kiểm mô phỏng qua worker: đặt `SASMS_SMS_MODE=MOCK`, `SASMS_SMS_WORKER_ENABLED=true`; dùng profile `postgresql` và các biến `SASMS_DB_URL`, `SASMS_DB_USERNAME`, `SASMS_DB_PASSWORD` từ secret môi trường. Worker bật cùng `DISABLED` làm khởi động thất bại để tránh tiêu thụ hàng đợi do bật nhầm. Không bật worker trước khi migrations và quyền đã sẵn sàng.

## Provision và tách tiến trình worker

Owner áp migrations bằng migration runner, sau đó:

```powershell
psql -X --dbname $env:DATABASE_URL -v ON_ERROR_STOP=1 -f database/sprint3/runtime-roles.sql
```

DBA tạo login riêng bằng công cụ quản lý secret, không dùng owner hoặc tài khoản của ứng dụng Web. Ví dụ tên login mới `sasms_sms_runtime`:

```sql
CREATE ROLE sasms_sms_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
GRANT sasms_sms_worker TO sasms_sms_runtime;
-- Cấp CONNECT trên đúng database triển khai nếu database đã thu hồi PUBLIC CONNECT.
-- Thiết lập password/credential bằng secret manager hoặc lệnh \password tương tác;
-- không ghi password vào script/evidence.
```

Không cấp `sasms_app` hoặc SELECT trực tiếp bảng khách hàng cho worker. `sasms_app` không được gọi claim/finish; chỉ hàm DB `SECURITY DEFINER` đã hạn chế search_path đọc dữ liệu cần thiết. Mỗi môi trường kiểm lại role membership/quyền cũ trước khi sử dụng login tái dùng.

Worker hiện dùng `JdbcTemplate`/transaction manager của datasource ứng dụng. Vì vậy chạy **một tiến trình backend riêng** với login worker, bind `127.0.0.1:8081`, giữ cấu hình JWT trống để các route nghiệp vụ đóng mặc định; tiến trình Web/POS giữ datasource và cấu hình riêng, worker tắt. Ví dụ sau khi build JAR:

```powershell
java -jar backend/target/pos-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=postgresql --server.address=127.0.0.1 --server.port=8081 --sasms.sms.mode=MOCK --sasms.sms.worker.enabled=true
```

Các biến datasource phải được cấp riêng cho tiến trình đó. Ví dụ chỉ mô phỏng; không dùng MOCK làm bằng chứng gửi provider. Không đặt `spring.main.web-application-type=none` cho cấu hình hiện tại vì security configuration của ứng dụng cần `HttpSecurity`.

## Hợp đồng HTTP gateway chung

Gateway/adapter của nhà cung cấp phải hỗ trợ hợp đồng dưới đây, hoặc Fullstack triển khai mapping riêng trước khi bật HTTP mode. Endpoint lấy từ cấu hình tin cậy của operator; không nhận URL từ trình duyệt hoặc payload outbox.

```http
POST /send
Authorization: Bearer <secret-runtime>
Content-Type: application/json; charset=utf-8
Accept: application/json
Idempotency-Key: 00000000-0000-4000-8000-000000000321
```

Payload mẫu hoàn toàn mô phỏng; UUID và thuê bao không phải bằng chứng gửi thực tế:

```json
{
  "eventId": "00000000-0000-4000-8000-000000000321",
  "destination": "+84901234567",
  "templateId": "WAR-12",
  "message": "Bao hanh WAR-12 da kich hoat. Ma: <warranty-uuid>. Tu 2026-11-21 den 2027-11-21.",
  "brandname": "SASMS"
}
```

HTTP 200 hoặc 202 chỉ được hiểu là provider nhận xử lý khi body đúng dạng:

```json
{"status":"accepted","messageId":"provider-message-123"}
```

`messageId` giới hạn 1–128 ký tự `[A-Za-z0-9_.:-]`. Body không đúng dạng, body quá 16 KiB, timeout, mất kết nối, redirect, HTTP 409/429/5xx hoặc kết quả khác đều là `UNKNOWN`, cần đối soát trước khi gửi lại. HTTP 400/401/403/404/422 là `REJECTED` theo hợp đồng gateway chung, được worker lưu `FAILED`. Chỉ mapping này được kiểm trong adapter; mapping với API vendor phải được xác nhận riêng, đặc biệt nếu provider có thể nhận tin trước khi trả mã lỗi.

Adapter không ghi API key, endpoint, destination, message, provider body hoặc exception chứa PII vào log. `toString()` của request/config/result che dữ liệu nhạy cảm. Không cấu hình HTTP wire logging hoặc SQL parameter logging trong tiến trình worker. TLS dùng trust store JVM; không có chế độ bỏ kiểm chứng certificate. Không có callback delivery receipt trong phạm vi mã hiện tại.

## Transaction và trạng thái

1. Nghiệp vụ commit bảo hành/lập lịch và outbox cùng nhau. SMS không tham gia transaction quyết định bảo hành, tiền hoặc tồn.
2. Worker chạy ngoài transaction caller (`NOT_SUPPORTED`), mở transaction `REQUIRES_NEW` để gọi `claim_sms_notification()`; DB khóa một sự kiện QUEUED bằng `FOR UPDATE SKIP LOCKED`, chuyển PROCESSING và tăng attempt. **Commit claim trước khi gọi HTTP**.
3. Adapter thực hiện một lần gọi. Cùng event UUID được giữ làm `Idempotency-Key`; không tự sinh UUID mới khi lỗi.
4. Worker mở transaction `REQUIRES_NEW` khác gọi `finish_sms_notification(event, attempt, status, provider_ref, error_code)`. DB kiểm đúng claim/attempt và event type.

| Kết quả | Trạng thái DB | Ý nghĩa |
|---|---|---|
| Chế độ MOCK | `MOCK_ACCEPTED` | Mô phỏng nội bộ; không gọi provider, không gửi tin |
| Provider hợp lệ trả accepted | `ACCEPTED` | Đã nhận xử lý; chưa chứng minh giao tới thuê bao |
| Payload lỗi trước HTTP hoặc provider từ chối rõ | `FAILED` | Có thể sửa nguyên nhân rồi yêu cầu retry có quyền |
| Kết quả gửi không xác định | `UNKNOWN` | Có khả năng tin đã gửi; cần đối soát |
| Tiến trình chết sau claim | `PROCESSING` | Có thể chết trước hoặc sau HTTP; cần đối soát |

`published_at` khi ACCEPTED chỉ ghi nhận chấp nhận xử lý. Không có trạng thái `DELIVERED` được tự suy ra. Bảo hành đã kích hoạt vẫn hiệu lực khi SMS FAILED/UNKNOWN.

Không tự chạy lại `UNKNOWN` hoặc claim `PROCESSING` quá hạn. Operator tra provider bằng event UUID/provider reference, lưu bằng chứng đối soát và xử lý theo runbook được DBA/PM duyệt; chưa có API tự động recovery cho hai trạng thái này. Lỗi finish sau HTTP để lại PROCESSING và cũng thuộc quy trình đó.

Chỉ `FAILED` được yêu cầu đưa lại QUEUED bằng `sasms.requeue_failed_sms(event_uuid, actor_uuid)`. Hàm kiểm actor active, quyền `warranty.manage` hoặc `logistics.manage` trong store của sự kiện và ghi audit. Tối đa **3 attempt tổng cộng**; đã đạt ngưỡng sẽ từ chối. Việc sửa dữ liệu/quyền/config phải hoàn tất trước khi requeue. MOCK_ACCEPTED/ACCEPTED không được gửi lại bằng hàm này. Không tự retry trong HTTP adapter, không backoff vô hạn và không xóa dấu UNKNOWN để ép resend.

## Kiểm thử và phần chờ nghiệm thu

Hai lớp [SmsAdapterTest](../../backend/src/test/java/com/smartelectronicsupermarket/sms/SmsAdapterTest.java) và [SmsOutboxWorkerTest](../../backend/src/test/java/com/smartelectronicsupermarket/sms/SmsOutboxWorkerTest.java) kiểm cấu hình đóng mặc định, mock rõ nhãn, validation, HTTP contract, idempotency header, timeout/redirect/error mapping, giới hạn body, che dữ liệu và thứ tự commit trước/sau adapter. Các HTTP test dùng client được mô phỏng, không liên lạc nhà cung cấp. Chạy từ `backend` với Maven/JDK tương thích `pom.xml`:

```powershell
.\mvnw.cmd '-Dtest=SmsAdapterTest,SmsOutboxWorkerTest' test
```

Lượt kiểm ngày **21/11/2026** đạt **18/18 test SMS**: 12 adapter, 5 worker và 1 [tích hợp PostgreSQL](../../backend/src/test/java/com/smartelectronicsupermarket/sms/SmsPostgreSqlIntegrationTest.java). Bộ backend đầy đủ có **279 ca, 263 đạt, 16 bỏ qua, 0 lỗi**; nhóm bảo mật/SMS có 99 ca đạt. Môi trường dùng Maven cache 3.9.15, chế độ offline, JDK 24 với override `-Djava.version=24`; `pom.xml` vẫn yêu cầu Java 25. [Evidence kỹ thuật](evidence/security/summary.json) và [toàn bộ backend](evidence/security/full-backend-summary.json) ghi suite, lệnh và giới hạn.

Ca PostgreSQL thực thi reserve/checkout/activate trên dữ liệu mô phỏng đã commit; worker claim, kết nối khác xác nhận PROCESSING đã commit trước khi adapter MOCK chạy, sau đó lưu MOCK_ACCEPTED. Poll lần hai không gửi lại; PAID/SOLD/ACTIVE được giữ. Test dọn đúng UUID và khôi phục guard payment trong cùng transaction dọn fixture. Để chạy trên DB thử đã nâng cấp, đặt `SASMS_S3_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/sasms_s3_validation`, `SASMS_S3_JDBC_USER` và `SASMS_S3_JDBC_PASSWORD` rồi chạy Maven với `-Dtest=SmsPostgreSqlIntegrationTest`. Queue thử phải trống; không chạy fixture trên DB nghiệp vụ. Không có HTTP/provider thật trong ca này.

Người dùng đã chọn **dùng mô phỏng để bàn giao trước**. Khi chuyển sang SMS thật, nhóm cần chọn vendor/Brandname và gói thử, map contract vendor, cấu hình secret/TLS, gửi tới thuê bao kiểm thử đã được phép, lưu provider reference và xác nhận nhận tin đã che số. QA/PO kiểm luồng kích hoạt/lập lịch E2E, video demo, PR merge và ký Review theo quy trình nhóm. Tài liệu này không thay chữ ký hoặc hợp đồng nhà cung cấp.
