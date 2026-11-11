import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Dataset } from '../api'
import { WorkspaceSidebar } from './WorkspaceSidebar'

const datasets: Dataset[] = Array.from({ length: 6 }, (_, index) => ({
  id: `dataset-${index}`,
  filename: `bars-${index}.csv`,
  status: 'COMPLETED',
  rowsImported: 1250,
  rowsRejected: 0,
  uploadedAt: '2025-01-02T00:00:00Z',
  frequency: 'minute',
}))

describe('WorkspaceSidebar', () => {
  it('switches views and selects datasets', () => {
    const onChangeView = vi.fn()
    const onSelectDataset = vi.fn()
    render(<WorkspaceSidebar datasets={datasets} selectedId="dataset-0" loading={false} issueCount={4} activeView="overview" onSelectDataset={onSelectDataset} onChangeView={onChangeView} />)

    fireEvent.click(screen.getByRole('button', { name: /Data quality/ }))
    fireEvent.click(screen.getByRole('button', { name: /bars-1.csv/ }))
    expect(onChangeView).toHaveBeenCalledWith('quality')
    expect(onSelectDataset).toHaveBeenCalledWith('dataset-1')
    expect(screen.getByText('+1 more in dataset selector')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
  })

  it('shows loading and empty dataset states', () => {
    const props = { datasets: [], selectedId: '', issueCount: 0, activeView: 'overview' as const, onSelectDataset: vi.fn(), onChangeView: vi.fn() }
    const { rerender } = render(<WorkspaceSidebar {...props} loading />)
    expect(screen.getByText('Loading datasets…')).toBeInTheDocument()
    rerender(<WorkspaceSidebar {...props} loading={false} />)
    expect(screen.getByText('No datasets yet. Import a file to begin.')).toBeInTheDocument()
  })
})
