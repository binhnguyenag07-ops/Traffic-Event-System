import { describe, expect, it } from 'vitest'
import { fitTransform, vehicleCorners } from './sceneTransform'

describe('fitTransform', () => {
  it('places the world origin at the canvas centre', () => {
    const transform = fitTransform(150, 800, 600)
    expect(transform.toScreen(0, 0)).toEqual([400, 300])
  })

  it('puts north above the centre and east to its right', () => {
    const transform = fitTransform(150, 800, 600)
    const [, northY] = transform.toScreen(0, 50)
    const [eastX] = transform.toScreen(50, 0)
    expect(northY).toBeLessThan(300) // smaller y is higher on the canvas
    expect(eastX).toBeGreaterThan(400)
  })

  it('fits the radius inside the smaller canvas dimension with padding', () => {
    const transform = fitTransform(100, 400, 400, { padding: 0.2 })
    // 100 m maps to (200 - padding) px each way: usable half is 160, so the
    // edge of the radius lands 160 px from centre.
    const [edgeX] = transform.toScreen(100, 0)
    expect(edgeX - 200).toBeCloseTo(160, 5)
  })

  it('round-trips a point through toScreen and toWorld', () => {
    const transform = fitTransform(150, 800, 600, { zoom: 1.7, panX: 12, panY: -8 })
    const [px, py] = transform.toScreen(34, -21)
    const [wx, wy] = transform.toWorld(px, py)
    expect(wx).toBeCloseTo(34, 6)
    expect(wy).toBeCloseTo(-21, 6)
  })
})

describe('vehicleCorners', () => {
  it('returns a box a vehicle-length long facing north', () => {
    const transform = fitTransform(50, 400, 400) // scale = 180/50 = 3.6 px/m
    const corners = vehicleCorners(transform, 0, 0, 0, 4, 2)
    // Facing north (heading 0): the front two corners are above centre (300),
    // the rear two below.
    const ys = corners.map((corner) => corner[1])
    expect(Math.min(...ys)).toBeLessThan(200)
    expect(Math.max(...ys)).toBeGreaterThan(200)
    // Length 4 m at 3.6 px/m spans 14.4 px front-to-back.
    expect(Math.max(...ys) - Math.min(...ys)).toBeCloseTo(4 * transform.scale, 5)
  })

  it('faces east when heading is 90 degrees', () => {
    const transform = fitTransform(50, 400, 400)
    const corners = vehicleCorners(transform, 0, 0, 90, 4, 2)
    const xs = corners.map((corner) => corner[0])
    // Facing east: the box is longer along x than a north-facing one would be.
    expect(Math.max(...xs) - Math.min(...xs)).toBeCloseTo(4 * transform.scale, 5)
  })
})
