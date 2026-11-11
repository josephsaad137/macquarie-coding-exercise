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
  items: T[]
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init)
  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body: unknown = await response.json()
      if (typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string') {
        message = body.message
      } else if (typeof body === 'object' && body !== null && 'error' in body && typeof body.error === 'string') {
        message = body.error
      }
    } catch {
      // Some server errors do not include a JSON response body.
    }
    throw new Error(message)
  }
  return response.json() as Promise<T>
}

function queryString(params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined) query.set(key, String(value))
  })
  return query.toString()
}

export const api = {
  listDatasets: () => request<Dataset[]>('/datasets'),

  importDataset: (file: File, frequency: 'minute' | 'daily') => {
    const form = new FormData()
    form.set('file', file)
    form.set('frequency', frequency)
    return request<ImportResult>('/datasets', { method: 'POST', body: form })
  },

  quality: (datasetId: string, page: number, size = 10) =>
    request<QualityPage>(`/datasets/${datasetId}/quality?${queryString({ page, size })}`),

  insights: (datasetId: string) =>
    request<Insight[]>(`/insights?${queryString({ datasetId })}`),

  dailyBars: (datasetId: string, contract: string, from: string, to: string) =>
    request<Page<DailyBar>>(`/analytics/daily-bars?${queryString({
      datasetId,
      contract: contract || undefined,
      from: from || '0001-01-01',
      to: to || '9999-12-31',
      size: 500,
    })}`),

  vwap: (datasetId: string, contract: string, from: string, to: string) =>
    request<Page<VwapPoint>>(`/analytics/vwap?${queryString({
      datasetId,
      contract: contract || undefined,
      from: from || '0001-01-01',
      to: to || '9999-12-31',
      size: 500,
    })}`),
}
