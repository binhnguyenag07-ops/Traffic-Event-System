// Coordinates for the physical view.
//
// The scene arrives in local metres with the region's origin at (0, 0), x east
// and y north. Everything is drawn in those metres, with y negated so north is
// up on a canvas whose y runs down. Zoom and pan are then a scale and a
// translation on the PixiJS container that holds the drawing.
//
// That split matters. Baking zoom into the drawing meant every layer had to be
// redrawn together to stay consistent, and when one was missed the geometry and
// the vehicles sat at different scales until the next poll. With the transform
// on the container, Pixi applies it to every layer at once and they cannot
// disagree — and zooming costs no redraw at all.

/** Metres in one degree of latitude, matching the server's projection. */
const METRES_PER_DEGREE_LATITUDE = 111320

/** A world point in drawing space: metres, y flipped so north is up. */
export function worldPoint(x, y) {
  return [x, -y]
}

/** Pixels per metre that fits a scene of the given radius into the canvas. */
export function fitScale(radiusMetres, width, height, padding = 0.08) {
  const usable = Math.min(width, height) * (1 - padding)
  return radiusMetres > 0 ? usable / (2 * radiusMetres) : 1
}

/**
 * The four corners of a vehicle box, in world metres.
 *
 * Compass degrees run clockwise from north, so the forward vector is
 * (sin, -cos) once y is flipped, and right is 90 degrees clockwise from it.
 */
export function vehicleCorners(x, y, headingDegrees, lengthMetres, widthMetres) {
  const [cx, cy] = worldPoint(x, y)
  const rad = (headingDegrees * Math.PI) / 180
  const halfLength = lengthMetres / 2
  const halfWidth = widthMetres / 2
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

/**
 * The latitude and longitude a metre offset from the scene origin lands on.
 *
 * Panning moves the viewport in metres, but a fresh scene has to be requested
 * for a geographic point, so this is how the two meet. It is the exact inverse
 * of the projection the server applies.
 */
export function offsetToLatLon(origin, eastMetres, northMetres) {
  const latitude = origin.latitude + northMetres / METRES_PER_DEGREE_LATITUDE
  const eastScale = METRES_PER_DEGREE_LATITUDE * Math.cos((origin.latitude * Math.PI) / 180)
  const longitude = origin.longitude + (eastScale === 0 ? 0 : eastMetres / eastScale)
  return { latitude, longitude }
}

/** Projects a geographic point into metres relative to a retained scene origin. */
export function latLonToOffset(origin, point) {
  const northMetres = (point.latitude - origin.latitude) * METRES_PER_DEGREE_LATITUDE
  const eastScale = METRES_PER_DEGREE_LATITUDE * Math.cos((origin.latitude * Math.PI) / 180)
  return {
    eastMetres: (point.longitude - origin.longitude) * eastScale,
    northMetres,
  }
}
