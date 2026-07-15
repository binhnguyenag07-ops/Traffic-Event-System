/**
 * Shared color/status constants for the dashboard panels and map.
 */
export const SIM_STATUS_COLOR = {
  READY: '#6366f1',
  RUNNING: '#16a34a',
  PAUSED: '#d97706',
  FINISHED: '#64748b',
}

export const VEHICLE_STATUS_COLOR = {
  MOVING: '#2563eb',
  ARRIVED: '#16a34a',
  BLOCKED: '#dc2626',
  WAITING: '#64748b',
}

export const ROAD_STATUS_COLOR = {
  OPEN: '#94a3b8',
  CONGESTED: '#d97706',
  BLOCKED: '#dc2626',
  CLOSED: '#991b1b',
}

export const EVENT_TYPES = ['CONGESTION', 'ACCIDENT', 'ROAD_CLOSURE', 'TRAFFIC_LIGHT_DELAY']

export const EVENT_TYPE_COLOR = {
  CONGESTION: '#f59e0b',
  ACCIDENT: '#dc2626',
  ROAD_CLOSURE: '#991b1b',
  TRAFFIC_LIGHT_DELAY: '#6366f1',
}

export const EVENT_STATUS_COLOR = {
  SCHEDULED: '#3b82f6',
  ACTIVE: '#d97706',
  RESOLVED: '#16a34a',
}

export const LOCATION_TYPE_COLOR = {
  NORMAL: '#64748b',
  SCHOOL: '#f59e0b',
  HOSPITAL: '#ef4444',
  COMMERCIAL: '#8b5cf6',
  RESIDENTIAL: '#22c55e',
  BAR: '#ec4899',
}

export const congestionColor = (level) => {
  if (level >= 0.7) return '#dc2626'
  if (level >= 0.4) return '#d97706'
  return '#16a34a'
}
