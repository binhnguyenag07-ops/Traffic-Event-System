import { describe, expect, it } from 'vitest'
import { drawSignals, drawStatic, drawTrails, laneDividers } from './drawScene'

// A stand-in for a PixiJS Graphics that records what was asked of it. The real
// one needs WebGL; what matters here is that the draw pass completes over
// realistic data and emits the shapes it should, because a throw or a hang in
// the ticker leaves a blank canvas with no other symptom.
function fakeGraphics() {
  const calls = { clear: 0, poly: 0, rect: 0, circle: 0, stroke: 0, fill: 0, moveTo: 0, lineTo: 0 }
  const self = {
    calls,
    clear: () => { calls.clear += 1; return self },
    poly: () => { calls.poly += 1; return self },
    rect: () => { calls.rect += 1; return self },
    circle: () => { calls.circle += 1; return self },
    stroke: () => { calls.stroke += 1; return self },
    fill: () => { calls.fill += 1; return self },
    moveTo: () => { calls.moveTo += 1; return self },
    lineTo: () => { calls.lineTo += 1; return self },
  }
  return self
}

const scene = {
  radiusMetres: 220,
  lanes: [
    { roadId: 'AB', laneIndex: 0, widthMetres: 3.5, centreline: [[0, 0], [40, 0], [80, 10]] },
    // A lane whose shape repeats a point, which OSM geometry does and the lane
    // offset preserves.
    { roadId: 'BC', laneIndex: 0, widthMetres: 3.0, centreline: [[0, 20], [0, 20], [30, 20]] },
  ],
  stopLines: [{ roadId: 'AB', from: [38, -5], to: [38, 5] }],
  connectors: [{ fromRoadId: 'AB', fromLane: 0, toRoadId: 'BC', toLane: 0, curve: [[40, 0], [45, 10], [40, 20]] }],
  junctions: [{ junctionId: 'B', pavement: [[35, -6], [45, -6], [45, 6], [35, 6]] }],
  buildings: [
    { id: 'B1', name: 'Block', type: 'RESIDENTIAL', centre: [10, 30], footprint: [[5, 25], [15, 25], [15, 35], [5, 35]] },
    { id: 'B2', name: 'Point only', type: 'HOSPITAL', centre: [60, 40], footprint: [] },
  ],
  signals: [{
    junctionId: 'B',
    approaches: [
      { roadId: 'AB', head: [38, 0], green: true, secondsRemaining: 12 },
      { roadId: 'BC', head: [0, 20], green: false, secondsRemaining: 12 },
    ],
  }],
  vehicles: [],
}

describe('drawStatic', () => {
  it('completes over a lane with a repeated shape point', () => {
    const graphics = fakeGraphics()
    // A zero-length segment used to make the dash walk step by nothing and spin.
    // vitest would hang rather than fail, so reaching the assertion is the test.
    drawStatic(graphics, scene)
    expect(graphics.calls.clear).toBe(1)
  })

  it('fills the ground, the junction pavement and both buildings', () => {
    const graphics = fakeGraphics()
    drawStatic(graphics, scene)
    // Ground and the point-only building are rects; pavement and the outlined
    // building are polygons.
    expect(graphics.calls.rect).toBe(2)
    expect(graphics.calls.poly).toBeGreaterThanOrEqual(2)
  })

  it('strokes lane surfaces and markings', () => {
    const graphics = fakeGraphics()
    drawStatic(graphics, scene)
    expect(graphics.calls.stroke).toBeGreaterThanOrEqual(5)
    expect(graphics.calls.lineTo).toBeGreaterThan(0)
  })

  it('keeps connector geometry out of the static paint pass', () => {
    const withConnectors = fakeGraphics()
    const withoutConnectors = fakeGraphics()
    drawStatic(withConnectors, scene)
    drawStatic(withoutConnectors, { ...scene, connectors: [] })
    expect(withConnectors.calls.stroke).toBe(withoutConnectors.calls.stroke)
    expect(withConnectors.calls.lineTo).toBe(withoutConnectors.calls.lineTo)
  })

  it('does nothing rather than throwing when there is no scene', () => {
    const graphics = fakeGraphics()
    expect(() => drawStatic(graphics, null)).not.toThrow()
    expect(graphics.calls.clear).toBe(1)
  })
})

describe('drawSignals', () => {
  it('draws a head per controlled approach', () => {
    const graphics = fakeGraphics()
    drawSignals(graphics, scene)
    // One unambiguous dot for each of the two approaches.
    expect(graphics.calls.circle).toBe(2)
  })

  it('is silent on a scene with no signals', () => {
    const graphics = fakeGraphics()
    drawSignals(graphics, { ...scene, signals: [] })
    expect(graphics.calls.circle).toBe(0)
  })
})

describe('drawTrails', () => {
  const rider = {
    id: 'V1', type: 'MOTORCYCLE', x: 10, y: 0, headingDegrees: 90,
    speedMps: 8, lengthMetres: 2.1, widthMetres: 0.75,
  }

  it('draws a trail behind a moving motorcycle so filtering reads', () => {
    const graphics = fakeGraphics()
    drawTrails(graphics, [rider])
    expect(graphics.calls.stroke).toBe(1)
  })

  it('batches all moving motorcycle trails into one stroke', () => {
    const graphics = fakeGraphics()
    drawTrails(graphics, [rider, { ...rider, id: 'V2', x: 20 }])
    expect(graphics.calls.stroke).toBe(1)
  })

  it('leaves out the trail for a stopped motorcycle', () => {
    const graphics = fakeGraphics()
    drawTrails(graphics, [{ ...rider, speedMps: 0 }])
    expect(graphics.calls.stroke).toBe(0)
  })

  it('draws nothing for other vehicle types, which are sprites', () => {
    const graphics = fakeGraphics()
    drawTrails(graphics, [{ ...rider, type: 'CAR' }, { ...rider, type: 'BUS' }])
    expect(graphics.calls.stroke).toBe(0)
  })

  it('handles an empty or missing list', () => {
    const graphics = fakeGraphics()
    expect(() => drawTrails(graphics, undefined)).not.toThrow()
    expect(graphics.calls.stroke).toBe(0)
  })
})

describe('laneDividers', () => {
  // A 2-lane road whose centres sit 3.5 m apart, both running due north.
  const road = (roadId, laneIndex, x) => ({
    roadId,
    laneIndex,
    widthMetres: 3.5,
    centreline: [[x, 0], [x, 100]],
  })

  it('paints the boundary between two lanes, not either lane centre', () => {
    const dividers = laneDividers([road('R', 0, -1.75), road('R', 1, 1.75)])

    expect(dividers).toHaveLength(1)
    // Markings separate lanes. A dash down a lane centre would sit exactly
    // where the traffic drives, which is what made vehicles look like they
    // were straddling the paint.
    expect(dividers[0]).toEqual([[0, 0], [0, 100]])
  })

  it('gives a single-lane road no divider at all', () => {
    expect(laneDividers([road('R', 0, 0)])).toEqual([])
  })

  it('never draws a divider between lanes of different roads', () => {
    expect(laneDividers([road('LEFT', 0, -1.75), road('RIGHT', 0, 1.75)])).toEqual([])
  })

  it('produces one divider fewer than the lane count', () => {
    const dividers = laneDividers([
      road('R', 0, -3.5), road('R', 1, 0), road('R', 2, 3.5),
    ])

    expect(dividers).toHaveLength(2)
    expect(dividers.map((line) => line[0][0])).toEqual([-1.75, 1.75])
  })
})
