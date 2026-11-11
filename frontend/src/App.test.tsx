import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'
import App from './App'

describe('App', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listDatasets').mockResolvedValue([])
  })

  it('shows the empty workspace and opens the import dialog', async () => {
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Start with a dataset' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Import data' }))
    expect(screen.getByRole('dialog', { name: 'Import market data' })).toBeInTheDocument()
  })
})
