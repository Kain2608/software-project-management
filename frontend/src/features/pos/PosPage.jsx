import { useState } from 'react'

import { classifyPosError, getPosStatus, getReceipt } from './posApi.js'
import StatusCard from '../../shared/components/StatusCard.jsx'

function shortIdentifier(value) {
  if (typeof value !== 'string' || value.length < 16) return value ?? '—'
  return `${value.slice(0, 8)}…${value.slice(-6)}`
}

function formatMinor(value) {
  if (!Number.isSafeInteger(value)) return '—'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })
    .format(value)
}

export default function PosPage({ accessToken = null }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [orderId, setOrderId] = useState('')
  const [receipt, setReceipt] = useState(null)
  const [receiptState, setReceiptState] = useState('idle')
  const [receiptMessage, setReceiptMessage] = useState('')
  const authenticated = typeof accessToken === 'string' && accessToken.length > 0

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
    setReceipt(null)
    if (!authenticated) {
      setReceiptState('auth-required')
      setReceiptMessage('Cần phiên JWT hợp lệ trước khi đọc hóa đơn.')
      return
    }
    setReceiptState('loading')
    setReceiptMessage('Đang tải dữ liệu hóa đơn đã thanh toán…')
    try {
      setReceipt(await getReceipt(orderId, accessToken))
      setReceiptState('success')
      setReceiptMessage('Đã tải bản xem trước. Đây không phải bằng chứng hóa đơn điện tử đã phát hành.')
    } catch (requestError) {
      const classified = classifyPosError(requestError)
      setReceiptState(classified.state)
      setReceiptMessage(classified.message)
    }
  }

  return (
    <section aria-labelledby="pos-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">MODULE 2 · POS</p>
          <h2 id="pos-title">Bán hàng và hóa đơn</h2>
        </div>
        <button type="button" onClick={checkBackend}>Kiểm tra Backend</button>
      </div>

      <div className="pos-scope-note">
        Checkout và xác nhận thanh toán đang chờ hợp đồng Sprint 2. Màn hình này chỉ đọc hóa đơn của đơn PAID và mở hộp thoại in của trình duyệt.
      </div>

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

      {receipt && (
        <article className="receipt-preview" aria-label="Bản xem trước hóa đơn">
          <header>
            <div><strong>SASMS</strong><span>HÓA ĐƠN BÁN HÀNG</span></div>
            <small>Đơn: {shortIdentifier(receipt.order_id)}</small>
          </header>
          <dl>
            <div><dt>Thanh toán lúc</dt><dd>{new Date(receipt.paid_at).toLocaleString('vi-VN')}</dd></div>
            <div><dt>Tổng tiền</dt><dd>{formatMinor(receipt.total_minor)}</dd></div>
          </dl>
          <ul>
            {receipt.items.map((item) => (
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

      {data && <StatusCard title={data.module} status={data.status} note={data.note} />}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
