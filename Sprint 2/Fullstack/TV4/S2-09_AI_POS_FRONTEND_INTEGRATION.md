# SASMS Sprint 2 — S2-09 AI-to-POS frontend integration (Vũ Duy Thái)

## Scope and proof

Read-only AI cross-sell widget wired into `PosPage`. The request carries both the new `items` representation from `Vu_AI` D52 v2 and matching legacy `sku_ids`; it requires a JWT and UUID store/warehouse context. A 400 ms debounce prevents repeated calls after rapid cart changes; requests are aborted on unmount or context change. Errors never block receipt viewing or checkout.

**Important:** Main `App.jsx` has no authenticated cashier or cart flow yet. Until the parent supplies `accessToken`, `storeId`, `warehouseId` and real `cartItems`, the widget is idle. Without an approved checkout contract, its optional `onReviewSuggestion` callback is **not** an order/stock/payment mutation. Button is disabled when no callback exists.

## Trust boundary

The `Vu_AI` branch currently returns model-generated UUIDs, prices and stock quantities, while `SecurityConfiguration` permits `/api/v1/ai/**` without authentication. Do **not** trust that data as sellable. The frontend deliberately re-reads each candidate using `GET /api/v1/products/{productId}` (Catalog) and `GET /api/v1/inventory/stock?warehouse_id=...&sku=...` (authorized stock), matches SKU/product UUID/warehouse, validates integer VND price and `sellable_count > 0`, and fails closed for unknown, inactive, inconsistent or out-of-stock items.

AI-provided `is_serialized` is not authoritative: products have no canonical policy field; the UI requires the order/reservation backend to revalidate Serial and price at commit. It does not automatically add a suggested item. The API currently does not prove that `warehouseId` belongs to the supplied `storeId` for a multi-store actor; the backend AI must enforce this. Catalog and Stock endpoints have independent permission checks.

## Verification

- `node --test frontend/src/features/pos/aiPosService.test.js`: 7 pure/service tests PASS on Node.js 22 (run locally during this implementation).
- Tests check JWT fail-closed, payload compatibility, authoritative price/stock substitution, fabricated ID rejection, inactive/out-of-stock/other-warehouse exclusion, and non-leaking error messages.
- **NOT TESTED:** Vite production build (dependencies unavailable in this execution environment), live JWT + real AI + PostgreSQL E2E, browser interactions, adding to a real cart, POS payment, physical devices.

## Blockers owned by other components

- AI backend owner: require JWT+`ai.use` store permission; verify `store_id`/`warehouse_id` relationship, real catalog UUIDs and stock; don't generate prices or IDs from SKU; add real PostgreSQL and Spring Security integration tests.
- BA/Architect: approve Serial policy per product and cart/order/reservation/payment contract.
- Fullstack: connect actual signed-in cashier/cart to `PosPage` and implement the server-validated add/reserve action after the contract is approved.

This incremental frontend change is suitable for **Draft PR review**, not acceptance of S2-09 end-to-end.
