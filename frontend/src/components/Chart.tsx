import type { ReactNode } from 'react'

export interface ChartProps {
  values: number[]
  label: string
  color: string
  formatValue: (value: number) => string
  xAxisStart?: string
  xAxisEnd?: string
  emptyMessage?: ReactNode
}

export function Chart({
  values,
  label,
  color,
  formatValue,
  xAxisStart,
  xAxisEnd,
  emptyMessage = 'Import market data to see your chart.',
}: ChartProps) {
  if (values.length === 0) {
    return <div className="chart-empty">{emptyMessage}</div>
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
    <div className="chart-figure">
      <div className="chart-wrap">
        <div className="chart-range"><span>{formatValue(high)}</span><span>{formatValue(low)}</span></div>
        <svg className="chart" role="img" aria-label={`${label} trend, latest ${formatValue(latest)}`} viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none">
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
      {xAxisStart && xAxisEnd && (
        <div className="chart-x-axis" aria-label="Date range">
          <span>{xAxisStart}</span>
          <span>{xAxisEnd}</span>
        </div>
      )}
    </div>
  )
}
