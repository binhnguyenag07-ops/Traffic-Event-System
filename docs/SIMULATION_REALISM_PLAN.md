# Simulation Realism Plan

The physical view renders, but it renders a fixed patch of a map that carries
no buildings, no lane data, and no signals, populated by vehicles that drive
between randomly chosen junctions at no particular time of day. This plan closes
that gap.

Seven problems, verified in the code rather than assumed, grouped into three
workstreams. `PHYSICAL_VIEW_PLAN.md` holds the two-view design.

**Status.** Workstream A and B are built: the zoom desync is fixed, the view
follows the camera and refetches as it moves, signals and buildings render, and
the map now carries real lane counts and 1162 buildings. Workstream C — the
clock and the demand model — is still to do, and is the only part that remains
blocked on nothing.

## Verified findings

| # | Problem | Root cause, as checked |
|---|---|---|
| 1 | View shows one fixed patch | `PhysicalScene` fetches once for the `target` passed at open time and never re-fetches; there is no pan, only wheel zoom |
| 2 | Layers desync while zooming | `onWheel` sets `store.lastTransform = null`, which makes the guard `if (store.lastTransform && …)` fail, so the static layer is *not* redrawn; vehicles redraw every tick at the new scale. They resync only when the next poll (≤600 ms) sets `dirtyStatic` |
| 3 | No sprites | Vehicles are `Graphics` polygons; no texture is loaded anywhere |
| 4 | Scene reads as black | Palette is three near-identical greys: background `0x161a20`, pavement `0x232730`, surface `0x2b2f38`. No signals reach the view at all — `PhysicalScene` and `PhysicalViewDto` contain **zero** signal fields, though `SimulationFrame.SignalState` already carries `greenIncomingRoadIds` and `phaseRemainingSeconds` |
| 5 | Generator behind the backend | `tools/generate_osm_map.py` queries only `way["highway"]`. `district1_network.json` has top-level keys `intersections`, `roads` — no `buildings`; and road records carry no `laneCount`, `laneWidthMetres`, `name` or `physicalRoadId` |
| 6 | No origin–destination model | Vehicles spawn between random intersections (`spawnPolicies.js`, boundary-to-boundary). `createVehicleBetweenBuildings` exists from PR #52 but is **unreachable on the real map**: it needs buildings, and there are none |
| 7 | No time of day | Nothing in `src/main/java` mentions an hour, a clock, or a departure time. The simulation knows only `elapsedSeconds` |

Two consequences worth stating plainly:

- **Lane geometry on the real map is inferred, not measured.** With no
  `laneCount` in the data, `RoadDataRoadLayout` falls back to `RoadClass`
  defaults. Every lane the physical view draws for District 1 is a guess.
- **The building-trip feature is dead code in production.** It is tested and
  merged, and on the only real map it can never run.

## The dependency that orders everything

**Workstream C cannot start before B.** "40% of residents leave for work at 7am"
requires residents, workplaces, and a map that says which building is which.
There are currently zero buildings. Every demand-model decision is blocked on
the generator.

A and B are independent of each other and can proceed in parallel.

---

## Workstream A — The renderer

### A1. Fix the zoom desync — *bug, do first*

Stop baking zoom and pan into the drawing. Draw the geometry **once, in world
metres**, into a PixiJS `Container`; express zoom and pan as
`container.scale` and `container.position`.

Pixi's scene graph then transforms every layer together, so:

- desync becomes impossible by construction, not by remembering to redraw;
- zoom costs no redraw at all, so it stays smooth at any scene size;
- the vehicle layer keeps updating at its own cadence without touching geometry.

`sceneTransform.js` loses its `zoom`/`pan` parameters — they become the
container transform — and keeps only metres → world-units and the vehicle-box
corners. Its unit tests carry over.

### A2. Follow the user instead of a fixed patch

- Add drag-to-pan (pointer events on the canvas, moving `container.position`).
- Track the viewport centre in world metres. When it drifts past ~40% of the
  loaded radius, re-fetch centred on the new position.
- Fetch geometry with a margin wider than the viewport so panning does not
  stutter at the edge.
- **Split the two cadences.** Geometry changes only when the region changes;
  vehicles change every frame. Today they share one response. Add:

  ```text
  GET /api/simulation/physical-view/vehicles?lat=&lon=&radiusMetres=
  ```

  returning the vehicle array alone in the same projection, for the fast poll.
  `/physical-view` stays as-is for first load and region change. `API_CONTRACT.md`
  already flags this split as the intended next step.

### A3. Traffic signals

`SignalState` already has everything needed and none of it is plumbed through.

- Add `SceneSignal` to `domain/scene`: junction id, and per controlled approach
  the road id, whether it is green, and seconds remaining.
- `PhysicalSceneAssembler` takes the frame's signals like it takes vehicles.
- `PhysicalViewDto` projects the signal head positions to local metres — place
  each head at its approach's stop line.
- Draw a red/amber/green head per controlled approach, with a countdown once
  zoomed past a threshold.

This is also what makes the scene *legible*: right now a stopped queue has no
visible cause.

### A4. Make it look like a street

**Step 1 — palette and markings. No assets, most of the win.**

| Element | Now | Proposed |
|---|---|---|
| Ground | — | `0x11141a` base, distinct from road |
| Road surface | `0x2b2f38` | `0x3a4048`, clearly lighter than ground |
| Junction pavement | `0x232730` | `0x333942`, distinct from road |
| Lane divider | 1 px `0x3c4250` | dashed `0xc8ccd4`, scaled with zoom |
| Road edge | — | solid `0x8a9099` at the carriageway boundary |
| Stop line | `0xe8eaed` | unchanged, it already reads |

Dashed centre lines and solid edge lines are what make tarmac look like a road;
they cost one extra stroke pass each.

**Step 2 — sprites.** Load a texture atlas through Pixi `Assets`, replace the
polygon boxes with top-down sprites for car / motorcycle / bus / emergency.

Honest note: **the bottleneck here is artwork, not code.** The sprite swap is
perhaps an hour once an atlas exists. Step 1 gets most of the visual gain for
none of that cost, so step 2 is deliberately last.

Buildings and land use would fill the empty space around the roads — but that
needs Workstream B first.

---

## Workstream B — Regenerate the map to match the backend

### The pipeline decision, reversed

`PHYSICAL_VIEW_PLAN.md` argues for a pinned `.osm.pbf` + osmium + Pyrosm
pipeline. **I now recommend extending the existing Overpass query instead**, and
the reversal is deliberate:

- the project targets one small, fixed bounding box, not arbitrary cities;
- osmium + pyrosm + geopandas is a heavy dependency chain and a large binary
  download for a course project;
- extending the query adds buildings, lanes and signals in a much smaller diff,
  with no new dependencies, and the existing response cache already blunts the
  Overpass reliability concern.

The PBF route stays documented as the answer *if* offline reproducibility
becomes a requirement. It is not one today.

### What the generator must emit

Query three feature sets over the same bounds:

```text
way ["highway"~"<road classes>"];        // already queried
way ["building"];                        // new
node["highway"="traffic_signals"];       // new
```

Then normalise:

- **Lanes.** Parse `lanes`, `lanes:forward`, `lanes:backward`, `width`,
  `maxspeed`, `oneway`, `name`. Emit `laneCount`, `laneWidthMetres`, `name`,
  `physicalRoadId` — the fields `Road` accepts and the map never supplies.
- **Provenance.** Record `laneCountSource: "OSM" | "INFERRED"` per road. OSM
  lane coverage is patchy; a guessed lane count must not be presented as a
  measured one. This is the same discipline `FilteringRules` follows.
- **Buildings.** Footprint → centroid for position; map the OSM tag to
  `BuildingType`:

  | OSM tag | `BuildingType` |
  |---|---|
  | `house`, `apartments`, `residential`, `dormitory` | `RESIDENTIAL` |
  | `office`, `commercial`, `retail`, `shop` | `OFFICE` / `COMMERCIAL` |
  | `school`, `university`, `college`, `kindergarten` | `SCHOOL` |
  | `hospital`, `clinic` | `HOSPITAL` |
  | `parking`, `garage` | `PARKING` |
  | `industrial`, `warehouse` | `INDUSTRIAL` |
  | anything else | `OTHER` |

- **Access points.** The fiddly part. Snap each building to the nearest
  intersection within a distance cap, recording the driveway length. A building
  with no road within the cap is **dropped, not invented** — `JsonMapLoader`
  validates that every access point references a real intersection, and a
  fabricated one would put vehicles on roads that do not serve it.
- **Signals.** `traffic_signals` nodes mark which junctions genuinely have
  lights, instead of the current assumption that every junction of degree ≥ 3
  does. This needs an additive `signalised` flag on `Intersection`.

### Risk to name up front

Regenerating the map **may invalidate `EXPERIMENT_RESULTS.md`** if road ids
change. Keep the existing `W<wayId>-<segment>` id scheme stable so existing
scenarios keep loading; if a break is unavoidable, regenerate the baseline in
the same commit and say so in the doc rather than leaving stale numbers.

---

## Workstream C — Who travels, from where, and when

### C1. A clock of day

Add a `SimulationClock` to `simulation/`: a start time of day, a compression
factor, and `elapsedSeconds → timeOfDay`. Expose `timeOfDaySeconds` on the
status and the frame so the UI can show `07:15` and the demand model can ask
what hour it is.

Small and additive; nothing existing needs to change to accommodate it.

### C2. Demand from buildings

A `TripDemandModel` in a new `domain/demand` package, answering: at this hour,
which trips begin, from which building, to which building, in what vehicle.

- **Population per building.** Footprint area × a per-type density, so a tower
  generates more trips than a shophouse. Falls back to a per-type constant when
  the footprint is missing.
- **Purposes.** `HOME_TO_WORK`, `WORK_TO_HOME`, `HOME_TO_SCHOOL`,
  `SCHOOL_TO_HOME`, `OTHER`. Each has an hourly departure curve — the classic
  double peak, work outbound around 07:00–08:00 and inbound around 17:00–18:00,
  school an hour earlier, a midday errand bump.
- **Destination choice.** A gravity model: attraction weighted by the
  destination's size and type for that purpose, divided by a function of travel
  distance. Nearby offices win over distant ones, which is what actually happens.
- **Vehicle type by trip.** Short trips lean motorcycle, long trips lean car,
  school runs lean motorcycle and bus — the District 1 mix, and it exercises the
  filtering model already built.
- **Determinism.** Seeded, so a scenario replays identically. The project
  already depends on this for experiments and `DriverProfile.variedFor`.

Then a `DemandDrivenSpawner` in `simulation/` asks the model each tick what
departs now and calls `VehicleService.createVehicleBetweenBuildings` — the
method PR #52 added and which has never been reachable on a real map. This is
what closes that loop.

**Constants must be sourced or labelled.** It is easy to produce a plausible
peak from invented numbers. Every density and curve gets either a citation or an
explicit "assumption" comment, the same rule applied to the filtering limits.

---

## Sequencing

1. **A1** — the zoom bug. Small, isolated, and the worst of the UX complaints.
2. **A3 + A4 step 1** — signals and palette. Immediate, visible, no data needed.
3. **B** — the generator. Unblocks everything downstream.
4. **A2** — region streaming. Independent; slots in wherever convenient.
5. **C1 → C2** — clock, then demand.
6. **A4 step 2** — sprites, once artwork exists.

**If time runs short, stop after B.** A map carrying real lanes, buildings and
signals is a deliverable in its own right, it makes the merged building-routing
feature actually reachable, and it turns every inferred lane in the physical
view into a measured one.

## Risks

- **Access-point snapping** is the hardest part of B. Needs a distance cap and a
  drop-on-failure rule, or the map will claim buildings are served by roads that
  do not reach them.
- **Demand realism versus defensibility.** Numbers that look right but come from
  nowhere are worse than none. Label assumptions.
- **Map regeneration invalidating the experiment baseline** — see above.
- **Container refactor and coordinates.** A1 moves the zoom into the scene
  graph. This *reduces* the number of places coordinates are converted, but the
  metres → world-units boundary must stay the only one on the draw path.

## Out of scope

- Pedestrians and cyclists — not in the domain model.
- Public-transport timetables; buses keep their current routing.
- Multi-day simulation, weather, seasonal demand.
- Live OSM synchronisation; the map stays a generated, committed artefact.
