import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Dataset } from '../api'
import { PageHeading } from './PageHeading'

const dataset: Dataset = {
  id: 'one',
  filename: 'bars.csv',
  status: 'COMPLETED',
  rowsImported: 10,
  rowsRejected: 0,
  uploadedAt: '2025-01-02T00:00:00Z',
  frequency: 'minute',
}

describe('PageHeading', () => {
  it('shows workspace context and formats the selected dataset date', () => {
    render(<PageHeading eyebrow="MARKET DATA" title="Market overview" subtitle="Explore futures data." dataset={dataset} />)
    expect(screen.getByRole('heading', { name: 'Market overview' })).toBeInTheDocument()
    expect(screen.getByText('bars.csv')).toBeInTheDocument()
    expect(screen.getByText('Added Jan 2, 2025')).toBeInTheDocument()
  })

  it('does not render a dataset stamp when no dataset is selected', () => {
    const { container } = render(<PageHeading eyebrow="DATA QUALITY" title="Quality report" subtitle="Findings." />)
    expect(container.querySelector('.dataset-stamp')).not.toBeInTheDocument()
  })
})
