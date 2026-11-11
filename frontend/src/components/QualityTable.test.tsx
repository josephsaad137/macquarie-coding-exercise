import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { QualityIssue } from '../api'
import { QualityTable } from './QualityTable'

const issues: QualityIssue[] = [
  { rowNumber: 8, contract: 'ESH25', timestamp: '2025-01-02T09:31:00', code: 'INVALID_OHLC', message: 'High is below low' },
  { rowNumber: null, contract: null, timestamp: null, code: 'MISSING_TIMESTAMP', message: '3 expected bars are missing' },
  { rowNumber: 12, contract: 'ESH25', timestamp: null, code: 'DUPLICATE_BAR', message: 'Duplicate bar' },
]

describe('QualityTable', () => {
  it('renders findings with severity-specific badges and nullable details', () => {
    const { container } = render(<QualityTable issues={issues} loading={false} />)
    expect(screen.getByText('INVALID OHLC')).toHaveClass('issue-red')
    expect(screen.getByText('MISSING TIMESTAMP')).toHaveClass('issue-amber')
    expect(screen.getByText('DUPLICATE BAR')).toHaveClass('issue-purple')
    expect(screen.getByText('2025-01-02 09:31:00')).toBeInTheDocument()
    expect(container.querySelectorAll('tbody tr')).toHaveLength(3)
  })

  it('shows loading and clean-dataset states', () => {
    const { rerender } = render(<QualityTable issues={[]} loading />)
    expect(screen.getByText('Loading quality findings…')).toBeInTheDocument()
    rerender(<QualityTable issues={[]} loading={false} />)
    expect(screen.getByText('All clear')).toBeInTheDocument()
  })
})
