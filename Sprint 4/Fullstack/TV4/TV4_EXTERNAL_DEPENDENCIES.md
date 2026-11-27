# SASMS — BẢNG DANH MỤC PHỤ THUỘC BÊN NGOÀI CỦA PHÂN HỆ FULLSTACK TV4 (EXTERNAL DEPENDENCIES)

**Dự án:** Hệ thống Quản trị Bán lẻ Điện máy Thông minh SASMS (INT1450)  
**Phân hệ lập báo cáo:** Vũ Duy Thái (TV4 — Fullstack Lead)  
**Ngày cập nhật:** 21/11/2026 (Asia/Ho_Chi_Minh)  
**Mục đích:** Xác định rõ các sản phẩm chuyển giao (deliverables), quyết định kỹ thuật và điều kiện tiên quyết thuộc trách nhiệm của các thành viên khác trong nhóm (TV1, TV2, TV3, TV5, TV6) mà TV4 cần để hoàn thiện hoặc đưa các tính năng vào nghiệm thu chính thức.

---

## 1. Nguyên tắc Phân định Ranh giới và Trách nhiệm (Separation of Concerns)

1. **Không vượt thẩm quyền:** TV4 không tự tiện thay thế các tài liệu thuộc sở hữu của QA (D78, D80, D81), BA (D34, D52, D83) hay DBA (D90, D91, D101).
2. **Minh bạch tình trạng chặn (Fail-Closed by Default):** Mọi tính năng thiếu thỏa thuận hợp đồng hoặc thiếu môi trường kiểm chứng độc lập đều được đưa về trạng thái cờ tính năng tắt (`feature flag = false`) để bảo đảm an toàn cho nhánh `main`.
3. **Thúc đẩy phối hợp liên phòng ban:** Bảng phụ thuộc này đóng vai trò là danh mục hành động (action items) gửi tới từng thành viên nhằm chuẩn bị tốt nhất cho buổi bảo vệ đồ án cuối kỳ.

---

## 2. Ma trận Phụ thuộc Bên ngoài Chi tiết (External Dependencies Matrix)

### 2.1 Phụ thuộc vào Phạm Đại Phát (TV3 — QA Lead / Tester)

| Tài liệu / Hạng mục phụ thuộc | Mã tài liệu | Trạng thái hiện tại | Đường dẫn trong Repo | Cần cho nhiệm vụ TV4 | Mức độ chặn (Blocking Status) | Hành động yêu cầu (Action Required) |
|---|---|---|---|---|:---:|---|
| **Báo cáo kiểm thử E2E hệ thống** | D78 | **CHƯA ĐẠT / NO-GO** (Chữ ký để trống) | `Sprint 4/Test/D78_Bao_cao_kiem_thu_he_thong_E2E_SASMS.docx` | Nghiệm thu tổng thể Sprint 4, Kích hoạt Hypercare D100 | **BLOCKING (Chặn Go-Live)** | Chạy lại kiểm thử tích hợp E2E trên môi trường Staging với các bản vá mới nhất của TV4, đánh giá lại tiêu chí và ký biên bản. |
| **Sổ theo dõi lỗi Sprint 4** | D80 (S4) | **CHƯA CÓ** (Thư mục chỉ có D78, D92, D95) | `Sprint 4/Test/` | Đóng lỗi S4-06 (BUG-D71-01, BUG-D73-01, BUG-D74-03) | **BLOCKING (Chặn nghiệm thu S4-06)** | Ban hành chính thức D80 Sprint 4, thực hiện re-test các bản vá của TV4 và chuyển trạng thái từ `BLOCKED_PENDING_QA_EVIDENCE` sang `VERIFIED_CLOSED`. |
| **Báo cáo kiểm thử tải và bảo mật** | D79, D81 | Bản nháp kỹ thuật | `Sprint 4/Test/` | Xác minh NFR: latency POS < 1s, AI < 3s; không còn lỗ hổng | **NON-BLOCKING (Khuyến nghị)** | Chạy công cụ kiểm thử tải (JMeter/K6) ghi nhận số đo chính thức trên staging. |

---

### 2.2 Phụ thuộc vào Trịnh Quốc An (TV1 — Project Manager / System Architect)

| Tài liệu / Quyết định phụ thuộc | Mã tài liệu | Trạng thái hiện tại | Đường dẫn trong Repo | Cần cho nhiệm vụ TV4 | Mức độ chặn (Blocking Status) | Hành động yêu cầu (Action Required) |
|---|---|---|---|---|:---:|---|
| **Kiến trúc Định danh Khách hàng (Customer Identity)** | D50 / `ARCH-DEC-S03-01` | **ĐÃ CHỐT QUYẾT ĐỊNH KIẾN TRÚC (23/11/2026)** | `Sprint 4/PM/QUYET_DINH_KIEN_TRUC_FEATURE_FLAG_D100_VA_NGHIEM_THU_TV4.md` | Chốt ranh giới Đặt hàng Online (S3-05 / BUG-D74-01) | **RESOLVED (GIỮ FAIL-CLOSED TRONG RC1)** | PM/Architect An đã chốt tách `customers.id` khỏi `users.auth_subject`, cấm cấp `sales.checkout` cho khách hàng và chấp thuận giữ `online-order.enabled=false` trong Release Candidate hiện tại. |
| **Phê duyệt Quy trình Hypercare & SLA D100** | D100 | **APPROVED FOR HYPERCARE READINESS (23/11/2026)** | `Sprint 4/Fullstack/TV4/D100_HYPERCARE_SLA_TV4.md` | Hoàn tất sản phẩm bàn giao S4-16 | **RESOLVED (ĐÃ DUYỆT KẾ HOẠCH)** | PM An và DBA Kiên đã ký duyệt kế hoạch SLA, lịch trực, RACI và điều kiện Rollback (chờ kích hoạt sau khi đạt D78/D83). |
| **Chính sách Mở Cờ tính năng (Feature Flags Policy)** | `FF-STAGING-POLICY-S4` | **ĐÃ CHỐT CHÍNH SÁCH STAGING (23/11/2026)** | `Sprint 4/PM/QUYET_DINH_KIEN_TRUC_FEATURE_FLAG_D100_VA_NGHIEM_THU_TV4.md` | Chạy kiểm thử E2E trên Staging cho POS, Logistics, Device History, Store Dashboard | **RESOLVED FOR STAGING / BLOCKED FOR PROD** | Cho phép bật riêng 6 cờ API qua biến môi trường trên Staging cổng `15435` (`tv4_staging_app_user`); giữ `false` mặc định trên `main` và giữ tắt Online Checkout khách vãng lai. |

---

### 2.3 Phụ thuộc vào Trần Trung Kiên (TV2 — DBA / Technical Lead)

| Hạng mục kỹ thuật / Quyết định DB | Mã tài liệu | Trạng thái hiện tại | Đường dẫn trong Repo | Cần cho nhiệm vụ TV4 | Mức độ chặn (Blocking Status) | Hành động yêu cầu (Action Required) |
|---|---|---|---|---|:---:|---|
| **Kiểm soát khóa tuần tự & Đối soát `P0002` / `40P01` / `55P03`** | BUG-D74-05 / `TV4-S4-REVIEW-APPROVAL-KIEN` | **RESOLVED AT DB LEVEL (23/11/2026)** | `Sprint 4/DB/TV4-S4-REVIEW-APPROVAL-KIEN.md` | Bảo đảm an toàn khi đặt hàng đồng thời cao (Concurrency S2-02, S4-06) | **RESOLVED_BY_DBA / PENDING_QA_TRIAGE** | Kiên DBA đã xác nhận `sasms.reserve_order` có `ORDER BY d.id FOR UPDATE OF d` chống deadlock `40P01`; làm rõ `P0002` trong BUG-D74-05 là `no_data_found` trong test harness lập lịch, chờ QA Phát triage khi rerun. |
| **Phân quyền vai trò chạy thực tế (Runtime Role Grants)** | Runtime Roles | **RESOLVED & PROVISIONED (23/11/2026)** | `scripts/provision_staging_e2e.sql`, `scripts/create_staging_e2e_container.ps1` | Đọc dữ liệu lịch sử thiết bị và doanh thu báo cáo chi nhánh trên Staging | **RESOLVED** | Kiên DBA đã cung cấp script tạo container Staging cổng `15435` và tài khoản `tv4_staging_app_user` thuộc nhóm `sasms_app` trên đủ 41 bảng (V001–V008). |
| **Kế hoạch Rollback và Vận hành Runbook** | D91, D101 | **RESOLVED & APPROVED (23/11/2026)** | `Sprint 4/DB/D91-ke-hoach-cutover-va-rollback.md`, `D101-...md` | Đồng bộ quy trình ứng cứu sự cố trong D100 Hypercare | **RESOLVED / ALIGNED** | Kiên DBA và PM An đã xác nhận đồng bộ 100% giữa D100 Mục 8 & 11 với D91 và D101. |

---

### 2.4 Phụ thuộc vào Nguyễn Quang Huy (TV5 — Product Owner / BA Lead)

| Tài liệu nghiệp vụ / Hợp đồng API | Mã tài liệu | Trạng thái hiện tại | Đường dẫn trong Repo | Cần cho nhiệm vụ TV4 | Mức độ chặn (Blocking Status) | Hành động yêu cầu (Action Required) |
|---|---|---|---|---|:---:|---|
| **Đặc tả API OpenAPI chính thức cho Online & Logistics** | D52 | Mới có phần Draft Inventory, POS, AI; thiếu Online/Logistics | `Sprint 0/DB/` | Chuẩn hóa giao tiếp Frontend - Backend Online & Logistics | **BLOCKING (Chặn chuẩn hóa API)** | Thống nhất cấu trúc request/response, mã lỗi nghiệp vụ và idempotency header cho các API đặt hàng trực tuyến và giao hàng. |
| **Biên bản Nghiệm thu Người dùng UAT** | D83, D125 | **DỰ THẢO (MAU_CHO_XAC_NHAN)** | `Sprint 4/BA/D83_Bien_ban_nghiem_thu_UAT_SASMS.docx` | Bàn giao và đóng nghiệm thu Sprint 4 | **BLOCKING (Chặn nghiệm thu tổng thể)** | Tổ chức buổi kiểm thử chấp nhận người dùng thực tế với đại diện các bên và ký biên bản chính thức. |
| **Quy định Nghiệp vụ Đổi trả và Doanh thu thuần (Net Revenue)** | D34, D42 | Chưa định nghĩa thời điểm ghi nhận refund thành công | `Sprint 3/BA/`, `Sprint 4/BA/` | Báo cáo Dashboard BI tổng hợp (Store Summary BI) | **NON-BLOCKING (Hiện hiển thị Gross Revenue)** | Định nghĩa rõ điều kiện trừ doanh thu khi đơn hàng bị hoàn tiền một phần hoặc toàn phần. |

---

### 2.5 Phụ thuộc Staging/CI và dịch vụ SMS (TV2 Kiên phụ trách kỹ thuật, TV4 Thái hỗ trợ, TV1 An/TV5 Huy chốt phạm vi)

| Hạ tầng / Dịch vụ tích hợp | Thành phần | Trạng thái hiện tại | Đường dẫn / Tài nguyên | Cần cho nhiệm vụ TV4 | Mức độ chặn (Blocking Status) | Hành động yêu cầu (Action Required) |
|---|---|---|---|---|:---:|---|
| **Hợp đồng và thông tin SMS Gateway** | SMS Provider API | Chưa có bằng chứng kết nối provider thật (đang dùng Adapter Mock) | `Sprint 3/DB/DS3-SMS-adapter-va-ban-giao.md` | BUG-D74-04 (SMS thật) | **BLOCKING (Nếu phạm vi bắt buộc SMS thật)** | **Kiên (S3-08)** phụ trách adapter/worker SMS, **Thái** hỗ trợ tích hợp; **An/Huy** quyết định có dùng provider/sandbox và nguồn kinh phí, không đưa secret vào Git. Nếu chưa duyệt, giữ MOCK và ghi giới hạn nghiệm thu. |
| **Môi trường CI/CD và Docker Staging tập trung** | Staging Server | PostgreSQL 16 kiểm thử cục bộ và GitHub CI đã có bằng chứng; **staging chung/JWT E2E chưa xác minh** | Pipeline GitHub Actions; `Sprint 2/DB/inputs/phan-cong-sprint-2.csv` (S2-12) | Chạy E2E Frontend–Backend–JWT trên staging | **BLOCKING (Chặn E2E/UAT)** | **Kiên là đầu mối chính S2-12**, **Thái hỗ trợ**, **An** điều phối môi trường; cung cấp URL staging, runtime login riêng, log CI/deploy và bằng chứng chạy E2E đã che secret. |

---

### 2.6 Phụ thuộc Nguyễn Tiến Vũ (TV6 — Data / AI Lead)

| Hạng mục | Trạng thái hiện tại | Cần bổ sung |
|---|---|---|
| **AI/BI Dashboard Sprint 4 (S4-03)** | Đã có mã nguồn/đối soát SQL; thiếu Browser E2E được QA xác nhận | Vũ phối hợp Thái chụp Dashboard có dữ liệu thật, đối soát số liệu PostgreSQL 16 và ghi rõ mức mô phỏng LLM. |
| **Provider LLM thực** | Adapter hiện dùng mô phỏng theo hồ sơ DS4 | Vũ báo cáo benchmark theo đúng chế độ mock; nếu phạm vi yêu cầu LLM thật, An/Huy phê duyệt provider và Vũ thực hiện đo thử an toàn. |

---

## 3. Tổng kết Kế hoạch Hành động Phối hợp (Coordinated Action Plan)

1. **Ưu tiên 1 (Trước ngày bảo vệ đồ án):**
   - Đề nghị QA Phát thực hiện re-test các bản vá BUG-D71-01, BUG-D73-01, BUG-D74-03 của TV4 và cập nhật trạng thái trong tài liệu D80/D78.
   - Đề nghị PM Trịnh Quốc An ký phê duyệt dự thảo D100 Hypercare SLA.
2. **Ưu tiên 2 (Giai đoạn thương mại hóa tiếp theo):**
   - Thống nhất mô hình định danh khách hàng (Customer IdP) cùng Kiến trúc sư An và BA Huy để gỡ cờ tính năng cho Online Order và Logistics.
   - An/Huy xác nhận phạm vi và provider SMS; Kiên phụ trách adapter SMS (S3-08), Thái hỗ trợ kiểm thử/tích hợp. Không gán nhiệm vụ Infra cho Vũ AI.
