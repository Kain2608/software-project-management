# D35 — Hợp đồng DB cho Serial/IMEI

Phần Trần Trung Kiên — Architect / DBA, rà soát tài liệu **20/11/2026** trên nguồn `main` tại `6544a00`; SQL/evidence hiện hành được kiểm ngày **17/11/2026**. Căn cứ chính là [D35 BA](../../Sprint%200/BA/D35_Quy_tac_Serial_IMEI_SASMS.docx), SER-01…10 và BSL BA 01, [D34 FRS](../../Sprint%200/BA/D34_FRS_V2_SASMS.docx) BR-01; đối chiếu [D71](../Tester/D71_Bo_Test_Case_Kho_QR_SASMS_v1.2.docx), [D80](../Tester/D80_So_loi_Bug_Log_SASMS.docx) và [bàn giao Fullstack](../Fullstack/TV4/SPRINT1_IMPLEMENTATION_REPORT.md).

Đây là hợp đồng kỹ thuật hiện hành V003…V005. Phương án uppercase/100 ký tự/một IMEI trong DEC-03/04 của [D53 Fullstack](../../Sprint%200/Fullstack/TV4/docs/sprint0/d53/D53_DECISION_REGISTER.md) mô tả lựa chọn trước V003 và chưa được sổ quyết định đánh dấu thay thế; không áp phương án đó vào dữ liệu mới. Việc đồng bộ quyết định và ký duyệt nghiệp vụ cần người sở hữu xác nhận, không được suy từ nhãn APPROVED cũ.

## Quy tắc hiện hành

| Dữ liệu | Hợp đồng DB theo BA |
|---|---|
| Serial | Bắt buộc, giữ raw; trim hai đầu tạo normalized, **phân biệt hoa/thường**, giữ số 0, dấu và khoảng trắng nội bộ; 1–128 ký tự, cấm control characters |
| IMEI | Chuỗi 15 chữ số và **Luhn tại DB**; không dùng IMEISV/EID thay IMEI |
| Nhiều IMEI | Một device có IMEI slot 1/2; UNIQUE(kind,normalized_value) chặn trùng giữa thiết bị và chéo slot, kể cả thiết bị đã bán |
| Profile | Theo product_id/SKU cụ thể; GENERIC, SONY_TV_7, APPLE_SERIAL_IMEI |
| Sony TV | Chỉ SKU được cấu hình/duyệt mới bắt 7 chữ số; SKU khác cùng hãng vẫn generic |
| Samsung/LG/Apple Serial | Theo kiểm tra chung/tem; không suy regex hoặc độ dài toàn hãng |
| QR | SASMS:DEVICE:<qr_token>, không chứa khách hàng hoặc secret |
| Sửa mã | Không sửa đè/xóa lịch sử sau giao dịch; cần quản lý, lý do, audit và thay đổi có kiểm soát. Chưa có API chỉnh mã quản lý |

[V003](../../database/migrations/V003__scoped_device_identifiers.sql) thêm device_identifiers và product_identifier_profiles, đưa DB lên **41 bảng**; V004 xử lý lease, V005 đồng bộ vai trò BA. S0 37 bảng/V002 39 bảng giữ nguyên lịch sử/checksum; [artifact hiện hành](../../database/sprint2) được sinh riêng.

Cột devices.serial/imei giữ tương thích consumer cũ; registry lưu cả hai IMEI. serial_raw/imei_raw và registry.raw_value giữ đầu vào mới. Không thể khôi phục raw trước đây đã upper-case: migration giữ nguyên mã cũ, raw NULL và legacy_unverified, không tự chứng nhận Luhn lịch sử. Cần đối soát tem/chứng từ trong quy trình quản lý.

## Nhập lô và retry

Giữ chữ ký receive_device_batch(receipt_item_id,request_key,devices_json,trusted_actor_id) RETURNS uuid[]. JSON SQL nhận serial, imei và imei2 tùy chọn. Ví dụ dữ liệu thử:

```json
[{"serial":"  001abc XYZ-1  ","imei":"490154203237518","imei2":"356938035643809"}]
```

Kiểm 1–500 phần tử, inventory.receive theo store, PO/receipt/quantity, profile/checksum/unique. Một lỗi rollback cả lô và movement/audit/outbox. Cùng key, actor, receipt và normalized payload đúng thứ tự trả cùng ID; đổi hoa/thường là đổi payload. Ledger V002 cũ retry theo đúng canonical lịch sử, không tạo hiệu ứng lặp.

`imei2` là mở rộng **SQL DB**; [InventoryBatchService](../../backend/src/main/java/com/smartelectronicsupermarket/inventory/service/InventoryBatchService.java) và DTO HTTP hiện chỉ cho `serial`/`imei`, nên chưa nhập được IMEI phụ qua UI/API. Fullstack cần bổ sung contract/validation/serialization và kiểm atomic retry với hai IMEI; không bỏ kiểm Luhn/unique ở DB để phù hợp DTO cũ. Backend vẫn xác thực JWT, scope và ánh xạ lỗi, không gửi actor do client tự chọn vào SQL.

Tra cứu theo registry và khóa chọn thiết bị là hai giao thức khác nhau: `find_and_lock_device_for_pos(..., serial, actor)` hiện chỉ tìm **Serial**. Consumer cần đổi nhãn nhập thành Serial hoặc triển khai bước resolve IMEI → device/Serial theo đúng store/kho/product trước khi gọi khóa; không gửi IMEI vào tham số Serial rồi coi hợp đồng đã hỗ trợ cả hai.

## Cấu hình profile

[Provisioning](../../scripts/provision_d35_rules.sql) yêu cầu đủ product_id, profile_code, requires_imei, approved_by, approved_at, source_reference từ hồ sơ phê duyệt thật. Thiếu tham số dừng lỗi; approver phải active và có catalog.write toàn chuỗi. Không đổi hãng DEMO, không tự đặt user demo/ngày hiện tại hoặc áp regex toàn hãng. Chạy lại đúng hồ sơ là no-op; cấu hình khác cần thay đổi version đã review. identifier_rules V002 giữ làm lịch sử, không được suy thành profile mới.

[SQL regression](../../database/tests/sprint2/identifiers_and_leases.sql) và [validator](../../scripts/validate_sprint2.py) kiểm raw/case/biên/Luhn/multi-slot/rollback/retry/profile/lịch sử và lease. Đọc [summary theo hash nguồn](../../database/evidence/sprint2/summary.json). Kết quả DB không tự đóng bug D80 hoặc thay chữ ký BA/PM/QA.
