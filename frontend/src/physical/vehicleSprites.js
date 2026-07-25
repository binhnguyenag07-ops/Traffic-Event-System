// Vehicle artwork for the physical view.
//
// The textures are drawn here and baked once at start-up rather than shipped as
// image files. That keeps the repository free of binary art nobody can review,
// and it means a sprite is defined in metres alongside the vehicle it stands
// for, so a bus is twelve metres of bus rather than whatever a PNG happened to
// be scaled to.
//
// Each texture is drawn nose-up. In world space north is up too, so a sprite's
// rotation is simply its compass heading.

import { Container, Graphics, Sprite } from 'pixi.js'

/** Texture resolution. Enough to stay crisp when zoomed well past 1:1. */
const PIXELS_PER_METRE = 32

const BODY = {
  CAR: 0x2f7fd4,
  MOTORCYCLE: 0xe0a415,
  BUS: 0xe2673c,
  EMERGENCY: 0xd63b3b,
}

const GLASS = 0x16212e
const TRIM = 0x0f151d

const SIZE = {
  CAR: { length: 4.5, width: 1.8 },
  MOTORCYCLE: { length: 2.1, width: 0.75 },
  BUS: { length: 12, width: 2.5 },
  EMERGENCY: { length: 5, width: 2 },
}

function roundedBody(graphics, w, h, colour, radius) {
  graphics.roundRect(0, 0, w, h, radius).fill({ color: colour })
}

function carTexture(colour, length, width) {
  const w = width * PIXELS_PER_METRE
  const h = length * PIXELS_PER_METRE
  const g = new Graphics()
  roundedBody(g, w, h, colour, w * 0.28)
  // Windscreen at the nose, rear window behind, so the direction of travel is
  // obvious without needing a separate marker.
  g.roundRect(w * 0.16, h * 0.16, w * 0.68, h * 0.22, w * 0.1).fill({ color: GLASS })
  g.roundRect(w * 0.16, h * 0.62, w * 0.68, h * 0.18, w * 0.1).fill({ color: GLASS })
  g.rect(w * 0.1, h * 0.02, w * 0.18, h * 0.04).fill({ color: 0xfff3c4 })
  g.rect(w * 0.72, h * 0.02, w * 0.18, h * 0.04).fill({ color: 0xfff3c4 })
  return g
}

function motorcycleTexture(colour, length, width) {
  const w = width * PIXELS_PER_METRE
  const h = length * PIXELS_PER_METRE
  const g = new Graphics()
  roundedBody(g, w * 0.55, h, colour, w * 0.3)
  g.x = 0
  // The rider is wider than the machine, which is what makes a motorcycle
  // read as one from above.
  g.roundRect(-w * 0.22, h * 0.34, w, h * 0.34, w * 0.3).fill({ color: TRIM })
  g.circle(w * 0.28, h * 0.28, w * 0.26).fill({ color: colour })
  return g
}

function busTexture(colour, length, width) {
  const w = width * PIXELS_PER_METRE
  const h = length * PIXELS_PER_METRE
  const g = new Graphics()
  roundedBody(g, w, h, colour, w * 0.16)
  g.roundRect(w * 0.12, h * 0.04, w * 0.76, h * 0.1, w * 0.06).fill({ color: GLASS })
  for (let i = 0; i < 5; i += 1) {
    g.rect(w * 0.06, h * (0.2 + i * 0.15), w * 0.1, h * 0.1).fill({ color: GLASS })
    g.rect(w * 0.84, h * (0.2 + i * 0.15), w * 0.1, h * 0.1).fill({ color: GLASS })
  }
  return g
}

function emergencyTexture(colour, length, width) {
  const g = carTexture(colour, length, width)
  const w = width * PIXELS_PER_METRE
  const h = length * PIXELS_PER_METRE
  // Light bar across the roof.
  g.rect(w * 0.12, h * 0.42, w * 0.34, h * 0.07).fill({ color: 0x3b7fe0 })
  g.rect(w * 0.54, h * 0.42, w * 0.34, h * 0.07).fill({ color: 0xffffff })
  return g
}

const DRAW = {
  CAR: carTexture,
  MOTORCYCLE: motorcycleTexture,
  BUS: busTexture,
  EMERGENCY: emergencyTexture,
}

/** Bakes one texture per vehicle type. Called once, when the scene starts. */
export function buildVehicleTextures(renderer) {
  const textures = {}
  Object.keys(SIZE).forEach((type) => {
    const { length, width } = SIZE[type]
    const graphics = DRAW[type](BODY[type], length, width)
    textures[type] = renderer.generateTexture({ target: graphics })
    graphics.destroy()
  })
  return textures
}

/**
 * Where and how big a sprite goes, in world metres.
 *
 * Kept apart from the drawing so the arithmetic can be tested without a
 * renderer: the y flip that puts north up, and the fact that a nose-up texture
 * needs no rotation offset, are both easy to get wrong and invisible until
 * every vehicle drives sideways.
 */
export function vehicleTransform(vehicle) {
  return {
    x: vehicle.x,
    y: -vehicle.y,
    rotation: (vehicle.headingDegrees * Math.PI) / 180,
    width: Math.max(vehicle.widthMetres, 0.7),
    height: Math.max(vehicle.lengthMetres, 1.5),
  }
}

/**
 * The vehicles on screen, as sprites reused between frames.
 *
 * Sprites are kept per vehicle id rather than rebuilt each frame: a vehicle that
 * is still there keeps its sprite, so the renderer batches them instead of
 * churning through allocations sixty times a second.
 */
export class VehicleLayer {
  constructor(textures) {
    this.textures = textures
    this.container = new Container()
    this.sprites = new Map()
  }

  update(vehicles) {
    const seen = new Set()
    ;(vehicles || []).forEach((vehicle) => {
      const type = String(vehicle.type || 'CAR').toUpperCase()
      const texture = this.textures[type] || this.textures.CAR
      let sprite = this.sprites.get(vehicle.id)
      if (!sprite) {
        sprite = new Sprite(texture)
        sprite.anchor.set(0.5)
        this.container.addChild(sprite)
        this.sprites.set(vehicle.id, sprite)
      } else if (sprite.texture !== texture) {
        sprite.texture = texture
      }
      const placed = vehicleTransform(vehicle)
      sprite.position.set(placed.x, placed.y)
      sprite.rotation = placed.rotation
      sprite.width = placed.width
      sprite.height = placed.height
      seen.add(vehicle.id)
    })

    this.sprites.forEach((sprite, id) => {
      if (seen.has(id)) return
      sprite.destroy()
      this.sprites.delete(id)
    })
  }
}
