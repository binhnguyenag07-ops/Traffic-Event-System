import { describe, expect, it } from 'vitest'
import {
  fitScale,
  latLonToOffset,
  offsetToLatLon,
  vehicleCorners,
  worldPoint,
} from './sceneTransform'

describe('worldPoint', () => {
  it('leaves east alone and flips north so it is up on the canvas', () => {
    expect(worldPoint(30, 40)).toEqual([30, -40])
  })

  it('keeps the origin at zero', () => {
    expect(worldPoint(0, 0)).toEqual([0, -0])
  })
})

describe('fitScale', () => {
  it('fits the whole radius inside the smaller canvas dimension', () => {
    const scale = fitScale(100, 400, 400, 0.2)
    // Usable half-width is 160 px, so 100 m maps to 160 px.
    expect(100 * scale).toBeCloseTo(160, 5)
  })

  it('uses the smaller dimension so nothing is cut off', () => {
    expect(fitScale(100, 1000, 400)).toBe(fitScale(100, 400, 400))
  })

  it('survives a zero radius rather than dividing by it', () => {
    expect(fitScale(0, 400, 400)).toBe(1)
  })
})

describe('vehicleCorners', () => {
  it('returns a box the vehicle is long, facing north', () => {
    const corners = vehicleCorners(0, 0, 0, 4, 2)
    const ys = corners.map((corner) => corner[1])
    expect(Math.max(...ys) - Math.min(...ys)).toBeCloseTo(4, 6)
    // Facing north means the front is above centre, which is a smaller y.
    expect(Math.min(...ys)).toBeLessThan(0)
  })

  it('lies along x when facing east', () => {
    const corners = vehicleCorners(0, 0, 90, 4, 2)
    const xs = corners.map((corner) => corner[0])
    expect(Math.max(...xs) - Math.min(...xs)).toBeCloseTo(4, 6)
  })

  it('is centred on the vehicle position', () => {
    const corners = vehicleCorners(10, 20, 35, 4, 2)
    const cx = corners.reduce((sum, corner) => sum + corner[0], 0) / 4
    const cy = corners.reduce((sum, corner) => sum + corner[1], 0) / 4
    expect(cx).toBeCloseTo(10, 6)
    expect(cy).toBeCloseTo(-20, 6)
  })
})

describe('offsetToLatLon', () => {
  const origin = { latitude: 10, longitude: 106 }

  it('returns the origin for no offset', () => {
    const moved = offsetToLatLon(origin, 0, 0)
    expect(moved.latitude).toBeCloseTo(10, 9)
    expect(moved.longitude).toBeCloseTo(106, 9)
  })

  it('moves north by roughly 111 km per degree', () => {
    expect(offsetToLatLon(origin, 0, 111320).latitude).toBeCloseTo(11, 6)
  })

  it('shortens a degree of longitude by the cosine of the latitude', () => {
    const eastMetres = 111320 * Math.cos((10 * Math.PI) / 180)
    expect(offsetToLatLon(origin, eastMetres, 0).longitude).toBeCloseTo(107, 6)
  })

  it('inverts the projection the server applied', () => {
    // A point 250 m east and 120 m north must come back to the same offset.
    const moved = offsetToLatLon(origin, 250, 120)
    const backNorth = (moved.latitude - origin.latitude) * 111320
    const backEast = (moved.longitude - origin.longitude)
      * 111320 * Math.cos((origin.latitude * Math.PI) / 180)
    expect(backNorth).toBeCloseTo(120, 6)
    expect(backEast).toBeCloseTo(250, 6)
  })

  it('round-trips through the exported inverse projection', () => {
    const moved = offsetToLatLon(origin, 250, 120)
    expect(latLonToOffset(origin, moved)).toEqual({
      eastMetres: expect.closeTo(250, 6),
      northMetres: expect.closeTo(120, 6),
    })
  })
})
