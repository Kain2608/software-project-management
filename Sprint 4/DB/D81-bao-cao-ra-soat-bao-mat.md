# D81 — Báo cáo rà soát bảo mật toàn diện (Sprint 4)

| Thuộc tính | Nội dung |
|---|---|
| **Đầu việc** | S4-13 — Hoàn tất rà soát bảo mật và xử lý lỗ hổng (12 giờ theo kế hoạch) |
| **Phụ trách chính** | Trần Trung Kiên — Architect / DBA |
| **Phối hợp hỗ trợ** | Vũ Duy Thái (Fullstack Lead), Nguyễn Tiến Vũ (Data / AI Lead) |
| **Kỳ báo cáo / Hạn** | Sprint 4; Hạn hoàn thành: **16/11/2026** |
| **Minh chứng đi kèm** | [evidence/security/summary.json](evidence/security/summary.json); log kiểm thử Backend & Frontend |
| **Kết quả Gate** | **PASS — Không còn lỗ hổng mức cao (Zero High/Critical Vulnerabilities)** |

---

## 1. Căn cứ và Phạm vi rà soát

Thực hiện theo phân công S4-13 nhằm đảm bảo hệ thống đạt chuẩn an toàn thông tin trước khi Go-Live Production, không để tồn tại các lỗ hổng bảo mật nghiêm trọng (P1/P2 hoặc CVE High/Critical).

Phạm vi rà soát bảo mật được tự động hóa qua công cụ [scripts/security_sprint4.py](../../scripts/security_sprint4.py), bao gồm 4 lớp bảo vệ:
1. **Phụ thuộc ứng dụng Backend (Java / Spring Boot / Maven).**
2. **Phụ thuộc ứng dụng Frontend (React / Vite / NPM Graph).**
3. **Mã nguồn và tệp cấu hình (Secret & Sensitive Data Leakage).**
4. **Phân quyền và bảo mật tầng Cơ sở dữ liệu (PostgreSQL Runtime Security & ACL).**

---

## 2. Kết quả rà soát chi tiết

### 2.1 Quét phụ thuộc Backend (OSV-Scanner / Maven)
- **Công cụ:** OSV-Scanner phân tích cây phụ thuộc Maven đã phân giải hoàn chỉnh (Resolved Maven Dependency Tree gồm 91 packages, bao gồm cả test scope).
- **Kết quả rà soát:**
  ```json
  "backend": {
    "status": "PASS",
    "packages": 91,
    "counts": {
      "LOW": 0,
      "MODERATE": 0,
      "HIGH": 0,
      "CRITICAL": 0,
      "UNKNOWN": 0
    }
  }
  ```
- **Xử lý trước đó:** Đã nâng cấp các thư viện liên quan đến Spring Security và JWT parser; loại bỏ hoàn toàn các CVE đã biết. Không còn lỗ hổng nào mức High hoặc Critical.

### 2.2 Quét phụ thuộc Frontend (NPM Audit)
- **Công cụ:** `npm audit --json` trên toàn bộ đồ thị `package-lock.json` của thư mục `frontend/`, bao gồm cả dependencies và devDependencies.
- **Kết quả rà soát:**
  ```json
  "frontend": {
    "status": "PASS",
    "exit_code": 0,
    "counts": {
      "info": 0, "low": 0, "moderate": 0, "high": 0, "critical": 0, "total": 0
    }
  }
  ```
- **Đánh giá:** Toàn bộ cây gói thư viện Frontend đạt mức an toàn tối đa: **0 lỗ hổng bảo mật**.

### 2.3 Quét rò rỉ mã bí mật (Limited Secret Scan)
- **Phạm vi:** Quét tự động **300 tệp tin** mã nguồn, cấu hình, migration và script trong kho lưu trữ repository.
- **Mẫu nhận diện (Patterns):**
  - Khóa riêng tư (`private_key`)
  - Mã truy cập GitHub (`github_token`)
  - Khóa truy cập AWS (`aws_access_key_id`)
  - Mã định danh dịch vụ SMS / LLM (`provider_token`)
- **Kết quả:** **0 phát hiện vi phạm (`matches: []`)**. Không có bất kỳ secret thật nào bị commit vào Git. Mọi thông tin nhạy cảm đều được cấu hình qua biến môi trường hoặc Secret Manager.

### 2.4 Bảo mật và phân quyền CSDL (PostgreSQL Runtime ACL)
- **Tài khoản chạy ứng dụng:** Toàn bộ Backend khi kết nối CSDL bắt buộc sử dụng tài khoản thuộc nhóm **`sasms_app`**, tuyệt đối không dùng tài khoản `postgres` hay `sasms_owner`.
- **Giới hạn quyền hạn:**
  - Nhóm `sasms_app` chỉ được cấp quyền thao tác DML nghiệp vụ (`SELECT`, `INSERT`, `UPDATE`) và thực thi các Stored Functions được phép.
  - Hoàn toàn bị thu hồi quyền DDL (`CREATE`, `DROP`, `ALTER`).
  - Đã thu hồi toàn bộ quyền kết nối công cộng `PUBLIC CONNECT` và quyền thực thi mặc định `PUBLIC EXECUTE`.
- **Bảo vệ Stored Procedures:** Mọi hàm nghiệp vụ trọng yếu đều được định nghĩa với `SECURITY DEFINER` và cố định tham số `SET search_path = pg_catalog, sasms`, ngăn chặn triệt để tấn công Search Path Hijacking.

---

## 3. Bảng tổng hợp kết quả và Kết luận

| Lớp bảo mật | Tiêu chí đánh giá | Số lượng kiểm tra | Kết quả ghi nhận | Trạng thái |
|---|---|---|---|---|
| **Backend Dependencies** | Zero CVE High / Critical | 91 packages | 0 lỗ hổng | **ĐẠT (PASS)** |
| **Frontend Dependencies** | Zero Vulnerability | Đồ thị npm lock | 0 lỗ hổng | **ĐẠT (PASS)** |
| **Source & Config Secrets** | Không rò rỉ API Key / Private Key | 300 tệp | 0 vi phạm | **ĐẠT (PASS)** |
| **Database Access Control** | Chạy bằng `sasms_app`, cấm superuser | Toàn bộ 41 bảng | Đã thu hồi PUBLIC | **ĐẠT (PASS)** |

**Kết luận của Architect / DBA:**  
Hệ thống SASMS đáp ứng đầy đủ điều kiện an toàn thông tin theo tiêu chí Cổng kiểm soát chất lượng M6, sẵn sàng cho việc cấp phát hạ tầng và triển khai chính thức trên Production.

