import { useEffect, useMemo, useRef, useState } from 'react'

import {
  classifyPosError,
  completeCashPayment,
  createDraftOrder,
  getPosStatus,
  getReceipt,
  reserveOrder,
  selectDevice,
} from './posApi.js'
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

const vnd = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })

// Store/warehouse/cart must come from the real authenticated checkout flow.
export default function PosPage({ accessToken = null, storeId = null, warehouseId = null, cartItems: providedCartItems = null, onCartItemsChange = null }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [orderId, setOrderId] = useState('')
  const [receipt, setReceipt] = useState(null)
  const [receiptState, setReceiptState] = useState('idle')
  const [receiptMessage, setReceiptMessage] = useState('')
  const [localCartItems, setLocalCartItems] = useState([])
  const [cartMessage, setCartMessage] = useState('')

  // Checkout modal and multi-step flow
  const [checkoutModalOpen, setCheckoutModalOpen] = useState(false)
  const [checkoutStep, setCheckoutStep] = useState('idle') // 'idle' | 'draft' | 'device_selection' | 'reserving' | 'payment' | 'paying' | 'completed' | 'error'
  const [draftOrder, setDraftOrder] = useState(null)
  const [serialInputs, setSerialInputs] = useState({})
  const [deviceLocks, setDeviceLocks] = useState({})
  const [paidResult, setPaidResult] = useState(null)
  const [completedReceipt, setCompletedReceipt] = useState(null)
  const [checkoutError, setCheckoutError] = useState('')
  const [cashAmountInput, setCashAmountInput] = useState('')

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

  async function startCheckout() {
    if (!authenticated || !storeId || !warehouseId) {
      setCartMessage('Cần phiên đăng nhập, cửa hàng và kho bán hàng hợp lệ để tạo đơn.')
      return
    }
    if (cartItems.length === 0) {
      setCartMessage('Giỏ hàng đang trống.')
      return
    }
    setCheckoutModalOpen(true)
    setCheckoutStep('draft')
    setCheckoutError('')
    setDraftOrder(null)
    setSerialInputs({})
    setDeviceLocks({})
    setPaidResult(null)
    setCompletedReceipt(null)

    try {
      const idempotencyKey = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : null
      const order = await createDraftOrder(
        {
          storeId,
          warehouseId,
          items: cartItems,
          idempotencyKey,
        },
        accessToken,
      )
      setDraftOrder(order)
      setCashAmountInput(String(order.total_amount ?? ''))
      setCheckoutStep('device_selection')
    } catch (err) {
      const classified = classifyPosError(err)
      setCheckoutError(classified.message)
      setCheckoutStep('error')
    }
  }

  async function handleLockSerial(orderItemId) {
    const serial = serialInputs[orderItemId]?.trim()
    if (!serial) {
      setCheckoutError('Vui lòng nhập số Serial / IMEI của thiết bị.')
      return
    }
    setCheckoutError('')
    try {
      const orderId = draftOrder.id || draftOrder.order_id
      const res = await selectDevice(
        {
          orderId,
          orderItemId,
          storeId,
          serial,
        },
        accessToken,
      )
      setDeviceLocks((prev) => ({
        ...prev,
        [orderItemId]: { deviceId: res.device_id, status: res.status || 'SELECTED', serial },
      }))
    } catch (err) {
      const classified = classifyPosError(err)
      setCheckoutError(classified.message)
    }
  }

  async function handleReserveOrder() {
    setCheckoutError('')
    setCheckoutStep('reserving')
    try {
      const orderId = draftOrder.id || draftOrder.order_id
      await reserveOrder(orderId, accessToken)
      setCheckoutStep('payment')
    } catch (err) {
      const classified = classifyPosError(err)
      setCheckoutError(classified.message)
      setCheckoutStep('device_selection')
    }
  }

  async function handleCompleteCashPayment() {
    setCheckoutError('')
    setCheckoutStep('paying')
    try {
      const orderId = draftOrder.id || draftOrder.order_id
      const amount = Number(cashAmountInput) || draftOrder.total_amount
      const paid = await completeCashPayment({ orderId, amount }, accessToken)
      setPaidResult(paid)

      // Automatically fetch authoritative receipt
      try {
        const rcpt = await getReceipt(orderId, accessToken)
        setCompletedReceipt(rcpt)
      } catch {
        setCompletedReceipt({
          order_id: orderId,
          paid_at: paid.captured_at || new Date().toISOString(),
          total_minor: Math.round(Number(paid.amount) * 100),
          lookup_qr_token: null,
          items: [],
        })
      }

      // Order completed: clear cart
      changeCart([])
      setCheckoutStep('completed')
    } catch (err) {
      const classified = classifyPosError(err)
      setCheckoutError(classified.message)
      setCheckoutStep('payment')
    }
  }

  function closeCheckout() {
    setCheckoutModalOpen(false)
    setCheckoutStep('idle')
    setDraftOrder(null)
    setCheckoutError('')
  }

  function handlePrintReceipt() {
    window.print()
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
        onCheckout={startCheckout}
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
              {visibleReceipt.lookup_qr_token && (
                <div className="receipt-preview__warranty-qr">
                  <p className="receipt-preview__qr-label">QR BẢO HÀNH THIẾT BỊ</p>
                  <div className="receipt-preview__qr-code">{visibleReceipt.lookup_qr_token}</div>
                  <small>Quét mã để tra cứu tình trạng bảo hành chính hãng</small>
                </div>
              )}
              <button type="button" onClick={() => window.print()}>Mở bản xem trước in</button>
              <small>Đóng hoặc lỗi hộp thoại in không làm thay đổi trạng thái đơn hàng.</small>
            </article>
          )}
        </section>
      </div>

      <p className="pos-scope-note">Quy trình Checkout, giữ Serial/IMEI và thanh toán tiền mặt được xác thực nguyên tử qua backend PostgreSQL (Order-First).</p>

      {checkoutModalOpen && (
        <div className="pos-checkout-backdrop" role="dialog" aria-modal="true" aria-labelledby="pos-checkout-title">
          <div className="pos-checkout-dialog">
            <header className="pos-checkout-dialog__header">
              <div>
                <p className="eyebrow">S2-02 · QUY TRÌNH THANH TOÁN</p>
                <h3 id="pos-checkout-title">Thanh toán đơn hàng tại quầy</h3>
                <p>Order-First: DRAFT → Gán Serial → Reserve → Thu tiền mặt (PAID)</p>
              </div>
              <button type="button" className="pos-checkout-dialog__close" onClick={closeCheckout} aria-label="Đóng">&times;</button>
            </header>

            <nav className="pos-checkout-steps" aria-label="Các bước thanh toán">
              <span className={`pos-checkout-step${checkoutStep === 'draft' ? ' pos-checkout-step--active' : ['device_selection', 'reserving', 'payment', 'paying', 'completed'].includes(checkoutStep) ? ' pos-checkout-step--done' : ''}`}>
                1. Tạo nháp
              </span>
              <span className={`pos-checkout-step${checkoutStep === 'device_selection' ? ' pos-checkout-step--active' : ['reserving', 'payment', 'paying', 'completed'].includes(checkoutStep) ? ' pos-checkout-step--done' : ''}`}>
                2. Gán Serial
              </span>
              <span className={`pos-checkout-step${checkoutStep === 'reserving' ? ' pos-checkout-step--active' : ['payment', 'paying', 'completed'].includes(checkoutStep) ? ' pos-checkout-step--done' : ''}`}>
                3. Giữ hàng
              </span>
              <span className={`pos-checkout-step${['payment', 'paying'].includes(checkoutStep) ? ' pos-checkout-step--active' : checkoutStep === 'completed' ? ' pos-checkout-step--done' : ''}`}>
                4. Tiền mặt
              </span>
              <span className={`pos-checkout-step${checkoutStep === 'completed' ? ' pos-checkout-step--active' : ''}`}>
                5. Hóa đơn / QR
              </span>
            </nav>

            <div className="pos-checkout-dialog__body">
              {checkoutError && (
                <div className="pos-cart__notice" style={{ background: '#fdf2f2', color: '#9b2c2c', borderColor: '#f8b4b4' }} role="alert">
                  <strong>Lỗi xử lý:</strong> {checkoutError}
                </div>
              )}

              {checkoutStep === 'draft' && (
                <div style={{ textAlign: 'center', padding: '32px 0' }}>
                  <p>Đang tạo đơn hàng nháp (DRAFT) với giá chuẩn từ Catalog...</p>
                </div>
              )}

              {checkoutStep === 'device_selection' && draftOrder && (
                <>
                  <div className="pos-checkout-order-summary">
                    <div>
                      <span>Mã đơn hàng: <strong>{draftOrder.code}</strong></span>
                      <small style={{ display: 'block', color: '#647a91' }}>ID: {shortIdentifier(draftOrder.id || draftOrder.order_id)}</small>
                    </div>
                    <div>
                      <span>Tổng tiền: <strong>{vnd.format(draftOrder.total_amount)}</strong></span>
                    </div>
                  </div>

                  <h4>Chọn thiết bị (Serial / IMEI)</h4>
                  <p style={{ margin: 0, fontSize: '12px', color: '#647a91' }}>
                    Nhập Serial hoặc IMEI cho từng dòng sản phẩm để khóa thiết bị vào đơn nháp trước khi giữ hàng.
                  </p>

                  <div className="pos-serial-list">
                    {(draftOrder.items || []).map((item, idx) => {
                      const itemId = item.order_item_id || item.id || `item-${idx}`
                      const lock = deviceLocks[itemId]
                      return (
                        <div key={itemId} className="pos-serial-item">
                          <div className="pos-serial-item__header">
                            <strong>{item.name || `Sản phẩm ${shortIdentifier(item.product_id)}`} (SL: {item.quantity})</strong>
                            <span>{vnd.format(item.line_total || item.unit_price * item.quantity)}</span>
                          </div>
                          <div className="pos-serial-item__controls">
                            <input
                              type="text"
                              placeholder="Nhập Serial hoặc IMEI (VD: S2-SER-0001)"
                              value={serialInputs[itemId] || ''}
                              onChange={(e) => {
                                const val = e.target.value
                                setSerialInputs((prev) => ({ ...prev, [itemId]: val }))
                              }}
                              disabled={Boolean(lock)}
                            />
                            <button
                              type="button"
                              className="pos-checkout-btn-secondary"
                              onClick={() => handleLockSerial(itemId)}
                              disabled={Boolean(lock)}
                            >
                              {lock ? 'Đã gán' : 'Gán Serial'}
                            </button>
                            {lock ? (
                              <span className="pos-serial-badge pos-serial-badge--selected">ĐÃ KHÓA ({lock.serial})</span>
                            ) : (
                              <span className="pos-serial-badge pos-serial-badge--pending">Chờ Serial</span>
                            )}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </>
              )}

              {checkoutStep === 'reserving' && (
                <div style={{ textAlign: 'center', padding: '32px 0' }}>
                  <p>Đang thực hiện giữ hàng (RESERVE) và khóa thiết bị 10 phút...</p>
                </div>
              )}

              {['payment', 'paying'].includes(checkoutStep) && draftOrder && (
                <>
                  <div className="pos-checkout-order-summary">
                    <div>
                      <span>Đơn hàng đã giữ: <strong>{draftOrder.code}</strong></span>
                      <small style={{ display: 'block', color: '#276729' }}>Trạng thái: RESERVED</small>
                    </div>
                    <div>
                      <span>Cần thanh toán: <strong>{vnd.format(draftOrder.total_amount)}</strong></span>
                    </div>
                  </div>

                  <div className="pos-cash-confirm">
                    <h4>Xác nhận thanh toán tiền mặt (Cash)</h4>
                    <p style={{ margin: 0, fontSize: '12px', color: '#647a91' }}>
                      Theo quy định hệ thống, số tiền thu thực tế phải bằng chính xác tổng giá trị đơn hàng.
                    </p>
                    <label>
                      Số tiền mặt thu từ khách (VNĐ)
                      <input
                        type="number"
                        value={cashAmountInput}
                        onChange={(e) => setCashAmountInput(e.target.value)}
                        placeholder="Số tiền thực thu"
                      />
                    </label>
                  </div>
                </>
              )}

              {checkoutStep === 'completed' && paidResult && (
                <>
                  <div className="pos-cart__notice" style={{ background: '#e2f0d9', color: '#276729', borderColor: '#b2d8a4' }}>
                    <strong>Thanh toán thành công!</strong> Đơn hàng đã chuyển sang trạng thái PAID, thiết bị chuyển sang SOLD.
                  </div>

                  {completedReceipt && (
                    <article className="receipt-preview" aria-label="Bản xem trước hóa đơn đã thanh toán">
                      <header>
                        <div><strong>SASMS</strong><span>HÓA ĐƠN BÁN HÀNG</span></div>
                        <small>Mã đơn: {draftOrder?.code || shortIdentifier(paidResult.order_id)}</small>
                        <small>Số hóa đơn: {paidResult.invoice_number}</small>
                      </header>
                      <dl>
                        <div><dt>Phương thức</dt><dd>TIỀN MẶT (CASH)</dd></div>
                        <div><dt>Thời gian</dt><dd>{new Date(paidResult.captured_at || Date.now()).toLocaleString('vi-VN')}</dd></div>
                        <div><dt>Đã thanh toán</dt><dd>{vnd.format(paidResult.amount)}</dd></div>
                      </dl>
                      {completedReceipt.items && completedReceipt.items.length > 0 && (
                        <ul>
                          {completedReceipt.items.map((item) => (
                            <li key={item.device_id}>
                              <span>{shortIdentifier(item.sku_id)} · {item.serial_masked}</span>
                              <strong>{formatMinor(item.price_minor)}</strong>
                            </li>
                          ))}
                        </ul>
                      )}
                      {completedReceipt.lookup_qr_token && (
                        <div className="receipt-preview__warranty-qr">
                          <p className="receipt-preview__qr-label">QR BẢO HÀNH THIẾT BỊ</p>
                          <div className="receipt-preview__qr-code">{completedReceipt.lookup_qr_token}</div>
                          <small>Quét mã bằng camera hoặc máy đọc QR để tra cứu bảo hành</small>
                        </div>
                      )}
                    </article>
                  )}
                </>
              )}
            </div>

            <footer className="pos-checkout-dialog__footer">
              {checkoutStep === 'device_selection' && (
                <>
                  <button type="button" className="pos-checkout-btn-secondary" onClick={closeCheckout}>
                    Hủy bỏ
                  </button>
                  <button type="button" className="pos-checkout-btn-primary" onClick={handleReserveOrder}>
                    Xác nhận giữ hàng (Reserve)
                  </button>
                </>
              )}

              {checkoutStep === 'payment' && (
                <>
                  <button type="button" className="pos-checkout-btn-secondary" onClick={() => setCheckoutStep('device_selection')}>
                    Quay lại
                  </button>
                  <button type="button" className="pos-checkout-btn-primary" onClick={handleCompleteCashPayment}>
                    Xác nhận thu đủ tiền & Hoàn tất bán
                  </button>
                </>
              )}

              {checkoutStep === 'paying' && (
                <button type="button" className="pos-checkout-btn-primary" disabled>
                  Đang xử lý giao dịch...
                </button>
              )}

              {checkoutStep === 'completed' && (
                <>
                  <button type="button" className="pos-checkout-btn-secondary" onClick={handlePrintReceipt}>
                    In hóa đơn (80mm)
                  </button>
                  <button type="button" className="pos-checkout-btn-primary" onClick={closeCheckout}>
                    Đơn hàng mới
                  </button>
                </>
              )}

              {checkoutStep === 'error' && (
                <button type="button" className="pos-checkout-btn-primary" onClick={closeCheckout}>
                  Đóng
                </button>
              )}
            </footer>
          </div>
        </div>
      )}
      {data && <StatusCard title={data.module} status={data.status} note={data.note} />}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
