// Smooths vehicle poses between two authoritative scenes.
//
// A straight x/y lerp is only correct on a straight road. At a junction it cuts
// across the paved corner even though the scene already contains lane connector
// curves. The helpers below sample the actual lane or connector polyline, while
// still using the authoritative snapshots as the exact endpoints.

const EPSILON = 1e-6
const CONNECTOR_HALF_SAMPLES = 8

/** Shortest signed turn from one compass bearing to another, in degrees. */
export function shortestTurn(fromDegrees, toDegrees) {
  const difference = ((toDegrees - fromDegrees + 540) % 360) - 180
  return difference === -180 ? 180 : difference
}

function clamp01(value) {
  return Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0))
}

function distance(a, b) {
  return Math.hypot(b[0] - a[0], b[1] - a[1])
}

function vehiclePoint(vehicle) {
  return [Number(vehicle.x), Number(vehicle.y)]
}

function cleanPath(points) {
  const result = []
  ;(points || []).forEach((point) => {
    if (!Array.isArray(point) || !Number.isFinite(point[0]) || !Number.isFinite(point[1])) return
    const previous = result[result.length - 1]
    if (!previous || distance(previous, point) > EPSILON) result.push([point[0], point[1]])
  })
  return result
}

function pointAtPolyline(points, fraction) {
  const path = cleanPath(points)
  if (path.length === 0) return null
  if (path.length === 1) return path[0]

  const lengths = path.slice(1).map((point, index) => distance(path[index], point))
  const total = lengths.reduce((sum, length) => sum + length, 0)
  if (total <= EPSILON) return path[0]

  let remaining = total * clamp01(fraction)
  for (let index = 0; index < lengths.length; index += 1) {
    const length = lengths[index]
    if (remaining <= length || index === lengths.length - 1) {
      const within = length <= EPSILON ? 0 : remaining / length
      return [
        path[index][0] + (path[index + 1][0] - path[index][0]) * within,
        path[index][1] + (path[index + 1][1] - path[index][1]) * within,
      ]
    }
    remaining -= length
  }
  return path[path.length - 1]
}

function bearingAtPolyline(points, fraction, fallback) {
  const before = pointAtPolyline(points, Math.max(0, clamp01(fraction) - 0.002))
  const after = pointAtPolyline(points, Math.min(1, clamp01(fraction) + 0.002))
  if (!before || !after || distance(before, after) <= EPSILON) return fallback
  return (
    Math.atan2(after[0] - before[0], after[1] - before[1]) * 180 / Math.PI
    + 360
  ) % 360
}

function sampleRange(points, from, to) {
  return Array.from({ length: CONNECTOR_HALF_SAMPLES + 1 }, (_, index) => (
    pointAtPolyline(points, from + (to - from) * index / CONNECTOR_HALF_SAMPLES)
  )).filter(Boolean)
}

function findConnector(connectors, fromRoadId, toRoadId, fromLane, toLane) {
  if (!fromRoadId || !toRoadId) return null
  const candidates = (connectors || []).filter((connector) => (
    connector.fromRoadId === fromRoadId && connector.toRoadId === toRoadId
  ))
  return candidates.sort((a, b) => {
    const score = (connector) => (
      (connector.fromLane === fromLane ? 2 : 0)
      + (connector.toLane === toLane ? 1 : 0)
    )
    return score(b) - score(a)
  })[0] || null
}

function findLane(lanes, source, target) {
  const candidates = (lanes || []).filter((lane) => lane.roadId === target.roadId)
  return candidates.find((lane) => lane.laneIndex === target.laneIndex)
    || candidates.find((lane) => lane.laneIndex === source.laneIndex)
    || null
}

function followPath(target, path, fraction, fallbackHeading) {
  const point = pointAtPolyline(path, fraction)
  if (!point) return target
  return {
    ...target,
    x: point[0],
    y: point[1],
    headingDegrees: bearingAtPolyline(path, fraction, fallbackHeading),
  }
}

function lanePose(source, target, lane, fraction) {
  const sourceProgress = clamp01(source.roadProgress)
  const targetProgress = clamp01(target.roadProgress)
  const progress = sourceProgress + (targetProgress - sourceProgress) * fraction
  const sourceBase = pointAtPolyline(lane.centreline, sourceProgress)
  const targetBase = pointAtPolyline(lane.centreline, targetProgress)
  const point = pointAtPolyline(lane.centreline, progress)
  if (!sourceBase || !targetBase || !point) return null

  // The lane centreline is the stable longitudinal path. These small endpoint
  // corrections retain the vehicle's authoritative lateral position, including
  // a motorcycle or car part-way through a lane change.
  const correctionX = (source.x - sourceBase[0])
    + ((target.x - targetBase[0]) - (source.x - sourceBase[0])) * fraction
  const correctionY = (source.y - sourceBase[1])
    + ((target.y - targetBase[1]) - (source.y - sourceBase[1])) * fraction
  return {
    ...target,
    x: point[0] + correctionX,
    y: point[1] + correctionY,
    headingDegrees: bearingAtPolyline(
      lane.centreline,
      progress,
      source.headingDegrees + shortestTurn(source.headingDegrees, target.headingDegrees) * fraction,
    ),
  }
}

function junctionAnchor(vehicle, connectors) {
  if (!vehicle?.inJunction) return vehicle
  const connector = findConnector(
    connectors,
    vehicle.junctionFromRoadId,
    vehicle.junctionToRoadId,
    vehicle.laneIndex,
    vehicle.laneIndex,
  )
  if (!connector) return vehicle
  return followPath(vehicle, connector.curve, 0.5, vehicle.headingDegrees)
}

/**
 * Vehicles interpolated a fraction {@code t} of the way from {@code previous}
 * towards {@code next}. Static scene geometry is optional so callers without a
 * physical map retain the straight-line fallback.
 */
export function interpolateVehicles(previous, next, t, geometry = {}) {
  const fraction = clamp01(t)
  const before = new Map((previous || []).map((vehicle) => [vehicle.id, vehicle]))
  const connectors = geometry?.connectors || []
  const lanes = geometry?.lanes || []

  return (next || []).map((target) => {
    const source = before.get(target.id)
    if (!source) return junctionAnchor(target, connectors)

    const unchanged = source.x === target.x
      && source.y === target.y
      && source.headingDegrees === target.headingDegrees
      && source.roadId === target.roadId
      && source.inJunction === target.inJunction
    if (unchanged) return junctionAnchor(target, connectors)

    if (source.roadId && source.roadId === target.roadId
      && Number.isFinite(source.roadProgress) && Number.isFinite(target.roadProgress)) {
      const lane = findLane(lanes, source, target)
      const pose = lane && lanePose(source, target, lane, fraction)
      if (pose) return pose
    }

    if (target.inJunction
      && source.roadId === target.junctionFromRoadId
      && target.junctionToRoadId) {
      const connector = findConnector(
        connectors,
        target.junctionFromRoadId,
        target.junctionToRoadId,
        source.laneIndex,
        target.laneIndex,
      )
      if (connector) {
        return followPath(
          target,
          [vehiclePoint(source), ...sampleRange(connector.curve, 0, 0.5)],
          fraction,
          target.headingDegrees,
        )
      }
    }

    if (source.inJunction
      && source.junctionFromRoadId
      && source.junctionToRoadId === target.roadId) {
      const connector = findConnector(
        connectors,
        source.junctionFromRoadId,
        source.junctionToRoadId,
        source.laneIndex,
        target.laneIndex,
      )
      if (connector) {
        return followPath(
          target,
          [...sampleRange(connector.curve, 0.5, 1), vehiclePoint(target)],
          fraction,
          target.headingDegrees,
        )
      }
    }

    // A fast playback may skip the one frame where the vehicle is explicitly
    // in the junction. The road ids still identify the complete connector.
    if (source.roadId && target.roadId && source.roadId !== target.roadId) {
      const connector = findConnector(
        connectors,
        source.roadId,
        target.roadId,
        source.laneIndex,
        target.laneIndex,
      )
      if (connector) {
        return followPath(
          target,
          [vehiclePoint(source), ...connector.curve, vehiclePoint(target)],
          fraction,
          target.headingDegrees,
        )
      }
    }

    return {
      ...target,
      x: source.x + (target.x - source.x) * fraction,
      y: source.y + (target.y - source.y) * fraction,
      headingDegrees: source.headingDegrees
        + shortestTurn(source.headingDegrees, target.headingDegrees) * fraction,
    }
  })
}
