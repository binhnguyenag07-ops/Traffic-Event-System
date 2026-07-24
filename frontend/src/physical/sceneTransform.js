// Metres to screen pixels for the physical view, and back.
//
// The scene arrives in local metres with the region's origin at (0, 0), x east
// and y north. The canvas measures pixels from its top-left with y running
// down. This module is the single place those two frames meet: the renderer
// draws only in pixels, the scene is only ever metres, and nothing in between
// mixes them. Keeping it as pure functions means the arithmetic is testable
// without a canvas.

/**
 * A transform that fits a scene of the given radius into a canvas, centred, with
 * a little padding. Pan and zoom compose on top without re-fitting.
 */
export function fitTransform(radiusMetres, width, height, { padding = 0.1, zoom = 1, panX = 0, panY = 0 } = {}) {
  const usable = Math.min(width, height) * (1 - padding)
  const baseScale = radiusMetres > 0 ? usable / (2 * radiusMetres) : 1
  const scale = baseScale * zoom
  return {
    scale,
    width,
    height,
    // World point (metres) -> screen point (pixels). North is up, so world y
    // is negated against the canvas' downward y.
    toScreen(x, y) {
      return [width / 2 + (x - panX) * scale, height / 2 - (y - panY) * scale]
    },
    // Screen point (pixels) -> world point (metres), the inverse, for picking.
    toWorld(px, py) {
      return [(px - width / 2) / scale + panX, -(py - height / 2) / scale + panY]
    },
    // Length in metres -> length in pixels, for lane and vehicle widths.
    lengthToScreen(metres) {
      return metres * scale
    },
  }
}

/**
 * The four corners of a vehicle box centred at a world point, facing a compass
 * heading, in screen pixels. Compass degrees run clockwise from north, so the
 * forward vector is (sin, cos) in world metres and (sin, -cos) on the canvas.
 */
export function vehicleCorners(transform, worldX, worldY, headingDegrees, lengthMetres, widthMetres) {
  const [cx, cy] = transform.toScreen(worldX, worldY)
  const rad = (headingDegrees * Math.PI) / 180
  const halfLength = transform.lengthToScreen(lengthMetres) / 2
  const halfWidth = transform.lengthToScreen(widthMetres) / 2
  // Forward points the way the vehicle faces; right is 90 degrees clockwise.
  const forward = [Math.sin(rad), -Math.cos(rad)]
  const right = [Math.cos(rad), Math.sin(rad)]
  const corner = (alongForward, alongRight) => [
    cx + forward[0] * alongForward + right[0] * alongRight,
    cy + forward[1] * alongForward + right[1] * alongRight,
  ]
  return [
    corner(halfLength, -halfWidth),
    corner(halfLength, halfWidth),
    corner(-halfLength, halfWidth),
    corner(-halfLength, -halfWidth),
  ]
}
