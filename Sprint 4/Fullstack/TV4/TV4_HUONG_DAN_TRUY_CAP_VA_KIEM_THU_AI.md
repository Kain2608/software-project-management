# SASMS — HƯỚNG DẪN TRUY CẬP HỆ THỐNG VÀ KIỂM THỬ PHÂN HỆ AI

**Người tổng hợp:** Vũ Duy Thái (TV4 — Fullstack)  
**Người sử dụng tài liệu:** Nguyễn Tiến Vũ (TV6 — Data/AI), Phạm Đại Phát (TV3 — QA)  
**Dự án:** Quản lý dự án xây dựng Phần mềm Quản lý Siêu thị Điện máy Thông minh (SASMS)  
**Ngày đối chiếu nguồn:** 23/11/2026  
**Mã nguồn tham chiếu:** `main` — `8d3efbcb`  
**Trạng thái tài liệu:** Hướng dẫn kỹ thuật dựa trên mã nguồn; **chưa phải biên bản xác nhận E2E/UAT thành công**.

## 1. Có thể truy cập và kiểm thử được gì?

| Mục | Tình trạng đối chiếu source | Cách xác minh |
| --- | --- | --- |
| Trang giao diện React/Vite | Có mã nguồn | Khởi chạy Frontend, xem trang `http://localhost:5173` |
| Màn hình `Bán hàng POS` và `Gợi ý bán kèm` | Có component `PosPage.jsx`, `CrossSellWidget.jsx` | Xem layout và trạng thái chờ cấu hình |
| API health | Có route công khai `/api/health` | Gọi khi Backend khởi động |
| API trạng thái AI | Có route công khai `/api/v1/ai/status` | Gọi khi Backend khởi động |
| API `POST /api/v1/ai/cross-sell` | Có mã nguồn, yêu cầu xác thực và quyền `ai.use` theo cửa hàng | Kiểm thử bằng tài khoản JWT thật và DB staging |
| API `POST /api/v1/ai/summaries/specifications` | Có mã nguồn, yêu cầu JWT và `ai.use` | Kiểm thử sau khi có dữ liệu sản phẩm |
| Dashboard BI | Có giao diện và API; API mặc định tắt bởi feature flag | Chỉ kiểm thử với staging được BA/PM/Architect phê duyệt bật |
| Đăng nhập trên Web | `frontend/src/main.jsx` hiện render `<App />` không truyền `authSession` | **Chưa có luồng đăng nhập OIDC tích hợp ở entrypoint** |
| Adapter OpenAI/Gemini | Có mock adapter sinh dữ liệu xác định | **Không phải kết quả gọi LLM thương mại thật** |

**Lưu ý:** `localhost` chỉ dùng trên máy đang chạy ứng dụng. Vũ muốn vào từ máy khác phải tự chạy bản local hoặc nhận URL staging có cấu hình, được phép truy cập từ An/Kiên; hiện chưa có URL staging chung đã được xác minh trong repo. Không dùng đường link Figma như URL hệ thống chạy thật.

## 2. Chuẩn bị trên Windows

Yêu cầu: có Git, JDK tương thích với `backend/pom.xml` (**source hiện đặt `java.version=25`**), Node.js/npm và, nếu chạy API có dữ liệu, PostgreSQL 16 staging với user ứng dụng được DBA cấp. Không cần cài Maven toàn cục vì repo có `mvnw.cmd`.

```powershell
cd D:\software-project-management-tv4-qa
git status
git pull origin main
java -version
node --version
npm.cmd --version
```

Nếu Vũ dùng máy riêng, clone repository vào một thư mục của Vũ rồi chạy các lệnh tương ứng tại thư mục đó. Không chạy `git pull` khi có thay đổi chưa commit; kiểm tra `git status` trước.

## 3. Truy cập Frontend để xem giao diện

Tại terminal PowerShell thứ nhất:

```powershell
cd D:\software-project-management-tv4-qa\frontend
npm.cmd ci
npm.cmd run dev
```

Mở URL do Vite in ra terminal, mặc định **http://localhost:5173**. Trang mở mặc định ở **Bán hàng POS**; thanh bên có mục **Dashboard**. Trong màn POS có khu vực **Gợi ý bán kèm**.

**Kết quả có thể xác minh ngay:** Giao diện tải được và các thông báo trạng thái hiển thị đúng. Nếu hiện `CHƯA KẾT NỐI IdP / KHO (FAIL-CLOSED)` hoặc `Chờ phiên đăng nhập, cửa hàng, kho và giỏ POS hợp lệ`, đây là trạng thái theo source hiện tại, **không phải bằng chứng AI đã chạy được qua trình duyệt**. Không dán JWT vào URL, Local Storage hoặc chỉnh code để bypass kiểm tra quyền.

Để kiểm thử code giao diện AI mà chưa cần Backend/IdP:

```powershell
node --test src/features/pos/aiPosService.test.js
npm.cmd run build
```

Ghi lại số test PASS/FAIL thực tế và ngày chạy; đây là unit test có mô phỏng transport, không phải browser E2E.

## 4. Backend và API trạng thái AI

Backend chỉ nên kết nối **database staging riêng** bằng runtime LOGIN không phải superuser; Kiên cần cung cấp username, database và cấu hình kết nối qua kênh an toàn. Không dùng `sasms_owner` làm tài khoản Backend.

**Chú ý bảo mật:** PR #52 có script provisioning chứa mật khẩu mặc định trong repository và cấp quyền rộng. Không chạy nguyên trạng script đó để tạo môi trường chia sẻ hay công bố mật khẩu. Cần Kiên rà soát, loại bỏ/luân chuyển credential và giới hạn quyền trước khi dùng.

Sau khi Kiên cung cấp staging hợp lệ, tại terminal thứ hai:

```powershell
cd D:\software-project-management-tv4-qa
$env:SASMS_DB_URL = 'jdbc:postgresql://127.0.0.1:<CONG_STAGING>/<TEN_DATABASE>'
$env:SASMS_DB_USERNAME = '<RUNTIME_USER_DUOC_DBA_CAP>'
$secret = Read-Host 'DB password (khong chia se)' -AsSecureString
$env:SASMS_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password

# Chi khi An/Kien cung cap thong so IdP/OIDC hop le:
# $env:SASMS_JWT_ISSUER = '<ISSUER>'
# $env:SASMS_JWT_AUDIENCE = '<AUDIENCE>'
# $env:SASMS_JWT_JWKS_URI = '<JWKS_URI>'

cd backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=postgresql'
```

Các giá trị trong dấu `<...>` là **chỗ trống**, không phải cấu hình có thể dùng ngay. Cần có runtime đã được Kiên xác nhận; thiếu DB/IdP, Backend có thể không khởi động hoặc API nghiệp vụ bị từ chối truy cập.

Nếu Backend đã khởi động và lắng nghe cổng 8080, tại terminal khác:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/health'
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/ai/status'
```

- Kết quả `UP` nghĩa là endpoint phản hồi; **không chứng minh** gợi ý AI được tính đúng hay LLM thật đã kết nối.
- `401` khi gọi API nghiệp vụ mà không có JWT là hành vi bảo vệ mong đợi.
- Backend không kết nối được: kiểm tra log, cổng 8080, PostgreSQL và profile trước khi kết luận lỗi AI.

## 5. Kiểm thử API gợi ý AI khi đã có JWT hợp lệ

**Điều kiện tiên quyết:** API đã chạy; IdP/JWKS thật; JWT còn hạn cấp cho nhân viên có `ai.use` tại `store_id`; `warehouse_id` thuộc cửa hàng; `sku_ids` có dữ liệu trong DB staging. Để trống hoặc nhập sai các giá trị này không cho kết quả kiểm thử AI hợp lệ.

```powershell
# Thuc hien tren may duoc phep test, khong chup hay paste token vao chat/Git.
$base = 'http://127.0.0.1:8080'
$storeId = Read-Host 'UUID cua store duoc cap quyen'
$warehouseId = Read-Host 'UUID cua warehouse thuoc store'
$sku = Read-Host 'SKU san pham chinh co trong DB'
$secure = Read-Host 'JWT that do IdP cap' -AsSecureString
$jwt = [System.Net.NetworkCredential]::new('', $secure).Password

$body = @{
    store_id = $storeId
    warehouse_id = $warehouseId
    sku_ids = @($sku)
    limit = 4
} | ConvertTo-Json -Depth 4

try {
    Invoke-RestMethod -Method Post `
        -Uri "$base/api/v1/ai/cross-sell" `
        -Headers @{ Authorization = "Bearer $jwt" } `
        -ContentType 'application/json' -Body $body
} finally {
    Remove-Variable jwt,secure -ErrorAction SilentlyContinue
}
```

**Kiểm tra đầu ra:** HTTP status, `source` (`AI`, `CACHE` hoặc `RULE_FALLBACK`), danh sách `suggestions`, khả năng lọc theo tồn kho, không cho giá/Serial do AI tự nghĩ ra quyết định đơn hàng. Danh sách rỗng có thể là kết quả hợp lệ khi không có sản phẩm bán kèm đủ điều kiện.

**Không sử dụng** endpoint `/api/v1/ai/simulate` làm đường demo thay thế. Source yêu cầu quyền `ai.use` **toàn cục** (không chỉ quyền theo cửa hàng). Role ADMIN không tự động có quyền này theo cấu hình DB hiện hành.

## 6. Kiểm thử Dashboard AI/BI

Sau khi hợp đồng Dashboard được Huy/An duyệt, Kiên cấp DB staging và hệ thống có phiên `STORE_MANAGER` đúng cửa hàng với `reports.read`, Thái mới bật API Dashboard **riêng cho staging** theo quyết định của nhóm. Tính năng này đang `false` mặc định trong `application.properties`.

- Tại Frontend: chọn **Dashboard** → chọn khoảng ngày → **Tải báo cáo**.
- Đối chiếu số liệu cửa hàng với PostgreSQL; kiểm tra phần hiệu quả AI. Nếu chưa có dữ liệu sự kiện `SHOWN/ACCEPTED` được đối soát, tỷ lệ chấp nhận có thể là `N/A`.
- Kiểm tra đổi cửa hàng/đổi quyền, lỗi 401/403, bộ lọc ngày và tự cập nhật 60 giây.
- **Hiện chưa có bằng chứng browser E2E có JWT thật**, nên phần này vẫn là kế hoạch kiểm thử, không ghi PASS trước.

## 7. Checklist bàn giao kết quả cho Vũ/Phát

- [ ] Ghi commit SHA, ngày chạy, cấu hình local/staging (không chứa mật khẩu/JWT).
- [ ] Chạy được Vite và chụp màn hình POS, widget AI.
- [ ] Ghi kết quả API `/api/health` và `/api/v1/ai/status` nếu Backend đã chạy.
- [ ] Có JWT thật, store/warehouse/SKU hợp lệ để kiểm thử `/cross-sell`, hoặc ghi `BLOCKED` và nguyên nhân cụ thể.
- [ ] Test `AI`, `CACHE`, `RULE_FALLBACK`, danh sách rỗng và trường hợp thiếu quyền nếu môi trường có khả năng mô phỏng hợp lệ.
- [ ] Test Dashboard khi API/IdP đã được cấp và phê duyệt; đối soát số liệu AI/BI.
- [ ] Đính kèm screenshot/log đã ẩn Authorization, PII, mật khẩu; ghi PASS/FAIL/NOT RUN theo thực tế.
- [ ] QA Phát xác nhận E2E độc lập; BA/PO Huy xác nhận UAT khi đủ điều kiện.

## 8. Ai cung cấp những phần còn thiếu?

| Người | Đầu ra cần cung cấp |
| --- | --- |
| **Thái (TV4)** | Hướng dẫn này, chạy frontend/backend, kết nối API, xử lý lỗi giao diện/integration |
| **Kiên (TV2)** | DB staging đã cấu hình an toàn, runtime login, store/warehouse seed phục vụ test |
| **An (TV1) và Huy (TV5)** | Thông số IdP/OIDC, vai trò/quyền, quyết định bật API/flag trên staging và hợp đồng nghiệp vụ |
| **Vũ (TV6)** | Kịch bản kiểm thử cross-sell/AI BI, đối soát kết quả và ghi rõ mock hay provider thật |
| **Phát (TV3)** | Chạy E2E độc lập, kết quả PASS/FAIL, bug log và minh chứng QA |

## 9. Các tệp nguồn đã đối chiếu

- `Sprint 0/Fullstack/TV4/docs/sprint0/D65_DEVELOPMENT_ENVIRONMENT.md` — hướng dẫn môi trường cũ; **lưu ý JDK trong file này không còn khớp với pom hiện tại**.
- `frontend/src/main.jsx`, `frontend/src/app/App.jsx`, `frontend/src/features/pos/CrossSellWidget.jsx`, `frontend/src/features/pos/aiPosService.js`, `frontend/vite.config.js`.
- `backend/src/main/java/com/smartelectronicsupermarket/ai/controller/AiCrossSellController.java`, `backend/src/main/java/com/smartelectronicsupermarket/security/config/SecurityConfiguration.java`, `backend/src/main/resources/application.properties`.
- `Sprint 2/AI/D52-hop-dong-tich-hop-ai-pos-sprint2.md`, `Sprint 4/AI/README.md`, `Sprint 4/Fullstack/TV4/TV4_DASHBOARD_E2E_CLOSEOUT.md`.

**Kết luận:** Tài liệu này giúp Vũ tự mở giao diện và xác định phần test AI có thể chạy ngay. Để Vũ thực sự nhìn thấy AI có dữ liệu từ Backend trên Web, nhóm vẫn phải tích hợp đăng nhập IdP ở frontend, cấu hình staging an toàn, cấp quyền và kiểm thử có bằng chứng. Không báo là đã hoàn thành E2E cho đến khi các điều kiện này được đáp ứng.
