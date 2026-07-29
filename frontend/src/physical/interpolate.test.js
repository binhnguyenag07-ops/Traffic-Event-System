import { describe, expect, it } from 'vitest'
import { interpolateVehicles, shortestTurn } from './interpolate'

function bearingBetweenPoses(from, to) {
  return (
    Math.atan2(to.x - from.x, to.y - from.y) * 180 / Math.PI
    + 360
  ) % 360
}

function headingToTrajectoryError(source, target, fraction, geometry) {
  const sample = 1e-3
  const [before] = interpolateVehicles(
    [source], [target], fraction - sample, geometry)
  const [at] = interpolateVehicles([source], [target], fraction, geometry)
  const [after] = interpolateVehicles(
    [source], [target], fraction + sample, geometry)
  const tangent = bearingBetweenPoses(before, after)
  return Math.abs(shortestTurn(at.headingDegrees, tangent))
}

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

  it('preserves both authoritative endpoint poses at t = 0 and t = 1', () => {
    expect(interpolateVehicles([a0], [a1], 0)[0]).toEqual(a0)
    expect(interpolateVehicles([a0], [a1], 1)[0]).toEqual(a1)
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

  it('moves monotonically between consecutive authoritative driveway frames', () => {
    const source = {
      ...a0,
      x: -20,
      y: 0,
      journeyPhase: 'DEPARTING_ORIGIN',
      originPlaceKind: 'BUILDING',
      originPlaceId: 'HOME',
      originAccessIntersectionId: 'J',
      accessLinkProgress: 0,
    }
    const target = {
      ...source,
      x: -10,
      accessLinkProgress: 0.5,
    }

    const [drawn] = interpolateVehicles([source], [target], 0.5)

    expect(drawn.y).toBeCloseTo(0, 6)
    expect(drawn.x).toBeCloseTo(-15, 6)
    expect(drawn.accessLinkProgress).toBe(0.5)
  })

  it('follows authoritative arrival frames and does not invent a removal ghost', () => {
    const source = {
      ...a0,
      x: 0,
      y: 0,
      journeyPhase: 'ARRIVING_DESTINATION',
      destinationPlaceKind: 'BUILDING',
      destinationPlaceId: 'OFFICE',
      destinationAccessIntersectionId: 'J',
      accessLinkProgress: 0,
    }
    const target = {
      ...source,
      x: -20,
      accessLinkProgress: 1,
    }

    const [halfway] = interpolateVehicles([source], [target], 0.5)
    const removed = interpolateVehicles([target], [], 0.5)

    expect(halfway.journeyPhase).toBe('ARRIVING_DESTINATION')
    expect(halfway.x).toBeCloseTo(-10, 6)
    expect(halfway.y).toBeCloseTo(0, 6)
    expect(removed).toHaveLength(0)
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

  it('keeps its heading tangent to a lane instead of yawing before its bend', () => {
    const source = {
      ...a0, x: 0, y: 20, headingDegrees: 180,
      roadId: 'R', roadProgress: 0, laneIndex: 0,
    }
    const target = {
      ...a1, x: 2, y: 0, headingDegrees: 90,
      roadId: 'R', roadProgress: 1, laneIndex: 0,
    }
    const geometry = {
      lanes: [{
        roadId: 'R',
        laneIndex: 0,
        centreline: [[0, 20], [0, 0], [2, 0]],
      }],
    }

    const [halfway] = interpolateVehicles([source], [target], 0.5, geometry)

    expect(halfway.x).toBeCloseTo(0, 6)
    expect(halfway.y).toBeGreaterThan(8)
    expect(Math.abs(shortestTurn(halfway.headingDegrees, 180))).toBeLessThan(1)
    expect(headingToTrajectoryError(source, target, 0.5, geometry)).toBeLessThan(1)
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

  it('never replaces authoritative endpoint headings with connector tangents', () => {
    const source = { ...waiting, headingDegrees: 179.25 }
    const target = { ...departing, headingDegrees: 91.75 }

    expect(interpolateVehicles([source], [target], 0, geometry)[0]).toEqual(source)
    expect(interpolateVehicles([source], [target], 1, geometry)[0]).toEqual(target)
  })

  it('keeps connector rotation locally bounded at every sampled animation step', () => {
    const poses = Array.from({ length: 41 }, (_, index) => (
      interpolateVehicles([waiting], [departing], index / 40, geometry)[0]
    ))

    for (let index = 1; index < poses.length; index += 1) {
      expect(Math.abs(shortestTurn(
        poses[index - 1].headingDegrees,
        poses[index].headingDegrees,
      ))).toBeLessThan(12)
    }
  })

  it('uses only the connector for the exact simulated lane pairing', () => {
    const laneOneSource = { ...waiting, laneIndex: 1 }
    const laneOneTarget = { ...departing, laneIndex: 1 }
    const [drawn] = interpolateVehicles(
      [laneOneSource], [laneOneTarget], 0.5, geometry)

    // Geometry only contains lane 0 -> lane 0. Falling back to a stable chord is
    // preferable to pulling a lane-1 vehicle sideways onto somebody else's arc.
    expect(drawn.x).toBeCloseTo(
      (laneOneSource.x + laneOneTarget.x) / 2,
      6,
    )
    expect(drawn.y).toBeCloseTo(
      (laneOneSource.y + laneOneTarget.y) / 2,
      6,
    )
  })

  it('rejects a connector whose endpoint tangent points backwards', () => {
    const unsafeGeometry = {
      ...geometry,
      connectors: [{
        ...geometry.connectors[0],
        curve: [[-1.75, 0], [-1.75, 1], [0, -1.75]],
      }],
    }
    const [drawn] = interpolateVehicles([waiting], [departing], 0.5, unsafeGeometry)

    expect(drawn.x).toBeCloseTo((waiting.x + departing.x) / 2, 6)
    expect(drawn.y).toBeCloseTo((waiting.y + departing.y) / 2, 6)
    expect(Math.abs(shortestTurn(waiting.headingDegrees, drawn.headingDegrees)))
      .toBeLessThan(60)
  })

  it('keeps the complete connector before entering a short outgoing road', () => {
    // The target is deliberately closer to the connector start than its end.
    // Nearest-point trimming used to select the start for both endpoints and
    // erase the whole turning arc.
    const shortRoadTarget = {
      ...departing,
      x: -1.6,
      y: -0.1,
      headingDegrees: 90,
      roadProgress: 0.49,
    }
    const poses = [0.25, 0.5, 0.75].map((t) => (
      interpolateVehicles([waiting], [shortRoadTarget], t, geometry)[0]
    ))

    // At least one intermediate pose must enter the south/east turn rather than
    // all three lying on the direct source-target chord.
    expect(poses.some((pose) => pose.y < -0.5)).toBe(true)
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

  it('does not rotate early when a skipped frame includes a long junction approach', () => {
    const farApproach = {
      ...approaching,
      roadProgress: 0.75,
      y: 10,
    }
    const fractions = [0.2, 0.4, 0.5, 0.6, 0.75, 0.85]
    const poses = fractions.map((fraction) => (
      interpolateVehicles([farApproach], [departing], fraction, geometry)[0]
    ))

    // The midpoint is still more than three metres before the connector. A
    // direct source→target heading interpolation used to yaw the sprite 45°
    // here even though its position was travelling due south.
    expect(poses[2].y).toBeGreaterThan(3)
    expect(Math.abs(shortestTurn(poses[2].headingDegrees, 180))).toBeLessThan(1)
    fractions.forEach((fraction) => {
      expect(headingToTrajectoryError(
        farApproach, departing, fraction, geometry,
      )).toBeLessThan(1)
    })
  })

  it('follows a curved approach before the connector instead of cutting through a block', () => {
    const curvedApproach = {
      ...geometry,
      lanes: [
        {
          roadId: 'AB',
          laneIndex: 0,
          centreline: [[0, 10], [0, 5], [-1.75, 0]],
        },
        geometry.lanes[1],
      ],
    }
    const earlier = {
      ...approaching,
      x: 0,
      y: 10,
      roadProgress: 0.6,
    }

    const [drawn] = interpolateVehicles([earlier], [departing], 0.25, curvedApproach)

    // A direct chord to the target has x=0.2 here. The mapped path first follows
    // the vertical lane and therefore remains on x=0 until it reaches the bend.
    expect(drawn.x).toBeCloseTo(0, 6)
    expect(drawn.y).toBeGreaterThan(5)
  })
})

describe('U-turn heading ambiguity', () => {
  const source = {
    id: 'u', type: 'CAR', roadId: 'IN', roadProgress: 0.9, laneIndex: 0,
    inJunction: false, x: 1, y: -1, headingDegrees: 0,
  }
  const target = {
    id: 'u', type: 'CAR', roadId: 'OUT', roadProgress: 0.1, laneIndex: 0,
    inJunction: false, x: -1, y: -1, headingDegrees: 180,
  }
  const geometry = {
    lanes: [
      { roadId: 'IN', laneIndex: 0, centreline: [[1, -10], [1, 0]] },
      { roadId: 'OUT', laneIndex: 0, centreline: [[-1, 0], [-1, -10]] },
    ],
    connectors: [{
      fromRoadId: 'IN',
      toRoadId: 'OUT',
      fromLane: 0,
      toLane: 0,
      // North -> west -> south: negative compass-bearing curvature.
      curve: [
        [1, 0],
        [0.9239, 0.3827],
        [0.7071, 0.7071],
        [0.3827, 0.9239],
        [0, 1],
        [-0.3827, 0.9239],
        [-0.7071, 0.7071],
        [-0.9239, 0.3827],
        [-1, 0],
      ],
    }],
  }

  it('rotates with a counter-clockwise connector instead of spinning right', () => {
    const [middle] = interpolateVehicles([source], [target], 0.5, geometry)

    expect(shortestTurn(source.headingDegrees, middle.headingDegrees))
      .toBeCloseTo(-90, 6)
    expect(middle.x).toBeCloseTo(0, 1)
    expect(middle.y).toBeGreaterThan(0.9)
    expect(interpolateVehicles([source], [target], 0, geometry)[0]).toEqual(source)
    expect(interpolateVehicles([source], [target], 1, geometry)[0]).toEqual(target)
  })

  it('keeps the U-turn direction monotonic along the connector tangent', () => {
    const poses = Array.from({ length: 17 }, (_, index) => (
      interpolateVehicles([source], [target], index / 16, geometry)[0]
    ))

    for (let index = 1; index < poses.length; index += 1) {
      const turn = shortestTurn(
        poses[index - 1].headingDegrees,
        poses[index].headingDegrees,
      )
      expect(turn).toBeLessThanOrEqual(1e-6)
      expect(turn).toBeGreaterThan(-45)
    }
  })
})
