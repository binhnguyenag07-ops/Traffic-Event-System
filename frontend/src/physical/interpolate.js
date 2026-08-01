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
const TANGENT_SAMPLE_METRES = 0.15
const MAX_TANGENT_SAMPLE_FRACTION = 0.05
const polylineMetricsCache = new WeakMap()
const laneIndexCache = new WeakMap()
const connectorIndexCache = new WeakMap()
const connectorPathCache = new WeakMap()
const ACCESS_PHASES = new Set(['DEPARTING_ORIGIN', 'ARRIVING_DESTINATION'])

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

function authoritativeHeading(source, target, fraction, turnHintDegrees = 0) {
  // Smoothstep makes angular velocity zero at both authoritative frames. That
  // prevents a visible jerk when the next render interval begins, while still
  // guaranteeing that neither endpoint can be replaced by a geometry tangent.
  let turn = shortestTurn(source.headingDegrees, target.headingDegrees)
  // Exactly opposite headings have two equally short rotations. Choose the
  // sign of the connector's real curvature; otherwise a left U-turn can follow
  // a left-hand arc while its sprite spins right through the same 180 degrees.
  if (Math.abs(Math.abs(turn) - 180) <= EPSILON
      && Math.abs(turnHintDegrees) > EPSILON) {
    turn = Math.sign(turnHintDegrees) * 180
  }
  const eased = fraction * fraction * (3 - 2 * fraction)
  return source.headingDegrees
    + turn * eased
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

function polylineMetrics(points) {
  if (Array.isArray(points)) {
    const cached = polylineMetricsCache.get(points)
    if (cached) return cached
  }
  const path = cleanPath(points)
  const cumulative = [0]
  for (let index = 1; index < path.length; index += 1) {
    cumulative.push(cumulative[index - 1] + distance(path[index - 1], path[index]))
  }
  const metrics = {
    path,
    cumulative,
    total: cumulative[cumulative.length - 1] || 0,
  }
  if (Array.isArray(points)) polylineMetricsCache.set(points, metrics)
  return metrics
}

function pointAtDistance(metrics, along) {
  const { path, cumulative, total } = metrics
  if (path.length === 0) return null
  if (path.length === 1 || total <= EPSILON) return path[0]
  const wanted = Math.max(0, Math.min(total, along))
  for (let index = 1; index < path.length; index += 1) {
    if (wanted <= cumulative[index] || index === path.length - 1) {
      const length = cumulative[index] - cumulative[index - 1]
      const within = length <= EPSILON ? 0 : (wanted - cumulative[index - 1]) / length
      return [
        path[index - 1][0] + (path[index][0] - path[index - 1][0]) * within,
        path[index - 1][1] + (path[index][1] - path[index - 1][1]) * within,
      ]
    }
  }
  return path[path.length - 1]
}

/** Arc-length position of the nearest point on a directed lane polyline. */
function projectOntoPolyline(points, point) {
  const metrics = polylineMetrics(points)
  const { path, cumulative } = metrics
  if (path.length === 0) return null
  if (path.length === 1) return { along: 0, point: path[0] }

  let best = null
  for (let index = 1; index < path.length; index += 1) {
    const from = path[index - 1]
    const to = path[index]
    const dx = to[0] - from[0]
    const dy = to[1] - from[1]
    const squaredLength = dx * dx + dy * dy
    if (squaredLength <= EPSILON * EPSILON) continue
    const projected = Math.max(0, Math.min(1,
      ((point[0] - from[0]) * dx + (point[1] - from[1]) * dy) / squaredLength))
    const candidate = [from[0] + dx * projected, from[1] + dy * projected]
    const gap = distance(candidate, point)
    if (!best || gap < best.gap) {
      best = {
        gap,
        point: candidate,
        along: cumulative[index - 1]
          + (cumulative[index] - cumulative[index - 1]) * projected,
      }
    }
  }
  return best
}

/**
 * Ordered portion of a lane between two authoritative/connector points.
 * Returning null for reverse order prevents a malformed projection from
 * creating a loop; the caller then uses a short local bridge.
 */
function lanePathBetween(lane, from, to) {
  if (!lane?.centreline) return null
  const metrics = polylineMetrics(lane.centreline)
  const start = projectOntoPolyline(lane.centreline, from)
  const end = projectOntoPolyline(lane.centreline, to)
  if (!start || !end || end.along + EPSILON < start.along) return null

  const result = [from, start.point]
  for (let index = 1; index + 1 < metrics.path.length; index += 1) {
    if (metrics.cumulative[index] > start.along + EPSILON
        && metrics.cumulative[index] < end.along - EPSILON) {
      result.push(metrics.path[index])
    }
  }
  result.push(end.point, to)
  return cleanPath(result)
}

function laneBearingAt(lane, point, fallback) {
  if (!lane?.centreline) return fallback
  const metrics = polylineMetrics(lane.centreline)
  const projected = projectOntoPolyline(lane.centreline, point)
  if (!projected || metrics.total <= EPSILON) return fallback
  const sample = Math.min(0.25, metrics.total / 4)
  const before = pointAtDistance(metrics, projected.along - sample)
  const after = pointAtDistance(metrics, projected.along + sample)
  return before && after && distance(before, after) > EPSILON
    ? bearingBetween(before, after)
    : fallback
}

function bearingBetween(from, to) {
  return (
    Math.atan2(to[0] - from[0], to[1] - from[1]) * 180 / Math.PI
    + 360
  ) % 360
}

/**
 * Bearing of the trajectory actually being drawn at one animation fraction.
 *
 * Position and orientation must come from the same curve. Rotating directly
 * from the two endpoint headings makes a car yaw while it is still on a long
 * straight approach, even though its position correctly stays on the lane.
 * A small centred finite difference follows curved lanes, connector arcs and
 * lateral endpoint corrections alike. The caller supplies the authoritative
 * interpolation only for a genuinely stationary/degenerate trajectory.
 */
function motionBearing(positionAt, fraction, fallback, sampleFraction) {
  const sample = Math.max(
    EPSILON,
    Math.min(MAX_TANGENT_SAMPLE_FRACTION, sampleFraction),
  )
  const before = positionAt(clamp01(fraction - sample))
  const after = positionAt(clamp01(fraction + sample))
  return before && after && distance(before, after) > EPSILON
    ? bearingBetween(before, after)
    : fallback
}

function accumulatedCurveTurn(curve) {
  let previousBearing = null
  let total = 0
  for (let index = 1; index < curve.length; index += 1) {
    const bearing = bearingBetween(curve[index - 1], curve[index])
    if (previousBearing !== null) total += shortestTurn(previousBearing, bearing)
    previousBearing = bearing
  }
  return total
}

/**
 * Whether a backend connector is safe enough to use as an animation path.
 *
 * The current backend contract has no mid-junction progress. A transition is
 * therefore always source -> complete connector -> target; guessing which part
 * of a connector was crossed from Euclidean proximity is invalid. In particular,
 * a target on a very short outgoing road can be nearer the connector's start
 * than its end, which used to collapse a real turn into a chord.
 */
function canonicalConnectorPath(connector, source, target, fromLane, toLane) {
  const curve = cleanPath(connector?.curve)
  if (curve.length < 2) return null

  const bearings = []
  for (let index = 1; index < curve.length; index += 1) {
    bearings.push(bearingBetween(curve[index - 1], curve[index]))
  }
  if (bearings.some((bearing, index) => (
    index > 0 && Math.abs(shortestTurn(bearings[index - 1], bearing)) > 60
  ))) {
    return null
  }

  // Reject a curve that begins or ends with a cusp relative to the two
  // lane tangents. Comparing with the target pose itself is insufficient on a
  // short curved exit, where the vehicle may already be past the entry tangent.
  const expectedStart = laneBearingAt(fromLane, curve[0], source.headingDegrees)
  const expectedEnd = laneBearingAt(toLane, curve[curve.length - 1], target.headingDegrees)
  if (Math.abs(shortestTurn(expectedStart, bearings[0])) > 60) return null
  if (Math.abs(shortestTurn(bearings[bearings.length - 1], expectedEnd)) > 60) {
    return null
  }

  const sourcePoint = vehiclePoint(source)
  const targetPoint = vehiclePoint(target)
  const approach = lanePathBetween(fromLane, sourcePoint, curve[0])
    || [sourcePoint, curve[0]]
  const exit = lanePathBetween(toLane, curve[curve.length - 1], targetPoint)
    || [curve[curve.length - 1], targetPoint]
  return {
    points: cleanPath([...approach, ...curve, ...exit]),
    turnHintDegrees: accumulatedCurveTurn(curve),
  }
}

/**
 * Reuses the canonical path throughout one rendered source→target interval.
 *
 * PhysicalScene calls interpolation at 60 FPS while both authoritative vehicle
 * objects stay unchanged. Connector validation/projection is substantially
 * more expensive than sampling a cached polyline, and geometry objects are
 * immutable for the lifetime of a scene. All three cache levels are weak so an
 * old timeline can still be collected in full.
 */
function cachedConnectorPath(connector, source, target, fromLane, toLane) {
  let bySource = connectorPathCache.get(connector)
  if (!bySource) {
    bySource = new WeakMap()
    connectorPathCache.set(connector, bySource)
  }
  let byTarget = bySource.get(source)
  if (!byTarget) {
    byTarget = new WeakMap()
    bySource.set(source, byTarget)
  }
  if (!byTarget.has(target)) {
    byTarget.set(
      target,
      canonicalConnectorPath(connector, source, target, fromLane, toLane),
    )
  }
  return byTarget.get(target)
}

/**
 * The connector joining two roads, preferring the one built for these lanes.
 *
 * Only an exact lane pairing is valid. Pulling a vehicle onto another lane's
 * connector creates a lateral jump and gives it that lane's tangent.
 */
function findConnector(connectors, fromRoadId, toRoadId, fromLane, toLane) {
  if (!fromRoadId || !toRoadId) return null
  const list = connectors || []
  let index = connectorIndexCache.get(list)
  if (!index) {
    index = new Map()
    list.forEach((connector) => {
      index.set(
        `${connector.fromRoadId}\u0000${connector.fromLane}\u0000`
          + `${connector.toRoadId}\u0000${connector.toLane}`,
        connector,
      )
    })
    connectorIndexCache.set(list, index)
  }
  return index.get(`${fromRoadId}\u0000${fromLane}\u0000${toRoadId}\u0000${toLane}`) || null
}

function findLane(lanes, roadId, preferredIndex, fallbackIndex) {
  const list = lanes || []
  let index = laneIndexCache.get(list)
  if (!index) {
    index = new Map()
    list.forEach((lane) => index.set(`${lane.roadId}\u0000${lane.laneIndex}`, lane))
    laneIndexCache.set(list, index)
  }
  return index.get(`${roadId}\u0000${preferredIndex}`)
    || index.get(`${roadId}\u0000${fallbackIndex}`)
    || null
}

function followPath(source, target, path, fraction, turnHintDegrees = 0) {
  const metrics = polylineMetrics(path)
  const positionAt = (at) => pointAtDistance(metrics, metrics.total * clamp01(at))
  const point = positionAt(fraction)
  if (!point) return target
  return {
    ...target,
    x: point[0],
    y: point[1],
    headingDegrees: motionBearing(
      positionAt,
      fraction,
      authoritativeHeading(source, target, fraction, turnHintDegrees),
      TANGENT_SAMPLE_METRES / Math.max(metrics.total, EPSILON),
    ),
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
  const metrics = polylineMetrics(lane.centreline)
  const sourceBase = pointAtDistance(metrics, metrics.total * from)
  const targetBase = pointAtDistance(metrics, metrics.total * to)
  if (!sourceBase || !targetBase) return null

  const offsetX = (source.x - sourceBase[0])
  const offsetY = (source.y - sourceBase[1])
  const targetOffsetX = target.x - targetBase[0]
  const targetOffsetY = target.y - targetBase[1]
  const positionAt = (at) => {
    const progress = from + (to - from) * at
    const point = pointAtDistance(metrics, metrics.total * progress)
    if (!point) return null
    return [
      point[0] + offsetX + (targetOffsetX - offsetX) * at,
      point[1] + offsetY + (targetOffsetY - offsetY) * at,
    ]
  }
  const point = positionAt(fraction)
  if (!point) return null
  const lanePoint = pointAtDistance(
    metrics,
    metrics.total * (from + (to - from) * fraction),
  )
  return {
    ...target,
    x: point[0],
    y: point[1],
    // Lateral interpolation positions a lane-changing vehicle correctly, but
    // it is not the direction of travel. Derive orientation only from the
    // directed lane tangent so a stopped/slow vehicle can never turn broadside
    // merely because two authoritative frames have different offsets.
    headingDegrees: laneBearingAt(
      lane,
      lanePoint,
      authoritativeHeading(source, target, fraction),
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

  const drawn = (next || []).map((target) => {
    const source = before.get(target.id)
    // Nothing to interpolate from. The authoritative pose is the only truth
    // there is, and a vehicle drawn anywhere else on its first frame arrives
    // with a jump.
    if (!source) return target

    // Interpolation is allowed to choose only intermediate poses. These two
    // guards make the backend endpoint contract explicit and regression-testable.
    if (fraction <= 0) return source
    if (fraction >= 1) return target

    const fromRoadId = roadOf(source)
    const toRoadId = roadOf(target)

    // Building access is now an authoritative backend phase sampled at every
    // physics sub-step. Its x/y endpoints already lie on the building↔access
    // segment, so the safe interpolation is the local chord between those two
    // samples. Reconstructing a whole building/access path here would send a
    // midway vehicle backwards to the building on every poll.
    if (ACCESS_PHASES.has(source.journeyPhase)
        && ACCESS_PHASES.has(target.journeyPhase)) {
      return {
        ...target,
        x: source.x + (target.x - source.x) * fraction,
        y: source.y + (target.y - source.y) * fraction,
        headingDegrees: authoritativeHeading(source, target, fraction),
      }
    }

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
        const fromLane = findLane(
          lanes, fromRoadId, source.laneIndex, source.laneIndex)
        const toLane = findLane(
          lanes, toRoadId, target.laneIndex, target.laneIndex)
        const connectorPath = cachedConnectorPath(
          connector, source, target, fromLane, toLane)
        if (connectorPath) {
          return followPath(
            source,
            target,
            connectorPath.points,
            fraction,
            connectorPath.turnHintDegrees,
          )
        }
      }
    }

    return {
      ...target,
      x: source.x + (target.x - source.x) * fraction,
      y: source.y + (target.y - source.y) * fraction,
      headingDegrees: authoritativeHeading(source, target, fraction),
    }
  })

  // The backend keeps a vehicle in ARRIVING_DESTINATION until its authoritative
  // pose reaches the building centre. Once it leaves the next scene it is
  // dwelling/complete, so there is no valid extra "ghost" trajectory to invent.
  return drawn
}
