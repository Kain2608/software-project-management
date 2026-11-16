import assert from 'node:assert/strict'
import test from 'node:test'

import { classifyCartApiError, searchCatalogForPos } from './posCartApi.js'

const warehouseId = '00000000-0000-4000-8000-000000000201'
const productId = '00000000-0000-4000-8000-000000000701'

function client() {
  const calls = []
  return {
    calls,
    async get(path, config) {
      calls.push([path, config])
      if (path === '/v1/products') {
        return { data: { items: [{ product_id: productId, sku: 'TV-55', name: 'Tivi 55', price: 12000000, active: true }] } }
      }
      return { data: { items: [{ sku_id: productId, warehouse_id: warehouseId, sellable_count: 2 }] } }
    },
  }
}

test('requires JWT and valid warehouse before any API call', async () => {
  const transport = client()
  await assert.rejects(() => searchCatalogForPos({ query: 'TV', warehouseId }, transport), /AUTHENTICATION_REQUIRED/)
  await assert.rejects(() => searchCatalogForPos({ query: 'TV', accessToken: 'jwt', warehouseId: 'bad' }, transport), /INVALID_WAREHOUSE/)
  assert.equal(transport.calls.length, 0)
})

test('searches active Catalog then reads sellable stock in the exact warehouse', async () => {
  const transport = client()
  const results = await searchCatalogForPos({ query: ' TV ', accessToken: 'jwt', warehouseId }, transport)
  assert.equal(results.length, 1)
  assert.equal(results[0].availableQuantity, 2)
  assert.equal(transport.calls[0][0], '/v1/products')
  assert.deepEqual(transport.calls[0][1].params, { query: 'TV', active: true, limit: 20, offset: 0 })
  assert.equal(transport.calls[0][1].headers.Authorization, 'Bearer jwt')
  assert.equal(transport.calls[1][0], '/v1/inventory/stock')
  assert.deepEqual(transport.calls[1][1].params, { warehouse_id: warehouseId, sku: 'TV-55', limit: 100, offset: 0 })
})

test('fails closed when stock response belongs to another warehouse', async () => {
  const transport = client()
  const originalGet = transport.get
  transport.get = async (path, config) => path === '/v1/inventory/stock'
    ? { data: { items: [{ sku_id: productId, warehouse_id: productId, sellable_count: 99 }] } }
    : originalGet(path, config)
  const results = await searchCatalogForPos({ query: 'TV', accessToken: 'jwt', warehouseId }, transport)
  assert.equal(results[0].availableQuantity, 0)
})

test('maps authentication, forbidden and dependency errors without leaking backend detail', () => {
  assert.equal(classifyCartApiError({ response: { status: 401 } }).state, 'auth-required')
  assert.equal(classifyCartApiError({ response: { status: 403 } }).state, 'forbidden')
  const fallback = classifyCartApiError({ message: 'SELECT password FROM users' })
  assert.equal(fallback.state, 'error')
  assert.doesNotMatch(fallback.message, /SELECT|password/)
})

test('treats an expired JWT response as authentication required', () => {
  const expired = classifyCartApiError({
    response: { status: 401, data: { code: 'TOKEN_EXPIRED', detail: 'internal detail' } },
  })
  assert.equal(expired.state, 'auth-required')
  assert.doesNotMatch(expired.message, /TOKEN_EXPIRED|internal detail/)
})
