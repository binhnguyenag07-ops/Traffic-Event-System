import { latLonToOffset } from './sceneTransform'

const MIN_STREAM_RADIUS_METRES = 120
const STREAM_OVERSCAN_FACTOR = 1.2
const SPRITE_OVERSCAN_METRES = 20
const QUERY_POSITION_EPSILON_METRES = 0.05
const QUERY_RADIUS_EPSILON_METRES = 0.05

/**
 * Radius needed to cover the actual camera, with a small prefetch ring for
 * vehicles about to enter it. Zoomed-in lane views therefore do not download
 * or animate the district-wide fleet.
 */
export function viewportStreamRadius(widthPixels, heightPixels, pixelsPerMetre) {
  if (!(pixelsPerMetre > 0)) return MIN_STREAM_RADIUS_METRES
  const cornerDistance = Math.hypot(widthPixels / 2, heightPixels / 2) / pixelsPerMetre
  return Math.max(MIN_STREAM_RADIUS_METRES, cornerDistance * STREAM_OVERSCAN_FACTOR)
}

/**
 * A frame id identifies simulation time, not the spatial subset in a response.
 * While paused, the frame stays fixed but a pan/zoom must still replace the old
 * vehicles and signals with those around the new camera.
 */
export function sameViewportQuery(previous, next) {
  if (!previous || !next) return false
  const moved = latLonToOffset(previous, next)
  return Math.hypot(moved.eastMetres, moved.northMetres)
      <= QUERY_POSITION_EPSILON_METRES
    && Math.abs(Number(previous.radiusMetres) - Number(next.radiusMetres))
      <= QUERY_RADIUS_EPSILON_METRES
}

export function shouldApplyViewportState(
  currentFrameId,
  nextFrameId,
  previousQuery,
  nextQuery,
) {
  return Number(nextFrameId) !== Number(currentFrameId)
    || !sameViewportQuery(previousQuery, nextQuery)
}

export function physicalTimelineChanged(currentTimelineId, nextTimelineId) {
  // A geometry-only response intentionally carries no dynamic frame metadata.
  // Its first state response establishes the live timeline instead of forcing
  // another geometry fetch.
  return Boolean(currentTimelineId)
    && currentTimelineId !== 'none'
    && currentTimelineId !== nextTimelineId
}

/**
 * State queries are centred on the moving camera, while static geometry keeps
 * the full-map origin loaded at boot. Rebase the lightweight response once so
 * all layers continue to share one metre coordinate system.
 */
export function rebasePhysicalState(state, retainedOrigin) {
  if (!state?.origin || !retainedOrigin) return state
  const { eastMetres, northMetres } = latLonToOffset(retainedOrigin, state.origin)
  const point = (head) => (
    Array.isArray(head) && head.length >= 2
      ? [head[0] + eastMetres, head[1] + northMetres]
      : head
  )
  return {
    ...state,
    origin: retainedOrigin,
    vehicles: (state.vehicles || []).map((vehicle) => ({
      ...vehicle,
      x: vehicle.x + eastMetres,
      y: vehicle.y + northMetres,
    })),
    signals: (state.signals || []).map((signal) => ({
      ...signal,
      approaches: (signal.approaches || []).map((approach) => ({
        ...approach,
        head: point(approach.head),
      })),
    })),
  }
}

/** Retains only sprites intersecting the visible camera plus a small edge ring. */
export function visibleViewportVehicles(
  vehicles,
  camera,
  widthPixels,
  heightPixels,
  pixelsPerMetre,
) {
  if (!(pixelsPerMetre > 0)) return vehicles || []
  const halfWidth = widthPixels / (2 * pixelsPerMetre) + SPRITE_OVERSCAN_METRES
  const halfHeight = heightPixels / (2 * pixelsPerMetre) + SPRITE_OVERSCAN_METRES
  return (vehicles || []).filter((vehicle) => (
    Math.abs(vehicle.x - camera.x) <= halfWidth
      && Math.abs(vehicle.y - camera.y) <= halfHeight
  ))
}
