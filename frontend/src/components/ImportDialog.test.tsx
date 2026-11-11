import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ImportResult } from '../api'
import { ImportDialog } from './ImportDialog'

const imported: ImportResult = {
  id: 'dataset-1',
  filename: 'bars.csv',
  status: 'COMPLETED',
  rowsImported: 1200,
  rowsRejected: 2,
  frequency: 'minute',
}

function renderDialog(onImport = vi.fn().mockResolvedValue(imported)) {
  const onClose = vi.fn()
  render(<ImportDialog onClose={onClose} onImport={onImport} />)
  return { onClose, onImport }
}

describe('ImportDialog', () => {
  it('rejects unsupported extensions before making an API call', async () => {
    const { onImport } = renderDialog()
    fireEvent.change(screen.getByLabelText(/Choose a data file/), { target: { files: [new File(['data'], 'bars.json')] } })
    fireEvent.click(screen.getByRole('button', { name: 'Import dataset' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Only CSV and Parquet files are supported.')
    expect(onImport).not.toHaveBeenCalled()
  })

  it('imports the selected file and chosen frequency, then reports accepted/rejected counts', async () => {
    const { onClose, onImport } = renderDialog()
    const file = new File(['bars'], 'bars.csv', { type: 'text/csv' })
    fireEvent.change(screen.getByLabelText(/Choose a data file/), { target: { files: [file] } })
    fireEvent.change(screen.getByLabelText('Data frequency'), { target: { value: 'daily' } })
    fireEvent.click(screen.getByRole('button', { name: 'Import dataset' }))

    await waitFor(() => expect(onImport).toHaveBeenCalledWith(file, 'daily'))
    expect(await screen.findByRole('status')).toHaveTextContent('1,200 rows accepted, 2 rejected')
    expect(onClose).not.toHaveBeenCalled()
  })

  it('surfaces API errors without disguising the import as successful', async () => {
    renderDialog(vi.fn().mockRejectedValue(new Error('Invalid timestamp')))
    fireEvent.change(screen.getByLabelText(/Choose a data file/), { target: { files: [new File(['data'], 'bars.csv')] } })
    fireEvent.click(screen.getByRole('button', { name: 'Import dataset' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid timestamp')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
