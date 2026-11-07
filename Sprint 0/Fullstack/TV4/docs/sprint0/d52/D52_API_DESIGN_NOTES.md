# D52 – OpenAPI Inventory/POS/AI – ghi chú bàn giao (DRAFT)

**Người phụ trách:** TV4. **Giai đoạn:** Sprint 0. **Cơ sở:** D34 FRS V2 (FR03–08, BR01–04, BR07, BR09, BR11, NFR05–06), kiểm kê D35/D40/D50/D51/D54 theo báo cáo TV4. **Trạng thái:** Đặc tả đề xuất; **chưa BA/Architect phê duyệt, chưa triển khai API, chưa chạy E2E**.

## Tài liệu chính

- [D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml](D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml) (OpenAPI 3.0.3; các operation Inventory, QR, POS, AI; đường dẫn từ thư mục gốc repo An: `Sprint 0/Fullstack/TV4/docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml`).
- Tất cả path `/api/v1/*` là **đề xuất để duyệt**, không phải endpoint đang hoạt động. Ba endpoint `GET /api/health`, `GET /api/pos/status`, `GET /api/serial-imei/status` thuộc **skeleton** (hiện nằm tại repo nguồn tham chiếu, chưa sao chép `backend/` sang repo An), không chứng minh nghiệp vụ.
- Định danh trong URL đang dùng `string` cho tính trung lập. Trước khi code phải gắn với UUID, FK và tên thực tế của 37 bảng PostgreSQL theo quyết định Architect.

## Ràng buộc bất biến

| Luồng | Điều kiện thiết kế | Test dự kiến |
|---|---|---|
| Nhập kho | Xác nhận phiếu và tăng tồn nguyên tử; Serial trùng phải rollback; idempotency key không tạo bản ghi thứ hai | valid receiving, duplicate Serial, changed payload reuse |
| QR | Chỉ tra cứu; không tự mở URL/ghi tồn; quyền scope kho/siêu thị tại API | valid, invalid, not found, forbidden, no stock event |
| POS | Reserve nguyên tử, chống giữ hai quầy; không chọn hàng SOLD/kiểm tra trả | two concurrent carts one device |
| Thanh toán | PENDING/UNKNOWN không đồng nghĩa PAID; callback đáng tin cậy, chống lặp | pending, duplicate callback, recheck, fail |
| In POS | Chỉ PAID mới có receipt; lỗi in không đổi PAID/SOLD; in lại giữ đơn | failed printer + idempotent reprint |
| AI | Không gửi dữ liệu khách không cần thiết; AI timeout fallback; giá từ hệ thống | AI down, SKU invalid, fallback, price source |
| RBAC | Kiểm tra role + scope tại backend, kể cả khi UI ẩn nút | cross-store and out-of-role calls |

## Các hợp đồng còn PENDING (không tự chốt)

1. Mapping tên role giữa D34 và D54.
2. `AVAILABLE/RESERVED` (D34) và `IN_STOCK` (D51).
3. Serial case-sensitive giữ khoảng trắng nội bộ (D35) so với uppercase/strip (D51).
4. Hỗ trợ IMEI2 trong DB.
5. Thời hạn giữ chỗ POS 10 phút, online 30 phút, PENDING không tự giải phóng (D34) so với đề xuất D51.
6. Nhập kho toàn phiếu hay nhận từng phần.
7. Format QR/token.
8. Provider thanh toán và xác minh callback/đối soát.
9. Chính sách thuế, giảm giá, làm tròn, mẫu biên nhận.
10. Lifecycle GOOD/DEFECTIVE và trạng thái kho.

### Ma trận quyết định API chờ review

| Mã | Quyết định | Ảnh hưởng hợp đồng | Người cần quyết định | Trạng thái |
| --- | --- | --- | --- | --- |
| API-P01 | Mapping role D34/D54 và permission/scope | Global bearer security, 401/403, quyền từng operation | BA + Architect | PENDING |
| API-P02 | `AVAILABLE` hay `IN_STOCK` | Stock filter, device status, reservation/order response | BA + Architect/DBA | PENDING |
| API-P03 | Chuẩn hóa Serial | `DeviceIdentifier.serial`, validation/error code, unique lookup | BA + Architect/DBA | PENDING |
| API-P04 | IMEI2 | Receive request, device lookup/receipt và mapping 37 bảng | BA + Architect/DBA | PENDING |
| API-P05 | TTL reservation/PAYMENT_PENDING | `expires_at`, reservation enum, release/reconcile behavior | BA/PO + Architect | PENDING |
| API-P06 | Nhận nguyên phiếu hay từng phần | Status code/result schema của receiving và idempotency | BA/PO + Architect | PENDING |
| API-P07 | QR payload/token/version | `QrLookupRequest.payload`, parse/validation và manual fallback | BA + Architect/Security | PENDING |
| API-P08 | Payment provider/callback/reconcile | Payment method/source, callback security, UNKNOWN/PENDING | PO + Architect + QA | PENDING |
| API-P09 | Thuế/giảm giá/làm tròn/receipt | Order totals, money representation và print data | BA/PO + Architect | PENDING |
| API-P10 | GOOD/DEFECTIVE/lifecycle | Receiving, stock availability và POS filtering | BA/PO + Architect/DBA | PENDING |
| API-P11 | FR-09 AI summary thuộc D52 hay tài liệu khác | Độ phủ tag AI và operation mới nếu được duyệt | BA/PO | PENDING |
| API-P12 | Owner của payment integration | Ranh giới giữa D52 và D56/tài liệu riêng | Architect + PM | PENDING |

Không bổ sung path/schema cho API-P01 đến API-P12 trước khi có quyết định và change record phù hợp.

## API review trước khi bàn giao

- [ ] BA duyệt đường dẫn, tên field, trạng thái và điều kiện lỗi.
- [ ] Architect đối chiếu bảng/kiểu/các hàm giao dịch PostgreSQL đã có, xác nhận role runtime/ACL.
- [ ] QA lập test case theo từng response, 401/403/409/422/503; xác nhận NFR.
- [ ] PO xác nhận luồng thanh toán và dữ liệu biên nhận.
- [x] Redocly CLI validation ngày 07/11/2026: **PASS, 0 error, 2 warning**. Hai warning còn lại là `info.license` và local development server; license và server ngoài local đang **PENDING PM/Architect**, không tự điền. YAML parse thành công vẫn không thay thế OpenAPI semantic validation.
- [ ] Sau phê duyệt mới triển khai API và E2E; tạo change request nếu thay đổi baseline.

## Kết quả khắc phục kỹ thuật ngày 07/11/2026

- Ban đầu Redocly báo 3 error do description có dấu phẩy trong YAML flow mapping nhưng chưa được quote, cùng 6 warning.
- Đã quote ba description tại response `202` của `initiatePayment`, response dùng chung `Conflict` và trường `PaymentResult.source`.
- Đã bổ sung description cho bốn tag và response `401` cho sáu operation kế thừa global `bearerAuth` nhưng trước đó chưa mô tả lỗi xác thực.
- Kiểm tra lại: 12 operation đều có `operationId` duy nhất; 97 `$ref` local resolve; tất cả operation có response `401`; Redocly xác nhận API description hợp lệ.
- Lệnh kiểm tra lại trực tiếp tại thư mục gốc repository đích (`D:\software-project-management-an`):
  ```powershell
  python -c "import yaml; yaml.safe_load(open('Sprint 0/Fullstack/TV4/docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml', encoding='utf-8'))"
  npx.cmd --offline -y @redocly/cli lint "Sprint 0/Fullstack/TV4/docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml"
  ```
- Không thay đổi path, enum, business state, field dữ liệu hoặc các quyết định đang PENDING.

### Hai cảnh báo Redocly còn lại

| Cảnh báo | Lý do giữ lại | Người quyết định | Trạng thái |
| --- | --- | --- | --- |
| `info.license` chưa có | Tài liệu dự án chưa chỉ định license; tự chọn license là quyết định pháp lý/quản trị | PM/Sponsor | PENDING |
| Server dùng `http://localhost:8080` | Đây là server local đúng phạm vi DRAFT; URL Dev/Staging/Prod chưa có baseline được duyệt | Architect/PM | PENDING |

Hai warning không làm Redocly fail và không làm mất các kết quả PASS. Khi có quyết định, cập nhật OpenAPI rồi chạy lại validator; không dùng ignore file để che lỗi mới.
