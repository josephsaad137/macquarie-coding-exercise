import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Dataset } from '../api'
import { QualityReport } from './QualityReport'

const dataset: Dataset = {
  id: 'one',
  filename: 'bars.csv',
  status: 'COMPLETED',
  rowsImported: 5,
  rowsRejected: 1,
  uploadedAt: '2025-01-02T00:00:00Z',
  frequency: 'minute',
}

describe('QualityReport', () => {
  it('paginates findings and disables navigation at the page boundaries', () => {
    const onIssuePageChange = vi.fn()
    const { rerender } = render(<QualityReport dataset={dataset} issueCount={21} issueTypes={2} issues={[]} issuePage={1} qualityPages={3} loading={false} insights={[]} onIssuePageChange={onIssuePageChange} />)

    expect(screen.getByText('Showing 11–20 of 21')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '← Previous' }))
    fireEvent.click(screen.getByRole('button', { name: 'Next →' }))
    expect(onIssuePageChange).toHaveBeenNthCalledWith(1, 0)
    expect(onIssuePageChange).toHaveBeenNthCalledWith(2, 2)

    rerender(<QualityReport dataset={dataset} issueCount={21} issueTypes={2} issues={[]} issuePage={0} qualityPages={3} loading={false} insights={[]} onIssuePageChange={onIssuePageChange} />)
    expect(screen.getByRole('button', { name: '← Previous' })).toBeDisabled()
  })

  it('shows loading findings while the page is fetched', () => {
    render(<QualityReport issueCount={0} issueTypes={0} issues={[]} issuePage={0} qualityPages={1} loading insights={[]} onIssuePageChange={vi.fn()} />)
    expect(screen.getByText('Loading quality findings…')).toBeInTheDocument()
  })
})
