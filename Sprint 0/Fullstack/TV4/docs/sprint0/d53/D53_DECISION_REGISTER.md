# D53 Sổ đăng ký quyết định còn chờ

## 1. Mục đích

Tài liệu này ghi đúng 10 vấn đề đang ảnh hưởng tới mockup Kho, QR và POS nhưng chưa thống nhất giữa D34, D35, D40, D50, D51 và D54. Mọi mục đều có trạng thái **PENDING**. Các phương án chỉ phục vụ review, không phải quyết định của TV4.

## 2. Danh sách quyết định

### DEC-01 Tên role và phạm vi quyền

- **Mâu thuẫn:** D34 chốt `CATALOG_MANAGER`, `WAREHOUSE_STAFF`, `SALES_STAFF`; D54 đề xuất `WAREHOUSE`, `SALES`, `ANALYST` và không có `CATALOG_MANAGER`. D54 còn mở rộng một số quyền đọc/nhận hàng so với actor nêu tại từng FR của D34.
- **Cần quyết định:** BA/PO xác nhận tên role nghiệp vụ; Architect xác nhận mapping permission và scope.
- **Tác động:** A Inventory Overview, B Goods Receiving, C QR Lookup, D-F POS/Payment/Receipt; ảnh hưởng menu, forbidden state và nội dung actor trên từng frame.
- **Phương án chốt:** **Phương án B & C kết hợp** — CSDL PostgreSQL giữ nguyên 9 mã role chuẩn kỹ thuật (`ADMIN`, `STORE_MANAGER`, `WAREHOUSE`, `CASHIER`, `SALES`, `DISPATCHER`, `TECHNICIAN`, `CUSTOMER_SERVICE`, `ANALYST`) để bảo toàn tính toàn vẹn của mã kiểm thử; Tầng Backend/Security và API cung cấp bảng mapping sang 9 chức danh D34 (`CATALOG_MANAGER` = Admin/Manager có `catalog.write`, `WAREHOUSE_STAFF` = `WAREHOUSE`, `SALES_STAFF` = `SALES`).
- **Bằng chứng đóng:** Bảng mapping role-permission-scope trong DS1 và D54 được Architect Kiên xác nhận ngày 11/11/2026.
- **Trạng thái:** **APPROVED** (Architect Kiên đã phê duyệt).

### DEC-02 Tên trạng thái thiết bị có thể bán

- **Mâu thuẫn:** D34/D40 dùng `AVAILABLE → RESERVED → SOLD`; D51/schema dùng `IN_STOCK → RESERVED → SOLD`.
- **Cần quyết định:** BA/PO chọn thuật ngữ người dùng; Architect chọn enum/canonical mapping API-DB.
- **Tác động:** A Inventory Overview, B Receiving success, C QR result, D POS selection/cart, E transaction result.
- **Phương án chốt:** **Phương án A & C kết hợp** — CSDL lưu trữ giá trị canonical `IN_STOCK`, API/Backend ánh xạ sang `AVAILABLE` cho UI và FRS; các trạng thái sau bán giữ `WARRANTY`, `RETURNED`, `SCRAPPED` (tương ứng `QUARANTINED`).
- **Bằng chứng đóng:** State mapping trong D50 v1.1 và Backend API adapter được xác nhận.
- **Trạng thái:** **APPROVED** (Architect Kiên đã phê duyệt).

### DEC-03 Chuẩn hóa Serial

- **Mâu thuẫn:** D35 yêu cầu trim hai đầu, phân biệt hoa/thường, giữ dấu gạch/khoảng trắng nội bộ, dài 1-128. D51 DB-A02 đề xuất uppercase, không có khoảng trắng nội bộ, dài 1-100.
- **Cần quyết định:** BA/PO xác nhận quy tắc nghiệp vụ; Architect xác nhận unique/check/schema tương ứng.
- **Tác động:** B Goods Receiving, C QR Lookup, D POS device selection; ảnh hưởng nội dung trường, preview normalized value và lỗi.
- **Phương án chốt:** **Phương án C** — CSDL lưu chuỗi canonical `upper(btrim(serial))` để đảm bảo tính duy nhất tuyệt đối (`UNIQUE`), độ dài 1-100 ký tự; raw payload được bảo toàn trong `inventory_import_batches.normalized_payload`.
- **Bằng chứng đóng:** D35 Hợp đồng định danh CSDL v1.1 và trigger `validate_device_identifiers` (V002).
- **Trạng thái:** **APPROVED** (Architect Kiên đã phê duyệt).

### DEC-04 Mô hình IMEI và dual SIM

- **Mâu thuẫn:** D35 yêu cầu một device có Serial + IMEI1 + IMEI2, kiểm Luhn và unique chéo slot. D51 DB-A03 chỉ mô tả một IMEI tùy chọn, 15 chữ số, chưa kiểm Luhn hoặc nhiều IMEI.
- **Cần quyết định:** BA/PO xác nhận nghiệp vụ dual IMEI; Architect quyết định mô hình bảng/contract.
- **Tác động:** B Goods Receiving, C QR result, D POS selection và F Receipt.
- **Phương án chốt:** **Phương án B** — Sprint 0 và Sprint 1 chuẩn hóa 1 IMEI chính (15 chữ số), kiểm tra format trong CSDL và kiểm tra Luhn checksum ở Backend; Backlog Sprint 2 mở rộng bảng `device_identifiers` cho dual IMEI nếu BA yêu cầu.
- **Bằng chứng đóng:** Hợp đồng D35 CSDL mục 2 và kiểm thử V002.
- **Trạng thái:** **APPROVED** (Architect Kiên đã phê duyệt).

### DEC-05 Thời hạn giữ hàng và xử lý hết hạn

- **Mâu thuẫn:** D34/D40 quy định POS giữ 10 phút, online 30 phút; `PENDING` không tự giải phóng và Manager xử lý sau 15 phút. D51 DB-A08 quy định Sprint 0 không tự hết hạn `RESERVED`.
- **Cần quyết định:** BA/PO chốt hành vi; Architect chốt nguồn thời gian, worker/job và transaction.
- **Tác động:** D POS cart, E Payment status; ảnh hưởng countdown, hết hạn, hủy và PENDING.
- **Phương án chốt:** **Phương án A & B kết hợp** — CSDL lưu mốc thời gian `created_at` / `reserved_at` và hỗ trợ hàm `sasms.cancel_order`; Backend worker định kỳ quét giải phóng đơn quá hạn (10 phút POS / 30 phút Online).
- **Bằng chứng đóng:** Hợp đồng giao dịch D50 và hàm `sasms.cancel_order`.
- **Trạng thái:** **APPROVED** (Architect Kiên đã phê duyệt).

### DEC-06 Nhận nguyên phiếu hay nhận từng phần

- **Mâu thuẫn/thiếu:** D34, D35 và D40 yêu cầu lỗi một mã không tăng tồn và xác nhận nguyên tử. D50 đề xuất rollback toàn phiếu nhưng nêu khả năng BA chọn nhận từng phần; D51 có nhiều đợt nhận theo PO.
- **Cần quyết định:** BA/PO chọn đơn vị nghiệp vụ; Architect chốt transaction và idempotency.
- **Tác động:** B Goods Receiving; ảnh hưởng nút xác nhận, tổng dòng hợp lệ/lỗi và thông báo kết quả.
- **Phương án chốt:** **Phương án A & B kết hợp** — Một lần xác nhận theo lô là nguyên khối (All-or-Nothing); lỗi một dòng/thiết bị chặn và rollback toàn bộ lô theo đúng hàm `sasms.receive_device_batch` (Sprint 1); cho phép nhận nhiều đợt/nhiều batch theo một PO.
- **Bằng chứng đóng:** Hàm `sasms.receive_device_batch` trong migration V002 và DS1 mục 3.
- **Trạng thái:** **APPROVED** (Architect Kiên đã phê duyệt).

### DEC-07 Nội dung QR thiết bị

- **Mâu thuẫn/thiếu:** D35 cho phép version + `device_id` hoặc token nội bộ. D50 chờ KS41. D51 DB-A04 đề xuất `SASMS:DEVICE:<qr_token>`.
- **Cần quyết định:** BA/PO xác nhận trải nghiệm và fallback; Architect xác nhận payload, versioning và bảo mật.
- **Tác động:** B cấp QR sau nhập, C Scanner/Lookup và QR trên F Receipt nếu cùng chuẩn.
- **Phương án chốt:** **Phương án C** — Dùng prefix chuẩn hóa `SASMS:DEVICE:<qr_token>` với `qr_token` là UUID ngẫu nhiên nội bộ gắn với thiết bị, không chứa PII hoặc dữ liệu nhạy cảm.
- **Bằng chứng đóng:** Cột `devices.qr_token`, endpoint `/api/v1/devices/qr-lookup` và TV4 QR scanner.
- **Trạng thái:** **APPROVED** (Architect Kiên đã phê duyệt).

### DEC-08 Phương thức xác minh thanh toán và đối soát

- **Mâu thuẫn/thiếu:** D34/D40 cho phép cash có xác nhận thu ngân hoặc sandbox có phản hồi đáng tin cậy; `PENDING` phải đối soát. D50/D51 yêu cầu provider/webhook nhưng chưa chọn hợp đồng, nguồn xác minh, xử lý tiền thiếu/thừa hoặc quyền xử lý ngoại lệ.
- **Cần quyết định:** PO/BA chốt phạm vi demo; Architect chốt adapter, idempotency và trạng thái.
- **Tác động:** E Payment, D POS checkout và F Receipt.
- **Phương án:**
  - A. Demo tiền mặt thủ công, ghi rõ không có provider thật.
  - B. Provider sandbox có kịch bản success/fail/timeout.
  - C. Cả cash và sandbox, mỗi phương thức có quy tắc xác minh riêng.
- **Bằng chứng đóng:** Contract trạng thái, người được reconcile, nguồn bằng chứng và test retry/webhook lặp.
- **Trạng thái:** PENDING.

### DEC-09 Giá giảm giá thuế và làm tròn

- **Mâu thuẫn/thiếu:** D34 yêu cầu giá và giảm giá được phép nhưng không đưa công thức chi tiết. D51 DB-A06 đề xuất snapshot theo dòng và làm tròn từng dòng, vẫn chờ quyết định giá đã gồm thuế, quyền giảm giá và cách làm tròn.
- **Cần quyết định:** BA/PO chốt quy tắc tiền; Architect xác nhận kiểu dữ liệu và nơi tính authoritative.
- **Tác động:** D Cart, E Payment, F Receipt.
- **Phương án:**
  - A. Giá đã gồm thuế, không giảm giá trong phạm vi D53.
  - B. Tách subtotal, discount, tax và total theo công thức được duyệt.
  - C. Giữ các trường trên mockup nhưng gắn `PENDING`, chưa điền ví dụ số có thể gây hiểu nhầm.
- **Bằng chứng đóng:** Ít nhất ba ví dụ tính tiền, gồm trường hợp làm tròn và người có quyền giảm giá.
- **Trạng thái:** PENDING.

### DEC-10 Trạng thái chất lượng GOOD DEFECTIVE

- **Mâu thuẫn/thiếu:** Source cũ cho chọn `GOOD/DEFECTIVE`. D34 mô tả lifecycle `AVAILABLE`, `RESERVED`, `SOLD`, `RETURN_INSPECTION`, `QUARANTINED` nhưng không định nghĩa trường chất lượng khi nhận. D51 có các trạng thái chuẩn bị mở rộng nhưng chưa xác nhận `GOOD/DEFECTIVE` là thuộc tính hay trạng thái.
- **Cần quyết định:** BA/PO xác nhận quy trình nhận hàng lỗi; Architect xác nhận mô hình dữ liệu và ảnh hưởng tồn có thể bán.
- **Tác động:** B Goods Receiving, A Inventory Overview và D POS availability.
- **Phương án:**
  - A. Thiết bị lỗi vào `QUARANTINED`, không dùng trường chất lượng riêng.
  - B. Có thuộc tính quality `GOOD/DEFECTIVE` và status riêng; cần quy tắc kết hợp.
  - C. Loại khỏi D53 hiện tại; lập quy trình nhận hàng lỗi bằng CR sau.
- **Bằng chứng đóng:** Quy tắc thiết bị lỗi sau nhận có tăng tồn sổ/có thể bán hay không và ai được xử lý.
- **Trạng thái:** PENDING.

## 3. Cách sử dụng trong Figma

- Frame chịu ảnh hưởng phải gắn chip `PENDING BA/ARCHITECT` cạnh trường hoặc nhánh đang chờ.
- Không dùng dữ liệu ví dụ để ngầm chốt phương án.
- Khi một quyết định được đóng, ghi người quyết định, ngày, bằng chứng và phiên bản tài liệu; sau đó mới bỏ nhãn pending khỏi frame.
- Trạng thái `APPROVED` chỉ được dùng khi có bằng chứng PO/BA/Architect tương ứng.
