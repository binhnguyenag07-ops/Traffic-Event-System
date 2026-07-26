// Viewport math shared by the Leaflet macro map and the PixiJS physical map.
//
// Keeping the zoom in Leaflet's Web Mercator units means switching views does
// not need an arbitrary conversion table: the same latitude, longitude and
// zoom describe the same camera in both renderers.

const WEB_MERCATOR_METRES_PER_PIXEL_AT_ZOOM_ZERO = 156543.03392804097

export const MIN_MAP_ZOOM = 16
export const MAX_MAP_ZOOM = 21
export const DEFAULT_PHYSICAL_ZOOM = 18

export function clampMapZoom(zoom) {
  const numeric = Number(zoom)
  if (!Number.isFinite(numeric)) return DEFAULT_PHYSICAL_ZOOM
  return Math.max(MIN_MAP_ZOOM, Math.min(MAX_MAP_ZOOM, numeric))
}

/** CSS pixels per ground metre at a latitude and Web Mercator zoom. */
export function pixelsPerMetreAtZoom(latitude, zoom) {
  const radians = (Number(latitude) * Math.PI) / 180
  const latitudeScale = Math.max(1e-6, Math.abs(Math.cos(radians)))
  const metresPerPixel = WEB_MERCATOR_METRES_PER_PIXEL_AT_ZOOM_ZERO
    * latitudeScale / (2 ** clampMapZoom(zoom))
  return 1 / metresPerPixel
}

/**
 * Move a camera while its scale changes so one screen point remains anchored.
 *
 * Camera x is east and camera y is north. Canvas y points down, hence the
 * opposite sign in the north adjustment.
 */
export function cameraAfterZoom(camera, screenX, screenY, oldScale, newScale) {
  if (!(oldScale > 0) || !(newScale > 0)) return { x: camera.x, y: camera.y }
  return {
    x: camera.x + screenX * (1 / oldScale - 1 / newScale),
    y: camera.y + screenY * (1 / newScale - 1 / oldScale),
  }
}
