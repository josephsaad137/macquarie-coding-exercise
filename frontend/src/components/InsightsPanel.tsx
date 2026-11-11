import type { Insight } from '../api'
import { InsightCard } from './InsightCard'

interface InsightsPanelProps {
  title: string
  description: string
  insights: Insight[]
  emptyMessage: string
  onViewQuality?: () => void
}

export function InsightsPanel({ title, description, insights, emptyMessage, onViewQuality }: InsightsPanelProps) {
  return (
    <section className={`insights-section${onViewQuality ? '' : ' quality-insights'}`}>
      <div className="insights-heading">
        <div><span className="insights-spark">✳</span><div><h2>{title}</h2><p>{description}</p></div></div>
        {onViewQuality && <button className="text-button" onClick={onViewQuality}>View quality report <span aria-hidden="true">→</span></button>}
      </div>
      {insights.length > 0
        ? <div className="insights-grid">{insights.map((insight) => <InsightCard key={insight.code} insight={insight} />)}</div>
        : <div className="insight-empty">{emptyMessage}</div>}
    </section>
  )
}
