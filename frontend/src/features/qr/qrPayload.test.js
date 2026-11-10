import assert from 'node:assert/strict'
import test from 'node:test'

import { abbreviateQrToken, validateQrPayload } from './qrPayload.js'

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
