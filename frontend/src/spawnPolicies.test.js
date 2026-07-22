import { describe, expect, it, vi } from 'vitest'
import { randomBoundarySpawnPolicy } from './spawnPolicies'

const trip = {
  startIntersectionId: 'WEST_GATE',
  destinationIntersectionId: 'EAST_GATE',
}

function sequence(...values) {
  let index = 0
  return () => values[index++]
}

describe('random boundary spawn policy', () => {
  it('plans a reachable backend vehicle request through the boundary planner', () => {
    const planTrip = vi.fn(() => trip)

    const request = randomBoundarySpawnPolicy.plan({
      boundaryPlanner: { planTrip },
      random: sequence(0.123456, 0.2),
    })

    expect(planTrip).toHaveBeenCalledWith(123456)
    expect(request).toEqual({
      policyId: 'RANDOM_BOUNDARY',
      type: 'CAR',
      ...trip,
    })
  })

  it.each([
    [0.00, 'CAR'],
    [0.59, 'CAR'],
    [0.60, 'MOTORCYCLE'],
    [0.99, 'MOTORCYCLE'],
  ])('uses the initial vehicle mix at draw %s', (typeDraw, expectedType) => {
    const request = randomBoundarySpawnPolicy.plan({
      boundaryPlanner: { planTrip: () => trip },
      random: sequence(0, typeDraw),
    })

    expect(request.type).toBe(expectedType)
  })

  it('rejects a network without a reachable trip instead of spawning a stuck dot', () => {
    expect(() => randomBoundarySpawnPolicy.plan({
      boundaryPlanner: { planTrip: () => null },
      random: sequence(0),
    })).toThrow('No reachable boundary-to-boundary trip')
  })
})
