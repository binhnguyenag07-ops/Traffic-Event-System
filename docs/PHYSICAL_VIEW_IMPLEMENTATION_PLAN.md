# Physical View: Implementation Plan

How the Leaflet macro map hands over to a lane-level PixiJS renderer when the
user zooms in, and what has to exist for that to work.

This is the build plan. `PHYSICAL_VIEW_PLAN.md` holds the original design
reasoning and the two-view boundary; this document does not repeat it.

## Decisions taken

| Question | Choice |
|---|---|
| Lane geometry | Built server-side, served by a new `/api/simulation/physical-view` endpoint |
| Renderer | PixiJS over WebGL |
| Transition | Explicit at high zoom, not automatic on the first zoom event |

Serving geometry rather than deriving it in the browser keeps one definition of
where a lane is. The renderer never re-implements `RoadLayout`, and a scenario
simulated on one layout cannot be drawn using another — the same class of bug
already found and fixed in `VehiclePoseResolver`.

## What already exists

More than half the work is done. This is not a from-scratch feature.

| Need | State |
|---|---|
| Continuous lateral position per vehicle | **done** — `lateralOffsetMetres`, real metres |
| Vehicle pose in world coordinates | **done** — `VehiclePose` carries lat/lon/heading |
| Authoritative frame stream | **done** — SSE at `/api/simulation/stream` |
| Frame history and scrubbing | **done** — `/trace/frames`, `TimelineScrubber.jsx` |
| Zoom tracking in the map | **done** — `MapZoomTracker` in `TrafficMap.jsx` |
| Road centreline geometry | **done** — `Road.getGeometry()` |
| Lane count and width per road | **done** — `getLaneCount()`, `getLaneWidthMetres()` |
| Signal phase state per junction | **done** — `SimulationFrame.SignalState` |
| Polyline maths | **done** — `PolylineGeometry`, incl. `offsetMetres` |
| **Lane centrelines as drawable polylines** | missing |
| **Stop lines and lane-to-lane connectors** | missing |
| **Region query by lat/lon/radius** | missing |
| **The renderer itself** | missing |

## Ownership

This feature crosses every member's package, which is why the sequencing below
puts the parts you own first and the shared parts behind an agreed contract.

| Area | Owner | This plan's need |
|---|---|---|
| `domain/network/**` | Student A | Read-only. Lane geometry is derived, not stored |
| `api/**` | Student D | One new controller, one new DTO package |
| `frontend/**` | Student D | New component tree, one hook change |
| `domain/vehicle/**`, `simulation/**` | You | Pose and frame data — already sufficient |

**Get agreement on the DTO shape before phase 3.** The JSON contract is the only
thing both sides must agree on; everything else can proceed in parallel once it
is fixed. Write it into `API_CONTRACT.md` as the record of that agreement.

## Phase 1 — Lane geometry in the domain — DONE

New package `domain/scene`, read-only over the existing aggregate. Nothing here
mutates `Road` or `Intersection`.

> **Placement changed from the original sketch.** This was first drafted as
> `domain/network/geometry`. But the two tools that build a lane centreline —
> `RoadLayout.laneCentreOffsetMetres` and `PolylineGeometry` — both live in
> `domain/vehicle`, and `domain/network` is otherwise wholly independent of
> `domain/vehicle`. A geometry package *under* `network` that reached up into
> `vehicle` would invert the layering. `domain/scene` sits alongside the two as
> a consumer of both, so the dependencies only ever point one way.

- **`LaneGeometry`** — one lane of one directed road: its centreline as a
  `List<GeoPoint>`, its index, its width. Built by offsetting the road's
  centreline sideways by `RoadLayout.laneCentreOffsetMetres`, the same call the
  pose resolver uses.
- **`StopLine`** — the transverse segment at a road's exit, spanning the
  carriageway, positioned a vehicle-length back from the junction centre.
- **`LaneConnector`** — a curve joining an incoming lane to an outgoing lane
  across a junction. A quadratic Bézier with its control point at the junction
  centre is enough; the ends are already tangent to both lanes.
- **`JunctionGeometry`** — the pavement polygon of a junction, built as the
  convex hull of the stop-line endpoints of every road meeting there.
- **`LaneGeometryBuilder`** — assembles the above for a set of roads.

Tests: a straight two-lane road yields two parallel centrelines the right
distance apart; a one-lane road yields one on the centreline; a curved road's
lane centrelines stay the right distance apart along their whole length; a
junction of four roads yields four stop lines and the expected connector count.

**This phase is independently valuable** and is the natural place to stop if the
deadline bites: it makes lane geometry testable without any renderer.

## Phase 2 — Region query — DONE

`PhysicalSceneAssembler` (in `domain/scene`, not `service/`, so it stays a pure
domain step testable without the simulation). Given a centre and radius it
returns the lanes, stop lines, connectors, junctions and vehicles in range,
filtering roads, junctions and vehicles against the same radius. Vehicles are
passed in rather than fetched, so the assembler does not depend on how the
simulation is driven; the controller resolves the current frame into them.

Original wording, kept for the record:

- **`PhysicalSceneService`** — given a centre and radius in metres, returns the
  roads intersecting that circle, their lane geometry, junctions, stop lines,
  connectors, and the vehicles currently on those roads.
- Reuse `PolylineGeometry.lengthMetres` and a simple bounding-box pre-filter;
  734 roads does not warrant a spatial index yet. Say so in a comment so the
  next person does not assume it was overlooked.

Tests: a radius covering one junction returns only its roads; a radius covering
nothing returns empty rather than throwing; vehicle filtering matches the road
filtering.

## Phase 3 — API contract — DONE

`GET /api/simulation/physical-view` is live, served by `PhysicalViewController`
and wired through `ApiRouteRegistry` as an optional controller (the same
nullable pattern the scenario and experiment controllers use, so existing
constructors keep working). `PhysicalViewDto.of` performs the one geographic →
local-metre projection. The contract is recorded in `API_CONTRACT.md`.

Original sketch of the response, kept for the record:

`GET /api/simulation/physical-view?lat=&lon=&radiusMetres=`

Response, in **local metre coordinates** with the query point as origin, so the
renderer never touches latitude and longitude:

```jsonc
{
  "origin": { "latitude": 10.7769, "longitude": 106.7009 },
  "radiusMetres": 150,
  "timelineId": "...", "frameId": 412,      // ties the scene to a frame
  "lanes": [
    { "roadId": "W123-0", "laneIndex": 0, "widthMetres": 3.5,
      "centreline": [[x, y], ...], "oneway": true }
  ],
  "junctions": [ { "id": "N1", "pavement": [[x, y], ...] } ],
  "stopLines": [ { "roadId": "W123-0", "from": [x, y], "to": [x, y] } ],
  "connectors": [ { "fromRoadId": "..", "fromLane": 0,
                    "toRoadId": "..", "toLane": 1, "curve": [[x, y], ...] } ],
  "signals": [ { "junctionId": "N1", "approaches":
                 [ { "roadId": "..", "green": true, "secondsRemaining": 7.5 } ] } ],
  "vehicles": [ { "id": "V-1", "type": "MOTORCYCLE", "x": 12.3, "y": -4.1,
                  "headingDegrees": 87.2, "speedMps": 6.1,
                  "lengthMetres": 2.1, "widthMetres": 0.75,
                  "laneIndex": 1, "lateralOffsetMetres": 0.0 } ]
}
```

Static geometry and moving vehicles are in one response for the first cut. Split
them once profiling says to: geometry is fetched once per region, vehicles every
frame. Note that in the code rather than leaving it implied.

Add the endpoint to `API_CONTRACT.md` in the same commit.

## Phases 4-6 — Renderer, transition, filtering — DONE

Built in `frontend/src/physical/` on `pixi.js` v8, verified end-to-end against the
running backend on the District 1 map: lane centrelines, junction pavement and
stop lines render, the scene polls live and interpolates between frames, scroll
zooms, and a real vehicle projects into the scene at the origin.

- `sceneTransform.js` — metres ↔ pixels and the vehicle-box corners. Pure,
  unit-tested (origin at centre, north up, east right, round-trip).
- `interpolate.js` — vehicle positions between two LIVE frames; a departed
  vehicle is dropped, a fresh one drawn at its own position, exact frame at
  t=0/1. Pure, unit-tested.
- `layers/drawScene.js` — the draw order: pavement, road surface, markings and
  stop lines, then vehicles, with a trail behind moving motorcycles.
- `PhysicalScene.jsx` — owns the PixiJS application, polls the endpoint, and
  runs the interpolate-and-draw ticker. Shows the frame it is given and never
  fetches a frame of its own, so it stays on the backend's clock.
- Transition: `TrafficMap` shows **Open physical view** at zoom ≥ 18, centred on
  the selected junction or the map centre; `App` renders the overlay.

The interpolation-honesty and coordinate-boundary rules from the sketch below
are enforced in code and called out in each module's header comment.

Original sketch, kept for the record:

## Phase 4 — Renderer skeleton

`frontend/src/physical/`, added to `package.json` as `pixi.js`.

- **`PhysicalScene.jsx`** — owns the PixiJS `Application`, sizes it to its
  container, tears it down on unmount. Nothing else touches Pixi lifecycle.
- **`layers/`** — one module per draw layer, in this order: ground, pavement and
  markings, stop lines and signal heads, vehicles, selection overlay.
- **`sceneTransform.js`** — metres to screen pixels, and pan/zoom within the
  physical view. Pure functions, unit-testable without Pixi.
- **`useVehicleInterpolation.js`** — smooths vehicle positions between
  authoritative frames.

**The interpolation rule matters and is easy to get wrong.** Interpolate pixels
between two adjacent LIVE frames only. On pause, on any history scrub, and on
the frame the timeline cursor names, draw that frame exactly. Interpolation must
never invent vehicle, signal, event, or road state — it is a visual smoothing of
positions and nothing else. Put that sentence in the module's javadoc.

## Phase 5 — Transition from the macro view

`TrafficMap.jsx` already tracks zoom via `MapZoomTracker`, so the hook exists.

- At zoom ≥ 18 with something selected, show an **Open physical view** control.
- Opening it passes `{ lat, lon, radiusMetres }` from the selection, plus the
  current `{ timelineId, frameId }`.
- The two views share one cursor: `useTrafficDashboard` already owns it, so lift
  nothing — pass it down.
- Closing returns to the macro map at the same zoom with the same selection and
  the same frame. A user who scrubbed back in the physical view stays scrubbed
  back on return.

Do not swap renderers automatically on zoom. An explicit action preserves the
selection and avoids a disorienting jump, and it makes the feature demonstrable
in one click.

## Phase 6 — Filtering made visible

The payoff for the sub-lane work. A motorcycle riding between two lanes now
reports an offset it genuinely occupies, so drawing it there is honest.

- Draw vehicles at their real `lateralOffsetMetres`, never at the lane centre
  derived from `laneIndex`.
- A short trail behind filtering riders makes the manoeuvre legible at a glance,
  which is what will read best in a demo.

## Sequencing and risk

Phases 1–2 are pure domain work with no dependency on anyone else and no
renderer. Phase 3 is the agreement point. Phases 4–6 are frontend.

If time runs short, **stop after phase 3**: a tested lane-geometry model and a
documented endpoint is a defensible deliverable on its own, and the renderer
becomes a stated next step rather than a half-finished screen.

Two risks worth naming now:

- **Coordinate confusion.** Three systems are in play: lat/lon, local metres,
  and screen pixels. Convert at exactly two boundaries — the endpoint converts
  lat/lon to metres, `sceneTransform` converts metres to pixels — and never in
  between.
- **Two clocks.** The macro and physical views must consume the same
  `{timelineId, frameId}`. If the physical view ever fetches its own frame, they
  will drift and the bug will look like a rendering fault rather than a state
  one.

## Out of scope

- Texture atlases and sprite artwork — coloured shapes first, art later.
- Region-scoped SSE. The client filters its own vehicle list until the network
  outgrows that.
- Buildings, land use, and water. `data/district1_network.json` carries no
  buildings at all, so there is nothing to draw; see the note in
  `PHYSICAL_VIEW_PLAN.md` about the OSM pipeline.
- Pedestrians and cyclists — not in the domain model.
