import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createLatestRequestGate,
  createPosContext,
  isSamePosContext,
  retainCartForContext,
  shouldInvalidateCartForApiState,
} from './posCartContext.js'

const session = {
  accessToken: 'jwt-session-a',
  storeId: '00000000-0000-4000-8000-000000000101',
  warehouseId: '00000000-0000-4000-8000-000000000201',
}

test('keeps the same cart reference during normal renders with unchanged context', () => {
  const cart = [{ productId: '00000000-0000-4000-8000-000000000701' }]
  const previous = createPosContext(session)
  const current = createPosContext({ ...session })
  assert.equal(isSamePosContext(previous, current), true)
  assert.equal(retainCartForContext(cart, previous, current), cart)
})

test('fails closed when session, store or warehouse changes', () => {
  const cart = [{ productId: '00000000-0000-4000-8000-000000000701' }]
  const previous = createPosContext(session)
  const changes = [
    { ...session, accessToken: 'jwt-session-b' },
    { ...session, storeId: '00000000-0000-4000-8000-000000000102' },
    { ...session, warehouseId: '00000000-0000-4000-8000-000000000202' },
    { ...session, accessToken: null },
  ]
  for (const next of changes) {
    assert.deepEqual(retainCartForContext(cart, previous, createPosContext(next)), [])
  }
})

test('aborts an older request and ignores its stale response', () => {
  const gate = createLatestRequestGate()
  const oldRequest = gate.start()
  const currentRequest = gate.start()
  assert.equal(oldRequest.signal.aborted, true)
  assert.equal(gate.isCurrent(oldRequest), false)
  assert.equal(gate.isCurrent(currentRequest), true)
  gate.invalidate()
  assert.equal(currentRequest.signal.aborted, true)
  assert.equal(gate.isCurrent(currentRequest), false)
})

test('invalidates cart on authentication failure but not ordinary API errors', () => {
  assert.equal(shouldInvalidateCartForApiState('auth-required'), true)
  assert.equal(shouldInvalidateCartForApiState('forbidden'), false)
  assert.equal(shouldInvalidateCartForApiState('error'), false)
})
