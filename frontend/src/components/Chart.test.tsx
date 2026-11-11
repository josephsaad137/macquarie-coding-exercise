import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Chart } from './Chart'

describe('Chart', () => {
  it('shows an empty state when no values are available', () => {
    render(<Chart values={[]} label="daily" color="#3977ef" formatValue={String} />)
    expect(screen.getByText('Import market data to see your chart.')).toBeInTheDocument()
  })

  it('describes the latest value and renders a trend for data points', () => {
    render(<Chart values={[10, 20, 15]} label="daily" color="#3977ef" formatValue={(value) => `$${value}`} />)
    expect(screen.getByRole('img', { name: 'daily trend, latest $15' })).toBeInTheDocument()
    expect(screen.getByText('$20')).toBeInTheDocument()
    expect(screen.getByText('$10')).toBeInTheDocument()
    expect(document.querySelector('polyline')).toBeInTheDocument()
  })

  it('handles a single constant value without invalid chart geometry', () => {
    render(<Chart values={[12]} label="daily" color="#3977ef" formatValue={String} />)
    expect(document.querySelector('polyline')?.getAttribute('points')).toMatch(/^\d+(?:\.\d+)?,\d+(?:\.\d+)?$/)
  })
})
