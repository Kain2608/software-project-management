import { useState } from 'react'
import { getSerialImeiStatus } from './serialImeiApi.js'
import StatusCard from '../../shared/components/StatusCard.jsx'

export default function SerialImeiPanel() {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  async function checkBackend() {
    try {
      setError('')
      setData(await getSerialImeiStatus())
    } catch {
      setData(null)
      setError('Không kết nối được Backend Serial/IMEI.')
    }
  }

  return (
    <section className="serial-panel" aria-labelledby="serial-page-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">MODULE 1 · THIẾT BỊ</p>
          <h2 id="serial-page-title">Tra cứu Serial / IMEI</h2>
        </div>
        <button type="button" onClick={checkBackend}>Kiểm tra Backend</button>
      </div>

      <div className="serial-panel__notice" role="status">
        <strong>Chờ đặc tả và backend Serial / IMEI</strong>
        <span>Quy trình chọn thiết bị, xác minh trạng thái và giữ Serial khi bán hàng chỉ khả dụng sau khi hợp đồng nghiệp vụ được phê duyệt.</span>
      </div>

      <div className="serial-panel__layout">
        <div className="serial-panel__info">
          <h3>Thông tin thiết bị</h3>
          <p>Khung giao diện chuẩn bị cho bước chọn Serial / IMEI theo D53. Không hiển thị thông tin thiết bị tự tạo.</p>
          <div className="serial-panel__slot"><span>SKU / Sản phẩm</span><strong>Chờ dữ liệu từ backend</strong></div>
          <div className="serial-panel__slot"><span>Serial / IMEI</span><strong>Chờ quyền tra cứu hợp lệ</strong></div>
          <div className="serial-panel__slot"><span>Trạng thái</span><strong>Chưa có kết quả xác minh</strong></div>
        </div>
        <div className="serial-panel__aside">
          <h3>Trạng thái tích hợp</h3>
          <p>Chức năng xác thực và kiểm tra Serial/IMEI chưa được kết nối với luồng POS đang chạy.</p>
          <div className="serial-panel__pending">CHƯA KHẢ DỤNG</div>
          <p>Không được tự đánh dấu SOLD, giữ Serial hay thay đổi dữ liệu kho từ giao diện này.</p>
        </div>
      </div>

      {data && <StatusCard title={data.module} status={data.status} note={data.note} />}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
