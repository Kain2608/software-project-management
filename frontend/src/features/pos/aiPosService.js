import { buildCrossSellPayload, reconcileSuggestion, validateCrossSellResponse } from './aiPosContract.js'

// Injected transport keeps data verification independently testable from Axios/Vite.
export async function fetchVerifiedCrossSell({ accessToken, storeId, warehouseId, cartItems, signal }, client) {
  if (typeof accessToken !== 'string' || !accessToken.trim()) {
    throw new Error('AUTHENTICATION_REQUIRED')
  }
  const payload = buildCrossSellPayload({ storeId, warehouseId, cartItems })
  const config = {
    headers: { Authorization: `Bearer ${accessToken}` },
    signal,
    timeout: 3500,
  }
  const { data } = await client.post('/v1/ai/cross-sell', payload, config)
  const response = validateCrossSellResponse(data)
  const candidates = response.suggestions.slice(0, 10)
  const verified = []
  const seen = new Set()
  for (const candidate of candidates) {
    if (signal?.aborted) throw new Error('REQUEST_ABORTED')
    if (!candidate || typeof candidate.product_id !== 'string' || typeof candidate.sku !== 'string') continue
    try {
      const { data: product } = await client.get(`/v1/products/${encodeURIComponent(candidate.product_id)}`, config)
      const { data: stock } = await client.get('/v1/inventory/stock', {
        ...config,
        params: { warehouse_id: warehouseId, sku: candidate.sku, limit: 100, offset: 0 },
      })
      const item = reconcileSuggestion(candidate, product, stock, warehouseId)
      if (item && !seen.has(item.productId.toLowerCase())) {
        seen.add(item.productId.toLowerCase())
        verified.push(item)
      }
    } catch (error) {
      if (signal?.aborted) throw error
      // A missing/out-of-scope product must never appear as sellable.
      if (error?.response?.status === 404) continue
      throw error
    }
    if (verified.length >= payload.limit) break
  }
  return { source: response.source, tookMs: response.took_ms, suggestions: verified }
}

export async function sendAiTelemetryEvent({ eventType, skuIds, skuId, storeId, warehouseId, source, accessToken, signal }, client) {
  if (typeof accessToken !== 'string' || !accessToken.trim()) {
    return
  }
  const payload = {
    event_type: eventType,
    sku_ids: skuIds,
    sku_id: skuId,
    store_id: storeId,
    warehouse_id: warehouseId,
    source: source || 'AI',
  }
  try {
    await client.post('/v1/ai/events', payload, {
      headers: { Authorization: `Bearer ${accessToken}` },
      signal,
      timeout: 3000,
    })
  } catch {
    // Non-blocking telemetry
  }
}
