const FORBIDDEN_URL_CREDENTIAL_PARAMS = [
  'token',
  'access_token',
  'jwt',
  'id_token',
  'bearer',
  'auth',
  'authorization',
  'sasms_token',
]

/**
 * Prohibits and detects tokens provided via URL query strings or hashes.
 * Passing credentials via URLs exposes them in browser history, HTTP Referer headers,
 * proxy logs, and shoulder surfing (OWASP Top 10 A02/A07).
 *
 * @param {string|URLSearchParams} searchInput - Query string or URLSearchParams
 * @returns {{ rejected: boolean, violation: string|null, message: string|null }}
 */
export function rejectUrlSuppliedCredentials(searchInput) {
  if (!searchInput) {
    return { rejected: false, violation: null, message: null }
  }

  let params
  try {
    if (typeof searchInput === 'string') {
      const cleanString = searchInput.startsWith('?') ? searchInput.slice(1) : searchInput
      params = new URLSearchParams(cleanString)
    } else if (searchInput instanceof URLSearchParams) {
      params = searchInput
    } else {
      return { rejected: false, violation: null, message: null }
    }
  } catch {
    return { rejected: false, violation: null, message: null }
  }

  for (const forbiddenKey of FORBIDDEN_URL_CREDENTIAL_PARAMS) {
    if (params.has(forbiddenKey)) {
      const value = params.get(forbiddenKey)
      if (typeof value === 'string' && value.trim().length > 0) {
        return {
          rejected: true,
          violation: 'SECURITY_VIOLATION_URL_CREDENTIAL_PROHIBITED',
          message: `Truyền token qua URL parameter '${forbiddenKey}' bị nghiêm cấm theo chính sách bảo mật SASMS (nguy cơ rò rỉ qua log/referrer).`,
        }
      }
    }
  }

  return { rejected: false, violation: null, message: null }
}

/**
 * Resolves an authenticated session strictly from in-memory IdP / OIDC configuration.
 *
 * Enforces fail-closed semantics:
 * - If no genuine IdP session is provided, session is strictly unauthenticated (accessToken = null).
 * - Never parses window.location.search or sessionStorage for access tokens.
 * - Store and warehouse scopes are only valid when accompanied by a verified accessToken.
 *
 * @param {object|null} sessionConfig - In-memory auth session from configured IdP
 * @returns {{ isAuthenticated: boolean, accessToken: string|null, storeId: string|null, warehouseId: string|null, status: string }}
 */
export function resolveAuthSession(sessionConfig = null) {
  if (!sessionConfig || typeof sessionConfig !== 'object') {
    return {
      isAuthenticated: false,
      accessToken: null,
      storeId: null,
      warehouseId: null,
      status: 'UNAUTHENTICATED',
    }
  }

  const rawToken = sessionConfig.accessToken
  const validToken = typeof rawToken === 'string' && rawToken.trim().length > 0 ? rawToken.trim() : null

  if (!validToken) {
    return {
      isAuthenticated: false,
      accessToken: null,
      storeId: null,
      warehouseId: null,
      status: 'UNAUTHENTICATED',
    }
  }

  const storeId = typeof sessionConfig.storeId === 'string' && sessionConfig.storeId.trim().length > 0
    ? sessionConfig.storeId.trim()
    : null
  const warehouseId = typeof sessionConfig.warehouseId === 'string' && sessionConfig.warehouseId.trim().length > 0
    ? sessionConfig.warehouseId.trim()
    : null

  return {
    isAuthenticated: true,
    accessToken: validToken,
    storeId,
    warehouseId,
    status: 'AUTHENTICATED',
  }
}
