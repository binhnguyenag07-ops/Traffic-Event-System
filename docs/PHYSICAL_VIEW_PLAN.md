# Macro and Physical Views

Current design and implementation record for the two renderers.

## Responsibility boundary

| View | Renderer | Responsibility |
|---|---|---|
| Macro | React Leaflet, canvas-preferred layers | Browse the network, inspect aggregate road state, events and routes |
| Physical | PixiJS 8/WebGL | Draw lane geometry and animate vehicle poses between backend frames |

The backend is authoritative for the simulation. The Pixi renderer does **not**
run a second collision, lane-change or junction model. It may only choose a
visually smooth path between two poses already emitted by the backend.

Both views consume the same `timelineId` and `frameId`. Initialize, reset,
scenario activation, map replacement and topology edits start a new timeline,
which prevents state from one network being painted over another.

WebGL means Pixi normally uses the GPU for rasterization, sprite transforms,
and composition when browser hardware acceleration is available. The Java
simulation remains CPU-owned; it is a deterministic, branch-heavy graph and
multi-agent state machine, not a large uniform numeric kernel. Rewriting that
model for GPU compute would not fix a bad turn path or coordinate contract and
is not justified for this network size.

## Data cadence

The Physical View uses two endpoints:

```text
GET /api/simulation/physical-view?lat=&lon=&radiusMetres=&fullMap=true&includeState=false
GET /api/simulation/physical-view/state?lat=&lon=&radiusMetres=
```

The first request loads the current network's static lanes, stop lines,
junctions, buildings, land cover and connectors once. `includeState=false`
prevents that full-map response from serializing the district-wide live fleet.
An immediate second request, and every later poll, carries only vehicles,
signals and frame metadata within the current camera radius.

`PhysicalScene.jsx` keeps every layer in one world-metre `Container`; pan and
zoom change only that container's transform. Static geometry is rebuilt only
when the timeline changes. `App.jsx` lazy-loads the Physical View, so PixiJS
does not inflate or initialize the normal Leaflet dashboard bundle. Pixi is
capped at 60 FPS so a 120/144 Hz monitor
does not redraw the full map unnecessarily. State polling runs at 250 ms while
frames advance. It only starts backing off after state has remained unchanged
for longer than the current backend playback interval (with a one-second
minimum), then caps the delay at 1000 ms; an updated frame immediately restores
250 ms. This grace period covers the default 1000 ms clock as well as the
250–2000 ms speed controls, avoiding aliasing and a dropped road transition at
a turn.

The dashboard uses the SSE frame as its live head. It refreshes the timeline
index at most once per wall second and aggregate statistics at most once every
two seconds, instead of issuing both requests for every 4 Hz frame. Backend
frame history retains 900 timestamps, tick traces 5,000 ticks, and flow history
100,000 samples.

While Physical View is open, the hidden macro map, command rail, Fleet and
timeline are unmounted, and the full-frame SSE plus snapshot fallback are
closed. Physical state supplies its own authoritative elapsed time. A
one-second `GET /api/simulation/status` poll carries only status/clock/pace
metadata so PAUSED, READY, scheduler failure and custom pace remain visible
without parsing the full fleet behind the camera.

Geometry and optional artwork load in parallel. Every asset slot has a bounded
load time and falls back to a generated shape independently, so a missing or
stalled texture cannot leave the scene waiting forever.

Each state request covers the visible camera diagonal plus 20% overscan, with a
120 m minimum. Returned local coordinates are rebased onto the retained static
origin. A second screen-bounds pass omits vehicle sprites and trails outside the
actual viewport, so panning remains smooth without reducing backend demand or
inventing a separate client simulation.

The District 1 scene currently contains 389 intersections, 730 directed roads,
1,318 lane centrelines and 1,162 buildings. This is small enough for a
single retained static scene. A materially larger map should tile its static
geometry rather than continuing to load the whole city.

Playback has three macro paces: real time (0.25 simulated seconds every 250 ms,
1×), balanced (2.5/250 ms, about 10×), and 24-hour overview (30/250 ms, about
120×). Physical View is always a live camera at the first cadence. Opening it
does not seek, reset or replay the day; it changes only the reversible tick
cadence and returns any history inspection to the live head. Closing it restores
the previous macro pace.

## Interpolation contract

`frontend/src/physical/interpolate.js` enforces these rules:

1. At interpolation fraction `0` and `1`, return the exact source and target
   backend poses, including heading.
2. On one road, sample the directed lane centreline by road progress and retain
   the backend's lateral endpoint offsets. This keeps curved-road movement on
   the road and preserves gradual motorcycle filtering/lane changes.
3. Across two roads, use only the connector matching the exact
   `(fromRoad, fromLane, toRoad, toLane)` transition.
4. Traverse the ordered remainder of the incoming lane, the complete connector,
   then the ordered prefix of the outgoing lane. Do not trim a connector by
   Euclidean proximity: on a short outgoing segment the target can be nearer
   the connector's start, which previously collapsed a turn into a chord.
5. Reject connectors with an endpoint cusp or an excessive internal turn and
   fall back to endpoint interpolation. Heading always takes the shortest
   smooth turn between authoritative headings. At the otherwise ambiguous
   180-degree U-turn, the connector's signed curvature chooses left versus
   right so the sprite rotates with its path.
6. During `DEPARTING_ORIGIN` or `ARRIVING_DESTINATION`, interpolate only the
   local chord between two authoritative access-link poses. Reconstructing the
   complete building path at each poll would make a midway vehicle reverse.
7. A departed vehicle is removed; a newly appeared vehicle is drawn at its
   first authoritative pose. The renderer never invents a post-arrival ghost.

Lane, connector and polyline indexes are cached for a retained scene. Static
connector guides are intentionally not painted, and motorcycle trails are
batched into one graphics stroke per frame.

## Backend geometry and lane continuity

`LaneGeometryBuilder` derives lane centrelines with the same `RoadLayout` used
to place vehicles. For each legal road transition it publishes the same
deterministic lane continuation used by `RoadOccupancy`: keep the incoming lane
index, clamping only when the outgoing road has fewer lanes.

Turning connectors use cubic Hermite candidates with adaptive handle lengths.
Every accepted curve must:

- start and end within 45 degrees of its lane tangents;
- contain no zero-length segment;
- contain no sampled vertex turn over 45 degrees.

If the continuation lane has no entry space, a vehicle waits. It is not moved
instantaneously into a different empty lane inside the junction; a real lateral
change can happen gradually after entry.

## Position and building-access contracts

Dynamics `roadProgress` is the vehicle's **front-bumper** fraction. The backend
uses it for leader gaps and caps that front at the painted stop line on red.
`VehiclePoseResolver` subtracts half the body length once and publishes
latitude/longitude and physical `x`/`y` at the **body centre**. Pixi centres the
sprite directly on that point; applying another frontend setback would put the
sprite half a vehicle behind the authoritative queue.

A building trip is also backend motion, not a cosmetic line:

```text
DEPARTING_ORIGIN      building centre → selected access node
ARRIVING_DESTINATION selected access node → building centre
```

`Vehicle` advances both at 15 km/h, accumulates trip time/distance, and occupies
no road until/after the access node. `journeyPhase` and resolved place/access
metadata are carried in every physical state. Return trips depart again from
the building they actually reached.

## Audited map interpretation

The 730 directed roads represent 560 physical segments. Of those, 390 are
one-way: 380 have OSM `oneway=yes` and 10 are roundabouts. There are 168
one-way segments with at least three lanes; 155 carry direct OSM lane tags and
13 use conservative inference/defaults. All 439 source ways matched current
direction, lane, and name tags after the committed corrections. Thus a
three-lane one-way road is usually a tagged multi-lane carriageway, not evidence
that a reverse edge is missing.

Eleven ways have `oneway:motorcycle=no`, which the current all-vehicle
direction graph cannot express. Adding a generic reverse edge would wrongly
legalize it for cars and buses, so this remains an explicit schema limitation.

## Honest limitations

- The backend currently crosses the junction boundary within a simulation
  sub-step and does not publish a `connectorProgress` or junction-occupancy
  duration. The connector traversal is therefore a **render-only interpolation**
  between two authoritative road poses. Junction permission, collision checks
  and road occupancy still belong to the backend.
- A missing or rejected connector uses a straight endpoint fallback. This is
  bounded and cannot spin, but it can visually cut a corner. Such a fallback is
  a geometry-data defect to fix, not permission for frontend free movement.
- District 1 lane counts are a mixture of OSM tags and generator defaults. The
  audit records aggregate provenance, but the current JSON schema does not
  persist it per road.
- Signal heads show the simulation's generated signal plan. They are not a
  survey of every real signal controller or phase in District 1.
- Pedestrians, bicycles, kerbside parking and public-transport dwell operations
  are outside the current model.
- Static geometry is still retained district-wide. Moving state and sprite
  submission are viewport-bounded; a city-scale map would also need tiled
  static geometry.

Adding timed junction occupancy would be a legitimate future model upgrade, but
it must be designed together with reservation release, deadlock detection and
collision tests. It is not safe to add as a renderer-only patch.
