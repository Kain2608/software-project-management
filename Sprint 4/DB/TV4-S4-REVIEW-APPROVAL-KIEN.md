# BIÊN BẢN REVIEW VÀ PHÊ DUYỆT ĐÓNG GÓP KỸ THUẬT TV4 (SPRINT 4)

**Học phần:** Quản trị Dự án Phần mềm (INT1450) — Dự án SASMS  
**Người đánh giá:** **Trần Trung Kiên — Architect / DBA (TV2)**  
**Đối tượng đánh giá:** Vũ Duy Thái — Fullstack Lead (TV4)  
**Phạm vi rà soát:**
1. Cấu hình DB Runtime Role & Cấp tài khoản Runtime LOGIN cho Staging E2E.
2. Quyền hạn dữ liệu và hiệu năng phân hệ BI Analytics Dashboard (S4-03).
3. Cơ chế kiểm soát khóa và nguy cơ Deadlock trong giao dịch POS Order-First.
4. Đối chiếu Quy trình Hypercare D100 (TV4) với Kế hoạch Cutover/Rollback D91 và Runbook D101 (Kiên DBA).  
**Ngày thực hiện:** 23/11/2026  
**Quyết định phê duyệt:** **`APPROVED WITH TECHNICAL NOTES` (PHÊ DUYỆT KÈM LƯU Ý KỸ THUẬT)**

---

## 1. Kết quả kiểm tra DB Runtime Role và Cấp quyền Staging E2E

### 1.1 Xác nhận thiết kế phân quyền CSDL (`sasms_app`)
- **Nguyên tắc phân tầng:** Theo tài liệu kiến trúc CSDL `D50`, `D54` và file `database/runtime-roles.sql`, `sasms_app` được định nghĩa là một **Group Role (`NOLOGIN`, `NOSUPERUSER`)**.
- Group role này đóng vai trò tập hợp các đặc quyền nghiệp vụ (DML: `SELECT`, `INSERT`, `UPDATE` và `EXECUTE` Stored Procedures), tuyệt đối không mở cổng đăng nhập trực tiếp để chống rò rỉ credential quản trị.
- Việc truy vấn `pg_roles` tìm user có `rolcanlogin=true` thuộc `sasms_app` trả về `0 rows` là **hoàn toàn chính xác theo thiết kế chuẩn**.

### 1.2 Bàn giao kịch bản cấp Runtime LOGIN cho Staging
Kiên đã khởi tạo và bổ sung 2 tệp công cụ tự động hóa vào repository:
- **Tệp SQL cấp quyền:** [`scripts/provision_staging_e2e.sql`](../../scripts/provision_staging_e2e.sql)
  - Khởi tạo Login User: **`tv4_staging_app_user`** (mật khẩu: `SasmsApp_Staging_2026!`).
  - Thuộc tính an toàn: `LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS`.
  - Gán quyền nhóm: `GRANT sasms_app TO tv4_staging_app_user;`.
  - Cấp toàn quyền thao tác nghiệp vụ trên 41 bảng và thực thi các Stored Procedures trong schema `sasms`; thu hồi quyền `TRUNCATE` và cấm hoàn toàn quyền `DDL`.
- **Tệp PowerShell tự động hóa container:** [`scripts/create_staging_e2e_container.ps1`](../../scripts/create_staging_e2e_container.ps1)
  - Tự động tạo container PostgreSQL 16 riêng biệt mang tên `sasms_staging_e2e_db` trên cổng **`15435`** (tách biệt hoàn toàn khỏi cổng 15434 của S3/S4 AI và 15433 của S2).
  - Tự động nạp baseline, seed và toàn bộ các bản migration từ **`V001` đến `V008`** (đảm bảo đủ **41 bảng nghiệp vụ**).
  - Tự động xác thực `table_count = 41`, `migration_count = 8`, `rolcanlogin = true`, `is_superuser = false`.

---

## 2. Kiểm tra Quyền hạn và Hiệu năng BI Analytics Dashboard (S4-03)

- **Quyền đọc dữ liệu:** Nhóm `sasms_app` đã được cấp quyền `SELECT` trên toàn bộ 41 bảng nghiệp vụ của hệ thống (bao gồm `sales_orders`, `inventory_movements`, `devices`, `ai_recommendations`, `stores`, `warehouses`).
- **Hiệu năng truy vấn phục vụ Dashboard:**
  - Migration `V008` của Kiên đã bổ sung các chỉ mục chuyên biệt:
    * `ix_sales_orders_s4_paid_store_date`: Phục vụ báo cáo doanh thu theo cửa hàng và thời gian qua Index-Only Scan.
    * `ix_movements_s4_warehouse_date`: Phục vụ thống kê biến động xuất/nhập/tồn kho.
    * `ix_devices_s4_sellable`: Phục vụ kiểm tra tồn kho khả dụng tức thời.
  - Số liệu kiểm chứng thực tế dưới tải 50 client đồng thời (tại `database/evidence/sprint4/summary.json`): Truy vấn tổng hợp `bi_summary` đạt thời gian phản hồi p95 là **`1.86ms`** (tối ưu giảm **184 lần** từ mức 342.39ms ban đầu), hoàn toàn đáp ứng yêu cầu làm mới dữ liệu Dashboard $\le 1$ phút mà không gây tải nặng cho CSDL.
- **Quyền ghi nhận Telemetry:** Bảng `sasms.ai_recommendations` cho phép Backend `INSERT` lịch sử gợi ý phục vụ đối soát, nhưng không cho phép sửa xóa dữ liệu lịch sử tài chính/kho.

---

## 3. Kiểm tra Cơ chế Khóa và Nguy cơ Deadlock trong POS Order-First

- **Triệt tiêu Deadlock bằng Global Ordering:**
  - Trong Stored Procedure `sasms.reserve_order` (`database/schema.sql` dòng 480–482), CSDL áp dụng nguyên tắc sắp xếp tuần tự hóa thiết bị:
    ```sql
    FOR dev IN SELECT d.* FROM sasms.devices d JOIN sasms.order_devices od ON od.device_id = d.id
        WHERE od.order_id = p_order AND od.active ORDER BY d.id FOR UPDATE OF d
    ```
  - Việc luôn lấy khóa trên bảng `devices` theo thứ tự tăng dần của `d.id` loại trừ hoàn toàn nguy cơ xảy ra chu trình chờ khóa (Deadlock) giữa các phiên thu ngân POS đồng thời.
- **Đính chính kỹ thuật trong tài liệu D100 của TV4:**
  - Tại Mục 4 và Mục 12 của tài liệu `D100_HYPERCARE_SLA_TV4.md`, TV4 có ghi: *"Xung đột deadlock P0002 khiến không thể giữ chỗ tồn kho (reserve)"*.
  - **Lưu ý chuẩn xác:** Mã lỗi **`P0002`** trong PostgreSQL thực chất là `NO_DATA_FOUND` (phát sinh khi `SELECT ... INTO STRICT` không tìm thấy dòng dữ liệu). Mã lỗi Deadlock thực sự của PostgreSQL là **`40P01` (`deadlock_detected`)**, và lỗi không lấy được khóa do tranh chấp là **`55P03` (`lock_not_available`)**.
  - Đề nghị TV4 cập nhật chính xác mã SQLSTATE trong tài liệu và exception handler tầng Backend để thuận tiện cho việc đối soát log.
- **Khóa giỏ hàng nháp có thời hạn (Lease Timeout):** Hàm `sasms.find_and_lock_device_for_pos` áp dụng timeout 15 phút, đảm bảo các giỏ hàng bỏ rơi (abandoned carts) sẽ tự động giải phóng thiết bị, không gây giữ khóa vĩnh viễn.

---

## 4. Đối chiếu Tài liệu D100 (TV4) với D91 (Cutover/Rollback) và D101 (Runbook CSDL)

1. **Về Điều kiện kích hoạt Rollback:** Mục 8 của D100 hoàn toàn khớp với Mục 3 của D91 (sự cố P1 kéo dài quá 2 giờ không có giải pháp tạm, sai lệch dữ liệu tài chính không thể hoàn nguyên, tỷ lệ lỗi giao dịch > 15%).
2. **Về Kịch bản Rollback CSDL:** D100 Cấp độ 3 dẫn chiếu chuẩn xác phương án phục hồi vật lý `pg_dump` trước Cutover theo D91. Kịch bản diễn tập thực tế của Kiên ngày 21/11/2026 đã chứng minh thời gian khôi phục CSDL đạt **`0.48 giây`** với **`34/34 checks pass`**, hoàn toàn thỏa mãn mục tiêu SLA của D100.
3. **Về Ma trận Trách nhiệm RACI:**
   - Hoạt động "Xử lý sự cố nghẽn khóa DB / Deadlock / Chậm truy vấn": Kiên làm **R** (Responsible), Thái làm **C** (Consulted) $\rightarrow$ **XÁC NHẬN**.
   - Hoạt động "Thực thi kịch bản Rollback Database / Hạ tầng": Kiên làm **R**, PM Trịnh Quốc An làm **A** (Accountable) $\rightarrow$ **XÁC NHẬN**.
   - *Lưu ý phân định ranh giới:* Kiên chịu trách nhiệm Rollback tầng CSDL (PostgreSQL Restore & Schema). Việc dừng/hoàn nguyên container ứng dụng Backend và Frontend (Rollback Cấp độ 1 & 2) thuộc trách nhiệm thực thi của Thái (TV4).
4. **Về Lịch trực Hypercare 3 ngày:** Kiên xác nhận sẵn sàng trực On-call 24/7 ứng cứu sự cố CSDL tầng P1/P2 trong 3 ngày Hypercare như đề xuất tại Mục 3 của D100 (chờ PM Trịnh Quốc An ký duyệt chính thức).

---

## 5. Kết luận và Ký duyệt

Trần Trung Kiên (Architect / DBA) xác nhận các đóng góp mã nguồn và tài liệu của Vũ Duy Thái (TV4) ở Sprint 4 đã đáp ứng toàn diện các tiêu chuẩn kỹ thuật về giao tiếp CSDL, xử lý transaction an toàn, hiệu năng truy vấn và quy trình vận hành.

Chính thức phê duyệt thông qua với trạng thái: **`APPROVED WITH TECHNICAL NOTES`**.

*(Đã ký)*  
**Trần Trung Kiên**  
Architect / DBA — Nhóm SASMS (INT1450)
