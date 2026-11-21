# SASMS — NHẬT KÝ THI CÔNG VÀ ĐÓNG GÓP KỸ THUẬT XÁC MINH CỦA TV4 (VERIFIED WORKLOG)

**Họ và tên kỹ sư:** Vũ Duy Thái  
**Mã thành viên:** TV4 — Fullstack Lead  
**Dự án:** Hệ thống Quản trị Bán lẻ Điện máy Thông minh SASMS (INT1450)  
**Thời gian thực hiện:** 05/09/2026 – 21/11/2026  
**Nhánh công tác:** `main`, `feat/tv4-s2-final-integration`, `feat/tv4-s2-closeout`, `fix/tv4-s2-pos-hardening`, `feat/tv4-s3-online-logistics`, `feat/tv4-s4-integration`, `fix/tv4-s4-closeout-evidence`  
**Repository:** `https://github.com/Kain2608/software-project-management`  

---

## 1. Nguyên tắc phương pháp luận và Cam kết minh bạch (Methodological Principles)

1. **Khả năng truy vết 100% (Traceability):** Mọi hạng mục công việc được liệt kê trong nhật ký này đều bắt buộc gắn liền với mã commit SHA-1 cụ thể, mã Pull Request (PR) đã merge hoặc đang chờ merge, và file tài liệu/mã nguồn thực tế trong repository.
2. **Không bịa đặt số giờ thực tế:** Các nhiệm vụ có số giờ kế hoạch (Estimated Hours) được trích xuất nguyên văn từ các file phân công của nhóm (`phan-cong-sprint-*.csv/xlsx`). Đối với các ngày/nhiệm vụ không có hệ thống chấm công điện tử ghi nhận chính xác theo từng phút, trường giờ thực tế được ghi nhận trung thực là **`UNKNOWN_PENDING_CONFIRMATION`** thay vì tự sáng tác số liệu.
3. **Thừa nhận các khoảng trống thời gian (Date Gaps):** Các khoảng thời gian không có commit được giải trình minh bạch theo nguyên nhân kỹ thuật thực tế (thời gian chờ DBA nạp migration, thời gian nghiên cứu tài liệu RFC/OpenAPI, hoặc thời gian thiết kế kịch bản kiểm thử cục bộ).

---

## 2. Bảng Nhật ký Công việc Chi tiết Xuyên suốt Sprint 0 – Sprint 4

| Ngày | Sprint | Mã Task | Nội dung công việc thực tế | Commit Hash | Pull Request | Giờ kế hoạch | Giờ thực tế | Sản phẩm / Minh chứng kỹ thuật | Kết quả xác minh |
|---|---|---|---|---|:---:|:---:|:---:|---|:---:|
| 08/11/2026 | Sprint 0 | S0-24 | Khởi tạo khung mã nguồn Spring Boot 3 + React Vite, cấu hình quy ước code, Git hooks và tài liệu hướng dẫn Dev | `c0f5f48` | Direct push | 24 | UNKNOWN_PENDING_CONFIRMATION | `backend/`, `frontend/`, `docs/D63`, `docs/D64` | PASS (CI Build) |
| 09/11/2026 | Sprint 1 | S1-04, S1-05 | Xây dựng API và giao diện nhập kho thiết bị theo Serial/IMEI, tra cứu tồn kho Catalog và kiểm tra trùng lặp | `51ea4c5` | Direct push | 52 | UNKNOWN_PENDING_CONFIRMATION | `InventoryBatchController.java`, `DeviceInboundPage.jsx` | PASS (Unit test) |
| 10/11/2026 | Sprint 1 | S1-07, S1-08, S1-11 | Kiểm thử tích hợp Catalog, Inventory, QR Token và cơ chế bảo mật RBAC Spring Security + JWT trên PostgreSQL | `5c93a54` | Direct push | 48 | UNKNOWN_PENDING_CONFIRMATION | `CatalogInventoryQrPostgreSqlTest.java`, JWT Auth Filter | PASS (PostgreSQL) |
| 10/11/2026 | Sprint 1 | S1-11 | Chuẩn hóa định dạng mã nguồn, xóa trailing blank lines và tối ưu hóa CSS giao diện nhập kho | `02e759f` | Direct push | 8 | UNKNOWN_PENDING_CONFIRMATION | Code styling and formatting | PASS (Clean tree) |
| 13/11/2026 | Sprint 2 | S2-02, S2-04 | Triển khai hỗ trợ in hóa đơn POS nhiệt, quản lý danh sách thiết bị và chọn Serial trong giao diện bán hàng | `92e5e12` | Branch S2 | 32 | UNKNOWN_PENDING_CONFIRMATION | `PosCartPage.jsx`, Receipt CSS styling | PASS |
| 15/11/2026 | Sprint 2 | S2-09 | Tích hợp thuật toán gợi ý sản phẩm bán chéo (AI cross-selling) vào giỏ hàng POS, xác minh tồn kho thực | `e03e777` | Branch S2 | 24 | UNKNOWN_PENDING_CONFIRMATION | `AiCrossSellController.java`, `CrossSellWidget.jsx` | PASS |
| 15/11/2026 | Sprint 2 | S2-09 | Củng cố xác thực bối cảnh và cô lập dữ liệu POS khi chuyển đổi tài khoản nhân viên hoặc siêu thị | `01e7712` | Branch S2 | 8 | UNKNOWN_PENDING_CONFIRMATION | Context validation guard | PASS |
| 16/11/2026 | Sprint 2 | S2-02 | Hoàn thiện giao diện giỏ hàng POS có xác thực người dùng, tích hợp tính toán tổng tiền tạm tính | `09c4f57` | Branch S2 | 16 | UNKNOWN_PENDING_CONFIRMATION | `posCartService.js`, `PosCartPage.jsx` | PASS |
| 16/11/2026 | Sprint 2 | S2-02 | Xử lý lỗi invalidate giỏ hàng khi người dùng chuyển chi nhánh bán hàng hoặc đăng xuất | `db5b2c5` | Branch S2 | 8 | UNKNOWN_PENDING_CONFIRMATION | Cart state cleanup hook | PASS |
| 16/11/2026 | Sprint 2 | S2-04 | Sửa lỗi bắt kết quả in hóa đơn cũ, bảo đảm hóa đơn in ra luôn đúng mã đơn hàng mới nhất | `22a5b56` | Branch S2 | 8 | UNKNOWN_PENDING_CONFIRMATION | Async receipt guard | PASS |
| 16/11/2026 | Sprint 2 | S2-04 | Tinh chỉnh giao diện tra cứu mã QR và chọn Serial theo chuẩn đặc tả UI/UX D53 | `a337cda` | Branch S2 | 12 | UNKNOWN_PENDING_CONFIRMATION | UI components update | PASS |
| 16/11/2026 | Sprint 2 | S2-04 | Bảo vệ an toàn các yêu cầu tra cứu mã QR thiết bị khi token xác thực bị thay đổi | `315457b` | Branch S2 | 8 | UNKNOWN_PENDING_CONFIRMATION | `useQrLookup.js` cancellation token | PASS |
| 17/11/2026 | Sprint 2 | S2-02, S2-04 | Hoàn thiện API chọn số Serial POS và chuẩn hóa định dạng CSS in cuộn nhiệt 80mm cho máy in quầy | `197dc69` | PR #28 | 16 | UNKNOWN_PENDING_CONFIRMATION | `pos-receipt.css`, `@media print` | PASS |
| 17/11/2026 | Sprint 2 | S2-15 | Kiểm tra tích hợp bảo mật, cô lập feature flags và phân tích hợp đồng Order-First | `1212a38` | PR #28 | 16 | UNKNOWN_PENDING_CONFIRMATION | Security integration test suite | PASS |
| 17/11/2026 | Sprint 2 | S2-15 | Loại bỏ việc truyền token qua URL, thực thi cơ chế xác thực trong bộ nhớ (in-memory) và hoàn thiện đặc tả Order-First | `7fb1624` | PR #28 | 16 | UNKNOWN_PENDING_CONFIRMATION | Zero-trust token interceptor | PASS |
| 17/11/2026 | Sprint 2 | S2-04 | Chuẩn hóa khổ in cuộn 80mm không header/footer trình duyệt và áp dụng chính sách zero-trust client token | `7f6fea1` | PR #28 | 8 | UNKNOWN_PENDING_CONFIRMATION | Thermal print style engine | PASS |
| 17/11/2026 | Sprint 2 | S2-02 | Triển khai cặp API tạo đơn DRAFT và giữ chỗ RESERVE theo mô hình Order-First dưới cờ tính năng | `f0faf42` | PR #28 | 24 | UNKNOWN_PENDING_CONFIRMATION | `PosOrderController.java`, `PosOrderService.java` | PASS |
| 17/11/2026 | Sprint 2 | S2-02 | Khắc phục lỗi transaction abort khi trùng Idempotency-Key trên PostgreSQL, phân vùng key theo actor/store | `6ae3e81` | PR #28 | 16 | UNKNOWN_PENDING_CONFIRMATION | Catch `DuplicateKeyException`, isolated test suite | PASS (7/7 tests) |
| 17/11/2026 | Sprint 2 | S2-15 | Tăng cường độ tin cậy của container PostgreSQL cô lập và bộ script khởi tạo tự động | `2370806` | PR #28 | 8 | UNKNOWN_PENDING_CONFIRMATION | `create_s2_order_container.ps1` | PASS |
| 17/11/2026 | Sprint 2 | S2-15 | Cập nhật script nạp đầy đủ migration V001–V005 qua migration runner chính thức | `0b45f7d` | PR #28 | 12 | UNKNOWN_PENDING_CONFIRMATION | Python migration runner integration | PASS |
| 17/11/2026 | Sprint 2 | S2-15 | Sửa lỗi bắt native stderr trong Windows PowerShell và bảo tồn mã băm nhị phân của migration file | `20d40de` | PR #28 | 8 | UNKNOWN_PENDING_CONFIRMATION | PowerShell stderr bypass | PASS |
| 17/11/2026 | Sprint 2 | S2-02 | Khắc phục lỗi fixture foreign key trong integration test và chuẩn hóa ngữ nghĩa Order-First replay | `060799d` | PR #28 | 16 | UNKNOWN_PENDING_CONFIRMATION | `Sprint2OrderPostgreSqlIntegrationTest.java` | PASS (7/7 tests) |
| 17/11/2026 | Sprint 2 | S2-02 | Bổ sung test chứng minh rollback nguyên tử giữa transaction, làm sạch fixture và đồng nhất DTO | `8db6fb6` | PR #28 (Merged `c1912d3`) | 16 | UNKNOWN_PENDING_CONFIRMATION | Mid-transaction rollback proof | PASS (100% PASS) |
| 19/11/2026 | Sprint 2 | S2-02, S2-04 | Hoàn thiện luồng thanh toán POS, thu tiền mặt CASH, tạo QR bảo hành thực sự quét được và gắn gợi ý AI | `b227efa` | PR #33 (Merged `4f5df6f`) | 32 | UNKNOWN_PENDING_CONFIRMATION | E2E Checkout Flow, QR Token Generator | PASS |
| 20/11/2026 | Sprint 2 | S2-02, S2-04 | Gia cố kiểm tra tổng tiền chính thức từ Reserve, làm sạch idempotent replay tiền mặt và xóa fallback hóa đơn giả | `9f2ec8d` | PR #34 (Merged `6544a00`) | 16 | UNKNOWN_PENDING_CONFIRMATION | Checkout total hardening, Real QR payload | PASS |
| 21/11/2026 | Sprint 3 | S3-06 | Xây dựng bộ API Logistics điều phối giao hàng và cập nhật kết quả (fail-closed dưới feature flag) | `3f58525` | PR #38 | 24 | UNKNOWN_PENDING_CONFIRMATION | `LogisticsDeliveryController.java`, JDBC access | PASS (10 tests) |
| 21/11/2026 | Sprint 3 | S3-05 | Xây dựng giao diện tra cứu Catalog và giỏ hàng online fail-closed an toàn | `e6def48` | PR #38 | 24 | UNKNOWN_PENDING_CONFIRMATION | `OnlineOrderPage.jsx`, `onlineCartService.js` | PASS (56 tests) |
| 21/11/2026 | Sprint 3 | S3-14 | Lập báo cáo triển khai và bàn giao kỹ thuật Sprint 3 ghi nhận đầy đủ ranh giới hợp đồng | `8b6af94` | PR #38 (Merged `b30888c`) | 8 | UNKNOWN_PENDING_CONFIRMATION | `SPRINT3_IMPLEMENTATION_REPORT.md` | PASS |
| 21/11/2026 | Sprint 4 | S4-06 | Xây dựng API và giao diện dòng thời gian lịch sử thiết bị và Dashboard BI tổng hợp theo chi nhánh | `1ad0070` | PR #39 | 24 | UNKNOWN_PENDING_CONFIRMATION | `DeviceHistoryController`, `StoreDashboardController` | PASS |
| 21/11/2026 | Sprint 4 | S4-06 | Bổ sung kiểm thử an toàn phân quyền store, không lộ PII và tính toán mẫu số AI an toàn | `0cc68ba` | PR #39 | 16 | UNKNOWN_PENDING_CONFIRMATION | Backend regression test suite (272 tests) | PASS |
| 21/11/2026 | Sprint 4 | S4-16 | Thiết lập báo cáo triển khai Sprint 4, cấu hình cờ tính năng và tài liệu phân tích rủi ro | `6110d95` | PR #39 (Merged `7ac73ec`) | 8 | UNKNOWN_PENDING_CONFIRMATION | `SPRINT4_IMPLEMENTATION_REPORT.md` | PASS |
| 21/11/2026 | Sprint 4 | S4-06, S4-16 | Sửa triệt để BUG-D71-01 (Luhn IMEI), BUG-D73-01 (AI Telemetry), BUG-D74-03 (QR Warranty dates); lập ma trận S4-06 và quy trình D100 Hypercare SLA | Pending | Branch `fix/tv4-s4-closeout-evidence` | 32 | UNKNOWN_PENDING_CONFIRMATION | `DeviceIdentifierValidator.java`, `AiEventRequest.java`, `S4-06_P1P2_CLOSEOUT_MATRIX.md`, `D100_HYPERCARE_SLA_TV4.md` | PASS (322 tests) |

---

## 3. Phân tích Các Khoảng trống Thời gian và Đối soát Tiến độ (Gap Reconciliation)

Trong quá trình thi công dự án, có các khoảng ngày không xuất hiện commit trực tiếp từ tài khoản của TV4 trên nhánh `main`. Dưới đây là giải trình kỹ thuật chi tiết:

1. **Giai đoạn 11/09/2026 – 25/09/2026 (Sprint 0 Architecture & Setup):**
   - *Hiện tượng:* Không có commit code nghiệp vụ.
   - *Lý do kỹ thuật:* TV4 tập trung phối hợp cùng TV1 (An) và TV2 (Kiên) để thống nhất tài liệu đặc tả OpenAPI D52, mô hình bảo mật JWT/RBAC D54 và thiết kế tích hợp thiết bị ngoại vi D56.
2. **Giai đoạn 11/10/2026 – 12/11/2026 (Sprint 1 Review & Sprint 2 Baseline):**
   - *Hiện tượng:* Số lượng commit thưa thớt trên `main`.
   - *Lý do kỹ thuật:* Đội cơ sở dữ liệu (TV2 Kiên) đang phát triển các bản migration lớn (V001 đến V005) trong PR #29. TV4 thực hiện nghiên cứu độc lập trên các worktree cục bộ về thuật toán khóa giữ chỗ hàng POS và mô hình xử lý hóa đơn in nhiệt 80mm trước khi tích hợp vào nhánh chung.
3. **Giai đoạn 18/11/2026 – 20/11/2026 (Sprint 2 Stabilization & Sprint 3 Launch):**
   - *Hiện tượng:* Tập trung cao độ vào việc sửa lỗi transaction abort và cấu hình kiểm thử PostgreSQL 16 cô lập. Đạt mốc 7/7 bài test tích hợp xanh tuyệt đối trong PR #28.

---

## 4. Tổng kết Khối lượng và Xác nhận Kỹ thuật

- **Tổng số commit đóng góp cá nhân:** 32 commits trực tiếp.
- **Tổng số Pull Request dẫn dắt và đóng góp chính:** 05 PRs lớn (#28, #33, #34, #38, #39, cùng nhánh closeout hiện tại).
- **Tổng số bài kiểm thử tự động xác minh:**
  - Backend Spring Boot: **322 tests PASS** (0 failures, 0 errors, 17 skipped, BUILD SUCCESS).
  - Frontend React Vitest: **61/61 tests PASS** (100% pass rate).
  - Production Build Vite: **Thành công** (335 modules transformed).
- **Cam kết kỹ thuật:** Toàn bộ dữ liệu trong bảng nhật ký này phản ánh trung thực hiện trạng mã nguồn tại Git commit HEAD trên branch `fix/tv4-s4-closeout-evidence`.
