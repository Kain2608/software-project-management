import assert from 'node:assert/strict'
import test from 'node:test'

import { classifyPosError, getReceipt } from './posApi.js'
import {
  createLatestRequestGate,
  createPosContext,
  isRequestCurrentForContext,
} from './posCartContext.js'

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

test('ignores a receipt response that completes after the authenticated context changes', async () => {
  let completeRequest
  const client = {
    get: (_path, config) => new Promise((resolve) => {
      completeRequest = () => resolve({ data: { order_id: 'old-session-order' } })
      assert.equal(config.signal.aborted, false)
    }),
  }
  const gate = createLatestRequestGate()
  const oldContext = createPosContext({
    accessToken: 'jwt-session-a',
    storeId: '00000000-0000-4000-8000-000000000101',
    warehouseId: '00000000-0000-4000-8000-000000000201',
  })
  const newContext = createPosContext({
    ...oldContext,
    accessToken: 'jwt-session-b',
  })
  const request = gate.start()
  const pendingReceipt = getReceipt('order-id', oldContext.accessToken, request.signal, client)

  gate.invalidate()
  completeRequest()
  const staleReceipt = await pendingReceipt

  assert.equal(request.signal.aborted, true)
  assert.equal(isRequestCurrentForContext(gate, request, oldContext, newContext), false)
  assert.equal(staleReceipt.order_id, 'old-session-order')
})
