function readableLabel(key) {
  return key.replaceAll(/([A-Z])/g, ' $1').trim()
}

function entry(key, label, value) {
  return { key, label, value }
}

/**
 * Keeps selected-vehicle liveness/navigation evidence visible instead of
 * letting it fall beyond the generic object's first few scalar properties.
 */
export function inspectorEntries(selected) {
  if (!selected?.data) return []
  if (selected.kind !== 'vehicle') {
    return Object.entries(selected.data)
      .filter(([, value]) => value !== null && typeof value !== 'object')
      .slice(0, 9)
      .map(([key, value]) => entry(key, readableLabel(key), value))
  }

  const vehicle = selected.data
  const decision = vehicle.lastNavigationDecision
  return [
    entry('id', 'ID', vehicle.id),
    entry('type', 'Type', vehicle.type),
    entry('status', 'Status', vehicle.status),
    entry('occupiedRoadId', 'Road', vehicle.occupiedRoadId),
    entry('laneIndex', 'Lane', Number(vehicle.laneIndex ?? 0) + 1),
    entry('instantaneousSpeed', 'Speed (km/h)', vehicle.instantaneousSpeed),
    entry('waitCause', 'Wait cause', vehicle.waitCause),
    entry('waitCauseSeconds', 'Wait cause seconds', vehicle.waitCauseSeconds),
    entry('blockingResource', 'Blocking resource', vehicle.blockingResource),
    entry('navigationMode', 'Navigation mode', vehicle.navigationMode),
    entry('knowledgeVersion', 'Knowledge version', vehicle.knowledgeVersion),
    entry('routeVersion', 'Route version', vehicle.routeVersion),
    entry('lastRerouteReason', 'Last reroute reason', vehicle.lastRerouteReason),
    entry('lastRerouteAtSeconds', 'Last reroute at', vehicle.lastRerouteAtSeconds),
    entry('nextStrategicDecisionAtSeconds', 'Next decision at', vehicle.nextStrategicDecisionAtSeconds),
    entry('nextPlannedMovement', 'Next movement', vehicle.nextPlannedMovement),
    entry('legalUTurnsCompleted', 'Legal U-turns completed', vehicle.legalUTurnsCompleted),
    entry('decisionResult', 'Last decision result',
      decision ? (decision.accepted ? 'ACCEPTED' : 'REJECTED') : null),
  ].filter(({ value }) => value !== null && value !== undefined && value !== '')
}
