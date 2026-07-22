const RANDOM_BOUNDARY_POLICY_ID = 'RANDOM_BOUNDARY'
const RANDOM_MOVING_VEHICLE_TYPE_WEIGHTS = [
  { type: 'CAR', weight: 60 },
  { type: 'MOTORCYCLE', weight: 40 },
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
