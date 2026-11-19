import { httpClient } from '../../shared/api/httpClient.js'

export async function getPosStatus() {
  const response = await httpClient.get('/pos/status')
  return response.data
}

export async function getReceipt(orderId, accessToken, signal, client = httpClient) {
  if (typeof accessToken !== 'string' || accessToken.trim().length === 0) {
    throw new Error('AUTHENTICATION_REQUIRED')
  }
  const response = await client.get(`/v1/pos/orders/${encodeURIComponent(orderId)}/receipt`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    signal,
  })
  return response.data
}

export async function createDraftOrder({ storeId, warehouseId, customerId, items, idempotencyKey }, accessToken, signal, client = httpClient) {
  if (typeof accessToken !== 'string' || accessToken.trim().length === 0) {
    throw new Error('AUTHENTICATION_REQUIRED')
  }
  const headers = { Authorization: `Bearer ${accessToken}` }
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey
  }
  const payload = {
    store_id: storeId,
    warehouse_id: warehouseId,
    customer_id: customerId || null,
    items: (items || []).map((i) => ({
      product_id: i.productId || i.product_id,
      quantity: i.quantity,
    })),
  }
  const response = await client.post('/v1/pos/orders', payload, { headers, signal })
  return response.data
}

export async function selectDevice({ orderId, orderItemId, storeId, serial }, accessToken, signal, client = httpClient) {
  if (typeof accessToken !== 'string' || accessToken.trim().length === 0) {
    throw new Error('AUTHENTICATION_REQUIRED')
  }
  const headers = { Authorization: `Bearer ${accessToken}` }
  const payload = { store_id: storeId, serial }
  const response = await client.post(
    `/v1/pos/orders/${encodeURIComponent(orderId)}/items/${encodeURIComponent(orderItemId)}/devices`,
    payload,
    { headers, signal },
  )
  return response.data
}

export async function reserveOrder(orderId, accessToken, signal, client = httpClient) {
  if (typeof accessToken !== 'string' || accessToken.trim().length === 0) {
    throw new Error('AUTHENTICATION_REQUIRED')
  }
  const headers = { Authorization: `Bearer ${accessToken}` }
  const response = await client.post(`/v1/pos/orders/${encodeURIComponent(orderId)}/reserve`, {}, { headers, signal })
  return response.data
}

export async function completeCashPayment({ orderId, amount }, accessToken, signal, client = httpClient) {
  if (typeof accessToken !== 'string' || accessToken.trim().length === 0) {
    throw new Error('AUTHENTICATION_REQUIRED')
  }
  const headers = { Authorization: `Bearer ${accessToken}` }
  const payload = amount ? { amount } : {}
  const response = await client.post(`/v1/pos/orders/${encodeURIComponent(orderId)}/payments/cash`, payload, { headers, signal })
  return response.data
}

export function classifyPosError(error) {
  const status = error?.response?.status
  if (status === 401) return { state: 'auth-required', message: 'Phiên xác thực không hợp lệ hoặc đã hết hạn.' }
  if (status === 403) return { state: 'forbidden', message: 'Tài khoản không có quyền xem hóa đơn này.' }
  if (status === 404) return { state: 'empty', message: 'Không tìm thấy hóa đơn trong phạm vi được phép.' }
  if (status === 409) return { state: 'conflict', message: 'Đơn hàng chưa ở trạng thái PAID nên chưa thể in hóa đơn.' }
  if (status === 400) return { state: 'error', message: 'Mã đơn hàng không hợp lệ.' }
  return { state: 'error', message: 'Không thể tải hóa đơn. Vui lòng thử lại sau.' }
}
