import { describe, expect, it } from 'vitest'
import { vehicleTransform } from './vehicleSprites'

// The sprite textures need a renderer, but where a sprite goes does not — and
// that arithmetic is the part that silently sends every vehicle sideways.
describe('vehicleTransform', () => {
  const base = { x: 12, y: 34, headingDegrees: 0, lengthMetres: 4.5, widthMetres: 1.8 }

  it('flips north so it is up on the canvas', () => {
    const placed = vehicleTransform({ ...base, lengthMetres: 0 })
    expect(placed.x).toBe(12)
    expect(placed.y).toBe(-34)
  })

  it('uses the body centre resolved by the Physical View boundary', () => {
    const placed = vehicleTransform(base)
    expect(placed.x).toBe(12)
    expect(placed.y).toBe(-34)
  })

  it('does not apply a second half-length offset at any heading', () => {
    const placed = vehicleTransform({ ...base, headingDegrees: 90 })
    expect(placed.x).toBeCloseTo(12, 9)
    expect(placed.y).toBeCloseTo(-34, 9)
  })

  it('needs no rotation offset, because the texture is drawn nose-up', () => {
    expect(vehicleTransform({ ...base, headingDegrees: 0 }).rotation).toBe(0)
  })

  it('turns a compass bearing into radians clockwise', () => {
    expect(vehicleTransform({ ...base, headingDegrees: 90 }).rotation).toBeCloseTo(Math.PI / 2, 9)
    expect(vehicleTransform({ ...base, headingDegrees: 180 }).rotation).toBeCloseTo(Math.PI, 9)
  })

  it('sizes the sprite in metres, width across and length along', () => {
    const placed = vehicleTransform(base)
    expect(placed.width).toBe(1.8)
    expect(placed.height).toBe(4.5)
  })

  it('keeps a tiny vehicle visible rather than sub-pixel', () => {
    const placed = vehicleTransform({ ...base, lengthMetres: 0.1, widthMetres: 0.1 })
    expect(placed.width).toBe(0.7)
    expect(placed.height).toBe(1.5)
  })
})
