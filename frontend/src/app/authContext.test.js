import test from 'node:test'
import assert from 'node:assert/strict'

import {
  rejectUrlSuppliedCredentials,
  resolveAuthSession,
} from './authContext.js'

test('rejectUrlSuppliedCredentials rejects all URL-supplied token parameters', () => {
  const forbiddenCases = [
    { query: '?token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.dummy', param: 'token' },
    { query: '?access_token=secret_token_123', param: 'access_token' },
    { query: '?jwt=header.payload.sig', param: 'jwt' },
    { query: '?id_token=sample_id_token', param: 'id_token' },
    { query: '?bearer=some_bearer_value', param: 'bearer' },
    { query: '?auth=auth_code_or_token', param: 'auth' },
    { query: '?authorization=Bearer_token', param: 'authorization' },
    { query: '?sasms_token=custom_token', param: 'sasms_token' },
    { query: 'token=isolated_token', param: 'token' },
    { query: '?screen=pos&token=leaked_via_url', param: 'token' },
  ]

  for (const tc of forbiddenCases) {
    const result = rejectUrlSuppliedCredentials(tc.query)
    assert.strictEqual(
      result.rejected,
      true,
      `Expected ${tc.query} to be rejected due to parameter '${tc.param}'`,
    )
    assert.strictEqual(
      result.violation,
      'SECURITY_VIOLATION_URL_CREDENTIAL_PROHIBITED',
    )
    assert.ok(
      result.message.includes(tc.param),
      `Expected error message to mention parameter '${tc.param}'`,
    )
  }
})

test('rejectUrlSuppliedCredentials accepts benign query strings without tokens', () => {
  const benignCases = [
    '',
    null,
    undefined,
    '?screen=pos',
    '?tab=cart&lang=vi',
    new URLSearchParams('view=receipt&order=SO-2026-0001'),
  ]

  for (const input of benignCases) {
    const result = rejectUrlSuppliedCredentials(input)
    assert.strictEqual(result.rejected, false)
    assert.strictEqual(result.violation, null)
    assert.strictEqual(result.message, null)
  }
})

test('resolveAuthSession returns strictly unauthenticated fail-closed state when no session is provided', () => {
  const emptyCases = [null, undefined, {}, false, '']

  for (const input of emptyCases) {
    const session = resolveAuthSession(input)
    assert.strictEqual(session.hasClientToken, false)
    assert.strictEqual(session.isBackendVerified, false)
    assert.strictEqual(session.accessToken, null)
    assert.strictEqual(session.storeId, null)
    assert.strictEqual(session.warehouseId, null)
    assert.strictEqual(session.status, 'NO_SESSION')
  }
})

test('resolveAuthSession rejects whitespace-only or non-string tokens and fails closed', () => {
  const invalidTokens = [
    { accessToken: '   ' },
    { accessToken: '' },
    { accessToken: 12345 },
    { accessToken: null, storeId: 'store-1', warehouseId: 'wh-1' },
  ]

  for (const input of invalidTokens) {
    const session = resolveAuthSession(input)
    assert.strictEqual(session.hasClientToken, false)
    assert.strictEqual(session.isBackendVerified, false)
    assert.strictEqual(session.accessToken, null)
    assert.strictEqual(session.storeId, null)
    assert.strictEqual(session.warehouseId, null)
    assert.strictEqual(session.status, 'NO_SESSION')
  }
})

test('resolveAuthSession rejects fake, demo or mock tokens', () => {
  const fakeTokens = [
    { accessToken: 'mock-jwt' },
    { accessToken: 'demo-token' },
    { accessToken: 'fake-token' },
    { accessToken: 'test-token' },
    { accessToken: 'dummy-token' },
  ]

  for (const input of fakeTokens) {
    const session = resolveAuthSession(input)
    assert.strictEqual(session.hasClientToken, false)
    assert.strictEqual(session.isBackendVerified, false)
    assert.strictEqual(session.accessToken, null)
    assert.strictEqual(session.status, 'REJECTED_FAKE_TOKEN')
  }
})

test('resolveAuthSession treats client token as unverified pending backend verification', () => {
  const validSessionConfig = {
    accessToken: 'genuine.inmemory.jwt.token',
    storeId: '019318b2-b13c-7c01-8b21-4fca67d71001',
    warehouseId: '019318b2-b13c-7c01-8b21-4fca67d72002',
  }

  const session = resolveAuthSession(validSessionConfig)
  assert.strictEqual(session.hasClientToken, true)
  // CRITICAL: Possession of client token string is NOT proof of authentication.
  // Backend Spring Security & PostgreSQL assert_permission make the authoritative determination.
  assert.strictEqual(session.isBackendVerified, false)
  assert.strictEqual(session.accessToken, 'genuine.inmemory.jwt.token')
  assert.strictEqual(session.storeId, '019318b2-b13c-7c01-8b21-4fca67d71001')
  assert.strictEqual(session.warehouseId, '019318b2-b13c-7c01-8b21-4fca67d72002')
  assert.strictEqual(session.status, 'CLIENT_TOKEN_PRESENT_PENDING_BACKEND_VERIFICATION')
})

test('regression: URL query strings cannot elevate or self-authenticate a session', () => {
  // Even if an attacker places ?token=evil or ?store_id=evil in window.location,
  // resolveAuthSession does NOT parse window.location or URL parameters.
  // When the app initializes without an IdP context, it strictly remains unauthenticated.
  const unauthenticatedInit = resolveAuthSession(null)
  assert.strictEqual(unauthenticatedInit.hasClientToken, false)
  assert.strictEqual(unauthenticatedInit.isBackendVerified, false)
  assert.strictEqual(unauthenticatedInit.accessToken, null)

  // Verify rejectUrlSuppliedCredentials flags any attempt to supply token in URL
  const attackUrl = 'https://pos.sasms.local/?token=forged_jwt_token_12345&store_id=unauthorized_store'
  const urlObj = new URL(attackUrl)
  const rejection = rejectUrlSuppliedCredentials(urlObj.search)
  assert.strictEqual(rejection.rejected, true)
  assert.strictEqual(rejection.violation, 'SECURITY_VIOLATION_URL_CREDENTIAL_PROHIBITED')
})
