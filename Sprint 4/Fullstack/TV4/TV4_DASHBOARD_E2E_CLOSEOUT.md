# SASMS Sprint 4 — Dashboard: hardening, E2E và điều kiện đóng nghiệm thu

**Phạm vi:** TV4 Fullstack / hỗ trợ S4-03 (Dashboard AI/BI). **Ngày:** 22/11/2026.

## Kết quả đã có chứng cứ trước thay đổi này

| Nội dung | Bằng chứng | Trạng thái |
|---|---|---|
| Backend full regression | `mvnw.cmd test`: 368 chạy, 350 PASS, 18 SKIPPED, 0 FAIL | Đã chạy ở máy TV4 trước bản vá này |
| Frontend | `npm test`: 65/65 PASS, `npm run build`: SUCCESS | Đã chạy ở máy TV4 trước bản vá này |
| Sprint 2 PostgreSQL Order | 7/7 PASS | Đã chạy ở DB kiểm thử cô lập |
| AI Evidence Sprint 2–3 | 2/2 PASS | Mock LLM; không phải provider thật |
| AI/BI SQL PostgreSQL 16 | 35/35 PASS; PG16.15; `sasms_s4_ai_clean` | Đã chạy; bản sao DB được dọn 1 fixture Sprint 1, cấp GRANT và bổ sung role test |
| Dashboard Browser | Đã mở trang/bộ lọc; chưa có phiên IdP nên không thấy dữ liệu | Chưa E2E |
| QA D78 / PO | D78 hiện ghi NO-GO, chưa có ký xác nhận | Chưa nghiệm thu |

**Vị trí chứng cứ local:** `audit/ai-pg16-validation-clean/ds4-ai-dashboard-reconciliation.json`. `audit/` cần lưu an toàn, không dùng `git add .` hoặc xóa hàng loạt.

## Lỗ hổng giao diện xử lý trong bản vá TV4

| Mã | Mức | Gốc lỗi | Sửa | Test/giới hạn |
|---|---|---|---|---|
| DASH-S4-01 | P1 (bảo mật giao diện) | Request từ phiên/cửa hàng cũ có thể hoàn tất sau khi đổi context; state chưa ràng buộc store và accessToken | AbortController, sequence guard, gắn report với đúng context | Code hardening; **chưa xác minh bằng browser E2E** |
| DASH-S4-02 | P2 | Tự làm mới 60s có thể lấy bộ lọc ngày vừa sửa mà người dùng chưa bấm Tải báo cáo | Lưu khoảng ngày đã submit trong `active` và dùng khi refresh | Code hardening; manual QA cần chạy |
| DASH-S4-03 | P2 | `toISOString().slice(0,10)` lấy ngày UTC, sai ngày Việt Nam ở một số giờ | Business day `Asia/Ho_Chi_Minh` | Thêm test timezone |
| DASH-S4-04 | P2 | Người dùng nhìn thấy form trống nhưng không biết thiếu IdP/store | Thông báo trạng thái rõ ràng, vô hiệu hóa nút khi thiếu phiên hoặc ngày sai | Cần kiểm tra ảnh trực tiếp |
| DASH-S4-05 | P2 | Đầu ngày/ cuối ngày chưa có kiểm tra ngày không hợp lệ | Xây dựng khoảng `[from,to)` cho ngày Việt Nam, kiểm tra ngày thực và from<=to | Thêm test ranh giới |
| DASH-S4-06 | P2 | Khi refresh trả 401/403, UI có thể giữ số liệu đã tải | Xóa report và dừng refresh ở tình huống quyền hết hiệu lực | Code hardening; cần staging test |

**Chú ý:** Chưa thể đánh dấu các mục này `QA VERIFIED CLOSED` nếu QA Phát chưa re-test và cập nhật D80.

## Quy trình triển khai Dashboard E2E khi có quyền hợp lệ

### A. Chuẩn bị staging cô lập

1. DBA Kiên cung cấp PostgreSQL 16 staging (DB test, schema V001–V008), runtime login **thuộc role `sasms_app`**; không chạy ứng dụng bằng `sasms_owner` và không dùng DB sản xuất.
2. Review và áp dụng `database/runtime-roles.sql` trên môi trường kiểm thử, bao gồm GRANT cột `INSERT` vào `sasms.ai_recommendations`; quyền phải được DBA duyệt trước production.
3. BA Huy/Architect An phê duyệt HTTP contract S4 và việc bật riêng flag `sasms.features.store-dashboard-api.enabled=true` trong môi trường kiểm thử; mặc định giữ OFF.
4. Cấu hình IdP OIDC thật cho Backend; JWT phải được cấp bởi issuer tin cậy. `frontend/src/main.jsx` hiện chạy `<App />` không truyền `authSession`, nên cần tích hợp IdP/adapter phía frontend trước khi E2E browser. **Không sửa để dùng token giả/URL/localStorage.**
5. Chuẩn bị 2 store và 2 danh tính: STORE_MANAGER có `reports.read` đúng store, CASHIER không có `reports.read`, một store khác để thử cách ly.

### B. Lệnh Backend tham khảo (PowerShell — chỉ staging khi được duyệt)

```powershell
$env:SASMS_DB_URL = 'jdbc:postgresql://127.0.0.1:15434/sasms_s4_ai_clean'
$env:SASMS_DB_USERNAME = '<runtime_login_da_duoc_DBA_cap>'
$env:SASMS_DB_PASSWORD = '<nhap_mat_khau_cuc_bo_KHONG_commit>'
$env:SASMS_FEATURES_STORE_DASHBOARD_API_ENABLED = 'true'
$env:SASMS_JWT_ISSUER = '<issuer_that>'
$env:SASMS_JWT_AUDIENCE = '<audience_that>'
$env:SASMS_JWT_JWKS_URI = '<jwks_uri_that>'
cd backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=postgresql'
```

Đây là mẫu cấu hình, không chứng minh Backend đã chạy. Không dùng mật khẩu trong lệnh được chia sẻ công khai.

### C. Kiểm thử HTTP (không in token ra terminal)

Ở terminal mới, tạo biến môi trường với token thật và UUID store test. `run_dashboard_http_e2e.ps1` thuộc bộ bản vá TV4, nằm trong `scripts/` sau khi áp dụng.

```powershell
$env:SASMS_E2E_BASE_URL = 'http://127.0.0.1:8080'
$env:SASMS_E2E_STORE_A = '<store_uuid_A>'
$env:SASMS_E2E_STORE_B = '<store_uuid_B>'
$env:SASMS_E2E_MANAGER_JWT = '<access_token_that_cua_MANAGER_store_A>'
$env:SASMS_E2E_CASHIER_JWT = '<access_token_that_cua_CASHIER_store_A>'
.\scripts\run_dashboard_http_e2e.ps1
```

Kỳ vọng trên hệ thống đã bật flag và cấp quyền: `401` nếu không có JWT, `403` với CASHIER, `200` với STORE_MANAGER/store A, `403` hoặc lỗi che giấu đã được contract phê duyệt cho cross-store. Script mặc định yêu cầu 403 cho cross-store vì `StoreDashboardService` ném `AccessDeniedException`; nếu BA duyệt cách che 404 thì sửa kịch bản cùng contract. Script sẽ báo `NOT RUN` khi thiếu biến; không tự nhận PASS.

### D. Kiểm tra trình duyệt thực tế (QA Phát)

- [ ] Đăng nhập OIDC thật (không dán JWT vào URL, query, browser storage).
- [ ] Mở Dashboard với STORE_MANAGER và tải kỳ tháng hiện tại.
- [ ] Chụp toàn màn hình có số liệu, mục AI, timestamp và Network `200` (ẩn Authorization header khi chụp).
- [ ] Đổi bộ lọc ngày mà không nhấn Tải; số liệu phải giữ nguyên, có thông báo bộ lọc đã đổi; sau 60 giây vẫn refresh kỳ cũ.
- [ ] Đổi sang ngày hợp lệ, nhấn Tải; network đúng khoảng UTC `[from,to)` với GMT+7.
- [ ] Đổi phiên sang CASHIER/cửa hàng khác trong khi request đang chạy; không được hiện số liệu cửa hàng cũ.
- [ ] Thu hồi JWT hoặc quyền reports.read, thử refresh; UI không giữ dữ liệu sau 401/403.
- [ ] Kiểm tra Responsive ở desktop/mobile theo frame Figma đã được PO phê duyệt.
- [ ] Ghi D80 bug status VERIFIED_CLOSED chỉ khi QA re-test thành công.
- [ ] PO/Huy ký review UAT D83, PM An quyết định GO/NO-GO theo D78/D90; không ghi Go-Live nếu chưa thật.

## Cần phối hợp để hoàn thành tất cả hạng mục ngoài khả năng sửa code đơn lẻ

| Hạng mục chưa xong | Chủ thể | Điều kiện đóng |
|---|---|---|
| IdP/OIDC frontend & backend, staging JWT | PM/Infra/BA + TV4 | Cấu hình issuer, client PKCE, scopes/claims, session lifecycle được duyệt; browser 200/401/403 |
| Dashboard D52 / thời gian, permissions | BA Huy/Architect An | Ký hợp đồng HTTP và định nghĩa đúng nghiệp vụ |
| DB GRANT, PG concurrency deadlock | DBA Kiên | Migration/GRANT reviewed, load/concurrency PASS |
| QA D80, D78 hệ thống | QA Phát | Hồi quy E2E và ký CLOSED/GO đề xuất |
| SMS/LLM provider thật | Sponsor/Infra/AI | Có sandbox, secret, cost, evidence và xác nhận nhận kết quả |
| UAT/PO và Go-Live | PO / PM | D83/D95 ký, D78 đạt, quyết định go-live chính thức |

### Lưu ý hồ sơ

Không đính kèm JWT, secret, số điện thoại cá nhân, ảnh Network có Authorization vào PR. Dùng evidence có hash và log đã che dữ liệu. Không tuyên bố có AI acceptance-rate thật khi backend chưa đối soát được SHOWN/ACCEPTED vào BI.
