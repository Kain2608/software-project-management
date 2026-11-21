import { useEffect, useMemo, useRef, useState } from 'react'
import { searchCatalogForPos, classifyCartApiError } from '../pos/posCartApi.js'
import { addCartItem, calculateCartTotal, removeCartItem, updateCartItemQuantity } from '../pos/posCartModel.js'
import { evaluateOnlineCheckout } from './onlineOrderPolicy.js'
import './onlineOrder.css'

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })

export default function OnlineOrderPage({ accessToken, storeId, warehouseId }) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [cart, setCart] = useState([])
  const [state, setState] = useState('idle')
  const [message, setMessage] = useState('')
  const requestGeneration = useRef(0)
  const activeRequest = useRef(null)

  useEffect(() => {
    requestGeneration.current += 1
    activeRequest.current?.abort()
    setResults([]); setCart([]); setState('idle'); setMessage('')
    return () => activeRequest.current?.abort()
  }, [accessToken, storeId, warehouseId])
  const policy = evaluateOnlineCheckout({ accessToken, storeId, warehouseId, cartItems: cart })
  const total = useMemo(() => calculateCartTotal(cart), [cart])

  async function search(event) {
    event.preventDefault()
    activeRequest.current?.abort()
    const controller = new AbortController()
    activeRequest.current = controller
    const generation = ++requestGeneration.current
    setState('loading'); setMessage('')
    try {
      const items = await searchCatalogForPos({ query, accessToken, warehouseId, signal: controller.signal })
      if (generation !== requestGeneration.current) return
      setResults(items); setState(items.length ? 'ready' : 'empty')
    } catch (error) {
      if (controller.signal.aborted || generation !== requestGeneration.current) return
      const view = classifyCartApiError(error); setState(view.state); setMessage(view.message)
    } finally {
      if (activeRequest.current === controller) activeRequest.current = null
    }
  }
  function mutate(action) { try { setCart(action); setMessage('') } catch (error) { setMessage(error.message) } }

  return <section className="online-order" aria-label="Đặt hàng online">
    <header><p className="eyebrow">SPRINT 3 · S3-05</p><h2>Đặt hàng online</h2>
      <p>Danh mục và tồn kho được đọc theo kho. Giỏ hàng bị xóa khi phiên hoặc phạm vi cửa hàng thay đổi.</p></header>
    <div className="online-order__notice" role="status">Chưa mở gửi đơn: định danh khách hàng, API online order và callback thanh toán vẫn chờ duyệt.</div>
    <form onSubmit={search} className="online-order__search">
      <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Tên sản phẩm hoặc SKU" aria-label="Tìm sản phẩm" />
      <button disabled={!policy.catalogEnabled || state === 'loading'}>{state === 'loading' ? 'Đang tải…' : 'Tìm'}</button>
    </form>
    {message && <p className="error">{message}</p>}
    {state === 'empty' && <p>Không có sản phẩm phù hợp trong kho đã chọn.</p>}
    <div className="online-order__grid"><div><h3>Kết quả</h3>{results.map((item) => <article key={item.productId}>
      <div><strong>{item.name}</strong><small>{item.sku} · còn {item.availableQuantity}</small></div>
      <span>{money.format(item.unitPrice)}</span><button onClick={() => mutate((old) => addCartItem(old, item))} disabled={item.availableQuantity < 1}>Thêm</button>
    </article>)}</div><div><h3>Giỏ hàng</h3>{cart.length === 0 && <p>Chưa có sản phẩm.</p>}{cart.map((item) => <article key={item.productId}>
      <div><strong>{item.name}</strong><small>{item.sku}</small></div>
      <input type="number" min="1" max={Math.min(item.availableQuantity, 99)} value={item.quantity} aria-label={`Số lượng ${item.name}`}
        onChange={(e) => mutate((old) => updateCartItemQuantity(old, item.productId, Number(e.target.value)))} />
      <button onClick={() => mutate((old) => removeCartItem(old, item.productId))}>Xóa</button>
    </article>)}<strong className="online-order__total">Tạm tính: {money.format(total)}</strong>
      <button className="online-order__checkout" disabled={!policy.checkoutEnabled}>Tiếp tục thanh toán</button></div></div>
  </section>
}
