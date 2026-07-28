// Smooths vehicle poses between two authoritative scenes.
//
// The one rule here: the backend's pose is the truth, and this file may only
// choose the *path* taken between two of them. It may never invent a pose of
// its own. An earlier version did — it anchored anything flagged `inJunction`
// to the midpoint of its turning connector — and because that only triggered on
// frames where the vehicle had not moved, a car queueing at a red light flipped
// between the stop line and the middle of the junction several times a second.
// That is what "the cars spin at four-way junctions" was.
//
// A straight x/y lerp is only correct on a straight road. Elsewhere the scene
// already carries the right path: lane centrelines along a road, and turning
// connectors across a junction. Both are sampled between the two authoritative
// endpoints, so the drawn motion follows the tarmac without ever contradicting
// the simulation.

const EPSILON = 1e-6

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

/**
 * The road a pose physically stands on.
 *
 * <p>A vehicle waiting to cross holds no road of its own, but it is not
 * nowhere: the backend parks it at the exit of the road it arrived on, still
 * pointing the way it came. Treating that as "on the approach road" is what
 * lets a queue at a red light interpolate along its lane like any other
 * traffic, instead of being special-cased into the junction.</p>
 */
function roadOf(pose) {
  if (pose.roadId) return pose.roadId
  return pose.inJunction ? pose.junctionFromRoadId || null : null
}

/** How far along {@link roadOf} the pose sits. A waiting vehicle is at its exit. */
function progressOf(pose) {
  if (pose.roadId) return Number.isFinite(pose.roadProgress) ? pose.roadProgress : null
  return pose.inJunction ? 1 : null
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

/**
 * The part of a connector that actually lies between two poses.
 *
 * <p>Splicing a whole connector between two points assumes the vehicle is about
 * to travel all of it. Once it is part-way across a junction that is false: the
 * start of the curve is behind it, so the spliced path runs backwards to the
 * junction mouth, round the full arc, and only then forwards to where the
 * vehicle actually is. Interpolating along that path swings the vehicle through
 * a reversal — the spin-then-straighten seen at every turn.</p>
 *
 * <p>Trimming to the nearest curve point at each end keeps the path monotone,
 * so progress along it is always progress along the turn.</p>
 */
function trimCurve(curve, from, to) {
  const path = cleanPath(curve)
  if (path.length < 2) return [from, to]
  const nearest = (point) => {
    let best = 0
    let bestDistance = Infinity
    path.forEach((candidate, index) => {
      const gap = distance(candidate, point)
      if (gap < bestDistance) { bestDistance = gap; best = index }
    })
    return best
  }
  const start = nearest(from)
  const end = nearest(to)
  const middle = start <= end
    ? path.slice(start, end + 1)
    : path.slice(end, start + 1).reverse()
  return [from, ...middle, to]
}

/**
 * The connector joining two roads, preferring the one built for these lanes.
 *
 * <p>A wrong-lane connector is still better than a straight line across the
 * corner, so a partial match is accepted — but only after every exact one has
 * been considered.</p>
 */
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

function findLane(lanes, roadId, preferredIndex, fallbackIndex) {
  const candidates = (lanes || []).filter((lane) => lane.roadId === roadId)
  return candidates.find((lane) => lane.laneIndex === preferredIndex)
    || candidates.find((lane) => lane.laneIndex === fallbackIndex)
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

/**
 * Motion along one road, sampled from the lane the vehicle is in.
 *
 * <p>The centreline is the stable longitudinal path; the small endpoint
 * corrections retain the vehicle's authoritative lateral position, including a
 * motorcycle part-way through a lane change, and hold a queue exactly where the
 * backend put it.</p>
 */
function lanePose(source, target, lane, sourceProgress, targetProgress, fraction) {
  const from = clamp01(sourceProgress)
  const to = clamp01(targetProgress)
  const progress = from + (to - from) * fraction
  const sourceBase = pointAtPolyline(lane.centreline, from)
  const targetBase = pointAtPolyline(lane.centreline, to)
  const point = pointAtPolyline(lane.centreline, progress)
  if (!sourceBase || !targetBase || !point) return null

  const offsetX = (source.x - sourceBase[0])
  const offsetY = (source.y - sourceBase[1])
  const correctionX = offsetX + ((target.x - targetBase[0]) - offsetX) * fraction
  const correctionY = offsetY + ((target.y - targetBase[1]) - offsetY) * fraction
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
    // Nothing to interpolate from. The authoritative pose is the only truth
    // there is, and a vehicle drawn anywhere else on its first frame arrives
    // with a jump.
    if (!source) return target

    const fromRoadId = roadOf(source)
    const toRoadId = roadOf(target)

    // Still on the same road — including a vehicle that has merely rolled up to
    // its stop line, which has not turned and must not be drawn turning.
    if (fromRoadId && fromRoadId === toRoadId) {
      const sourceProgress = progressOf(source)
      const targetProgress = progressOf(target)
      if (sourceProgress !== null && targetProgress !== null) {
        const lane = findLane(lanes, fromRoadId, target.laneIndex, source.laneIndex)
        const pose = lane
          && lanePose(source, target, lane, sourceProgress, targetProgress, fraction)
        if (pose) return pose
      }
    }

    // Crossing a junction: the two poses sit on either side of a turn, and the
    // connector between those roads is the path the vehicle took.
    if (fromRoadId && toRoadId && fromRoadId !== toRoadId) {
      const connector = findConnector(
        connectors, fromRoadId, toRoadId, source.laneIndex, target.laneIndex)
      if (connector) {
        return followPath(
          target,
          trimCurve(connector.curve, vehiclePoint(source), vehiclePoint(target)),
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
