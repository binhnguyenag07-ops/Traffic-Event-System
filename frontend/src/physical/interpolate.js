// Smooths vehicle positions between two authoritative scenes.
//
// The backend publishes a frame at most once a simulated second; drawn straight,
// vehicles would jump a second's travel at a time. This interpolates their
// pixels between two adjacent LIVE frames so motion reads as continuous.
//
// It never invents anything but position and heading. A vehicle that is not in
// the newer frame has left and is dropped, not coasted onward; a vehicle only in
// the newer frame is drawn there from the start, not faded in. Lane, type, and
// size come from the newer frame unchanged. On a paused or scrubbed frame the
// caller passes t = 0 (or the same scene twice), so the exact frame is drawn.

/** Shortest signed turn from one compass bearing to another, in degrees. */
export function shortestTurn(fromDegrees, toDegrees) {
  const difference = ((toDegrees - fromDegrees + 540) % 360) - 180
  return difference === -180 ? 180 : difference
}

/**
 * Vehicles interpolated a fraction {@code t} of the way from {@code previous}
 * towards {@code next}. The result has one entry per vehicle in {@code next};
 * vehicles absent from {@code next} are omitted.
 */
export function interpolateVehicles(previous, next, t) {
  const clamped = Math.max(0, Math.min(1, t))
  const before = new Map((previous || []).map((vehicle) => [vehicle.id, vehicle]))
  return (next || []).map((target) => {
    const source = before.get(target.id)
    if (!source || clamped >= 1) {
      return target
    }
    return {
      ...target,
      x: source.x + (target.x - source.x) * clamped,
      y: source.y + (target.y - source.y) * clamped,
      headingDegrees: source.headingDegrees + shortestTurn(source.headingDegrees, target.headingDegrees) * clamped,
    }
  })
}
