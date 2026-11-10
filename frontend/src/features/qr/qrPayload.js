const QR_PREFIX = 'SASMS:DEVICE:'
const CANONICAL_UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

export function validateQrPayload(rawPayload) {
  if (typeof rawPayload !== 'string' || rawPayload.length === 0) {
    return { valid: false, message: 'Payload QR không được để trống.' }
  }

  if (!rawPayload.startsWith(QR_PREFIX)) {
    return { valid: false, message: `QR nội bộ phải bắt đầu bằng ${QR_PREFIX}` }
  }

  const qrToken = rawPayload.slice(QR_PREFIX.length)
  if (!CANONICAL_UUID_PATTERN.test(qrToken)) {
    return {
      valid: false,
      message: 'qr_token phải là UUID canonical viết thường, không có khoảng trắng.',
    }
  }

  return { valid: true, qrToken }
}

export function abbreviateQrToken(qrToken) {
  return `${qrToken.slice(0, 8)}…${qrToken.slice(-6)}`
}
