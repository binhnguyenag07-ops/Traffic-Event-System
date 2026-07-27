// Runs every physical-view test case headlessly and reports what moved wrong.
//
// The lab exists to be looked at, but the eye is bad at the specific faults
// these cases are built for: a heading that flicks to north for a single frame
// is gone before you register it, and a vehicle that creeps two metres past a
// stop line looks like a vehicle at a stop line. So this reads the same numbers
// the renderer draws from — `/api/simulation/physical-view` — and measures them.
//
// It asserts nothing about the simulation being realistic. It reports movement
// that is geometrically impossible: headings that jump, positions that teleport,
// and vehicles that an event stopped but that moved anyway.
//
//   node tools/probe_test_cases.mjs            all cases
//   node tools/probe_test_cases.mjs u-turn     one case, by id fragment
//
// Requires the backend on :7070.

const API = 'http://localhost:7070'
const TICKS = 60

// A heading step larger than this between consecutive frames is reported. Real
// cornering is gradual; anything sharper is the bearing being recomputed from a
// different source rather than the vehicle actually turning that fast.
const HEADING_JUMP_DEGREES = 45
// A vehicle covering more than this in one 3 s tick is not driving.
const TELEPORT_METRES = 60

async function json(path, options) {
  const response = await fetch(API + path, options)
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(`${path} -> ${response.status} ${JSON.stringify(body)}`)
  return body
}

/** Shortest absolute turn between two compass bearings, in degrees. */
function turn(from, to) {
  const difference = Math.abs(((to - from + 540) % 360) - 180)
  return difference
}

async function sample(centre) {
  const [latitude, longitude] = centre
  const view = await json(
    `/api/simulation/physical-view?lat=${latitude}&lon=${longitude}&fullMap=true`)
  return (view.vehicles || []).map((vehicle) => ({
    id: vehicle.id,
    x: vehicle.x,
    y: vehicle.y,
    heading: vehicle.headingDegrees,
  }))
}

async function runCase(scenario) {
  const centre = scenario.lab.centre
  await json(`/api/scenarios/${scenario.id}/activate`, { method: 'POST' })

  const series = new Map()
  const record = (frame, tick) => {
    for (const vehicle of frame) {
      if (!series.has(vehicle.id)) series.set(vehicle.id, [])
      series.get(vehicle.id).push({ ...vehicle, tick })
    }
  }

  record(await sample(centre), 0)
  for (let tick = 1; tick <= TICKS; tick += 1) {
    await json('/api/simulation/tick', { method: 'POST' })
    record(await sample(centre), tick)
  }

  const findings = []
  let movedAtAll = 0
  for (const [id, points] of series) {
    if (points.length < 2) continue
    let headingMin = Infinity
    let headingMax = -Infinity
    let worstTurn = 0
    let worstTurnTick = 0
    let worstStep = 0
    let worstStepTick = 0
    let travelled = 0
    let northSnaps = 0

    for (let index = 1; index < points.length; index += 1) {
      const previous = points[index - 1]
      const current = points[index]
      const step = Math.hypot(current.x - previous.x, current.y - previous.y)
      travelled += step
      if (step > worstStep) { worstStep = step; worstStepTick = current.tick }
      const turned = turn(previous.heading, current.heading)
      if (turned > worstTurn) { worstTurn = turned; worstTurnTick = current.tick }
      headingMin = Math.min(headingMin, current.heading)
      headingMax = Math.max(headingMax, current.heading)
      // Exactly due north with neighbours pointing elsewhere is the signature of
      // a bearing computed from a zero-length segment, not a real turn.
      if (current.heading === 0 && previous.heading !== 0) northSnaps += 1
    }
    if (travelled > 1) movedAtAll += 1

    if (worstTurn > HEADING_JUMP_DEGREES) {
      findings.push(`${id}: heading jumped ${worstTurn.toFixed(0)}° in one tick (tick ${worstTurnTick})`)
    }
    if (worstStep > TELEPORT_METRES) {
      findings.push(`${id}: moved ${worstStep.toFixed(0)} m in one tick (tick ${worstStepTick})`)
    }
    if (northSnaps > 0) {
      findings.push(`${id}: snapped to due north ${northSnaps}×`)
    }
    series.set(id, { points, headingMin, headingMax, travelled })
  }

  return { findings, vehicles: series.size, movedAtAll, series }
}

/**
 * Case-specific checks. The generic ones above catch impossible movement; these
 * catch movement that is possible but wrong for this particular case.
 */
async function specificChecks(scenario, result) {
  const findings = []
  const id = scenario.id

  if (id === 'test-straight-heading' || id === 'test-duplicate-vertices') {
    for (const [vehicleId, data] of result.series) {
      if (!data.points) continue
      const spread = data.headingMax - data.headingMin
      if (spread > 1) {
        findings.push(`${vehicleId}: heading varied ${spread.toFixed(1)}° on a straight road`)
      }
    }
  }

  if (id === 'test-accident-immobile') {
    // The accident starts at 20 s; each tick is 3 s, so it is live from tick 7.
    for (const [vehicleId, data] of result.series) {
      if (!data.points) continue
      const after = data.points.filter((point) => point.tick >= 10)
      if (after.length < 2) continue
      const moved = Math.hypot(
        after[after.length - 1].x - after[0].x,
        after[after.length - 1].y - after[0].y)
      if (moved > 2) {
        findings.push(`${vehicleId}: moved ${moved.toFixed(1)} m AFTER the accident began`)
      }
    }
  }

  if (id === 'test-congestion-reroute' && result.movedAtAll === 0) {
    findings.push('no vehicle moved — the control case should show traffic flowing')
  }

  return findings
}

async function main() {
  const filter = process.argv[2]
  const scenarios = (await json('/api/scenarios'))
    .filter((scenario) => scenario.lab)
    .filter((scenario) => !filter || scenario.id.includes(filter))

  if (scenarios.length === 0) {
    console.log('no matching lab cases')
    return
  }

  let clean = 0
  for (const scenario of scenarios) {
    const result = await runCase(scenario)
    const findings = [...result.findings, ...(await specificChecks(scenario, result))]
    const label = scenario.id.replace(/^test-/, '')
    if (findings.length === 0) {
      clean += 1
      console.log(`PASS  ${label.padEnd(22)} ${result.vehicles} vehicles, ${result.movedAtAll} moved`)
      continue
    }
    console.log(`FAIL  ${label.padEnd(22)} ${result.vehicles} vehicles, ${result.movedAtAll} moved`)
    for (const finding of findings) console.log(`        · ${finding}`)
  }
  console.log(`\n${clean}/${scenarios.length} cases clean`)
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
