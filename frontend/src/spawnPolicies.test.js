import { describe, expect, it, vi } from 'vitest'
import {
  createBuildingTripPlanner,
  randomBoundarySpawnPolicy,
  randomBuildingSpawnPolicy,
} from './spawnPolicies'

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
      type: 'MOTORCYCLE',
      ...trip,
    })
  })

  it.each([
    [0.00, 'MOTORCYCLE'],
    [0.895, 'MOTORCYCLE'],
    [0.896, 'CAR'],
    [0.99, 'CAR'],
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

describe('building traffic', () => {
  const buildings = [
    { id: 'HOME', type: 'RESIDENTIAL', accessPoints: [{ intersectionId: 'A' }] },
    { id: 'OFFICE', type: 'OFFICE', accessPoints: [{ intersectionId: 'C' }] },
    { id: 'ISLAND', type: 'COMMERCIAL', accessPoints: [{ intersectionId: 'X' }] },
  ]
  const roads = [
    { id: 'AB', startIntersectionId: 'A', endIntersectionId: 'B', status: 'OPEN' },
    { id: 'BC', startIntersectionId: 'B', endIntersectionId: 'C', status: 'OPEN' },
    { id: 'XA', startIntersectionId: 'X', endIntersectionId: 'A', status: 'CLOSED' },
  ]

  it('chooses a residential origin and a reachable destination', () => {
    const planner = createBuildingTripPlanner(buildings, roads)

    expect(planner.planTrip(0)).toEqual({
      startBuildingId: 'HOME',
      destinationBuildingId: 'OFFICE',
    })
  })

  it('creates a backend building request with the documented fleet proxy', () => {
    const request = randomBuildingSpawnPolicy.plan({
      buildingPlanner: createBuildingTripPlanner(buildings, roads),
      random: sequence(0, 0),
    })

    expect(request).toEqual({
      policyId: 'RANDOM_BUILDING',
      type: 'MOTORCYCLE',
      startBuildingId: 'HOME',
      destinationBuildingId: 'OFFICE',
    })
  })

  it('rejects disconnected buildings instead of placing a vehicle on a road', () => {
    const planner = createBuildingTripPlanner(
      buildings.filter((building) => building.id !== 'OFFICE'),
      roads,
    )

    expect(() => randomBuildingSpawnPolicy.plan({
      buildingPlanner: planner,
      random: sequence(0),
    })).toThrow('No reachable building-to-building trip')
  })

  it('does not choose a different building on the same access node', () => {
    const planner = createBuildingTripPlanner([
      buildings[0],
      { id: 'NEIGHBOUR', type: 'OFFICE', accessPoints: [{ intersectionId: 'A' }] },
    ], roads)

    expect(planner.planTrip(0)).toBeNull()
  })
})
