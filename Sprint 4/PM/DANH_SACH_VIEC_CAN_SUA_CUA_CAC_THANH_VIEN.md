# DANH SÁCH ĐẦU VIỆC CẦN SỬA VÀ BỔ SUNG CỦA CÁC THÀNH VIÊN (TV2 – TV6)

- **Dự án:** Hệ thống Quản lý Siêu thị Điện máy Thông minh **SASMS (Vendora Store)** — **Nhóm 5 (INT1450)**
- **Người tổng hợp:** Trịnh Quốc An (`TV1` — Project Manager / Scrum Master)
- **Ngày rà soát:** 28/11/2026 (đối chiếu sau commit `d65c530` — PR #54, #55, #56, #57 và lượt QA retest ngày 24/11/2026)
- **Trạng thái dự án hiện tại:** **`NO-GO Production`** theo [D94_Bien_Ban_Go_No_Go_SASMS.docx](D94_Bien_Ban_Go_No_Go_SASMS.docx); đã hoàn tất đồng bộ toàn bộ hồ sơ PM theo Baseline **1.000.000.000 VNĐ / 380 Man-Days** (`Du_Toan_Ngay_Cong_SASMS.docx` & `D12`).

> **Mục tiêu tài liệu:** Chỉ rõ từng lỗi kỹ thuật, tài liệu còn thiếu và điểm chưa khớp của từng thành viên (`TV2`, `TV3`, `TV4`, `TV5`, `TV6`) cần xử lý dứt điểm để mở khóa cổng **E2E (`D78`)**, **UAT (`D83`)** và **Go-Live (`D94`)**.

---

## 0. Quy ước đồng bộ bắt buộc cho tất cả thành viên (SSOT)

Khi chỉnh sửa hoặc tạo mới bất kỳ tài liệu nào, toàn nhóm phải dùng thống nhất các thông số sau (đã khóa trên toàn bộ hồ sơ PM):

| Thông số | Giá trị chuẩn thống nhất |
| :--- | :--- |
| **Tên nhóm** | **Nhóm 5** *(không dùng Nhóm 4 / Nhóm 10)* |
| **Mã thành viên chuẩn** | `TV1` Trịnh Quốc An (PM) · `TV2` Trần Trung Kiên (Arch/DBA) · `TV3` Phạm Đại Phát (QA/Doc) · `TV4` Vũ Duy Thái (Fullstack) · `TV5` Nguyễn Quang Huy (BA/PO) · `TV6` Nguyễn Tiến Vũ (Data/AI) |
| **Ngân sách Baseline (BAC)** | **1.000.000.000 VNĐ** (`380 Man-Days = 3.040 giờ`) theo `Du_Toan_Ngay_Cong_SASMS.docx` & `D12` *(chi phí thực nghiệm học phần Lean ~\$100–\$150 USD chỉ ghi chú tham chiếu)* |
| **Phạm vi nghiệp vụ chuẩn** | **6 Module**, **16 User Stories (+ EN-01 / 25 Use Cases)** *(không ghi 24 User Stories)* |
| **Môi trường kỹ thuật chuẩn** | **PostgreSQL 16** (41 bảng `V001–V008`), **Spring Boot 4.1.1 / JDK 25** (`backend/pom.xml`), **React/Vite** |
| **Nguyên tắc báo cáo** | **Trung thực dựa trên bằng chứng** — không ghi "Zero Bug P1/P2" hoặc "Đã Go-Live" khi `D78`/`D80`/`D83` chưa đạt |

---

## 1. Trần Trung Kiên (`TV2` — Solution Architect & Lead DBA)

| Ưu tiên | Hạng mục cần sửa / bổ sung | File / Vị trí liên quan | Tiêu chí hoàn thành (DoD) |
| :---: | :--- | :--- | :--- |
| **P0** | **Dọn mật khẩu mặc định & giới hạn quyền trong script provisioning PR #52** | `scripts/provision_staging_e2e.sql` và các script tạo môi trường Staging liên quan | Loại bỏ mật khẩu hardcode/mặc định và quyền quá rộng; luân chuyển credential; đảm bảo Backend chỉ chạy bằng runtime user thuộc `sasms_app` để không mâu thuẫn với báo cáo bảo mật `D81`. |
| **P0** | **Cấp Staging PostgreSQL 16 an toàn & phối hợp sửa 2 lỗi High POS** | Môi trường Staging + `BUG-D72-R1-01`, `BUG-D72-R1-02` | Cấp thông số kết nối Staging sạch qua kênh an toàn cho TV4/TV3/TV6; phối hợp TV4 kiểm tra quyền RBAC Catalog cho vai trò cửa hàng (`BUG-D72-R1-02`) và cơ chế giải phóng giữ chỗ quá hạn (`BUG-D72-R1-01`). |
| **P1** | **Đồng bộ lại bảng đối chiếu DoD trong `D131` DB** | `Sprint 4/DB/D131-ho-so-review-DB-M6.md` (Mục 3, dòng 61 & 65) | Không ghi sẵn trạng thái **"ĐẠT"** cho tiêu chí *"Không còn lỗi P1/P2 trong Bug Log"* và *"UAT được người dùng & Sponsor ký"* vì `D80` (24/11) còn lỗi P1 và `D83` chưa chạy; đổi sang *"Đạt tầng CSDL; chờ QA/PO xác nhận toàn hệ thống"* cho khớp `D94` và `D131` của PM. |

---

## 2. Vũ Duy Thái (`TV4` — Senior Fullstack Developer Lead)

| Ưu tiên | Hạng mục cần sửa / bổ sung | File / Vị trí liên quan | Tiêu chí hoàn thành (DoD) |
| :---: | :--- | :--- | :--- |
| **P0** | **Sửa 2 lỗi High mới phát hiện ở POS (`D80` Sprint 2 ngày 24/11)** | `BUG-D72-R1-01` (giữ chỗ quá hạn không tự giải phóng, thiết bị kẹt `RESERVED`) & `BUG-D72-R1-02` (vai trò theo cửa hàng bị `403` khi đọc Catalog) | Cài đặt cơ chế giải phóng giữ chỗ quá hạn `hold_expires_at` (`TC-POS-007` & `TC-10-07` PASS); mở quyền đọc Catalog đúng phạm vi cửa hàng (`TC-POS-001` PASS). |
| **P0** | **Sửa lại 2 lỗi bị QA retest FAIL $\rightarrow$ `REOPENED` ngày 24/11** | `BUG-D73-01` (sự kiện hiển thị/chấp nhận gợi ý AI không lưu — phối hợp TV6) & `BUG-D74-03` (bảo hành QR) | Tìm nguyên nhân retest fail trên bản chạy thật, sửa triệt để và chuyển QA Phát retest đạt `VERIFIED`. |
| **P0** | **Sửa lỗi High mới Sprint 3 (`BUG-D74-R1-01`)** | `Sprint 3/Test/D80_So_Loi_Bug_Log_Sprint_3.docx` (Mục 7–12) | Khắc phục theo bước tái hiện trong D80 Sprint 3 và bàn giao QA retest PASS. |
| **P0** | **Tích hợp luồng đăng nhập OIDC/JWT thật trên Frontend** | `frontend/src/main.jsx` (hiện render `<App />` chưa truyền `authSession`) | Nối adapter OIDC/PKCE (in-memory, không dùng localStorage/URL query) để QA và PO có thể chạy E2E & UAT trực tiếp trên trình duyệt. |
| **P1** | **Sửa các lỗi Medium & khoảng trống UI còn mở ngày 24/11** | `BUG-D72-R1-03` (hóa đơn nhiều máy chỉ có 1 QR & hiện UUID), `BUG-D71-R2-01..03` (thông báo lỗi chung chung `field=null`), `BUG-D74-R1-02..03`, `BUG-D74-02` (UI web giao/lắp) | Hóa đơn hiển thị đủ QR bảo hành từng thiết bị và tên/SKU thay UUID; payload lỗi validation trả rõ `field`; bổ sung màn hình giao/lắp tối thiểu theo phân công. |
| **P2** | **Cập nhật phiên bản JDK trong tài liệu môi trường `D65`** | `Sprint 0/Fullstack/TV4/docs/sprint0/D65_DEVELOPMENT_ENVIRONMENT.md` | Cập nhật yêu cầu từ JDK 17 lên **JDK 25 / Spring Boot 4.1.1** khớp với `backend/pom.xml`. |

---

## 3. Phạm Đại Phát (`TV3` — QA/QC & Documentation Lead)

| Ưu tiên | Hạng mục cần sửa / bổ sung | File / Vị trí liên quan | Tiêu chí hoàn thành (DoD) |
| :---: | :--- | :--- | :--- |
| **P0** | **Chạy lại E2E toàn hệ thống 6 module & cập nhật `D78`, `D80` Sprint 4** | `Sprint 4/Test/D78_Bao_Cao_Kiem_Thu_E2E_Toan_He_Thong_SASMS.docx` & tạo mới `Sprint 4/Test/D80_So_Loi_Bug_Log_Sprint_4.docx` | Sau khi TV4/TV2/TV6 đóng hết lỗi High/P1 và có Staging sạch + OIDC FE: chạy đủ 6 module đạt pass rate $\ge 95\%$, 0 P1/P2 OPEN, cập nhật D78 từ NO-GO sang PASS kèm log/screenshot. |
| **P1** | **Hoàn thiện báo cáo hiệu năng AI `D79` từ Dự thảo v0.1** | `Sprint 2/Tester/D79_Bao_Cao_Kiem_Thu_Hieu_Nang_Va_Tai_AI.docx` | Bổ sung JSON/log chi tiết benchmark 100 req / 50 worker trên PostgreSQL 16 thật, đối chiếu `p95 < 3s`, fallback rate và kết quả xác minh `ISS-003`; chuyển sang bản chính thức có chữ ký. |
| **P1** | **Bổ sung 03 tài liệu QA còn thiếu theo bảng phân công `01`, `05`, `06`** | - **`Tiền Sprint/QA/D18`** (*Kế hoạch quản lý chất lượng* — thư mục hiện **trống**!)<br>- **`Tiền Sprint/QA/D02`** (*Biên bản họp khởi động Tuần 1*)<br>- **`Sprint 4/Test/D93`** (*Kế hoạch & tài liệu đào tạo người dùng*) | Tạo đủ 3 file đúng mã, khớp tiêu chuẩn Zero P1/P2, mốc 11 tuần và 9 vai trò canonical. |
| **P1** | **Hoàn thiện kiểm kê thực tế trên Biên bản bàn giao `D95`** | `Sprint 4/Test/D95_Bien_Ban_Ban_Giao_He_Thong_SASMS.docx` | Điền thực tế URL repo, commit SHA, phiên bản DB/build, danh mục tài liệu đã giao và tick đúng trạng thái từng mục *(hiện tất cả đang để trống `☐ Chưa bàn giao`)*. |
| **P2** | **Chuẩn hóa bản nộp giảng viên `D143`** | `Nhom_5_Bao_cao_Chi_Tiet.docx` *(PM đã đồng bộ Nhóm 5, TV1–TV6, 1 tỷ VNĐ, 16 US & trạng thái 24/11)* | Rà soát lần cuối phần kiểm thử và đóng gói bản nộp đúng mã `D143` nếu giảng viên yêu cầu. |

---

## 4. Nguyễn Quang Huy (`TV5` — Lead Business Analyst / Product Owner)

| Ưu tiên | Hạng mục cần sửa / bổ sung | File / Vị trí liên quan | Tiêu chí hoàn thành (DoD) |
| :---: | :--- | :--- | :--- |
| **P0** | **Trả lời dứt điểm 03 câu hỏi nghiệp vụ mở từ QA (`Q-S2-01..03`)** | `Sprint 2/Tester/D80_Bug_Log_Sprint2_D72_D73_20261124.docx` (Mục 5) | Chốt bằng văn bản: (1) `Q-S2-01`: ánh xạ `RETURN_INSPECTION/QUARANTINED` vs `RETURNED/SCRAPPED`; (2) `Q-S2-02`: tra kho ngoài phạm vi trả `200 []` có chấp nhận thay `403` không; (3) `Q-S2-03`: tiêu chí gợi ý AI tương thích, số lượng tối đa & thứ tự hiển thị. |
| **P0** | **Chốt dứt khoát phạm vi Online Checkout (`FR-10` / `BUG-D74-01`)** | `D34`, `D52`, `D83` | Một là hoàn thiện hợp đồng OpenAPI Customer Identity & Payment để mở `FR-10`; hai là ký xác nhận chính thức giữ `FR-10` ở trạng thái Fail-Closed / giới hạn phạm vi Giai đoạn 1 để không chặn nghiệm thu học phần. |
| **P0** | **Chạy thực tế & ký Biên bản nghiệm thu UAT (`D83`)** | `Sprint 4/BA/D83_Bien_ban_nghiem_thu_UAT_SASMS_MAU_CHO_XAC_NHAN.docx` | Thực thi đủ **9/9 kịch bản (`UAT-01` đến `UAT-09`)** trên Staging sạch, điền URL/commit/người chạy/minh chứng thực tế và ký xác nhận (đổi tên bỏ hậu tố `MAU_CHO_XAC_NHAN`). |
| **P1** | **Chốt 4 biên bản Sprint Review & Retrospective (`D122–D125`) và bổ sung `D60`** | `Sprint 1..4/BA/D122..D125_*_DU_THAO.docx` & tạo mới **`D60`** (*Sprint Backlog & Biên bản Sprint Planning 1–4*) | Cập nhật quyết định nghiệm thu từng Story của PO (16 US / 25 UC) khớp thực tế, bổ sung chữ ký, bỏ trạng thái `DU_THAO`; bổ sung `D60` còn thiếu. |
| **P2** | **Dọn file trùng trong `Tiền Sprint/BA/`** | `Tiền Sprint/BA/` (đang có song song bản tên có dấu và không dấu cho `D30`–`D33`) | Giữ lại duy nhất 1 bộ chuẩn mới nhất cho mỗi mã `D30`, `D31`, `D32`, `D33` để tránh nhầm lẫn khi đối chiếu. |

---

## 5. Nguyễn Tiến Vũ (`TV6` — Data & AI Engineer Lead)

| Ưu tiên | Hạng mục cần sửa / bổ sung | File / Vị trí liên quan | Tiêu chí hoàn thành (DoD) |
| :---: | :--- | :--- | :--- |
| **P0** | **Phối hợp TV4 sửa dứt điểm `BUG-D73-01` (`REOPENED` 24/11)** | `AiCrossSellController` / `ai_recommendations` / telemetry `SHOWN` & `ACCEPTED` | Đảm bảo sự kiện hiển thị và chấp nhận gợi ý AI từ POS được ghi nhận vào DB đúng chuẩn để QA Phát retest `TC-AI-018` PASS và Dashboard tính được tỷ lệ chấp nhận thật thay vì `N/A`. |
| **P1** | **Chạy lại đối soát BI Dashboard `DS4` trên PostgreSQL 16 chuẩn dự án** | `Sprint 4/AI/DS4-phan-mem-tang-truong-ai-sprint4.md` & `evidence/ds4-ai-dashboard-reconciliation.json` | Chạy lại script `scripts/validate_ai_sprint34.py` trên **PostgreSQL 16** (hiện mới chạy trên PG 18.7) và cập nhật log bằng chứng. |
| **P1** | **Bổ sung ảnh chụp BI Dashboard thực tế & hỗ trợ benchmark `D79`** | `Sprint 4/AI/DS4*` & `Sprint 2/Tester/D79*` | Chụp ảnh màn hình BI Dashboard đang chạy có dữ liệu thực từ Staging (ẩn Authorization/PII) bổ sung vào `DS4`; phối hợp TV3 chốt số liệu benchmark PG 16 trong `D79` và ghi rõ chế độ mock hay LLM thật. |

---

## 6. Thứ tự thực hiện để nhanh nhất chuyển từ `NO-GO` sang `GO`

1. **Bước 1 (`TV2` Kiên + `TV5` Huy):** Kiên dọn mật khẩu PR #52 và cấp Staging PG 16 sạch; Huy chốt 3 câu hỏi `Q-S2-01..03` và ranh giới `FR-10`.
2. **Bước 2 (`TV4` Thái + `TV6` Vũ):** Sửa các lỗi High/P1 (`BUG-D72-R1-01/02`, `BUG-D73-01`, `BUG-D74-03`, `BUG-D74-R1-01`) và nối OIDC vào `frontend/src/main.jsx`; Vũ chạy đối soát PG 16 & chụp ảnh Dashboard.
3. **Bước 3 (`TV3` Phát + `TV5` Huy):** Phát chạy lại `D78` E2E, chốt `D79`, cập nhật `D80`/`D95` và bổ sung `D18`/`D02`/`D93`; Huy chạy & ký `D83` UAT cùng `D122–D125`.
4. **Bước 4 (`TV1` An — PM):** Mở lại phiên họp Go/No-Go cập nhật **`D94` $\rightarrow$ `GO`**, cập nhật **`D131`/`D142`/`D144`** và kích hoạt giải ngân phần bảo lưu `255.000.000 VNĐ` cùng kế hoạch Hypercare 3 ngày `D100`.
