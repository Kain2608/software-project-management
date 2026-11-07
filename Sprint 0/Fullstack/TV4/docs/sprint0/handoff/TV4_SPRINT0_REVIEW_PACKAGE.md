# SASMS Sprint 0 TV4 Hồ sơ chờ review

**Ngày tổng hợp:** 07/11/2026

**Repository đích hiện tại:** `D:\software-project-management-an`, nhánh `feat/tv4-sprint0-handoff` (tài liệu đặt tại `Sprint 0/Fullstack/TV4/docs/sprint0/`)

**Repository nguồn tham chiếu (nơi thu thập bằng chứng build/test lịch sử):** `D:\smart-electronic-supermarket-sprint0`, nhánh `feat/tv4-sprint0-postgresql`, commit `12918f7`

**Trạng thái hồ sơ:** Đủ để mở **Draft PR** và xin review phần tài liệu/thiết kế và khung source skeleton; chưa đủ để chuyển Ready for Review, merge hoặc ghi nghiệm thu. Phần mã nguồn `backend/` và `frontend/` **đã được tích hợp vào root repo của An ở Phase 2** theo đồng ý `A1, B1, C2` của PM Trịnh Quốc An (xem [TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md](../../../TV4_PHASE2_SOURCE_INTEGRATION_REPORT.md)), đang chờ Architect/DBA Trần Trung Kiên, BA/PO Nguyễn Quang Huy và QA Phạm Đại Phát review (`PENDING`, chưa `APPROVED`).

## 1. Mục đích và nguyên tắc

Hồ sơ này tập hợp đầu ra TV4, minh chứng kỹ thuật đã đạt, các quyết định còn PENDING và checklist cho BA/PO/Architect/QA. Kết quả PASS trong báo cáo khắc phục được giữ nguyên theo đúng lịch sử chạy tại **repository nguồn**, đồng thời ghi rõ hạng mục nào đã chạy PASS tại **repository đích của An** trong Phase 2 (`D52 Redocly`, `mvnw.cmd -o test`, `npm.cmd ci --offline`, `npm.cmd run build`) và hạng mục nào còn chờ Phase 3 (`PostgreSqlConnectionSmokeTest` với DB login thuộc `sasms_app`). Không dùng hồ sơ này để tự ghi `APPROVED`, xác nhận Figma đã QA hoặc tuyên bố tích hợp nhà cung cấp đã hoạt động.

Hai báo cáo nguồn (nằm cùng bộ tại `Sprint 0/Fullstack/TV4/docs/sprint0/`):

- [TV4_SPRINT0_FINAL_TECHNICAL_AUDIT.md](../TV4_SPRINT0_FINAL_TECHNICAL_AUDIT.md): ảnh chụp trạng thái tại thời điểm kiểm toán ban đầu trên repo nguồn.
- [TV4_SPRINT0_REMEDIATION_REPORT.md](../TV4_SPRINT0_REMEDIATION_REPORT.md): kết quả khắc phục kỹ thuật sau kiểm toán trên repo nguồn; tài liệu này là nguồn mới hơn cho D52, frontend build và JDBC smoke test.

## 2. Danh sách deliverable

| Mã | Đầu ra | File chính tại repo đích (`Sprint 0/Fullstack/TV4/docs/sprint0/...`) | Trạng thái kỹ thuật | Trạng thái review |
| --- | --- | --- | --- | --- |
| D52 | OpenAPI Inventory/POS/AI | [d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml](../d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml) và [d52/D52_API_DESIGN_NOTES.md](../d52/D52_API_DESIGN_NOTES.md) | Redocly PASS (đã chạy lại tại vị trí mới trong repo An), 0 error/2 warning; `$ref`, operationId, 401 PASS | PENDING BA/Architect/QA |
| D53 | Mockup Kho/QR/POS | 3 đặc tả trong `d53/` ([D53_SCREEN_SPEC.md](../d53/D53_SCREEN_SPEC.md), [D53_DECISION_REGISTER.md](../d53/D53_DECISION_REGISTER.md), [D53_FIGMA_HANDOFF.md](../d53/D53_FIGMA_HANDOFF.md)); manifest/evidence/B10 checklist trong `handoff/` | Có 52 record/link trong manifest; visual/prototype NOT VERIFIED | PENDING QA và PO từng nhóm |
| D56 | Integration Design | [d56/D56_INTEGRATION_DESIGN_EINVOICE_SMS_PRINTER.md](../d56/D56_INTEGRATION_DESIGN_EINVOICE_SMS_PRINTER.md) | Có matrix/callback/retry/timeout/error/recovery ở mức design | PENDING provider, BA/PO/Architect/QA |
| D63 | Coding/Git/PR conventions | [D63_CODING_GIT_PR_CONVENTIONS.md](../D63_CODING_GIT_PR_CONVENTIONS.md) | Đã điều chỉnh khớp quy ước `Sprint N/<Vai trò>/` của repo An; không tự nhận CI/protection | PENDING PM/Architect |
| D64 | Source skeleton | [D64_REPOSITORY_SOURCE_SKELETON.md](../D64_REPOSITORY_SOURCE_SKELETON.md) (source `backend/` & `frontend/` đã tích hợp tại root repo An) | Bộ DB của Kiên giữ nguyên 100%; `backend/` (`16` file + `.gitignore`) & `frontend/` (`14` file + `.gitignore`) **đã tích hợp tại root repo An ở Phase 2** và build/test offline PASS | PM An đã đồng ý `A1, B1, C2`; PENDING Kiên (Architect/DBA) & QA |
| D65 | Development environment | [D65_DEVELOPMENT_ENVIRONMENT.md](../D65_DEVELOPMENT_ENVIRONMENT.md) | Đã cập nhật kết quả chạy `mvnw.cmd -o test` và `npm ci` + `npm run build` tại repo An (Phase 2), cách ly Docker `-p tv4_s0_check` | PM An đã đồng ý `B1, C2`; PENDING Kiên (Architect/DBA) cấp login `sasms_app` & QA review |

## 3. Minh chứng kiểm thử đã đạt (phân biệt repo nguồn và repo đích)

> **Lưu ý:** Trong Phase 2, `backend/` và `frontend/` đã được tích hợp sang **repository đích** (`D:\software-project-management-an`) và đã chạy xác minh thành công `mvnw.cmd -o test` (`BUILD SUCCESS`), `npm.cmd ci --offline` và `npm.cmd run build`. Riêng kiểm thử runtime với Docker/PostgreSQL (`PostgreSqlConnectionSmokeTest`) chưa bật tại repo đích trong Phase 2 để bảo vệ tuyệt đối môi trường DB và chờ DBA Kiên cấp phát tài khoản thuộc group role `sasms_app` ở Phase 3.

| Hạng mục | Lệnh/bằng chứng | Kết quả tại repo nguồn | Trạng thái tại repo đích (An — Phase 2) | Giới hạn |
| --- | --- | --- | --- | --- |
| OpenAPI semantic validation | `npx.cmd --offline -y @redocly/cli lint "Sprint 0/Fullstack/TV4/docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml"` | PASS, 0 error, 2 warning | **PASS (đã chạy lại tại repo An)** | Chưa BA/Architect approve contract |
| OpenAPI structure | Kiểm tra YAML parse, `$ref`, operationId, 401 | 97 ref resolve; 12 operationId duy nhất; 0 operation thiếu 401 | **PASS (đã chạy lại tại repo An)** | Không chứng minh API đã code (`DEFERRED TO FUTURE SPRINT`) |
| Frontend dependency | `npm.cmd ci --offline` | PASS, 88 package | **PASS (88 packages, 0 vulnerabilities tại `frontend/`)** | Dùng lockfile hiện tại; không đổi version |
| Frontend build | `npm.cmd run build` | PASS, Vite 5.4.21, 92 module | **PASS (Vite 5.4.21, 92 modules, 835 ms tại `frontend/`)** | Không phải UI/E2E test (`DEFERRED TO FUTURE SPRINT`) |
| Backend test | `.\mvnw.cmd -o test` | BUILD SUCCESS; context test PASS | **PASS (`BUILD SUCCESS`, Tests run: 2, Skipped: 1, 4.472 s tại `backend/`)** | `SmartElectronicSupermarketApplicationTests` PASS; JDBC smoke SKIPPED khi không bật env |
| PostgreSQL precheck | Compose `ps`, `pg_isready`, read-only query (`-p sasms_s0_check`) | Healthy; PG 16.15; DB sasms; 37 bảng | Đã có `database/evidence/` của Kiên; TV4 **NOT RUN** lại trong Phase 2 | Không chạy đè lên DB của Kiên |
| JDBC smoke | Test riêng với biến bật, loopback DB, transaction read-only | 1 test, 0 fail/error/skip; BUILD SUCCESS | **SKIPPED / NOT RUN against live DB** (chờ cấp login thuộc `sasms_app` ở Phase 3) | Không chứng minh Repository/JPA/Kho/POS E2E (`DEFERRED TO FUTURE SPRINT`) |
| Schema/seed/compose integrity | SHA-256 so với repo đích | PASS | **PASS (trùng khớp 100% với bản của Kiên)** | Không phải phê duyệt nghiệp vụ 37 bảng |
| Compose config | `docker compose config --quiet` | PASS | File giống hệt nguồn | Không chứng minh full deployment |

Không có bằng chứng CI remote, branch protection, API nghiệp vụ, D56 provider integration hoặc Kho/POS E2E.

## 4. D53 traceability và bằng chứng

### 4.1 Phân loại trạng thái

| Thuật ngữ | Ý nghĩa hiện tại |
| --- | --- |
| Frame có record | Có dòng tương ứng trong manifest do công cụ tạo |
| Có link | Có URL node Figma được ghi trong manifest |
| Frame có thật/hiển thị đúng | NOT VERIFIED độc lập trong lần kiểm toán |
| QA PASS | Chưa có frame nào được ghi PASS |
| PO APPROVED | Chưa có; toàn bộ sáu nhóm PENDING |

Link Figma: `https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55`.

### 4.2 Độ phủ manifest

| Nhóm | Số record/link | Mobile được ghi rõ | Vấn đề review |
| --- | ---: | --- | --- |
| A Inventory | 7 | A07 | Cần map lại thứ tự A02-A06 với handoff |
| B Receiving | 10 | B10 | B10 nguy cơ overflow; handoff dự kiến hai trạng thái mobile nhưng manifest có một |
| C QR | 10 | Chưa có dấu vết | Cần xác minh mobile camera/result và forbidden |
| D POS | 9 | Chưa có dấu vết | Cần mobile cart, populated cart, uncertain reservation; TTL vẫn PENDING |
| E Payment | 8 | Chưa có dấu vết | Cần mobile status/forbidden theo handoff |
| F Receipt | 8 | Chưa có dấu vết | Cần mobile receipt, print success và quyết định 80 mm |

`Chưa có dấu vết` chỉ là kết luận từ manifest, không khẳng định Figma không có layer/layout khác. QA/PO phải kiểm tra trực tiếp.

### 4.3 B10

Checklist riêng: [D53_B10_MOBILE_QA_CHECKLIST.md](D53_B10_MOBILE_QA_CHECKLIST.md) (`Sprint 0/Fullstack/TV4/docs/sprint0/handoff/D53_B10_MOBILE_QA_CHECKLIST.md`).

Trạng thái: `NOT VERIFIED`; PO: `PENDING`. Không được đổi trạng thái nếu chưa có người kiểm tra, viewport, version Figma và link bằng chứng.

## 5. D56 ma trận review tích hợp

| Tích hợp | Trigger nghiệp vụ đã có căn cứ | Idempotency/retry/recovery | Nội dung còn PENDING |
| --- | --- | --- | --- |
| Hóa đơn điện tử | Đơn PAID và yêu cầu hợp lệ; tách khỏi biên nhận POS | Cùng logical request/key; timeout → UNKNOWN/PENDING; tra provider_ref trước retry; callback chống replay | Provider/sandbox, payload, key format, timeout/backoff/max attempt, signature, permission |
| SMS bảo hành | Bảo hành đã kích hoạt hợp lệ; SMS lỗi không rollback bảo hành | Một outbox event; retry transient cùng event; accepted chưa phải delivered; manual review/DLQ | Provider, template, consent/policy, delivery callback, retry numbers, permission |
| Máy in | In/reprint biên nhận của đơn PAID | `print_job_id`; retry job khác reprint; offline không đổi order/inventory | Model/driver/USB-network-agent, timeout/ack, 80 mm format, permission |

Callback chỉ vào backend/adapter, phải xác minh source/signature/timestamp hoặc replay control, provider event ID và provider_ref. Exact endpoint/header/algorithm/status mapping còn PENDING; không có tích hợp thật hoặc test D56-T01..T10 được ghi PASS.

Payment adapter không nằm trong ba adapter D56 hiện tại. Architect/PM cần xác định đưa payment callback vào D56 hay tài liệu riêng.

## 6. So sánh JDK 17 và Java 26

| Tiêu chí | JDK 17 | Java 26 hiện tại | Ảnh hưởng cần xem xét |
| --- | --- | --- | --- |
| Căn cứ | Workbook D65 yêu cầu JDK 17 | `backend/pom.xml` đặt `<java.version>26`; máy kiểm tra dùng 26.0.2.1 | Hai nguồn chưa thống nhất; không tự coi bên nào là baseline cuối |
| Build hiện tại | Với pom target 26, JDK 17 không thể compile target 26 | Maven test hiện PASS trên JDK 26 | Chọn JDK 17 đòi thay pom/source/dependency qua CR; chọn 26 đòi cập nhật workbook/baseline |
| CI | Phổ biến và dễ tìm runner/image hơn, nhưng phải kiểm tra Spring Boot/dependency mục tiêu | CI phải có JDK 26 cụ thể; runner thiếu sẽ fail trước test | Pipeline phải pin đúng major; không dùng `latest` |
| Docker backend | Chưa có Dockerfile backend trong skeleton | Chưa có Dockerfile backend trong skeleton | Khi tạo image phải chọn JRE/JDK cùng major với bytecode; hiện chưa thể ghi image chuẩn |
| Máy thành viên | Có thể phù hợp yêu cầu học phần ban đầu | Tất cả thành viên phải cài/nhận toolchain 26 hoặc dùng image/CI thống nhất | Không đồng bộ sẽ gây lỗi class version/toolchain |
| Tính tái lập | Cần Maven/toolchain và source target 17 được duyệt | Hiện build đã chứng minh trên JDK 26 + Maven Wrapper | D65 phải phản ánh lựa chọn sau quyết định; evidence máy hiện tại không tự thành baseline |
| Rủi ro thay đổi | Hạ target có thể phát hiện API/language/dependency không tương thích | Giữ 26 có rủi ro môi trường nhóm/CI chưa sẵn | Cần thử build trên môi trường được chọn trước khi chốt |

### Phương án trình review

1. **Phương án A - giữ Java 26:** Architect xác nhận Spring Boot/dependency/CI support; PM cập nhật baseline D65/workbook; nhóm thống nhất toolchain hoặc image. Không cần thay code trong hồ sơ review.
2. **Phương án B - về JDK 17:** Tạo change request riêng để đổi pom và kiểm tra tương thích source/Spring Boot/dependency; chạy lại toàn bộ build/test. Không thực hiện trong task tài liệu này.
3. **Phương án C - điều tra bằng CI matrix 17/26:** Chỉ dùng để thu thập bằng chứng trước quyết định; không gọi cả hai là baseline và không merge thay đổi version chưa duyệt.

**Trạng thái:** PENDING PM/Architect. Hồ sơ này không đề xuất tự đổi baseline.

## 7. D52 quyết định và warning còn lại

Kết quả kỹ thuật giữ nguyên: Redocly PASS, 0 error/2 warning; 97 `$ref` hợp lệ; 12 operationId duy nhất; security/401 nhất quán.

Hai warning:

| Warning | Lý do chưa sửa | Owner |
| --- | --- | --- |
| Thiếu `info.license` | Chưa có quyết định license dự án | PM/Sponsor |
| Server là localhost | Đúng phạm vi local DRAFT; Dev/Staging/Prod URL chưa chốt | Architect/PM |

Các API decision chi tiết nằm trong `D52_API_DESIGN_NOTES.md`, gồm mapping role/status/Serial/IMEI2/TTL/receiving/QR/payment/money/quality, FR-09 scope và payment integration ownership. Tất cả vẫn PENDING; không thêm path/schema trước quyết định.

## 8. Danh sách vấn đề PENDING và owner

| Mã/nhóm | Nội dung | Người quyết định/review | Bằng chứng đóng |
| --- | --- | --- | --- |
| D53 DEC-01..10 | Role, status, Serial, IMEI2, TTL, receiving, QR, payment, money, quality | BA/PO + Architect | Comment/quyết định có ngày và phiên bản |
| D53 visual | 52 frame, mobile C-F, B10, prototype | QA + PO | Checklist/ảnh/comment đúng node và version |
| D56 provider | E-invoice/SMS/print provider/device/sandbox | PM/PO + Architect | Provider doc, sandbox/device decision |
| D56 contract | Callback signature, timeout/retry/DLQ, permission | Architect/Security + QA + BA | Contract và test matrix được review |
| JDK | JDK 17 hay Java 26 | PM + Architect | Baseline và CI/toolchain evidence |
| D52 warning | License và non-local server | PM/Sponsor + Architect | OpenAPI cập nhật và lint lại |
| D52 API | API-P01..P12 | BA/PO + Architect/DBA + QA | Review path/schema/error/security và change record |
| D63 | Branch/merge/CI/protection/coverage | PM + Architect + QA | Quy ước được duyệt, evidence cấu hình thật |
| D64/D65 | Runtime DB role, migration, fresh-machine setup | Architect/DBA + QA | ACL/migration decision và log setup/test |

## 9. Checklist BA PO Architect QA

### BA

- [ ] Đối chiếu D52 path/request/response/error với D34/D35/D40.
- [ ] Chốt hoặc ghi owner/date cho các decision API-P01..P12.
- [ ] Đối chiếu nội dung 52 frame với FR-03..FR-07 và D35.
- [ ] Xác nhận nội dung receipt/e-invoice/SMS không thay business rule.

### PO

- [ ] Review từng nhóm A-F bằng link Figma, không duyệt chỉ dựa trên số lượng 52.
- [ ] Ghi comment/ảnh/biên bản đúng frame và version.
- [ ] Xác nhận payment/receipt và phạm vi provider demo/sandbox.
- [ ] Không ghi APPROVED khi B10/mobile/prototype chưa có bằng chứng.

### Architect DBA

- [ ] Review D52 security, ID/schema mapping, transaction/idempotency và callback boundary.
- [ ] Chốt owner của payment integration.
- [ ] Review D56 adapter/outbox/runtime ACL/retry/recovery, không tự thêm bảng ngoài baseline.
- [ ] Chốt JDK 17/Java 26, CI/toolchain và Docker runtime hướng tới.
- [ ] Chốt runtime DB role và migration policy.

### QA

- [ ] Lưu Redocly output và kiểm contract test plan; không nhầm spec PASS với API implemented.
- [ ] QA trực quan 52 frame; ưu tiên B10 và mobile C-F.
- [ ] Xác minh prototype P1-P4 hoặc ghi NOT RUN.
- [ ] Review/run D56-T01..T10 khi có stub/sandbox; hiện không ghi PASS.
- [ ] Xác nhận backend/frontend/JDBC evidence trong môi trường review.

## 10. Điều kiện Draft PR

### Đã đủ

- D52 semantic validation không còn error.
- Frontend build, backend test, Compose config và JDBC smoke có bằng chứng PASS trong phạm vi.
- Schema/seed giữ nguyên và khớp INPUT.
- PENDING được ghi rõ owner, tác động và bằng chứng cần có.
- Không tự ghi QA/PO/Architect approved.

### Chưa đủ cho Ready for Review hoặc merge

- D53 chưa visual QA/PO approve; B10/mobile C-F/prototype chưa có bằng chứng.
- D56 chưa chọn provider/sandbox/device và chưa chạy test.
- JDK, runtime DB role, migration và API business decisions chưa chốt.
- Chưa có CI/branch protection/remote PR evidence.

**Kết luận:** Hồ sơ **ĐỦ ĐIỀU KIỆN MỞ DRAFT PR** để lấy review, với tiêu đề/mô tả ghi rõ `DRAFT - NOT APPROVED - PENDING BA/PO/ARCHITECT/QA`. Hồ sơ **CHƯA ĐỦ ĐIỀU KIỆN** chuyển Ready for Review, merge hoặc tuyên bố Sprint 0 nghiệm thu. Không có PR nào được mở trong lần chuẩn bị hồ sơ này.
