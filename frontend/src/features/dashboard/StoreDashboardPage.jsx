import { useCallback, useEffect, useRef, useState } from 'react'
import { classifyDashboardError, loadStoreDashboard } from './dashboardApi.js'
import {
  REFRESH_INTERVAL_MS,
  aiEffectivenessView,
  dashboardUtcRange,
  dataAgeSeconds,
  todayInVietnam,
} from './dashboardView.js'
import './dashboard.css'

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })

const ERROR_MESSAGES = {
  auth: 'Cần phiên đăng nhập hợp lệ.',
  forbidden: 'Không có quyền reports.read tại cửa hàng này.',
  disabled: 'Dashboard đang tắt hoặc chưa có API được phê duyệt.',
  error: 'Không thể tải Dashboard. Hãy kiểm tra kết nối Backend.',
}

export default function StoreDashboardPage({ accessToken, storeId }) {
  const today = todayInVietnam()
  const [from, setFrom] = useState(today.slice(0, 8) + '01')
  const [to, setTo] = useState(today)
  const [report, setReport] = useState(null)
  const [message, setMessage] = useState('')
  const [age, setAge] = useState(null)
  const [loading, setLoading] = useState(false)

  // The last *submitted* range drives refresh; changing inputs alone never changes the report scope.
  const active = useRef(null)
  const request = useRef({ sequence: 0, controller: null })

  // A response created for a previous JWT or store must never be painted in the new session.
  const data = report?.storeId === storeId && report?.accessToken === accessToken
    ? report.data
    : null

  useEffect(() => {
    request.current.sequence += 1
    request.current.controller?.abort()
    request.current.controller = null
    active.current = null
    setReport(null)
    setMessage('')
    setLoading(false)
  }, [accessToken, storeId])

  useEffect(() => () => {
    request.current.sequence += 1
    request.current.controller?.abort()
  }, [])

  const fetchData = useCallback(async (silent = false) => {
    const submitted = silent ? active.current : dashboardUtcRange(from, to)
    if (!submitted || !accessToken || !storeId) {
      if (!silent) setMessage('Cần đăng nhập, chọn cửa hàng và khoảng ngày hợp lệ để tải báo cáo.')
      return
    }

    request.current.controller?.abort()
    const controller = new AbortController()
    const sequence = ++request.current.sequence
    request.current.controller = controller

    if (!silent) {
      active.current = null
      setLoading(true)
      setReport(null)
      setMessage('Đang tải báo cáo…')
    }

    try {
      const next = await loadStoreDashboard({
        storeId,
        from: submitted.from,
        to: submitted.to,
        accessToken,
        signal: controller.signal,
      })
      if (sequence !== request.current.sequence || controller.signal.aborted) return

      if (!silent) {
        active.current = { ...submitted, selectionFrom: from, selectionTo: to }
      }
      setReport({ storeId, accessToken, data: next })
      setMessage('')
    } catch (error) {
      if (sequence !== request.current.sequence || controller.signal.aborted) return
      const category = classifyDashboardError(error)
      if (silent && (category === 'auth' || category === 'forbidden' || category === 'disabled')) {
        // Do not keep previously loaded business data visible after permission/session errors.
        active.current = null
        setReport(null)
        setMessage(ERROR_MESSAGES[category])
      } else {
        setMessage(silent
          ? 'Làm mới tự động thất bại; đang hiển thị số liệu đã tải trước đó.'
          : ERROR_MESSAGES[category])
      }
    } finally {
      if (sequence === request.current.sequence) {
        request.current.controller = null
        if (!silent) setLoading(false)
      }
    }
  }, [accessToken, from, storeId, to])

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
    if (!loading) fetchData(false)
  }

  const validRange = dashboardUtcRange(from, to) !== null
  const canLoad = Boolean(accessToken && storeId && validRange && !loading)
  const pendingChanges = Boolean(data && active.current &&
    (from !== active.current.selectionFrom || to !== active.current.selectionTo))
  const ai = data ? aiEffectivenessView(data) : null

  return (
    <section className="dashboard-page">
      <h2>Dashboard cửa hàng</h2>
      <p>Số liệu được lấy từ Backend theo phạm vi cửa hàng, tự làm mới tối đa mỗi phút khi đang mở trang.</p>
      <form onSubmit={submit}>
        <label>
          Từ ngày
          <input type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label>
          Đến ngày
          <input type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
        </label>
        <button type="submit" disabled={!canLoad}>{loading ? 'Đang tải…' : 'Tải báo cáo'}</button>
      </form>
      {(!accessToken || !storeId) && (
        <p role="status">Chưa có phiên đăng nhập và cửa hàng hợp lệ từ IdP/OIDC. Cần kết nối xác thực để tải số liệu thật.</p>
      )}
      {!validRange && <p role="alert">Khoảng ngày không hợp lệ. Ngày kết thúc phải lớn hơn hoặc bằng ngày bắt đầu.</p>}
      {pendingChanges && <p role="status">Bộ lọc đã thay đổi. Nhấn “Tải báo cáo” để áp dụng khoảng ngày mới.</p>}
      {message && <p role="status">{message}</p>}
      {data && (
        <>
          <p className="dashboard-meta">
            Cập nhật lúc {new Date(data.generated_at).toLocaleTimeString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}
            {age != null && ` (${age} giây trước)`}
          </p>
          <div className="dashboard-cards">
            <article><small>Doanh thu PAID gộp</small><strong>{money.format(data.gross_paid_revenue)}</strong></article>
            <article><small>Đơn PAID</small><strong>{data.paid_order_count}</strong></article>
            <article><small>Thiết bị sẵn bán</small><strong>{data.available_device_count}</strong></article>
            <article>
              <small>Nhập / bán / trả (kho)</small>
              <strong>{data.received_movement_count} / {data.sold_movement_count} / {data.returned_movement_count}</strong>
            </article>
          </div>

          <h3>Hiệu quả AI gợi ý bán kèm</h3>
          <div className="dashboard-cards" data-testid="ai-effectiveness">
            <article><small>Doanh thu có gợi ý AI (ước lượng)</small><strong>{money.format(ai.revenue)}</strong></article>
            <article><small>Tỷ trọng trên doanh thu</small><strong>{ai.shareLabel}</strong></article>
            <article><small>Đơn / dòng hàng có gợi ý</small><strong>{ai.orders} / {ai.items}</strong></article>
            <article><small>Tỷ lệ chấp nhận gợi ý</small><strong>{ai.acceptanceLabel}</strong></article>
          </div>
          <ul className="dashboard-sources">
            {ai.sources.map((s) => <li key={s.key}><span>{s.label}</span><strong>{s.count}</strong></li>)}
          </ul>
          <p className="dashboard-note">
            Doanh thu có gợi ý AI là ước lượng quy gán, không chứng minh khách hàng đã chấp nhận gợi ý.
            Dashboard chưa tính được tỷ lệ chấp nhận từ sự kiện SHOWN/ACCEPTED nên hiển thị N/A.
          </p>

          <h3>Sản phẩm bán nhiều</h3>
          {data.top_products.length === 0 ? (
            <p>Không có dữ liệu trong kỳ.</p>
          ) : (
            <ul>
              {data.top_products.map((p) => (
                <li key={p.product_id}><span>{p.sku} · {p.name}</span><strong>{p.quantity}</strong></li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  )
}
