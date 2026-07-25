# Macro and Physical Views

Design record for the two-renderer split, and what of it is built.

Outstanding work on the physical view is in `SIMULATION_REALISM_PLAN.md`; this
document is the design it rests on.

## Two-view boundary

| View | Renderer | Responsibility |
|---|---|---|
| Macro | React Leaflet | Browse the full network, inspect pressure/incidents/routes, view lightweight vehicle dots, and select an area |
| Physical | PixiJS/WebGL canvas | Animate lane-level vehicles, stop lines, traffic lights, and junction movements |

At a high Leaflet zoom (18 or above) an **Open physical view** action appears
for the selected junction, or the map centre when nothing is selected. Leaflet is
never replaced automatically on a zoom event: an explicit transition preserves
the user's selection and avoids a disorienting renderer switch.

Both views consume the same `{timelineId, frameId}` rather than maintaining
independent clocks. The macro view uses aggregate road state; the physical view
requests only the geometry and moving objects within its region.

## The interpolation contract

The backend records one immutable `SimulationFrame` after every microscopic
sub-step (at most one simulated second) and at every exact event boundary. It
carries `tick`, `elapsedSeconds`, vehicle poses, event states, road states, and
traffic-signal phase state. The current frame goes out over SSE; retained frames
are queried through:

```text
GET /api/simulation/trace/frames
GET /api/simulation/trace/frames/at?elapsedSeconds=...
```

The physical renderer draws the exact frame the shared cursor selects. It may
interpolate **pixels** between two adjacent LIVE frames for smoothness, but
interpolation must never create authoritative event, signal, road, or vehicle
state. On pause or a history scrub it draws the selected frame exactly.

That rule is what makes synchronisation enforceable even though two renderers
cannot paint pixels in the same nanosecond. It is enforced in
`frontend/src/physical/interpolate.js`: a departed vehicle is dropped rather
than coasted onward, a newly appeared one is drawn at its own position, and
everything but position and heading comes from the newer frame unchanged.

History retains 900 timestamps in memory. Initialize, reset, scenario or map
replacement, and any in-place network edit start a new timeline; the client
discards a selected history frame when its timeline changes or the frame leaves
the retained window.

## Lateral position

A vehicle carries a signed offset in metres from the road centreline and crosses
towards a new lane at a bounded lateral speed, so one part-way through a lane
change reports a position it genuinely occupies. Sub-lane occupancy landed after
that, so a motorcycle can ride the gap between two lanes and report an offset
that is really where it is.

The renderer therefore places vehicles from `lateralOffsetMetres` and **must not**
derive lateral position from the lane index. See `MOTORCYCLE_FILTERING_PLAN.md`
for the model behind it.

## What is built

Backend, in `domain/scene` and served by `GET /api/simulation/physical-view`
(contract in `API_CONTRACT.md`):

- `LaneGeometryBuilder` derives each lane's centreline by offsetting the road
  shape along its local normal, using the same
  `RoadLayout.laneCentreOffsetMetres` the simulation places vehicles with, so
  what is drawn and what is simulated cannot disagree.
- Stop lines across each approach, quadratic turning curves between lanes across
  a junction, and a junction pavement polygon.
- `PhysicalSceneAssembler` filters roads, junctions and vehicles against one
  radius.
- `PhysicalViewDto` performs the single geographic → local-metre projection, so
  the browser never sees a latitude.

Frontend, in `frontend/src/physical` on PixiJS v8:

- `sceneTransform.js` — metres ↔ pixels, and the corners of a vehicle box for a
  heading. Pure and unit-tested.
- `interpolate.js` — the contract above. Pure and unit-tested.
- `layers/drawScene.js` — draw order: junction pavement, road surface, lane
  markings and stop lines, then vehicles.
- `PhysicalScene.jsx` — owns the PixiJS application, polls the endpoint, draws
  on a ticker.

The package lives in `domain/scene` rather than `domain/network/geometry`
because the two tools it builds on, `RoadLayout` and `PolylineGeometry`, are
both in `domain/vehicle`, and `domain/network` is otherwise independent of
`domain/vehicle`. A geometry package under `network` reaching up into `vehicle`
would invert the layering; a sibling consuming both does not.

## Known gaps

The renderer draws a fixed region, has no sprites, no signal heads, and a
palette too dark to read. The map it draws carries no buildings and no measured
lane counts, so every lane shown for District 1 is inferred from `RoadClass`
defaults. All of that, and the demand model that would give the vehicles
somewhere real to go, is planned in `SIMULATION_REALISM_PLAN.md`.
