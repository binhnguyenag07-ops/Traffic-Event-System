# API Contract

All endpoints use JSON unless noted. Errors use `{"error":"message"}`. The
simulation still has an integer `tick` for deterministic trace indexing, but
physical time and event scheduling use `elapsedSeconds`.

The browser client prefixes these paths with the build-time
`VITE_API_BASE_URL`, defaulting to `/api`; a trailing slash is removed. Set it
before the Vite production build only when the API is mounted at a different
base.

## Health

| Method | Endpoint | Result |
|---|---|---|
| GET | `/api/health` | Service status and name |

## Network and scenarios

| Method | Endpoint | Result |
|---|---|---|
| GET | `/api/network` | Intersection, road, and building counts |
| GET | `/api/network/intersections` | Geographic intersections |
| GET | `/api/network/intersections/{id}` | One intersection or `404` |
| GET | `/api/network/roads` | Directed roads, conditions, lane geometry, and polyline geometry |
| GET | `/api/network/roads/{id}` | One road or `404` |
| GET | `/api/network/movements` | Explicit junction-movement rules |
| GET | `/api/network/buildings` | Buildings and access intersections |
| GET | `/api/network/buildings/{id}` | One building or `404` |
| POST | `/api/network/intersections` | Add a junction while playback is not running |
| DELETE | `/api/network/intersections/{id}` | Remove a safe-to-remove junction and return `204` |
| POST | `/api/network/roads` | Add one directed road or a bidirectional pair |
| DELETE | `/api/network/roads/{id}` | Remove an unoccupied road and return `204` |
| PUT | `/api/network/roads/{id}/condition` | Change status and congestion |
| PUT | `/api/network/movements` | Add or replace an explicit movement rule |
| DELETE | `/api/network/movements` | Remove an explicit movement rule |
| GET | `/api/network/intersections/{id}/roads` | Incoming and outgoing roads |
| GET | `/api/network/export` | Download current network JSON |
| GET | `/api/scenarios` | Bundled scenarios |
| GET | `/api/scenarios/active` | Server-owned active scenario id |
| POST | `/api/scenarios/{id}/activate` | Reset and activate a bundled scenario |
| POST | `/api/scenarios/import` | Multipart JSON import using field `map` |

Road responses and exported JSON include `name`, `physicalRoadId`, `laneCount`,
`laneWidthMetres`, `roadWidthMetres`, `roadClass`, and `geometry`. Import and
road-creation payloads may omit lane values; defaults are derived from class:
PRIMARY 3 × 3.5 m, SECONDARY/TERTIARY 2 × 3.25 m, and other classes 1 × 3.0 m.
Two directions of the same street share a `physicalRoadId`.

Imports are limited to 2 MB, 600 intersections, and 1,200 roads. A successful
import atomically replaces the network only after validation.

Normal server startup loads and initializes the complete scenario selected by
`SCENARIO_FILE`, defaulting to `data/scenarios/district1_typical_day.json`.
`MAP_FILE` is used only as an explicit topology-only startup override when
`SCENARIO_FILE` is unset. Activating a scenario reloads its map, adaptive
configuration, driver profiles, demand, scripted vehicles, and events.

## Vehicles, events, and signals

| Method | Endpoint | Result |
|---|---|---|
| GET | `/api/vehicles` | Current vehicles |
| GET | `/api/vehicles/{id}` | One vehicle or `404` |
| POST | `/api/vehicles` | Create CAR, MOTORCYCLE, BUS, or EMERGENCY |
| DELETE | `/api/vehicles/{id}` | Remove a vehicle |
| GET | `/api/events` | All scheduled, active, and resolved events |
| GET | `/api/events/{id}` | One event or `404` |
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
Demand-created vehicles additionally include `tripPlanId`, open
`tripPurpose`, `currentTripLegIndex`, `tripLegCount`, `currentPlaceId`, and
`dwellUntilSeconds`. `DWELLING` vehicles have released their road occupancy and
are omitted from the physical lane scene.

`MOTORCYCLE` uses a 2.1 m × 0.75 m physical profile, compact following gaps,
and `MOTORCYCLE_FILTERING_MOBIL`. It accepts useful adjacent-lane gaps that do
not fit a car without bypassing target-follower braking checks. Once a maneuver
is accepted, `lateralOffsetMetres` advances progressively toward the target lane
centre; renderer frames therefore show the motorcycle between lane centres
during the lateral movement.

## Routing, simulation, and diagnostics

| Method | Endpoint | Result |
|---|---|---|
| POST | `/api/routes` | One BFS, Dijkstra, or A* route |
| POST | `/api/routes/compare` | All compatible algorithms |
| GET | `/api/routing/algorithms` | Algorithm names and preferences |
| POST | `/api/simulation/initialize` | Optional generated demand plus scripted vehicles and events |
| POST | `/api/simulation/start` | Start continuous playback |
| POST | `/api/simulation/pause` | Pause playback |
| POST | `/api/simulation/resume` | Resume playback |
| POST | `/api/simulation/reset` | Rebuild the initialized run at tick zero |
| POST | `/api/simulation/tick` | One manual logical tick |
| PUT | `/api/simulation/parameters` | Logical seconds per tick and/or playback interval |
| GET | `/api/simulation/status` | Status, clock, playback settings, and demand counters |
| GET | `/api/simulation/tick/current` | Current integer trace tick |
| GET | `/api/simulation/snapshot` | Current snapshot |
| GET | `/api/simulation/diagnostics` | Adaptive navigation, information, wait, U-turn, and deadlock evidence |
| GET | `/api/simulation/signals` | Generated junction signal plans |
| GET | `/api/simulation/stream` | Server-Sent authoritative renderer frames |
| GET | `/api/simulation/trace` | Recorded trace range and vehicle ids |
| GET | `/api/simulation/trace/frames` | Retained frame index for the current timeline |
| GET | `/api/simulation/trace/frames/at?elapsedSeconds={s}` | Latest retained frame at or before `s` |
| GET | `/api/simulation/trace/ticks/{tick}` | All recorded vehicle states at one tick |
| GET | `/api/simulation/trace/vehicles/{vehicleId}?fromTick={a}&toTick={b}` | One vehicle's trace over an optional inclusive tick range |
| GET | `/api/simulation/trace/vehicles/{vehicleId}/ticks/{tick}` | One vehicle state at one tick |
| GET | `/api/simulation/trace/flow` | Per-road density/speed/flow samples |
| GET | `/api/simulation/trace/fundamental` | Density-bin averages for a fundamental diagram |
| GET | `/api/simulation/trace/roads/{roadId}/time-space` | Per-vehicle road trajectories |
| GET | `/api/simulation/physical-view` | Static physical scene plus current frame state |
| GET | `/api/simulation/physical-view/state` | Lightweight current physical state |
| GET | `/api/statistics` | Traffic and algorithm metrics |
| GET | `/api/statistics/traffic` | Traffic metrics only |
| GET | `/api/statistics/algorithm/{name}` | Metrics for one registered algorithm |
| GET | `/api/experiments/latest` | Checked-in experiment baseline |
| POST | `/api/experiments/run` | Fresh deterministic experiment run |

`GET /api/simulation/snapshot` materializes a JSON DTO containing `timelineId`,
`frameId`, `tick`, `elapsedSeconds`, `status`, `vehicles`, `trafficEvents`,
`roadStates`, and `trafficSignals`. Inside the simulator, snapshot collection
containers are copied before they leave the synchronized aggregate, but their
domain elements remain mutable. The controller maps them immediately to DTOs;
this operational endpoint must not be treated as a deep-immutable replay value.
Use `SimulationFrame` from SSE or frame lookup when one coherent renderer state
is required. The status endpoint is likewise an operational summary assembled
from individual synchronized reads, not a replacement for a renderer frame.

Flow density is vehicles per kilometre per lane
(`densityVehPerKmPerLane`); speed is metres per second (`meanSpeedMps`); flow is
vehicles per hour per lane (`flowVehPerHourPerLane`). Event boundaries inside a
logical tick are applied at their exact simulated second.

Arrived vehicles stay in snapshots for at least five simulated seconds, then
move to completed-trip history. Initialize, reset, scenario activation, and map
replacement clear traces and completed-trip history.

Initialization accepts the complete demand shape documented in
`DEMAND_AND_SCENARIO_DESIGN.md`: `demandParameters`, clock/duration, seed,
scale, zones, and `demandSources`. Legacy `vehicles` and `events` remain valid
and may be combined with generated traffic. A source may select buildings,
boundaries, zones or an explicit place; arbitrary new purpose identifiers do
not require a Java enum.

`GET /api/simulation/status` contains a `demand` object with
`scheduledTrips`, `spawnedTrips`, `failedTrips`, `pendingTrips`, and
`failedPlanIds`. A plan is rejected before spawning if any consecutive leg has
no directed path. Traffic statistics separately report `roadActiveVehicles`,
`dwellingVehicles`, `activeVehiclesByPurpose`, and
`completedTripsByPurpose`.

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

The SSE endpoint emits the current `SimulationFrame` whenever one exists. Its
defensive fallback for a service implementation with no frame is the current
snapshot DTO; the production simulator creates an initial frame at construction.

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

## Simulation clock

`GET /api/simulation/status` carries the run clock alongside the tick counters:

```jsonc
{
  "status": "READY",
  "currentTick": 0,
  "elapsedSeconds": 0.0,
  "simulationSecondsPerTick": 0.5,
  "playbackIntervalMs": 1000,
  "startTimeOfDaySeconds": 21600.0,   // the run began at 06:00
  "timeOfDaySeconds": 21600.0,
  "timeOfDay": "06:00",
  "demand": {
    "scheduledTrips": 216,
    "spawnedTrips": 0,
    "failedTrips": 0,
    "pendingTrips": 216,
    "failedPlanIds": []
  }
}
```

This is the initial status of the checked-in District 1 scenario. Its four
scripted warm-start vehicles appear in snapshots but are not counted as
demand-spawned trips.

`startTimeOfDaySeconds` is published so a client can label **any** recorded
frame with its clock time from that frame's own `elapsedSeconds`:

```text
timeOfDay = (startTimeOfDaySeconds + elapsedSeconds) mod 86400
```

Frames deliberately do not carry a clock time of their own. A frame already
records its elapsed seconds, so storing the derived value beside it would be a
second copy of the same fact, free to disagree. The clock wraps at midnight.

## Continuous playback failures

Continuous playback uses one fixed-delay scheduled executor. If a scheduled tick
throws a `RuntimeException`, the service logs the failure, attempts to pause the
simulator, cancels future scheduled ticks, and publishes the resulting snapshot.
The normal resulting state is `PAUSED`. If the pause operation itself fails,
both failures are logged and the scheduled task still remains cancelled. A
fatal JVM `Error` receives the same cleanup and is then rethrown.

There is no separate SSE error event for this condition: clients receive the
resulting snapshot and operators inspect the server log. A client must not
assume that an earlier `RUNNING` response guarantees continuing ticks.
`POST /api/simulation/tick` returns `409` while the simulator is running; pause
before manual stepping.

## Physical view

```text
GET /api/simulation/physical-view?lat=<deg>&lon=<deg>&radiusMetres=<m>
GET /api/simulation/physical-view?lat=<deg>&lon=<deg>&fullMap=true
```

The first form returns the lane-level scene within `radiusMetres` of the point
for the PixiJS renderer. In this regional mode `lat`, `lon`, and
`radiusMetres` are required and numeric; a missing or non-numeric value returns
`400`, as does a non-positive radius. The radius is capped at 1000 m.

`fullMap=true` deliberately loads the complete geometry of the current network.
It requires only `lat` and `lon`; the server derives the smallest radius that
contains the loaded scenario around that origin. This is bounded by the network
already held in memory, not by an arbitrary city-sized radius.

The scene is projected into **local metres** with the query point as the origin,
x running east and y running north. This is deliberate: the renderer works in a
single flat frame and never sees latitude or longitude, which keeps the three
coordinate systems (geographic, local metres, screen pixels) from being mixed.

```jsonc
{
  "origin": { "latitude": 10.0, "longitude": 106.0 },
  "radiusMetres": 300.0,
  "timelineId": "...",        // ties the scene to one renderer timeline
  "frameId": 412,             // -1 only if the service exposes no current frame
  "elapsedSeconds": 41.2,
  "lanes":      [ { "roadId", "laneIndex", "widthMetres", "oneway", "centreline": [[x,y], ...] } ],
  "stopLines":  [ { "roadId", "from": [x,y], "to": [x,y] } ],
  "connectors": [ { "fromRoadId", "fromLane", "toRoadId", "toLane", "curve": [[x,y], ...] } ],
  "junctions":  [ { "junctionId", "pavement": [[x,y], ...] } ],
  "vehicles":   [ { "id", "type", "x", "y", "headingDegrees", "speedMps",
                    "lengthMetres", "widthMetres", "laneIndex", "lateralOffsetMetres",
                    "roadId", "roadProgress", "inJunction",
                    "junctionFromRoadId", "junctionToRoadId" } ]
}
```

Geometry is derived from the network (`domain.scene.LaneGeometryBuilder`);
vehicles come from the current authoritative frame. `timelineId` and `frameId`
are that frame's, so the physical view and the macro map stay on one clock. A
vehicle's `lateralOffsetMetres` is its real offset, so a filtering motorcycle is
drawn between the lanes it is riding between rather than snapped to one. Road
progress and the two junction road ids let the renderer follow lane centrelines
and connector curves rather than cutting a straight chord across a bend.

After that first geometry response the renderer polls only the changing state:

```text
GET /api/simulation/physical-view/state?lat=<origin-lat>&lon=<origin-lon>&radiusMetres=<scene-radius>
```

The state response has the same `origin`, `radiusMetres`, `timelineId`,
`frameId`, and `elapsedSeconds`, adds `playbackIntervalMs`, and carries only
`signals` and `vehicles`. The interval controls interpolation time only; changing
playback speed does not alter `simulationSecondsPerTick` or the physical model.
Static lanes, junctions, land cover and buildings therefore cross the wire and
enter Pixi once per timeline instead of being rebuilt with every state poll. A
changed `timelineId` causes the client to reload the complete geometry.
