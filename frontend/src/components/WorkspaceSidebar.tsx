import type { Dataset } from '../api'
import { formatNumber } from './formatters'

interface WorkspaceSidebarProps {
  datasets: Dataset[]
  selectedId: string
  loading: boolean
  issueCount: number
  activeView: 'overview' | 'quality'
  onSelectDataset: (id: string) => void
  onChangeView: (view: 'overview' | 'quality') => void
}

export function WorkspaceSidebar({
  datasets,
  selectedId,
  loading,
  issueCount,
  activeView,
  onSelectDataset,
  onChangeView,
}: WorkspaceSidebarProps) {
  return (
    <aside className="sidebar">
      <a className="brand" href="#" aria-label="MarketScope home" onClick={(event) => event.preventDefault()}>
        <span className="brand-mark" aria-hidden="true"><span /><span /><span /></span>
        <span>market<span className="brand-light">scope</span><small>DATA INTELLIGENCE</small></span>
      </a>
      <div className="side-label">WORKSPACE</div>
      <nav className="side-nav" aria-label="Main navigation">
        <button className={activeView === 'overview' ? 'nav-item active' : 'nav-item'} onClick={() => onChangeView('overview')}>
          <span className="nav-icon">◫</span> Overview
        </button>
        <button className={activeView === 'quality' ? 'nav-item active' : 'nav-item'} onClick={() => onChangeView('quality')}>
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
              onClick={() => onSelectDataset(dataset.id)}
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
        <p className="sidebar-empty">{loading ? 'Loading datasets…' : 'No datasets yet. Import a file to begin.'}</p>
      )}
      <div className="sidebar-bottom">
        <div className="backend-indicator"><span /> LOCAL BACKEND</div>
        <p>Data is stored in memory and cleared when the backend stops.</p>
      </div>
    </aside>
  )
}
