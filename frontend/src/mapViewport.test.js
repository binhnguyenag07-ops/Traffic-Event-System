import { describe, expect, it } from 'vitest'
import {
  cameraAfterZoom,
  clampMapZoom,
  MAX_MAP_ZOOM,
  MIN_MAP_ZOOM,
  pixelsPerMetreAtZoom,
} from './mapViewport'

describe('shared map zoom', () => {
  it('doubles physical scale for each Leaflet zoom level', () => {
    const scale18 = pixelsPerMetreAtZoom(10.78, 18)
    expect(pixelsPerMetreAtZoom(10.78, 19)).toBeCloseTo(scale18 * 2, 10)
  })

  it('clamps both renderers to their shared zoom range', () => {
    expect(clampMapZoom(10)).toBe(MIN_MAP_ZOOM)
    expect(clampMapZoom(30)).toBe(MAX_MAP_ZOOM)
  })

  it('keeps the world point under the cursor fixed while zooming', () => {
    const camera = { x: 25, y: -12 }
    const screen = { x: 140, y: -80 }
    const oldScale = 2
    const newScale = 4
    const worldEast = camera.x + screen.x / oldScale
    const worldNorth = camera.y - screen.y / oldScale

    const next = cameraAfterZoom(camera, screen.x, screen.y, oldScale, newScale)

    expect((worldEast - next.x) * newScale).toBeCloseTo(screen.x, 10)
    expect((next.y - worldNorth) * newScale).toBeCloseTo(screen.y, 10)
  })
})
