import { describe, expect, it } from 'vitest'
import { buildDemoTraffic, createBoundaryTripPlanner, replacementDelayMs } from './demoTraffic'

const intersections = [
  { id: 'A', latitude: 1, longitude: 0 },
  { id: 'B', latitude: 1, longitude: 1 },
  { id: 'C', latitude: 1, longitude: 2 },
  { id: 'D', latitude: 1, longitude: 3 },
  { id: 'N', latitude: 2, longitude: 1.5 },
  { id: 'S', latitude: 0, longitude: 1.5 },
]
const roads = [
  { id: 'AB', startIntersectionId: 'A', endIntersectionId: 'B', status: 'OPEN' },
  { id: 'BC', startIntersectionId: 'B', endIntersectionId: 'C', status: 'OPEN' },
  { id: 'CD', startIntersectionId: 'C', endIntersectionId: 'D', status: 'OPEN' },
  { id: 'DC', startIntersectionId: 'D', endIntersectionId: 'C', status: 'OPEN' },
  { id: 'CB', startIntersectionId: 'C', endIntersectionId: 'B', status: 'OPEN' },
  { id: 'BA', startIntersectionId: 'B', endIntersectionId: 'A', status: 'OPEN' },
  { id: 'AD-BLOCKED', startIntersectionId: 'A', endIntersectionId: 'D', status: 'BLOCKED' },
]

describe('boundary traffic', () => {
  it('builds a deterministic fleet with the requested type composition', () => {
    const first = buildDemoTraffic(intersections, roads)
    const second = buildDemoTraffic(intersections, roads)

    expect(first).toEqual(second)
    expect(first.vehicles).toHaveLength(24)
    expect(first.vehicles.filter((item) => item.type === 'CAR')).toHaveLength(10)
    expect(first.vehicles.filter((item) => item.type === 'MOTORCYCLE')).toHaveLength(8)
    expect(first.vehicles.filter((item) => item.type === 'BUS')).toHaveLength(4)
    expect(first.vehicles.filter((item) => item.type === 'EMERGENCY')).toHaveLength(2)
    expect(first.vehicles.every((item) => ['A', 'D'].includes(item.startIntersectionId))).toBe(true)
    expect(first.vehicles.every((item) => ['A', 'D'].includes(item.destinationIntersectionId))).toBe(true)
    expect(first.vehicles.every((item) => item.startIntersectionId !== item.destinationIntersectionId)).toBe(true)
  })

  it('plans a reachable trip between opposite map boundaries', () => {
    const planner = createBoundaryTripPlanner(intersections, roads)
    const trip = planner.planTrip(0)

    expect(planner.boundaryIds).toEqual(expect.arrayContaining(['A', 'D', 'N', 'S']))
    expect(planner.entryIds).toEqual(['A', 'D'])
    expect(planner.exitIds).toEqual(['A', 'D'])
    expect(trip).toMatchObject({
      startIntersectionId: 'A',
      destinationIntersectionId: 'D',
      startSide: 'WEST',
      destinationSide: 'EAST',
    })
  })

  it('excludes blocked roads and returns no trip across a disconnected graph', () => {
    const planner = createBoundaryTripPlanner(intersections, [roads.at(-1)])

    expect(planner.planTrip(0)).toBeNull()
  })

  it('supports x/y coordinates for imported synthetic maps', () => {
    const synthetic = intersections.map(({ id, latitude: y, longitude: x }) => ({ id, x, y }))

    expect(createBoundaryTripPlanner(synthetic, roads).planTrip(0)).toMatchObject({
      startIntersectionId: 'A',
      destinationIntersectionId: 'D',
    })
  })

  it('uses deterministic replacement delays between one and three seconds', () => {
    expect(replacementDelayMs(4)).toBe(replacementDelayMs(4))
    expect([0, 1, 20, 99].every((seed) => {
      const delay = replacementDelayMs(seed)
      return delay >= 1000 && delay <= 3000
    })).toBe(true)
  })

  it('only schedules live conditions on usable roads', () => {
    const { events } = buildDemoTraffic(intersections, roads)

    expect(events).toHaveLength(2)
    expect(events.map((item) => item.affectedRoadId)).not.toContain('AD-BLOCKED')
    expect(events.map((item) => item.type)).toEqual(['ACCIDENT', 'CONGESTION'])
  })
})
