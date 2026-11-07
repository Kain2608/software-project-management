import { useState } from 'react'
import { getPosStatus } from './posApi.js'
import StatusCard from '../../shared/components/StatusCard.jsx'

export default function PosPage() {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  async function checkBackend() {
    try {
      setError('')
      setData(await getPosStatus())
    } catch {
      setData(null)
      setError('Không kết nối được Backend POS. Hãy kiểm tra Spring Boot đang chạy ở cổng 8080.')
    }
  }

  return (
    <section>
      <div className="section-heading">
        <div>
          <p className="eyebrow">MODULE 1</p>
          <h2>Point of Sale (POS)</h2>
        </div>
        <button onClick={checkBackend}>Kiểm tra Backend</button>
      </div>

      <div className="placeholder-grid">
        <div className="placeholder">Tìm / chọn sản phẩm</div>
        <div className="placeholder">Giỏ hàng bán hàng</div>
        <div className="placeholder">Thông tin giao dịch</div>
      </div>

      {data && <StatusCard title={data.module} status={data.status} note={data.note} />}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
