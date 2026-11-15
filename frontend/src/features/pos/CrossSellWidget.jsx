import { useEffect, useState } from 'react'
import { getVerifiedCrossSell } from './aiPosApi.js'
import './aiPos.css'
import { classifyCrossSellError, isUuid } from './aiPosContract.js'

const vnd = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })

export default function CrossSellWidget({ accessToken, storeId, warehouseId, cartItems = [], onReviewSuggestion }) {
  const [state, setState] = useState({ status: 'idle', suggestions: [], message: '' })
  // The POS cart is not yet wired to the App/login. Recompute only when its meaningful data changes.
  const cartKey = JSON.stringify(Array.isArray(cartItems) ? cartItems.map((item) => [item.productId ?? item.product_id, item.sku]) : [])
  const ready = typeof accessToken === 'string' && accessToken.length > 0 &&
    isUuid(storeId) && isUuid(warehouseId) && Array.isArray(cartItems) && cartItems.length > 0

  useEffect(() => {
    if (!ready) {
      setState({ status: 'idle', suggestions: [], message: 'Chờ phiên đăng nhập, cửa hàng, kho và giỏ POS hợp lệ.' })
      return undefined
    }
    const controller = new AbortController()
    let disposed = false
    setState({ status: 'loading', suggestions: [], message: 'Đang xác minh gợi ý với Catalog và tồn kho…' })
    const timer = setTimeout(async () => {
      try {
        const result = await getVerifiedCrossSell({ accessToken, storeId, warehouseId, cartItems, signal: controller.signal })
        if (disposed) return
        setState({
          status: 'success',
          suggestions: result.suggestions,
          message: result.suggestions.length ? `Nguồn: ${result.source}. Giá và tồn kho đã đối chiếu qua API nghiệp vụ.` :
            'Chưa có gợi ý nào xác minh được giá, mã sản phẩm và tồn kho. Không tự thêm hàng giả vào POS.',
        })
      } catch (error) {
        if (!disposed) setState({ status: 'error', suggestions: [], message: classifyCrossSellError(error) })
      }
    }, 400)
    return () => {
      disposed = true
      clearTimeout(timer)
      controller.abort()
    }
  }, [ready, accessToken, storeId, warehouseId, cartKey])

  return (
    <div className="cross-sell" aria-label="Gợi ý AI bán kèm">
      <div className="cross-sell__heading">
        <div>
          <p className="eyebrow">AI CROSS-SELL · SPRINT 2</p>
          <h3>Gợi ý bán kèm</h3>
        </div>
        <span className="cross-sell__tag">Không ảnh hưởng thanh toán</span>
      </div>
      <p role="status" aria-live="polite" className="cross-sell__status">{state.message}</p>
      {state.status === 'success' && state.suggestions.length > 0 && (
        <ul className="cross-sell__list">
          {state.suggestions.map((item) => (
            <li className="cross-sell__item" key={item.productId}>
              <div>
                <strong>{item.name}</strong>
                <small>{item.sku} · {item.reason}</small>
                <span>Còn {item.availableQuantity} · {vnd.format(item.listPrice)}</span>
              </div>
              <button
                type="button"
                disabled={typeof onReviewSuggestion !== 'function'}
                title="Chỉ chuyển đề xuất đã đối chiếu cho luồng xác minh giỏ hàng; không tự ghi đơn hàng"
                onClick={() => onReviewSuggestion?.({ ...item, requiresServerValidation: true })}
              >
                Xem để thêm
              </button>
            </li>
          ))}
        </ul>
      )}
      <small>Không lấy giá, số tồn hoặc chính sách Serial từ AI làm căn cứ thanh toán. Phải kiểm tra lại khi tạo đơn/giữ hàng.</small>
    </div>
  )
}
