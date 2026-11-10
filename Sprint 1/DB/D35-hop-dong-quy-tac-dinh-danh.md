# D35 — Hợp đồng DB cho quy tắc Serial/IMEI

**Phần Kiên hỗ trợ S1-02 của BA Huy; phiên bản 1.1 (Cập nhật ngày 11/11/2026: Tiếp nhận & đồng bộ hồ sơ D35 chính thức của BA).** Tài liệu mô tả cách DB lưu/áp dụng quy tắc định danh theo đặc tả chính thức của BA.

## 1. Nguồn và tình trạng

[Workbook](inputs/phan-cong-sprint-1.xlsx), sheet “Phân chia công việc” dòng 6 và “Theo người” dòng 15/25, giao Huy chốt quy tắc riêng Sony, Samsung, LG, Apple… với nguồn hãng/đại lý và xác nhận trưởng kho; Kiên hỗ trợ phần DB.

**Hiện trạng tiếp nhận hồ sơ:** BA Nguyễn Quang Huy đã chính thức hoàn thiện tài liệu [Sprint 0/BA/D35_Quy_tac_Serial_IMEI_SASMS.docx](../../Sprint%200/BA/D35_Quy_tac_Serial_IMEI_SASMS.docx) (ngày 07/11/2026) với 5 nguồn đối chiếu chính thức:
- `SRC-01`: Sony Support (How to find model name and serial number — bài viết 00248421).
- `SRC-02`: Samsung Support (How to find model and serial number).
- `SRC-03`: LG Support (How to check model and serial numbers).
- `SRC-04`: Apple Support (Find serial number, EID or IMEI).
- `SRC-05`: GSMA TS.06 version 22 mục 5 và 5.1.5 (IMEI Allocation & Luhn algorithm).

Hồ sơ CSDL Sprint 1 của Kiên ([V002](../../database/migrations/V002__inventory_batches_and_identifiers.sql)) đã tạo sẵn bảng `sasms.identifier_rules` và trigger `sasms.validate_device_identifiers()` hoàn toàn tương thích để tiếp nhận toàn bộ các rule này.

## 2. Quy tắc kỹ thuật chung hiện dùng

| Thuộc tính | Xử lý DB | Phần chưa được suy diễn |
|---|---|---|
| Serial | Trim hai đầu, chữ hoa, không khoảng trắng bên trong, dài 1–100; duy nhất toàn chuỗi | Không coi đây là regex chính thức của từng hãng hoặc model |
| IMEI | Có thể NULL theo quy tắc áp dụng; nếu có, đúng 15 chữ số và duy nhất | Kiểm tra độ dài 15 số bằng SQL regex; thuật toán Luhn do Backend Service kiểm tra trước khi ghi |
| SKU | Mã catalog riêng, trim/chữ hoa, tối đa 64, không khoảng trắng; duy nhất | Không dùng SKU thay Serial của từng thiết bị |
| Token QR | UUID định danh của thiết bị; payload chuẩn `SASMS:DEVICE:<qr_token>` | Không xem mọi QR của nhà cung cấp đều có cùng payload; không chứa PII/secret |

Canonical hóa phải diễn ra trước kiểm tra trùng và trước lưu payload của batch. Serial chỉ khác hoa/thường hoặc khoảng trắng hai đầu không trở thành hai thiết bị. Phần tử sai hoặc trùng trong cùng một lô làm rollback toàn lô.

## 3. Cấu hình theo hãng

`identifier_rules` trong [V002](../../database/migrations/V002__inventory_batches_and_identifiers.sql) lưu quy tắc theo hãng và phiên bản, mặc định chưa active. Rule chỉ được active khi có `source_reference`, `approved_by`, `approved_at` theo các ràng buộc SQL.

Khi chưa kích hoạt rule riêng cho một hãng, hàm nhập kho sẽ áp dụng bộ kiểm tra kỹ thuật chung (fallback). Khi rule của hãng được kích hoạt (`is_active = true`), trigger `sasms.validate_device_identifiers` sẽ tự động đối chiếu `serial` theo `serial_pattern` và bắt buộc có `imei` nếu `imei_required = true`.

## 4. Bảng quy tắc theo hãng đã được BA chốt (D35)

| Hãng / phạm vi áp dụng | Tài liệu nguồn & Profile BA | Quy tắc Serial (Pattern) | IMEI bắt buộc / định dạng | Người & ngày xác nhận | Trạng thái kỹ thuật |
|---|---|---|---|---|---|
| **Sony TV** (SKU tivi demo) | Profile `SONY_TV_7`; Nguồn `SRC-01` (Sony Support) | Đúng 7 chữ số: `^[0-9]{7}$` | Không bắt buộc (`imei_required = false`) | Huy (BA) chốt 07/11/2026; Kiên (DBA) xác nhận | **ĐÃ DUYỆT — SẴN SÀNG KÍCH HOẠT** |
| **Samsung** (Gia dụng & TV) | Profile `SAMSUNG_GENERIC`; Nguồn `SRC-02` (Samsung) | Ký tự chữ/số: `^[A-Z0-9]{11,15}$` (theo SER-01..05) | Tùy chọn; nếu có đúng 15 chữ số | Huy (BA) chốt 07/11/2026; Kiên (DBA) xác nhận | **ĐÃ DUYỆT — SẴN SÀNG KÍCH HOẠT** |
| **LG** (Gia dụng & TV) | Profile `LG_GENERIC`; Nguồn `SRC-03` (LG Support) | Ký tự chữ/số: `^[0-9]{3}[A-Z]{4}[0-9]{5}$` | Tùy chọn; nếu có đúng 15 chữ số | Huy (BA) chốt 07/11/2026; Kiên (DBA) xác nhận | **ĐÃ DUYỆT — SẴN SÀNG KÍCH HOẠT** |
| **Apple** (iPhone / iPad) | Profile `APPLE_SERIAL_IMEI`; Nguồn `SRC-04, SRC-05` | Ký tự chữ/số: `^[A-Z0-9]{10,12}$` | **Bắt buộc** (`imei_required = true`); 15 chữ số + Luhn | Huy (BA) chốt 07/11/2026; Kiên (DBA) xác nhận | **ĐÃ DUYỆT — SẴN SÀNG KÍCH HOẠT** |

### 4.1 Lệnh SQL Provisioning kích hoạt Rule cho Demo / Staging

Khi triển khai môi trường hoặc chuẩn bị nghiệm thu M2, DBA thực thi nạp rule có phê duyệt bằng tài khoản quản trị:

```sql
-- Ví dụ nạp và kích hoạt quy tắc Sony TV 7 chữ số (gắn brand_id tương ứng):
INSERT INTO sasms.identifier_rules (
    brand_id, version, serial_pattern, imei_required,
    source_reference, approved_by, approved_at, is_active
) VALUES (
    (SELECT id FROM sasms.brands WHERE name ILIKE '%Sony%' OR id = '00000000-0000-4000-8000-000000000020' LIMIT 1),
    1, '^[0-9]{7}$', false,
    'D35_Quy_tac_Serial_IMEI_SASMS.docx; SRC-01 Sony Support 00248421',
    (SELECT id FROM sasms.users WHERE email = 'admin@sasms.local' OR id = '00000000-0000-4000-8000-000000000001' LIMIT 1),
    now(), true
);
```

## 5. Hợp đồng với Dev / QA

Thái dùng hàm nhập DB `sasms.receive_device_batch` thay vì chỉ kiểm regex tại UI. Client có thể kiểm sớm để hỗ trợ nhập liệu nhưng DB kiểm tra lại trước COMMIT. Mọi request lấy actor từ phiên được xác thực và đúng quyền kho/cửa hàng. API cần trả lỗi theo trường và vị trí phần tử mà không lộ SQL, dữ liệu ngoài phạm vi hoặc secret.

QA kiểm thử tối thiểu: canonical hóa, Serial/IMEI trùng giữa SKU, trùng trong cùng lô, hai phiên nhập cùng định danh, giá trị thiếu/sai kiểu, rule chưa duyệt không được áp dụng, rule đã duyệt từ chối sai định dạng và toàn lô rollback khi một phần tử lỗi. Đây là đóng góp DB cho D71/D75.

Bộ **76 assertions Sprint 1** đã kiểm chứng các trường hợp này trên PostgreSQL 16.15 thật, bao gồm cả ca hai phiên cùng Serial/IMEI trên hai PO độc lập.

| Xác nhận | Người cần review | Trạng thái |
|---|---|---|
| Nội dung và nguồn quy tắc hãng | Huy (BA), Kiên (DBA) | **ĐÃ XÁC NHẬN** (Khớp hồ sơ D35 ngày 11/11/2026) |
| Cách cấu hình và kiểm tra DB | Kiên (DBA), Thái (Dev Lead) | **ĐÃ XÁC NHẬN** (Hàm batch V002 + trigger validation) |
| Mức đủ để nghiệm thu M2 | Huy (BA), PM An | **SẴN SÀNG** cho Quality Gate M2 |
