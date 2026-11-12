import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, type Dataset, type ImportResult } from './api'
import App from './App'

const firstDataset: Dataset = {
  id: 'dataset-1',
  filename: 'first.csv',
  status: 'COMPLETED',
  rowsImported: 12,
  rowsRejected: 2,
  uploadedAt: '2025-01-02T00:00:00Z',
  frequency: 'minute',
}

const secondDataset: Dataset = {
  ...firstDataset,
  id: 'dataset-2',
  filename: 'second.parquet',
  frequency: 'daily',
}

const importResult: ImportResult = {
  id: 'dataset-3',
  filename: 'new.csv',
  status: 'COMPLETED',
  rowsImported: 8,
  rowsRejected: 1,
  frequency: 'daily',
}

describe('App', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(api, 'listDatasets').mockResolvedValue([])
    vi.spyOn(api, 'quality').mockResolvedValue({
      datasetId: firstDataset.id,
      totalIssues: 0,
      page: 0,
      size: 10,
      issues: [],
    })
    vi.spyOn(api, 'insights').mockResolvedValue([])
    vi.spyOn(api, 'dailyBars').mockResolvedValue({ totalElements: 0, page: 0, size: 500, items: [] })
    vi.spyOn(api, 'vwap').mockResolvedValue({ totalElements: 0, page: 0, size: 500, items: [] })
    vi.spyOn(api, 'importDataset').mockResolvedValue(importResult)
  })

  it('shows the empty workspace and opens the import dialog', async () => {
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Start with a dataset' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Import your first dataset/ }))
    expect(screen.getByRole('dialog', { name: 'Import market data' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Close import dialog' }))
    expect(screen.queryByRole('dialog', { name: 'Import market data' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Import data' }))
    expect(screen.getByRole('dialog', { name: 'Import market data' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Close import dialog' }))
    expect(screen.queryByRole('dialog', { name: 'Import market data' })).not.toBeInTheDocument()
  })

  it('loads dataset analytics and quality findings, and changes quality pages', async () => {
    vi.mocked(api.listDatasets).mockResolvedValue([firstDataset])
    vi.mocked(api.quality).mockResolvedValue({
      datasetId: firstDataset.id,
      totalIssues: 21,
      page: 0,
      size: 10,
      issues: [],
    })
    vi.mocked(api.insights).mockResolvedValue([{
      code: 'RECURRING_QUALITY_ISSUE',
      title: 'Review data quality',
      description: 'Repeated validation findings were detected.',
      recommendation: 'Review the affected records.',
      evidence: ['3 duplicate bars'],
    }])

    render(<App />)

    expect(await screen.findByText('12')).toBeInTheDocument()
    await waitFor(() => {
      expect(api.dailyBars).toHaveBeenCalledWith(firstDataset.id, '', '', '')
      expect(api.vwap).toHaveBeenCalledWith(firstDataset.id, '', '', '')
    })
    fireEvent.click(screen.getByRole('button', { name: 'View quality report' }))
    expect(await screen.findByRole('heading', { name: 'Quality report' })).toBeInTheDocument()
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Next →' }))
    await waitFor(() => expect(api.quality).toHaveBeenLastCalledWith(firstDataset.id, 1))
    expect(screen.getByText('Showing 11–20 of 21')).toBeInTheDocument()
  })

  it('applies normalized filters and reloads analytics', async () => {
    vi.mocked(api.listDatasets).mockResolvedValue([firstDataset])
    render(<App />)

    await screen.findByText('12')
    fireEvent.change(screen.getByPlaceholderText('All contracts'), { target: { value: ' esh25 ' } })
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2025-01-01' } })
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2025-01-31' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }))

    await waitFor(() => {
      expect(api.dailyBars).toHaveBeenLastCalledWith(firstDataset.id, 'ESH25', '2025-01-01', '2025-01-31')
      expect(api.vwap).toHaveBeenLastCalledWith(firstDataset.id, 'ESH25', '2025-01-01', '2025-01-31')
    })
  })

  it('switches datasets and resets the active filters', async () => {
    vi.mocked(api.listDatasets).mockResolvedValue([firstDataset, secondDataset])
    vi.mocked(api.dailyBars)
      .mockRejectedValueOnce(new Error('Analytics unavailable'))
      .mockResolvedValue({ totalElements: 0, page: 0, size: 500, items: [] })
    render(<App />)
    await screen.findByRole('alert')

    fireEvent.change(screen.getByRole('combobox', { name: 'Selected dataset' }), { target: { value: secondDataset.id } })

    await waitFor(() => expect(api.dailyBars).toHaveBeenLastCalledWith(secondDataset.id, '', '', ''))
    expect(screen.getByPlaceholderText('All contracts')).toHaveValue('')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('imports a dataset, refreshes the list, and displays the successful result', async () => {
    vi.mocked(api.listDatasets).mockResolvedValueOnce([firstDataset]).mockResolvedValue([importResult as Dataset])
    render(<App />)
    await screen.findByText('12')

    fireEvent.click(screen.getByRole('button', { name: 'Import data' }))
    const file = new File(['bars'], 'new.csv', { type: 'text/csv' })
    fireEvent.change(screen.getByLabelText(/Choose a data file/), { target: { files: [file] } })
    fireEvent.change(screen.getByLabelText('Data frequency'), { target: { value: 'daily' } })
    fireEvent.click(screen.getByRole('button', { name: 'Import dataset' }))

    await waitFor(() => expect(api.importDataset).toHaveBeenCalledWith(file, 'daily'))
    expect(await screen.findByRole('status')).toHaveTextContent('new.csv imported: 8 rows accepted, 1 rejected.')
    await waitFor(() => expect(api.listDatasets).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('combobox', { name: 'Selected dataset' })).toHaveValue(importResult.id)
  })

  it('reports a list-loading error and lets the user dismiss it', async () => {
    vi.mocked(api.listDatasets).mockRejectedValue(new Error('Dataset service unavailable'))
    render(<App />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Dataset service unavailable')
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss error' }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('reports analytics failures without hiding the workspace', async () => {
    vi.mocked(api.listDatasets).mockResolvedValue([firstDataset])
    vi.mocked(api.dailyBars).mockRejectedValue(new Error('Analytics unavailable'))
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Market overview' })).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('Analytics unavailable')
  })

  it('reports quality loading failures', async () => {
    vi.mocked(api.listDatasets).mockResolvedValue([firstDataset])
    vi.mocked(api.quality).mockRejectedValue('quality failure')
    render(<App />)

    fireEvent.click(screen.getByRole('button', { name: /Data quality/ }))
    expect(await screen.findByRole('heading', { name: 'Quality report' })).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load quality findings.')
  })

  it('reports insight loading failures', async () => {
    vi.mocked(api.listDatasets).mockResolvedValue([firstDataset])
    vi.mocked(api.insights).mockRejectedValue('insight failure')
    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load insights.')
  })

  it('reports a failed refresh after an import while retaining the imported result', async () => {
    vi.mocked(api.listDatasets)
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new Error('Refresh failed'))
    render(<App />)
    await screen.findByRole('heading', { name: 'Start with a dataset' })
    fireEvent.click(screen.getByRole('button', { name: 'Import data' }))
    const file = new File(['bars'], 'new.csv', { type: 'text/csv' })
    fireEvent.change(screen.getByLabelText(/Choose a data file/), { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: 'Import dataset' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Import succeeded, but the dataset list could not be refreshed: Refresh failed',
    )
    expect(screen.getByRole('status')).toHaveTextContent('new.csv imported: 8 rows accepted, 1 rejected.')
  })
})
