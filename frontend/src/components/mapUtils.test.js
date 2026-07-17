import { describe, expect, it } from 'vitest'
import {
  HCMC_CENTER,
  HCMC_DISTRICT_BOUNDS,
  positionAlongPath,
  positionFor,
  positionsForRoad,
  roadColor,
} from './mapUtils'

describe('map utilities', () => {
  it('uses geographic coordinates when supplied', () => {
    expect(positionFor({ latitude: 10.77, longitude: 106.7 })).toEqual([10.77, 106.7])
  })

  it('projects synthetic coordinates around the demo centre', () => {
    expect(positionFor({ x: 2, y: 1 })).toEqual([HCMC_CENTER[0] + 0.0035, HCMC_CENTER[1] + 0.007])
  })

  it('keeps the demo centre inside the district operating bounds', () => {
    const [[south, west], [north, east]] = HCMC_DISTRICT_BOUNDS
    expect(HCMC_CENTER[0]).toBeGreaterThan(south)
    expect(HCMC_CENTER[0]).toBeLessThan(north)
    expect(HCMC_CENTER[1]).toBeGreaterThan(west)
    expect(HCMC_CENTER[1]).toBeLessThan(east)
  })

  it('gives blocked roads priority over traffic pressure colours', () => {
    expect(roadColor({ status: 'BLOCKED', congestionLevel: 0 })).toBe('#b4232f')
    expect(roadColor({ status: 'OPEN', vehicleDensity: 0.8 })).toBe('#e24a34')
  })

  it('uses real road geometry when available', () => {
    const geometry = [
      { latitude: 10.77, longitude: 106.69 },
      { latitude: 10.78, longitude: 106.70 },
      { latitude: 10.79, longitude: 106.71 },
    ]
    expect(positionsForRoad({ geometry }, {}, {})).toEqual([
      [10.77, 106.69],
      [10.78, 106.70],
      [10.79, 106.71],
    ])
  })

  it('moves a vehicle along every segment in a road polyline', () => {
    const path = [[0, 0], [0, 1], [0, 3]]
    expect(positionAlongPath(path, 0.5)).toEqual([0, 1.5])
    expect(positionAlongPath(path, 1)).toEqual([0, 3])
  })
})
