# D131 — Hồ sơ kỹ thuật DB cho Quality Gate M6

| Thuộc tính | Nội dung |
|---|---|
| **Cột mốc Quality Gate** | **M6 — Full System Go-Live & Handover** |
| **Thời gian diễn ra** | 20/11/2026 (15:30 – 16:30 Thứ Sáu, Tuần 11) |
| **Phụ trách phần hồ sơ DB** | Trần Trung Kiên — Architect / DBA |
| **Nhánh nghiệm thu** | `DB` (đồng bộ hoàn toàn trên `main`) |
| **Trạng thái** | **SẴN SÀNG THÔNG QUA CỔNG M6 & BÀN GIAO TOÀN DIỆN** |

---

## 1. Căn cứ và Mục tiêu thẩm định Cổng M6

Cổng kiểm soát chất lượng **M6 (Quality Gate M6)** là cột mốc tối hậu của dự án SASMS, đánh giá toàn diện tính sẵn sàng của hệ thống để:
1. **Chính thức Go-Live trên môi trường Cloud Production.**
2. **Không còn bất kỳ lỗi tồn đọng mức độ nghiêm trọng nào (Zero P1/P2 Bugs).**
3. **Hoàn tất rà soát an toàn bảo mật, đạt 100% tiêu chí an toàn (0 High/Critical CVE).**
4. **Bảo đảm các cam kết hiệu năng theo Điều lệ dự án (Charter):** quét QR < 1 giây, POS < 2 giây, AI < 3 giây, làm mới Dashboard $\le 1$ phút.
5. **Hoàn tất toàn bộ tài liệu vận hành (Runbook), quy trình sao lưu và bàn giao hệ thống.**

---

## 2. Bằng chứng kỹ thuật thực nghiệm tầng CSDL (Trần Trung Kiên chịu trách nhiệm)

Toàn bộ các tiêu chí kỹ thuật do Architect / DBA phụ trách đều có bằng chứng số liệu và nhật ký thực thi thực tế đi kèm trong repository:

### 2.1 Hiệu năng CSDL vượt xa chỉ tiêu (S4-04 & S4-07)
- Căn cứ tệp kết quả đo đạc chính thức [database/evidence/sprint4/summary.json](../../database/evidence/sprint4/summary.json) chạy dưới tải **50 client đồng thời** trên dữ liệu 100.000 thiết bị:
  - **Quét Serial / QR:** Đo được **0.926 mili-giây** (Chuẩn cam kết: < 1.0 giây).
  - **Tồn kho phục vụ AI gợi ý:** Đo được **0.249 mili-giây** (Chuẩn cam kết: < 3.0 giây).
  - **BI Dashboard Summary:** Đo được **1.860 mili-giây**, tối ưu giảm **184 lần** từ mức 342.39ms (Chuẩn cam kết: < 60 giây).
  - **Tồn kho real-time siêu thị:** Đo được **3.278 mili-giây** (Chuẩn cam kết: < 2.0 giây).
- **Kết luận:** Tầng CSDL đáp ứng 100% các cam kết SLA với độ trễ cực thấp.

### 2.2 An toàn bảo mật tuyệt đối (S4-13)
- Căn cứ tệp bằng chứng [evidence/security/summary.json](evidence/security/summary.json):
  - Backend dependencies (91 packages): **0 CVE** (Zero Critical, Zero High).
  - Frontend packages: **0 lỗ hổng** bảo mật qua kiểm toán npm audit.
  - Secret scan: **300 tệp tin** được quét sạch, không rò rỉ bất kỳ API key, token hay private key nào.
  - Database ACL: Đã thu hồi toàn bộ quyền kết nối công cộng và quyền superuser của ứng dụng; Backend chạy bằng nhóm đặc quyền giới hạn `sasms_app`.

### 2.3 Diễn tập sao lưu và phục hồi thảm họa đạt 100% (S4-14)
- Căn cứ tệp bằng chứng [evidence/restore/environment-summary.json](evidence/restore/environment-summary.json):
  - **34/34 hạng mục kiểm tra đạt kết quả xuất sắc (passed)**.
  - Khôi phục nguyên vẹn 100% dữ liệu 41 bảng, cấu trúc schema, ràng buộc toàn vẹn và quyền hạn `sasms_app`.
  - Thời gian khôi phục đo được: **0.48 giây** (Mục tiêu RTO < 4 giờ; RPO < 15 phút).

### 2.4 Hạ tầng triển khai tự động hóa CI/CD Cloud (S4-08)
- Hai workflow GitHub Actions đã sẵn sàng:
  - [.github/workflows/sprint4.yml](../../.github/workflows/sprint4.yml): Tự động kiểm tra hồi quy CSDL, đo đạc hiệu năng và diễn tập backup/restore.
  - [.github/workflows/sprint4-cloud.yml](../../.github/workflows/sprint4-cloud.yml): Cơ chế phát hành có kiểm soát lên AWS App Runner và Vercel.

---

## 3. Bảng đối chiếu Tiêu chuẩn Hoàn thành (DoD) Cổng M6

| STT | Tiêu chí kiểm soát tại Cổng M6 | Kết quả kỹ thuật phía CSDL | Người thẩm định | Đánh giá |
|---|---|---|---|---|
| 1 | Hệ thống deploy tự động lên Cloud Production | Pipeline CI/CD và script `release.py` sẵn sàng | Trần Trung Kiên | **ĐẠT** |
| 2 | Không còn lỗi P1/P2 nào trong Bug Log | Đã tối ưu hóa câu lệnh SQL, loại bỏ deadlock | Kiên, Thái, Phát | **ĐẠT** |
| 3 | Quét QR < 1s, AI < 3s, POS < 2s | Đo lường p95 mức CSDL đều < 4 mili-giây | Trần Trung Kiên, Phát | **ĐẠT** |
| 4 | Báo cáo rà soát bảo mật không có lỗi nghiêm trọng | Báo cáo D81: 0 CVE, 0 Secret Leak | Trần Trung Kiên | **ĐẠT** |
| 5 | Hoàn thiện tài liệu Runbook, Backup/Restore | Tài liệu D101 và D91 kèm bằng chứng diễn tập | Trần Trung Kiên | **ĐẠT** |
| 6 | UAT được đại diện người dùng và Sponsor ký | Các module vận hành trơn tru trên CSDL | Nguyễn Quang Huy | **ĐẠT** |

---

## 4. Tuyên bố và Chữ ký của Architect / DBA

Trần Trung Kiên xác nhận toàn bộ các hạng mục công việc Cơ sở dữ liệu và Kiến trúc hệ thống trong **Sprint 4** và **xuyên suốt toàn bộ dự án SASMS (Sprint 0 $\rightarrow$ Sprint 4)** đã hoàn thành toàn diện, đạt độ chính xác và tính toàn vẹn 100%, vượt qua tất cả các bài kiểm tra tự động và diễn tập thảm họa.

Kiến trúc CSDL SASMS hoàn toàn sẵn sàng và đủ điều kiện để Hội đồng nghiệm thu ký quyết định **THÔNG QUA CỔNG CHẤT LƯỢNG M6**, chính thức đưa hệ thống vào vận hành thương mại (**GO-LIVE**) và tiến hành đóng dự án theo đúng kế hoạch.

