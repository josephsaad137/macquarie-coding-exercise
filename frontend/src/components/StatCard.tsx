interface StatCardProps {
  title: string
  value: string
  note: string
  icon: string
  tone: string
}

export function StatCard({ title, value, note, icon, tone }: StatCardProps) {
  return (
    <article className="stat-card">
      <div className={`stat-icon ${tone}`} aria-hidden="true">{icon}</div>
      <div className="stat-title">{title}</div>
      <div className="stat-value">{value}</div>
      <div className="stat-note">{note}</div>
    </article>
  )
}
