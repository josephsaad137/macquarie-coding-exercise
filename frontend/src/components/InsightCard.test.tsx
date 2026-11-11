import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Insight } from '../api'
import { InsightCard } from './InsightCard'

const insight: Insight = {
  code: 'RECURRING_GAPS',
  title: 'Recurring timestamp gaps',
  description: 'Gaps recur for ESH25.',
  recommendation: 'Review the source feed interval.',
  evidence: ['10 missing spans', 'Mostly near session open', 'Additional evidence'],
}

describe('InsightCard', () => {
  it('displays the recommendation and only the first two evidence items', () => {
    render(<InsightCard insight={insight} />)
    expect(screen.getByText('RECURRING GAPS')).toBeInTheDocument()
    expect(screen.getByText('Review the source feed interval.')).toBeInTheDocument()
    expect(screen.getByText('10 missing spans · Mostly near session open')).toBeInTheDocument()
    expect(screen.queryByText('Additional evidence')).not.toBeInTheDocument()
  })

  it('omits evidence when the API returns none', () => {
    const { container } = render(<InsightCard insight={{ ...insight, evidence: [] }} />)
    expect(container.querySelector('.evidence')).not.toBeInTheDocument()
  })
})
