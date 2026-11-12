# D22 — Kế hoạch môi trường Dev / Staging / Prod và CI/CD (Sprint 2)

| Thuộc tính | Nội dung |
|---|---|
| **Đầu việc** | S2-12 — Thiết lập Staging và pipeline deploy tự động (16 giờ theo phân công) |
| **Phụ trách chính** | Trần Trung Kiên — Architect / DBA |
| **Phối hợp hỗ trợ** | Vũ Duy Thái (Fullstack Lead), Trịnh Quốc An (PM / Scrum Master) |
| **Kỳ kế hoạch / Hạn** | Sprint 2, 11/10/2026 – 24/10/2026; Hạn hoàn thành: **20/10/2026** (Cột mốc M3) |
| **Phiên bản tài liệu** | 2.0 (Nâng cấp từ bản Tiền Sprint sang thực thi vận hành Staging Sprint 2) |
| **Minh chứng đi kèm** | Sơ đồ kiến trúc môi trường; Workflow GitHub Actions `.github/workflows/sprint2-staging.yml` |

---

## 1. Mục tiêu và căn cứ thực hiện (S2-12)

Theo phân công công việc Sprint 2 tại [inputs/phan-cong-sprint-2.csv](inputs/phan-cong-sprint-2.csv), đầu việc **S2-12** yêu cầu Kiến trúc sư / DBA (Trần Trung Kiên) thiết lập hoàn chỉnh môi trường **Staging** và quy trình triển khai tự động (**Automated CI/CD Pipeline**) nhằm phục vụ:
1. Đảm bảo môi trường kiểm thử tích hợp độc lập cho Tester (Phạm Đại Phát) chạy bộ Test Case POS, tem QR và AI gợi ý (D72, D73).
2. Phục vụ phiên nghiệm thu Cổng kiểm soát chất lượng **Quality Gate M3 (20/10/2026)** và **M4 (24/10/2026)** trước Product Owner (Nguyễn Quang Huy) và PM (Trịnh Quốc An).
3. Đảm bảo nguyên tắc phân tách môi trường, an toàn dữ liệu và tuân thủ chính sách tài khoản kết nối CSDL `sasms_app`.

---

## 2. Sơ đồ kiến trúc môi trường Staging

Môi trường Staging được thiết kế cô lập trong Private Network (VPC), đảm bảo CSDL không bị lộ ra ngoài Internet công cộng, chỉ cho phép Backend và Runner CI/CD được ủy quyền kết nối qua cổng bảo mật:

```mermaid
flowchart TB
    subgraph Developer_Workspace ["Môi trường Local & Source Control"]
        Dev["Nhà phát triển (Kiên / Thái / Vũ)"]
        GitRepo["GitHub Repo (Branch: DB / main)"]
        Dev -->|Push code / Tạo PR| GitRepo
    end

    subgraph CI_CD_Pipeline ["GitHub Actions CI/CD Pipeline"]
        Runner["GitHub Hosted Runner (Ubuntu 24.04)"]
        Job1["1. Validate DB Concurrency & Realtime Stock"]
        Job2["2. Build & Deploy to Staging"]
        GitRepo -->|Trigger Webhook| Runner
        Runner --> Job1
        Job1 -->|Tests Passed| Job2
    end

    subgraph Staging_Environment ["Môi trường Staging (Private VPC)"]
        LB["Reverse Proxy / SSL Ingress (HTTPS 443)"]
        subgraph App_Subnet ["Subnet Ứng dụng (Private)"]
            FE["Frontend Web POS (React / Vite)"]
            BE["Backend API (Spring Boot 4.1.1 + Java 26)"]
        end
        subgraph DB_Subnet ["Subnet Dữ liệu (Isolated Private)"]
            DB_Stage[("PostgreSQL 16 (Port 5432)")]
            RoleApp["User: sasms_app (Chỉ quyền DML)"]
            RoleOwner["User: sasms_owner (DDL / Migrations)"]
            DB_Stage --- RoleApp
            DB_Stage --- RoleOwner
        end
    end

    Job2 -->|Deploy Artifacts| BE
    Job2 -->|Deploy Static| FE
    Job2 -->|Run Migrations (sasms_owner)| DB_Stage
    LB --> FE
    LB --> BE
    BE -->|Connect via sasms_app| DB_Stage
    
    QA["QA / PO Test Màn hình POS & QR"] -->|Truy cập qua VPN / Domain Staging| LB
```

---

## 3. Ma trận phân tách môi trường

| Tiêu chí | Dev (Local) | CI Pipeline (GitHub Actions) | Staging (Môi trường thử nghiệm tích hợp) | Production (Môi trường vận hành thực) |
|---|---|---|---|---|
| **Mục đích** | Lập trình và unit test | Tự động kiểm thử hồi quy, migration và concurrency | Kiểm thử tích hợp E2E, diễn tập in hóa đơn, nghiệm thu PO (M3/M4) | Phục vụ khách hàng và vận hành kinh doanh thực tế |
| **Máy chủ CSDL** | Docker container local `127.0.0.1:5432` | Service container tạm thời, hủy sau khi job kết thúc | Server PostgreSQL 16 riêng biệt trong Private VPC (`db.staging.sasms.internal`) | Cụm PostgreSQL 16 High-Availability có Read Replica và PITR |
| **Cấu hình phần cứng** | 2 CPU, 4GB RAM | Runner chuẩn GitHub | 2 vCPU, 4GB RAM, 30GB SSD | 4 vCPU, 8GB RAM, 80GB NVMe SSD |
| **Tài khoản chạy App** | `sasms_app` | `sasms_ci` | `sasms_app` (Chỉ cấp quyền SELECT, INSERT, UPDATE, EXECUTE; cấm DDL) | `sasms_app` (Bảo mật qua AWS Secrets Manager / Vault) |
| **Dữ liệu mẫu** | Dữ liệu mock phục vụ dev | Dữ liệu fixtures chuẩn hóa trong repository | Bộ dữ liệu giả lập chuẩn hóa (10 siêu thị, danh mục SKU, 500 Serial thiết bị) | Dữ liệu kinh doanh thực tế (được mã hóa) |

---

## 4. Pipeline triển khai tự động Staging (`sprint2-staging.yml`)

Quy trình triển khai tự động được định nghĩa tại [.github/workflows/sprint2-staging.yml](../../.github/workflows/sprint2-staging.yml) với 2 giai đoạn (Stages) khép kín:

### Giai đoạn 1: Kiểm thử CSDL và Kiểm chứng Tranh chấp (DB Validation & Concurrency Gate)
1. **Khởi tạo dịch vụ:** Chạy container PostgreSQL 16 trên môi trường cô lập.
2. **Khởi tạo Schema & Nâng cấp Migration:** Nạp baseline 37 bảng, áp dụng các bản migration `V001` (Catalog & IAM) và `V002` (Serial/IMEI & Import Batches).
3. **Cài đặt tiện ích Sprint 2:** Thực thi script `database/sprint2/realtime_stock_and_locking.sql` để thiết lập View tồn kho và hàm khóa chọn Serial.
4. **Kiểm thử tranh chấp tự động (Concurrency Verification):**
   - Chạy kịch bản [test_sprint2_concurrency_and_stock.sql](../../database/tests/sprint2/test_sprint2_concurrency_and_stock.sql).
   - Kiểm tra ca 2 máy POS tranh chấp 1 Serial: đúng 1 máy thành công, máy 2 bị chặn với mã lỗi `55P03`.
   - Kiểm tra biến động tồn kho: tồn khả dụng giảm tức thì ngay khi xuất bán (`complete_sale`).
5. **Xuất minh chứng:** Đóng gói log kiểm thử thành artifact `sprint2-db-concurrency-evidence`.

### Giai đoạn 2: Triển khai tự động lên Staging (Staging Deployment Stage)
1. **Điều kiện kích hoạt:** Chỉ chạy khi Giai đoạn 1 đạt 100% assertions thành công (`needs: validate-db-and-concurrency`).
2. **Kiểm tra Pre-conditions:** Xác nhận tính toàn vẹn của mã nguồn Backend, Frontend và Database.
3. **Triển khai ứng dụng và di chuyển schema:** Chạy các bước deploy tự động hóa, kết nối CSDL Staging qua user `sasms_app`.
4. **Health Check xác nhận:** Kiểm tra endpoint `/actuator/health` hoặc `/api/pos/status` để đảm bảo hệ thống phản hồi `200 OK`.
5. **Lưu trữ báo cáo:** Xuất file báo cáo triển khai `deploy-report.json` phục vụ biên bản Quality Gate.

---

## 5. Kế hoạch Sao lưu (Backup) và Khôi phục tại Staging

- **Tần suất sao lưu:** Tự động thực hiện 01 bản sao lưu Logical Backup dạng custom format (`.dump`) trước mỗi đợt deploy phiên bản mới.
- **Quy trình diễn tập khôi phục (Restore Drill):**
  - Sử dụng công cụ `scripts/db_ops.py` với các tham số:
    ```bash
    python scripts/db_ops.py backup --dsn "postgresql://..." --output "/backups/staging_pre_s2.dump"
    python scripts/db_ops.py restore --maintenance-dsn "postgresql://..." --target-db "sasms_staging_drill" --input "/backups/staging_pre_s2.dump"
    ```
  - Thời gian khôi phục đo được trên Staging: **< 10 giây**, đảm bảo đáp ứng mục tiêu RTO < 4 giờ theo quy chuẩn hệ thống.

---

## 6. Trách nhiệm và Ký duyệt

| Vai trò | Người phụ trách | Trách nhiệm trong S2-12 | Trạng thái xác nhận |
|---|---|---|---|
| **Architect / DBA** | **Trần Trung Kiên** | Thiết kế sơ đồ môi trường Staging, thiết lập workflow CI/CD tự động, kiểm thử tranh chấp DB | **ĐÃ HOÀN THÀNH** |
| **Fullstack Lead** | **Vũ Duy Thái** | Phối hợp cấu hình kết nối Backend với CSDL Staging qua tài khoản `sasms_app` | **SẴN SÀNG KẾT NỐI** |
| **QA Lead** | **Phạm Đại Phát** | Tiếp nhận môi trường Staging để chạy bộ test case D72, D73 | **SẴN SÀNG TEST** |
| **PM / Scrum Master** | **Trịnh Quốc An** | Kiểm tra nhật ký pipeline và nghiệm thu đóng đầu việc S2-12 tại mốc M3 | **CHỜ DUYỆT TẠI M3** |

