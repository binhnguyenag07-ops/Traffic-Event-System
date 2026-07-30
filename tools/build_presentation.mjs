// Generates PRESENTATION.html: a standalone, offline walkthrough of the whole
// system for someone who has to learn it — architecture, the OOP patterns and
// where they actually live, one simulated tick end to end, and a searchable
// index of every backend type.
//
// The class index is read out of the source rather than written by hand. A
// hand-written index is wrong the first time somebody renames a class and
// nobody notices for a month; this one is wrong for exactly as long as it takes
// to rerun the script. The narrative prose around it is authored, because
// "what is this package for" is not recoverable from a parse.
//
// The parser is deliberately shallow: Java is not a regular language and this
// does not pretend to parse it. It reads the file's package, its first
// top-level type declaration, that type's Javadoc, and its public method
// signatures. That is all the index needs, and shallow means it degrades to a
// missing description instead of a crash on syntax it has not seen.
//
//   node tools/build_presentation.mjs
//
// Writes PRESENTATION.html at the repository root. Open it in any browser; it
// has no external requests, no build step, and no server.

import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

const ROOT = resolve(import.meta.dirname, '..')
const OUTPUT = join(ROOT, 'PRESENTATION.html')

/* ------------------------------------------------------------------ parsing */

const TYPE_DECLARATION =
  /^\s*(?:public\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+|static\s+)*(class|interface|enum|record)\s+([A-Za-z0-9_]+)/

const MODIFIERS = '(?:static\\s+|final\\s+|synchronized\\s+|abstract\\s+|default\\s+)*'
const SIGNATURE = '(?:<[^>]+>\\s*)?([A-Za-z0-9_<>,.\\[\\]?\\s]+?)\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)'

/**
 * Public method signatures, at one level of nesting so nested helper types are
 * skipped.
 *
 * Two variants because interface members are implicitly public and the source
 * does not repeat the keyword. Requiring it would have reported every interface
 * in the project as having no methods, which on a codebase whose whole routing
 * and dynamics story is "one interface, several implementations" would have
 * hidden exactly the types a reader most needs to see.
 */
const METHOD_DECLARATION = new RegExp(
  `^\\s{4}public\\s+(?!class|interface|enum|record)${MODIFIERS}${SIGNATURE}`)
const INTERFACE_METHOD_DECLARATION = new RegExp(
  `^\\s{4}(?!public|private|class|interface|enum|record|@)${MODIFIERS}${SIGNATURE}`)

/**
 * The first sentence of a Javadoc block, flattened to one line.
 *
 * Returns an empty string for a block that is only tags, which is common on
 * records whose fields carry the whole explanation.
 */
function firstSentence(lines) {
  const body = []
  for (const raw of lines) {
    const line = raw.replace(/^\s*\/?\*+\/?/, '').replace(/\*\/\s*$/, '').trim()
    if (line.startsWith('@')) break
    body.push(line)
  }
  const text = body.join(' ')
    .replace(/<\/?p>/g, ' ')
    .replace(/\{@code\s+([^}]*)\}/g, '$1')
    .replace(/\{@link\s+([^}]*)\}/g, '$1')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (!text) return ''
  const stop = text.search(/\.(\s|$)/)
  return stop === -1 ? text : text.slice(0, stop + 1)
}

/**
 * Splits a parameter list on commas that are not inside generics.
 *
 * `List<String, Integer> a, int b` is two components, not three. Depth
 * counting is enough here because a record component list cannot contain
 * parentheses or a lambda.
 */
function splitTopLevel(text) {
  const parts = []
  let depth = 0
  let current = ''
  for (const character of text) {
    if (character === '<') depth += 1
    else if (character === '>') depth -= 1
    if (character === ',' && depth === 0) { parts.push(current); current = ''; continue }
    current += character
  }
  parts.push(current)
  return parts
}

function parseJava(relativePath) {
  const source = readFileSync(join(ROOT, relativePath), 'utf8')
  const lines = source.split(/\r?\n/)

  const packageName = (source.match(/^package\s+([a-z0-9_.]+);/m) || [])[1] || ''

  let declarationIndex = -1
  for (let index = 0; index < lines.length; index += 1) {
    if (TYPE_DECLARATION.test(lines[index])) { declarationIndex = index; break }
  }
  if (declarationIndex === -1) return null

  const [, kind, name] = lines[declarationIndex].match(TYPE_DECLARATION)

  // The declaration can wrap across lines before the opening brace. A wide
  // record's component list is the reason for the generous line budget.
  let header = ''
  for (let index = declarationIndex; index < lines.length && index < declarationIndex + 30; index += 1) {
    header += ' ' + lines[index]
    if (lines[index].includes('{')) break
  }
  const extendsMatch = header.match(/\bextends\s+([A-Za-z0-9_.]+)/)
  const implementsMatch = header.match(/\bimplements\s+([A-Za-z0-9_.,<>\s]+?)\s*\{/)

  // Walk back over the Javadoc block immediately above the declaration.
  const doc = []
  let cursor = declarationIndex - 1
  while (cursor >= 0 && (lines[cursor].trim().startsWith('@') || lines[cursor].trim() === '')) cursor -= 1
  if (cursor >= 0 && lines[cursor].trim().endsWith('*/')) {
    const end = cursor
    while (cursor >= 0 && !lines[cursor].trim().startsWith('/**')) cursor -= 1
    if (cursor >= 0) doc.push(...lines.slice(cursor, end + 1))
  }

  const methods = []
  const pattern = kind === 'interface' ? INTERFACE_METHOD_DECLARATION : METHOD_DECLARATION
  for (const line of lines) {
    const match = line.match(pattern)
    if (!match) continue
    const [, returns, method, parameters] = match
    if (method === name) continue
    methods.push({
      name: method,
      returns: returns.trim().split(/\s+/).pop(),
      parameters: parameters
        .split(',')
        .map((parameter) => parameter.trim().split(/\s+/)[0])
        .filter(Boolean)
        .join(', '),
    })
  }

  // A record's components and an enum's constants are that type's entire
  // content. Reporting "0 public methods" for the 96 records and 34 enums here
  // would be technically true and completely useless.
  let members = []
  if (kind === 'record') {
    const components = header.match(/record\s+[A-Za-z0-9_]+\s*\(([\s\S]*?)\)/)
    if (components) {
      members = splitTopLevel(components[1])
        .map((component) => component.trim().replace(/\s+/g, ' '))
        .filter(Boolean)
    }
  } else if (kind === 'enum') {
    // Constants run from the declaration to the first ';' or member. Reading
    // line by line rather than regexing the whole body keeps SCREAMING_CASE
    // static finals further down the file out of the list.
    for (let index = declarationIndex + 1; index < lines.length; index += 1) {
      const line = lines[index].trim()
      if (line === '}' || /^(public|private|protected|static|final|@)/.test(line)) break
      const constant = line.match(/^([A-Z][A-Z0-9_]*)\s*(?:\(|,|;|$)/)
      if (constant) members.push(constant[1])
      if (line.includes(';')) break
    }
  }

  return {
    path: relativePath,
    package: packageName.replace(/^team\.traffic\.?/, '') || '(root)',
    name,
    kind,
    members,
    isAbstract: /\babstract\s+class\b/.test(lines[declarationIndex]),
    extends: extendsMatch ? extendsMatch[1] : null,
    implements: implementsMatch
      ? implementsMatch[1].split(',').map((item) => item.trim()).filter(Boolean)
      : [],
    summary: firstSentence(doc),
    lines: lines.length,
    methods,
  }
}

function tracked(pattern) {
  return execFileSync('git', ['ls-files', pattern], { cwd: ROOT })
    .toString('utf8')
    .split(/\r?\n/)
    .filter(Boolean)
}

/* ------------------------------------------------------- authored narrative */

/**
 * Package descriptions and the layer each belongs to.
 *
 * Layer order here is the order the reader meets them: outermost request path
 * first, then the model it drives, then what persists it.
 */
const LAYERS = [
  {
    id: 'api',
    title: 'API',
    blurb: 'Javalin routes, request/response DTOs, the SSE stream, and static hosting for the built frontend. Controllers translate HTTP into service calls and never touch the simulation directly.',
    packages: {
      'api': 'The Javalin server itself: route registration, JSON mapping, static frontend hosting, and the Server-Sent Events endpoint that pushes renderer frames.',
      'api.controller': 'One controller per resource — network, routes, vehicles, events, scenarios, simulation, statistics, experiments. Each parses input, calls a service, and shapes a response.',
      'api.dto': 'Request and response records. They exist so the wire format can change without changing the domain, and so a malformed request fails at the boundary.',
    },
  },
  {
    id: 'service',
    title: 'Services',
    blurb: 'The use-case boundary. Every interface here has exactly one production implementation, which is what makes the controllers testable: a controller test injects a fake service and never starts a simulation.',
    packages: {
      'service': 'The interfaces: RoadNetworkService, RoutingService, VehicleService, TrafficEventService, SimulationService, StatisticsService, NetworkEditingService.',
      'service.impl': 'The implementations. SimulationServiceImpl also owns the scheduled executor that drives continuous playback and the failure handling that pauses the run if a tick throws.',
    },
  },
  {
    id: 'domain',
    title: 'Domain',
    blurb: 'The model, with no knowledge of HTTP, JSON, or the renderer. This is where the object-oriented design lives and where nearly every test points.',
    packages: {
      'domain.network': 'RoadNetwork is the graph aggregate: intersections, directed roads, buildings, and the incoming/outgoing adjacency indexes routing reads. Each Road carries a stable physicalRoadId so the two directions of one street stay one street.',
      'domain.vehicle': 'The inheritance hierarchy. Vehicle is abstract; Car, Motorcycle, Bus, and EmergencyVehicle differ in physical profile, driver profile, and right-of-way, not in control flow.',
      'domain.vehicle.dynamics': 'The microscopic model — the largest package in the system. Krauss car-following, MOBIL lane changes, motorcycle filtering, stop-line arbitration, junction admission, collision detection, and the sub-step engine that runs them in a fixed order.',
      'domain.vehicle.navigation': 'What a driver knows and when. Information access, latency, reroute patience, minimum expected saving, cooldowns, and stall escape — the difference between a route being bad and a driver noticing it is bad.',
      'domain.vehicle.pose': 'Where a vehicle actually is: lane-offset latitude, longitude, and heading, derived from progress along a road rather than stored independently.',
      'domain.demand': 'Seeded trip generation. Building occupancy and attraction, hourly curves, purpose mixes, multi-stop plans with dwell, and exact return-to-origin conservation. The gravity model and the scripted model are interchangeable.',
      'domain.routing': 'Route value objects: the resolved path, its cost breakdown, and the snapshot a vehicle carries so a mid-trip network edit cannot silently rewrite history.',
      'domain.event': 'TrafficEvent and its four subclasses, plus the information policy that decides when drivers learn an event exists.',
      'domain.signal': 'Cyclic traffic-signal phase plans, derived for junctions of degree three or more.',
      'domain.scene': 'Lane-level geometry derived for the physical view: lane centrelines, junction connectors, stop lines, and surface polygons.',
      'domain.statistics': 'Records for completed trips, algorithm timings, and network density.',
    },
  },
  {
    id: 'routing',
    title: 'Routing',
    blurb: 'Three interchangeable search strategies behind one interface, plus the registry that lets the API list and compare them at runtime. This is the textbook Strategy pattern doing real work.',
    packages: {
      'routing': 'PathFindingStrategy with BFS, Dijkstra, and A* implementations, the registry that names them, congestion-aware cost, and movement restrictions at junctions.',
    },
  },
  {
    id: 'simulation',
    title: 'Simulation',
    blurb: 'The clock and everything that advances with it. TrafficSimulator is the aggregate; TickProcessor is the one place a tick is defined.',
    packages: {
      'simulation': 'TrafficSimulator, SimulationClock, TickProcessor, the immutable SimulationFrame the renderer reads, and the snapshot the REST API materialises.',
      'simulation.navigation': 'AdaptiveNavigationCoordinator: which drivers reconsider their route this tick, and whether the saving justifies it.',
      'simulation.deadlock': 'Detection and strict recovery for gridlock. Never teleports and never despawns — a deadlock is resolved by reservation order or not at all.',
      'simulation.trace': 'Per-road flow samples, fundamental-diagram bins, and time-space trajectories for the statistics panel.',
      'simulation.history': 'Bounded replay: authoritative frames captured at exact state transitions, indexed by simulated milliseconds, behind one timelineId.',
    },
  },
  {
    id: 'persistence',
    title: 'Persistence',
    blurb: 'Loading and saving, with validation at the edge. A map that fails validation never replaces the live network.',
    packages: {
      'persistence': 'JSON and CSV map loaders behind a common MapLoader interface, the scenario loader that assembles map plus demand plus events, the exporter, and the synthetic grid generator used by experiments.',
    },
  },
  {
    id: 'support',
    title: 'Supporting',
    blurb: 'The extension seams and the entry points.',
    packages: {
      'observer': 'TrafficEventBus and TrafficEventListener — the Observer extension boundary for anything that wants to react to an event beginning or resolving.',
      'experiment': 'The deterministic comparison runner behind <code>/api/experiments/run</code>. Works on isolated networks so it cannot disturb the live simulation.',
      'exception': 'Domain-specific failures: an invalid map, a network edit that would break a live vehicle.',
      '(root)': 'App and Config: the entry point, port and scenario resolution, and wiring.',
    },
  },
]

/** Design patterns, each anchored to the types that actually implement it. */
const PATTERNS = [
  {
    name: 'Strategy',
    idea: 'One interface, several interchangeable algorithms, chosen at runtime.',
    detail: 'This is the backbone of the project. Routing exposes three searches behind one interface, so <code>/api/routes/compare</code> can run all of them on the same request and report the difference. The same shape repeats in the dynamics layer, where a motorcycle swaps in a different lane-change model without changing a line of the engine that calls it.',
    anchors: [
      ['PathFindingStrategy', 'BfsPathFindingStrategy, DijkstraPathFindingStrategy, AStarPathFindingStrategy'],
      ['LaneChangeModel', 'MobilLaneChangeModel, FilteringLaneChangeModel'],
      ['CarFollowingModel', 'KraussCarFollowingModel'],
      ['TripDemandModel', 'GravityDemandModel, ScriptedDemandModel'],
      ['MapLoader', 'JsonMapLoader, CsvMapLoader'],
      ['RoadGeometryProvider', 'OsrmRoadGeometryProvider'],
    ],
  },
  {
    name: 'Inheritance and polymorphism',
    idea: 'A shared abstract base holds the behaviour every subtype needs; subtypes differ in data, not control flow.',
    detail: 'Vehicle is abstract and defines the trip lifecycle. Its four subclasses override physical profile (length, width, acceleration), driver profile, and right-of-way priority. The dynamics engine iterates a list of Vehicle and never asks what kind each one is — a motorcycle filters through a gap because its profile permits it, not because of a type check. TrafficEvent follows the same shape for congestion, accidents, breakdowns, and closures.',
    anchors: [
      ['Vehicle (abstract)', 'Car, Motorcycle, Bus, EmergencyVehicle'],
      ['TrafficEvent (abstract)', 'CongestionEvent, AccidentEvent, BreakdownEvent, RoadClosureEvent'],
    ],
  },
  {
    name: 'Observer',
    idea: 'Publishers announce; subscribers react; neither holds a reference to the other’s implementation.',
    detail: 'TickProcessor publishes event transitions to TrafficEventBus. Anything can subscribe. Note the honest detail: the production app does not subscribe DynamicReroutingListener — adaptive rerouting is coordinated separately, because publishing that an event exists and deciding whether a driver reroutes are related inputs, not the same mechanism.',
    anchors: [['TrafficEventBus', 'TrafficEventListener, DynamicReroutingListener']],
  },
  {
    name: 'Interface segregation',
    idea: 'A collaborator sees only the part of a type it needs.',
    detail: 'The dynamics layer needs a road’s lane count and width, not its name, id, congestion level, or geometry. RoadLayout is that narrow view, which is why two very different sources — a full Road record and a bare road class — can both satisfy it. TrafficParticipant and JunctionEnvironment do the same job elsewhere.',
    anchors: [
      ['RoadLayout', 'RoadDataRoadLayout, RoadClassRoadLayout'],
      ['TrafficParticipant', 'Vehicle'],
      ['JunctionEnvironment', 'TrafficState'],
    ],
  },
  {
    name: 'Aggregate and encapsulation',
    idea: 'One object owns a cluster of state, and every mutation goes through it.',
    detail: 'RoadNetwork owns intersections, roads, and adjacency; TrafficSimulator owns vehicles, events, and the clock. Their collection getters return copied containers, so a caller cannot add or remove entries behind the aggregate’s back. This is what makes it safe for the scheduled playback thread and API commands to share one simulator.',
    anchors: [['RoadNetwork', 'intersections, roads, adjacency indexes'], ['TrafficSimulator', 'vehicles, events, clock']],
  },
  {
    name: 'Value object / immutable snapshot',
    idea: 'State handed across a boundary is copied, not shared.',
    detail: 'SimulationFrame is the renderer contract: value records and copied collections, captured under the simulator lock. SimulationSnapshot is the weaker cousin — it copies its lists but still points at mutable domain objects, so it is fine for immediately building a REST response and explicitly not the replay contract. Knowing which is which is the difference between a consistent replay and a torn frame.',
    anchors: [['SimulationFrame', 'the renderer and replay contract'], ['SimulationSnapshot', 'REST materialisation only']],
  },
]

/** The tick, in the order it happens. */
const TICK_STEPS = [
  ['Clock advances', 'SimulationClock adds simulationSecondsPerTick to elapsed seconds and increments the trace tick index.'],
  ['Demand releases', 'Any seeded plan whose departure time has arrived becomes a live Vehicle on its first leg.'],
  ['Events transition', 'Events that start or resolve this tick change state. TrafficEventCoordinator rebuilds each affected road from a clean baseline plus every still-active overlay, so overlapping events cannot corrupt road state.'],
  ['Navigation reconsiders', 'AdaptiveNavigationCoordinator asks which drivers know about a problem yet, given information access and latency, and whether rerouting saves enough to be worth it.'],
  ['Dynamics sub-steps', 'VehicleDynamicsEngine splits the tick into sub-steps of at most one simulated second. Each sub-step captures an immutable TrafficState, arbitrates stop lines and signal phases, plans all car-following and lane changes, then commits them together — planning before committing is what keeps the result independent of vehicle list order.'],
  ['Poses resolve', 'Progress along a road plus a lane offset becomes latitude, longitude, and heading. LateralMotionModel slides a lane change across several sub-steps instead of teleporting sideways.'],
  ['Arrivals archive', 'A vehicle that reaches its destination becomes a CompletedTrip and leaves the active list; its statistics survive it.'],
  ['Frame captured', 'SimulationFrameHistory records an immutable frame at exact transitions and sampled physics boundaries, then the SSE stream pushes it to every connected renderer.'],
]

const ALGORITHMS = [
  {
    name: 'BFS',
    where: 'BfsPathFindingStrategy',
    what: 'Fewest roads, ignoring cost. Correct only when every edge is equal, which on a real map it never is — so it is the baseline that shows what the weighted searches buy you.',
    cost: 'O(V + E)',
  },
  {
    name: 'Dijkstra',
    where: 'DijkstraPathFindingStrategy',
    what: 'Cheapest path by travel cost, expanding uniformly in every direction. Always optimal for non-negative costs, which congestion weighting preserves.',
    cost: 'O((V + E) log V)',
  },
  {
    name: 'A*',
    where: 'AStarPathFindingStrategy',
    what: 'Dijkstra plus a straight-line-distance heuristic that pulls the search towards the destination. The heuristic never overestimates, so it stays optimal while expanding far fewer nodes.',
    cost: 'O((V + E) log V), fewer expansions',
  },
  {
    name: 'Krauss car-following',
    where: 'KraussCarFollowingModel',
    what: 'Safe speed given the gap to the leader, its speed, and reaction time. A vehicle never chooses a speed from which it could not stop if the leader braked hard — collisions come from events, not from the model failing.',
    cost: 'O(1) per vehicle per sub-step',
  },
  {
    name: 'MOBIL lane changing',
    where: 'MobilLaneChangeModel, FilteringLaneChangeModel',
    what: 'Change lane when the gain to you outweighs the politeness-weighted loss to the driver you cut in front of, and only if their required braking stays safe. Motorcycles use a low-incentive variant that accepts gaps a car would refuse, without weakening the safety term.',
    cost: 'O(1) per candidate lane',
  },
  {
    name: 'Gravity demand',
    where: 'GravityDemandModel',
    what: 'Trip volume between two places scales with their occupancy and attraction and decays with distance. Seeded, so the same scenario produces the same 38,518 plans every run.',
    cost: 'O(P²) over places, precomputed once',
  },
]

/* ------------------------------------------------------------------- render */

const escape = (value) => String(value)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')

function main() {
  const mainFiles = tracked('src/main/java/**/*.java')
  const testFiles = tracked('src/test/java/**/*.java')
  const types = mainFiles.map(parseJava).filter(Boolean)

  const byPackage = new Map()
  for (const type of types) {
    if (!byPackage.has(type.package)) byPackage.set(type.package, [])
    byPackage.get(type.package).push(type)
  }
  for (const list of byPackage.values()) list.sort((a, b) => a.name.localeCompare(b.name))

  const countLines = (files) => files.reduce(
    (total, file) => total + readFileSync(join(ROOT, file), 'utf8').split(/\r?\n/).length, 0)

  const stats = {
    types: types.length,
    packages: byPackage.size,
    mainLines: countLines(mainFiles),
    testFiles: testFiles.length,
    testLines: countLines(testFiles),
    frontendFiles: tracked('frontend/src/**').length,
    documented: types.filter((type) => type.summary).length,
  }

  const html = page({ stats, byPackage, types })
  writeFileSync(OUTPUT, html)
  console.log(`PRESENTATION.html  ${stats.types} types, ${stats.packages} packages, ${(html.length / 1024).toFixed(0)} KB`)
  console.log('open it directly in a browser — no server, no network')
}

function classCard(type) {
  const badges = [type.isAbstract ? 'abstract' : null, type.kind]
    .filter(Boolean)
    .map((badge) => `<span class="badge b-${badge}">${badge}</span>`)
    .join('')
  const relations = []
  if (type.extends) relations.push(`extends <code>${escape(type.extends)}</code>`)
  if (type.implements.length) {
    relations.push(`implements ${type.implements.map((item) => `<code>${escape(item)}</code>`).join(', ')}`)
  }
  const methods = type.methods.slice(0, 40).map((method) =>
    `<li><code><span class="m-name">${escape(method.name)}</span>(<span class="m-args">${escape(method.parameters)}</span>) <span class="m-ret">&rarr; ${escape(method.returns)}</span></code></li>`).join('')
  const more = type.methods.length > 40
    ? `<li class="muted">&hellip; and ${type.methods.length - 40} more</li>` : ''

  // Members come first: for a record or an enum they are the point of the type,
  // and its handful of methods are incidental.
  const memberLabel = type.kind === 'record' ? 'component' : 'constant'
  const members = type.members.map((member) =>
    `<li><code class="mem">${escape(member)}</code></li>`).join('')

  const counts = []
  if (type.members.length) {
    counts.push(`${type.members.length} ${memberLabel}${type.members.length === 1 ? '' : 's'}`)
  }
  counts.push(`${type.methods.length} method${type.methods.length === 1 ? '' : 's'}`)
  counts.push(`${type.lines} lines`)

  return `<article class="card" data-name="${escape(type.name.toLowerCase())}" data-package="${escape(type.package)}" data-kind="${type.kind}">
  <header><h4>${escape(type.name)}</h4>${badges}</header>
  ${relations.length ? `<p class="rel">${relations.join(' &middot; ')}</p>` : ''}
  <p class="sum">${type.summary ? escape(type.summary) : '<span class="muted">No class comment.</span>'}</p>
  <details><summary>${counts.join(' &middot; ')}</summary>
    ${members ? `<ul class="methods members">${members}</ul>` : ''}
    <ul class="methods">${methods}${more}</ul>
    <p class="path"><code>${escape(type.path)}</code></p>
  </details>
</article>`
}

function page({ stats, byPackage, types }) {
  const layerSections = LAYERS.map((layer) => {
    const packages = Object.entries(layer.packages)
      .filter(([name]) => byPackage.has(name))
      .map(([name, blurb]) => {
        const list = byPackage.get(name)
        return `<section class="pkg">
        <h3 id="pkg-${name.replace(/\W/g, '-')}">${escape(name)} <span class="count">${list.length}</span></h3>
        <p class="pkg-blurb">${blurb}</p>
        <div class="cards">${list.map(classCard).join('\n')}</div>
      </section>`
      }).join('\n')
    return `<section class="layer" id="layer-${layer.id}">
    <h2>${layer.title}</h2>
    <p class="lead">${layer.blurb}</p>
    ${packages}
  </section>`
  }).join('\n')

  const orphans = [...byPackage.keys()]
    .filter((name) => !LAYERS.some((layer) => name in layer.packages))
  const orphanSection = orphans.length === 0 ? '' : `<section class="layer" id="layer-other">
    <h2>Other packages</h2>
    <p class="lead">Present in the source but not yet described in <code>tools/build_presentation.mjs</code>. Add a blurb there and rerun.</p>
    ${orphans.map((name) => `<section class="pkg"><h3>${escape(name)} <span class="count">${byPackage.get(name).length}</span></h3>
      <div class="cards">${byPackage.get(name).map(classCard).join('\n')}</div></section>`).join('\n')}
  </section>`

  const navLayers = LAYERS.map((layer) =>
    `<a href="#layer-${layer.id}">${layer.title}</a>`).join('')

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Traffic Event System &mdash; project walkthrough</title>
<style>
:root{
  --bg:#0e1116; --panel:#161b22; --panel2:#1c2230; --line:#2a3242;
  --ink:#e6edf3; --dim:#9aa7b8; --faint:#6b7789;
  --accent:#4c9aff; --accent2:#7ee787; --warn:#f0a04b; --pink:#ff7b9c;
  --mono:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
}
@media (prefers-color-scheme: light){
  :root{ --bg:#f6f8fa; --panel:#fff; --panel2:#f0f3f7; --line:#d8dee6;
    --ink:#1c2128; --dim:#57606a; --faint:#8c959f; --accent:#0969da; --accent2:#1a7f37; }
}
*{box-sizing:border-box}
html{scroll-behavior:smooth; scroll-padding-top:1rem}
body{margin:0;background:var(--bg);color:var(--ink);
  font:16px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI",Inter,Roboto,sans-serif;
  display:grid;grid-template-columns:260px 1fr}
a{color:var(--accent);text-decoration:none}
a:hover{text-decoration:underline}
code{font-family:var(--mono);font-size:.86em}

nav{position:sticky;top:0;height:100vh;overflow-y:auto;padding:1.5rem 1rem;
  background:var(--panel);border-right:1px solid var(--line)}
nav h1{font-size:.95rem;margin:0 0 .2rem;letter-spacing:-.01em}
nav .sub{font-size:.75rem;color:var(--faint);margin:0 0 1.2rem}
nav a{display:block;padding:.32rem .6rem;border-radius:6px;color:var(--dim);font-size:.85rem}
nav a:hover{background:var(--panel2);color:var(--ink);text-decoration:none}
nav .grp{font-size:.68rem;text-transform:uppercase;letter-spacing:.09em;
  color:var(--faint);margin:1.1rem 0 .3rem;padding-left:.6rem}

main{padding:2.5rem 3rem 6rem;max-width:1100px;min-width:0}
h2{font-size:1.7rem;margin:3.5rem 0 .4rem;letter-spacing:-.02em;
  padding-top:.6rem;border-top:1px solid var(--line)}
section.layer:first-of-type h2{border-top:none}
h3{font-size:1.12rem;margin:2rem 0 .3rem;font-family:var(--mono);color:var(--accent2)}
h4{margin:0;font-size:.95rem;font-family:var(--mono)}
p.lead{color:var(--dim);margin:.3rem 0 1rem;max-width:72ch}
p.pkg-blurb{color:var(--dim);font-size:.9rem;margin:.2rem 0 .9rem;max-width:76ch}
.muted{color:var(--faint)}

header.hero{padding:1rem 0 0}
header.hero h1{font-size:2.6rem;margin:0 0 .3rem;letter-spacing:-.03em;line-height:1.1}
header.hero .tag{color:var(--dim);font-size:1.05rem;max-width:68ch;margin:0 0 1.5rem}

.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));
  gap:.7rem;margin:1.5rem 0 2rem}
.stat{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:.8rem .9rem}
.stat b{display:block;font-size:1.5rem;letter-spacing:-.02em}
.stat span{font-size:.72rem;color:var(--faint);text-transform:uppercase;letter-spacing:.06em}

.flow{display:flex;flex-direction:column;gap:.5rem;margin:1.2rem 0 2rem;counter-reset:s}
.flow li{list-style:none;background:var(--panel);border:1px solid var(--line);
  border-left:3px solid var(--accent);border-radius:8px;padding:.7rem .9rem .7rem 3rem;position:relative}
.flow li::before{counter-increment:s;content:counter(s);position:absolute;left:.9rem;top:.7rem;
  width:1.4rem;height:1.4rem;border-radius:50%;background:var(--accent);color:#fff;
  font-size:.72rem;font-weight:700;display:grid;place-items:center}
.flow b{display:block;font-size:.92rem}
.flow p{margin:.15rem 0 0;font-size:.87rem;color:var(--dim)}

.pat{background:var(--panel);border:1px solid var(--line);border-radius:10px;
  padding:1.1rem 1.2rem;margin:.9rem 0}
.pat h3{margin:0;color:var(--pink);font-family:inherit;font-size:1.05rem}
.pat .idea{font-size:.88rem;color:var(--faint);font-style:italic;margin:.1rem 0 .6rem}
.pat p.d{margin:0 0 .8rem;font-size:.92rem;color:var(--dim);max-width:76ch}
.anchor{display:flex;gap:.5rem;align-items:baseline;flex-wrap:wrap;
  font-family:var(--mono);font-size:.8rem;padding:.28rem 0;border-top:1px dashed var(--line)}
.anchor b{color:var(--accent2);font-weight:600}
.anchor span{color:var(--faint)}

table{border-collapse:collapse;width:100%;margin:1rem 0;font-size:.88rem}
th,td{text-align:left;padding:.55rem .7rem;border-bottom:1px solid var(--line);vertical-align:top}
th{color:var(--faint);font-size:.72rem;text-transform:uppercase;letter-spacing:.06em;font-weight:600}
td code{color:var(--accent2)}

.tools{position:sticky;top:0;z-index:5;background:var(--bg);
  padding:.9rem 0;margin:1rem 0 0;border-bottom:1px solid var(--line);
  display:flex;gap:.6rem;flex-wrap:wrap;align-items:center}
.tools input{flex:1;min-width:220px;background:var(--panel);border:1px solid var(--line);
  color:var(--ink);border-radius:8px;padding:.5rem .8rem;font-size:.9rem;font-family:var(--mono)}
.tools input:focus{outline:2px solid var(--accent);outline-offset:-1px}
.tools button{background:var(--panel);border:1px solid var(--line);color:var(--dim);
  border-radius:999px;padding:.35rem .8rem;font-size:.78rem;cursor:pointer}
.tools button:hover{color:var(--ink);border-color:var(--accent)}
.tools button[aria-pressed=true]{background:var(--accent);border-color:var(--accent);color:#fff}
.tools .hits{font-size:.78rem;color:var(--faint);font-family:var(--mono)}

.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(310px,1fr));gap:.7rem}
.card{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:.8rem .9rem}
.card header{display:flex;align-items:center;gap:.4rem;flex-wrap:wrap;margin-bottom:.3rem}
.badge{font-size:.62rem;text-transform:uppercase;letter-spacing:.06em;
  padding:.1rem .4rem;border-radius:4px;border:1px solid var(--line);color:var(--faint)}
.b-interface{color:var(--accent);border-color:var(--accent)}
.b-record{color:var(--accent2);border-color:var(--accent2)}
.b-enum{color:var(--warn);border-color:var(--warn)}
.b-abstract{color:var(--pink);border-color:var(--pink)}
.card .rel{margin:.1rem 0 .35rem;font-size:.76rem;color:var(--faint)}
.card .sum{margin:0 0 .5rem;font-size:.85rem;color:var(--dim)}
.card details summary{cursor:pointer;font-size:.75rem;color:var(--faint);
  font-family:var(--mono);list-style:none}
.card details summary::-webkit-details-marker{display:none}
.card details summary::before{content:'\\25b8 ';color:var(--accent)}
.card details[open] summary::before{content:'\\25be '}
.methods{margin:.5rem 0 0;padding:0 0 0 .2rem;list-style:none;
  max-height:280px;overflow-y:auto}
.methods li{font-size:.74rem;padding:.1rem 0;border-bottom:1px solid var(--line)}
.m-name{color:var(--ink)} .m-args{color:var(--faint)} .m-ret{color:var(--accent2)}
.members{max-height:none;margin-bottom:.3rem}
.members li{border-bottom:none;padding:.05rem 0}
.mem{color:var(--warn)}
.card .path{margin:.5rem 0 0;font-size:.68rem;color:var(--faint);word-break:break-all}
.card.hide{display:none}
.pkg.hide,.layer.hide{display:none}

.note{background:var(--panel2);border-left:3px solid var(--warn);
  border-radius:0 8px 8px 0;padding:.8rem 1rem;margin:1.2rem 0;font-size:.89rem;color:var(--dim)}
.note b{color:var(--ink)}

@media (max-width:900px){
  body{grid-template-columns:1fr}
  nav{position:static;height:auto;border-right:none;border-bottom:1px solid var(--line)}
  main{padding:1.5rem 1.2rem 4rem}
}
</style>
</head>
<body>

<nav>
  <h1>Traffic Event System</h1>
  <p class="sub">Project walkthrough</p>
  <a href="#top">Overview</a>
  <a href="#arch">Architecture</a>
  <a href="#tick">One tick, end to end</a>
  <a href="#oop">OOP design</a>
  <a href="#algos">Algorithms</a>
  <a href="#frontend">Frontend</a>
  <a href="#testing">Testing</a>
  <a href="#run">Running it</a>
  <p class="grp">Class index</p>
  <a href="#index">Search all types</a>
  ${navLayers}
</nav>

<main>
<header class="hero" id="top">
  <h1>Traffic Event System</h1>
  <p class="tag">A microscopic urban traffic simulator for District 1, Ho Chi Minh City.
  Java 17 and Javalin own the model; React, Leaflet, and PixiJS draw it. Everything on
  this page is generated from the source, so it is as current as the last time
  <code>tools/build_presentation.mjs</code> was run.</p>

  <div class="stats">
    <div class="stat"><b>${stats.types}</b><span>Java types</span></div>
    <div class="stat"><b>${stats.packages}</b><span>packages</span></div>
    <div class="stat"><b>${stats.mainLines.toLocaleString('en')}</b><span>lines of model</span></div>
    <div class="stat"><b>${stats.testFiles}</b><span>test classes</span></div>
    <div class="stat"><b>${stats.testLines.toLocaleString('en')}</b><span>lines of test</span></div>
    <div class="stat"><b>${stats.frontendFiles}</b><span>frontend files</span></div>
  </div>

  <p class="lead">The system loads a real OpenStreetMap-derived road graph of 730 directed
  roads over 560 physical segments, generates a seeded 24-hour demand of 38,518 trip plans,
  and advances every vehicle second by second: car-following, lane changes, signal phases,
  stop lines, accidents, closures, and rerouting. It is an educational microscopic model,
  not a calibrated forecast, and the documentation is careful about which is which.</p>
</header>

<section id="arch">
  <h2>Architecture</h2>
  <p class="lead">Layered, with the dependency arrow pointing one way: the API knows about
  services, services know about the domain, the domain knows about nothing. That is the
  rule that makes the domain testable without a server.</p>

  <svg viewBox="0 0 720 400" style="width:100%;height:auto;margin:1rem 0 1.5rem" role="img"
       aria-label="Layer diagram: browser to API to services to domain, with routing, simulation and persistence alongside">
    <defs>
      <marker id="a" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
        <path d="M0,0 L7,3 L0,6 z" fill="currentColor"/>
      </marker>
      <style>
        .bx{fill:var(--panel);stroke:var(--line)}
        .t{fill:var(--ink);font:600 13px var(--mono)}
        .s{fill:var(--dim);font:11px sans-serif}
        .ln{stroke:var(--accent);fill:none;color:var(--accent)}
      </style>
    </defs>

    <rect class="bx" x="20" y="14" width="300" height="54" rx="8"/>
    <text class="t" x="36" y="38">Browser</text>
    <text class="s" x="36" y="56">React &middot; Leaflet macro map &middot; PixiJS physical view</text>

    <rect class="bx" x="400" y="14" width="300" height="54" rx="8"/>
    <text class="t" x="416" y="38">data/</text>
    <text class="s" x="416" y="56">maps &middot; demand assumptions &middot; scenarios</text>

    <rect class="bx" x="20" y="102" width="300" height="54" rx="8"/>
    <text class="t" x="36" y="126">api</text>
    <text class="s" x="36" y="144">Javalin controllers &middot; DTOs &middot; SSE stream</text>

    <rect class="bx" x="400" y="102" width="300" height="54" rx="8"/>
    <text class="t" x="416" y="126">persistence</text>
    <text class="s" x="416" y="144">MapLoader &middot; scenario loader &middot; export</text>

    <rect class="bx" x="20" y="190" width="300" height="54" rx="8"/>
    <text class="t" x="36" y="214">service</text>
    <text class="s" x="36" y="232">use-case boundary, one impl each</text>

    <rect class="bx" x="400" y="190" width="300" height="54" rx="8"/>
    <text class="t" x="416" y="214">routing</text>
    <text class="s" x="416" y="232">BFS &middot; Dijkstra &middot; A* behind one interface</text>

    <rect class="bx" x="20" y="278" width="300" height="54" rx="8"/>
    <text class="t" x="36" y="302">simulation</text>
    <text class="s" x="36" y="320">clock &middot; tick &middot; frames &middot; deadlock &middot; trace</text>

    <rect class="bx" x="400" y="278" width="300" height="54" rx="8"/>
    <text class="t" x="416" y="302">observer &middot; experiment</text>
    <text class="s" x="416" y="320">extension seams</text>

    <rect class="bx" x="20" y="352" width="680" height="34" rx="8"/>
    <text class="t" x="36" y="374">domain</text>
    <text class="s" x="130" y="374">network &middot; vehicle &middot; dynamics &middot; navigation &middot; demand &middot; event &middot; signal &middot; scene &middot; routing &middot; statistics</text>

    <path class="ln" d="M170,68 L170,102" marker-end="url(#a)"/>
    <path class="ln" d="M170,156 L170,190" marker-end="url(#a)"/>
    <path class="ln" d="M170,244 L170,278" marker-end="url(#a)"/>
    <path class="ln" d="M170,332 L170,352" marker-end="url(#a)"/>
    <path class="ln" d="M550,68 L550,102" marker-end="url(#a)"/>
    <path class="ln" d="M400,217 L320,217" marker-end="url(#a)"/>
    <path class="ln" d="M550,156 L550,352" marker-end="url(#a)"/>
  </svg>

  <div class="note"><b>The one thing to understand about ownership:</b> the browser never
  advances the simulation. It sends commands and it draws frames. The server owns the clock,
  which is why pausing in one tab pauses it in every tab, and why a replay scrubber can
  exist at all.</div>

  <h3 style="font-family:inherit;color:var(--ink)">Threads and failure</h3>
  <p class="lead">Continuous playback runs on one scheduled executor thread; API commands
  enter through the same synchronized <code>TrafficSimulator</code> boundary. If a scheduled
  tick throws, <code>SimulationServiceImpl</code> logs it at SEVERE, moves the simulator to
  PAUSED, cancels future ticks, and publishes the resulting snapshot. A failed tick cannot
  leave a nominal playback loop quietly advancing over broken state.</p>
</section>

<section id="tick">
  <h2>One tick, end to end</h2>
  <p class="lead">Everything the simulator does happens in this order, in
  <code>TickProcessor</code>. If you only read one thing about the runtime, read this.</p>
  <ol class="flow">
    ${TICK_STEPS.map(([title, body]) => `<li><b>${title}</b><p>${body}</p></li>`).join('\n    ')}
  </ol>
  <div class="note"><b>Why plan-then-commit matters:</b> if each vehicle moved as soon as it
  was examined, the vehicle at index 0 would see a different world than the vehicle at index
  40, and the result would depend on list order. Capturing an immutable
  <code>TrafficState</code>, planning every move against it, and committing them together
  is what makes a run reproducible from its seed.</div>
</section>

<section id="oop">
  <h2>Object-oriented design</h2>
  <p class="lead">Every pattern below is anchored to types you can open. Nothing here is a
  pattern applied for its own sake &mdash; each one is load-bearing, and the section says
  what would break without it.</p>
  ${PATTERNS.map((pattern) => `<div class="pat">
    <h3>${pattern.name}</h3>
    <p class="idea">${pattern.idea}</p>
    <p class="d">${pattern.detail}</p>
    ${pattern.anchors.map(([base, kids]) =>
      `<div class="anchor"><b>${escape(base)}</b><span>&rarr; ${escape(kids)}</span></div>`).join('\n    ')}
  </div>`).join('\n  ')}
</section>

<section id="algos">
  <h2>Algorithms</h2>
  <p class="lead">Six algorithms carry the simulation. Three choose routes; two move
  vehicles; one decides who travels at all.</p>
  <table>
    <thead><tr><th>Algorithm</th><th>Implemented in</th><th>What it does</th><th>Cost</th></tr></thead>
    <tbody>
    ${ALGORITHMS.map((algorithm) => `<tr>
      <td><b>${algorithm.name}</b></td>
      <td><code>${algorithm.where}</code></td>
      <td>${algorithm.what}</td>
      <td><code>${escape(algorithm.cost)}</code></td>
    </tr>`).join('\n    ')}
    </tbody>
  </table>
  <div class="note"><b>Congestion-aware cost.</b>
  <code>travelCost = distance / speedLimit &times; (1 + congestionLevel + vehicleDensity)</code>.
  A blocked or closed road costs infinity. A road merely at storage capacity stays routable
  and takes a soft queue delay &mdash; physical admission still refuses entry until space
  appears, so routing optimism never becomes a physics violation.</div>
</section>

<section id="frontend">
  <h2>Frontend</h2>
  <p class="lead">Two renderers over one authoritative state, and neither of them advances
  anything.</p>
  <table>
    <thead><tr><th>Part</th><th>Role</th></tr></thead>
    <tbody>
      <tr><td><code>api/trafficAPI.js</code></td><td>The whole REST and SSE client. One place knows the wire format.</td></tr>
      <tr><td><code>hooks/useTrafficDashboard.js</code></td><td>Dashboard state machine: what is loaded, what is streaming, what the user asked for.</td></tr>
      <tr><td><code>components/TrafficMap.jsx</code></td><td>React-Leaflet macro map: road pressure, incidents, routes, network editing.</td></tr>
      <tr><td><code>physical/PhysicalScene.jsx</code></td><td>PixiJS lane-level view. Owns the app, polls by camera viewport, follows the vehicle.</td></tr>
      <tr><td><code>physical/sceneTransform.js</code></td><td>Metres to world units. The one conversion, so nothing else guesses.</td></tr>
      <tr><td><code>physical/interpolate.js</code></td><td>Smoothing between two authoritative frames &mdash; motion the model did not have to compute.</td></tr>
      <tr><td><code>components/TimelineScrubber.jsx</code></td><td>LIVE/HISTORY scrubbing over the bounded frame history.</td></tr>
      <tr><td><code>spawnPolicies.js</code></td><td>Request planning only. It picks a plausible origin and destination and then sends the ordinary <code>POST /api/vehicles</code>; it is not a second simulation authority.</td></tr>
    </tbody>
  </table>
  <div class="note"><b>Performance, honestly stated.</b> PixiJS draws through WebGL, so
  sprites and lane geometry are GPU-rendered. The simulation itself is branch-heavy stateful
  Java on the CPU and stays there: moving it to GPU compute would be a large redesign and
  would weaken deterministic replay at this map size. The controls that do exist instead cap
  rendering at 60 FPS, reuse static geometry, stream moving state by camera, suspend the
  hidden macro stream while the physical view is open, bound history, and offer explicit
  playback paces.</div>
</section>

<section id="testing">
  <h2>Testing</h2>
  <p class="lead">${stats.testFiles} test classes, ${stats.testLines.toLocaleString('en')} lines
  &mdash; about ${Math.round(stats.testLines / stats.mainLines * 100)}% of the size of the
  model itself, covering domain, routing, persistence, services, controllers, and simulation.</p>
  <table>
    <thead><tr><th>Suite</th><th>What it protects</th></tr></thead>
    <tbody>
      <tr><td>domain/vehicle/dynamics</td><td>The microscopic model: safe following distance, lane-change acceptance, stop-line compliance, order-independence.</td></tr>
      <tr><td>routing</td><td>Each strategy finds the same optimal cost where it should, and A* never returns worse than Dijkstra.</td></tr>
      <tr><td>persistence</td><td>Malformed maps are rejected at load, not halfway through a simulation.</td></tr>
      <tr><td>api/controller</td><td>Controllers against fake services &mdash; status codes and payload shape without booting a simulation.</td></tr>
      <tr><td>simulation</td><td>Clock arithmetic, tick ordering, frame capture, deadlock detection.</td></tr>
    </tbody>
  </table>
  <p class="lead">Beyond unit tests there is a <b>physical-view lab</b>: sixteen tiny
  purpose-built maps under <code>data/scenarios/test/</code>, each isolating one geometric
  situation &mdash; a u-turn, a sharp corner, a four-way junction, a stop line, motorcycle
  filtering, an immobilising accident. <code>tools/probe_test_cases.mjs</code> drives all
  sixteen headlessly and reports geometrically impossible motion: headings that jump more
  than 45&deg; between frames, vehicles covering more than 60 m in one tick, and vehicles an
  event stopped that moved anyway. The eye cannot catch a one-frame heading flick; the probe
  can.</p>
  <pre style="background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:.9rem;overflow-x:auto;font-size:.82rem"><code>mvn test                          # backend
cd frontend &amp;&amp; npm test           # frontend
node tools/probe_test_cases.mjs   # physical-view lab, needs the server on :7070</code></pre>
</section>

<section id="run">
  <h2>Running it</h2>
  <p class="lead">One script installs locked frontend dependencies, builds the frontend,
  runs a clean Maven package, and starts the app on port 7070. It stops on the first failure
  rather than launching something half-built.</p>
  <pre style="background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:.9rem;overflow-x:auto;font-size:.82rem"><code>./run-demo.sh        # Linux, macOS
.\\run-demo.ps1       # Windows PowerShell</code></pre>
  <p class="lead">Then open <code>http://localhost:7070</code>. The basemap needs internet
  for OpenStreetMap tiles; the simulation, routing, events, and vehicle positions all run
  locally. Run from the repository root &mdash; <code>data/maps</code>,
  <code>data/scenarios</code>, and <code>data/demand</code> are deliberately external
  runtime inputs rather than embedded in the JAR, so a scenario can be edited without a
  rebuild.</p>
</section>

<section id="index">
  <h2>Class index</h2>
  <p class="lead">All ${stats.types} backend types, grouped by layer and package.
  ${stats.documented} of them carry a class comment, shown here verbatim. Expand a card for
  its public methods and file path.</p>

  <div class="tools">
    <input id="q" type="search" placeholder="filter by class name&hellip;" autocomplete="off">
    <button data-kind="all" aria-pressed="true">all</button>
    <button data-kind="class">class</button>
    <button data-kind="interface">interface</button>
    <button data-kind="record">record</button>
    <button data-kind="enum">enum</button>
    <span class="hits" id="hits"></span>
  </div>
</section>

${layerSections}
${orphanSection}

<p class="muted" style="margin-top:4rem;font-size:.8rem;border-top:1px solid var(--line);padding-top:1rem">
Generated by <code>tools/build_presentation.mjs</code> from ${stats.types} source files.
Map data &copy; OpenStreetMap contributors, ODbL.
</p>

</main>

<script>
(function () {
  var input = document.getElementById('q')
  var hits = document.getElementById('hits')
  var buttons = Array.prototype.slice.call(document.querySelectorAll('.tools button'))
  var cards = Array.prototype.slice.call(document.querySelectorAll('.card'))
  var kind = 'all'

  function apply() {
    var needle = input.value.trim().toLowerCase()
    var shown = 0
    cards.forEach(function (card) {
      var okName = !needle
        || card.dataset.name.indexOf(needle) !== -1
        || card.dataset.package.toLowerCase().indexOf(needle) !== -1
      var okKind = kind === 'all' || card.dataset.kind === kind
      var visible = okName && okKind
      card.classList.toggle('hide', !visible)
      if (visible) shown++
    })
    // Collapse a package or layer once nothing inside it survives the filter,
    // so the page does not become a list of empty headings.
    document.querySelectorAll('.pkg').forEach(function (pkg) {
      pkg.classList.toggle('hide', pkg.querySelectorAll('.card:not(.hide)').length === 0)
    })
    document.querySelectorAll('.layer').forEach(function (layer) {
      layer.classList.toggle('hide', layer.querySelectorAll('.card:not(.hide)').length === 0)
    })
    hits.textContent = shown + ' / ' + cards.length
  }

  input.addEventListener('input', apply)
  buttons.forEach(function (button) {
    button.addEventListener('click', function () {
      kind = button.dataset.kind
      buttons.forEach(function (other) {
        other.setAttribute('aria-pressed', String(other === button))
      })
      apply()
    })
  })
  // '/' focuses the filter, the one shortcut worth having on a reference page.
  document.addEventListener('keydown', function (event) {
    if (event.key === '/' && document.activeElement !== input) {
      event.preventDefault()
      input.focus()
    }
  })
  apply()
})()
</script>
</body>
</html>`
}

main()
