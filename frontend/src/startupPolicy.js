export const STARTUP_ACTION = Object.freeze({
  WAIT: 'WAIT',
  NONE: 'NONE',
  CONNECT: 'CONNECT',
  START: 'START',
  RESUME: 'RESUME',
  DEMO: 'DEMO',
})

export const DEMAND_OVERVIEW_PACE = Object.freeze({
  simulationSecondsPerTick: 30,
  playbackIntervalMs: 250,
})

export function hasScheduledDemand(demandStatistics) {
  return Number(demandStatistics?.scheduledTrips) > 0
    || Number(demandStatistics?.pendingTrips) > 0
}

/**
 * Chooses how the dashboard should attach to the server-owned simulation.
 *
 * Demand is deliberately part of this decision. A freshly loaded demand
 * scenario can have no active vehicles before its first departure; treating
 * that state as an empty map would initialize the synthetic demo and erase the
 * real trip queue.
 */
export function decideStartupAction(snapshot, demandStatistics) {
  if (!snapshot || demandStatistics == null) return STARTUP_ACTION.WAIT

  const hasActiveVehicles = (snapshot.vehicles || [])
    .some((vehicle) => vehicle.status !== 'ARRIVED')
  const hasDemand = hasScheduledDemand(demandStatistics)
  const hasServerSimulation = hasActiveVehicles || hasDemand

  if (!hasServerSimulation) {
    return snapshot.status === 'READY' ? STARTUP_ACTION.DEMO : STARTUP_ACTION.NONE
  }
  if (snapshot.status === 'RUNNING') return STARTUP_ACTION.CONNECT
  if (snapshot.status === 'READY') return STARTUP_ACTION.START
  if (snapshot.status === 'PAUSED') return STARTUP_ACTION.RESUME
  return STARTUP_ACTION.NONE
}
