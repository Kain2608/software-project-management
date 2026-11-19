import React, { useMemo } from 'react'
import { QRCodeDecoderErrorCorrectionLevel, QRCodeEncoder } from '@zxing/library'
import { formatWarrantyQrPayload, validateQrPayload } from './qrPayload.js'

export default function WarrantyQrCode({
  payload,
  qrToken,
  size = 140,
  ariaLabel = 'Mã QR bảo hành thiết bị SASMS',
  className = '',
}) {
  const resolvedPayload = useMemo(() => {
    if (payload) {
      if (payload.startsWith('SASMS:DEVICE:')) {
        return payload
      }
      try {
        return formatWarrantyQrPayload(payload)
      } catch {
        return payload
      }
    }
    if (qrToken) {
      try {
        return formatWarrantyQrPayload(qrToken)
      } catch {
        return ''
      }
    }
    return ''
  }, [payload, qrToken])

  const qrData = useMemo(() => {
    if (!resolvedPayload) return null
    try {
      const val = validateQrPayload(resolvedPayload)
      if (!val.valid) {
        return { error: val.message }
      }
      const code = QRCodeEncoder.encode(
        resolvedPayload,
        QRCodeDecoderErrorCorrectionLevel.M
      )
      const matrix = code.getMatrix()
      const width = matrix.getWidth()
      const height = matrix.getHeight()
      const quietZone = 4
      const viewBoxWidth = width + quietZone * 2
      const viewBoxHeight = height + quietZone * 2

      let pathData = ''
      for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
          if (matrix.get(x, y) === 1) {
            pathData += `M${x + quietZone},${y + quietZone}h1v1h-1z `
          }
        }
      }

      return {
        viewBox: `0 0 ${viewBoxWidth} ${viewBoxHeight}`,
        pathData,
        width: viewBoxWidth,
        height: viewBoxHeight,
      }
    } catch (err) {
      return { error: err.message || 'Không thể tạo mã QR' }
    }
  }, [resolvedPayload])

  if (!resolvedPayload) {
    return (
      <div
        className={`warranty-qr-empty ${className}`}
        style={{
          width: size,
          height: size,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#f8f9fa',
          border: '1px dashed #ced4da',
          borderRadius: 4,
          fontSize: 12,
          color: '#6c757d',
        }}
      >
        Chưa có mã QR
      </div>
    )
  }

  if (qrData?.error) {
    return (
      <div
        className={`warranty-qr-error ${className}`}
        role="alert"
        style={{
          width: size,
          minHeight: size,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#fff5f5',
          border: '1px solid #ffc9c9',
          borderRadius: 4,
          padding: 8,
          fontSize: 12,
          color: '#e03131',
          textAlign: 'center',
        }}
      >
        <span>{qrData.error}</span>
      </div>
    )
  }

  return (
    <div
      className={`warranty-qr-container ${className}`}
      data-testid="warranty-qr-code"
      style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center' }}
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox={qrData.viewBox}
        width={size}
        height={size}
        shapeRendering="crispEdges"
        role="img"
        aria-label={ariaLabel}
        style={{ width: size, height: size, maxWidth: '100%', display: 'block' }}
      >
        <rect width="100%" height="100%" fill="#ffffff" />
        <path d={qrData.pathData} fill="#000000" />
      </svg>
    </div>
  )
}
