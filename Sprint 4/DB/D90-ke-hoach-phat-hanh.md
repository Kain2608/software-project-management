# D90 — Kế hoạch phát hành chính thức (Release Plan)

| Thuộc tính | Nội dung |
|---|---|
| **Đầu việc** | S4-14 — Hoàn thiện Release Plan, Cutover và Rollback; diễn tập khôi phục (24 giờ) |
| **Phụ trách kỹ thuật** | Trần Trung Kiên — Architect / DBA |
| **Chủ trì quản lý** | Trịnh Quốc An (PM / Scrum Master), Vũ Duy Thái (Fullstack Lead) |
| **Kỳ kế hoạch / Hạn** | Sprint 4; Hạn hoàn thiện kế hoạch: **17/11/2026**; Ngày Go-Live: **20/11/2026** |
| **Cột mốc mục tiêu** | **M6 — Full System Go-Live & Handover (20/11/2026)** |
| **Phiên bản phát hành** | **SASMS Release v1.0.0 (Production Candidate)** |

---

## 1. Mục tiêu và Căn cứ phát hành

Văn bản D90 quy định toàn bộ lộ trình, điều kiện tiên quyết và các mốc triển khai đưa hệ thống Quản lý chuỗi siêu thị điện máy thông minh SASMS từ môi trường Staging lên vận hành chính thức trên môi trường Production (Go-Live).

### Căn cứ phát hành:
1. Bản phân công Sprint 4 tại [inputs/phan-cong-sprint-4.csv](inputs/phan-cong-sprint-4.csv).
2. Kết quả kiểm thử E2E đạt tỷ lệ pass $\ge 95\%$ và không còn lỗi P1/P2 (D78).
3. Báo cáo rà soát bảo mật D81 đạt 100% tiêu chí an toàn (0 High/Critical CVE).
4. Biên bản nghiệm thu người dùng UAT có chữ ký đại diện thu ngân, thủ kho, kỹ thuật viên (D83).
5. Biên bản cuộc họp Go/No-Go ngày 18/11/2026 thống nhất 100% quyết định phát hành (D94).

---

## 2. Phạm vi phiên bản phát hành v1.0.0

Phiên bản v1.0.0 bao gồm đầy đủ 6 module nghiệp vụ tương ứng 16 User Stories chính thức:
- **Module 1 (Quản trị danh mục & Kho):** Quản lý sản phẩm, định danh duy nhất Serial/IMEI theo quy tắc D35, nhập kho theo lô chống trùng.
- **Module 2 (Bán lẻ tại quầy POS):** Tìm kiếm và khóa chọn Serial chống bán trùng (Two-phase locking), in hóa đơn nhiệt và tem QR bảo hành tức thì.
- **Module 3 (AI Recommendation):** Gợi ý sản phẩm bán kèm (Cross-sell) với thời gian phản hồi < 3 giây và luồng dự phòng DB.
- **Module 4 (Bán hàng trực tuyến & Điều phối giao lắp):** Giữ hàng online 30 phút, lập lịch giao hàng và điều phối kỹ thuật viên với đệm 30 phút.
- **Module 5 (Bảo hành điện tử & CRM Đổi trả):** Kích hoạt bảo hành điện tử đa kênh, gửi tin nhắn SMS thương hiệu tự động, tra cứu lịch sử vòng đời Serial và xử lý đổi trả hàng.
- **Module 6 (BI Analytics Dashboard):** Thống kê doanh thu, tồn kho và hiệu quả bán lẻ với tốc độ làm mới $\le 1$ phút.

---

## 3. Lịch trình phát hành và Các mốc chuyển đổi (Release Milestones)

| Thời gian (Giờ UTC+7) | Mốc công việc | Thành phần thực hiện | Tiêu chí hoàn thành mốc |
|---|---|---|---|
| **17/11/2026 17:00** | Chốt Release Candidate & Đóng mã nguồn | Toàn bộ nhóm | Merge toàn bộ PR vào `main`, gắn tag `v1.0.0-rc1` |
| **18/11/2026 09:00** | Họp Hội đồng Go/No-Go | Sponsor, PM, Lead các bộ phận | Ký biên bản Go/No-Go (D94), phê duyệt mở cửa sổ phát hành |
| **18/11/2026 22:00** | Mở Cửa sổ bảo trì Cutover (Maintenance Window) | Kiên, Thái, An | Treo thông báo bảo trì, chặn lưu lượng ghi bên ngoài |
| **18/11/2026 22:15** | Sao lưu chốt dữ liệu trước Cutover | Trần Trung Kiên | Tạo snapshot Logical & Physical Backup, xác minh checksum |
| **18/11/2026 22:45** | Áp dụng Migration CSDL Production (V001 $\rightarrow$ V008) | Trần Trung Kiên | Migration hoàn tất đến V008, nạp danh mục và quyền runtime |
| **18/11/2026 23:15** | Triển khai Backend API & Frontend Web | Vũ Duy Thái, Kiên | Deploy container lên AWS App Runner và static lên Vercel |
| **18/11/2026 23:45** | Chạy kịch bản Smoke Test Production | Phát, Thái, Kiên | Kiểm tra đăng nhập, quét Serial, tạo đơn mẫu, test outbox SMS |
| **19/11/2026 00:30** | Mở luồng lưu lượng chính thức (Go-Live) | Toàn nhóm | Tháo biển bảo trì, mở kết nối Internet công cộng |
| **19/11 – 20/11/2026** | Giai đoạn Hypercare (Giám sát tăng cường) | Cả nhóm | Trực kỹ thuật 24/7 theo quy trình SLA (D100) |
| **20/11/2026 15:30** | Quality Gate M6 & Nghiệm thu đóng dự án | Sponsor, PM, Cả nhóm | Ký biên bản M6 (D131) và Biên bản bàn giao hệ thống (D95) |

---

## 4. Điều kiện Tiên quyết và Kế hoạch ứng phó rủi ro

1. **Điều kiện tiên quyết để bấm nút Release:**
   - Pipeline CI/CD [.github/workflows/sprint4.yml](../../.github/workflows/sprint4.yml) trên `main` đạt trạng thái xanh (Success).
   - Bản sao lưu CSDL trước release đã được kiểm tra khôi phục thử thành công trên môi trường cô lập.
   - Tài khoản kết nối CSDL của Backend đã cấu hình chính xác nhóm `sasms_app`.
2. **Kế hoạch ứng phó rủi ro (Contingency Plan):**
   - Nếu quá trình áp dụng migration CSDL thất bại: Kích hoạt quy trình quay lui Rollback CSDL về bản sao lưu trước đó trong vòng **15 phút**.
   - Nếu Backend không thể kết nối hoặc lỗi dịch vụ: Kích hoạt Rollback phiên bản container trên AWS App Runner về phiên bản trước đó.
   - Chi tiết phương án ứng phó được quy định cụ thể tại văn bản [D91 — Kế hoạch Cutover và Rollback](D91-ke-hoach-cutover-va-rollback.md).

