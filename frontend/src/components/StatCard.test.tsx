import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StatCard } from './StatCard'

describe('StatCard', () => {
  it('renders the metric, note, and visual tone', () => {
    const { container } = render(<StatCard title="ROWS IMPORTED" value="1,234" note="Minute frequency" icon="▤" tone="blue" />)
    expect(screen.getByText('ROWS IMPORTED')).toBeInTheDocument()
    expect(screen.getByText('1,234')).toBeInTheDocument()
    expect(screen.getByText('Minute frequency')).toBeInTheDocument()
    expect(container.querySelector('.stat-icon.blue')).toBeInTheDocument()
  })
})
