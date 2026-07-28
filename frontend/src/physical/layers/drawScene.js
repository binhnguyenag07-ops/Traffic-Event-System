// Draws a physical scene, back to front: ground, blocks, pavement, road
// surface, markings, signals, vehicles.
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
const GROUND = 0xc9c9c5
const BUILDING_EDGE = 0x606a70
const ROAD_CASING = 0x242a30
const ROAD = 0x4b5158
const PAVEMENT = 0x525a62
const LANE_DASH = 0xe5e8e9
const TRAIL = 0xe0a415

// Photographic material should add grain, not replace the map's visual
// hierarchy. The semantic palette stays dominant at a glance.
const NORMAL_TEXTURE_ALPHA = 0.12
const LAND_TEXTURE_ALPHA = 0.2
const ROOF_TEXTURE_ALPHA = 0.48
const ROAD_TEXTURE_ALPHA = 0.32

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

/**
 * A fillable strip around a lane centreline.
 *
 * Pixi accepts a texture in a stroke style, but combining that texture with a
 * world-scale matrix makes some multi-segment strokes triangulate across their
 * bends. The result is a giant asphalt wedge instead of a road. A corridor
 * polygon keeps the material clipped to the lane's real width.
 */
function laneCorridor(points, width) {
  if (!points || points.length < 2 || width <= 0) return []

  const unique = points.filter((point, index) => {
    if (index === 0) return true
    const previous = points[index - 1]
    return Math.hypot(point[0] - previous[0], point[1] - previous[1]) > 1e-9
  })
  if (unique.length < 2) return []

  const halfWidth = width / 2
  const left = []
  const right = []
  for (let index = 0; index < unique.length; index += 1) {
    const point = unique[index]
    const previous = unique[Math.max(0, index - 1)]
    const next = unique[Math.min(unique.length - 1, index + 1)]
    const dx = next[0] - previous[0]
    const dy = next[1] - previous[1]
    const length = Math.hypot(dx, dy)
    if (length <= 1e-9) continue
    const nx = -dy / length
    const ny = dx / length
    left.push([point[0] + nx * halfWidth, point[1] + ny * halfWidth])
    right.push([point[0] - nx * halfWidth, point[1] - ny * halfWidth])
  }
  return [...left, ...right.reverse()]
}

/**
 * The painted dividers of a road: one line between each pair of adjacent lanes.
 *
 * <p>Markings separate lanes, so they belong on the boundaries — not down the
 * middle of each lane, which is exactly where the traffic drives. Dashing every
 * centreline put a white line under every vehicle and made correctly placed
 * traffic look like it was straddling the paint.</p>
 *
 * <p>The boundary is taken as the midline between two lane centrelines rather
 * than by offsetting one of them sideways. Both come from the same road shape
 * offset by different amounts, so they run point for point, and a midpoint
 * needs no normals and cannot pick the wrong side.</p>
 */
export function laneDividers(lanes) {
  const byRoad = new Map()
  for (const lane of lanes || []) {
    if (!byRoad.has(lane.roadId)) byRoad.set(lane.roadId, [])
    byRoad.get(lane.roadId).push(lane)
  }
  const dividers = []
  for (const group of byRoad.values()) {
    group.sort((a, b) => a.laneIndex - b.laneIndex)
    for (let index = 0; index + 1 < group.length; index += 1) {
      const left = group[index].centreline
      const right = group[index + 1].centreline
      if (!left || !right || left.length !== right.length || left.length < 2) continue
      dividers.push(left.map((point, at) => [
        (point[0] + right[at][0]) / 2,
        (point[1] + right[at][1]) / 2,
      ]))
    }
  }
  return dividers
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
  graphics.rect(-radius * 3, -radius * 3, radius * 6, radius * 6).fill({ color: GROUND })
  if (land.NORMAL) {
    graphics.rect(-radius * 3, -radius * 3, radius * 6, radius * 6)
      .fill({ ...land.NORMAL, alpha: NORMAL_TEXTURE_ALPHA })
  }

  // Ground cover first: it sits under everything, and a building standing in a
  // park should be drawn on the park rather than beside it.
  scene.landCovers?.forEach((cover) => {
    const kind = String(cover.type || 'OTHER').toUpperCase()
    if (kind === 'NORMAL') return
    const colour = colourFor(LAND_COLOUR, kind, LAND_COLOUR.OTHER)
    fillPolygon(graphics, cover.polygon, colour)
    if (land[kind]) {
      fillPolygon(graphics, cover.polygon, colour, LAND_TEXTURE_ALPHA, land[kind])
    }
  })

  scene.buildings?.forEach((building) => {
    const colour = colourFor(BUILDING_COLOUR, building.type, BUILDING_COLOUR.OTHER)
    const roof = roofs[String(building.type || '').toUpperCase()]
    if (building.footprint && building.footprint.length >= 3) {
      fillPolygon(graphics, building.footprint, colour)
      if (roof) {
        fillPolygon(graphics, building.footprint, colour, ROOF_TEXTURE_ALPHA, roof)
      }
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

  scene.lanes?.forEach((lane) => strokePath(graphics, lane.centreline, {
    width: lane.widthMetres, color: ROAD, cap: 'round', join: 'round',
  }))
  scene.junctions?.forEach((junction) =>
    fillPolygon(graphics, junction.pavement, PAVEMENT, 0.96))
  if (surfaces.ROAD) {
    scene.lanes?.forEach((lane) =>
      fillPolygon(graphics, laneCorridor(lane.centreline, lane.widthMetres),
        ROAD, ROAD_TEXTURE_ALPHA, surfaces.ROAD))
  }
  if (surfaces.JUNCTION) {
    scene.junctions?.forEach((junction) =>
      fillPolygon(graphics, junction.pavement, PAVEMENT,
        ROAD_TEXTURE_ALPHA, surfaces.JUNCTION))
  }
  // Connector curves remain in the scene as interpolation paths, but are not
  // painted as guide lines. Junction pavement already supplies the visible
  // tarmac; stroking every legal movement added thousands of overlapping
  // segments to the static GPU mesh without changing what a driver sees.

  laneDividers(scene.lanes).forEach((divider) => {
    strokeDashed(graphics, divider, { width: 0.16, color: LANE_DASH, alpha: 0.9 })
  })

}

/**
 * One signal dot per controlled approach.
 *
 * The old combination of a stop-line bar, a glow and a lamp looked like a gate
 * blocking the road. Direction still matters — one junction can be green on one
 * axis and red on the other — so each approach keeps one small dot, with no bar
 * and no second glow circle.
 */
export function drawSignals(graphics, scene) {
  graphics.clear()
  if (!scene?.signals) return
  scene.signals.forEach((signal) => {
    signal.approaches?.forEach((approach) => {
      const [x, y] = worldPoint(approach.head[0], approach.head[1])
      const colour = approach.green ? 0x2fbf62 : 0xe03b3b
      graphics.circle(x, y, 0.48).fill({ color: colour })
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
  let hasTrail = false
  vehicles.forEach((vehicle) => {
    if (String(vehicle.type).toUpperCase() !== 'MOTORCYCLE') return
    if (!(vehicle.speedMps > 1.5)) return
    const rad = (vehicle.headingDegrees * Math.PI) / 180
    const back = Math.max(vehicle.lengthMetres, 1.5) * 2.2
    const [cx, cy] = worldPoint(vehicle.x, vehicle.y)
    graphics.moveTo(cx, cy)
    graphics.lineTo(cx - Math.sin(rad) * back, cy + Math.cos(rad) * back)
    hasTrail = true
  })
  // One batched stroke keeps the hot 60 FPS path to one Graphics submission
  // instead of creating one submission per moving motorcycle.
  if (hasTrail) graphics.stroke({ width: 0.35, color: TRAIL, alpha: 0.35 })
}
