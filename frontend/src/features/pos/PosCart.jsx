import { useState } from 'react'

import { classifyCartApiError, searchCatalogForPos } from './posCartApi.js'
import {
  MAX_CART_QUANTITY,
  addCartItem,
  calculateCartTotal,
  removeCartItem,
  updateCartItemQuantity,
} from './posCartModel.js'
import './posCart.css'

const vnd = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })

function cartMessage(error) {
  if (error?.message === 'OUT_OF_STOCK') return 'Sản phẩm hiện không còn tồn có thể bán tại kho này.'
  if (error?.message === 'QUANTITY_EXCEEDS_STOCK') return 'Số lượng vượt tồn có thể bán hoặc giới hạn 99 sản phẩm.'
  return 'Không thể cập nhật giỏ hàng với dữ liệu hiện tại.'
}

export default function PosCart({ accessToken, warehouseId, items, onItemsChange, message, onMessageChange }) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [searchState, setSearchState] = useState('idle')
  const authenticated = typeof accessToken === 'string' && accessToken.trim().length > 0
  const hasWarehouse = typeof warehouseId === 'string' && warehouseId.length > 0
  const ready = authenticated && hasWarehouse
  const total = calculateCartTotal(items)

  async function search(event) {
    event.preventDefault()
    if (!ready) {
      setSearchState('auth-required')
      onMessageChange('Cần đăng nhập và chọn đúng kho bán hàng trước khi tìm sản phẩm.')
      return
    }
    setSearchState('loading')
    onMessageChange('Đang tải danh mục và kiểm tra tồn kho…')
    try {
      const products = await searchCatalogForPos({ query, accessToken, warehouseId })
      setResults(products)
      setSearchState(products.length ? 'success' : 'empty')
      onMessageChange(products.length ? `Tìm thấy ${products.length} sản phẩm.` : 'Không tìm thấy sản phẩm đang hoạt động.')
    } catch (error) {
      const classified = classifyCartApiError(error)
      setResults([])
      setSearchState(classified.state)
      onMessageChange(classified.message)
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
      <div className="pos-cart__heading">
        <div>
          <p className="eyebrow">S2-02 · GIỎ HÀNG TẠI QUẦY</p>
          <h3 id="pos-cart-title">Chọn sản phẩm</h3>
        </div>
        <span>{items.length} dòng hàng</span>
      </div>

      {!ready && (
        <p className="pos-cart__notice" role="status">
          Chưa có phiên đăng nhập và kho bán hàng. Tra cứu Catalog/Stock đang khóa an toàn.
        </p>
      )}

      <form className="pos-cart__search" onSubmit={search}>
        <label htmlFor="pos-product-query">
          Tên hoặc SKU
          <input
            id="pos-product-query"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Ví dụ: TV hoặc SKU"
            maxLength={100}
            disabled={!ready || searchState === 'loading'}
          />
        </label>
        <button type="submit" disabled={!ready || searchState === 'loading'}>
          {searchState === 'loading' ? 'Đang kiểm tra…' : 'Tìm sản phẩm'}
        </button>
      </form>

      {message && <p className={`pos-cart__message pos-cart__message--${searchState}`} aria-live="polite">{message}</p>}

      {results.length > 0 && (
        <ul className="pos-cart__results" aria-label="Kết quả tìm sản phẩm">
          {results.map((product) => (
            <li key={product.productId}>
              <div><strong>{product.name}</strong><small>{product.sku}</small></div>
              <div><span>{vnd.format(product.unitPrice)}</span><small>Có thể bán: {product.availableQuantity}</small></div>
              <button type="button" onClick={() => add(product)} disabled={product.availableQuantity < 1}>
                {product.availableQuantity < 1 ? 'Hết hàng' : 'Thêm'}
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="pos-cart__lines">
        <h4>Giỏ hiện tại</h4>
        {items.length === 0 ? (
          <p className="pos-cart__empty">Chưa có sản phẩm. Giỏ hàng chưa tạo order hoặc giữ Serial.</p>
        ) : (
          <ul>
            {items.map((item) => (
              <li key={item.productId}>
                <div><strong>{item.name}</strong><small>{item.sku}</small></div>
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
                <span>{vnd.format(item.unitPrice * item.quantity)}</span>
                <button type="button" className="button-secondary" onClick={() => onItemsChange(removeCartItem(items, item.productId))}>Xóa</button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <footer className="pos-cart__summary">
        <div><span>Tổng tiền dự kiến</span><strong>{vnd.format(total)}</strong></div>
        <small>Giá và tồn phải được backend kiểm tra lại khi tạo order/giữ Serial.</small>
        <button type="button" disabled>Thanh toán chưa khả dụng</button>
      </footer>
    </section>
  )
}
