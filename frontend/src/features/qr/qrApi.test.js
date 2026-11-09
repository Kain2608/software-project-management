import assert from 'node:assert/strict'
import test from 'node:test'

import { classifyLookupError } from './qrApi.js'

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
