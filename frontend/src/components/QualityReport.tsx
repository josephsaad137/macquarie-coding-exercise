import type { Dataset, Insight, QualityIssue } from '../api'
import { InsightsPanel } from './InsightsPanel'
import { PageHeading } from './PageHeading'
import { QualityTable } from './QualityTable'
import { formatNumber } from './formatters'

interface QualityReportProps {
  dataset?: Dataset
  issueCount: number
  issueTypes: number
  issues: QualityIssue[]
  issuePage: number
  qualityPages: number
  loading: boolean
  insights: Insight[]
  onIssuePageChange: (page: number) => void
}

export function QualityReport({
  dataset,
  issueCount,
  issueTypes,
  issues,
  issuePage,
  qualityPages,
  loading,
  insights,
  onIssuePageChange,
}: QualityReportProps) {
  return (
    <section className="quality-page">
      <PageHeading eyebrow="DATA QUALITY" title="Quality report" subtitle="Review validation findings, malformed records, duplicates, and missing timestamps." dataset={dataset} />
      <div className="quality-summary">
        <div className="quality-summary-icon">⌁</div>
        <div><strong>{formatNumber(issueCount)} findings</strong><span>{issueCount === 0 ? 'No quality issues found for this dataset.' : 'Findings are grouped by source record and issue type.'}</span></div>
        <span className="quality-types">{formatNumber(issueTypes)} types on this page</span>
      </div>
      <section className="panel quality-panel">
        <div className="panel-title-row">
          <div><h2>Validation findings</h2><p>{dataset ? dataset.filename : 'Select a dataset to see findings'}</p></div>
          <span className="table-count">{issueCount ? `Page ${issuePage + 1} of ${qualityPages}` : '0 findings'}</span>
        </div>
        <QualityTable issues={issues} loading={loading} />
        <div className="pagination">
          <span>{issueCount ? `Showing ${issuePage * 10 + 1}–${Math.min((issuePage + 1) * 10, issueCount)} of ${formatNumber(issueCount)}` : 'No findings to display'}</span>
          <div>
            <button className="page-button" disabled={issuePage === 0} onClick={() => onIssuePageChange(issuePage - 1)}>← Previous</button>
            <button className="page-button" disabled={issuePage + 1 >= qualityPages} onClick={() => onIssuePageChange(issuePage + 1)}>Next →</button>
          </div>
        </div>
      </section>
      <InsightsPanel title="Suggested actions" description="Recommendations generated from recurring patterns." insights={insights} emptyMessage="No recommendations are available yet." />
    </section>
  )
}
