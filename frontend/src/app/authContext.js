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

const FORBIDDEN_FAKE_TOKEN_PATTERNS = [
  'mock-jwt',
  'demo-token',
  'fake-token',
  'test-token',
  'dummy-token',
  'mock-token',
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
 * Resolves an in-memory client session candidate from IdP / OIDC configuration.
 *
 * Enforces strict Zero-Trust & Fail-Closed semantics:
 * 1. Không nhận JWT từ URL hoặc browser storage (localStorage, sessionStorage).
 * 2. Từ chối token giả lập (mock-jwt, demo-token...).
 * 3. Client KHÔNG tự nhận định là đã xác thực (isBackendVerified: false);
 *    việc sở hữu chuỗi token trong bộ nhớ chỉ là candidate token phía client (hasClientToken: true).
 * 4. Quyền thực thi (RBAC) và phạm vi cửa hàng/kho (Store & Warehouse Scope)
 *    HOÀN TOÀN do Backend Spring Security và PostgreSQL quyết định trên từng request.
 *
 * @param {object|null} sessionConfig - In-memory auth session from configured IdP
 * @returns {{ hasClientToken: boolean, isBackendVerified: boolean, accessToken: string|null, storeId: string|null, warehouseId: string|null, status: string }}
 */
export function resolveAuthSession(sessionConfig = null) {
  if (!sessionConfig || typeof sessionConfig !== 'object') {
    return {
      hasClientToken: false,
      isBackendVerified: false,
      accessToken: null,
      storeId: null,
      warehouseId: null,
      status: 'NO_SESSION',
    }
  }

  const rawToken = sessionConfig.accessToken
  const validToken = typeof rawToken === 'string' && rawToken.trim().length > 0 ? rawToken.trim() : null

  if (!validToken) {
    return {
      hasClientToken: false,
      isBackendVerified: false,
      accessToken: null,
      storeId: null,
      warehouseId: null,
      status: 'NO_SESSION',
    }
  }

  // Reject hardcoded fake/demo tokens
  const lowerToken = validToken.toLowerCase()
  if (FORBIDDEN_FAKE_TOKEN_PATTERNS.some(fake => lowerToken === fake || lowerToken.startsWith(fake))) {
    return {
      hasClientToken: false,
      isBackendVerified: false,
      accessToken: null,
      storeId: null,
      warehouseId: null,
      status: 'REJECTED_FAKE_TOKEN',
    }
  }

  const storeId = typeof sessionConfig.storeId === 'string' && sessionConfig.storeId.trim().length > 0
    ? sessionConfig.storeId.trim()
    : null
  const warehouseId = typeof sessionConfig.warehouseId === 'string' && sessionConfig.warehouseId.trim().length > 0
    ? sessionConfig.warehouseId.trim()
    : null

  return {
    hasClientToken: true,
    isBackendVerified: false, // Backend determines authentication & permission via Spring Security / PostgreSQL
    accessToken: validToken,
    storeId,
    warehouseId,
    status: 'CLIENT_TOKEN_PRESENT_PENDING_BACKEND_VERIFICATION',
  }
}
