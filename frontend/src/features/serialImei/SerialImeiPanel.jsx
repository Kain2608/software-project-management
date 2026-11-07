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
    <section>
      <div className="section-heading">
        <div>
          <p className="eyebrow">BOUNDARY</p>
          <h2>Serial / IMEI</h2>
        </div>
        <button onClick={checkBackend}>Kiểm tra Backend</button>
      </div>

      <p>
        Tuần 2 chỉ dựng ranh giới chức năng. Quy tắc kiểm tra, vòng đời và lưu trữ
        Serial/IMEI sẽ được triển khai khi có đặc tả nghiệp vụ được xác nhận.
      </p>

      {data && <StatusCard title={data.module} status={data.status} note={data.note} />}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
