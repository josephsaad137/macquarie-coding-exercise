import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Dataset } from '../api'
import { TopBar } from './TopBar'

const datasets: Dataset[] = [{
  id: 'one',
  filename: 'daily.csv',
  status: 'COMPLETED',
  rowsImported: 10,
  rowsRejected: 0,
  uploadedAt: '2025-01-02T00:00:00Z',
  frequency: 'daily',
}]

describe('TopBar', () => {
  it('updates dataset selection and opens the import flow', () => {
    const onSelectDataset = vi.fn()
    const onImport = vi.fn()
    render(<TopBar datasets={datasets} selectedId="one" activeView="quality" onSelectDataset={onSelectDataset} onImport={onImport} />)
    expect(screen.getByText(/Data quality/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Selected dataset'), { target: { value: 'one' } })
    fireEvent.click(screen.getByRole('button', { name: /Import data/ }))
    expect(onSelectDataset).toHaveBeenCalledWith('one')
    expect(onImport).toHaveBeenCalledOnce()
  })
})
