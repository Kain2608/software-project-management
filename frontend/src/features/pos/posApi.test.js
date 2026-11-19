import assert from 'node:assert/strict'
import test from 'node:test'

import {
  classifyPosError,
  completeCashPayment,
  createDraftOrder,
  getReceipt,
  reserveOrder,
  selectDevice,
} from './posApi.js'
import {
  createLatestRequestGate,
  createPosContext,
  isRequestCurrentForContext,
} from './posCartContext.js'

test('requires an access token before receipt lookup', async () => {
  await assert.rejects(() => getReceipt('order-id', null), /AUTHENTICATION_REQUIRED/)
})

test('createDraftOrder requires access token and posts correct payload with idempotency key', async () => {
  await assert.rejects(() => createDraftOrder({}, null), /AUTHENTICATION_REQUIRED/)

  let capturedPath = null
  let capturedPayload = null
  let capturedHeaders = null
  const mockClient = {
    post: async (path, payload, config) => {
      capturedPath = path
      capturedPayload = payload
      capturedHeaders = config.headers
      return { data: { id: 'order-123', status: 'DRAFT' } }
    },
  }

  const result = await createDraftOrder(
    {
      storeId: 'store-1',
      warehouseId: 'wh-1',
      customerId: 'cust-1',
      items: [{ productId: 'prod-1', quantity: 2 }],
      idempotencyKey: 'idem-key-1',
    },
    'valid-jwt',
    undefined,
    mockClient,
  )

  assert.equal(capturedPath, '/v1/pos/orders')
  assert.equal(capturedPayload.store_id, 'store-1')
  assert.equal(capturedPayload.warehouse_id, 'wh-1')
  assert.equal(capturedPayload.customer_id, 'cust-1')
  assert.equal(capturedPayload.items[0].product_id, 'prod-1')
  assert.equal(capturedPayload.items[0].quantity, 2)
  assert.equal(capturedHeaders['Authorization'], 'Bearer valid-jwt')
  assert.equal(capturedHeaders['Idempotency-Key'], 'idem-key-1')
  assert.equal(result.id, 'order-123')
})

test('selectDevice requires access token and posts serial lock', async () => {
  await assert.rejects(() => selectDevice({}, null), /AUTHENTICATION_REQUIRED/)

  let capturedPath = null
  let capturedPayload = null
  const mockClient = {
    post: async (path, payload) => {
      capturedPath = path
      capturedPayload = payload
      return { data: { device_id: 'dev-1', status: 'SELECTED' } }
    },
  }

  const result = await selectDevice(
    { orderId: 'ord-1', orderItemId: 'item-1', storeId: 'store-1', serial: 'SN-001' },
    'jwt-token',
    undefined,
    mockClient,
  )

  assert.equal(capturedPath, '/v1/pos/orders/ord-1/items/item-1/devices')
  assert.equal(capturedPayload.store_id, 'store-1')
  assert.equal(capturedPayload.serial, 'SN-001')
  assert.equal(result.status, 'SELECTED')
})

test('reserveOrder requires access token and posts reserve', async () => {
  await assert.rejects(() => reserveOrder('ord-1', null), /AUTHENTICATION_REQUIRED/)

  let capturedPath = null
  const mockClient = {
    post: async (path) => {
      capturedPath = path
      return { data: { order_id: 'ord-1', status: 'RESERVED' } }
    },
  }

  const result = await reserveOrder('ord-1', 'jwt-token', undefined, mockClient)
  assert.equal(capturedPath, '/v1/pos/orders/ord-1/reserve')
  assert.equal(result.status, 'RESERVED')
})

test('completeCashPayment requires access token and posts cash payment', async () => {
  await assert.rejects(() => completeCashPayment({}, null), /AUTHENTICATION_REQUIRED/)

  let capturedPath = null
  let capturedPayload = null
  const mockClient = {
    post: async (path, payload) => {
      capturedPath = path
      capturedPayload = payload
      return { data: { order_id: 'ord-1', status: 'PAID', invoice_number: 'RECEIPT-ORD-1' } }
    },
  }

  const result = await completeCashPayment({ orderId: 'ord-1', amount: 500000 }, 'jwt-token', undefined, mockClient)
  assert.equal(capturedPath, '/v1/pos/orders/ord-1/payments/cash')
  assert.equal(capturedPayload.amount, 500000)
  assert.equal(result.status, 'PAID')
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
