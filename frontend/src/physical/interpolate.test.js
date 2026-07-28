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
    expect(result.find((v) => v.id === 'B')).toMatchObject({ x: 7, y: 7, headingDegrees: 180 })
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
})

// A north arm running south into a junction at the origin, turning east. Lane
// AB ends at (-1.75, 0), lane BC starts at (0, -1.75), and the connector is the
// quarter arc between them — the shape LaneGeometryBuilder actually produces.
describe('a junction, using only what the backend says', () => {
  const geometry = {
    lanes: [
      { roadId: 'AB', laneIndex: 0, centreline: [[-1.75, 40], [-1.75, 0]] },
      { roadId: 'BC', laneIndex: 0, centreline: [[0, -1.75], [40, -1.75]] },
    ],
    connectors: [{
      fromRoadId: 'AB',
      toRoadId: 'BC',
      fromLane: 0,
      toLane: 0,
      curve: [
        [-1.75, 0], [-1.71, -0.31], [-1.59, -0.62], [-1.41, -0.92], [-1.18, -1.18],
        [-0.92, -1.41], [-0.62, -1.59], [-0.31, -1.71], [0, -1.75],
      ],
    }],
  }

  // The backend parks a vehicle waiting to cross at the stop line of the road
  // it arrived on, pointing the way it came. It never places one mid-turn.
  const waiting = {
    id: 'v', type: 'CAR', roadId: null, laneIndex: 0, inJunction: true,
    junctionFromRoadId: 'AB', junctionToRoadId: 'BC',
    x: -1.75, y: 3, headingDegrees: 180,
  }
  const approaching = {
    id: 'v', type: 'CAR', roadId: 'AB', roadProgress: 0.9, laneIndex: 0,
    inJunction: false, x: -1.75, y: 4, headingDegrees: 180,
  }
  const departing = {
    id: 'v', type: 'CAR', roadId: 'BC', roadProgress: 0.02, laneIndex: 0,
    inJunction: false, x: 0.8, y: -1.75, headingDegrees: 90,
  }

  it('leaves a vehicle stopped at a red light exactly where it was put', () => {
    const [drawn] = interpolateVehicles([waiting], [waiting], 0.5, geometry)
    expect(drawn).toMatchObject({ x: -1.75, y: 3, headingDegrees: 180 })
  })

  it('does not swing a waiting vehicle when its queue shuffles forward', () => {
    // The frame a vehicle ahead departs, everyone behind shifts up the road.
    // That is a metre of travel, not the start of a turn.
    const [drawn] = interpolateVehicles(
      [waiting], [{ ...waiting, y: 2 }], 0.5, geometry)
    expect(drawn.headingDegrees).toBeCloseTo(180, 6)
    expect(drawn.y).toBeCloseTo(2.5, 6)
  })

  it('draws a waiting vehicle on its approach even on its first frame', () => {
    const [drawn] = interpolateVehicles([], [waiting], 0.5, geometry)
    expect(drawn).toMatchObject({ x: -1.75, y: 3, headingDegrees: 180 })
  })

  it('does not start the turn while the vehicle is only rolling up to the line', () => {
    const [drawn] = interpolateVehicles([approaching], [waiting], 0.5, geometry)
    expect(drawn.headingDegrees).toBeCloseTo(180, 6)
    expect(drawn.x).toBeCloseTo(-1.75, 6)
  })

  it('sweeps the connector when the vehicle actually crosses', () => {
    const poses = [0, 0.25, 0.5, 0.75, 1]
      .map((t) => interpolateVehicles([waiting], [departing], t, geometry)[0])

    // Ends pinned to the authoritative frames.
    expect(poses[0]).toMatchObject({ x: -1.75, y: 3 })
    expect(poses[4].x).toBeCloseTo(0.8, 6)
    expect(poses[4].y).toBeCloseTo(-1.75, 6)

    // The turn is spread across the interval rather than taken in one step, and
    // it never doubles back.
    for (let index = 1; index < poses.length; index += 1) {
      expect(Math.abs(shortestTurn(poses[index - 1].headingDegrees, poses[index].headingDegrees)))
        .toBeLessThan(60)
      expect(poses[index].x).toBeGreaterThanOrEqual(poses[index - 1].x - 1e-6)
      expect(poses[index].y).toBeLessThanOrEqual(poses[index - 1].y + 1e-6)
    }
  })

  it('stays inside the corner instead of cutting across it', () => {
    const [half] = interpolateVehicles([waiting], [departing], 0.5, geometry)
    const straightY = waiting.y + (departing.y - waiting.y) * 0.5
    expect(half.y).toBeLessThan(straightY)
  })

  it('still follows the connector when fast playback skips the waiting frame', () => {
    const poses = [0, 0.2, 0.4, 0.6, 0.8, 1]
      .map((t) => interpolateVehicles([approaching], [departing], t, geometry)[0])

    // The straight run down to the junction mouth is most of this path, so the
    // vehicle holds its approach bearing for a while and only then turns. What
    // matters is that it gets there gradually and lands on the exit bearing.
    expect(poses[0].headingDegrees).toBeCloseTo(180, 6)
    expect(poses[5].headingDegrees).toBeCloseTo(90, 6)
    for (let index = 1; index < poses.length; index += 1) {
      expect(Math.abs(shortestTurn(poses[index - 1].headingDegrees, poses[index].headingDegrees)))
        .toBeLessThan(60)
      expect(poses[index].y).toBeLessThanOrEqual(poses[index - 1].y + 1e-6)
    }
  })
})
