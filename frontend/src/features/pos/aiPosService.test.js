import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildCrossSellPayload,
  reconcileSuggestion,
  classifyCrossSellError,
  isCrossSellContextReady,
} from './aiPosContract.js'
import { fetchVerifiedCrossSell, sendAiTelemetryEvent } from './aiPosService.js'

const store = '00000000-0000-4000-8000-000000000101'
const warehouse = '00000000-0000-4000-8000-000000000201'
const sourceId = '00000000-0000-4000-8000-000000000702'
const accessoryId = '00000000-0000-4000-8000-000000000801'
const cartItems = [{ productId: sourceId, sku: 'PHONE-IP16-128' }]
const candidate = { product_id: accessoryId, sku: 'ACC-CASE-IP16', name: 'UNTRUSTED', list_price: 1, available_quantity: 1000, is_serialized: false, reason: 'Bảo vệ máy' }
const product = { product_id: accessoryId, sku: candidate.sku, name: 'Ốp từ Catalog', price: 450000, active: true }
const stock = { items: [{ warehouse_id: warehouse, sku_id: accessoryId, sellable_count: 5 }] }

function clientWith({ productValue = product, stockValue = stock, suggestions = [candidate] } = {}) {
  const calls = []
  return {
    calls,
    async post(path, body, config) {
      calls.push([path, body, config])
      return { data: { source: 'RULE_FALLBACK', took_ms: 2510, suggestions } }
    },
    async get(path, config) {
      calls.push([path, config])
      if (path.startsWith('/v1/products/')) return { data: productValue }
      return { data: stockValue }
    },
  }
}

test('builds the new Vu_AI items and the matching legacy sku_ids', () => {
  const payload = buildCrossSellPayload({ storeId: store, warehouseId: warehouse, cartItems })
  assert.deepEqual(payload.items, [{ product_id: sourceId, sku: 'PHONE-IP16-128' }])
  assert.deepEqual(payload.sku_ids, ['PHONE-IP16-128'])
  assert.equal(payload.limit, 4)
})

test('accepts a complete JWT, store, warehouse and cart context only', () => {
  assert.equal(isCrossSellContextReady({ accessToken: 'jwt', storeId: store, warehouseId: warehouse, cartItems }), true)
  assert.equal(isCrossSellContextReady({ accessToken: '   ', storeId: store, warehouseId: warehouse, cartItems }), false)
  assert.equal(isCrossSellContextReady({ accessToken: 'jwt', storeId: store, warehouseId: warehouse, cartItems: [{ sku: 'PHONE-IP16-128' }] }), false)
})

test('rejects invalid store, warehouse, items and limits before a request', () => {
  assert.throws(() => buildCrossSellPayload({ storeId: 'ST1', warehouseId: warehouse, cartItems }), /INVALID/)
  assert.throws(() => buildCrossSellPayload({ storeId: store, warehouseId: warehouse, cartItems: [] }), /INVALID/)
  assert.throws(() => buildCrossSellPayload({ storeId: store, warehouseId: warehouse, cartItems, limit: 11 }), /INVALID/)
})

test('never calls API without JWT', async () => {
  const client = clientWith()
  await assert.rejects(() => fetchVerifiedCrossSell({ storeId: store, warehouseId: warehouse, cartItems }, client), /AUTHENTICATION/)
  assert.equal(client.calls.length, 0)
})

test('renders only authoritative Catalog name/price and current warehouse stock', async () => {
  const client = clientWith()
  const response = await fetchVerifiedCrossSell({ accessToken: 'test-token', storeId: store, warehouseId: warehouse, cartItems }, client)
  assert.equal(response.suggestions.length, 1)
  assert.equal(response.suggestions[0].name, 'Ốp từ Catalog')
  assert.equal(response.suggestions[0].listPrice, 450000)
  assert.equal(response.suggestions[0].availableQuantity, 5)
  assert.equal(response.suggestions[0].requiresServerValidation, true)
  assert.equal(client.calls[0][0], '/v1/ai/cross-sell')
  assert.equal(client.calls[0][1].store_id, store)
  assert.equal(client.calls[0][1].warehouse_id, warehouse)
  assert.equal(client.calls[0][2].headers.Authorization, 'Bearer test-token')
  assert.equal(client.calls[1][0], `/v1/products/${accessoryId}`)
  assert.equal(client.calls[2][0], '/v1/inventory/stock')
  assert.deepEqual(client.calls[2][1].params, { warehouse_id: warehouse, sku: candidate.sku, limit: 100, offset: 0 })
})

test('fails closed for the legacy main AI response without product_id', async () => {
  const client = clientWith({ suggestions: [{ sku_id: candidate.sku, reason: candidate.reason }] })
  const response = await fetchVerifiedCrossSell({ accessToken: 'test-token', storeId: store, warehouseId: warehouse, cartItems }, client)
  assert.deepEqual(response.suggestions, [])
  assert.equal(client.calls.length, 1)
})

test('fails closed for fabricated AI UUID or SKU mismatch', async () => {
  const client = clientWith({ productValue: { ...product, product_id: sourceId } })
  const response = await fetchVerifiedCrossSell({ accessToken: 'test-token', storeId: store, warehouseId: warehouse, cartItems }, client)
  assert.deepEqual(response.suggestions, [])
  assert.equal(reconcileSuggestion({ ...candidate, sku: 'WRONG' }, product, stock, warehouse), null)
})

test('does not offer sold/out-of-stock, other warehouse or inactive products', async () => {
  const zero = clientWith({ stockValue: { items: [{ warehouse_id: warehouse, sku_id: accessoryId, sellable_count: 0 }] } })
  assert.deepEqual((await fetchVerifiedCrossSell({ accessToken: 't', storeId: store, warehouseId: warehouse, cartItems }, zero)).suggestions, [])
  const crossStore = clientWith({ stockValue: { items: [{ warehouse_id: store, sku_id: accessoryId, sellable_count: 30 }] } })
  assert.deepEqual((await fetchVerifiedCrossSell({ accessToken: 't', storeId: store, warehouseId: warehouse, cartItems }, crossStore)).suggestions, [])
  const inactive = clientWith({ productValue: { ...product, active: false } })
  assert.deepEqual((await fetchVerifiedCrossSell({ accessToken: 't', storeId: store, warehouseId: warehouse, cartItems }, inactive)).suggestions, [])
})

test('does not expose raw backend SQL and differentiates auth scope', () => {
  assert.match(classifyCrossSellError({ response: { status: 401 } }), /đăng nhập/)
  assert.match(classifyCrossSellError({ response: { status: 403 } }), /quyền/)
  assert.doesNotMatch(classifyCrossSellError({ message: 'SELECT secret FROM users' }), /SELECT|secret/)
})

test('sendAiTelemetryEvent sends SHOWN and ACCEPTED payloads to /v1/ai/events', async () => {
  const calls = []
  const client = {
    async post(path, body, config) {
      calls.push({ path, body, config })
      return { data: { status: 'RECORDED' } }
    },
  }
  await sendAiTelemetryEvent({
    eventType: 'SHOWN',
    skuIds: ['ACC-CASE-IP16'],
    storeId: store,
    warehouseId: warehouse,
    source: 'AI',
    accessToken: 'test-token',
  }, client)

  assert.equal(calls.length, 1)
  assert.equal(calls[0].path, '/v1/ai/events')
  assert.equal(calls[0].body.event_type, 'SHOWN')
  assert.deepEqual(calls[0].body.sku_ids, ['ACC-CASE-IP16'])
  assert.equal(calls[0].config.headers.Authorization, 'Bearer test-token')

  await sendAiTelemetryEvent({
    eventType: 'ACCEPTED',
    skuId: 'ACC-CASE-IP16',
    storeId: store,
    warehouseId: warehouse,
    source: 'AI',
    accessToken: 'test-token',
  }, client)

  assert.equal(calls.length, 2)
  assert.equal(calls[1].body.event_type, 'ACCEPTED')
  assert.equal(calls[1].body.sku_id, 'ACC-CASE-IP16')
})

test('sendAiTelemetryEvent ignores requests without valid access token', async () => {
  const calls = []
  const client = {
    async post(path, body, config) {
      calls.push({ path, body, config })
    },
  }
  await sendAiTelemetryEvent({ eventType: 'SHOWN', accessToken: '' }, client)
  assert.equal(calls.length, 0)
})
