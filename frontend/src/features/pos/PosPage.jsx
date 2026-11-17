import { useEffect, useMemo, useRef, useState } from 'react'

import { classifyPosError, getPosStatus, getReceipt } from './posApi.js'
import CrossSellWidget from './CrossSellWidget.jsx'
import PosCart from './PosCart.jsx'
import { addCartItem, toCrossSellCartItems } from './posCartModel.js'
import {
  createLatestRequestGate,
  createPosContext,
  isRequestCurrentForContext,
  isSamePosContext,
  retainCartForContext,
} from './posCartContext.js'
import StatusCard from '../../shared/components/StatusCard.jsx'

function shortIdentifier(value) {
  if (typeof value !== 'string' || value.length < 16) return value ?? '—'
  return `${value.slice(0, 8)}…${value.slice(-6)}`
}

function formatMinor(value) {
  if (!Number.isSafeInteger(value)) return '—'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value)
}

// Store/warehouse/cart must come from the real authenticated checkout flow.
// App.jsx does not yet supply these; the AI widget remains read-only and idle until integrated.
export default function PosPage({ accessToken = null, storeId = null, warehouseId = null, cartItems: providedCartItems = null, onCartItemsChange = null }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [orderId, setOrderId] = useState('')
  const [receipt, setReceipt] = useState(null)
  const [receiptState, setReceiptState] = useState('idle')
  const [receiptMessage, setReceiptMessage] = useState('')
  const [localCartItems, setLocalCartItems] = useState([])
  const [cartMessage, setCartMessage] = useState('')
  const authenticated = typeof accessToken === 'string' && accessToken.trim().length > 0
  const controlled = Array.isArray(providedCartItems) && typeof onCartItemsChange === 'function'
  const currentContext = createPosContext({ accessToken, storeId, warehouseId })
  const previousContext = useRef(currentContext)
  const latestContext = useRef(currentContext)
  const receiptContext = useRef(null)
  const receiptRequestGate = useRef(null)
  if (receiptRequestGate.current === null) receiptRequestGate.current = createLatestRequestGate()
  latestContext.current = currentContext
  const storedCartItems = controlled ? providedCartItems : localCartItems
  const cartItems = retainCartForContext(storedCartItems, previousContext.current, currentContext)
  const visibleReceipt = isSamePosContext(receiptContext.current, currentContext) ? receipt : null
  const crossSellCartItems = useMemo(() => toCrossSellCartItems(cartItems), [cartItems])

  useEffect(() => {
    if (isSamePosContext(previousContext.current, currentContext)) return
    previousContext.current = currentContext
    if (controlled) onCartItemsChange([])
    else setLocalCartItems([])
    receiptRequestGate.current.invalidate()
    receiptContext.current = null
    setCartMessage('Phiên, cửa hàng hoặc kho đã thay đổi. Giỏ cục bộ cũ đã được xóa để kiểm tra lại tồn kho.')
    setReceipt(null)
    setReceiptState('idle')
    setReceiptMessage('')
  }, [accessToken, storeId, warehouseId, controlled, onCartItemsChange])

  useEffect(() => () => receiptRequestGate.current.invalidate(), [])

  function changeCart(nextItems) {
    if (controlled) onCartItemsChange?.(nextItems)
    else setLocalCartItems(nextItems)
  }

  function addVerifiedSuggestion(suggestion) {
    try {
      changeCart(addCartItem(cartItems, suggestion))
      setCartMessage(`Đã thêm ${suggestion.name} vào giỏ cục bộ. Giá và tồn sẽ được kiểm tra lại khi tạo order.`)
    } catch {
      setCartMessage('Không thể thêm gợi ý vì dữ liệu không hợp lệ hoặc đã vượt tồn có thể bán.')
    }
  }

  async function checkBackend() {
    try {
      setError('')
      setData(await getPosStatus())
    } catch {
      setData(null)
      setError('Không kết nối được Backend POS. Hãy kiểm tra Spring Boot đang chạy ở cổng 8080.')
    }
  }

  async function loadReceipt(event) {
    event.preventDefault()
    receiptRequestGate.current.invalidate()
    receiptContext.current = null
    setReceipt(null)
    if (!authenticated) {
      setReceiptState('auth-required')
      setReceiptMessage('Cần phiên JWT hợp lệ trước khi đọc hóa đơn.')
      return
    }
    const requestContext = createPosContext({ accessToken, storeId, warehouseId })
    const request = receiptRequestGate.current.start()
    setReceiptState('loading')
    setReceiptMessage('Đang tải dữ liệu hóa đơn đã thanh toán…')
    try {
      const loadedReceipt = await getReceipt(orderId, accessToken, request.signal)
      if (!isRequestCurrentForContext(receiptRequestGate.current, request, requestContext, latestContext.current)) return
      receiptContext.current = requestContext
      setReceipt(loadedReceipt)
      setReceiptState('success')
      setReceiptMessage('Đã tải bản xem trước. Đây không phải bằng chứng hóa đơn điện tử đã phát hành.')
    } catch (requestError) {
      if (!isRequestCurrentForContext(receiptRequestGate.current, request, requestContext, latestContext.current)) return
      const classified = classifyPosError(requestError)
      setReceiptState(classified.state)
      setReceiptMessage(classified.message)
    }
  }

  return (
    <section className="pos-module" aria-labelledby="pos-title">
      <div className="pos-module__utilities">
        <div>
          <p className="eyebrow">MODULE 2 · POS</p>
          <h2 id="pos-title">Chọn sản phẩm và lập giỏ hàng</h2>
        </div>
        <button type="button" onClick={checkBackend}>Kiểm tra Backend</button>
      </div>

      <div className={`pos-business-state${cartItems.length ? ' pos-business-state--active' : ''}`} role="status">
        <strong>{cartItems.length ? `Giỏ hàng có ${cartItems.length} dòng sản phẩm` : 'Giỏ hàng đang trống'}</strong>
        <span>{cartItems.length
          ? 'Giỏ này là bản nháp tại trình duyệt; backend phải xác nhận lại giá, tồn và Serial trước khi tạo đơn.'
          : 'Chọn sản phẩm và thiết bị để bắt đầu. Truy cập Catalog/Stock yêu cầu phiên JWT và kho bán hàng hợp lệ.'}</span>
      </div>

      <PosCart
        accessToken={accessToken}
        storeId={storeId}
        warehouseId={warehouseId}
        items={cartItems}
        onItemsChange={changeCart}
        message={cartMessage}
        onMessageChange={setCartMessage}
      />

      <div className="pos-followup">
        <CrossSellWidget
          accessToken={accessToken}
          storeId={storeId}
          warehouseId={warehouseId}
          cartItems={crossSellCartItems}
          onReviewSuggestion={addVerifiedSuggestion}
        />

        <section className="pos-receipt-panel" aria-labelledby="pos-receipt-title">
          <h3 id="pos-receipt-title" className="pos-receipt-panel__heading">Tra cứu hóa đơn</h3>
          <p className="pos-receipt-panel__description">Xem hóa đơn của đơn PAID trong phạm vi được cấp quyền. In bằng hộp thoại trình duyệt.</p>
          <form className="receipt-form" onSubmit={loadReceipt}>
            <label htmlFor="receipt-order-id">
              Mã UUID đơn hàng
              <input
                id="receipt-order-id"
                value={orderId}
                onChange={(event) => setOrderId(event.target.value)}
                placeholder="00000000-0000-0000-0000-000000000000"
                required
              />
            </label>
            <button type="submit" disabled={receiptState === 'loading'}>
              {receiptState === 'loading' ? 'Đang tải…' : 'Xem hóa đơn'}
            </button>
          </form>

          {receiptMessage && <p className={`receipt-message receipt-message--${receiptState}`}>{receiptMessage}</p>}

          {visibleReceipt && (
            <article className="receipt-preview" aria-label="Bản xem trước hóa đơn">
              <header>
                <div><strong>SASMS</strong><span>HÓA ĐƠN BÁN HÀNG</span></div>
                <small>Đơn: {shortIdentifier(visibleReceipt.order_id)}</small>
              </header>
              <dl>
                <div><dt>Thanh toán lúc</dt><dd>{new Date(visibleReceipt.paid_at).toLocaleString('vi-VN')}</dd></div>
                <div><dt>Tổng tiền</dt><dd>{formatMinor(visibleReceipt.total_minor)}</dd></div>
              </dl>
              <ul>
                {visibleReceipt.items.map((item) => (
                  <li key={item.device_id}>
                    <span>{shortIdentifier(item.sku_id)} · {item.serial_masked}</span>
                    <strong>{formatMinor(item.price_minor)}</strong>
                  </li>
                ))}
              </ul>
              <button type="button" onClick={() => window.print()}>Mở bản xem trước in</button>
              <small>Đóng hoặc lỗi hộp thoại in không làm thay đổi trạng thái đơn hàng.</small>
            </article>
          )}
        </section>
      </div>

      <p className="pos-scope-note">Checkout, giữ Serial và xác nhận thanh toán chưa khả dụng. Giao diện không tự tạo đơn, không đổi trạng thái SOLD hay cập nhật tồn kho.</p>
      {data && <StatusCard title={data.module} status={data.status} note={data.note} />}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
