const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export const MAX_CART_QUANTITY = 99

function cartError(code) {
  return new Error(code)
}

function isUuid(value) {
  return typeof value === 'string' && UUID_PATTERN.test(value)
}

export function normalizeCatalogItem(product, availableQuantity) {
  const price = Number(product?.price)
  if (!isUuid(product?.product_id) || typeof product?.sku !== 'string' || !product.sku.trim() ||
      typeof product?.name !== 'string' || !product.name.trim() || product.active !== true ||
      !Number.isSafeInteger(price) || price < 0 ||
      !Number.isSafeInteger(availableQuantity) || availableQuantity < 0) {
    throw cartError('INVALID_PRODUCT')
  }
  return {
    productId: product.product_id,
    sku: product.sku,
    name: product.name,
    unitPrice: price,
    availableQuantity,
    quantity: 1,
  }
}

export function normalizeSuggestedItem(suggestion) {
  return normalizeCatalogItem({
    product_id: suggestion?.productId,
    sku: suggestion?.sku,
    name: suggestion?.name,
    price: suggestion?.listPrice,
    active: true,
  }, suggestion?.availableQuantity)
}

export function addCartItem(items, item) {
  if (!Array.isArray(items)) throw cartError('INVALID_CART')
  const normalized = item?.unitPrice === undefined ? normalizeSuggestedItem(item) : normalizeCatalogItem({
    product_id: item.productId,
    sku: item.sku,
    name: item.name,
    price: item.unitPrice,
    active: true,
  }, item.availableQuantity)
  if (normalized.availableQuantity < 1) throw cartError('OUT_OF_STOCK')

  const index = items.findIndex((entry) => entry.productId === normalized.productId)
  if (index < 0) return [...items, normalized]

  const existing = items[index]
  const nextQuantity = existing.quantity + 1
  const maximum = Math.min(normalized.availableQuantity, MAX_CART_QUANTITY)
  if (nextQuantity > maximum) throw cartError('QUANTITY_EXCEEDS_STOCK')
  return items.map((entry, currentIndex) => currentIndex === index
    ? { ...entry, ...normalized, quantity: nextQuantity }
    : entry)
}

export function updateCartItemQuantity(items, productId, quantity) {
  if (!Array.isArray(items) || !isUuid(productId) || !Number.isInteger(quantity) || quantity < 1) {
    throw cartError('INVALID_QUANTITY')
  }
  const item = items.find((entry) => entry.productId === productId)
  if (!item) throw cartError('CART_ITEM_NOT_FOUND')
  if (quantity > Math.min(item.availableQuantity, MAX_CART_QUANTITY)) {
    throw cartError('QUANTITY_EXCEEDS_STOCK')
  }
  return items.map((entry) => entry.productId === productId ? { ...entry, quantity } : entry)
}

export function removeCartItem(items, productId) {
  if (!Array.isArray(items) || !isUuid(productId)) throw cartError('INVALID_PRODUCT')
  return items.filter((entry) => entry.productId !== productId)
}

export function calculateCartTotal(items) {
  if (!Array.isArray(items)) throw cartError('INVALID_CART')
  return items.reduce((total, item) => {
    if (!Number.isSafeInteger(item?.unitPrice) || item.unitPrice < 0 ||
        !Number.isInteger(item?.quantity) || item.quantity < 1) {
      throw cartError('INVALID_CART_ITEM')
    }
    const lineTotal = item.unitPrice * item.quantity
    const nextTotal = total + lineTotal
    if (!Number.isSafeInteger(lineTotal) || !Number.isSafeInteger(nextTotal)) throw cartError('TOTAL_OVERFLOW')
    return nextTotal
  }, 0)
}

export function toCrossSellCartItems(items) {
  if (!Array.isArray(items)) return []
  return items.map(({ productId, sku }) => ({ productId, sku }))
}
