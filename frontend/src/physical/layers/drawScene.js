// Draws a physical scene onto PixiJS Graphics, in a stable back-to-front order:
// junction pavement, road surface, lane markings and stop lines, then vehicles.
// The geometry is static between frames and redrawn only when a new scene
// arrives; the vehicles are redrawn every animation frame from interpolated
// positions.

import { vehicleCorners } from '../sceneTransform'

const SURFACE = 0x2b2f38
const PAVEMENT = 0x232730
const LANE_EDGE = 0x3c4250
const CONNECTOR = 0x2f3540
const STOP_LINE = 0xe8eaed

const VEHICLE_COLOUR = {
  CAR: 0x4da3ff,
  MOTORCYCLE: 0xffd23f,
  BUS: 0xff8c42,
  EMERGENCY: 0xff3b3b,
}

function colourFor(type) {
  return VEHICLE_COLOUR[String(type || 'CAR').toUpperCase()] ?? VEHICLE_COLOUR.CAR
}

function strokePolyline(graphics, points, transform, style) {
  if (!points || points.length < 2) return
  const [startX, startY] = transform.toScreen(points[0][0], points[0][1])
  graphics.moveTo(startX, startY)
  for (let index = 1; index < points.length; index += 1) {
    const [x, y] = transform.toScreen(points[index][0], points[index][1])
    graphics.lineTo(x, y)
  }
  graphics.stroke(style)
}

/** Redraws the static geometry: pavement, lanes, connectors, stop lines. */
export function drawStatic(graphics, scene, transform) {
  graphics.clear()
  if (!scene) return

  scene.junctions?.forEach((junction) => {
    if (!junction.pavement || junction.pavement.length < 3) return
    const flat = junction.pavement.flatMap(([x, y]) => transform.toScreen(x, y))
    graphics.poly(flat).fill({ color: PAVEMENT })
  })

  scene.connectors?.forEach((connector) => {
    strokePolyline(graphics, connector.curve, transform, {
      width: Math.max(1, transform.lengthToScreen(2.6)), color: CONNECTOR, cap: 'round', join: 'round',
    })
  })

  scene.lanes?.forEach((lane) => {
    strokePolyline(graphics, lane.centreline, transform, {
      width: Math.max(1, transform.lengthToScreen(lane.widthMetres)),
      color: SURFACE, cap: 'round', join: 'round',
    })
    // A faint hairline down the lane centre reads as a lane marking.
    strokePolyline(graphics, lane.centreline, transform, {
      width: 1, color: LANE_EDGE, alpha: 0.5,
    })
  })

  scene.stopLines?.forEach((stopLine) => {
    strokePolyline(graphics, [stopLine.from, stopLine.to], transform, {
      width: Math.max(1, transform.lengthToScreen(0.5)), color: STOP_LINE, alpha: 0.7,
    })
  })
}

/** Redraws every vehicle at its interpolated position and heading. */
export function drawVehicles(graphics, vehicles, transform) {
  graphics.clear()
  if (!vehicles) return
  vehicles.forEach((vehicle) => {
    const colour = colourFor(vehicle.type)
    // A short trail behind a moving motorcycle makes filtering legible: you can
    // see it threading between lanes rather than teleporting.
    if (String(vehicle.type).toUpperCase() === 'MOTORCYCLE' && vehicle.speedMps > 1.5) {
      const rad = (vehicle.headingDegrees * Math.PI) / 180
      const back = transform.lengthToScreen(vehicle.lengthMetres * 2.5)
      const [cx, cy] = transform.toScreen(vehicle.x, vehicle.y)
      graphics.moveTo(cx, cy)
      graphics.lineTo(cx - Math.sin(rad) * back, cy + Math.cos(rad) * back)
      graphics.stroke({ width: Math.max(1, transform.lengthToScreen(0.4)), color: colour, alpha: 0.25 })
    }
    const corners = vehicleCorners(
      transform, vehicle.x, vehicle.y, vehicle.headingDegrees,
      Math.max(vehicle.lengthMetres, 1.5), Math.max(vehicle.widthMetres, 0.7),
    )
    graphics.poly(corners.flatMap((corner) => corner)).fill({ color: colour })
  })
}
