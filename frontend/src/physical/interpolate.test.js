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
})
