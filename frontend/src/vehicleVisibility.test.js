import { describe, expect, it } from 'vitest'
import { isVehicleVisibleOnMap } from './vehicleVisibility'

describe('map vehicle visibility', () => {
  it.each(['ARRIVED', 'DWELLING'])('hides %s vehicles that are off the road', (status) => {
    expect(isVehicleVisibleOnMap({ status })).toBe(false)
  })

  it.each(['MOVING', 'WAITING', 'BLOCKED', 'STOPPED'])(
    'keeps %s vehicles that still occupy the road visible',
    (status) => {
      expect(isVehicleVisibleOnMap({ status })).toBe(true)
    },
  )
})
