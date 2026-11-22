import { useCallback, useEffect, useRef, useState } from 'react'
import { classifyDashboardError, loadStoreDashboard } from './dashboardApi.js'
import { REFRESH_INTERVAL_MS, aiEffectivenessView, dataAgeSeconds } from './dashboardView.js'
import './dashboard.css'

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })

function utc(day, end = false) {
  if (!day) return ''
  const value = new Date(`${day}T00:00:00+07:00`)
  if (end) value.setUTCDate(value.getUTCDate() + 1)
  return value.toISOString()
}

const ERROR_MESSAGES = {
  auth: 'Cần phiên đăng nhập hợp lệ.',
  forbidden: 'Không có quyền reports.read tại cửa hàng này.',
  disabled: 'Dashboard đang tắt chờ duyệt hợp đồng.',
  error: 'Không thể tải dashboard.',
}

export default function StoreDashboardPage({ accessToken, storeId }) {
  const today = new Date().toISOString().slice(0, 10)
  const [from, setFrom] = useState(today.slice(0, 8) + '01')
  const [to, setTo] = useState(today)
  const [data, setData] = useState(null)
  const [message, setMessage] = useState('')
  const [age, setAge] = useState(null)
  const active = useRef(null) // the range that was last loaded successfully; drives auto-refresh

  useEffect(() => {
    active.current = null
    setData(null)
    setMessage('')
  }, [accessToken, storeId])

  const fetchData = useCallback(
    async (silent) => {
      const range = { from: utc(from), to: utc(to, true) }
      if (!silent) {
        setData(null)
        setMessage('Đang tải…')
      }
      try {
        const next = await loadStoreDashboard({ storeId, ...range, accessToken })
        active.current = range
        setData(next)
        setMessage('')
      } catch (error) {
        if (!silent) setMessage(ERROR_MESSAGES[classifyDashboardError(error)])
        else setMessage('Làm mới tự động thất bại; đang hiển thị số liệu lần trước.')
      }
    },
    [accessToken, from, storeId, to],
  )

  // Auto-refresh every minute once a report is on screen (S4-03: refresh <= 1 minute).
  useEffect(() => {
    if (!data) return undefined
    const timer = setInterval(() => {
      if (active.current && !document.hidden) fetchData(true)
    }, REFRESH_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [data, fetchData])

  useEffect(() => {
    if (!data) return undefined
    setAge(dataAgeSeconds(data.generated_at))
    const tick = setInterval(() => setAge(dataAgeSeconds(data.generated_at)), 5000)
    return () => clearInterval(tick)
  }, [data])

  function submit(event) {
    event.preventDefault()
    fetchData(false)
  }

  const ai = data ? aiEffectivenessView(data) : null

  return (
    <section className="dashboard-page">
      <h2>Dashboard cửa hàng</h2>
      <p>Số liệu lấy trực tiếp từ cơ sở dữ liệu theo cửa hàng, tự làm mới mỗi phút.</p>
      <form onSubmit={submit}>
        <label>
          Từ ngày
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label>
          Đến ngày
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <button disabled={!storeId}>Tải báo cáo</button>
      </form>
      {message && <p role="status">{message}</p>}
      {data && (
        <>
          <p className="dashboard-meta">
            Cập nhật lúc {new Date(data.generated_at).toLocaleTimeString('vi-VN')}
            {age != null && ` (${age} giây trước)`}
          </p>
          <div className="dashboard-cards">
            <article>
              <small>Doanh thu PAID gộp</small>
              <strong>{money.format(data.gross_paid_revenue)}</strong>
            </article>
            <article>
              <small>Đơn PAID</small>
              <strong>{data.paid_order_count}</strong>
            </article>
            <article>
              <small>Thiết bị sẵn bán</small>
              <strong>{data.available_device_count}</strong>
            </article>
            <article>
              <small>Nhập / bán / trả (kho)</small>
              <strong>
                {data.received_movement_count} / {data.sold_movement_count} / {data.returned_movement_count}
              </strong>
            </article>
          </div>

          <h3>Hiệu quả AI gợi ý bán kèm</h3>
          <div className="dashboard-cards" data-testid="ai-effectiveness">
            <article>
              <small>Doanh thu có gợi ý AI (ước lượng)</small>
              <strong>{money.format(ai.revenue)}</strong>
            </article>
            <article>
              <small>Tỷ trọng trên doanh thu</small>
              <strong>{ai.shareLabel}</strong>
            </article>
            <article>
              <small>Đơn / dòng hàng có gợi ý</small>
              <strong>
                {ai.orders} / {ai.items}
              </strong>
            </article>
            <article>
              <small>Tỷ lệ chấp nhận gợi ý</small>
              <strong>{ai.acceptanceLabel}</strong>
            </article>
          </div>
          <ul className="dashboard-sources">
            {ai.sources.map((s) => (
              <li key={s.key}>
                <span>{s.label}</span>
                <strong>{s.count}</strong>
              </li>
            ))}
          </ul>
          <p className="dashboard-note">
            Doanh thu có gợi ý AI là ước lượng: dòng hàng được bán trong đơn PAID sau khi AI gợi ý sản phẩm đó cho
            một món khác trong cùng đơn. Hệ thống chưa ghi nhận lượt hiển thị/nhấn nên tỷ lệ chấp nhận hiển thị N/A.
          </p>

          <h3>Sản phẩm bán nhiều</h3>
          {data.top_products.length === 0 ? (
            <p>Không có dữ liệu trong kỳ.</p>
          ) : (
            <ul>
              {data.top_products.map((p) => (
                <li key={p.product_id}>
                  <span>
                    {p.sku} · {p.name}
                  </span>
                  <strong>{p.quantity}</strong>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  )
}
