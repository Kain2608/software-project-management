import test from 'node:test'
import assert from 'node:assert/strict'
import { evaluateOnlineCheckout, ONLINE_ORDER_BLOCKERS } from './onlineOrderPolicy.js'

test('fails closed without identity and never enables checkout', () => {
  const state = evaluateOnlineCheckout({ cartItems: [] })
  assert.equal(state.catalogEnabled, false)
  assert.equal(state.checkoutEnabled, false)
  assert.ok(state.blockers.includes('AUTHENTICATION_REQUIRED'))
})

test('allows read-only catalog flow while submission remains blocked', () => {
  const state = evaluateOnlineCheckout({ accessToken: 'opaque', storeId: 's', warehouseId: 'w', cartItems: [{}] })
  assert.equal(state.catalogEnabled, true)
  assert.equal(state.hasItems, true)
  assert.equal(state.checkoutEnabled, false)
  assert.deepEqual(state.blockers, [ONLINE_ORDER_BLOCKERS.AUTH, ONLINE_ORDER_BLOCKERS.PAYMENT, ONLINE_ORDER_BLOCKERS.API])
})
