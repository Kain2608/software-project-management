import assert from 'node:assert/strict'
import test from 'node:test'

import { abbreviateQrToken, formatWarrantyQrPayload, validateQrPayload } from './qrPayload.js'
import {
  BinaryBitmap,
  HybridBinarizer,
  QRCodeDecoderErrorCorrectionLevel,
  QRCodeEncoder,
  QRCodeReader,
  RGBLuminanceSource,
} from '@zxing/library'

const TOKEN = '123e4567-e89b-12d3-a456-426614174000'

test('accepts the proposed SASMS device payload and returns qr_token', () => {
  assert.deepEqual(validateQrPayload(`SASMS:DEVICE:${TOKEN}`), {
    valid: true,
    qrToken: TOKEN,
  })
})

test('rejects null and empty payloads', () => {
  assert.equal(validateQrPayload(null).valid, false)
  assert.equal(validateQrPayload('').valid, false)
})

test('rejects a wrong or lower-case prefix', () => {
  assert.equal(validateQrPayload(`OTHER:DEVICE:${TOKEN}`).valid, false)
  assert.equal(validateQrPayload(`sasms:device:${TOKEN}`).valid, false)
})

test('rejects missing and malformed UUID tokens', () => {
  assert.equal(validateQrPayload('SASMS:DEVICE:').valid, false)
  assert.equal(validateQrPayload('SASMS:DEVICE:not-a-uuid').valid, false)
})

test('rejects upper-case UUID and non-canonical UUID variants', () => {
  assert.equal(validateQrPayload(`SASMS:DEVICE:${TOKEN.toUpperCase()}`).valid, false)
  assert.equal(validateQrPayload('SASMS:DEVICE:123e4567e89b12d3a456426614174000').valid, false)
})

test('does not trim whitespace or accept trailing content', () => {
  assert.equal(validateQrPayload(` SASMS:DEVICE:${TOKEN}`).valid, false)
  assert.equal(validateQrPayload(`SASMS:DEVICE:${TOKEN} `).valid, false)
  assert.equal(validateQrPayload(`SASMS:DEVICE:${TOKEN}:extra`).valid, false)
})

test('rejects an external URL', () => {
  assert.equal(validateQrPayload(`https://example.com/device/${TOKEN}`).valid, false)
})

test('abbreviates a token without exposing the full value in the result card', () => {
  assert.equal(abbreviateQrToken(TOKEN), '123e4567…174000')
})

test('formatWarrantyQrPayload formats canonical UUID token into SASMS:DEVICE prefix', () => {
  assert.equal(formatWarrantyQrPayload(TOKEN), `SASMS:DEVICE:${TOKEN}`)
})

test('formatWarrantyQrPayload rejects invalid tokens', () => {
  assert.throws(() => formatWarrantyQrPayload(''), /qr_token phải là UUID canonical/)
  assert.throws(() => formatWarrantyQrPayload('invalid-token'), /qr_token phải là UUID canonical/)
  assert.throws(() => formatWarrantyQrPayload(null), /qr_token phải là UUID canonical/)
})

test('scannable QR code can be encoded and decoded roundtrip with @zxing/library', () => {
  const payload = formatWarrantyQrPayload(TOKEN)
  const code = QRCodeEncoder.encode(payload, QRCodeDecoderErrorCorrectionLevel.M)
  const matrix = code.getMatrix()
  const w = matrix.getWidth()
  const h = matrix.getHeight()
  const quietZone = 4
  const scale = 4
  const fullW = (w + quietZone * 2) * scale
  const fullH = (h + quietZone * 2) * scale

  const grayArray = new Uint8ClampedArray(fullW * fullH)
  grayArray.fill(255) // white background

  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      if (matrix.get(x, y) === 1) {
        for (let dy = 0; dy < scale; dy++) {
          for (let dx = 0; dx < scale; dx++) {
            const py = (y + quietZone) * scale + dy
            const px = (x + quietZone) * scale + dx
            grayArray[py * fullW + px] = 0 // black module
          }
        }
      }
    }
  }

  const lum = new RGBLuminanceSource(grayArray, fullW, fullH)
  const bitmap = new BinaryBitmap(new HybridBinarizer(lum))
  const reader = new QRCodeReader()
  const result = reader.decode(bitmap)

  assert.equal(result.getText(), payload)
  const validation = validateQrPayload(result.getText())
  assert.equal(validation.valid, true)
  assert.equal(validation.qrToken, TOKEN)
})
