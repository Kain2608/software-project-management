export default function StatusCard({ title, status, note }) {
  return (
    <article className="status-card">
      <h3>{title}</h3>
      <p><strong>Trạng thái:</strong> {status}</p>
      <p>{note}</p>
    </article>
  )
}
