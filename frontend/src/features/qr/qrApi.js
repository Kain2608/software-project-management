import { httpClient } from '../../shared/api/httpClient.js'

export async function lookupQrDevice(payload, accessToken, signal, client = httpClient) {
  if (typeof accessToken !== 'string' || accessToken.length === 0) {
    throw new Error('AUTHENTICATION_REQUIRED')
  }

  const response = await client.post(
    '/v1/devices/qr-lookup',
    { payload },
    {
      headers: { Authorization: `Bearer ${accessToken}` },
      signal,
    },
  )
  return response.data
}

export function createQrLookupContext(accessToken = null) {
  return { accessToken }
}

export function isSameQrLookupContext(left, right) {
  return left?.accessToken === right?.accessToken
}

export function isQrLookupRequestCurrent(gate, request, requestContext, currentContext) {
  return gate.isCurrent(request) && isSameQrLookupContext(requestContext, currentContext)
}

export function createLatestQrLookupGate(createController = () => new AbortController()) {
  let generation = 0
  let controller = null

  return {
    start() {
      controller?.abort()
      controller = createController()
      generation += 1
      return { generation, signal: controller.signal }
    },
    invalidate() {
      generation += 1
      controller?.abort()
      controller = null
    },
    isCurrent(request) {
      return request?.generation === generation && request.signal?.aborted !== true
    },
  }
}

export function classifyLookupError(error) {
  const status = error?.response?.status
  if (status === 401) {
    return { state: 'auth-required', message: 'Phiên xác thực không hợp lệ hoặc đã hết hạn.' }
  }
  if (status === 403) {
    return { state: 'forbidden', message: 'Tài khoản không có quyền xem thiết bị này.' }
  }
  if (status === 404) {
    return { state: 'empty', message: 'Không tìm thấy thiết bị trong phạm vi được phép.' }
  }
  if (status === 400 || status === 422) {
    return { state: 'error', message: 'Payload QR không được backend chấp nhận.' }
  }
  return { state: 'error', message: 'Không thể tra cứu thiết bị. Vui lòng thử lại sau.' }
}
