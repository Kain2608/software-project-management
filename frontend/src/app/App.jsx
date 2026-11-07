import PosPage from '../features/pos/PosPage.jsx'
import SerialImeiPanel from '../features/serialImei/SerialImeiPanel.jsx'

export default function App() {
  return (
    <main className="app-shell">
      <header className="hero">
        <p className="eyebrow">SMART ELECTRONIC SUPERMARKET</p>
        <h1>Module 1 — POS Source Code Skeleton</h1>
        <p>
          Week 2 foundation: React frontend + Spring Boot backend. Business rules are
          intentionally excluded until requirements are approved.
        </p>
      </header>

      <PosPage />
      <SerialImeiPanel />
    </main>
  )
}
