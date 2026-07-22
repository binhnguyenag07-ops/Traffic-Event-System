const vehicle = (id, type, startIntersectionId, destinationIntersectionId) => ({
  id,
  type,
  startIntersectionId,
  destinationIntersectionId,
})

const isUsableRoad = (road) => !['BLOCKED', 'CLOSED'].includes(road.status || road.roadStatus)

function coordinateFor(intersection) {
  const latitude = Number(intersection?.latitude ?? intersection?.y)
  const longitude = Number(intersection?.longitude ?? intersection?.x)
  return Number.isFinite(latitude) && Number.isFinite(longitude) ? [latitude, longitude] : null
}

function sideFor([latitude, longitude], bounds) {
  const distances = [
    ['NORTH', bounds.north - latitude],
    ['SOUTH', latitude - bounds.south],
    ['EAST', bounds.east - longitude],
    ['WEST', longitude - bounds.west],
  ]
  return distances.reduce((closest, candidate) => candidate[1] < closest[1] ? candidate : closest)[0]
}

function reachableFrom(startId, outgoing) {
  const hops = new Map([[startId, 0]])
  const queue = [startId]
  for (let index = 0; index < queue.length; index += 1) {
    const current = queue[index]
    for (const next of outgoing.get(current) || []) {
      if (hops.has(next)) continue
      hops.set(next, hops.get(current) + 1)
      queue.push(next)
    }
  }
  return hops
}

export function createBoundaryTripPlanner(intersections, roads, boundaryRatio = 0.08) {
  const located = intersections
    .map((intersection) => ({ ...intersection, coordinate: coordinateFor(intersection) }))
    .filter((intersection) => intersection.coordinate)
  if (located.length < 2) return { boundaryIds: [], entryIds: [], exitIds: [], planTrip: () => null }

  const latitudes = located.map((intersection) => intersection.coordinate[0])
  const longitudes = located.map((intersection) => intersection.coordinate[1])
  const bounds = {
    south: Math.min(...latitudes),
    north: Math.max(...latitudes),
    west: Math.min(...longitudes),
    east: Math.max(...longitudes),
  }
  const latitudeMargin = Math.max((bounds.north - bounds.south) * boundaryRatio, Number.EPSILON)
  const longitudeMargin = Math.max((bounds.east - bounds.west) * boundaryRatio, Number.EPSILON)
  const intersectionById = new Map(located.map((intersection) => [intersection.id, intersection]))
  const outgoing = new Map()
  const incoming = new Set()

  roads.filter(isUsableRoad).forEach((road) => {
    if (!intersectionById.has(road.startIntersectionId) || !intersectionById.has(road.endIntersectionId)) return
    const choices = outgoing.get(road.startIntersectionId) || []
    choices.push(road.endIntersectionId)
    outgoing.set(road.startIntersectionId, choices)
    incoming.add(road.endIntersectionId)
  })

  const boundary = located.filter((intersection) => {
    const [latitude, longitude] = intersection.coordinate
    return latitude <= bounds.south + latitudeMargin
      || latitude >= bounds.north - latitudeMargin
      || longitude <= bounds.west + longitudeMargin
      || longitude >= bounds.east - longitudeMargin
  })
  const usableBoundary = boundary.length >= 2 ? boundary : located
  const entryIds = usableBoundary.filter((intersection) => outgoing.has(intersection.id)).map((item) => item.id)
  const exitIds = usableBoundary.filter((intersection) => incoming.has(intersection.id)).map((item) => item.id)
  const oppositeSide = { NORTH: 'SOUTH', SOUTH: 'NORTH', EAST: 'WEST', WEST: 'EAST' }

  const tripFrom = (startId, seed) => {
    const start = intersectionById.get(startId)
    if (!start) return null
    const reachable = reachableFrom(startId, outgoing)
    const startSide = sideFor(start.coordinate, bounds)
    const candidates = exitIds
      .filter((id) => id !== startId && reachable.has(id))
      .map((id) => {
        const destination = intersectionById.get(id)
        const destinationSide = sideFor(destination.coordinate, bounds)
        const distance = Math.hypot(
          destination.coordinate[0] - start.coordinate[0],
          destination.coordinate[1] - start.coordinate[1],
        )
        return { id, destinationSide, distance, hops: reachable.get(id) }
      })
    if (!candidates.length) return null

    const opposite = candidates.filter((candidate) => candidate.destinationSide === oppositeSide[startSide])
    const different = candidates.filter((candidate) => candidate.destinationSide !== startSide)
    const preferred = opposite.length ? opposite : different.length ? different : candidates
    preferred.sort((left, right) => (
      right.distance - left.distance || right.hops - left.hops || left.id.localeCompare(right.id)
    ))
    const variedPool = preferred.slice(0, Math.min(8, preferred.length))
    const destination = variedPool[Math.abs(seed) % variedPool.length]
    return {
      startIntersectionId: startId,
      destinationIntersectionId: destination.id,
      startSide,
      destinationSide: destination.destinationSide,
    }
  }

  return {
    boundaryIds: usableBoundary.map((intersection) => intersection.id),
    entryIds,
    exitIds,
    planTrip(seed = 0) {
      if (!entryIds.length || !exitIds.length) return null
      const firstIndex = Math.abs(seed * 11) % entryIds.length
      for (let offset = 0; offset < entryIds.length; offset += 1) {
        const startId = entryIds[(firstIndex + offset) % entryIds.length]
        const trip = tripFrom(startId, seed + offset)
        if (trip) return trip
      }
      return null
    },
  }
}

export function replacementDelayMs(seed) {
  return 1000 + (Math.abs(seed * 7919) % 2001)
}

export function buildDemoTraffic(intersections, roads) {
  const planner = createBoundaryTripPlanner(intersections, roads)
  const fallbackIds = intersections.map((intersection) => intersection.id)
  if (fallbackIds.length < 2) return { vehicles: [], events: [] }

  const tripFor = (index) => planner.planTrip(index) || {
    startIntersectionId: fallbackIds[index % fallbackIds.length],
    destinationIntersectionId: fallbackIds[(index + 1) % fallbackIds.length],
  }
  const vehicleTypes = [
    ...Array.from({ length: 10 }, () => 'CAR'),
    ...Array.from({ length: 8 }, () => 'MOTORCYCLE'),
    ...Array.from({ length: 4 }, () => 'BUS'),
    ...Array.from({ length: 2 }, () => 'EMERGENCY'),
  ]
  const typeCounts = { CAR: 0, MOTORCYCLE: 0, BUS: 0, EMERGENCY: 0 }
  const prefixes = { CAR: 'CAR', MOTORCYCLE: 'MOTO', BUS: 'BUS', EMERGENCY: 'EMS' }
  const vehicles = vehicleTypes.map((type, index) => {
    typeCounts[type] += 1
    const trip = tripFor(index)
    return vehicle(
      `${prefixes[type]}-${String(typeCounts[type]).padStart(2, '0')}`,
      type,
      trip.startIntersectionId,
      trip.destinationIntersectionId,
    )
  })

  const usableRoads = roads.filter(isUsableRoad)
  const accidentRoad = usableRoads[0]
  const congestionRoad = usableRoads[Math.floor(usableRoads.length * 0.65)]
  const events = []

  if (accidentRoad) {
    events.push({
      id: 'LIVE-ACCIDENT',
      type: 'ACCIDENT',
      affectedRoadId: accidentRoad.id,
      startTimeSeconds: 0,
      durationSeconds: 120,
      positionAlongRoad: 0.45,
      blockedLaneIndexes: [0],
      value: 0.65,
    })
  }
  if (congestionRoad && congestionRoad.id !== accidentRoad?.id) {
    events.push({
      id: 'LIVE-CONGESTION',
      type: 'CONGESTION',
      affectedRoadId: congestionRoad.id,
      startTimeSeconds: 0,
      durationSeconds: 120,
      value: 0.25,
    })
  }

  return { vehicles, events }
}
