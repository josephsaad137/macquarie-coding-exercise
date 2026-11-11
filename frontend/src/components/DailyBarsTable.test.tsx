import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
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
    render(<DailyBarsTable bars={[bar]} loading={false} />)
    expect(screen.getByText('Jan 2, 2025')).toBeInTheDocument()
    expect(screen.getByText('ESH25')).toBeInTheDocument()
    expect(screen.getByText('103.25')).toBeInTheDocument()
    expect(screen.getByText('4,200')).toBeInTheDocument()
  })

  it('shows loading and empty states instead of a table', () => {
    const { rerender } = render(<DailyBarsTable bars={[]} loading />)
    expect(screen.getByText('Loading daily bars…')).toBeInTheDocument()
    rerender(<DailyBarsTable bars={[]} loading={false} />)
    expect(screen.getByText('No daily bars match your filters.')).toBeInTheDocument()
  })
})
