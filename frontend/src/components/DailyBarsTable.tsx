import type { DailyBar } from '../api'
import { formatDate, formatNumber } from './formatters'

interface DailyBarsTableProps {
  bars: DailyBar[]
  loading: boolean
}

export function DailyBarsTable({ bars, loading }: DailyBarsTableProps) {
  if (loading) return <div className="table-empty">Loading daily bars…</div>
  if (bars.length === 0) return <div className="table-empty">No daily bars match your filters.</div>
  return (
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
  )
}
