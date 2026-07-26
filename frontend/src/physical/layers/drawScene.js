// Draws a physical scene, back to front: ground, blocks, pavement, road
// surface, markings, stop lines, signals, vehicles.
//
// Everything is drawn in world metres. The container this goes into carries the
// zoom and pan, so nothing here needs to know the scale — which is also why a
// lane marking painted 0.15 m wide stays 0.15 m wide at every zoom, like real
// paint, instead of needing to be recomputed.

import { worldPoint } from '../sceneTransform'

// A daytime map palette. The first attempt kept everything in the bottom
// quarter of the luminance range -- ground 21, road 63, buildings 68 out of 255
// -- which is a set of near-identical darks, and reads as a black screen. Land,
// blocks and carriageway now sit far enough apart to tell apart at a glance.
const GROUND = 0xe7e3da
const BUILDING_EDGE = 0x9aa0aa
const ROAD_CASING = 0xa8aeb8
const ROAD = 0xfbfbfc
const PAVEMENT = 0xf2f3f5
const CONNECTOR = 0xf5f6f8
const LANE_DASH = 0xb9bec7
const STOP_LINE = 0x4a5059
const TRAIL = 0xe0a415

// What the ground is, where it is not road or building. NORMAL has no entry: it
// is the background everything else is drawn on, which is exactly what ordinary
// urban fabric with nothing mapped on it should look like.
const LAND_COLOUR = {
  PARK: 0xc7dfb4,
  WATER: 0xa8cfe0,
  GRASS: 0xd2e3c2,
  FOREST: 0xb5d3a4,
  COMMERCIAL: 0xe6dcd0,
  RESIDENTIAL: 0xe4ded2,
  INDUSTRIAL: 0xdedad2,
  CONSTRUCTION: 0xded6c6,
  RELIGIOUS: 0xe2dcd4,
  RECREATION: 0xd4e3c8,
  OTHER: 0xe2ded6,
}

// Buildings by what they are for, so a hospital does not look like a house.
const BUILDING_COLOUR = {
  RESIDENTIAL: 0xd8cfc0,
  COMMERCIAL: 0xd6c8d8,
  OFFICE: 0xc6d2e2,
  SCHOOL: 0xcadcc4,
  HOSPITAL: 0xecc8c8,
  PARKING: 0xd9dde2,
  INDUSTRIAL: 0xd3cec6,
  OTHER: 0xd5d7db,
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

function fillPolygon(graphics, points, colour, alpha = 1, fill = null) {
  if (!points || points.length < 3) return
  const style = fill ? { ...fill, alpha } : { color: colour, alpha }
  graphics.poly(points.flatMap(([x, y]) => worldPoint(x, y))).fill(style)
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

/**
 * Ground, buildings, pavement, road surface and markings. Redrawn per scene.
 *
 * `assets` is optional and partial. Any surface with a texture loaded is filled
 * with it; the rest keep their flat colour, so the scene renders whether the
 * assets folder is empty, half full, or complete.
 */
export function drawStatic(graphics, scene, assets = {}) {
  graphics.clear()
  if (!scene) return
  const radius = scene.radiusMetres || 200
  const surfaces = assets.surfaces || {}
  const roofs = assets.buildings || {}
  const land = assets.land || {}

  // The base is NORMAL land: ordinary fabric, and what fills everywhere nothing
  // else is mapped.
  graphics.rect(-radius * 3, -radius * 3, radius * 6, radius * 6)
    .fill(land.NORMAL ? { ...land.NORMAL } : { color: GROUND })

  // Ground cover first: it sits under everything, and a building standing in a
  // park should be drawn on the park rather than beside it.
  scene.landCovers?.forEach((cover) => {
    const kind = String(cover.type || 'OTHER').toUpperCase()
    if (kind === 'NORMAL') return
    fillPolygon(graphics, cover.polygon, colourFor(LAND_COLOUR, kind, LAND_COLOUR.OTHER),
      1, land[kind])
  })

  scene.buildings?.forEach((building) => {
    const colour = colourFor(BUILDING_COLOUR, building.type, BUILDING_COLOUR.OTHER)
    const roof = roofs[String(building.type || '').toUpperCase()]
    if (building.footprint && building.footprint.length >= 3) {
      fillPolygon(graphics, building.footprint, colour, 1, roof)
      strokePath(graphics, [...building.footprint, building.footprint[0]],
        { width: 0.4, color: BUILDING_EDGE, alpha: 0.7 })
    } else if (building.centre) {
      // A map that knows only where a building stands still gets a block.
      const [x, y] = worldPoint(building.centre[0], building.centre[1])
      graphics.rect(x - 5, y - 5, 10, 10).fill({ color: colour })
    }
  })

  // Every carriageway gets its casing before any surface is laid, so a road
  // running beside another is not outlined on top of its neighbour's tarmac.
  scene.lanes?.forEach((lane) => strokePath(graphics, lane.centreline, {
    width: lane.widthMetres + 0.7, color: ROAD_CASING, cap: 'round', join: 'round',
  }))
  scene.junctions?.forEach((junction) => fillPolygon(graphics, junction.pavement, ROAD_CASING))

  const roadStyle = surfaces.ROAD
    ? { ...surfaces.ROAD, cap: 'round', join: 'round' }
    : { color: ROAD, cap: 'round', join: 'round' }
  scene.lanes?.forEach((lane) => strokePath(graphics, lane.centreline, {
    ...roadStyle, width: lane.widthMetres,
  }))
  scene.junctions?.forEach((junction) =>
    fillPolygon(graphics, junction.pavement, PAVEMENT, 0.92, surfaces.JUNCTION))
  scene.connectors?.forEach((connector) => strokePath(graphics, connector.curve, {
    width: 2.8, color: CONNECTOR, cap: 'round', join: 'round',
  }))

  scene.lanes?.forEach((lane) => {
    strokeDashed(graphics, lane.centreline, { width: 0.16, color: LANE_DASH, alpha: 0.9 })
  })

  scene.stopLines?.forEach((stopLine) => strokePath(graphics, [stopLine.from, stopLine.to], {
    width: 0.5, color: STOP_LINE, alpha: 0.8,
  }))
}

/**
 * Signal lamps, as a small glow beside the kerb.
 *
 * They were a 1.7 m disc across the middle of the carriageway, which read as
 * something dumped in the road rather than a light controlling it. A real lamp
 * is a few centimetres; this is a little larger so it stays visible when zoomed
 * out, and no bigger.
 */
export function drawSignals(graphics, scene) {
  graphics.clear()
  if (!scene?.signals) return
  scene.signals.forEach((signal) => {
    signal.approaches?.forEach((approach) => {
      const [x, y] = worldPoint(approach.head[0], approach.head[1])
      const colour = approach.green ? 0x2fbf62 : 0xe03b3b
      graphics.circle(x, y, 0.85).fill({ color: colour, alpha: 0.18 })
      graphics.circle(x, y, 0.32).fill({ color: colour })
    })
  })
}

/**
 * Trails behind moving motorcycles.
 *
 * The bodies are sprites; this is the one thing they cannot show. A short tail
 * makes filtering legible, so a rider threading between two lanes reads as a
 * manoeuvre rather than a vehicle in the wrong place.
 */
export function drawTrails(graphics, vehicles) {
  graphics.clear()
  if (!vehicles) return
  vehicles.forEach((vehicle) => {
    if (String(vehicle.type).toUpperCase() !== 'MOTORCYCLE') return
    if (!(vehicle.speedMps > 1.5)) return
    const rad = (vehicle.headingDegrees * Math.PI) / 180
    const back = Math.max(vehicle.lengthMetres, 1.5) * 2.2
    const [cx, cy] = worldPoint(vehicle.x, vehicle.y)
    graphics.moveTo(cx, cy)
    graphics.lineTo(cx - Math.sin(rad) * back, cy + Math.cos(rad) * back)
    graphics.stroke({ width: 0.35, color: TRAIL, alpha: 0.35 })
  })
}
