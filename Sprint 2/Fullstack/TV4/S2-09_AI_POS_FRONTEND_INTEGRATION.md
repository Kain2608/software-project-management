# SASMS Sprint 2 — S2-09 AI-to-POS frontend integration (Vũ Duy Thái)

**Ngày rà soát cuối:** 15/11/2026
**PR:** #21 — `feat/tv4-s2-ai-pos-verified` → `main`
**Trạng thái:** Frontend unit/build PASS; live AI/JWT/POS E2E NOT TESTED.

## 1. Phạm vi frontend

`CrossSellWidget` được gắn vào `PosPage` ở chế độ chỉ đọc. Widget chỉ gọi API khi nhận đủ JWT khác rỗng, `storeId`/`warehouseId` UUID hợp lệ và `cartItems` có `productId` + `sku` hợp lệ. Request gửi cả `items` theo D52 v2 trên `Vu_AI` và `sku_ids` để tương thích response/request cũ. Debounce 400 ms và `AbortController` ngăn request thừa khi giỏ thay đổi hoặc component unmount.

Kết quả rỗng được ẩn theo D52. Nút “Xem để thêm” chỉ chuyển candidate đã kiểm tra sang callback; widget không tự ghi giỏ, giữ hàng, sửa tồn kho hay thanh toán.

## 2. Đối chiếu D52 và code backend

- D52 v2 trên `Vu_AI` yêu cầu `POST /api/v1/ai/cross-sell`, Bearer JWT, quyền `ai.use`, store scope, giá từ `products.list_price` và tồn từ kho thuộc store.
- `main` hiện vẫn trả response legacy chỉ có `sku_id` và `reason`. Frontend loại response này theo fail-closed; vì vậy branch PR không thể chạy live đầy đủ chỉ với backend `main` hiện tại.
- Code trên `Vu_AI` có shape DTO phù hợp D52, nhưng implementation hiện vẫn cho phép `/api/v1/ai/**` public và adapters/fallback tự tạo UUID, giá, số tồn trong Java. Chưa có bằng chứng code gọi Catalog/Stock hoặc `sasms.has_permission(..., 'ai.use', store_id)` thật.
- Vì vậy frontend không tin `name`, `list_price`, `available_quantity` hoặc `is_serialized` từ AI. Mỗi candidate được đọc lại qua Catalog và Stock API, đối chiếu UUID/SKU/warehouse, `active=true` và `sellable_count > 0`. Candidate không khớp bị loại.
- Vai trò CASHIER trong baseline có `ai.use`, `catalog.read` và `inventory.read`, nhưng kiểm tra quyền/store thực tế vẫn thuộc backend.

## 3. Trạng thái App/POS

`frontend/src/app/App.jsx` hiện gọi `<PosPage />` không truyền login, JWT, store, warehouse hay cart. Do đó widget mặc định ở trạng thái chờ và không gọi API. Không hardcode token, store hoặc sản phẩm demo để giả E2E PASS.

Khi parent thật cung cấp context hợp lệ, unit test xác nhận service:

1. Gửi JWT trong Authorization header.
2. Gửi đúng `store_id`, `warehouse_id`, `items` và `sku_ids`.
3. Đọc lại Catalog theo `product_id`.
4. Đọc Stock theo warehouse + SKU.
5. Chỉ trả suggestion sau khi dữ liệu authoritative khớp.

Đây là **UNIT/CONTRACT PASS**, không phải browser/live E2E PASS.

## 4. Kết quả kiểm thử ngày 15/11/2026

| Kiểm tra | Kết quả | Bằng chứng / giới hạn |
|---|---|---|
| `npm ci` | PASS | 92 packages; audit báo 2 finding (1 moderate, 1 high); không chạy force fix hoặc nâng major. |
| `npm test` sau hardening | PASS | 22/22 tests, 0 fail, 0 skipped; trong đó AI POS có 9 tests. |
| `npm run build` sau hardening | PASS | Vite 5.4.21; 326 modules; cảnh báo bundle JS 626.04 kB > 500 kB. |
| AI POS unit/contract | PASS | Readiness context, JWT header, request D52, Catalog/Stock reconciliation và response legacy fail-closed. |
| Browser với login/cart thật | NOT TESTED | App chưa có luồng login/cart thật. |
| Live JWT + `Vu_AI` + PostgreSQL | NOT TESTED | Backend AI chưa được merge vào main và chưa chứng minh Catalog/Stock/RBAC thật. |
| Thêm hàng/Serial/payment | NOT TESTED | Chưa có checkout callback/contract end-to-end. |

## 5. Blocker và trách nhiệm phối hợp

- **AI backend (Vũ):** bảo vệ endpoint bằng JWT; kiểm `ai.use` + store scope; xác minh warehouse thuộc store; trả product UUID/price/stock từ DB thật thay vì dữ liệu adapter/fallback tự tạo.
- **Fullstack/PM:** tích hợp login, trusted cashier context và cart thật vào `App`/`PosPage`.
- **BA/Architect:** chốt nguồn canonical cho `is_serialized` và action add/reserve/checkout.
- **QA/PO:** kiểm thử browser, live API, cross-store, empty/fallback, cart action và acceptance.

PR #21 phù hợp để tiếp tục ở trạng thái **Draft**. Không được ghi S2-09 E2E PASS hoặc Sprint 2 accepted từ các unit/build results này.
