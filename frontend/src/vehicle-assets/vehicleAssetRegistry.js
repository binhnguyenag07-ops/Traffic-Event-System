const DEFAULT_TYPE = 'DEFAULT'

const normalizeType = (type) => String(type || DEFAULT_TYPE).trim().toUpperCase()

const normalizeAsset = (type, descriptor = {}) => {
  const normalizedType = normalizeType(type)
  const markerSize = Number(descriptor.markerSize ?? 32)

  if (!Number.isFinite(markerSize) || markerSize <= 0) {
    throw new TypeError('Vehicle asset markerSize must be a positive number')
  }

  return Object.freeze({
    type: normalizedType,
    label: descriptor.label || normalizedType,
    iconKey: descriptor.iconKey || 'circle-help',
    assetUrl: descriptor.assetUrl || null,
    color: descriptor.color || '#475569',
    backgroundColor: descriptor.backgroundColor || '#f1f5f9',
    borderColor: descriptor.borderColor || descriptor.color || '#94a3b8',
    markerSize,
    markerAnchor: Object.freeze(
      descriptor.markerAnchor
        ? [...descriptor.markerAnchor]
        : [markerSize / 2, markerSize / 2]
    ),
  })
}

export const DEFAULT_VEHICLE_ASSETS = Object.freeze({
  DEFAULT: normalizeAsset(DEFAULT_TYPE, {
    label: 'Vehicle',
    iconKey: 'circle-help',
    color: '#475569',
    backgroundColor: '#f1f5f9',
    borderColor: '#94a3b8',
  }),
  CAR: normalizeAsset('CAR', {
    label: 'Car',
    iconKey: 'car-front',
    color: '#0f766e',
    backgroundColor: '#ccfbf1',
    borderColor: '#5eead4',
  }),
  BUS: normalizeAsset('BUS', {
    label: 'Bus',
    iconKey: 'bus-front',
    color: '#a16207',
    backgroundColor: '#fef3c7',
    borderColor: '#fcd34d',
    markerSize: 34,
  }),
  EMERGENCY: normalizeAsset('EMERGENCY', {
    label: 'Emergency vehicle',
    iconKey: 'ambulance',
    color: '#b91c1c',
    backgroundColor: '#fee2e2',
    borderColor: '#fca5a5',
    markerSize: 36,
  }),
})

export class VehicleAssetRegistry {
  constructor(initialAssets = DEFAULT_VEHICLE_ASSETS) {
    this.assets = new Map()
    Object.entries(initialAssets).forEach(([type, descriptor]) => {
      this.register(type, descriptor)
    })

    if (!this.assets.has(DEFAULT_TYPE)) {
      this.register(DEFAULT_TYPE, DEFAULT_VEHICLE_ASSETS.DEFAULT)
    }
  }

  register(type, descriptor) {
    const normalizedType = normalizeType(type)
    this.assets.set(normalizedType, normalizeAsset(normalizedType, descriptor))
    return this
  }

  resolve(type) {
    return this.assets.get(normalizeType(type)) || this.assets.get(DEFAULT_TYPE)
  }

  list() {
    return [...this.assets.values()]
  }
}

export const vehicleAssetRegistry = new VehicleAssetRegistry()

export const getVehicleAsset = (type) => vehicleAssetRegistry.resolve(type)
