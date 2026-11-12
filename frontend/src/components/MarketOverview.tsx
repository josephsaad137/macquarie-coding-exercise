import { useState, type FormEvent } from 'react'
import type { DailyBar, Dataset, Insight, VwapPoint } from '../api'
import { Chart } from './Chart'
import { DailyBarsTable } from './DailyBarsTable'
import { InsightsPanel } from './InsightsPanel'
import { PageHeading } from './PageHeading'
import { formatDate, formatNumber, formatTimestamp } from './formatters'
import { StatCard } from './StatCard'

export interface AnalyticsFilters {
  contract: string
  from: string
  to: string
}

interface MarketOverviewProps {
  dataset?: Dataset
  selectedId: string
  importedRows: number
  rejectedRows: number
  rejectionRate: string
  issueCount: number
  issueTypes: number
  dailyBarCount: number
  chartPointLimit: number
  dailyBarRange: { start: string | null; end: string | null }
  vwapRange: { start: string | null; end: string | null }
  tableBars: DailyBar[]
  dailyBarPage: number
  dailyBarPages: number
  dailyBarsLoading: boolean
  vwapPointCount: number
  bars: DailyBar[]
  vwap: VwapPoint[]
  insights: Insight[]
  analyticsLoading: boolean
  analyticsError: string
  filters: AnalyticsFilters
  onFiltersChange: (filters: AnalyticsFilters) => void
  onApplyFilters: (filters: AnalyticsFilters) => void
  onDailyBarPageChange: (page: number) => void
  onImport: () => void
  onViewQuality: () => void
}

export function MarketOverview({
  dataset,
  selectedId,
  importedRows,
  rejectedRows,
  rejectionRate,
  issueCount,
  issueTypes,
  dailyBarCount,
  chartPointLimit,
  dailyBarRange,
  vwapRange,
  tableBars,
  dailyBarPage,
  dailyBarPages,
  dailyBarsLoading,
  vwapPointCount,
  bars,
  vwap,
  insights,
  analyticsLoading,
  analyticsError,
  filters,
  onFiltersChange,
  onApplyFilters,
  onDailyBarPageChange,
  onImport,
  onViewQuality,
}: MarketOverviewProps) {
  const [filterError, setFilterError] = useState('')

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (filters.from && filters.to && filters.to < filters.from) {
      setFilterError('End date must be on or after the start date.')
      return
    }
    setFilterError('')
    onApplyFilters({ ...filters, contract: filters.contract.trim().toUpperCase() })
  }

  return (
    <>
      <PageHeading eyebrow="MARKET DATA WORKSPACE" title="Market overview" subtitle="Monitor your futures data, surface issues, and explore market activity." dataset={dataset} />
      {!dataset && !selectedId ? (
        <section className="welcome-card">
          <div className="welcome-graphic" aria-hidden="true"><span>↗</span><i /><i /><i /></div>
          <div><div className="eyebrow">YOUR MARKET DATA, IN FOCUS</div><h2>Start with a dataset</h2><p>Import historical futures bars in CSV or Parquet format to review data quality and generate analytics.</p><button className="button button-primary" onClick={onImport}>Import your first dataset <span aria-hidden="true">→</span></button></div>
        </section>
      ) : (
        <>
          {dataset && <section className="stats-grid" aria-label="Dataset summary">
            <StatCard title="ROWS IMPORTED" value={formatNumber(importedRows)} note={dataset.frequency === 'minute' ? 'Minute frequency' : 'Daily frequency'} icon="▤" tone="blue" />
            <StatCard title="QUALITY FINDINGS" value={formatNumber(issueCount)} note={issueCount === 0 ? 'No issues identified' : `${issueTypes} issue types on this page`} icon="⌁" tone="amber" />
            <StatCard title="ROWS REJECTED" value={formatNumber(rejectedRows)} note={rejectionRate} icon="↗" tone="rose" />
            <StatCard title="DAILY BARS" value={formatNumber(dailyBarCount)} note={analyticsLoading ? 'Updating analytics…' : `${formatNumber(vwapPointCount)} VWAP points`} icon="⌗" tone="green" />
          </section>}

          <section className="panel filter-panel">
            <div className="panel-title-row"><div><h2>Market analytics</h2><p>Explore daily OHLCV bars and rolling 15-minute VWAP.</p></div><span className="panel-tag">OHLCV APPROXIMATION</span></div>
            <form className="filter-form" onSubmit={applyFilters}>
              <label className="filter-field contract-field"><span>Contract</span><input value={filters.contract} onChange={(event) => onFiltersChange({ ...filters, contract: event.target.value })} placeholder="All contracts" /></label>
              <label className="filter-field"><span>From</span><input type="date" value={filters.from} onChange={(event) => onFiltersChange({ ...filters, from: event.target.value })} /></label>
              <label className="filter-field"><span>To</span><input type="date" value={filters.to} onChange={(event) => onFiltersChange({ ...filters, to: event.target.value })} /></label>
              <button className="button button-dark" type="submit" disabled={!selectedId || analyticsLoading}>Apply filters</button>
            </form>
            {(filterError || analyticsError) && <div className="inline-error" role="alert">{filterError || analyticsError}</div>}
          </section>

          <div className="charts-grid">
            <section className="panel chart-panel">
              <div className="chart-heading"><div><span className="chart-kicker">DAILY OHLCV</span><h2>Closing price</h2></div><span className="chart-current">{bars.length ? formatNumber(bars[bars.length - 1].close) : '—'}</span></div>
              {analyticsLoading ? <div className="chart-empty">Loading daily bars…</div> : <Chart
                values={bars.map((bar) => bar.close)}
                label="daily"
                color="#3977ef"
                formatValue={formatNumber}
                xAxisStart={bars[0] ? formatDate(bars[0].date) : undefined}
                xAxisEnd={bars.length ? formatDate(bars[bars.length - 1].date) : undefined}
              />}
              <div className="chart-footer">
                <span>{analyticsLoading ? 'Updating date range…' : dailyBarRange.start && dailyBarRange.end ? `Full dataset date range: ${formatDate(dailyBarRange.start)} – ${formatDate(dailyBarRange.end)}` : 'No bars in this date range'}</span>
                <span>{analyticsLoading ? 'Updating chart coverage…' : chartCoverage(dailyBarCount, chartPointLimit)}</span>
                <span className="chart-legend"><i className="legend-blue" /> Close</span>
              </div>
            </section>
            <section className="panel chart-panel">
              <div className="chart-heading"><div><span className="chart-kicker">ROLLING 15-MINUTE</span><h2>VWAP</h2></div><span className="chart-current">{vwap.length ? formatNumber(vwap[vwap.length - 1].value) : '—'}</span></div>
              {analyticsLoading ? <div className="chart-empty">Loading VWAP…</div> : <Chart
                values={vwap.map((point) => point.value)}
                label="vwap"
                color="#19a882"
                formatValue={formatNumber}
                xAxisStart={vwap[0] ? formatTimestamp(vwap[0].timestamp) : undefined}
                xAxisEnd={vwap.length ? formatTimestamp(vwap[vwap.length - 1].timestamp) : undefined}
              />}
              <div className="chart-footer">
                <span>{analyticsLoading ? 'Updating timestamp range…' : vwapRange.start && vwapRange.end ? `Full dataset timestamp range: ${formatTimestamp(vwapRange.start)} – ${formatTimestamp(vwapRange.end)}` : 'No VWAP points in this date range'}</span>
                <span>{analyticsLoading ? 'Updating chart coverage…' : chartCoverage(vwapPointCount, chartPointLimit)}</span>
                <span className="chart-legend"><i className="legend-green" /> VWAP</span>
              </div>
            </section>
          </div>

          <section className="panel bars-panel">
            <div className="panel-title-row"><div><h2>Daily bars</h2><p>Aggregated open, high, low, close, and volume by contract.</p></div><span className="table-count">{formatNumber(dailyBarCount)} rows</span></div>
            <DailyBarsTable
              bars={tableBars}
              loading={dailyBarsLoading}
              page={dailyBarPage}
              pageCount={dailyBarPages}
              totalRows={dailyBarCount}
              onPageChange={onDailyBarPageChange}
            />
          </section>

          <InsightsPanel title="Intelligent insights" description="Rule-based patterns and recommended actions for this dataset." insights={insights.slice(0, 3)} emptyMessage={selectedId ? 'No quality patterns to highlight. Your dataset is looking good.' : 'Import a dataset to generate insights.'} onViewQuality={onViewQuality} />
        </>
      )}
    </>
  )
}

function chartCoverage(totalPoints: number, limit: number): string {
  const shownPoints = Math.min(totalPoints, limit)
  return totalPoints > limit
    ? `Showing ${formatNumber(shownPoints)} of ${formatNumber(totalPoints)} points (limit ${formatNumber(limit)})`
    : `Showing all ${formatNumber(totalPoints)} points (limit ${formatNumber(limit)})`
}
