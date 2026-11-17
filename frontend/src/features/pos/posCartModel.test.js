import assert from 'node:assert/strict'
import test from 'node:test'

import {
  MAX_CART_QUANTITY,
  addCartItem,
  calculateCartTotal,
  normalizeCatalogItem,
  removeCartItem,
  toCrossSellCartItems,
  updateCartItemQuantity,
} from './posCartModel.js'

const productId = '00000000-0000-4000-8000-000000000701'
const product = { product_id: productId, sku: 'TV-55', name: 'Tivi 55', price: 12000000, active: true }

test('adds, updates and removes an immutable cart line', () => {
  const original = []
  const added = addCartItem(original, normalizeCatalogItem(product, 3))
  const updated = updateCartItemQuantity(added, productId, 2)
  const removed = removeCartItem(updated, productId)
  assert.deepEqual(original, [])
  assert.equal(added[0].quantity, 1)
  assert.equal(updated[0].quantity, 2)
  assert.deepEqual(removed, [])
})

test('rejects invalid, inactive, fractional-price and out-of-stock products', () => {
  assert.throws(() => normalizeCatalogItem({ ...product, product_id: 'bad' }, 1), /INVALID_PRODUCT/)
  assert.throws(() => normalizeCatalogItem({ ...product, active: false }, 1), /INVALID_PRODUCT/)
  assert.throws(() => normalizeCatalogItem({ ...product, price: 1.5 }, 1), /INVALID_PRODUCT/)
  assert.throws(() => addCartItem([], normalizeCatalogItem(product, 0)), /OUT_OF_STOCK/)
})

test('never exceeds known sellable stock or the frontend quantity guard', () => {
  const one = addCartItem([], normalizeCatalogItem(product, 2))
  const two = addCartItem(one, normalizeCatalogItem(product, 2))
  assert.throws(() => addCartItem(two, normalizeCatalogItem(product, 2)), /QUANTITY_EXCEEDS_STOCK/)
  assert.throws(() => updateCartItemQuantity(one, productId, MAX_CART_QUANTITY + 1), /QUANTITY_EXCEEDS_STOCK/)
})

test('calculates integer VND total without rounding', () => {
  const cart = [{ ...normalizeCatalogItem(product, 3), quantity: 2 }]
  assert.equal(calculateCartTotal(cart), 24000000)
  assert.throws(() => calculateCartTotal([{ ...cart[0], unitPrice: 2.5 }]), /INVALID_CART_ITEM/)
})

test('passes only product identity to CrossSellWidget and has no payment or stock mutation fields', () => {
  const cart = [{ ...normalizeCatalogItem(product, 3), quantity: 2 }]
  assert.deepEqual(toCrossSellCartItems(cart), [{ productId, sku: 'TV-55' }])
  assert.equal('payment_status' in cart[0], false)
  assert.equal('reserved_order_id' in cart[0], false)
})

test('accepts a Catalog and Stock verified CrossSellWidget suggestion without adding side effects', () => {
  const suggestion = {
    productId,
    sku: 'TV-55',
    name: 'Tivi 55',
    listPrice: 12000000,
    availableQuantity: 2,
    requiresServerValidation: true,
  }
  const cart = addCartItem([], suggestion)
  assert.deepEqual(cart, [{
    productId,
    sku: 'TV-55',
    name: 'Tivi 55',
    unitPrice: 12000000,
    availableQuantity: 2,
    quantity: 1,
  }])
  assert.equal('requiresServerValidation' in cart[0], false)
})
