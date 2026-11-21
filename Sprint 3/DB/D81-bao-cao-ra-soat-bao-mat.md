# D81 — Rà soát bảo mật giai đoạn 1, Sprint 3

Phần bàn giao S3-11 của **Trần Trung Kiên — Architect / DBA**, hỗ trợ Phạm Đại Phát. Ngày thực hiện: **21/11/2026** (Asia/Bangkok); hạn kế hoạch trong workbook: 06/11/2026. Đây là kết quả kỹ thuật trên mã nguồn và kiểm thử local, chưa có chữ ký Kiên/QA/PM, kiểm thử xâm nhập Staging hay xác nhận cổng M5.

## Phạm vi và bằng chứng

Đã đọc cơ chế JWT, ánh xạ actor, quyền theo cửa hàng, JDBC, thông báo SMS, ACL PostgreSQL và cấu hình CI. Các ca Java dùng Spring/MockMvc/Mockito và khóa RSA sinh trong test; không liên hệ IdP hay nhà cung cấp SMS thật. Ngoài các ca JDBC mock, đã chạy một ca Java tích hợp PostgreSQL 16.11 cho outbox → worker → adapter MOCK; bằng chứng SQL/ACL và ba ca đồng thời nằm trong bộ DB riêng.

- **99 ca Java thuộc nhóm bảo mật/SMS đạt**, 0 lỗi, 0 bỏ qua; gồm 9 ca decoder/cấu hình JWT, các ca từ chối truy cập, actor, dữ liệu QR/hóa đơn/tồn, 12 ca SMS adapter, 5 ca worker và 1 ca PostgreSQL MOCK worker.
- Bộ backend đầy đủ: **279 ca, 263 đạt, 16 bỏ qua, 0 lỗi**. Các ca bỏ qua là 14 ca HTTP/DB S1/S2, 1 smoke PostgreSQL và 1 ca tạo evidence AI do điều kiện môi trường riêng. [Kết quả đầy đủ](evidence/security/full-backend-summary.json) và [log Maven](evidence/security/full-backend-tests.log) ghi exit code 0; không tính các ca bỏ qua là đã đạt.
- DB: **46 kiểm tra đạt** và ba ca hai kết nối; [summary SQL](../../database/evidence/sprint3/summary.json) lưu regression S0/S1/S2 và source hash.
- **7 ca frontend auth đạt**, kiểm không tự nhận đã xác thực, không nhận token URL và từ chối token giả.
- Quét mẫu token/private key trên source hiện tại: **0 khớp** trong bốn nhóm mẫu. Phạm vi và SHA-256 từng file ở [summary.json](evidence/security/summary.json); không quét lịch sử Git, dữ liệu nhị phân, tài liệu Office hay secret của môi trường, không thay thế công cụ phát hiện secret đầy đủ.
- [Tóm tắt Java](evidence/security/java-test-summary.log), [log auth frontend](evidence/security/frontend-auth.log), [script thu thập bằng chứng](evidence/security/collect_review.py).

Máy local có **Zulu OpenJDK 24.0.1**, Maven **3.9.15** và dependency cache. Dùng `-Djava.version=24` để chạy; `pom.xml` vẫn yêu cầu Java 25. Cần chạy lại bằng JDK 25 trước phát hành. Lần wrapper Maven 3.9.16 đầu tiên không tải được qua mạng; lần chạy thành công dùng Maven cache ở chế độ offline. Không lấy kết quả của các lần biên dịch lỗi trước làm bằng chứng đạt.

## Phát hiện và xử lý

Quy ước ưu tiên trong hồ sơ: P1 = có thể phá vỡ ranh giới tin cậy xác thực; P2 = cần khép lại trước phát hành hoặc có biện pháp vận hành được xác nhận. Đây là đánh giá kỹ thuật, chưa phải mức lỗi được QA/PM ký duyệt.

| Mã | Mức | Bằng chứng / tác động | Xử lý và kết quả |
|---|---|---|---|
| SEC-S3-01 | P1 | Cấu hình cũ nhận HTTP cho JWKS ở máy từ xa. Khi đường truyền bị can thiệp, khóa xác minh có thể bị thay, ảnh hưởng tính tin cậy của chữ ký. | Đã sửa `SasmsJwtProperties.java:33`: yêu cầu HTTPS, host hợp lệ, không userinfo/query/fragment. HTTP chỉ cho đúng `localhost`, `127.0.0.1`, `[::1]` để phát triển local. Test từ chối remote HTTP, host giả `localhost.evil.test`, URL chứa credential, thiếu host và fragment/query đều đạt. |
| SEC-S3-02 | P2 | Decoder cũ dùng `JwtTimestampValidator` mặc định, cho phép token hợp lệ về chữ ký nhưng thiếu `exp`; nếu IdP phát token như vậy, token không có thời điểm hết hạn. | Đã sửa `SasmsJwtDecoderFactory.java:37`: `setAllowEmptyExpiryClaim(false)`. Test token RS256 có issuer/audience/subject đúng nhưng thiếu `exp` bị từ chối; các ca chữ ký, issuer, audience, hết hạn và chưa hiệu lực tiếp tục đạt. |
| SEC-S3-03 | P2 — còn mở | Chưa có bằng chứng cấu hình gateway rate limit/quota và giới hạn request tổng thể trên môi trường triển khai. Source có giới hạn batch/AI và timeout riêng nhưng không chứng minh chống lạm dụng toàn dịch vụ. | Kiên + Thái cấu hình và Phát kiểm thử 429/quota trước mở Staging công khai; cần rate limit theo actor/IP cho API đắt tiền, hạn mức SMS và cảnh báo. Chưa đánh dấu đã xử lý. |
| SEC-S3-04 | P2 — còn mở | Chưa thực hiện audit CVE/SCA bằng cơ sở dữ liệu lỗ hổng hiện hành. Việc biên dịch/test không kết luận dependency an toàn. | Thái chạy audit dependency frontend/backend và xử lý phát hiện theo bản lock/phiên bản phát hành; Phát lưu kết quả trước Release. Chưa kết luận có/không có CVE. |
| SEC-S3-05 | P2 — còn mở | Chưa có kiểm thử trên Staging với TLS/IdP/secret thật và DAST; chưa có hợp đồng sandbox SMS được cung cấp. | Kiên + Phát xác minh TLS, rotation/revoke credential, quyền cross-store, gateway và log che PII; thử SMS được cấp phép khi có gói thử. Không ghi “đã gửi thành công” từ mock hoặc HTTP stub. |

Hai sửa đổi không chạm migration cũ đã áp dụng, không tạo secret mặc định, không cấp thêm quyền cho user. URL cấu hình IdP trước đây dùng remote HTTP hoặc query phải được chuyển sang HTTPS không chứa thông tin nhạy cảm trước khi bật xác thực; cấu hình thiếu/hỏng dừng khởi tạo.

## Kiểm soát đã đối chiếu

| Nhóm rủi ro | Kiểm soát và nguồn | Mức bằng chứng / giới hạn |
|---|---|---|
| Xác thực | `SecurityConfiguration.java:30–55` dùng Bearer resource server, stateless, tắt Basic/form/session login; chỉ bốn GET status/health được public. `SasmsJwtDecoderFactory.java:25–42` cố định RS256, kiểm issuer/audience, `exp` bắt buộc và thời gian. | Các ca fail-closed/configured JWT đạt. Không kiểm chứng kết nối JWKS thật, thu hồi token hay luân chuyển khóa tại IdP. Cho phép clock skew mặc định 60 giây của Spring. |
| Giả actor / leo quyền | `JdbcActorIdentityResolver.java:15–45` ánh xạ subject bằng query tham số hóa, yêu cầu issuer cấu hình khớp và đúng một user active; converter không lấy actor từ header hoặc role client. | Các ca subject thiếu, user không tồn tại/inactive, DB lỗi và header/role giả đạt. Cấp/thu quyền thực tế cần identity provisioning của môi trường. |
| Truy cập object/cửa hàng | `JdbcQrDeviceLookupDataAccess.java:22–28`, `JdbcReceiptDataAccess.java:31–36`, truy vấn tồn và dịch vụ POS kiểm permission ở store của object. QR là định danh, không tự cấp quyền. | Ca query/authorization đạt với fixture/mock. Chưa chứng minh toàn bộ endpoint Web/Logistics/Warranty E2E; bên Fullstack phải gọi actor đã xác thực và các hàm DB có scope. |
| SQL injection / lỗi trả về | Các query JDBC bind tham số `?`; `ApiExceptionHandler.java:25–81` ánh xạ lỗi SQL sang mã nghiệp vụ, không trả SQL/DSN/stack trace. | Ca handler và query thuộc tập chạy đạt. Không có DAST toàn API hay fuzz request quy mô lớn. |
| Token phía trình duyệt | `frontend/src/app/authContext.js:78–130` chỉ nhận session trong bộ nhớ và luôn đặt `isBackendVerified=false`; không đọc token từ browser storage/URL để cấp quyền. | 7 ca auth đạt. Hàm kiểm URL là biện pháp hỗ trợ; ranh giới quyết định vẫn ở backend. Chưa có flow đăng nhập IdP production. |
| CSRF / CORS | API dùng Bearer header, session stateless; CSRF bị tắt tại `SecurityConfiguration.java:30`. Không có cấu hình CORS wildcard trong source đã đọc. | Phù hợp đường API hiện tại không dùng cookie để xác thực. Nếu thêm cookie/session hoặc frontend khác origin, phải thiết kế lại CSRF/CORS và test trước khi bật. |
| ACL và phân nhiệm DB | `database/runtime-roles.sql:8–19`: group NOLOGIN, không superuser/CREATEDB/CREATEROLE/BYPASSRLS. V004 khóa search_path của function và bảo vệ CAPTURED; V005 giới hạn ADMIN ở IAM/audit, không tự duyệt hoàn tiền. | Review source; kết quả SQL phải đọc evidence DB riêng. `sasms_app` có SELECT rộng và tin backend thực thi scope, chưa có RLS toàn hệ thống; chỉ cấp credential này cho backend tin cậy, không cấp cho trình duyệt/người dùng cuối. |
| SMS / bí mật / gửi trùng | `sms/SmsProperties.java` mặc định DISABLED, mode HTTP yêu cầu HTTPS và secret từ cấu hình; `HttpSmsAdapter` không theo redirect, có timeout/giới hạn response, một lần gửi, kết quả mơ hồ là UNKNOWN. `SmsOutboxWorker` claim trong transaction riêng, gửi ngoài transaction và hoàn tất theo attempt. | 18 ca SMS đạt: 17 unit/contract và 1 tích hợp PostgreSQL. Ca thật kiểm kết nối khác nhìn thấy PROCESSING đã commit, adapter chạy ngoài transaction, MOCK_ACCEPTED giữ PAID/SOLD/ACTIVE và không gửi lại. Theo lựa chọn của người dùng, bàn giao dùng mô phỏng. Chưa có delivery receipt/sandbox thật; không tự gửi lại UNKNOWN/PROCESSING khi chưa đối soát. |
| Secret và CI | `.github/workflows/*` có `permissions: contents: read`; password literal hiện có được ghi rõ chỉ cho PostgreSQL service cô lập trong CI. `.env` và pgpass bị ignore. | Không tìm thấy bốn nhóm token/private key trong source đã quét. Không kiểm history/secret store/GitHub settings; literal CI không được tái dùng trên Staging/Production. Workflow readiness ghi `NOT_DEPLOYED`, không là deploy thật. |

Chín role canonical theo V005: `ADMIN`, `CATALOG_MANAGER`, `WAREHOUSE_STAFF`, `CASHIER`, `SALES_STAFF`, `DISPATCHER`, `TECHNICIAN`, `CUSTOMER_SERVICE`, `STORE_MANAGER`. Alias `WAREHOUSE`, `SALES`, `ANALYST` giữ lịch sử; không tính thành role nghiệp vụ mới và không dùng nhãn ADMIN để suy ra quyền hoàn tiền. Ma trận chi tiết vẫn lấy từ migration/quyền DB thực tế, không từ role claim client.

## Tái lập và điều kiện đóng hồ sơ

Trên JDK 25, chạy từ `backend`:

```powershell
.\mvnw.cmd '-Dtest=*Security*Test,*Identity*Test,*PermissionCheckerTest,SasmsJwtDecoderFactoryTest,ApiExceptionHandlerTest,JdbcQrDeviceLookupDataAccessTest,JdbcReceiptDataAccessTest,JdbcInventoryStockDataAccessTest,Sms*Test' clean test
```

Sau đó từ thư mục gốc repo:

```powershell
python -X utf8 'Sprint 3/DB/evidence/security/collect_review.py'
```

Script chạy lại 7 ca auth frontend, đọc báo cáo Surefire hiện có, lưu case/count và hash nguồn. `source_sha256` dùng byte local; `source_sha256_lf` chuẩn hóa CRLF thành LF để đối chiếu blob Git trên nền tảng khác. Nó **không tự chạy Maven**; chỉ tái thu thập sau lần Java test thành công. Kiểm tra version/hash trước khi sử dụng evidence cho một thay đổi khác. Nếu file nguồn thay đổi sau test, phải kiểm lại ca liên quan rồi thu thập kết quả mới.

Hồ sơ S3-11 đã có danh sách phát hiện, hai sửa đổi và kết quả kiểm kỹ thuật local. Việc đóng các mục còn mở thuộc Kiên/Thái/Phát theo trách nhiệm ở trên và phải hoàn tất **trước mở Staging công khai hoặc Release**, không hồi tố ngày kế hoạch. Cổng M5, nghiệm thu PO, chữ ký trưởng bộ phận và xác nhận người dùng tiếp tục để trống cho người có thẩm quyền.

## Căn cứ công khai

Tham khảo ngày 21/11/2026: [OWASP REST Security — HTTPS, JWT và kiểm soát truy cập](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html), [Spring Security Resource Server JWT — thuật toán và validator](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [mã nguồn JwtTimestampValidator 7.1.x — mặc định cho phép thiếu exp](https://github.com/spring-projects/spring-security/blob/7.1.x/oauth2/oauth2-jose/src/main/java/org/springframework/security/oauth2/jwt/JwtTimestampValidator.java). Áp dụng các hướng dẫn này để rà soát các kiểm soát cụ thể; chưa là chứng nhận tuân thủ OWASP đầy đủ.
