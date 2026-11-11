import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Insight } from '../api'
import { InsightsPanel } from './InsightsPanel'

const insight: Insight = {
  code: 'RECURRING_GAPS',
  title: 'Recurring gaps',
  description: 'Repeated gaps were detected.',
  recommendation: 'Review the feed.',
  evidence: [],
}

describe('InsightsPanel', () => {
  it('renders insight cards and invokes the quality navigation action', () => {
    const onViewQuality = vi.fn()
    render(<InsightsPanel title="Intelligent insights" description="Rule-based recommendations." insights={[insight]} emptyMessage="No insights." onViewQuality={onViewQuality} />)
    expect(screen.getByRole('heading', { name: 'Intelligent insights' })).toBeInTheDocument()
    expect(screen.getByText('Recurring gaps')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /View quality report/ }))
    expect(onViewQuality).toHaveBeenCalledOnce()
  })

  it('shows the provided empty state', () => {
    render(<InsightsPanel title="Suggested actions" description="Recommendations." insights={[]} emptyMessage="No recommendations are available yet." />)
    expect(screen.getByText('No recommendations are available yet.')).toBeInTheDocument()
  })
})
