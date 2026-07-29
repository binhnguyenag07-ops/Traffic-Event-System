const RANDOM_BOUNDARY_POLICY_ID = 'RANDOM_BOUNDARY'
const RANDOM_BUILDING_POLICY_ID = 'RANDOM_BUILDING'
// Citywide registered-fleet counts reported by the Ho Chi Minh City Media
// Center in December 2024: roughly 8.4 million motorcycles and 980,000 cars.
// Registration is only a transparent test-spawn proxy, not a measured District
// 1 traffic-flow mix. Buses are deliberately excluded: passenger modal share
// cannot be converted into bus vehicle instances without route/headway data.
const RANDOM_MOVING_VEHICLE_TYPE_WEIGHTS = [
  { type: 'MOTORCYCLE', weight: 8_400 },
  { type: 'CAR', weight: 980 },
]

function normalizedDraw(random) {
  const value = Number(random())
  if (!Number.isFinite(value)) throw new Error('Spawn policy random source must return a finite number.')
  return Math.min(Math.max(value, 0), 1 - Number.EPSILON)
}

function chooseVehicleType(draw) {
  const totalWeight = RANDOM_MOVING_VEHICLE_TYPE_WEIGHTS
    .reduce((sum, item) => sum + item.weight, 0)
  let cursor = draw * totalWeight

  for (const item of RANDOM_MOVING_VEHICLE_TYPE_WEIGHTS) {
    if (cursor < item.weight) return item.type
    cursor -= item.weight
  }
  return RANDOM_MOVING_VEHICLE_TYPE_WEIGHTS.at(-1).type
}

const isUsableRoad = (road) => !['BLOCKED', 'CLOSED'].includes(road.status || road.roadStatus)

function reachableFrom(startIds, outgoing) {
  const visited = new Set(startIds)
  const queue = [...startIds]
  for (let index = 0; index < queue.length; index += 1) {
    for (const next of outgoing.get(queue[index]) || []) {
      if (visited.has(next)) continue
      visited.add(next)
      queue.push(next)
    }
  }
  return visited
}

function accessIds(building, networkNodes) {
  return [...new Set((building?.accessPoints || [])
    .map((access) => access.intersectionId)
    .filter((id) => networkNodes.has(id)))]
}

/**
 * Plans only building pairs that are connected in the directed road graph.
 * Residential origins are preferred when the map contains them, matching the
 * manual control's promise that a vehicle visibly leaves a home rather than
 * appearing in the middle of a street.
 */
export function createBuildingTripPlanner(buildings, roads) {
  const outgoing = new Map()
  const networkNodes = new Set()
  roads.filter(isUsableRoad).forEach((road) => {
    networkNodes.add(road.startIntersectionId)
    networkNodes.add(road.endIntersectionId)
    const destinations = outgoing.get(road.startIntersectionId) || []
    destinations.push(road.endIntersectionId)
    outgoing.set(road.startIntersectionId, destinations)
  })
  const usable = buildings
    .map((building) => ({ building, accessIds: accessIds(building, networkNodes) }))
    .filter((entry) => entry.accessIds.length)
    .sort((left, right) => left.building.id.localeCompare(right.building.id))
  const homes = usable.filter((entry) => entry.building.type === 'RESIDENTIAL')
  const origins = homes.length ? homes : usable

  return {
    buildingIds: usable.map((entry) => entry.building.id),
    originBuildingIds: origins.map((entry) => entry.building.id),
    planTrip(seed = 0) {
      if (usable.length < 2 || origins.length === 0) return null
      const firstIndex = Math.abs(seed * 17) % origins.length
      for (let offset = 0; offset < origins.length; offset += 1) {
        const origin = origins[(firstIndex + offset) % origins.length]
        const reachable = reachableFrom(origin.accessIds, outgoing)
        const destinations = usable.filter((candidate) => (
          candidate.building.id !== origin.building.id
          && candidate.accessIds.some((id) => (
            reachable.has(id) && !origin.accessIds.includes(id)
          ))
        ))
        if (!destinations.length) continue
        const nonResidential = destinations.filter(
          (candidate) => candidate.building.type !== 'RESIDENTIAL',
        )
        const pool = nonResidential.length ? nonResidential : destinations
        const destination = pool[Math.abs(seed * 31 + offset) % pool.length]
        return {
          startBuildingId: origin.building.id,
          destinationBuildingId: destination.building.id,
        }
      }
      return null
    },
  }
}

// Future demand-aware policies (for example HOME_TO_WORK) can implement this
// same plan({ boundaryPlanner, random }) contract and return the same request shape.
export const randomBoundarySpawnPolicy = Object.freeze({
  id: RANDOM_BOUNDARY_POLICY_ID,
  plan({ boundaryPlanner, random = Math.random }) {
    if (typeof boundaryPlanner?.planTrip !== 'function') {
      throw new Error('The road network is not ready for random spawning.')
    }

    const seed = Math.floor(normalizedDraw(random) * 1_000_000)
    const trip = boundaryPlanner.planTrip(seed)
    if (!trip) {
      throw new Error('No reachable boundary-to-boundary trip is available on this network.')
    }

    return {
      policyId: RANDOM_BOUNDARY_POLICY_ID,
      type: chooseVehicleType(normalizedDraw(random)),
      startIntersectionId: trip.startIntersectionId,
      destinationIntersectionId: trip.destinationIntersectionId,
    }
  },
})

export const randomBuildingSpawnPolicy = Object.freeze({
  id: RANDOM_BUILDING_POLICY_ID,
  plan({ buildingPlanner, random = Math.random }) {
    if (typeof buildingPlanner?.planTrip !== 'function') {
      throw new Error('Building access data is not ready for random spawning.')
    }
    const seed = Math.floor(normalizedDraw(random) * 1_000_000)
    const trip = buildingPlanner.planTrip(seed)
    if (!trip) {
      throw new Error('No reachable building-to-building trip is available on this network.')
    }
    return {
      policyId: RANDOM_BUILDING_POLICY_ID,
      type: chooseVehicleType(normalizedDraw(random)),
      ...trip,
    }
  },
})
