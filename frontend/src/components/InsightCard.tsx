import type { Insight } from '../api'

export function InsightCard({ insight }: { insight: Insight }) {
  return (
    <article className="insight-card">
      <span className="insight-code">{insight.code.replaceAll('_', ' ')}</span>
      <h3>{insight.title}</h3>
      <p>{insight.description}</p>
      <div className="recommendation"><span>RECOMMENDED ACTION</span><p>{insight.recommendation}</p></div>
      {insight.evidence.length > 0 && <div className="evidence">{insight.evidence.slice(0, 2).join(' · ')}</div>}
    </article>
  )
}
