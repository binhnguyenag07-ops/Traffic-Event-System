import assert from 'node:assert/strict'
import test from 'node:test'
import {
  DEFAULT_VEHICLE_ASSETS,
  VehicleAssetRegistry,
  getVehicleAsset,
} from './vehicleAssetRegistry.js'

test('resolves built-in assets without a map renderer', () => {
  assert.equal(getVehicleAsset('car').iconKey, 'car-front')
  assert.equal(getVehicleAsset('BUS').markerSize, 34)
  assert.equal(getVehicleAsset('emergency').iconKey, 'ambulance')
})

test('falls back safely for unknown vehicle types', () => {
  const asset = getVehicleAsset('motorcycle')

  assert.equal(asset.type, 'DEFAULT')
  assert.equal(asset.iconKey, 'circle-help')
})

test('allows a renderer-independent custom image asset', () => {
  const registry = new VehicleAssetRegistry(DEFAULT_VEHICLE_ASSETS)
  registry.register('CAR', {
    label: 'Custom car',
    assetUrl: '/assets/vehicles/custom-car.png',
    color: '#111827',
    markerSize: 40,
    markerAnchor: [20, 32],
  })

  const asset = registry.resolve('car')
  assert.equal(asset.assetUrl, '/assets/vehicles/custom-car.png')
  assert.equal(asset.markerSize, 40)
  assert.deepEqual(asset.markerAnchor, [20, 32])
})

test('rejects invalid marker dimensions', () => {
  const registry = new VehicleAssetRegistry()

  assert.throws(
    () => registry.register('CAR', { markerSize: 0 }),
    /markerSize must be a positive number/
  )
})
