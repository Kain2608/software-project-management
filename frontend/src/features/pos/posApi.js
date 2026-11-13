import { httpClient } from '../../shared/api/httpClient.js'

export async function getPosStatus() {
  const response = await httpClient.get('/pos/status')
  return response.data
}

export async function getReceipt(orderId, accessToken) {
  if (typeof accessToken !== 'string' || accessToken.length === 0) {
    throw new Error('AUTHENTICATION_REQUIRED')
  }
  const response = await httpClient.get(`/v1/pos/orders/${encodeURIComponent(orderId)}/receipt`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
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
