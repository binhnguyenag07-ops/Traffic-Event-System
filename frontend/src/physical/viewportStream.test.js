import { describe, expect, it } from 'vitest'
import {
  rebasePhysicalState,
  physicalTimelineChanged,
  sameViewportQuery,
  shouldApplyViewportState,
  viewportStreamRadius,
  visibleViewportVehicles,
} from './viewportStream'

describe('physical viewport state streaming', () => {
  it('requests only the visible camera plus a bounded prefetch ring', () => {
    // 800×600 at 2 px/m has a 250 m corner radius; add 20% overscan.
    expect(viewportStreamRadius(800, 600, 2)).toBeCloseTo(300, 6)
    expect(viewportStreamRadius(100, 100, 100)).toBe(120)
  })

  it('distinguishes a new viewport even when simulation frame time is unchanged', () => {
    const first = { latitude: 10.77, longitude: 106.69, radiusMetres: 300 }
    expect(sameViewportQuery(first, { ...first })).toBe(true)
    expect(sameViewportQuery(first, {
      ...first,
      longitude: 106.691,
    })).toBe(false)
    expect(sameViewportQuery(first, {
      ...first,
      radiusMetres: 500,
    })).toBe(false)
    expect(sameViewportQuery(null, first)).toBe(false)

    expect(shouldApplyViewportState(42, 42, first, { ...first })).toBe(false)
    expect(shouldApplyViewportState(42, 42, first, {
      ...first,
      longitude: 106.691,
    })).toBe(true)
    expect(shouldApplyViewportState(42, 43, first, { ...first })).toBe(true)
  })

  it('lets the first local state establish a geometry-only scene timeline', () => {
    expect(physicalTimelineChanged('none', 'timeline-live')).toBe(false)
    expect(physicalTimelineChanged('timeline-live', 'timeline-live')).toBe(false)
    expect(physicalTimelineChanged('timeline-old', 'timeline-live')).toBe(true)
  })

  it('rebases camera-centred vehicles and signals to retained full-map metres', () => {
    const retainedOrigin = { latitude: 10, longitude: 106 }
    const state = rebasePhysicalState({
      origin: { latitude: 10.001, longitude: 106 },
      vehicles: [{ id: 'v', x: 5, y: 7 }],
      signals: [{ junctionId: 'j', approaches: [{ roadId: 'r', head: [2, 3] }] }],
    }, retainedOrigin)

    expect(state.origin).toBe(retainedOrigin)
    expect(state.vehicles[0].x).toBeCloseTo(5, 6)
    expect(state.vehicles[0].y).toBeCloseTo(118.32, 6)
    expect(state.signals[0].approaches[0].head[0]).toBeCloseTo(2, 6)
    expect(state.signals[0].approaches[0].head[1]).toBeCloseTo(114.32, 6)
  })

  it('submits only visible and near-edge vehicles to Pixi', () => {
    const vehicles = [
      { id: 'centre', x: 100, y: 50 },
      { id: 'edge', x: 369, y: 50 },
      { id: 'far', x: 500, y: 50 },
    ]
    expect(visibleViewportVehicles(
      vehicles,
      { x: 100, y: 50 },
      1000,
      600,
      2,
    ).map((vehicle) => vehicle.id)).toEqual(['centre', 'edge'])
  })
})
