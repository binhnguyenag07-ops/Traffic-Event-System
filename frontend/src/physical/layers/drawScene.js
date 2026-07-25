// Draws a physical scene, back to front: ground, blocks, pavement, road
// surface, markings, stop lines, signals, vehicles.
//
// Everything is drawn in world metres. The container this goes into carries the
// zoom and pan, so nothing here needs to know the scale — which is also why a
// lane marking painted 0.15 m wide stays 0.15 m wide at every zoom, like real
// paint, instead of needing to be recomputed.

import { vehicleCorners, worldPoint } from '../sceneTransform'

const GROUND = 0x12151a
const PAVEMENT = 0x333942
const ROAD = 0x3a4048
const CONNECTOR = 0x363c44
const LANE_DASH = 0xc8ccd4
const ROAD_EDGE = 0x8a9099
const STOP_LINE = 0xeef0f3

// Buildings by what they are for, so a hospital does not look like a house.
const BUILDING_COLOUR = {
  RESIDENTIAL: 0x4a4740,
  COMMERCIAL: 0x4b4553,
  OFFICE: 0x3f4a58,
  SCHOOL: 0x4a5240,
  HOSPITAL: 0x5a4242,
  PARKING: 0x3b3f44,
  INDUSTRIAL: 0x494440,
  OTHER: 0x40444b,
}

const VEHICLE_COLOUR = {
  CAR: 0x53a8ff,
  MOTORCYCLE: 0xffd23f,
  BUS: 0xff8c42,
  EMERGENCY: 0xff4747,
}

const LANE_DASH_LENGTH_METRES = 3
const LANE_GAP_METRES = 4

function colourFor(table, key, fallback) {
  return table[String(key || '').toUpperCase()] ?? fallback
}

function strokePath(graphics, points, style) {
  if (!points || points.length < 2) return
  const [startX, startY] = worldPoint(points[0][0], points[0][1])
  graphics.moveTo(startX, startY)
  for (let index = 1; index < points.length; index += 1) {
    const [x, y] = worldPoint(points[index][0], points[index][1])
    graphics.lineTo(x, y)
  }
  graphics.stroke(style)
}

function fillPolygon(graphics, points, colour, alpha = 1) {
  if (!points || points.length < 3) return
  graphics.poly(points.flatMap(([x, y]) => worldPoint(x, y))).fill({ color: colour, alpha })
}

/** Dashes along a polyline, so a lane divider reads as paint rather than a line. */
function strokeDashed(graphics, points, style) {
  if (!points || points.length < 2) return
  let carry = 0
  let drawing = true
  for (let index = 1; index < points.length; index += 1) {
    const [ax, ay] = worldPoint(points[index - 1][0], points[index - 1][1])
    const [bx, by] = worldPoint(points[index][0], points[index][1])
    const segment = Math.hypot(bx - ax, by - ay)
    // Repeated shape points are common in OSM geometry and survive the lane
    // offset. A zero-length segment would advance the walk by nothing and spin
    // here forever, so it is skipped rather than stepped through.
    if (segment <= 1e-9) continue
    let travelled = 0
    while (travelled < segment) {
      const target = drawing ? LANE_DASH_LENGTH_METRES : LANE_GAP_METRES
      const step = Math.min(target - carry, segment - travelled)
      if (drawing) {
        const t0 = travelled / segment
        const t1 = (travelled + step) / segment
        graphics.moveTo(ax + (bx - ax) * t0, ay + (by - ay) * t0)
        graphics.lineTo(ax + (bx - ax) * t1, ay + (by - ay) * t1)
      }
      travelled += step
      carry += step
      if (carry >= target - 1e-9) {
        carry = 0
        drawing = !drawing
      }
    }
  }
  graphics.stroke(style)
}

/** Ground, buildings, pavement, road surface and markings. Redrawn per scene. */
export function drawStatic(graphics, scene) {
  graphics.clear()
  if (!scene) return
  const radius = scene.radiusMetres || 200

  graphics.rect(-radius * 3, -radius * 3, radius * 6, radius * 6).fill({ color: GROUND })

  scene.buildings?.forEach((building) => {
    const colour = colourFor(BUILDING_COLOUR, building.type, BUILDING_COLOUR.OTHER)
    if (building.footprint && building.footprint.length >= 3) {
      fillPolygon(graphics, building.footprint, colour)
    } else if (building.centre) {
      // A map that knows only where a building stands still gets a block.
      const [x, y] = worldPoint(building.centre[0], building.centre[1])
      graphics.rect(x - 5, y - 5, 10, 10).fill({ color: colour })
    }
  })

  scene.junctions?.forEach((junction) => fillPolygon(graphics, junction.pavement, PAVEMENT))

  scene.connectors?.forEach((connector) => strokePath(graphics, connector.curve, {
    width: 2.8, color: CONNECTOR, cap: 'round', join: 'round',
  }))

  // Surface first, then its edge lines, then the dashed divider down the middle.
  scene.lanes?.forEach((lane) => strokePath(graphics, lane.centreline, {
    width: lane.widthMetres, color: ROAD, cap: 'round', join: 'round',
  }))
  scene.lanes?.forEach((lane) => {
    strokeDashed(graphics, lane.centreline, { width: 0.14, color: LANE_DASH, alpha: 0.55 })
  })

  scene.stopLines?.forEach((stopLine) => strokePath(graphics, [stopLine.from, stopLine.to], {
    width: 0.5, color: STOP_LINE, alpha: 0.85,
  }))

  scene.stopLines?.forEach((stopLine) => strokePath(graphics, [stopLine.from, stopLine.to], {
    width: 0.12, color: ROAD_EDGE, alpha: 0.35,
  }))
}

/** Signal heads at the stop lines they control. */
export function drawSignals(graphics, scene) {
  graphics.clear()
  if (!scene?.signals) return
  scene.signals.forEach((signal) => {
    signal.approaches?.forEach((approach) => {
      const [x, y] = worldPoint(approach.head[0], approach.head[1])
      const colour = approach.green ? 0x35d07f : 0xff4d4d
      graphics.circle(x, y, 1.5).fill({ color: colour, alpha: 0.22 })
      graphics.circle(x, y, 0.85).fill({ color: colour })
    })
  })
}

/** Every vehicle at its interpolated position and heading. */
export function drawVehicles(graphics, vehicles) {
  graphics.clear()
  if (!vehicles) return
  vehicles.forEach((vehicle) => {
    const colour = colourFor(VEHICLE_COLOUR, vehicle.type, VEHICLE_COLOUR.CAR)
    const length = Math.max(vehicle.lengthMetres, 1.5)
    const width = Math.max(vehicle.widthMetres, 0.7)

    // A trail behind a moving rider makes filtering legible: you can see it
    // threading between the lanes rather than sitting in one.
    if (String(vehicle.type).toUpperCase() === 'MOTORCYCLE' && vehicle.speedMps > 1.5) {
      const rad = (vehicle.headingDegrees * Math.PI) / 180
      const back = length * 2.2
      const [cx, cy] = worldPoint(vehicle.x, vehicle.y)
      graphics.moveTo(cx, cy)
      graphics.lineTo(cx - Math.sin(rad) * back, cy + Math.cos(rad) * back)
      graphics.stroke({ width: width * 0.5, color: colour, alpha: 0.22 })
    }

    const corners = vehicleCorners(
      vehicle.x, vehicle.y, vehicle.headingDegrees, length, width,
    )
    graphics.poly(corners.flat()).fill({ color: colour })
    // A lighter nose so the direction of travel reads at a glance.
    const nose = [corners[0], corners[1],
      [(corners[1][0] + corners[2][0]) / 2, (corners[1][1] + corners[2][1]) / 2],
      [(corners[0][0] + corners[3][0]) / 2, (corners[0][1] + corners[3][1]) / 2]]
    graphics.poly(nose.flat()).fill({ color: 0xffffff, alpha: 0.22 })
  })
}
