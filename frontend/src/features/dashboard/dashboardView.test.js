import test from 'node:test'
import assert from 'node:assert/strict'
import { REFRESH_INTERVAL_MS, aiEffectivenessView, dataAgeSeconds } from './dashboardView.js'

test('refresh interval satisfies the one-minute criterion', () => {
  assert.ok(REFRESH_INTERVAL_MS <= 60_000)
})

test('missing or empty AI block renders N/A and zeros, never NaN', () => {
  for (const data of [undefined, {}, { ai_effectiveness: null }]) {
    const view = aiEffectivenessView(data)
    assert.equal(view.shareLabel, 'N/A')
    assert.equal(view.acceptanceLabel, 'N/A')
    assert.equal(view.hasSales, false)
    assert.equal(view.revenue, 0)
    assert.deepEqual(view.sources.map((s) => s.count), [0, 0, 0])
  }
})

test('attributed sales are passed through from the backend without recomputation', () => {
  const view = aiEffectivenessView({
    ai_acceptance_rate: null,
    ai_effectiveness: {
      state: 'ATTRIBUTION_ESTIMATE',
      attributed_order_count: 2,
      attributed_item_count: 3,
      attributed_revenue: 250,
      revenue_share_percent: 12.5,
      source_breakdown: { LLM: 1, CACHE: 1, RULE: 1 },
    },
  })
  assert.equal(view.hasSales, true)
  assert.equal(view.shareLabel, '12.5%')
  assert.equal(view.revenue, 250)
  assert.deepEqual(view.sources.map((s) => s.count), [1, 1, 1])
})

test('data age handles bad timestamps', () => {
  assert.equal(dataAgeSeconds('2026-11-20T10:00:00Z', Date.parse('2026-11-20T10:00:42Z')), 42)
  assert.equal(dataAgeSeconds('garbage'), null)
  assert.equal(dataAgeSeconds('2026-11-20T10:00:10Z', Date.parse('2026-11-20T10:00:00Z')), 0)
})
