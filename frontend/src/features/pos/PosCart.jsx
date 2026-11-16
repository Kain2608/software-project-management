import { useEffect, useRef, useState } from 'react'

import { classifyCartApiError, searchCatalogForPos } from './posCartApi.js'
import {
  MAX_CART_QUANTITY,
  addCartItem,
  calculateCartTotal,
  removeCartItem,
  updateCartItemQuantity,
} from './posCartModel.js'
import {
  createLatestRequestGate,
  createPosContext,
  isSamePosContext,
  shouldInvalidateCartForApiState,
} from './posCartContext.js'
import './posCart.css'

const vnd = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })

function cartMessage(error) {
  if (error?.message === 'OUT_OF_STOCK') return 'Sản phẩm hiện không còn tồn có thể bán tại kho này.'
  if (error?.message === 'QUANTITY_EXCEEDS_STOCK') return 'Số lượng vượt tồn có thể bán hoặc giới hạn 99 sản phẩm.'
  return 'Không thể cập nhật giỏ hàng với dữ liệu hiện tại.'
}

export default function PosCart({ accessToken, storeId, warehouseId, items, onItemsChange, message, onMessageChange }) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [searchState, setSearchState] = useState('idle')
  const requestGate = useRef(null)
  const resultsContext = useRef(null)
  if (requestGate.current === null) requestGate.current = createLatestRequestGate()
  const authenticated = typeof accessToken === 'string' && accessToken.trim().length > 0
  const hasWarehouse = typeof warehouseId === 'string' && warehouseId.length > 0
  const ready = authenticated && hasWarehouse
  const total = calculateCartTotal(items)
  const currentSearchContext = createPosContext({ accessToken, storeId, warehouseId })
  const latestSearchContext = useRef(currentSearchContext)
  const previousSearchContext = useRef(currentSearchContext)
  latestSearchContext.current = currentSearchContext
  const visibleResults = isSamePosContext(resultsContext.current, currentSearchContext) ? results : []

  useEffect(() => {
    const contextChanged = !isSamePosContext(previousSearchContext.current, currentSearchContext)
    previousSearchContext.current = currentSearchContext
    requestGate.current.invalidate()
    resultsContext.current = null
    setResults([])
    setSearchState(ready ? 'idle' : 'auth-required')
    if (contextChanged) {
      onMessageChange(ready
        ? 'Phiên, cửa hàng hoặc kho đã thay đổi. Hãy tìm lại để lấy tồn kho hiện tại.'
        : 'Cần đăng nhập và chọn đúng kho bán hàng trước khi tìm sản phẩm.')
    }
  }, [accessToken, storeId, warehouseId, ready])

  async function search(event) {
    event.preventDefault()
    if (!ready) {
      setSearchState('auth-required')
      onMessageChange('Cần đăng nhập và chọn đúng kho bán hàng trước khi tìm sản phẩm.')
      return
    }
    const requestContext = createPosContext({ accessToken, storeId, warehouseId })
    const request = requestGate.current.start()
    setSearchState('loading')
    onMessageChange('Đang tải danh mục và kiểm tra tồn kho…')
    try {
      const products = await searchCatalogForPos({ query, accessToken, warehouseId, signal: request.signal })
      if (!requestGate.current.isCurrent(request) || !isSamePosContext(requestContext, latestSearchContext.current)) return
      resultsContext.current = requestContext
      setResults(products)
      setSearchState(products.length ? 'success' : 'empty')
      onMessageChange(products.length ? `Tìm thấy ${products.length} sản phẩm.` : 'Không tìm thấy sản phẩm đang hoạt động.')
    } catch (error) {
      if (!requestGate.current.isCurrent(request)) return
      const classified = classifyCartApiError(error)
      resultsContext.current = null
      setResults([])
      setSearchState(classified.state)
      onMessageChange(classified.message)
      if (shouldInvalidateCartForApiState(classified.state)) onItemsChange([])
    }
  }

  function add(product) {
    try {
      onItemsChange(addCartItem(items, product))
      onMessageChange(`Đã thêm ${product.name} vào giỏ cục bộ.`)
    } catch (error) {
      onMessageChange(cartMessage(error))
    }
  }

  function update(productId, rawQuantity) {
    try {
      onItemsChange(updateCartItemQuantity(items, productId, Number(rawQuantity)))
      onMessageChange('Đã cập nhật số lượng trong giỏ cục bộ.')
    } catch (error) {
      onMessageChange(cartMessage(error))
    }
  }

  return (
    <section className="pos-cart" aria-labelledby="pos-cart-title">
      <div className="pos-cart__products">
        <div className="pos-cart__heading">
          <p className="eyebrow">S2-02 · CHỌN SẢN PHẨM</p>
          <h3 id="pos-cart-title">Chọn sản phẩm theo SKU</h3>
          <p>Tra cứu Catalog và số lượng có thể bán theo kho được cấp quyền.</p>
        </div>

        {!ready && (
          <p className="pos-cart__notice" role="status">
            Chưa có phiên đăng nhập và kho bán hàng. Tra cứu Catalog/Stock đang khóa an toàn.
          </p>
        )}

        <form className="pos-cart__search" onSubmit={search}>
          <label htmlFor="pos-product-query">
            Tìm tên sản phẩm hoặc SKU
            <input
              id="pos-product-query"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Nhập tên hoặc SKU của sản phẩm"
              maxLength={100}
              disabled={!ready || searchState === 'loading'}
            />
          </label>
          <button type="submit" disabled={!ready || searchState === 'loading'}>
            {searchState === 'loading' ? 'Đang kiểm tra…' : 'Tìm sản phẩm'}
          </button>
        </form>

        {message && <p className={`pos-cart__message pos-cart__message--${searchState}`} aria-live="polite">{message}</p>}

        {visibleResults.length > 0 ? (
          <ul className="pos-cart__results" aria-label="Kết quả tìm sản phẩm">
            {visibleResults.map((product) => (
              <li key={product.productId}>
                <div><strong>{product.name}</strong><small>{product.sku}</small></div>
                <div><span>{vnd.format(product.unitPrice)}</span><small>Khả dụng: {product.availableQuantity}</small></div>
                <button type="button" onClick={() => add(product)} disabled={product.availableQuantity < 1}>
                  {product.availableQuantity < 1 ? 'Hết hàng' : '+ Thêm'}
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <div className="pos-cart__empty-search">
            <span className="pos-cart__empty-search-icon" aria-hidden="true">⌕</span>
            <strong>{ready ? 'Bắt đầu bằng tên hoặc SKU' : 'Chờ phiên bán hàng hợp lệ'}</strong>
            <span>{ready ? 'Kết quả tìm kiếm và số tồn kho sẽ xuất hiện tại đây.' : 'Không sử dụng sản phẩm minh họa làm hàng tồn kho thật.'}</span>
          </div>
        )}

        <p className="pos-cart__catalog-note">Chọn Serial/IMEI và giữ thiết bị phải được backend kiểm tra khi tạo đơn; giao diện này chưa thực hiện thao tác đó.</p>
      </div>

      <aside className="pos-cart__checkout" aria-label="Giỏ hàng bán tại quầy">
        <div className="pos-cart__checkout-title">
          <h3>Giỏ hàng</h3>
          <span>{items.length} dòng hàng</span>
        </div>
        <div className="pos-cart__lines">
          {items.length === 0 ? (
            <div className="pos-cart__empty" role="status">
              <strong>Giỏ hàng đang trống</strong>
              <span>Chọn sản phẩm từ danh mục để bắt đầu.</span>
            </div>
          ) : (
            <ul>
              {items.map((item) => (
                <li key={item.productId}>
                  <div className="pos-cart__line-heading"><strong>{item.name}</strong><small>{item.sku}</small></div>
                  <div className="pos-cart__line-controls">
                    <label>
                      Số lượng
                      <input
                        aria-label={`Số lượng ${item.name}`}
                        type="number"
                        min="1"
                        max={Math.min(item.availableQuantity, MAX_CART_QUANTITY)}
                        value={item.quantity}
                        onChange={(event) => update(item.productId, event.target.value)}
                      />
                    </label>
                    <strong>{vnd.format(item.unitPrice * item.quantity)}</strong>
                    <button type="button" className="button-secondary" onClick={() => onItemsChange(removeCartItem(items, item.productId))}>Xóa</button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
        <footer className="pos-cart__summary">
          <div><span>Tạm tính</span><strong>{vnd.format(total)}</strong></div>
          <button className="pos-cart__checkout-button" type="button" disabled title="Chờ backend xác nhận đơn, giữ Serial và thanh toán nguyên tử">
            Tiếp tục thanh toán · Chưa khả dụng
          </button>
          <p>Giá, tồn kho và Serial phải được backend xác minh lại khi tạo đơn.</p>
        </footer>
      </aside>
    </section>
  )
}
