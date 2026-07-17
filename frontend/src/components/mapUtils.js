export const HCMC_CENTER = [10.774, 106.701]
export const HCMC_DISTRICT_BOUNDS = [
  [10.762, 106.687],
  [10.789, 106.718],
]

export function positionFor(intersection) {
  if (Number.isFinite(intersection?.latitude) && Number.isFinite(intersection?.longitude)) {
    return [intersection.latitude, intersection.longitude]
  }
  return [HCMC_CENTER[0] + Number(intersection?.y || 0) * 0.0035,
    HCMC_CENTER[1] + Number(intersection?.x || 0) * 0.0035]
}

export function positionsForRoad(road, start, end) {
  if (road?.geometry?.length > 1) {
    return road.geometry.map(positionFor)
  }
  return [positionFor(start), positionFor(end)]
}

export function positionAlongPath(positions, progress) {
  if (!positions?.length) return HCMC_CENTER
  if (positions.length === 1) return positions[0]

  const clampedProgress = Math.max(0, Math.min(1, Number(progress) || 0))
  const lengths = positions.slice(1).map((position, index) => {
    const previous = positions[index]
    const latitudeScale = Math.cos(((previous[0] + position[0]) / 2) * Math.PI / 180)
    return Math.hypot(
      (position[0] - previous[0]),
      (position[1] - previous[1]) * latitudeScale,
    )
  })
  const totalLength = lengths.reduce((sum, length) => sum + length, 0)
  if (totalLength === 0) return positions[0]

  let remaining = totalLength * clampedProgress
  for (let index = 0; index < lengths.length; index += 1) {
    if (remaining <= lengths[index] || index === lengths.length - 1) {
      const segmentProgress = lengths[index] === 0 ? 0 : remaining / lengths[index]
      return [
        positions[index][0] + (positions[index + 1][0] - positions[index][0]) * segmentProgress,
        positions[index][1] + (positions[index + 1][1] - positions[index][1]) * segmentProgress,
      ]
    }
    remaining -= lengths[index]
  }
  return positions.at(-1)
}

export function roadColor(road) {
  if (road.status === 'BLOCKED' || road.status === 'CLOSED') return '#b4232f'
  const pressure = Math.max(Number(road.congestionLevel || 0), Number(road.vehicleDensity || 0))
  if (pressure >= 0.75) return '#e24a34'
  if (pressure >= 0.45) return '#e89b2c'
  if (pressure >= 0.2) return '#d2b33c'
  return '#288b67'
}
