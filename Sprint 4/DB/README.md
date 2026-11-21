# Sprint 4 — DB / Architect, Trần Trung Kiên

Nhánh thực hiện: **`DB`**.  
Kỳ kế hoạch: **08/11/2026 – 20/11/2026 (Tuần 10–11)**.  
Chủ đề Sprint 4: **CRM, Dashboard, E2E, Tối ưu và Go-Live**.  
Phạm vi nghiệp vụ: **US-12 (Should), US-13, US-14, US-15, US-16**. Cam kết: 24 điểm Must, 5 điểm Should.  
Cột mốc quyết định: **Quality Gate M6 — Full System Go-Live & Handover (20/11/2026)**.

---

## 1. Đầu việc chính do Trần Trung Kiên (Architect / DBA) phụ trách

Căn cứ bảng phân công chính thức tại [inputs/phan-cong-sprint-4.csv](inputs/phan-cong-sprint-4.csv), Kiên phụ trách chính **6 đầu việc**, tổng cộng **116 giờ**:

| Mã việc | Công việc | Hạn nộp | Giờ | Tài liệu đầu ra | Minh chứng kỹ thuật đã hoàn thành |
|---|---|---|---|---|---|
| **S4-04** | TSK-04-03 Tối ưu truy vấn tổng hợp báo cáo kho Serial/IMEI | 12/11/2026 | 20h | [DS4 — Thiết kế và bàn giao DB](DS4-thiet-ke-va-ban-giao-DB.md) | V008 partial indexes; đo đạc benchmark thực tế trên 100.000 thiết bị / 50.000 đơn hàng: truy vấn `bi_summary` giảm từ 342.39ms xuống **1.86ms** (giảm 184 lần); `inventory_stock` đạt **3.28ms** |
| **S4-07** | TSK-04-06 Tối ưu độ trễ quét QR < 1 giây và AI < 3 giây | 16/11/2026 | 24h | [DS4 — Thiết kế và bàn giao DB](DS4-thiet-ke-va-ban-giao-DB.md) | Index `ix_devices_s4_sellable`: truy vấn quét QR đạt **0.93ms** (< 1s); kiểm tra tồn khả dụng phục vụ AI đạt **0.25ms** (< 3s) |
| **S4-13** | Hoàn tất rà soát bảo mật và xử lý lỗ hổng | 16/11/2026 | 12h | [D81 — Báo cáo rà soát bảo mật](D81-bao-cao-ra-soat-bao-mat.md) | Quét phụ thuộc Backend (OSV-Scanner) 91 packages: **0 CVE**; Frontend (NPM Audit): **0 lỗ hổng**; Secret scan 300 files: **0 secret** |
| **S4-08** | TSK-04-07 Thiết lập CI/CD và deploy cloud AWS / Vercel | 18/11/2026 | 20h | [D91 — Kế hoạch Cutover và Rollback](D91-ke-hoach-cutover-va-rollback.md) | Pipeline [.github/workflows/sprint4.yml](../../.github/workflows/sprint4.yml) & [sprint4-cloud.yml](../../.github/workflows/sprint4-cloud.yml); bộ điều khiển triển khai [infra/sprint4/release.py](../../infra/sprint4/release.py) |
| **S4-14** | Hoàn thiện Release Plan, Cutover và Rollback; diễn tập khôi phục | 17/11/2026 | 24h | [D90 — Kế hoạch phát hành](D90-ke-hoach-phat-hanh.md)<br>[D91 — Kế hoạch Cutover và Rollback](D91-ke-hoach-cutover-va-rollback.md) | Diễn tập sao lưu logic và khôi phục cô lập đạt **34/34 checks** thành công; bảo toàn 100% dữ liệu, schema, ACL và quyền `sasms_app` |
| **S4-17** | Tài liệu vận hành (Runbook), quy trình sao lưu và bảo trì | 19/11/2026 | 16h | [D101 — Tài liệu vận hành, sao lưu và bảo trì](D101-tai-lieu-van-hanh-sao-luu-va-bao-tri.md) | Runbook quy định tần suất backup PITR (RPO 15 phút, RTO 4 giờ), quy trình bảo trì vacuum/analyze, giám sát kết nối và xử lý sự cố Production |

---

## 2. Đầu việc Kiên tham gia hỗ trợ

Kiên hỗ trợ 4 đầu việc chuyên môn theo bảng phân công:

| Mã việc | Công việc | Người làm chính | Đóng góp chuyên môn của Kiên |
|---|---|---|---|
| **S4-03** | TSK-04-02 BI Analytics Dashboard doanh thu, kho, hiệu quả AI | Nguyễn Tiến Vũ (Data / AI Lead) | Cung cấp index `ix_sales_orders_s4_paid_store_date` và `ix_movements_s4_warehouse_date` giúp làm mới dashboard < 1 phút |
| **S4-06** | TSK-04-05 Sửa toàn bộ lỗi P1/P2 phát hiện trong kiểm thử | Vũ Duy Thái (Fullstack Lead) | Tối ưu hóa câu lệnh SQL và mapping JDBC trong Backend; loại bỏ triệt để xung đột khóa và timeout |
| **S4-11** | Báo cáo kiểm thử hiệu năng (QR < 1s, POS < 2s, AI < 3s) và tải | Phạm Đại Phát (QA Lead) | Cung cấp số liệu đo đạc benchmark thực nghiệm với 50 client đồng thời, đạt p95 < 5ms mức CSDL |
| **S4-18** | Lập biên bản bàn giao hệ thống (mã nguồn, tài khoản, tài liệu) | Phạm Đại Phát (QA Lead) | Cung cấp danh mục bàn giao CSDL, tài khoản ứng dụng `sasms_app`, migration scripts và runbook vận hành |

---

## 3. Bộ hồ sơ bàn giao Sprint 4

1. **Tài liệu kỹ thuật & vận hành:**
   - [DS4 — Thiết kế và bàn giao DB](DS4-thiet-ke-va-ban-giao-DB.md): Báo cáo tối ưu hóa CSDL V008, kết quả đo lường benchmark và hợp đồng API.
   - [D81 — Báo cáo rà soát bảo mật](D81-bao-cao-ra-soat-bao-mat.md): Kết quả quét an ninh phụ thuộc, mã nguồn và cấu hình phân quyền.
   - [D90 — Kế hoạch phát hành (Release Plan)](D90-ke-hoach-phat-hanh.md): Kế hoạch và các điều kiện sẵn sàng Go-Live Production.
   - [D91 — Kế hoạch chuyển đổi Go-Live (Cutover) và quay lui (Rollback)](D91-ke-hoach-cutover-va-rollback.md): Kịch bản chuyển đổi dữ liệu và biên bản diễn tập phục hồi CSDL.
   - [D101 — Tài liệu vận hành (Runbook), sao lưu và bảo trì](D101-tai-lieu-van-hanh-sao-luu-va-bao-tri.md): Hướng dẫn vận hành hệ thống, quy trình backup/restore và giám sát Production.
   - [D131 — Hồ sơ DB cho Quality Gate M6](D131-ho-so-review-DB-M6.md): Hồ sơ kỹ thuật phục vụ Cổng kiểm soát chất lượng cuối cùng M6 (20/11/2026).
2. **Minh chứng kỹ thuật thực nghiệm (Evidence):**
   - [evidence/security/summary.json](evidence/security/summary.json): Kết quả quét OSV-Scanner, NPM Audit và Secret Scan.
   - [evidence/restore/environment-summary.json](evidence/restore/environment-summary.json): Biên bản chạy diễn tập sao lưu và phục hồi CSDL (34 checks pass).
   - [../../database/evidence/sprint4/summary.json](../../database/evidence/sprint4/summary.json): Kết quả đo đạc benchmark trước và sau tối ưu V008 với 50 clients đồng thời.
3. **Mã nguồn và hạ tầng Cloud:**
   - [../../database/migrations/V008__sprint4_query_optimization.sql](../../database/migrations/V008__sprint4_query_optimization.sql): Migration tối ưu hóa chỉ mục CSDL.
   - [../../infra/sprint4/release.py](../../infra/sprint4/release.py): Bộ công cụ tự động hóa triển khai Production AWS / Vercel.
   - [../../.github/workflows/sprint4.yml](../../.github/workflows/sprint4.yml) & [sprint4-cloud.yml](../../.github/workflows/sprint4-cloud.yml): Pipeline CI/CD phát hành tự động.

