import { Ambulance, BusFront, CarFront, CircleHelp } from 'lucide-react'
import { getVehicleAsset } from './vehicleAssetRegistry'

const ICON_COMPONENTS = Object.freeze({
  ambulance: Ambulance,
  'bus-front': BusFront,
  'car-front': CarFront,
  'circle-help': CircleHelp,
})

export default function VehicleAsset({
  type,
  size,
  decorative = false,
  className = '',
}) {
  const asset = getVehicleAsset(type)
  const displaySize = Number(size || asset.markerSize)
  const Icon = ICON_COMPONENTS[asset.iconKey] || CircleHelp
  const accessibilityProps = decorative
    ? { 'aria-hidden': true }
    : { role: 'img', 'aria-label': asset.label, title: asset.label }

  return (
    <span
      className={`vehicle-asset ${className}`.trim()}
      style={{
        '--vehicle-asset-size': `${displaySize}px`,
        '--vehicle-asset-color': asset.color,
        '--vehicle-asset-background': asset.backgroundColor,
        '--vehicle-asset-border': asset.borderColor,
      }}
      {...accessibilityProps}
    >
      {asset.assetUrl ? (
        <img src={asset.assetUrl} alt="" draggable="false" />
      ) : (
        <Icon size={Math.round(displaySize * 0.58)} strokeWidth={2.1} />
      )}
    </span>
  )
}
