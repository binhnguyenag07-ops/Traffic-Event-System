// Builds the physical-view test lab: small synthetic maps, one per thing that
// can look wrong on screen.
//
// District 1 is a poor place to find a rendering bug. It has 730 roads, so a
// vehicle facing the wrong way is one speck among hundreds, and its geometry
// comes from OpenStreetMap, so when something looks bent there is no way to
// tell a drawing fault from a map that really is bent. Every map here is
// authored in metres from a single origin, which means the correct picture is
// known exactly: a straight road IS straight, a lane IS 3.5 m wide, and a
// vehicle's heading on it is a constant that can be read off the screen.
//
// Each case pairs a map with a scenario and one sentence saying what should be
// seen. The sentence matters as much as the geometry — a case nobody can grade
// is just a picture.
//
//   node tools/generate_test_maps.mjs
//
// Writes data/maps/test/*.json and data/scenarios/test/*.json.

import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const MAP_DIR = join(ROOT, 'data', 'maps', 'test')
const SCENARIO_DIR = join(ROOT, 'data', 'scenarios', 'test')

// Somewhere in District 1, so the numbers look like the real map's and the
// same latitude-dependent longitude scaling applies.
const ORIGIN = { latitude: 10.78, longitude: 106.7 }
const METRES_PER_DEGREE_LATITUDE = 111_320

/** A point this many metres east and north of the origin, as [lat, lon]. */
function at(eastMetres, northMetres) {
  const latitude = ORIGIN.latitude + northMetres / METRES_PER_DEGREE_LATITUDE
  const longitude = ORIGIN.longitude
    + eastMetres / (METRES_PER_DEGREE_LATITUDE * Math.cos(ORIGIN.latitude * Math.PI / 180))
  return [round(latitude, 7), round(longitude, 7)]
}

function round(value, digits) {
  const factor = 10 ** digits
  return Math.round(value * factor) / factor
}

function metresBetween([lat1, lon1], [lat2, lon2]) {
  const dNorth = (lat2 - lat1) * METRES_PER_DEGREE_LATITUDE
  const dEast = (lon2 - lon1) * METRES_PER_DEGREE_LATITUDE
    * Math.cos(ORIGIN.latitude * Math.PI / 180)
  return Math.hypot(dEast, dNorth)
}

function polylineLengthKm(points) {
  let total = 0
  for (let i = 1; i < points.length; i += 1) total += metresBetween(points[i - 1], points[i])
  return round(total / 1000, 5)
}

function junction(id, eastMetres, northMetres, locationType = 'NORMAL') {
  const [latitude, longitude] = at(eastMetres, northMetres)
  return {
    id,
    name: id,
    // The macro view wants a normalised planar coordinate. Metres scaled down
    // keep the shape right; nothing in the physical view reads these.
    x: round(eastMetres / 1000, 6),
    y: round(northMetres / 1000, 6),
    latitude,
    longitude,
    locationType,
  }
}

/**
 * One directed road. `points` are [east, north] metre pairs; the first and last
 * must sit on the two junctions, because the renderer trusts the geometry and
 * the router trusts the junctions, and a case where they disagree tests the
 * wrong thing.
 */
function road(id, from, to, points, options = {}) {
  const geometry = points.map(([east, north]) => at(east, north))
  return {
    id,
    name: options.name || id,
    physicalRoadId: options.physicalRoadId || id.replace(/-[FR]$/, ''),
    startIntersectionId: from,
    endIntersectionId: to,
    distance: polylineLengthKm(geometry),
    speedLimit: options.speedLimit ?? 40,
    capacity: options.capacity ?? 60,
    currentVehicleCount: 0,
    congestionLevel: 0,
    vehicleDensity: 0,
    roadStatus: options.roadStatus || 'OPEN',
    roadClass: options.roadClass || 'SECONDARY',
    laneCount: options.laneCount ?? 1,
    laneWidthMetres: options.laneWidthMetres ?? 3.5,
    geometry,
  }
}

/** A road and its reverse, sharing one physical carriageway id. */
function twoWay(baseId, a, b, points, options = {}) {
  return [
    road(`${baseId}-F`, a, b, points, { ...options, physicalRoadId: baseId }),
    road(`${baseId}-R`, b, a, [...points].reverse(), { ...options, physicalRoadId: baseId }),
  ]
}

function building(id, type, eastMetres, northMetres, accessIntersectionId, sizeMetres = 18) {
  const half = sizeMetres / 2
  const [latitude, longitude] = at(eastMetres, northMetres)
  return {
    id,
    name: `${type} ${id}`,
    type,
    x: round(eastMetres / 1000, 6),
    y: round(northMetres / 1000, 6),
    latitude,
    longitude,
    accessPoints: [
      { id: `${id}-A0`, intersectionId: accessIntersectionId, drivewayDistanceMetres: 20 },
    ],
    footprint: [
      at(eastMetres - half, northMetres - half),
      at(eastMetres + half, northMetres - half),
      at(eastMetres + half, northMetres + half),
      at(eastMetres - half, northMetres + half),
    ],
  }
}

function landCover(id, type, eastMetres, northMetres, sizeMetres = 40) {
  const half = sizeMetres / 2
  return {
    id,
    type,
    polygon: [
      at(eastMetres - half, northMetres - half),
      at(eastMetres + half, northMetres - half),
      at(eastMetres + half, northMetres + half),
      at(eastMetres - half, northMetres + half),
    ],
  }
}

function vehicle(id, type, start, destination) {
  return { id, type, startIntersectionId: start, destinationIntersectionId: destination }
}

/** Points along a circular arc, centred on (centreEast, centreNorth). */
function arc(centreEast, centreNorth, radius, fromDegrees, toDegrees, steps) {
  return Array.from({ length: steps + 1 }, (_, index) => {
    const angle = (fromDegrees + (toDegrees - fromDegrees) * index / steps) * Math.PI / 180
    return [centreEast + radius * Math.cos(angle), centreNorth + radius * Math.sin(angle)]
  })
}

// ---------------------------------------------------------------------------
// The cases.
// ---------------------------------------------------------------------------

const CASES = []

function testCase(definition) {
  CASES.push(definition)
}

testCase({
  id: 'straight-heading',
  name: 'Straight road: heading must not move',
  expect: 'One car drives 300 m due north. Its sprite must keep exactly the same '
    + 'angle for the whole run. Any twitch is a heading bug, not a map bug.',
  radiusMetres: 200,
  build: () => ({
    intersections: [junction('A', 0, -150), junction('B', 0, 150)],
    roads: [road('R1', 'A', 'B', [[0, -150], [0, -50], [0, 50], [0, 150]])],
    vehicles: [vehicle('v-straight', 'CAR', 'A', 'B')],
  }),
})

testCase({
  id: 'duplicate-vertices',
  name: 'Repeated geometry points',
  expect: 'Same straight road, but the centreline repeats a point twice. The car '
    + 'must still hold one heading. A flick to north as it passes the middle means '
    + 'a zero-length segment is being used to compute the bearing.',
  radiusMetres: 200,
  build: () => ({
    intersections: [junction('A', 0, -150), junction('B', 0, 150)],
    roads: [road('R1', 'A', 'B', [
      [0, -150], [0, -50], [0, 0], [0, 0], [0, 0], [0, 50], [0, 150],
    ])],
    vehicles: [vehicle('v-dup', 'CAR', 'A', 'B')],
  }),
})

testCase({
  id: 'gentle-curve',
  name: 'Gentle curve: rotation must be smooth',
  expect: 'A 400 m radius bend. The car should rotate a little each frame. '
    + 'Stepwise jumps mean the heading is taken from whole segments instead of '
    + 'being sampled along the curve.',
  radiusMetres: 260,
  build: () => {
    const points = arc(0, 0, 400, 250, 290, 32)
    return {
      intersections: [
        junction('A', points[0][0], points[0][1]),
        junction('B', points[points.length - 1][0], points[points.length - 1][1]),
      ],
      roads: [road('R1', 'A', 'B', points)],
      vehicles: [vehicle('v-curve', 'CAR', 'A', 'B')],
    }
  },
})

testCase({
  id: 'sharp-corner',
  name: 'Right-angle corner',
  expect: 'Two 150 m legs meeting at 90 degrees inside one road. The painted lane '
    + 'must stay a constant width round the corner. A pinch or a crossed-over spike '
    + 'on the inside is the lane-offset maths folding back on itself.',
  radiusMetres: 220,
  build: () => ({
    intersections: [junction('A', -150, 0), junction('B', 0, 150)],
    roads: [road('R1', 'A', 'B', [[-150, 0], [-75, 0], [0, 0], [0, 75], [0, 150]])],
    vehicles: [vehicle('v-corner', 'CAR', 'A', 'B')],
  }),
})

testCase({
  id: 'two-way-offset',
  name: 'Two-way road: carriageways must not overlap',
  expect: 'One physical street carrying both directions. The two streams must be '
    + 'drawn side by side with a visible gap. If they sit on top of each other the '
    + 'carriageway offset is not being applied.',
  radiusMetres: 220,
  build: () => ({
    intersections: [junction('A', 0, -150), junction('B', 0, 150)],
    roads: twoWay('R1', 'A', 'B', [[0, -150], [0, 0], [0, 150]], { laneCount: 2 }),
    vehicles: [
      vehicle('v-north', 'CAR', 'A', 'B'),
      vehicle('v-south', 'CAR', 'B', 'A'),
    ],
  }),
})

testCase({
  id: 'multi-lane',
  name: 'Three lanes side by side',
  expect: 'Six cars on a three-lane road. They must sit on three distinct lane '
    + 'centres. Two sprites sharing one centre line, or a car half off the asphalt, '
    + 'is a lane-offset bug.',
  radiusMetres: 220,
  build: () => ({
    intersections: [junction('A', 0, -150), junction('B', 0, 150)],
    roads: [road('R1', 'A', 'B', [[0, -150], [0, 150]], { laneCount: 3, capacity: 200 })],
    vehicles: Array.from({ length: 6 }, (_, index) =>
      vehicle(`v-lane-${index}`, 'CAR', 'A', 'B')),
  }),
})

testCase({
  id: 'moto-filtering',
  name: 'Motorcycles filtering past a queue',
  expect: 'Cars and motorcycles share one 4.5 m lane. Motorcycles should end up '
    + 'abreast of the cars, not queued behind them, and must never overlap a car.',
  radiusMetres: 160,
  build: () => ({
    intersections: [junction('A', 0, -100), junction('B', 0, 100)],
    roads: [road('R1', 'A', 'B', [[0, -100], [0, 100]], {
      laneCount: 1, laneWidthMetres: 4.5, capacity: 200,
    })],
    vehicles: [
      ...Array.from({ length: 4 }, (_, i) => vehicle(`v-car-${i}`, 'CAR', 'A', 'B')),
      ...Array.from({ length: 6 }, (_, i) => vehicle(`v-moto-${i}`, 'MOTORCYCLE', 'A', 'B')),
    ],
  }),
})

testCase({
  id: 'stop-line',
  name: 'Queue at a stop line',
  expect: 'Cars queue at a signal. Every nose must stop short of the painted line '
    + 'and no two bodies may overlap. A car sitting half across the line means the '
    + 'sprite is centred on the bumper instead of set back from it.',
  radiusMetres: 130,
  build: () => ({
    intersections: [
      junction('A', 0, -140), junction('J', 0, 0), junction('B', 0, 140),
      junction('W', -140, 0), junction('E', 140, 0),
    ],
    roads: [
      road('IN', 'A', 'J', [[0, -140], [0, 0]], { capacity: 200 }),
      road('OUT', 'J', 'B', [[0, 0], [0, 140]]),
      road('CROSS-W', 'W', 'J', [[-140, 0], [0, 0]]),
      road('CROSS-E', 'J', 'E', [[0, 0], [140, 0]]),
    ],
    vehicles: Array.from({ length: 6 }, (_, index) =>
      vehicle(`v-queue-${index}`, 'CAR', 'A', 'B')),
  }),
})

testCase({
  id: 'junction-4way',
  name: 'Four-way junction with signals',
  expect: 'Traffic from all four arms. Signals must read as small glowing dots at '
    + 'each approach, and turning vehicles must follow a curve across the junction '
    + 'rather than cutting the corner in a straight line.',
  radiusMetres: 200,
  build: () => {
    const arms = [
      ['N', 0, 160], ['S', 0, -160], ['E', 160, 0], ['W', -160, 0],
    ]
    const intersections = [junction('J', 0, 0), ...arms.map(([id, e, n]) => junction(id, e, n))]
    const roads = arms.flatMap(([id, e, n]) => twoWay(`ARM-${id}`, id, 'J', [[e, n], [0, 0]], {
      laneCount: 2, capacity: 120,
    }))
    return {
      intersections,
      roads,
      vehicles: [
        vehicle('v-ns', 'CAR', 'N', 'S'),
        vehicle('v-sn', 'CAR', 'S', 'N'),
        vehicle('v-ew', 'CAR', 'E', 'W'),
        vehicle('v-we', 'BUS', 'W', 'E'),
        vehicle('v-ne', 'MOTORCYCLE', 'N', 'E'),
        vehicle('v-ws', 'MOTORCYCLE', 'W', 'S'),
      ],
    }
  },
})

testCase({
  id: 'u-turn',
  name: 'U-turn at a junction',
  expect: 'A two-way street with a junction in the middle. A vehicle sent back the '
    + 'way it came must swing round through an arc. If it flips 180 degrees on the '
    + 'spot in one frame, U-turns have no turning geometry.',
  radiusMetres: 220,
  build: () => ({
    intersections: [junction('A', 0, -160), junction('M', 0, 0), junction('B', 0, 160)],
    roads: [
      ...twoWay('R1', 'A', 'M', [[0, -160], [0, 0]], { laneCount: 2 }),
      ...twoWay('R2', 'M', 'B', [[0, 0], [0, 160]], { laneCount: 2 }),
    ],
    vehicles: [vehicle('v-uturn', 'CAR', 'A', 'A')],
  }),
})

testCase({
  id: 'dead-end',
  name: 'Dead end',
  expect: 'A road that simply stops. The car must come to rest at the end, still '
    + 'on the asphalt and still facing along it — not drift past the last vertex '
    + 'and not snap round to face north.',
  radiusMetres: 160,
  build: () => ({
    intersections: [junction('A', 0, -100), junction('DEAD', 0, 100)],
    roads: [road('R1', 'A', 'DEAD', [[0, -100], [0, 100]])],
    vehicles: [vehicle('v-dead', 'CAR', 'A', 'DEAD')],
  }),
})

testCase({
  id: 'accident-immobile',
  name: 'Crashed vehicles must stay put',
  expect: 'An accident is declared 20 s in, naming both vehicles. From that moment '
    + 'until it clears they must not move one metre. If they drive away, something '
    + 'is releasing vehicles that an event stopped.',
  radiusMetres: 200,
  build: () => ({
    intersections: [junction('A', 0, -150), junction('B', 0, 150)],
    roads: [road('R1', 'A', 'B', [[0, -150], [0, 150]], { capacity: 200 })],
    vehicles: [
      vehicle('v-lead', 'CAR', 'A', 'B'),
      vehicle('v-follow', 'CAR', 'A', 'B'),
    ],
    events: [{
      id: 'crash',
      type: 'ACCIDENT',
      affectedRoadId: 'R1',
      startTimeSeconds: 20,
      durationSeconds: 600,
      value: 0.8,
      causingVehicleId: 'v-follow',
      positionAlongRoad: 0.5,
    }],
  }),
})

testCase({
  id: 'congestion-reroute',
  name: 'Congestion: rerouting is correct here',
  expect: 'Two ways round a diamond, the left one jammed. Vehicles SHOULD switch to '
    + 'the right branch. This is the control case for the accident one: same '
    + 'mechanism, and here the movement is what we want.',
  radiusMetres: 260,
  build: () => ({
    intersections: [
      junction('A', 0, -180), junction('L', -140, 0),
      junction('R', 140, 0), junction('D', 0, 180),
    ],
    roads: [
      road('A-L', 'A', 'L', [[0, -180], [-140, 0]], { capacity: 200 }),
      road('L-D', 'L', 'D', [[-140, 0], [0, 180]], { capacity: 200 }),
      road('A-R', 'A', 'R', [[0, -180], [140, 0]], { capacity: 200 }),
      road('R-D', 'R', 'D', [[140, 0], [0, 180]], { capacity: 200 }),
    ],
    vehicles: Array.from({ length: 8 }, (_, index) => vehicle(`v-div-${index}`, 'CAR', 'A', 'D')),
    events: [{
      id: 'jam',
      type: 'CONGESTION',
      affectedRoadId: 'A-L',
      startTimeSeconds: 5,
      durationSeconds: 900,
      value: 0.9,
    }],
  }),
})

testCase({
  id: 'stranded',
  name: 'No way through',
  expect: 'The only road onward is closed and there is no detour. The vehicle must '
    + 'stop and stay stopped. It must not U-turn onto oncoming traffic, teleport, '
    + 'or drive through the closure.',
  radiusMetres: 200,
  build: () => ({
    intersections: [junction('A', 0, -150), junction('M', 0, 0), junction('B', 0, 150)],
    roads: [
      road('A-M', 'A', 'M', [[0, -150], [0, 0]]),
      road('M-B', 'M', 'B', [[0, 0], [0, 150]]),
    ],
    vehicles: [vehicle('v-stuck', 'CAR', 'A', 'B')],
    events: [{
      id: 'closure',
      type: 'ROAD_CLOSURE',
      affectedRoadId: 'M-B',
      startTimeSeconds: 5,
      durationSeconds: 900,
    }],
  }),
})

testCase({
  id: 'asset-grid',
  name: 'Every asset slot at once',
  expect: 'One building of each type, one land cover of each type, and one of each '
    + 'vehicle. Any flat coloured block is a texture that failed to load; any two '
    + 'types looking identical is a manifest pointing twice at the same file.',
  radiusMetres: 320,
  build: () => {
    const buildingTypes = [
      'RESIDENTIAL', 'COMMERCIAL', 'OFFICE', 'SCHOOL',
      'HOSPITAL', 'INDUSTRIAL', 'PARKING', 'OTHER',
    ]
    const landTypes = [
      'PARK', 'WATER', 'GRASS', 'FOREST', 'COMMERCIAL', 'RESIDENTIAL',
      'INDUSTRIAL', 'CONSTRUCTION', 'RELIGIOUS', 'RECREATION', 'OTHER',
    ]
    return {
      intersections: [junction('A', -250, 0), junction('B', 250, 0)],
      roads: [road('R1', 'A', 'B', [[-250, 0], [250, 0]], { laneCount: 2, capacity: 120 })],
      buildings: buildingTypes.map((type, index) =>
        building(`B${index}`, type, -220 + index * 60, 60, 'A')),
      landCovers: landTypes.map((type, index) =>
        landCover(`L${index}`, type, -240 + index * 48, -70, 44)),
      vehicles: [
        vehicle('v-car', 'CAR', 'A', 'B'),
        vehicle('v-moto', 'MOTORCYCLE', 'A', 'B'),
        vehicle('v-bus', 'BUS', 'A', 'B'),
        vehicle('v-emg', 'EMERGENCY', 'A', 'B'),
      ],
    }
  },
})

testCase({
  id: 'homes-onto-the-street',
  name: 'Traffic pouring out of houses',
  expect: 'Ten houses along a residential street and two offices at the end, with '
    + 'demand generated from the houses themselves. Vehicles must appear at the '
    + 'junctions their driveways reach, join the correct carriageway, and head for '
    + 'the offices — not materialise in the middle of the road.',
  radiusMetres: 300,
  build: () => {
    const stops = [['A', -300], ['B', -100], ['C', 100], ['D', 300]]
    const intersections = stops.map(([id, east]) => junction(id, east, 0))
    const roads = [
      ...twoWay('ST1', 'A', 'B', [[-300, 0], [-100, 0]], { laneCount: 2, capacity: 200 }),
      ...twoWay('ST2', 'B', 'C', [[-100, 0], [100, 0]], { laneCount: 2, capacity: 200 }),
      ...twoWay('ST3', 'C', 'D', [[100, 0], [300, 0]], { laneCount: 2, capacity: 200 }),
    ]
    // Houses on the north side, reaching the junction nearest each of them.
    const houses = Array.from({ length: 10 }, (_, index) => building(
      `H${index}`, 'RESIDENTIAL',
      -280 + index * 50, 55,
      index < 4 ? 'A' : index < 7 ? 'B' : 'C',
      24))
    const offices = [
      building('O0', 'OFFICE', 250, -55, 'D', 34),
      building('O1', 'OFFICE', 310, -55, 'D', 34),
    ]
    return {
      intersections,
      roads,
      buildings: [...houses, ...offices],
      landCovers: [landCover('L0', 'RESIDENTIAL', 0, 90, 120)],
      vehicles: [],
      demand: {
        demandParameters: '../../demand/default_assumptions.json',
        startTimeOfDay: '07:00',
        durationHours: 3,
        seed: 7,
        // Ten houses over three hours give about four vehicles at once, which is
        // too thin to see anything go wrong. Scaled up until the street is busy:
        // this is a rendering test, not a demand estimate.
        scale: 8.0,
        demandSources: [{
          id: 'houses',
          from: { kind: 'BUILDING', types: ['RESIDENTIAL'] },
          production: { kind: 'POPULATION_DERIVED', tripsPerPersonPerDay: 2.0 },
          flows: [{
            id: 'to-work',
            purpose: 'COMMUTE',
            share: 0.9,
            vehicleMix: [
              { type: 'MOTORCYCLE', share: 0.75 },
              { type: 'CAR', share: 0.25 },
            ],
            curve: 'MORNING_PEAK',
            completion: 'REMOVE_AT_LAST_STOP',
            legs: [{ to: { kind: 'BUILDING', types: ['OFFICE'] } }],
          }],
        }],
      },
    }
  },
})

// ---------------------------------------------------------------------------

/** Centre of a map, so the lab can open the view where the roads actually are. */
function centreOf(intersections) {
  const latitudes = intersections.map((entry) => entry.latitude)
  const longitudes = intersections.map((entry) => entry.longitude)
  return [
    round((Math.min(...latitudes) + Math.max(...latitudes)) / 2, 7),
    round((Math.min(...longitudes) + Math.max(...longitudes)) / 2, 7),
  ]
}

function main() {
  mkdirSync(MAP_DIR, { recursive: true })
  mkdirSync(SCENARIO_DIR, { recursive: true })

  const index = []
  for (const definition of CASES) {
    const built = definition.build()
    const map = {
      intersections: built.intersections,
      roads: built.roads,
      buildings: built.buildings || [],
      landCovers: built.landCovers || [],
    }
    writeFileSync(
      join(MAP_DIR, `${definition.id}.json`),
      `${JSON.stringify(map, null, 2)}\n`,
    )

    const scenario = {
      id: `test-${definition.id}`,
      name: definition.name,
      description: definition.expect,
      map: `../../maps/test/${definition.id}.json`,
      lab: {
        physicalOnly: true,
        centre: centreOf(built.intersections),
        radiusMetres: definition.radiusMetres,
        expect: definition.expect,
      },
      // A case that generates its own traffic carries a demand block; the rest
      // script their vehicles outright, which keeps them exactly reproducible.
      ...(built.demand || {}),
      vehicles: built.vehicles || [],
      events: built.events || [],
    }
    writeFileSync(
      join(SCENARIO_DIR, `${definition.id}.json`),
      `${JSON.stringify(scenario, null, 2)}\n`,
    )

    index.push({
      id: scenario.id,
      roads: map.roads.length,
      vehicles: scenario.vehicles.length,
      events: scenario.events.length,
    })
  }

  console.log(`wrote ${CASES.length} cases to data/maps/test and data/scenarios/test`)
  for (const entry of index) {
    console.log(
      `  ${entry.id.padEnd(28)} roads ${String(entry.roads).padStart(2)}`
      + `  vehicles ${String(entry.vehicles).padStart(2)}`
      + `  events ${entry.events}`,
    )
  }
}

main()
