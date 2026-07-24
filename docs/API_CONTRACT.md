# API Contract

All endpoints use JSON unless noted. Errors use `{"error":"message"}`. The
simulation still has an integer `tick` for deterministic trace indexing, but
physical time and event scheduling use `elapsedSeconds`.

## Network and scenarios

| Method | Endpoint | Result |
|---|---|---|
| GET | `/api/network/intersections` | Geographic intersections |
| GET | `/api/network/roads` | Directed roads, conditions, lane geometry, and polyline geometry |
| POST | `/api/network/intersections` | Add a junction while playback is not running |
| POST | `/api/network/roads` | Add one directed road or a bidirectional pair |
| PUT | `/api/network/roads/{id}/condition` | Change status and congestion |
| GET | `/api/network/intersections/{id}/roads` | Incoming and outgoing roads |
| GET | `/api/network/export` | Download current network JSON |
| GET | `/api/scenarios` | Bundled scenarios |
| POST | `/api/scenarios/{id}/activate` | Reset and activate a bundled scenario |
| POST | `/api/scenarios/import` | Multipart JSON import using field `map` |

Road responses and exported JSON include `name`, `physicalRoadId`, `laneCount`,
`laneWidthMetres`, `roadWidthMetres`, `roadClass`, and `geometry`. Import and
road-creation payloads may omit lane values; defaults are derived from class:
PRIMARY 3 × 3.5 m, SECONDARY/TERTIARY 2 × 3.25 m, and other classes 1 × 3.0 m.
Two directions of the same street share a `physicalRoadId`.

Imports are limited to 2 MB, 600 intersections, and 1,200 roads. A successful
import atomically replaces the network only after validation.

## Vehicles, events, and signals

| Method | Endpoint | Result |
|---|---|---|
| GET | `/api/vehicles` | Current vehicles |
| POST | `/api/vehicles` | Create CAR, MOTORCYCLE, BUS, or EMERGENCY |
| DELETE | `/api/vehicles/{id}` | Remove a vehicle |
| GET | `/api/events` | All scheduled, active, and resolved events |
| POST | `/api/events` | Create an event |
| GET | `/api/events/active` | Active events |
| GET | `/api/events/road/{roadId}` | Events for one road |
| PUT | `/api/events/{id}/status` | Explicitly activate or resolve an event |
| DELETE | `/api/events/{id}` | Remove an event and release its effects |
| GET | `/api/simulation/signals` | Junction signal plans and phases |

Event types are `CONGESTION`, `ACCIDENT`, `BREAKDOWN`, and `ROAD_CLOSURE`.
`TRAFFIC_LIGHT_DELAY` no longer exists; `TrafficSignal` is a junction-owned,
cyclic controller.

Example positioned breakdown:

```json
{
  "type": "BREAKDOWN",
  "affectedRoadId": "R11",
  "startTimeSeconds": 25.5,
  "durationSeconds": 90,
  "affectedVehicleId": "bus-7",
  "positionAlongRoad": 0.72,
  "blockedLaneIndexes": [0]
}
```

`positionAlongRoad` is in `[0,1]`; lane indexes are zero-based and validated
against the affected road. A breakdown requires an existing vehicle. Empty
`blockedLaneIndexes` means all lanes for a physical obstruction. Legacy
`startTick`/`durationTicks` names are accepted only as migration inputs;
responses emit seconds fields.

Vehicle snapshots include `laneIndex`, `lateralOffsetMetres`, resolved
`latitude`/`longitude`, `headingDegrees`, speed, trip metrics, and route state.
`STOPPED` vehicles remain physical lane occupants.

`MOTORCYCLE` uses a 2.1 m × 0.75 m physical profile, compact following gaps,
and `MOTORCYCLE_FILTERING_MOBIL`. This is a safety-preserving adjacent-lane
filtering approximation: it accepts useful compact gaps that do not fit a car,
but it does not yet occupy a continuous position between two lane centre lines.

## Routing, simulation, and diagnostics

| Method | Endpoint | Result |
|---|---|---|
| POST | `/api/routes` | One BFS, Dijkstra, or A* route |
| POST | `/api/routes/compare` | All compatible algorithms |
| GET | `/api/routing/algorithms` | Algorithm names and preferences |
| POST | `/api/simulation/initialize` | Vehicles and scheduled events |
| POST | `/api/simulation/start` | Start continuous playback |
| POST | `/api/simulation/pause` | Pause playback |
| POST | `/api/simulation/resume` | Resume playback |
| POST | `/api/simulation/tick` | One manual logical tick |
| PUT | `/api/simulation/parameters` | Logical seconds per tick and playback interval |
| GET | `/api/simulation/snapshot` | Current snapshot |
| GET | `/api/simulation/stream` | Server-Sent authoritative renderer frames |
| GET | `/api/simulation/trace/frames` | Retained frame index for the current timeline |
| GET | `/api/simulation/trace/frames/at?elapsedSeconds={s}` | Latest retained frame at or before `s` |
| GET | `/api/simulation/trace/flow` | Per-road density/speed/flow samples |
| GET | `/api/simulation/trace/fundamental` | Density-bin averages for a fundamental diagram |
| GET | `/api/simulation/trace/roads/{roadId}/time-space` | Per-vehicle road trajectories |
| GET | `/api/statistics` | Traffic and algorithm metrics |
| GET | `/api/experiments/latest` | Checked-in experiment baseline |
| POST | `/api/experiments/run` | Fresh deterministic experiment run |

A current snapshot contains `timelineId`, `frameId`, `tick`, `elapsedSeconds`,
`vehicles`, `trafficEvents`, `roadStates`, and `trafficSignals`. Flow density is vehicles per kilometre per
lane (`densityVehPerKmPerLane`); speed is metres per second (`meanSpeedMps`);
flow is vehicles per hour per lane (`flowVehPerHourPerLane`). Event boundaries
inside a logical tick are applied at their exact simulated second.

Arrived vehicles stay in snapshots for at least five simulated seconds, then
move to completed-trip history. Initialize, reset, scenario activation, and map
replacement clear traces and completed-trip history.

### Authoritative renderer-frame replay

The SSE stream and historical frame lookup use one immutable frame contract:

```text
timelineId, frameId, tick, elapsedSeconds, status,
vehicles, trafficEvents, roadStates, trafficSignals
```

Vehicle frame entries contain the resolved latitude/longitude, heading, lane,
road progress, dimensions, speed, and status needed by a renderer. Signal frame
entries additionally contain the active phase, green incoming roads, and phase
remaining seconds at that frame time.

The backend captures a post-movement frame after every physical sub-step (at
most one simulated second) and again after event transitions. When movement and
an event transition share a timestamp, the post-transition frame replaces the
earlier value, so a lookup never observes a half-applied boundary. The lookup
uses floor semantics; the macro UI avoids ambiguity by scrubbing only across
`recordedSeconds` returned by the timeline endpoint.

History is in memory and bounded to 900 retained timestamps. A request older
than the retained window returns `404`. Initialize, reset, scenario/map
replacement, and any in-place network edit create a new `timelineId`. This is
intentional: frames store dynamic road state but not a full historical copy of
network geometry.

## Physical view

```text
GET /api/simulation/physical-view?lat=<deg>&lon=<deg>&radiusMetres=<m>
```

Returns the lane-level scene within `radiusMetres` of the point, for the PixiJS
renderer. All three query parameters are required and numeric; a missing or
non-numeric one returns `400`, as does a non-positive radius. The radius is
capped at 1000 m so one request cannot ask for the whole city.

The scene is projected into **local metres** with the query point as the origin,
x running east and y running north. This is deliberate: the renderer works in a
single flat frame and never sees latitude or longitude, which keeps the three
coordinate systems (geographic, local metres, screen pixels) from being mixed.

```jsonc
{
  "origin": { "latitude": 10.0, "longitude": 106.0 },
  "radiusMetres": 300.0,
  "timelineId": "...",        // ties the scene to one frame; "none" before init
  "frameId": 412,             // -1 before the first frame
  "elapsedSeconds": 41.2,
  "lanes":      [ { "roadId", "laneIndex", "widthMetres", "oneway", "centreline": [[x,y], ...] } ],
  "stopLines":  [ { "roadId", "from": [x,y], "to": [x,y] } ],
  "connectors": [ { "fromRoadId", "fromLane", "toRoadId", "toLane", "curve": [[x,y], ...] } ],
  "junctions":  [ { "junctionId", "pavement": [[x,y], ...] } ],
  "vehicles":   [ { "id", "type", "x", "y", "headingDegrees", "speedMps",
                    "lengthMetres", "widthMetres", "laneIndex", "lateralOffsetMetres" } ]
}
```

Geometry is derived from the network (`domain.scene.LaneGeometryBuilder`);
vehicles come from the current authoritative frame. `timelineId` and `frameId`
are that frame's, so the physical view and the macro map stay on one clock. A
vehicle's `lateralOffsetMetres` is its real offset, so a filtering motorcycle is
drawn between the lanes it is riding between rather than snapped to one.

Static geometry and moving vehicles share one response for now; when the network
grows, geometry (fetched once per region) and vehicles (fetched per frame) split.
