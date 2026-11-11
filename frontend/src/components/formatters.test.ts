import { describe, expect, it } from 'vitest'
import { formatDate, formatNumber, formatTimestamp } from './formatters'

describe('formatters', () => {
  it('formats numbers for dashboard summaries', () => {
    expect(formatNumber(12345.678)).toBe('12,345.68')
  })

  it('formats date-only values without shifting calendar day', () => {
    expect(formatDate('2025-01-02')).toBe('Jan 2, 2025')
  })

  it('formats timestamps and missing values', () => {
    expect(formatTimestamp('2025-01-02T09:31:00')).toBe('2025-01-02 09:31:00')
    expect(formatTimestamp(null)).toBe('—')
  })
})
