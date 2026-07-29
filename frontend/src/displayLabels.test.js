import { describe, expect, it } from 'vitest'
import { buildingLabel, intersectionLabel, roadLabel } from './displayLabels'

const intersections = new Map([
  ['A', {
    id: 'A', name: 'Hàm Nghi / Hồ Tùng Mậu', latitude: 10.77001, longitude: 106.70001,
  }],
  ['B', {
    id: 'B', name: 'Chợ Bến Thành', latitude: 10.77002, longitude: 106.70002,
  }],
])

describe('human-readable network labels', () => {
  it('keeps opaque junction ids out and disambiguates repeated street names', () => {
    expect(intersectionLabel(intersections.get('A')))
      .toBe('Hàm Nghi / Hồ Tùng Mậu · 10.770010, 106.700010')
    expect(intersectionLabel({ id: 'N123', latitude: 10.77, longitude: 106.7 }))
      .toBe('Junction at 10.770000, 106.700000')
  })

  it('describes a directed road by street and endpoint names', () => {
    expect(roadLabel({
      name: 'Lê Lợi',
      startIntersectionId: 'A',
      endIntersectionId: 'B',
      laneCount: 3,
      distance: 0.12,
    }, intersections)).toBe(
      'Lê Lợi — Hàm Nghi / Hồ Tùng Mậu · 10.770010, 106.700010'
      + ' → Chợ Bến Thành · 10.770020, 106.700020 · 3 lanes · 120 m',
    )
  })

  it('turns OSM-generated building ids into a useful location label', () => {
    expect(buildingLabel({
      id: 'B35114136',
      name: 'Building 35114136',
      type: 'RESIDENTIAL',
      latitude: 10.77123,
      longitude: 106.70123,
      accessPoints: [{ intersectionId: 'A' }],
    }, intersections)).toBe(
      'Residential building · near Hàm Nghi / Hồ Tùng Mậu · 10.770010, 106.700010'
      + ' · 10.771230, 106.701230',
    )
  })

  it('disambiguates named buildings without exposing their ids', () => {
    expect(buildingLabel({
      id: 'B99',
      name: 'City Hall',
      type: 'OFFICE',
      latitude: 10.77555,
      longitude: 106.70222,
      accessPoints: [{ intersectionId: 'B' }],
    }, intersections)).toBe(
      'City Hall · near Chợ Bến Thành · 10.770020, 106.700020'
      + ' · 10.775550, 106.702220',
    )
  })
})
