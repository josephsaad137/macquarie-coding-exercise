import { useRef, useState, type FormEvent } from 'react'
import type { ImportResult } from '../api'

interface ImportDialogProps {
  onClose: () => void
  onImport: (file: File, frequency: 'minute' | 'daily') => Promise<ImportResult>
}

export function ImportDialog({ onClose, onImport }: ImportDialogProps) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [frequency, setFrequency] = useState<'minute' | 'daily'>('minute')
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setMessage('')
    if (!file || file.size === 0) {
      setError('Choose a CSV or Parquet file to import.')
      return
    }
    if (file.size > 50 * 1024 * 1024) {
      setError('Files must be 50 MB or smaller.')
      return
    }
    if (!/\.(csv|parquet)$/i.test(file.name)) {
      setError('Only CSV and Parquet files are supported.')
      return
    }

    setUploading(true)
    try {
      const result = await onImport(file, frequency)
      setMessage(`${result.filename} imported: ${new Intl.NumberFormat('en-US').format(result.rowsImported)} rows accepted, ${new Intl.NumberFormat('en-US').format(result.rowsRejected)} rejected.`)
      setFile(null)
      if (fileInput.current) fileInput.current.value = ''
    } catch (importError) {
      setError(importError instanceof Error ? importError.message : 'Import failed. Check the backend and try again.')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="import-modal" role="dialog" aria-modal="true" aria-labelledby="import-title">
        <div className="modal-header">
          <div><div className="eyebrow">ADD TO WORKSPACE</div><h2 id="import-title">Import market data</h2></div>
          <button className="icon-button" onClick={onClose} aria-label="Close import dialog">×</button>
        </div>
        {message && <div className="alert alert-success" role="status">{message}</div>}
        <form onSubmit={handleSubmit}>
          <label className="upload-drop">
            <span className="upload-icon">↑</span><strong>{file?.name ?? 'Choose a data file'}</strong><span>CSV or Parquet · up to 50 MB</span>
            <input ref={fileInput} name="file" type="file" accept=".csv,.parquet" onChange={(event) => setFile(event.currentTarget.files?.[0] ?? null)} />
          </label>
          <label className="filter-field frequency-field"><span>Data frequency</span><select value={frequency} onChange={(event) => setFrequency(event.target.value as 'minute' | 'daily')}><option value="minute">Minute bars</option><option value="daily">Daily bars</option></select></label>
          <div className="import-note"><strong>Required fields</strong><span>Contract, timestamp, open, high, low, close, and volume.</span></div>
          {error && <div className="alert alert-error" role="alert">{error}</div>}
          <div className="modal-actions"><button type="button" className="button button-quiet" onClick={onClose}>Cancel</button><button type="submit" className="button button-primary" disabled={uploading}>{uploading ? 'Importing…' : 'Import dataset'}</button></div>
        </form>
      </section>
    </div>
  )
}
