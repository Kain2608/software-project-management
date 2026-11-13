# D52 — Hợp đồng Tích hợp API Gợi ý Bán kèm AI vào Web POS (Sprint 2)

| Thuộc tính | Giá trị |
| --- | --- |
| Mã tài liệu | **D52 (AI-POS Contract)** |
| Tên văn bản | Hợp đồng giao tiếp API: Tích hợp Phân hệ AI Gợi ý Bán kèm vào Màn hình POS (Sprint 2 Baseline) |
| Nhiệm vụ liên quan | **S2-08** (Nguyễn Tiến Vũ — AI Lead) phối hợp **S2-02** (Vũ Duy Thái — Fullstack Lead) |
| Căn cứ nền tảng | [D55 Thiết kế AI](../../Sprint%200/AI/D55-thiet-ke-ai-prompt-rag-cache-fallback.md)<br>[DS1 Khung dịch vụ AI](../../Sprint%201/AI/DS1-phan-mem-tang-truong-ai-sprint1.md)<br>[D34 FRS V2](../../Sprint%200/BA/D34_FRS_V2_SASMS.docx) (FR-08, BR-09, NFR-03)<br>[S0-19/22/23 Hợp đồng DB](../../Sprint%200/DB/bo-sung/S0-19-22-23-hop-dong-API-AI-tich-hop.md) |
| Phiên bản đặc tả | **2.0.0** (Đã giải quyết toàn bộ 4 yêu cầu từ phía Backend) |
| Tệp OpenAPI chuẩn | [`Sprint 2/AI/D52_AI_POS_INTEGRATION_CONTRACT.yaml`](D52_AI_POS_INTEGRATION_CONTRACT.yaml) |
| Trạng thái | **ĐÃ CHỐT HỢP ĐỒNG (Bàn giao cho Backend tích hợp POS)** |

---

## 1. Bối cảnh và Mục đích Hợp đồng

Trong kế hoạch Sprint 2, nhiệm vụ **S2-02** (Vũ Duy Thái — Fullstack Lead) chịu trách nhiệm xây dựng **Màn hình bán hàng POS tại quầy** và nhiệm vụ **S2-08** (Nguyễn Tiến Vũ — AI Lead) chịu trách nhiệm vận hành **AI Recommendation Engine**.

Văn bản hợp đồng này chính thức hóa giao thức kết nối giữa giao diện Web POS và Phân hệ AI Gateway, giải quyết triệt để 4 vấn đề kỹ thuật mà Backend đã nêu:
1. **Validation & Response lỗi rõ ràng:** Định nghĩa chi tiết các mã lỗi `400`, `401`, `403`, `422`, `503` và kịch bản trả về `200` với danh sách rỗng khi hết hàng tồn tại kho.
2. **Xác minh Giá và Số lượng tồn từ CSDL:** Kết quả gợi ý luôn được backend truy vấn giá niêm yết từ `sasms.products.list_price` và số lượng tồn khả dụng từ `sasms.stock_availability.available` của đúng siêu thị/kho của phiên thu ngân.
3. **Phân quyền chặt chẽ theo JWT:** Ràng buộc quyền `ai.use` và kiểm tra phạm vi `store_id` của thu ngân qua token Bearer JWT.
4. **Quy tắc phân định `is_serialized`:** Xác định phụ kiện bán theo số lượng (`is_serialized = false`) và thiết bị máy chính bắt buộc quét Serial (`is_serialized = true`) mà không cần can thiệp cấu trúc CSDL hiện hữu.

---

## 2. Quy tắc Xử lý Nghiệp vụ & Dữ liệu Cốt lõi

### 2.1. Quy tắc Xác định `is_serialized` (Không cần sửa CSDL)
CSDL hiện tại quản lý thiết bị theo từng chiếc có Serial (`devices`). Để phân định mặt hàng bán kèm nào cần quét Serial và mặt hàng nào bán theo số lượng, Backend áp dụng quy tắc suy luận theo ngành hàng (`category`):
- **Nhóm Máy chính (Thiết bị có Serial):** Các SKU thuộc nhóm ngành hàng `SMARTPHONE`, `LAPTOP`, `TELEVISION`, `WASHING_MACHINE`, `REFRIGERATOR`, `AIR_CONDITIONER` $\rightarrow$ gán `is_serialized = true`. Khi thu ngân bấm chọn, POS hiển thị popup yêu cầu quét mã barcode Serial/IMEI.
- **Nhóm Phụ kiện & Dịch vụ:** Các SKU thuộc nhóm `ACCESSORY`, `CASE`, `CABLE`, `CHARGER`, `WARRANTY_SERVICE`, `SUPPORT` $\rightarrow$ gán `is_serialized = false`. Thu ngân bấm nút `[+ Thêm]` là tăng số lượng trực tiếp trong giỏ hàng mà không đòi Serial.

### 2.2. Kiểm tra Phạm vi Cửa hàng & Quyền Hạn qua JWT
- Thu ngân đăng nhập gửi Bearer JWT trong Header `Authorization`.
- Backend trích xuất danh tính nhân viên, gọi hàm SQL `sasms.has_permission(actor_uuid, 'ai.use', request.store_id)`.
- Nếu nhân viên không được gán làm việc tại siêu thị truyền vào trong body (`request.store_id`), backend lập tức từ chối với mã **`403 Forbidden`**.

### 2.3. Quy tắc Lấy Giá và Tồn Kho (Authoritative Pricing & Stock)
- AI chỉ đóng vai trò tư vấn phụ kiện phù hợp (`product_id`, `sku`, `reason`).
- Backend lọc các phụ kiện có `stock_availability.available > 0` tại `warehouse_id` thuộc `store_id`.
- Giá bán gán vào DTO là giá niêm yết chính thức `products.list_price`. Tuyệt đối không nhận giá do AI hoặc trình duyệt gửi lên (tuân thủ **BR-09** và **AC-08-3**).
- Nếu toàn bộ phụ kiện gợi ý đều hết hàng tại kho đó, API trả về mã **`200 OK`** với mảng `suggestions: []` (không báo lỗi 500 làm sập giao diện POS).

---

## 3. Đặc tả Kỹ thuật Endpoint `POST /api/v1/ai/cross-sell`

### 3.1. Request Schema
- **URL:** `POST /api/v1/ai/cross-sell`
- **Headers:**
  - `Authorization: Bearer <JWT_TOKEN>`
  - `Content-Type: application/json`
- **Payload cấu trúc:**
```json
{
  "store_id": "00000000-0000-0000-0000-000000000101",
  "warehouse_id": "00000000-0000-0000-0000-000000000201",
  "sku_ids": [
    "PHONE-IP16-128"
  ],
  "limit": 4
}
```

### 3.2. Response Thành công (HTTP 200 OK)
```json
{
  "source": "AI",
  "took_ms": 684,
  "suggestions": [
    {
      "product_id": "e1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
      "sku": "ACC-CASE-IP16",
      "name": "Ốp lưng chống sốc iPhone 16 chính hãng",
      "list_price": 450000.00,
      "available_quantity": 15,
      "is_serialized": false,
      "reason": "Ốp lưng chống sốc chuẩn thiết kế bảo vệ tối ưu cho iPhone 16"
    },
    {
      "product_id": "f2b3c4d5-e6f7-8a9b-0c1d-2e3f4a5b6c7d",
      "sku": "ACC-CHG-20W",
      "name": "Củ sạc nhanh Type-C Power Delivery 20W",
      "list_price": 590000.00,
      "available_quantity": 28,
      "is_serialized": false,
      "reason": "Củ sạc nhanh Type-C công suất cao tương thích chuẩn sạc máy"
    },
    {
      "product_id": "a3c4d5e6-f7a8-9b0c-1d2e-3f4a5b6c7d8e",
      "sku": "SRV-WAR-EXT12",
      "name": "Gói bảo hành rơi vỡ mở rộng 12 tháng SASMS",
      "list_price": 1200000.00,
      "available_quantity": 999,
      "is_serialized": false,
      "reason": "Gói bảo hành toàn diện bảo vệ rủi ro rơi vỡ vào nước"
    }
  ]
}
```

### 3.3. Ma trận Phản hồi Mã lỗi

| Mã HTTP | Mã lỗi nghiệp vụ (`code`) | Mô tả nguyên nhân | Hướng dẫn xử lý cho giao diện POS |
| --- | --- | --- | --- |
| **`400`** | `INVALID_ARGUMENT` | Dữ liệu thiếu `sku_ids` hoặc UUID sai định dạng | POS kiểm tra lại dữ liệu giỏ hàng trước khi gửi request. |
| **`401`** | `UNAUTHORIZED` | Token JWT không hợp lệ hoặc phiên thu ngân hết hạn | POS tự động chuyển hướng về màn hình đăng nhập thu ngân. |
| **`403`** | `FORBIDDEN_SCOPE` | Thu ngân không có quyền bán hàng tại cửa hàng `store_id` này | POS thông báo: "Bạn không có quyền thao tác tại siêu thị này". |
| **`422`** | `SKU_NOT_FOUND` | SKU trong giỏ hàng không tồn tại trong CSDL | POS loại bỏ SKU không hợp lệ khỏi tham số gọi gợi ý. |
| **`200`** | *(suggestions: [])* | Các phụ kiện gợi ý đều hết hàng tại kho | POS tự động ẩn Widget gợi ý bán kèm. |
| **`200`** | *(source: RULE_FALLBACK)* | AI Timeout > 2.5s hoặc lỗi mạng LLM ngoài | POS hiển thị gợi ý bình thường từ Luật dự phòng, gắn nhãn `[Luật dự phòng]`. **Tiến trình thanh toán không bị gián đoạn (AC-08-2)**. |

---

## 4. Hướng dẫn Tích hợp Phía Frontend Web POS (Dành cho Thái)

1. **Thời điểm gọi API (Trigger Event):**
   - Khi thu ngân quét mã barcode/QR thêm một sản phẩm chính vào giỏ hàng.
   - Sử dụng cơ chế chống dội (**Debounce 400ms**) để tránh bắn nhiều request liên tiếp khi quét nhanh nhiều món.
2. **Hiển thị Widget Bán kèm (UI Widget Layout):**
   - Render component `CrossSellWidget` nằm ngay dưới bảng danh sách sản phẩm giỏ hàng.
   - Mỗi thẻ phụ kiện hiển thị: Tên phụ kiện, Giá niêm yết (format VND), Nhãn tồn kho (`Còn x chiếc`), Lý do tư vấn bán kèm và Nút bấm `[+ Thêm]`.
3. **Thao tác thêm vào giỏ hàng:**
   - Khi thu ngân click `[+ Thêm]`:
     - Nếu `is_serialized === false`: Gọi trực tiếp action thêm dòng đơn hàng với `quantity = 1`.
     - Nếu `is_serialized === true`: Mở popup quét mã barcode Serial của phụ kiện đó trước khi thêm vào giỏ.

---

### Ký duyệt Bàn giao Kỹ thuật

| Đại diện Phân hệ | Họ và tên | Chức danh | Trạng thái xác nhận |
| --- | --- | --- | :---: |
| **Đại diện Phân hệ AI** | **Nguyễn Tiến Vũ** | Data / AI Lead | **ĐÃ HOÀN TẤT & BÀN GIAO** |
| **Đại diện Phân hệ Fullstack** | **Vũ Duy Thái** | Fullstack / POS Lead | Tiếp nhận hợp đồng để tích hợp S2-02 |
| **Đại diện Nghiệp vụ** | **Nguyễn Quang Huy** | Product Owner / BA Lead | Đã xác nhận quy tắc bán kèm & giá |
