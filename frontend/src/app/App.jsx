import { useState } from 'react'
import PosPage from '../features/pos/PosPage.jsx'
import QrCameraScanner from '../features/qr/QrCameraScanner.jsx'
import SerialImeiPanel from '../features/serialImei/SerialImeiPanel.jsx'

const screens = {
  pos: {
    breadcrumb: 'Bán hàng POS / Giỏ hàng',
    title: 'Bán hàng tại quầy',
    subtitle: 'Chọn sản phẩm, kiểm tra tồn kho và chuẩn bị giao dịch tại quầy.',
  },
  qr: {
    breadcrumb: 'Kho hàng / Quét QR',
    title: 'Quét QR thiết bị',
    subtitle: 'Quét mã hoặc nhập payload để tra cứu thiết bị theo quyền truy cập.',
  },
  serial: {
    breadcrumb: 'Kho hàng / Serial · IMEI',
    title: 'Quản lý Serial / IMEI',
    subtitle: 'Theo dõi trạng thái tích hợp; các thao tác nghiệp vụ cần backend được phê duyệt.',
  },
}

export default function App() {
  const [activeScreen, setActiveScreen] = useState('pos')
  const screen = screens[activeScreen]

  const navButton = (id, label) => (
    <button
      key={id}
      type="button"
      className={`sasms-nav__item${activeScreen === id ? ' sasms-nav__item--active' : ''}`}
      aria-current={activeScreen === id ? 'page' : undefined}
      onClick={() => setActiveScreen(id)}
    >
      <span className="sasms-nav__mark" aria-hidden="true" />
      {label}
    </button>
  )

  return (
    <div className="sasms-app">
      <aside className="sasms-sidebar" aria-label="Điều hướng hệ thống SASMS">
        <div className="sasms-brand">
          <strong>SASMS</strong>
          <span>SMART ELECTRONICS</span>
        </div>

        <nav className="sasms-nav" aria-label="Điều hướng chính">
          <span className="sasms-nav__group">HỆ THỐNG</span>
          <span className="sasms-nav__item sasms-nav__item--unavailable" aria-disabled="true" title="Tổng quan chưa được tích hợp trong bản frontend này">
            <span className="sasms-nav__mark" aria-hidden="true" />Tổng quan
          </span>
          <span className="sasms-nav__item sasms-nav__item--unavailable" aria-disabled="true" title="Module Kho chưa có màn hình tích hợp trong frontend này">
            <span className="sasms-nav__mark" aria-hidden="true" />Kho hàng
          </span>
          {navButton('qr', 'Quét QR')}
          <span className="sasms-nav__item sasms-nav__item--unavailable" aria-disabled="true" title="Module Nhập kho chưa có màn hình tích hợp trong frontend này">
            <span className="sasms-nav__mark" aria-hidden="true" />Nhập kho
          </span>
          {navButton('pos', 'Bán hàng POS')}
          {navButton('serial', 'Serial / IMEI')}
          <span className="sasms-nav__item sasms-nav__item--unavailable" aria-disabled="true" title="Báo cáo chưa được tích hợp">
            <span className="sasms-nav__mark" aria-hidden="true" />Báo cáo
          </span>
        </nav>

        <div className="sasms-sidebar__footer">
          <strong>Sprint 2 · Frontend</strong>
          <small>Quyền truy cập và giao dịch vẫn do backend xác thực.</small>
        </div>
      </aside>

      <div className="sasms-workspace">
        <header className="sasms-topbar">
          <div className="sasms-breadcrumb">{screen.breadcrumb}</div>
          <span className="sasms-topbar__environment">D53 · BẢN THIẾT KẾ CHƯA DUYỆT</span>
        </header>

        <main className="sasms-main" id="main-content">
          <div className="sasms-page-heading">
            <div>
              <p className="sasms-page-heading__eyebrow">SASMS · SMART ELECTRONIC SUPERMARKET</p>
              <h1>{screen.title}</h1>
              <p>{screen.subtitle}</p>
            </div>
            <span className="sasms-page-heading__tag">SASMS · D53 v0.1</span>
          </div>

          <div className="sasms-view" key={activeScreen}>
            {activeScreen === 'pos' && <PosPage />}
            {activeScreen === 'qr' && <QrCameraScanner />}
            {activeScreen === 'serial' && <SerialImeiPanel />}
          </div>

          <footer className="sasms-footer">
            <strong>D53 · GIAO DIỆN THAM CHIẾU CHỜ PO REVIEW</strong>
            <span>Giao diện dựa trên mockup Sprint 0. Chưa xác nhận hoàn tất login, checkout, thanh toán hay nghiệm thu E2E.</span>
          </footer>
        </main>
      </div>
    </div>
  )
}
