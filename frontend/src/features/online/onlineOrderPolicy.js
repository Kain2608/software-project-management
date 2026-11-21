export const ONLINE_ORDER_BLOCKERS = Object.freeze({
  AUTH: 'CUSTOMER_IDENTITY_CONTRACT_PENDING',
  PAYMENT: 'ONLINE_PAYMENT_CALLBACK_PENDING',
  API: 'ONLINE_ORDER_HTTP_CONTRACT_PENDING',
})

export function evaluateOnlineCheckout({ accessToken, storeId, warehouseId, cartItems }) {
  const configured = Boolean(accessToken && storeId && warehouseId)
  return {
    catalogEnabled: configured,
    checkoutEnabled: false,
    hasItems: Array.isArray(cartItems) && cartItems.length > 0,
    blockers: configured
      ? [ONLINE_ORDER_BLOCKERS.AUTH, ONLINE_ORDER_BLOCKERS.PAYMENT, ONLINE_ORDER_BLOCKERS.API]
      : ['AUTHENTICATION_REQUIRED', ONLINE_ORDER_BLOCKERS.AUTH,
          ONLINE_ORDER_BLOCKERS.PAYMENT, ONLINE_ORDER_BLOCKERS.API],
  }
}
