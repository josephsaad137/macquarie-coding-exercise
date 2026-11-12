const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api'

export interface Dataset {
  id: string
  filename: string
  status: string
  rowsImported: number
  rowsRejected: number
  uploadedAt: string
  frequency: 'minute' | 'daily'
}

export type ImportResult = Omit<Dataset, 'uploadedAt'>

export interface QualityIssue {
  rowNumber: number | null
  contract: string | null
  timestamp: string | null
  code: string
  message: string
}

export interface QualityPage {
  datasetId: string
  totalIssues: number
  page: number
  size: number
  issues: QualityIssue[]
}

export interface Insight {
  code: string
  title: string
  description: string
  recommendation: string
  evidence: string[]
}

export interface DailyBar {
  contract: string
  date: string
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export interface VwapPoint {
  contract: string
  timestamp: string
  value: number
}

export interface Page<T> {
  totalElements: number
  page: number
  size: number
  rangeStart?: string | null
  rangeEnd?: string | null
  items: T[]
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method ?? 'GET'
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, init)
  } catch (error) {
    console.error('API network request failed', {
      method,
      path,
      error: error instanceof Error ? error.message : String(error),
    })
    throw error
  }

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body: unknown = await response.json()
      if (typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string') {
        message = body.message
      } else if (typeof body === 'object' && body !== null && 'error' in body && typeof body.error === 'string') {
        message = body.error
      } else if (typeof body === 'object' && body !== null && 'detail' in body && typeof body.detail === 'string') {
        message = body.detail
      }
    } catch {
      // Some server errors do not include a JSON response body.
    }
    const logData = { method, path, status: response.status, message }
    if (response.status >= 500) {
      console.error('API request failed', logData)
    } else {
      console.warn('API request was rejected', logData)
    }
    throw new Error(message)
  }

  try {
    return await response.json() as T
  } catch (error) {
    console.error('API response could not be decoded', {
      method,
      path,
      status: response.status,
      error: error instanceof Error ? error.message : String(error),
    })
    throw error
  }
}

function queryString(params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined) query.set(key, String(value))
  })
  return query.toString()
}

const ANALYTICS_PAGE_SIZE = 500
export const DAILY_BARS_PAGE_SIZE = 10
export const ANALYTICS_CHART_POINT_LIMIT = ANALYTICS_PAGE_SIZE

async function latestAnalyticsPage<T>(path: string): Promise<Page<T>> {
  const firstPage = await request<Page<T>>(`${path}&page=0&size=${ANALYTICS_PAGE_SIZE}`)
  if (firstPage.totalElements <= ANALYTICS_PAGE_SIZE) return firstPage

  const lastPageNumber = Math.floor((firstPage.totalElements - 1) / ANALYTICS_PAGE_SIZE)
  const lastPage = await request<Page<T>>(`${path}&page=${lastPageNumber}&size=${ANALYTICS_PAGE_SIZE}`)
  const precedingPage = lastPageNumber === 1
    ? firstPage
    : await request<Page<T>>(`${path}&page=${lastPageNumber - 1}&size=${ANALYTICS_PAGE_SIZE}`)

  return {
    ...firstPage,
    page: lastPageNumber,
    items: [...precedingPage.items, ...lastPage.items].slice(-ANALYTICS_PAGE_SIZE),
  }
}

export const api = {
  listDatasets: () => request<Dataset[]>('/datasets'),

  importDataset: async (file: File, frequency: 'minute' | 'daily') => {
    const form = new FormData()
    form.set('file', file)
    form.set('frequency', frequency)
    const result = await request<ImportResult>('/datasets', { method: 'POST', body: form })
    console.info('Dataset import completed', {
      datasetId: result.id,
      filename: result.filename,
      frequency: result.frequency,
      rowsImported: result.rowsImported,
      rowsRejected: result.rowsRejected,
    })
    return result
  },

  quality: (datasetId: string, page: number, size = 10) =>
    request<QualityPage>(`/datasets/${datasetId}/quality?${queryString({ page, size })}`),

  insights: (datasetId: string) =>
    request<Insight[]>(`/insights?${queryString({ datasetId })}`),

  dailyBars: (datasetId: string, contract: string, from: string, to: string) =>
    latestAnalyticsPage<DailyBar>(`/analytics/daily-bars?${queryString({
      datasetId,
      contract: contract || undefined,
      from: from || '0001-01-01',
      to: to || '9999-12-31',
    })}`),

  dailyBarsPage: (datasetId: string, contract: string, from: string, to: string, page: number) =>
    request<Page<DailyBar>>(`/analytics/daily-bars?${queryString({
      datasetId,
      contract: contract || undefined,
      from: from || '0001-01-01',
      to: to || '9999-12-31',
      page,
      size: DAILY_BARS_PAGE_SIZE,
    })}`),

  vwap: (datasetId: string, contract: string, from: string, to: string) =>
    latestAnalyticsPage<VwapPoint>(`/analytics/vwap?${queryString({
      datasetId,
      contract: contract || undefined,
      from: from || '0001-01-01',
      to: to || '9999-12-31',
    })}`),
}
