import { httpClient } from '../../shared/api/httpClient.js'
import { normalizeCatalogItem } from './posCartModel.js'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function authConfig(accessToken, extra = {}) {
  if (typeof accessToken !== 'string' || !accessToken.trim()) throw new Error('AUTHENTICATION_REQUIRED')
  return {
    ...extra,
    headers: { Authorization: `Bearer ${accessToken}` },
  }
}

export async function searchCatalogForPos({ query, accessToken, warehouseId, signal }, client = httpClient) {
  const normalizedQuery = typeof query === 'string' ? query.trim() : ''
  if (!normalizedQuery || normalizedQuery.length > 100) throw new Error('INVALID_QUERY')
  if (typeof warehouseId !== 'string' || !UUID_PATTERN.test(warehouseId)) throw new Error('INVALID_WAREHOUSE')

  const config = authConfig(accessToken, {
    signal,
    params: { query: normalizedQuery, active: true, limit: 20, offset: 0 },
  })
  const { data } = await client.get('/v1/products', config)
  if (!Array.isArray(data?.items)) throw new Error('CATALOG_CONTRACT_INVALID')

  return Promise.all(data.items.filter((product) => product?.active === true).map(async (product) => {
    const { data: stock } = await client.get('/v1/inventory/stock', authConfig(accessToken, {
      signal,
      params: { warehouse_id: warehouseId, sku: product.sku, limit: 100, offset: 0 },
    }))
    const row = stock?.items?.find((entry) =>
      typeof entry?.sku_id === 'string' && entry.sku_id.toLowerCase() === product.product_id.toLowerCase() &&
      typeof entry?.warehouse_id === 'string' && entry.warehouse_id.toLowerCase() === warehouseId.toLowerCase())
    const availableQuantity = Number.isSafeInteger(row?.sellable_count) ? row.sellable_count : 0
    return normalizeCatalogItem(product, availableQuantity)
  }))
}

export function classifyCartApiError(error) {
  const status = error?.response?.status
  if (error?.message === 'AUTHENTICATION_REQUIRED' || status === 401) {
    return { state: 'auth-required', message: 'Cần phiên đăng nhập hợp lệ để tra cứu danh mục và tồn kho.' }
  }
  if (status === 403) {
    return { state: 'forbidden', message: 'Tài khoản không có quyền xem danh mục hoặc kho đã chọn.' }
  }
  if (status === 404) return { state: 'empty', message: 'Không tìm thấy dữ liệu trong phạm vi được phép.' }
  if (error?.message === 'INVALID_QUERY') return { state: 'error', message: 'Nhập tên hoặc SKU cần tìm.' }
  if (error?.message === 'INVALID_WAREHOUSE') return { state: 'error', message: 'Kho bán hàng chưa hợp lệ.' }
  return { state: 'error', message: 'Không thể tải danh mục hoặc tồn kho. Vui lòng thử lại.' }
}
