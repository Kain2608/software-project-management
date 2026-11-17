import assert from 'node:assert/strict'
import test from 'node:test'

import {
  classifyLookupError,
  createLatestQrLookupGate,
  createQrLookupContext,
  isQrLookupRequestCurrent,
  lookupQrDevice,
} from './qrApi.js'

test('passes the AbortSignal to the authenticated QR lookup request', async () => {
  const controller = new AbortController()
  const client = {
    post: async (path, body, config) => {
      assert.equal(path, '/v1/devices/qr-lookup')
      assert.deepEqual(body, { payload: 'SASMS:DEVICE:token' })
      assert.equal(config.headers.Authorization, 'Bearer jwt-session-a')
      assert.equal(config.signal, controller.signal)
      return { data: { device_id: 'device-a' } }
    },
  }

  const result = await lookupQrDevice(
    'SASMS:DEVICE:token',
    'jwt-session-a',
    controller.signal,
    client,
  )

  assert.deepEqual(result, { device_id: 'device-a' })
})

test('treats a QR response from an earlier authentication context as stale', async () => {
  let completeRequest
  const client = {
    post: (_path, _body, config) => new Promise((resolve) => {
      completeRequest = () => resolve({ data: { device_id: 'old-session-device' } })
      assert.equal(config.signal.aborted, false)
    }),
  }
  const gate = createLatestQrLookupGate()
  const oldContext = createQrLookupContext('jwt-session-a')
  const newContext = createQrLookupContext('jwt-session-b')
  const request = gate.start()
  const pendingLookup = lookupQrDevice(
    'SASMS:DEVICE:token',
    oldContext.accessToken,
    request.signal,
    client,
  )

  gate.invalidate()
  completeRequest()
  const staleResult = await pendingLookup

  assert.equal(request.signal.aborted, true)
  assert.equal(isQrLookupRequestCurrent(gate, request, oldContext, newContext), false)
  assert.deepEqual(staleResult, { device_id: 'old-session-device' })
})

test('maps authentication and authorization responses without exposing server details', () => {
  assert.equal(classifyLookupError({ response: { status: 401 } }).state, 'auth-required')
  assert.equal(classifyLookupError({ response: { status: 403 } }).state, 'forbidden')
})

test('uses the same empty state for a concealed or missing device', () => {
  assert.deepEqual(classifyLookupError({ response: { status: 404 } }), {
    state: 'empty',
    message: 'Không tìm thấy thiết bị trong phạm vi được phép.',
  })
})

test('maps validation and dependency failures to safe messages', () => {
  assert.equal(classifyLookupError({ response: { status: 400 } }).state, 'error')
  assert.equal(classifyLookupError({ response: { status: 422 } }).state, 'error')
  const fallback = classifyLookupError({ response: { status: 503 }, message: 'SQL secret detail' })
  assert.equal(fallback.state, 'error')
  assert.equal(fallback.message.includes('SQL'), false)
  assert.equal(fallback.message.includes('secret'), false)
})
