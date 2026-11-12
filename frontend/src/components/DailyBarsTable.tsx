import { DAILY_BARS_PAGE_SIZE, type DailyBar } from '../api'
import { formatDate, formatNumber } from './formatters'

interface DailyBarsTableProps {
  bars: DailyBar[]
  loading: boolean
  page: number
  pageCount: number
  totalRows: number
  onPageChange: (page: number) => void
}

export function DailyBarsTable({ bars, loading, page, pageCount, totalRows, onPageChange }: DailyBarsTableProps) {
  if (!loading && totalRows === 0) return <div className="table-empty">No daily bars match your filters.</div>

  const start = page * DAILY_BARS_PAGE_SIZE

  return (
    <>
      {loading ? (
        <div className="table-empty" aria-live="polite">Loading daily bars…</div>
      ) : (
        <div className="table-scroll">
          <table>
            <thead><tr><th>DATE</th><th>CONTRACT</th><th className="numeric">OPEN</th><th className="numeric">HIGH</th><th className="numeric">LOW</th><th className="numeric">CLOSE</th><th className="numeric">VOLUME</th></tr></thead>
            <tbody>{bars.map((bar) => (
              <tr key={`${bar.contract}-${bar.date}`}>
                <td>{formatDate(bar.date)}</td><td><span className="contract-chip">{bar.contract}</span></td>
                <td className="numeric">{formatNumber(bar.open)}</td><td className="numeric">{formatNumber(bar.high)}</td>
                <td className="numeric">{formatNumber(bar.low)}</td><td className="numeric price-cell">{formatNumber(bar.close)}</td>
                <td className="numeric">{formatNumber(bar.volume)}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {totalRows > 0 && (
        <div className="pagination">
          <span>Showing {formatNumber(start + 1)}–{formatNumber(Math.min(start + bars.length, totalRows))} of {formatNumber(totalRows)}</span>
          <div>
            <button className="page-button" disabled={page === 0} onClick={() => onPageChange(page - 1)}>← Previous</button>
            <button className="page-button" disabled={page + 1 >= pageCount} onClick={() => onPageChange(page + 1)}>Next →</button>
          </div>
        </div>
      )}
    </>
  )
}
