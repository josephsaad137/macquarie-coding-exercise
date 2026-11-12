import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'

describe('api client', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists datasets from the API base path', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify([])))

    await expect(api.listDatasets()).resolves.toEqual([])
    expect(fetchMock).toHaveBeenCalledWith('/api/datasets', undefined)
  })

  it('uploads a file and its selected frequency as multipart form data', async () => {
    const result = { id: 'one', filename: 'bars.csv', frequency: 'daily' }
    fetchMock.mockResolvedValue(new Response(JSON.stringify(result)))
    const file = new File(['data'], 'bars.csv', { type: 'text/csv' })

    await expect(api.importDataset(file, 'daily')).resolves.toEqual(result)
    const [url, options] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/datasets')
    expect(options.method).toBe('POST')
    expect(options.body).toBeInstanceOf(FormData)
    expect((options.body as FormData).get('file')).toBe(file)
    expect((options.body as FormData).get('frequency')).toBe('daily')
  })

  it('requests a selected quality page with its page size', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ issues: [] })))

    await api.quality('dataset-1', 2)

    expect(fetchMock).toHaveBeenCalledWith('/api/datasets/dataset-1/quality?page=2&size=10', undefined)
  })

  it('encodes dataset identifiers in insight requests', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify([])))

    await api.insights('dataset & one')

    expect(fetchMock).toHaveBeenCalledWith('/api/insights?datasetId=dataset+%26+one', undefined)
  })

  it('applies date defaults and omits an empty contract for daily bars', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ totalElements: 0, page: 0, size: 500, items: [] })))

    await api.dailyBars('dataset-1', '', '', '')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/analytics/daily-bars?datasetId=dataset-1&from=0001-01-01&to=9999-12-31&page=0&size=500',
      undefined,
    )
  })

  it('keeps large daily-bar chart requests bounded to one page', async () => {
    const firstPage = {
      totalElements: 501,
      page: 0,
      size: 500,
      items: Array.from({ length: 500 }, (_, index) => ({ date: `day-${index}` })),
    }
    fetchMock.mockResolvedValue(new Response(JSON.stringify(firstPage)))

    const result = await api.dailyBars('dataset-1', '', '', '')

    expect(result.totalElements).toBe(501)
    expect(result.items).toHaveLength(500)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/analytics/daily-bars?datasetId=dataset-1&from=0001-01-01&to=9999-12-31&page=0&size=500',
      undefined,
    )
  })

  it('includes contract and date filters for VWAP requests', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ totalElements: 100000, page: 0, size: 500, items: [] })))

    const result = await api.vwap('dataset-1', 'ESH25', '2025-01-01', '2025-01-31')

    expect(result.totalElements).toBe(100000)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/analytics/vwap?datasetId=dataset-1&contract=ESH25&from=2025-01-01&to=2025-01-31&page=0&size=500',
      undefined,
    )
  })

  it('uses the server message for failed responses', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ message: 'Invalid date range' }), { status: 400 }))

    await expect(api.listDatasets()).rejects.toThrow('Invalid date range')
  })

  it('uses the server error when no message is provided', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ error: 'Dataset not found' }), { status: 404 }))

    await expect(api.listDatasets()).rejects.toThrow('Dataset not found')
  })

  it('falls back to the status when the error body is missing or has no recognized message', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ detail: 'ignored' }), { status: 503 }))
      .mockResolvedValueOnce({
        ok: false,
        status: 502,
        json: () => Promise.reject(new Error('invalid JSON')),
      })

    await expect(api.listDatasets()).rejects.toThrow('Request failed (503)')
    await expect(api.listDatasets()).rejects.toThrow('Request failed (502)')
  })
})
