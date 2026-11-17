export function createPosContext({ accessToken = null, storeId = null, warehouseId = null } = {}) {
  return { accessToken, storeId, warehouseId }
}

export function isSamePosContext(left, right) {
  return left?.accessToken === right?.accessToken &&
    left?.storeId === right?.storeId &&
    left?.warehouseId === right?.warehouseId
}

export function retainCartForContext(items, previousContext, currentContext) {
  return isSamePosContext(previousContext, currentContext) ? items : []
}

export function shouldInvalidateCartForApiState(state) {
  return state === 'auth-required'
}

export function isRequestCurrentForContext(gate, request, requestContext, currentContext) {
  return gate.isCurrent(request) && isSamePosContext(requestContext, currentContext)
}

export function createLatestRequestGate(createController = () => new AbortController()) {
  let generation = 0
  let controller = null

  return {
    start() {
      controller?.abort()
      controller = createController()
      generation += 1
      return { generation, signal: controller.signal }
    },
    invalidate() {
      generation += 1
      controller?.abort()
      controller = null
    },
    isCurrent(request) {
      return request?.generation === generation && request.signal?.aborted !== true
    },
  }
}
