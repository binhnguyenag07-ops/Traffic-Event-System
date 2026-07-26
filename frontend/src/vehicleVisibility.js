const OFF_NETWORK_STATUSES = new Set(['ARRIVED', 'DWELLING'])

/**
 * Only vehicles physically occupying the road network belong on either map.
 *
 * STOPPED remains visible: it represents a vehicle waiting at a signal or
 * blocked by an incident. DWELLING does not: that vehicle is inside its
 * destination building between trip legs.
 */
export function isVehicleVisibleOnMap(vehicle) {
  return !OFF_NETWORK_STATUSES.has(String(vehicle?.status || '').toUpperCase())
}
