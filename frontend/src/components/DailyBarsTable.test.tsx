import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { DailyBar } from '../api'
import { DailyBarsTable } from './DailyBarsTable'

const bar: DailyBar = {
  contract: 'ESH25',
  date: '2025-01-02',
  open: 100,
  high: 105,
  low: 98,
  close: 103.25,
  volume: 4200,
}

describe('DailyBarsTable', () => {
  it('renders OHLCV rows with formatted values', () => {
    render(<DailyBarsTable bars={[bar]} loading={false} page={0} pageCount={1} totalRows={1} onPageChange={vi.fn()} />)
    expect(screen.getByText('Jan 2, 2025')).toBeInTheDocument()
    expect(screen.getByText('ESH25')).toBeInTheDocument()
    expect(screen.getByText('103.25')).toBeInTheDocument()
    expect(screen.getByText('4,200')).toBeInTheDocument()
  })

  it('shows loading and empty states instead of a table', () => {
    const props = { bars: [], page: 0, pageCount: 0, totalRows: 0, onPageChange: vi.fn() }
    const { rerender } = render(<DailyBarsTable {...props} loading />)
    expect(screen.getByText('Loading daily bars…')).toBeInTheDocument()
    rerender(<DailyBarsTable {...props} loading={false} />)
    expect(screen.getByText('No daily bars match your filters.')).toBeInTheDocument()
  })

  it('keeps the pagination controls mounted while loading the next page', () => {
    const onPageChange = vi.fn()
    const props = { bars: [bar], pageCount: 2, totalRows: 11, onPageChange }
    const { rerender } = render(<DailyBarsTable {...props} loading={false} page={0} />)
    const nextButton = screen.getByRole('button', { name: 'Next →' })
    nextButton.focus()
    fireEvent.click(nextButton)

    rerender(<DailyBarsTable {...props} loading page={0} />)

    expect(screen.getByText('Loading daily bars…')).toHaveAttribute('aria-live', 'polite')
    expect(screen.getByRole('button', { name: 'Next →' })).toBe(nextButton)
    expect(document.activeElement).toBe(nextButton)

    rerender(<DailyBarsTable {...props} loading={false} page={1} />)
    expect(screen.getByText('Showing 11–11 of 11')).toBeInTheDocument()
  })

  it('requests pages through all bars and disables navigation at the boundaries', () => {
    const onPageChange = vi.fn()
    const bars = Array.from({ length: 25 }, (_, index) => ({
      ...bar,
      date: `2025-01-${String(index + 1).padStart(2, '0')}`,
    }))
    const { rerender } = render(
      <DailyBarsTable bars={bars.slice(0, 10)} loading={false} page={0} pageCount={3} totalRows={25} onPageChange={onPageChange} />,
    )

    expect(screen.getByText('Showing 1–10 of 25')).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(11)
    expect(screen.getByRole('button', { name: '← Previous' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Next →' }))
    expect(onPageChange).toHaveBeenLastCalledWith(1)
    rerender(
      <DailyBarsTable bars={bars.slice(10, 20)} loading={false} page={1} pageCount={3} totalRows={25} onPageChange={onPageChange} />,
    )
    expect(screen.getByText('Showing 11–20 of 25')).toBeInTheDocument()
    expect(screen.getByText('Jan 11, 2025')).toBeInTheDocument()
    expect(screen.queryByText('Jan 1, 2025')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Next →' }))
    expect(onPageChange).toHaveBeenLastCalledWith(2)
    rerender(
      <DailyBarsTable bars={bars.slice(20)} loading={false} page={2} pageCount={3} totalRows={25} onPageChange={onPageChange} />,
    )
    expect(screen.getByText('Showing 21–25 of 25')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next →' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: '← Previous' }))
    expect(onPageChange).toHaveBeenLastCalledWith(1)
  })
})
