import { describe, expect, it } from 'vitest'
import { decideStartupAction, STARTUP_ACTION } from './startupPolicy'

const EMPTY_DEMAND = {
  scheduledTrips: 0,
  spawnedTrips: 0,
  failedTrips: 0,
  pendingTrips: 0,
  failedPlanIds: [],
}

function snapshot(status = 'READY', vehicles = []) {
  return { status, vehicles }
}

describe('dashboard startup policy', () => {
  it('waits until demand metadata is known', () => {
    expect(decideStartupAction(snapshot(), null)).toBe(STARTUP_ACTION.WAIT)
  })

  it('starts a fresh demand scenario even before its first vehicle exists', () => {
    const demand = { ...EMPTY_DEMAND, scheduledTrips: 216, pendingTrips: 216 }
    expect(decideStartupAction(snapshot(), demand)).toBe(STARTUP_ACTION.START)
  })

  it('resumes a paused demand scenario with an empty current fleet', () => {
    const demand = { ...EMPTY_DEMAND, scheduledTrips: 4, pendingTrips: 4 }
    expect(decideStartupAction(snapshot('PAUSED'), demand)).toBe(STARTUP_ACTION.RESUME)
  })

  it('reconnects to an already running server simulation', () => {
    const vehicle = { id: 'warm-1', status: 'MOVING' }
    expect(decideStartupAction(snapshot('RUNNING', [vehicle]), EMPTY_DEMAND))
      .toBe(STARTUP_ACTION.CONNECT)
  })

  it('starts an existing warm-start fleet without demand', () => {
    const vehicle = { id: 'warm-1', status: 'WAITING' }
    expect(decideStartupAction(snapshot('READY', [vehicle]), EMPTY_DEMAND))
      .toBe(STARTUP_ACTION.START)
  })

  it('uses synthetic demo traffic only for a topology-only ready server', () => {
    expect(decideStartupAction(snapshot(), EMPTY_DEMAND)).toBe(STARTUP_ACTION.DEMO)
  })

  it('does not replace an empty non-ready server with demo traffic', () => {
    expect(decideStartupAction(snapshot('STOPPED'), EMPTY_DEMAND)).toBe(STARTUP_ACTION.NONE)
  })
})
