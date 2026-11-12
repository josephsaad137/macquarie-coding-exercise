import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { DailyBar, Dataset, VwapPoint } from '../api'
import { MarketOverview } from './MarketOverview'

const dataset: Dataset = {
  id: 'one',
  filename: 'bars.csv',
  status: 'COMPLETED',
  rowsImported: 5,
  rowsRejected: 1,
  uploadedAt: '2025-01-02T00:00:00Z',
  frequency: 'minute',
}

function renderOverview() {
  const onApplyFilters = vi.fn()
  const onFiltersChange = vi.fn()
  render(
    <MarketOverview
      dataset={dataset}
      selectedId="one"
      importedRows={5}
      rejectedRows={1}
      rejectionRate="16.7% rejected"
      issueCount={0}
      issueTypes={0}
      dailyBarCount={0}
      vwapPointCount={0}
      bars={[]}
      vwap={[]}
      insights={[]}
      analyticsLoading={false}
      analyticsError=""
      filters={{ contract: ' esh25 ', from: '', to: '' }}
      onFiltersChange={onFiltersChange}
      onApplyFilters={onApplyFilters}
      onImport={vi.fn()}
      onViewQuality={vi.fn()}
    />,
  )
  return { onApplyFilters, onFiltersChange }
}

describe('MarketOverview', () => {
  it('normalizes the contract and applies the selected filters', () => {
    const { onApplyFilters } = renderOverview()
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }))
    expect(onApplyFilters).toHaveBeenCalledWith({ contract: 'ESH25', from: '', to: '' })
  })

  it('blocks an inverted date range with a validation message', () => {
    const onApplyFilters = vi.fn()
    render(
      <MarketOverview dataset={dataset} selectedId="one" importedRows={5} rejectedRows={1} rejectionRate="16.7% rejected" issueCount={0} issueTypes={0} dailyBarCount={0} vwapPointCount={0} bars={[]} vwap={[]} insights={[]} analyticsLoading={false} analyticsError="" filters={{ contract: '', from: '2025-02-01', to: '2025-01-01' }} onFiltersChange={vi.fn()} onApplyFilters={onApplyFilters} onImport={vi.fn()} onViewQuality={vi.fn()} />,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }))
    expect(screen.getByRole('alert')).toHaveTextContent('End date must be on or after the start date.')
    expect(onApplyFilters).not.toHaveBeenCalled()
  })

  it('displays analytics values and date ranges from returned market data', () => {
    const bar: DailyBar = {
      contract: 'ESH25',
      date: '2025-01-02',
      open: 100,
      high: 105,
      low: 98,
      close: 103.25,
      volume: 4200,
    }
    const point: VwapPoint = {
      contract: 'ESH25',
      timestamp: '2025-01-02T09:30:00Z',
      value: 102.75,
    }
    render(
      <MarketOverview
        dataset={dataset}
        selectedId="one"
        importedRows={5}
        rejectedRows={1}
        rejectionRate="16.7% rejected"
        issueCount={0}
        issueTypes={0}
        dailyBarCount={501}
        vwapPointCount={12345}
        bars={[bar]}
        vwap={[point]}
        insights={[]}
        analyticsLoading={false}
        analyticsError=""
        filters={{ contract: '', from: '', to: '' }}
        onFiltersChange={vi.fn()}
        onApplyFilters={vi.fn()}
        onImport={vi.fn()}
        onViewQuality={vi.fn()}
      />,
    )

    expect(screen.getByRole('img', { name: 'daily trend, latest 103.25' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'vwap trend, latest 102.75' })).toBeInTheDocument()
    expect(screen.getByText('501')).toBeInTheDocument()
    expect(screen.getByText('12,345 VWAP points')).toBeInTheDocument()
    expect(screen.getByText('Jan 2, 2025')).toBeInTheDocument()
    expect(screen.getByText('4,200')).toBeInTheDocument()
    expect(screen.getByText('2025-01-02 09:30:00Z – 2025-01-02 09:30:00Z')).toBeInTheDocument()
  })

  it('shows the first-dataset onboarding action', () => {
    const onImport = vi.fn()
    render(<MarketOverview selectedId="" importedRows={0} rejectedRows={0} rejectionRate="No rejected rows" issueCount={0} issueTypes={0} dailyBarCount={0} vwapPointCount={0} bars={[]} vwap={[]} insights={[]} analyticsLoading={false} analyticsError="" filters={{ contract: '', from: '', to: '' }} onFiltersChange={vi.fn()} onApplyFilters={vi.fn()} onImport={onImport} onViewQuality={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: /Import your first dataset/ }))
    expect(onImport).toHaveBeenCalledOnce()
  })
})
