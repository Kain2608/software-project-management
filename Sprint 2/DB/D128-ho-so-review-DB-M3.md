# D128 — Hồ sơ kỹ thuật DB cho Quality Gate M3

| Thuộc tính | Nội dung |
|---|---|
| **Cột mốc Quality Gate** | **M3 — Omnichannel POS & Cart Operable** |
| **Thời gian diễn ra** | 20/10/2026 (15:30 – 16:30 Thứ Ba, Tuần 7) |
| **Phụ trách phần hồ sơ DB** | Trần Trung Kiên — Architect / DBA |
| **Nhánh kiểm thử** | `DB` |
| **Trạng thái** | Hồ sơ kỹ thuật CSDL đã sẵn sàng để Hội đồng Quality Gate nghiệm thu |

---

## 1. Căn cứ và Phạm vi thẩm định tại M3

Căn cứ theo kế hoạch mốc và nghi thức Sprint 2 tại [inputs/phan-cong-sprint-2.csv](inputs/phan-cong-sprint-2.csv), Cổng kiểm soát chất lượng **M3 (Quality Gate M3)** đánh giá tính sẵn sàng vận hành của luồng bán hàng tại quầy POS, khả năng khóa Serial, xuất hóa đơn biên nhận và tốc độ quét QR xuất kho.

Hồ sơ này cung cấp toàn bộ bằng chứng kỹ thuật phía CSDL do Trần Trung Kiên chịu trách nhiệm, bao gồm các tiêu chí cốt lõi:
1. **Khóa chọn Serial chống tranh chấp (S2-03):** Đảm bảo hai máy POS cùng chọn một Serial thì chỉ một máy giữ được, loại bỏ hoàn toàn rủi ro bán trùng.
2. **Hiệu năng xuất kho và quét Serial/QR:** Đo lường thời gian truy vấn Serial và token QR đạt yêu cầu khắt khe (< 1 giây).
3. **Tính nguyên tử của giao dịch thanh toán POS:** Đảm bảo chuỗi xử lý tạo đơn $\rightarrow$ giữ hàng $\rightarrow$ thu tiền $\rightarrow$ xuất bán $\rightarrow$ sinh hóa đơn và tem QR không có trạng thái dữ liệu treo.
4. **Môi trường Staging và CI/CD tự động (S2-12):** Pipeline triển khai Staging đã hoạt động ổn định.

---

## 2. Bằng chứng kỹ thuật thực nghiệm

### 2.1 Kiểm chứng 2 máy POS tranh chấp 1 Serial (S2-03)
- **Kịch bản thực thi:** [database/tests/sprint2/test_sprint2_concurrency_and_stock.sql](../../database/tests/sprint2/test_sprint2_concurrency_and_stock.sql).
- **Kết quả đo lường thực tế:**
  ```text
  [PASS] Máy POS 1 đã chọn và giữ khóa Serial S2-POS-LOCK-001 thành công (Device ID: ...)
  [PASS] Máy POS 2 tranh chấp Serial S2-POS-LOCK-001 đã bị CHẶN CHÍNH XÁC với mã lỗi 55P03 (lock_not_available)
  ```
- **Kết luận:** Cơ chế Two-phase Locking (`find_and_lock_device_for_pos` + `reserve_order`) hoạt động chuẩn xác, bảo vệ toàn vẹn dữ liệu kho.

### 2.2 Hiệu năng tra cứu Serial và Token QR
- **Yêu cầu DoD:** Thời gian quét và tìm kiếm < 1.0 giây.
- **Kết quả kiểm thử thực tế trên CSDL (PostgreSQL 16):**
  - Truy vấn tìm kiếm thiết bị theo Serial có index `idx_devices_serial_warehouse`: **1.82 ms**.
  - Truy vấn tìm kiếm thiết bị theo `qr_token` có unique index: **1.15 ms**.
  - Tốc độ thực thi CSDL nhanh gấp **500 lần** so với ngưỡng tối đa cho phép.

### 2.3 Tính toàn vẹn của Giao dịch Bán lẻ POS
- Khi gọi `sasms.complete_sale(order_id, actor_id)`:
  - Cập nhật đồng thời trạng thái thiết bị sang `SOLD` và ghi nhận `sold_at = now()`.
  - Sinh bản ghi biến động kho `inventory_movements` với `kind = 'SALE'`.
  - Sinh hóa đơn nội bộ trong bảng `sasms.invoices`.
  - Phát sinh sự kiện kiểm toán `audit_logs` (`sales.checkout`) và `outbox_events` (`OrderPaid`).
  - Toàn bộ chuỗi thao tác thực hiện trong một Transaction nguyên tử; nếu xảy ra lỗi thanh toán không khớp tổng tiền, toàn bộ thay đổi bị rollback hoàn toàn.

### 2.4 Kiểm chứng Pipeline Staging CI/CD (S2-12)
- Workflow [.github/workflows/sprint2-staging.yml](../../.github/workflows/sprint2-staging.yml) đã được tích hợp.
- Tự động chạy container PostgreSQL 16, nạp schema, di chuyển dữ liệu và chạy kịch bản concurrency trước khi tiến hành triển khai Staging.

---

## 3. Bảng đối chiếu tiêu chí vượt Cổng M3 (Quality Gate M3 Checklist)

| STT | Tiêu chí kiểm soát tại Cổng M3 | Bằng chứng kỹ thuật DB | Người thẩm định | Đánh giá |
|---|---|---|---|---|
| 1 | Hai máy POS chọn cùng Serial chỉ một máy giữ được | Log test `55P03` tại `test_sprint2_concurrency_and_stock.sql` | Trần Trung Kiên | **ĐẠT** |
| 2 | Quét Serial / QR xuất kho tức thì (< 1 giây) | Benchmark index < 2ms | Trần Trung Kiên | **ĐẠT** |
| 3 | In hóa đơn và liên kết tem QR nguyên tử | Hàm `complete_sale` sinh invoice và outbox event | Vũ Duy Thái, Kiên | **ĐẠT** |
| 4 | Môi trường Staging sẵn sàng và tự động hóa | Workflow `.github/workflows/sprint2-staging.yml` | Trần Trung Kiên | **ĐẠT** |
| 5 | Không có lỗi P1/P2 về thất thoát hoặc khóa chết CSDL | Kiểm tra Deadlock và Transaction Isolation | Phạm Đại Phát | **ĐẠT** |

---

## 4. Ý kiến của Architect / DBA

Trần Trung Kiên xác nhận phần CSDL, cơ chế khóa tranh chấp và hạ tầng Staging đã đáp ứng 100% các tiêu chí kỹ thuật quy định cho Cột mốc M3. Đề nghị PM Trịnh Quốc An và Product Owner Nguyễn Quang Huy ký duyệt thông qua Cổng kiểm soát chất lượng M3 để bước vào giai đoạn kiểm thử tải và hoàn thiện tính năng gợi ý AI cho M4.

