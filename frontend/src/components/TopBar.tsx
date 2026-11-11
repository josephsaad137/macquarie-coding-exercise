import type { Dataset } from '../api'

interface TopBarProps {
  datasets: Dataset[]
  selectedId: string
  activeView: 'overview' | 'quality'
  onSelectDataset: (id: string) => void
  onImport: () => void
}

export function TopBar({ datasets, selectedId, activeView, onSelectDataset, onImport }: TopBarProps) {
  return (
    <header className="topbar">
      <div className="breadcrumb">Workspace <span>/</span> {activeView === 'quality' ? 'Data quality' : 'Market overview'}</div>
      <div className="topbar-actions">
        {datasets.length > 0 && (
          <label className="dataset-select-wrap">
            <span className="sr-only">Selected dataset</span>
            <select value={selectedId} onChange={(event) => onSelectDataset(event.target.value)}>
              {datasets.map((dataset) => <option key={dataset.id} value={dataset.id}>{dataset.filename}</option>)}
            </select>
          </label>
        )}
        <button className="button button-primary" onClick={onImport}>
          <span aria-hidden="true">＋</span> Import data
        </button>
      </div>
    </header>
  )
}
