import type { QualityIssue } from '../api'
import { formatTimestamp } from './formatters'

interface QualityTableProps {
  issues: QualityIssue[]
  loading: boolean
}

export function QualityTable({ issues, loading }: QualityTableProps) {
  if (loading) return <div className="table-empty">Loading quality findings…</div>
  if (issues.length === 0) {
    return <div className="table-empty"><span className="empty-check">✓</span><strong>All clear</strong><span>No findings on this page.</span></div>
  }
  return (
    <div className="table-scroll">
      <table>
        <thead><tr><th>ISSUE</th><th>CONTRACT</th><th>TIMESTAMP</th><th>ROW</th><th>DETAIL</th></tr></thead>
        <tbody>{issues.map((issue, index) => (
          <tr key={`${issue.code}-${issue.rowNumber ?? index}-${issue.timestamp ?? ''}`}>
            <td><span className={`issue-badge ${issue.code.includes('INVALID') || issue.code.includes('MALFORMED') ? 'issue-red' : issue.code.includes('MISSING') ? 'issue-amber' : 'issue-purple'}`}>{issue.code.replaceAll('_', ' ')}</span></td>
            <td>{issue.contract ?? '—'}</td><td className="timestamp-cell">{formatTimestamp(issue.timestamp)}</td>
            <td>{issue.rowNumber ?? '—'}</td><td className="issue-message">{issue.message}</td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  )
}
