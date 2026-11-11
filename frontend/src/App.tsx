import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { api, type DailyBar, type Dataset, type Insight, type QualityIssue, type VwapPoint } from './api'
import './App.css'

type View = 'overview' | 'quality'
type Filters = { contract: string; from: string; to: string }

const EMPTY_FILTERS: Filters = { contract: '', from: '', to: '' }

function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value)
}

function formatDate(value: string): string {
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function formatTimestamp(value: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ')
}

function Chart({ values, label, color, formatValue }: {
  values: number[]
  label: string
  color: string
  formatValue: (value: number) => string
}) {
  if (values.length === 0) {
    return <div className="chart-empty">Import market data to see your chart.</div>
  }

  const width = 720
  const height = 190
  const padding = 12
  const low = Math.min(...values)
  const high = Math.max(...values)
  const span = high - low || 1
  const points = values.map((value, index) => {
    const x = padding + (index / Math.max(values.length - 1, 1)) * (width - padding * 2)
    const y = height - padding - ((value - low) / span) * (height - padding * 2)
    return `${x},${y}`
  }).join(' ')
  const latest = values[values.length - 1]

  return (
    <div className="chart-wrap" role="img" aria-label={`${label} trend, latest ${formatValue(latest)}`}>
      <div className="chart-range"><span>{formatValue(high)}</span><span>{formatValue(low)}</span></div>
      <svg className="chart" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" aria-hidden="true">
        <defs>
          <linearGradient id={`fill-${label}`} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity=".22" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>
        {[0.25, 0.5, 0.75].map((fraction) => (
          <line key={fraction} x1="0" x2={width} y1={height * fraction} y2={height * fraction} className="chart-grid" />
        ))}
        <polygon points={`12,${height} ${points} ${width - 12},${height}`} fill={`url(#fill-${label})`} />
        <polyline points={points} fill="none" stroke={color} strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" />
      </svg>
    </div>
  )
}

function StatCard({ title, value, note, icon, tone }: {
  title: string
  value: string
  note: string
  icon: string
  tone: string
}) {
  return (
    <article className="stat-card">
      <div className={`stat-icon ${tone}`} aria-hidden="true">{icon}</div>
      <div className="stat-title">{title}</div>
      <div className="stat-value">{value}</div>
      <div className="stat-note">{note}</div>
    </article>
  )
}

function App() {
  const [datasets, setDatasets] = useState<Dataset[]>([])
  const [selectedId, setSelectedId] = useState('')
  const [view, setView] = useState<View>('overview')
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState<Filters>(EMPTY_FILTERS)
  const [bars, setBars] = useState<DailyBar[]>([])
  const [vwap, setVwap] = useState<VwapPoint[]>([])
  const [issues, setIssues] = useState<QualityIssue[]>([])
  const [issueCount, setIssueCount] = useState(0)
  const [issuePage, setIssuePage] = useState(0)
  const [loadedQualityKey, setLoadedQualityKey] = useState('')
  const [insights, setInsights] = useState<Insight[]>([])
  const [datasetsLoading, setDatasetsLoading] = useState(true)
  const [loadedAnalyticsKey, setLoadedAnalyticsKey] = useState('')
  const [pageError, setPageError] = useState('')
  const [analyticsError, setAnalyticsError] = useState('')
  const [showImport, setShowImport] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState('')
  const [uploadMessage, setUploadMessage] = useState('')
  const [frequency, setFrequency] = useState<'minute' | 'daily'>('minute')

  const selectedDataset = datasets.find((dataset) => dataset.id === selectedId)
  const importedRows = selectedDataset?.rowsImported ?? 0
  const rejectedRows = selectedDataset?.rowsRejected ?? 0
  const rejectionRate = importedRows + rejectedRows > 0
    ? `${((rejectedRows / (importedRows + rejectedRows)) * 100).toFixed(1)}% rejected`
    : 'No rejected rows'
  const qualityPages = Math.max(1, Math.ceil(issueCount / 10))
  const qualityKey = `${selectedId}:${issuePage}`
  const analyticsKey = `${selectedId}:${appliedFilters.contract}:${appliedFilters.from}:${appliedFilters.to}`
  const qualityLoading = Boolean(selectedId && loadedQualityKey !== qualityKey)
  const analyticsLoading = Boolean(selectedId && loadedAnalyticsKey !== analyticsKey)
  const issueTypes = useMemo(() => new Set(issues.map((issue) => issue.code)).size, [issues])

  useEffect(() => {
    let active = true
    api.listDatasets()
      .then((result) => {
        if (!active) return
        setDatasets(result)
        if (result.length > 0) setSelectedId(result[0].id)
      })
      .catch((error: unknown) => {
        if (active) setPageError(error instanceof Error ? error.message : 'Unable to load datasets.')
      })
      .finally(() => {
        if (active) setDatasetsLoading(false)
      })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!selectedId) return
    let active = true
    api.quality(selectedId, issuePage)
      .then((result) => {
        if (!active) return
        setIssues(result.issues)
        setIssueCount(result.totalIssues)
        setLoadedQualityKey(qualityKey)
      })
      .catch((error: unknown) => {
        if (active) {
          setPageError(error instanceof Error ? error.message : 'Unable to load quality findings.')
          setLoadedQualityKey(qualityKey)
        }
      })
    return () => { active = false }
  }, [selectedId, issuePage, qualityKey])

  useEffect(() => {
    if (!selectedId) return
    let active = true
    api.insights(selectedId)
      .then((result) => { if (active) setInsights(result) })
      .catch((error: unknown) => {
        if (active) setPageError(error instanceof Error ? error.message : 'Unable to load insights.')
      })
    return () => { active = false }
  }, [selectedId])

  useEffect(() => {
    if (!selectedId) return
    let active = true
    Promise.all([
      api.dailyBars(selectedId, appliedFilters.contract, appliedFilters.from, appliedFilters.to),
      api.vwap(selectedId, appliedFilters.contract, appliedFilters.from, appliedFilters.to),
    ])
      .then(([dailyResult, vwapResult]) => {
        if (!active) return
        setBars(dailyResult.items)
        setVwap(vwapResult.items)
        setLoadedAnalyticsKey(analyticsKey)
      })
      .catch((error: unknown) => {
        if (active) {
          setAnalyticsError(error instanceof Error ? error.message : 'Unable to load analytics.')
          setLoadedAnalyticsKey(analyticsKey)
        }
      })
    return () => { active = false }
  }, [selectedId, appliedFilters, analyticsKey])

  async function refreshDatasets(): Promise<Dataset[]> {
    const result = await api.listDatasets()
    setDatasets(result)
    return result
  }

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    setUploadError('')
    setUploadMessage('')
    const formData = new FormData(form)
    const file = formData.get('file')
    if (!(file instanceof File) || file.size === 0) {
      setUploadError('Choose a CSV or Parquet file to import.')
      return
    }
    if (file.size > 50 * 1024 * 1024) {
      setUploadError('Files must be 50 MB or smaller.')
      return
    }
    if (!/\.(csv|parquet)$/i.test(file.name)) {
      setUploadError('Only CSV and Parquet files are supported.')
      return
    }

    setUploading(true)
    try {
      const result = await api.importDataset(file, frequency)
      setDatasets((current) => [
        { ...result, uploadedAt: new Date().toISOString() },
        ...current.filter((dataset) => dataset.id !== result.id),
      ])
      setSelectedId(result.id)
      setIssuePage(0)
      setFilters(EMPTY_FILTERS)
      setAppliedFilters(EMPTY_FILTERS)
      setUploadMessage(`${result.filename} imported: ${formatNumber(result.rowsImported)} rows accepted, ${formatNumber(result.rowsRejected)} rejected.`)
      form.reset()
      try {
        await refreshDatasets()
      } catch (error) {
        const detail = error instanceof Error ? error.message : 'Unknown error'
        setUploadError(`Import succeeded, but the dataset list could not be refreshed: ${detail}`)
      }
    } catch (error) {
      setUploadError(error instanceof Error ? error.message : 'Import failed. Check the backend and try again.')
    } finally {
      setUploading(false)
    }
  }

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (filters.from && filters.to && filters.to < filters.from) {
      setAnalyticsError('End date must be on or after the start date.')
      return
    }
    setAnalyticsError('')
    setAppliedFilters({ ...filters, contract: filters.contract.trim().toUpperCase() })
  }

  function selectDataset(id: string) {
    setSelectedId(id)
    setAnalyticsError('')
    setIssuePage(0)
    setFilters(EMPTY_FILTERS)
    setAppliedFilters(EMPTY_FILTERS)
  }

  const dailyValues = bars.map((bar) => bar.close)
  const vwapValues = vwap.map((point) => point.value)

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="MarketScope home" onClick={(event) => event.preventDefault()}>
          <span className="brand-mark" aria-hidden="true"><span /><span /><span /></span>
          <span>market<span className="brand-light">scope</span><small>DATA INTELLIGENCE</small></span>
        </a>
        <div className="side-label">WORKSPACE</div>
        <nav className="side-nav" aria-label="Main navigation">
          <button className={view === 'overview' ? 'nav-item active' : 'nav-item'} onClick={() => setView('overview')}>
            <span className="nav-icon">◫</span> Overview
          </button>
          <button className={view === 'quality' ? 'nav-item active' : 'nav-item'} onClick={() => setView('quality')}>
            <span className="nav-icon">⌁</span> Data quality
            {issueCount > 0 && <span className="nav-count">{formatNumber(issueCount)}</span>}
          </button>
        </nav>
        <div className="side-divider" />
        <div className="side-label dataset-label">DATASETS <span>{datasets.length}</span></div>
        {datasets.length > 0 ? (
          <div className="dataset-list">
            {datasets.slice(0, 5).map((dataset) => (
              <button
                key={dataset.id}
                className={selectedId === dataset.id ? 'dataset-option selected' : 'dataset-option'}
                onClick={() => selectDataset(dataset.id)}
                title={dataset.filename}
              >
                <span className="dataset-file-icon">{dataset.frequency === 'minute' ? '◷' : '▤'}</span>
                <span className="dataset-option-text"><strong>{dataset.filename}</strong><small>{dataset.frequency} bars · {formatNumber(dataset.rowsImported)} rows</small></span>
                {selectedId === dataset.id && <span className="selected-dot" />}
              </button>
            ))}
            {datasets.length > 5 && <div className="more-datasets">+{datasets.length - 5} more in dataset selector</div>}
          </div>
        ) : (
          <p className="sidebar-empty">{datasetsLoading ? 'Loading datasets…' : 'No datasets yet. Import a file to begin.'}</p>
        )}
        <div className="sidebar-bottom">
          <div className="backend-indicator"><span /> LOCAL BACKEND</div>
          <p>Data is stored in memory and cleared when the backend stops.</p>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumb">Workspace <span>/</span> {view === 'quality' ? 'Data quality' : 'Market overview'}</div>
          <div className="topbar-actions">
            {datasets.length > 0 && (
              <label className="dataset-select-wrap">
                <span className="sr-only">Selected dataset</span>
                <select value={selectedId} onChange={(event) => selectDataset(event.target.value)}>
                  {datasets.map((dataset) => <option key={dataset.id} value={dataset.id}>{dataset.filename}</option>)}
                </select>
              </label>
            )}
            <button className="button button-primary" onClick={() => { setShowImport(true); setUploadError(''); setUploadMessage('') }}>
              <span aria-hidden="true">＋</span> Import data
            </button>
          </div>
        </header>

        <div className="page-content">
          {pageError && <div className="alert alert-error" role="alert">{pageError}<button onClick={() => setPageError('')} aria-label="Dismiss error">×</button></div>}
          {view === 'overview' ? (
            <>
              <section className="page-heading">
                <div>
                  <div className="eyebrow"><span className="live-dot" /> MARKET DATA WORKSPACE</div>
                  <h1>Market overview</h1>
                  <p className="heading-subtitle">Monitor your futures data, surface issues, and explore market activity.</p>
                </div>
                {selectedDataset && <div className="dataset-stamp"><span className="stamp-icon">▤</span><span><strong>{selectedDataset.filename}</strong><small>Added {formatDate(selectedDataset.uploadedAt)}</small></span></div>}
              </section>

              {datasets.length === 0 && !datasetsLoading ? (
                <section className="welcome-card">
                  <div className="welcome-graphic" aria-hidden="true"><span>↗</span><i /><i /><i /></div>
                  <div><div className="eyebrow">YOUR MARKET DATA, IN FOCUS</div><h2>Start with a dataset</h2><p>Import historical futures bars in CSV or Parquet format to review data quality and generate analytics.</p><button className="button button-primary" onClick={() => setShowImport(true)}>Import your first dataset <span aria-hidden="true">→</span></button></div>
                </section>
              ) : (
                <>
                  {selectedDataset && <section className="stats-grid" aria-label="Dataset summary">
                    <StatCard title="ROWS IMPORTED" value={formatNumber(importedRows)} note={selectedDataset.frequency === 'minute' ? 'Minute frequency' : 'Daily frequency'} icon="▤" tone="blue" />
                    <StatCard title="QUALITY FINDINGS" value={formatNumber(issueCount)} note={issueCount === 0 ? 'No issues identified' : `${issueTypes} issue types on this page`} icon="⌁" tone="amber" />
                    <StatCard title="ROWS REJECTED" value={formatNumber(rejectedRows)} note={rejectionRate} icon="↗" tone="rose" />
                    <StatCard title="DAILY BARS" value={formatNumber(bars.length)} note={analyticsLoading ? 'Updating analytics…' : `${formatNumber(vwap.length)} VWAP points`} icon="⌗" tone="green" />
                  </section>}

                  <section className="panel filter-panel">
                    <div className="panel-title-row"><div><h2>Market analytics</h2><p>Explore daily OHLCV bars and rolling 15-minute VWAP.</p></div><span className="panel-tag">OHLCV APPROXIMATION</span></div>
                    <form className="filter-form" onSubmit={applyFilters}>
                      <label className="filter-field contract-field"><span>Contract</span><input value={filters.contract} onChange={(event) => setFilters({ ...filters, contract: event.target.value })} placeholder="All contracts" /></label>
                      <label className="filter-field"><span>From</span><input type="date" value={filters.from} onChange={(event) => setFilters({ ...filters, from: event.target.value })} /></label>
                      <label className="filter-field"><span>To</span><input type="date" value={filters.to} onChange={(event) => setFilters({ ...filters, to: event.target.value })} /></label>
                      <button className="button button-dark" type="submit" disabled={!selectedId || analyticsLoading}>Apply filters</button>
                    </form>
                    {analyticsError && <div className="inline-error" role="alert">{analyticsError}</div>}
                  </section>

                  <div className="charts-grid">
                    <section className="panel chart-panel">
                      <div className="chart-heading"><div><span className="chart-kicker">DAILY OHLCV</span><h2>Closing price</h2></div><span className="chart-current">{bars.length ? formatNumber(bars[bars.length - 1].close) : '—'}</span></div>
                      {analyticsLoading ? <div className="chart-empty">Loading daily bars…</div> : <Chart values={dailyValues} label="daily" color="#3977ef" formatValue={formatNumber} />}
                      <div className="chart-footer"><span>{bars.length ? `${formatDate(bars[0].date)} – ${formatDate(bars[bars.length - 1].date)}` : 'No bars in this date range'}</span><span className="chart-legend"><i className="legend-blue" /> Close</span></div>
                    </section>
                    <section className="panel chart-panel">
                      <div className="chart-heading"><div><span className="chart-kicker">ROLLING 15-MINUTE</span><h2>VWAP</h2></div><span className="chart-current">{vwap.length ? formatNumber(vwap[vwap.length - 1].value) : '—'}</span></div>
                      {analyticsLoading ? <div className="chart-empty">Loading VWAP…</div> : <Chart values={vwapValues} label="vwap" color="#19a882" formatValue={formatNumber} />}
                      <div className="chart-footer"><span>{vwap.length ? `${formatTimestamp(vwap[0].timestamp)} – ${formatTimestamp(vwap[vwap.length - 1].timestamp)}` : 'No VWAP points in this date range'}</span><span className="chart-legend"><i className="legend-green" /> VWAP</span></div>
                    </section>
                  </div>

                  <section className="panel bars-panel">
                    <div className="panel-title-row"><div><h2>Daily bars</h2><p>Aggregated open, high, low, close, and volume by contract.</p></div><span className="table-count">{formatNumber(bars.length)} shown</span></div>
                    <DailyBarsTable bars={bars.slice(0, 8)} loading={analyticsLoading} />
                  </section>

                  <section className="insights-section">
                    <div className="insights-heading"><div><span className="insights-spark">✳</span><div><h2>Intelligent insights</h2><p>Rule-based patterns and recommended actions for this dataset.</p></div></div><button className="text-button" onClick={() => setView('quality')}>View quality report <span aria-hidden="true">→</span></button></div>
                    {insights.length > 0 ? <div className="insights-grid">{insights.slice(0, 3).map((insight) => <InsightCard key={insight.code} insight={insight} />)}</div> : <div className="insight-empty">{selectedId ? 'No quality patterns to highlight. Your dataset is looking good.' : 'Import a dataset to generate insights.'}</div>}
                  </section>
                </>
              )}
            </>
          ) : (
            <section className="quality-page">
              <section className="page-heading">
                <div><div className="eyebrow"><span className="live-dot" /> DATA QUALITY</div><h1>Quality report</h1><p className="heading-subtitle">Review validation findings, malformed records, duplicates, and missing timestamps.</p></div>
                {selectedDataset && <div className="dataset-stamp"><span className="stamp-icon">▤</span><span><strong>{selectedDataset.filename}</strong><small>{selectedDataset.frequency} dataset</small></span></div>}
              </section>
              <div className="quality-summary">
                <div className="quality-summary-icon">⌁</div><div><strong>{formatNumber(issueCount)} findings</strong><span>{issueCount === 0 ? 'No quality issues found for this dataset.' : 'Findings are grouped by source record and issue type.'}</span></div>
                <span className="quality-types">{formatNumber(issueTypes)} types on this page</span>
              </div>
              <section className="panel quality-panel">
                <div className="panel-title-row"><div><h2>Validation findings</h2><p>{selectedDataset ? selectedDataset.filename : 'Select a dataset to see findings'}</p></div><span className="table-count">{issueCount ? `Page ${issuePage + 1} of ${qualityPages}` : '0 findings'}</span></div>
                <QualityTable issues={issues} loading={datasetsLoading || qualityLoading} />
                <div className="pagination">
                  <span>{issueCount ? `Showing ${issuePage * 10 + 1}–${Math.min((issuePage + 1) * 10, issueCount)} of ${formatNumber(issueCount)}` : 'No findings to display'}</span>
                  <div><button className="page-button" disabled={issuePage === 0} onClick={() => setIssuePage(issuePage - 1)}>← Previous</button><button className="page-button" disabled={issuePage + 1 >= qualityPages} onClick={() => setIssuePage(issuePage + 1)}>Next →</button></div>
                </div>
              </section>
              <section className="insights-section quality-insights"><div className="insights-heading"><div><span className="insights-spark">✳</span><div><h2>Suggested actions</h2><p>Recommendations generated from recurring patterns.</p></div></div></div>
                {insights.length > 0 ? <div className="insights-grid">{insights.map((insight) => <InsightCard key={insight.code} insight={insight} />)}</div> : <div className="insight-empty">No recommendations are available yet.</div>}
              </section>
            </section>
          )}
          <footer className="page-footer"><span>MARKETSCOPE <span className="footer-dot">·</span> LOCAL DATA WORKSPACE</span><span>Analytics are calculated from the selected dataset</span></footer>
        </div>
      </main>

      {showImport && (
        <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setShowImport(false) }}>
          <section className="import-modal" role="dialog" aria-modal="true" aria-labelledby="import-title">
            <div className="modal-header"><div><div className="eyebrow">ADD TO WORKSPACE</div><h2 id="import-title">Import market data</h2></div><button className="icon-button" onClick={() => setShowImport(false)} aria-label="Close import dialog">×</button></div>
            {uploadMessage && <div className="alert alert-success" role="status">{uploadMessage}</div>}
            <form onSubmit={handleUpload}>
              <label className="upload-drop">
                <span className="upload-icon">↑</span><strong>Choose a data file</strong><span>CSV or Parquet · up to 50 MB</span>
                <input name="file" type="file" accept=".csv,.parquet" required />
              </label>
              <label className="filter-field frequency-field"><span>Data frequency</span><select value={frequency} onChange={(event) => setFrequency(event.target.value as 'minute' | 'daily')}><option value="minute">Minute bars</option><option value="daily">Daily bars</option></select></label>
              <div className="import-note"><strong>Required fields</strong><span>Contract, timestamp, open, high, low, close, and volume.</span></div>
              {uploadError && <div className="alert alert-error" role="alert">{uploadError}</div>}
              <div className="modal-actions"><button type="button" className="button button-quiet" onClick={() => setShowImport(false)}>Cancel</button><button type="submit" className="button button-primary" disabled={uploading}>{uploading ? 'Importing…' : 'Import dataset'}</button></div>
            </form>
          </section>
        </div>
      )}
    </div>
  )
}

function DailyBarsTable({ bars, loading }: { bars: DailyBar[]; loading: boolean }) {
  if (loading) return <div className="table-empty">Loading daily bars…</div>
  if (bars.length === 0) return <div className="table-empty">No daily bars match your filters.</div>
  return (
    <div className="table-scroll"><table><thead><tr><th>DATE</th><th>CONTRACT</th><th className="numeric">OPEN</th><th className="numeric">HIGH</th><th className="numeric">LOW</th><th className="numeric">CLOSE</th><th className="numeric">VOLUME</th></tr></thead>
      <tbody>{bars.map((bar) => <tr key={`${bar.contract}-${bar.date}`}><td>{formatDate(bar.date)}</td><td><span className="contract-chip">{bar.contract}</span></td><td className="numeric">{formatNumber(bar.open)}</td><td className="numeric">{formatNumber(bar.high)}</td><td className="numeric">{formatNumber(bar.low)}</td><td className="numeric price-cell">{formatNumber(bar.close)}</td><td className="numeric">{formatNumber(bar.volume)}</td></tr>)}</tbody>
    </table></div>
  )
}

function QualityTable({ issues, loading }: { issues: QualityIssue[]; loading: boolean }) {
  if (loading) return <div className="table-empty">Loading quality findings…</div>
  if (issues.length === 0) return <div className="table-empty"><span className="empty-check">✓</span><strong>All clear</strong><span>No findings on this page.</span></div>
  return (
    <div className="table-scroll"><table><thead><tr><th>ISSUE</th><th>CONTRACT</th><th>TIMESTAMP</th><th>ROW</th><th>DETAIL</th></tr></thead>
      <tbody>{issues.map((issue, index) => <tr key={`${issue.code}-${issue.rowNumber ?? index}-${issue.timestamp ?? ''}`}><td><span className={`issue-badge ${issue.code.includes('INVALID') || issue.code.includes('MALFORMED') ? 'issue-red' : issue.code.includes('MISSING') ? 'issue-amber' : 'issue-purple'}`}>{issue.code.replaceAll('_', ' ')}</span></td><td>{issue.contract ?? '—'}</td><td className="timestamp-cell">{formatTimestamp(issue.timestamp)}</td><td>{issue.rowNumber ?? '—'}</td><td className="issue-message">{issue.message}</td></tr>)}</tbody>
    </table></div>
  )
}

function InsightCard({ insight }: { insight: Insight }) {
  return (
    <article className="insight-card"><span className="insight-code">{insight.code.replaceAll('_', ' ')}</span><h3>{insight.title}</h3><p>{insight.description}</p><div className="recommendation"><span>RECOMMENDED ACTION</span><p>{insight.recommendation}</p></div>{insight.evidence.length > 0 && <div className="evidence">{insight.evidence.slice(0, 2).join(' · ')}</div>}</article>
  )
}

export default App
