const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const SOURCES = new Set(['AI', 'CACHE', 'RULE_FALLBACK'])

export function isUuid(value) {
  return typeof value === 'string' && UUID_PATTERN.test(value)
}

export function isCrossSellContextReady({ accessToken, storeId, warehouseId, cartItems }) {
  return typeof accessToken === 'string' && accessToken.trim().length > 0 &&
    isUuid(storeId) && isUuid(warehouseId) &&
    Array.isArray(cartItems) && cartItems.length > 0 && cartItems.length <= 50 &&
    cartItems.every((item) => {
      const productId = item?.productId ?? item?.product_id
      return isUuid(productId) && typeof item?.sku === 'string' && item.sku.trim().length > 0
    })
}

export function buildCrossSellPayload({ storeId, warehouseId, cartItems, limit = 4 }) {
  if (!isUuid(storeId) || !isUuid(warehouseId)) {
    throw new Error('INVALID_STORE_OR_WAREHOUSE')
  }
  if (!Array.isArray(cartItems) || cartItems.length === 0 || cartItems.length > 50) {
    throw new Error('INVALID_CART_ITEMS')
  }
  if (!Number.isInteger(limit) || limit < 1 || limit > 10) {
    throw new Error('INVALID_LIMIT')
  }
  const items = cartItems.map((item) => ({
    product_id: item?.productId ?? item?.product_id,
    sku: item?.sku,
  }))
  if (items.some(({ product_id, sku }) => !isUuid(product_id) || typeof sku !== 'string' || !sku.trim())) {
    throw new Error('INVALID_CART_PRODUCT')
  }
  // Send the same SKUs in both forms: Vu_AI's new contract and the older D52 request.
  return {
    store_id: storeId,
    warehouse_id: warehouseId,
    items,
    sku_ids: items.map(({ sku }) => sku),
    limit,
  }
}

export function validateCrossSellResponse(data) {
  if (!data || !SOURCES.has(data.source) || !Array.isArray(data.suggestions) ||
    !Number.isSafeInteger(data.took_ms) || data.took_ms < 0) {
    throw new Error('AI_CONTRACT_INVALID')
  }
  return data
}

// The AI response is a candidate list ONLY. These fields must be re-read from Catalog/Stock.
// Never use its price, quantity, name, UUID or is_serialized to create an order.
export function reconcileSuggestion(candidate, product, stock, warehouseId) {
  if (!candidate || !isUuid(candidate.product_id) || !isUuid(product?.product_id) ||
      candidate.product_id.toLowerCase() !== product.product_id.toLowerCase() ||
      typeof candidate.sku !== 'string' || candidate.sku !== product.sku ||
      product.active !== true || typeof product.name !== 'string' || !product.name.trim()) {
    return null
  }
  const price = Number(product.price)
  if (!Number.isSafeInteger(price) || price < 0) return null // VND integer semantics of current POS.
  const row = stock?.items?.find((entry) =>
    typeof entry?.sku_id === 'string' && entry.sku_id.toLowerCase() === product.product_id.toLowerCase() &&
    typeof entry?.warehouse_id === 'string' && entry.warehouse_id.toLowerCase() === warehouseId.toLowerCase())
  if (!row || !Number.isSafeInteger(row.sellable_count) || row.sellable_count <= 0) return null
  return {
    productId: product.product_id,
    sku: product.sku,
    name: product.name,
    listPrice: price,
    availableQuantity: row.sellable_count,
    reason: typeof candidate.reason === 'string' ? candidate.reason.slice(0, 200) : '',
    // Never infer serial policy from the AI-provided boolean or category name.
    requiresServerValidation: true,
  }
}

export function classifyCrossSellError(error) {
  if (error?.message === 'AUTHENTICATION_REQUIRED' || error?.response?.status === 401) {
    return 'Cần đăng nhập lại để xem gợi ý bán kèm.'
  }
  if (error?.response?.status === 403) return 'Tài khoản chưa có quyền sử dụng AI tại cửa hàng này.'
  if (error?.message?.startsWith('INVALID_')) return 'Chưa có thông tin giỏ hàng và cửa hàng hợp lệ.'
  return 'Chưa thể tải gợi ý bán kèm. POS vẫn có thể tiếp tục hoạt động.'
}
