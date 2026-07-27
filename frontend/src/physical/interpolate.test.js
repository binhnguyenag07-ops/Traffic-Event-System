import { describe, expect, it } from 'vitest'
import { interpolateVehicles, shortestTurn } from './interpolate'

describe('shortestTurn', () => {
  it('turns the short way across the 360/0 seam', () => {
    expect(shortestTurn(350, 10)).toBe(20)
    expect(shortestTurn(10, 350)).toBe(-20)
  })

  it('is zero for the same bearing', () => {
    expect(shortestTurn(90, 90)).toBe(0)
  })
})

describe('interpolateVehicles', () => {
  const a0 = { id: 'A', type: 'CAR', x: 0, y: 0, headingDegrees: 0 }
  const a1 = { id: 'A', type: 'CAR', x: 10, y: 20, headingDegrees: 90 }

  it('places a vehicle halfway between its two frames', () => {
    const [drawn] = interpolateVehicles([a0], [a1], 0.5)
    expect(drawn.x).toBe(5)
    expect(drawn.y).toBe(10)
    expect(drawn.headingDegrees).toBe(45)
  })

  it('draws the exact newer frame at t = 0 and t = 1', () => {
    expect(interpolateVehicles([a0], [a1], 0)[0].x).toBe(0)
    expect(interpolateVehicles([a0], [a1], 1)[0].x).toBe(10)
  })

  it('draws a newly appeared vehicle at its own position, not faded in', () => {
    const fresh = { id: 'B', type: 'MOTORCYCLE', x: 7, y: 7, headingDegrees: 180 }
    const result = interpolateVehicles([a0], [a1, fresh], 0.5)
    expect(result).toHaveLength(2)
    expect(result.find((v) => v.id === 'B')).toMatchObject({ x: 7, y: 7 })
  })

  it('drops a vehicle that has left rather than coasting it onward', () => {
    const result = interpolateVehicles([a0], [], 0.5)
    expect(result).toHaveLength(0)
  })

  it('keeps type and other fields from the newer frame', () => {
    const [drawn] = interpolateVehicles([a0], [{ ...a1, type: 'BUS', laneIndex: 2 }], 0.5)
    expect(drawn.type).toBe('BUS')
    expect(drawn.laneIndex).toBe(2)
  })

  it('renders safely before physical geometry has loaded', () => {
    const [drawn] = interpolateVehicles([a0], [a1], 0.5, null)
    expect(drawn).toMatchObject({ x: 5, y: 10, headingDegrees: 45 })
  })

  it('follows a curved lane instead of cutting across its chord', () => {
    const source = {
      ...a0, roadId: 'R', roadProgress: 0, laneIndex: 0,
    }
    const target = {
      ...a1, x: 10, y: 10, roadId: 'R', roadProgress: 1, laneIndex: 0,
    }
    const geometry = {
      lanes: [{
        roadId: 'R',
        laneIndex: 0,
        centreline: [[0, 0], [0, 10], [10, 10]],
      }],
    }

    const [drawn] = interpolateVehicles([source], [target], 0.5, geometry)

    expect(drawn.x).toBeCloseTo(0, 6)
    expect(drawn.y).toBeCloseTo(10, 6)
  })

  it('uses both halves of a lane connector continuously through a junction', () => {
    const incoming = {
      ...a0, roadId: 'AB', roadProgress: 1, laneIndex: 0,
    }
    const junction = {
      ...a0,
      x: 4,
      y: 4,
      roadId: null,
      inJunction: true,
      junctionFromRoadId: 'AB',
      junctionToRoadId: 'BC',
      laneIndex: 0,
    }
    const outgoing = {
      ...a1, x: 8, y: 4, roadId: 'BC', roadProgress: 0.2, laneIndex: 0,
    }
    const geometry = {
      connectors: [{
        fromRoadId: 'AB',
        fromLane: 0,
        toRoadId: 'BC',
        toLane: 0,
        curve: [[0, 0], [0, 4], [4, 4]],
      }],
    }

    const [endOfFirstHalf] = interpolateVehicles([incoming], [junction], 1, geometry)
    const [startOfSecondHalf] = interpolateVehicles([junction], [outgoing], 0, geometry)

    expect(endOfFirstHalf.x).toBeCloseTo(startOfSecondHalf.x, 6)
    expect(endOfFirstHalf.y).toBeCloseTo(startOfSecondHalf.y, 6)
    expect(endOfFirstHalf).toMatchObject({ x: 0, y: 4 })
  })

  it('still follows the connector when fast playback skips the junction frame', () => {
    const incoming = {
      ...a0, roadId: 'AB', roadProgress: 1, laneIndex: 0,
    }
    const outgoing = {
      ...a1, x: 8, y: 4, roadId: 'BC', roadProgress: 0.2, laneIndex: 0,
    }
    const geometry = {
      connectors: [{
        fromRoadId: 'AB',
        fromLane: 0,
        toRoadId: 'BC',
        toLane: 0,
        curve: [[0, 0], [0, 4], [4, 4]],
      }],
    }

    const [drawn] = interpolateVehicles([incoming], [outgoing], 0.5, geometry)

    expect(drawn.y).toBeCloseTo(4, 6)
    expect(drawn.x).toBeCloseTo(2, 6)
  })
})
