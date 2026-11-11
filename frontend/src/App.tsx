import { useEffect, useMemo, useState } from 'react'
import { api, type DailyBar, type Dataset, type ImportResult, type QualityIssue, type VwapPoint } from './api'
import { ImportDialog } from './components/ImportDialog'
import { MarketOverview, type AnalyticsFilters } from './components/MarketOverview'
import { QualityReport } from './components/QualityReport'
import { TopBar } from './components/TopBar'
import { WorkspaceSidebar } from './components/WorkspaceSidebar'
import './App.css'

type View = 'overview' | 'quality'

const EMPTY_FILTERS: AnalyticsFilters = { contract: '', from: '', to: '' }

function App() {
  const [datasets, setDatasets] = useState<Dataset[]>([])
  const [selectedId, setSelectedId] = useState('')
  const [view, setView] = useState<View>('overview')
  const [filters, setFilters] = useState<AnalyticsFilters>(EMPTY_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState<AnalyticsFilters>(EMPTY_FILTERS)
  const [bars, setBars] = useState<DailyBar[]>([])
  const [vwap, setVwap] = useState<VwapPoint[]>([])
  const [issues, setIssues] = useState<QualityIssue[]>([])
  const [issueCount, setIssueCount] = useState(0)
  const [issuePage, setIssuePage] = useState(0)
  const [loadedQualityKey, setLoadedQualityKey] = useState('')
  const [insights, setInsights] = useState<Awaited<ReturnType<typeof api.insights>>>([])
  const [datasetsLoading, setDatasetsLoading] = useState(true)
  const [loadedAnalyticsKey, setLoadedAnalyticsKey] = useState('')
  const [pageError, setPageError] = useState('')
  const [analyticsError, setAnalyticsError] = useState('')
  const [showImport, setShowImport] = useState(false)

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

  async function handleImport(file: File, frequency: 'minute' | 'daily'): Promise<ImportResult> {
    const result = await api.importDataset(file, frequency)
    setDatasets((current) => [
      { ...result, uploadedAt: new Date().toISOString() },
      ...current.filter((dataset) => dataset.id !== result.id),
    ])
    setSelectedId(result.id)
    setIssuePage(0)
    setFilters(EMPTY_FILTERS)
    setAppliedFilters(EMPTY_FILTERS)
    try {
      setDatasets(await api.listDatasets())
    } catch (error) {
      const detail = error instanceof Error ? error.message : 'Unknown error'
      setPageError(`Import succeeded, but the dataset list could not be refreshed: ${detail}`)
    }
    return result
  }

  function selectDataset(id: string) {
    setSelectedId(id)
    setAnalyticsError('')
    setIssuePage(0)
    setFilters(EMPTY_FILTERS)
    setAppliedFilters(EMPTY_FILTERS)
  }

  return (
    <div className="app-shell">
      <WorkspaceSidebar
        datasets={datasets}
        selectedId={selectedId}
        loading={datasetsLoading}
        issueCount={issueCount}
        activeView={view}
        onSelectDataset={selectDataset}
        onChangeView={setView}
      />
      <main className="main-content">
        <TopBar
          datasets={datasets}
          selectedId={selectedId}
          activeView={view}
          onSelectDataset={selectDataset}
          onImport={() => setShowImport(true)}
        />
        <div className="page-content">
          {pageError && <div className="alert alert-error" role="alert">{pageError}<button onClick={() => setPageError('')} aria-label="Dismiss error">×</button></div>}
          {view === 'overview' ? (
            <MarketOverview
              dataset={selectedDataset}
              selectedId={selectedId}
              importedRows={importedRows}
              rejectedRows={rejectedRows}
              rejectionRate={rejectionRate}
              issueCount={issueCount}
              issueTypes={issueTypes}
              bars={bars}
              vwap={vwap}
              insights={insights}
              analyticsLoading={analyticsLoading}
              analyticsError={analyticsError}
              filters={filters}
              onFiltersChange={setFilters}
              onApplyFilters={(nextFilters) => {
                setAnalyticsError('')
                setAppliedFilters(nextFilters)
              }}
              onImport={() => setShowImport(true)}
              onViewQuality={() => setView('quality')}
            />
          ) : (
            <QualityReport
              dataset={selectedDataset}
              issueCount={issueCount}
              issueTypes={issueTypes}
              issues={issues}
              issuePage={issuePage}
              qualityPages={qualityPages}
              loading={datasetsLoading || qualityLoading}
              insights={insights}
              onIssuePageChange={setIssuePage}
            />
          )}
          <footer className="page-footer"><span>MARKETSCOPE <span className="footer-dot">·</span> LOCAL DATA WORKSPACE</span><span>Analytics are calculated from the selected dataset</span></footer>
        </div>
      </main>
      {showImport && (
        <ImportDialog
          onClose={() => setShowImport(false)}
          onImport={handleImport}
        />
      )}
    </div>
  )
}

export default App
