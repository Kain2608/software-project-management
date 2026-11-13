import assert from 'node:assert/strict'
import test from 'node:test'

import { classifyPosError, getReceipt } from './posApi.js'

test('requires an access token before receipt lookup', async () => {
  await assert.rejects(() => getReceipt('order-id', null), /AUTHENTICATION_REQUIRED/)
})

test('maps concealed receipt and unpaid order without leaking server details', () => {
  assert.equal(classifyPosError({ response: { status: 404 } }).state, 'empty')
  assert.equal(classifyPosError({ response: { status: 409 } }).state, 'conflict')
  const fallback = classifyPosError({ response: { status: 503 }, message: 'SELECT password' })
  assert.equal(fallback.state, 'error')
  assert.equal(fallback.message.includes('SELECT'), false)
  assert.equal(fallback.message.includes('password'), false)
})
