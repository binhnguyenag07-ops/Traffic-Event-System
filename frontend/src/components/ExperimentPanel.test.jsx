// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ExperimentPanel from './ExperimentPanel'
import { experimentApi } from '../api/trafficAPI'

vi.mock('../api/trafficAPI', () => ({
  experimentApi: { latest: vi.fn(), run: vi.fn() },
}))

describe('ExperimentPanel', () => {
  beforeEach(() => {
    experimentApi.latest.mockResolvedValue({
      seed: 42,
      requestsPerScenario: 30,
      scenarios: [{
        id: 'normal', name: 'Normal traffic', intersectionCount: 6, roadCount: 17,
        algorithms: [{
          algorithm: 'A*', objective: 'SHORTEST_TIME', successRate: 100,
          averageComputationTimeMillis: 0.2, averageVisitedNodes: 4,
          averagePathOptimality: 100,
        }],
      }],
    })
  })

  it('renders reproducible experiment metrics', async () => {
    render(<ExperimentPanel />)

    expect(await screen.findByText('Normal traffic')).toBeTruthy()
    expect(screen.getAllByText('A*').length).toBeGreaterThan(0)
    expect(screen.getByText('100.0%')).toBeTruthy()
    expect(screen.getByText('6 junctions · 17 roads · 30 requests')).toBeTruthy()
  })
})
