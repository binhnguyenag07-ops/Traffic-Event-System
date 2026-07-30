# Traffic Event System

An object-oriented urban traffic simulator built with Java 17, Maven, Javalin,
and a React frontend. The system models a directed road network, loads scenario
maps, computes routes with BFS/Dijkstra/A*, runs multi-vehicle simulations, and
processes positioned traffic events, lane-level incidents, and phased traffic
signals.

## Current Status

The repository currently includes:

- road-network graph operations, physical-road identity, lane count/width, and density tracking;
- JSON/CSV map loading, map saving, and synthetic grid-map generation;
- route search with BFS, Dijkstra, A*, congestion-aware route costs, and
  incoming-road-aware movement restrictions;
- deterministic driver knowledge, location-aware adaptive rerouting, legal
  U-turns, cause-local stall handling, and strict-physics deadlock recovery;
- microscopic Krauss car-following with deterministic per-driver variation,
  curve-speed limits, lane occupancy, MOBIL lane changes, and a compact
  motorcycle filtering strategy;
- traffic-event models for congestion, positioned accidents, breakdowns, and road closures;
- event service, event API, and overlap-safe event overlay composition;
- simulation clock, tick processing, pause/resume/reset, and snapshots;
- server-owned live playback with configurable tick timing and SSE renderer frames;
- bounded authoritative frame history captured at exact state transitions and
  sampled microscopic boundaries, with a LIVE/HISTORY scrubber on the macro
  map;
- lane-offset vehicle pose (latitude, longitude, heading), collision detection,
  emergency priority, real stop-line control, and bus dwell stops;
- seeded building/boundary demand with open purpose ids, data-defined hourly
  curves and vehicle mixes, exact return-to-origin conservation, multi-stop
  plans, planned dwell, and explicit unroutable-plan reporting;
- a reader-facing 24-hour demand schedule with named origins/destinations,
  departure, estimated arrival/dwell/return clocks, a read-only hourly profile
  and purpose summary, plus an always-visible simulation clock;
- open-boundary demo traffic where completed vehicles exit and different
  same-type vehicles enter from reachable boundary roads;
- traffic and algorithm statistics services;
- React operations console with a React-Leaflet/OpenStreetMap macro map for road
  pressure, incidents, routes, network editing, and statistics, plus a PixiJS
  lane-level physical view; both render backend-resolved vehicle poses;
- Fleet controls that select human-readable building/street names and create a
  real backend car or motorcycle on a reachable building-to-building route;
  random test vehicles prefer residential origins and use a documented
  citywide registered-fleet ratio only as a test-spawn proxy; coordinates,
  directed endpoints, lane count, and road length disambiguate repeated names;
- session-scoped network editing for junctions, directed/two-way roads, road
  conditions, connected-road searches, and JSON export;
- atomic browser JSON import for geographic HCMC networks, with stored/OSRM/
  straight-fallback road geometry;
- cyclic `TrafficSignal` phases derived for real junctions (degree at least 3);
- per-road flow samples, fundamental-diagram bins, and time-space trajectories;
- total and average trip time, actual route length, density, rerouting, and
  path-optimality statistics;
- reproducible normal, congestion, random-event, and 20×20-grid experiments
  with tables and frontend comparison charts;
- unit and integration tests for domain, routing, persistence, services,
  controllers, and simulation.

## Requirements

- Java Development Kit 17 or newer
- Apache Maven 3.9 or newer
- Node.js and npm for the frontend
- Git

Check the installed versions:

```powershell
java -version
mvn -version
node -v
npm -v
git --version
```

## Run the Complete Demo

The launch scripts run from the repository root, install the locked frontend
dependencies, build the frontend, run a clean Maven package, and start the
application at `http://localhost:7070`. They stop immediately if any native
build or launch step fails.

Linux or macOS:

```bash
./run-demo.sh
```

Windows PowerShell:

```powershell
.\run-demo.ps1
```

Both scripts take the same options. `--port` moves the server, `--scenario`
starts on a different bundled run (including the physical-view lab cases under
`data/scenarios/test/`), and `--skip-build` relaunches an existing JAR without
waiting for a rebuild. Run either with `--help` for the full list. With no
options the behaviour is unchanged.

```bash
./run-demo.sh --port 8080 --scenario data/scenarios/test/u-turn.json
```

The map uses OpenStreetMap tiles, so the basemap requires an internet
connection. The traffic simulation, routing, events, and vehicle positions run
locally.

PixiJS normally draws the Physical View through WebGL, so vehicle sprites and
lane geometry are already GPU-rendered when the browser exposes hardware
acceleration. The authoritative simulation itself is Java code on the CPU:
graph routing, vehicle interactions, signal arbitration, events, and demand are
branch-heavy, stateful operations. Moving that model to GPU compute would be a
large redesign and would weaken deterministic replay for this map size; the
implemented performance controls instead cap rendering at 60 FPS, reuse static
geometry, stream moving state by camera, suspend the hidden full-fleet macro
stream while Physical View is open, bound history, throttle secondary requests,
and offer explicit playback paces.

The compiled frontend is embedded in the JAR, but `data/maps`,
`data/scenarios`, and `data/demand` remain external runtime inputs. Keep the
repository `data/` directory beside the launch scripts. If starting the JAR
manually, run it from the repository root or set `SCENARIO_FILE` to a valid
scenario path. `MAP_FILE` is a topology-only override for map-development runs.

By default the backend initializes the complete
`district1_typical_day.json` scenario before serving requests. Opening the
dashboard starts playback from its seeded demand schedule; pause and use
**Reload scenario** whenever a clean seeded rerun is needed.

The checked-in run's demand horizon starts at 06:00 and covers every departure
hour through 06:00 on simulation day `D+1`. Initialization advances the ordinary
microscopic model for five simulated minutes without recording replay history,
so the first interactive frame is a populated 06:05 state rather than an empty
road. The simulator may continue beyond the departure horizon while vehicles
finish dwell or return legs.

The typical-day scenario sets `automaticCollisionEvents: false`. Its demand is
not paired with a calibrated crash-occurrence model, so a braking-risk heuristic
must not turn routine dense queues into dozens of invented accidents. Explicit
accident/breakdown controls and the dedicated collision lab remain available;
other scenarios retain automatic detection by default.

The header clock is derived from the authoritative frame. Use **Real time**
(1 simulated second per wall second), **Balanced** (10×), or **24 h overview**
(120×, about 12 wall minutes for one simulated day) on the macro dashboard.
Physical View is a live camera: opening it switches to four authoritative
0.25-second ticks per wall second (1×), never seeks the clock and never uses a
30-second overview tick for close-up motion. Closing it restores the previous
macro pace.

## Backend Setup

```powershell
git clone https://github.com/binhnguyenag07-ops/Traffic-Event-System.git
cd Traffic-Event-System
mvn test
```

Run the API server:

```powershell
mvn exec:java
```

Default backend URL:

```text
http://localhost:7070
```

## Frontend Setup

```powershell
cd frontend
npm install
npm run dev
```

The Vite dev server will print the local frontend URL.
Requests under `/api` are proxied to `http://localhost:7070` during development.

## Main API Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/health` | health check |
| `GET /api/network` | current intersection, road, and building counts |
| `GET /api/network/intersections` | list intersections |
| `GET /api/network/intersections/{id}` | get one intersection |
| `POST /api/network/intersections` | add an intersection |
| `DELETE /api/network/intersections/{id}` | remove an intersection and its safe-to-remove roads |
| `GET /api/network/roads` | list roads |
| `GET /api/network/roads/{id}` | get one road |
| `POST /api/network/roads` | add a directed or two-way road |
| `DELETE /api/network/roads/{id}` | remove an unoccupied road |
| `PUT /api/network/roads/{id}/condition` | update road status and congestion |
| `GET /api/network/movements` | list explicit junction-movement rules |
| `PUT /api/network/movements` | add or replace an explicit movement rule |
| `DELETE /api/network/movements` | remove an explicit movement rule |
| `GET /api/network/buildings` | list buildings and access intersections |
| `GET /api/network/buildings/{id}` | get one building |
| `GET /api/network/intersections/{id}/roads` | list incoming and outgoing roads |
| `GET /api/network/export` | download the current network as JSON |
| `POST /api/routes` | compute route |
| `GET /api/routing/algorithms` | list registered algorithms and compatible preferences |
| `POST /api/routes/compare` | compare all algorithms compatible with a request |
| `GET /api/vehicles` | list vehicles |
| `GET /api/vehicles/{id}` | get one vehicle |
| `POST /api/vehicles` | create vehicle |
| `DELETE /api/vehicles/{id}` | remove one vehicle |
| `GET /api/events` | list events |
| `GET /api/events/{id}` | get one event |
| `POST /api/events` | create traffic event |
| `GET /api/events/active` | list active events |
| `GET /api/events/road/{roadId}` | list events affecting one road |
| `PUT /api/events/{id}/status` | activate or resolve event |
| `DELETE /api/events/{id}` | remove event |
| `GET /api/scenarios` | list bundled complete run definitions |
| `GET /api/scenarios/active` | get the server-owned active scenario id |
| `POST /api/scenarios/{id}/activate` | load a scenario map, demand, vehicles, and events |
| `POST /api/scenarios/import` | upload a geographic JSON map in multipart field `map` |
| `POST /api/simulation/initialize` | load generated demand plus optional scripted vehicles/events |
| `POST /api/simulation/start` | start simulation |
| `POST /api/simulation/pause` | pause simulation |
| `POST /api/simulation/resume` | resume simulation |
| `POST /api/simulation/reset` | reset simulation |
| `POST /api/simulation/tick` | advance one tick |
| `PUT /api/simulation/parameters` | set logical seconds per tick and playback interval |
| `GET /api/simulation/status` | run status, clock, playback settings, and demand counters |
| `GET /api/simulation/demand/schedule?offset={n}&limit={n}` | paged exact seeded plans plus full-day hourly/purpose/vehicle aggregates |
| `GET /api/simulation/tick/current` | current integer trace tick |
| `GET /api/simulation/snapshot` | current simulation snapshot |
| `GET /api/simulation/diagnostics` | adaptive navigation, information, wait, U-turn, and deadlock evidence |
| `GET /api/simulation/stream` | live authoritative renderer frames using Server-Sent Events |
| `GET /api/simulation/signals` | generated traffic-signal plans |
| `GET /api/simulation/trace` | trace range and recorded vehicle ids |
| `GET /api/simulation/trace/frames` | retained authoritative frame timeline |
| `GET /api/simulation/trace/frames/at?elapsedSeconds={s}` | frame at or immediately before a simulated second |
| `GET /api/simulation/trace/ticks/{tick}` | all recorded vehicle states at one tick |
| `GET /api/simulation/trace/vehicles/{vehicleId}?fromTick={a}&toTick={b}` | one vehicle's trace over an optional tick range |
| `GET /api/simulation/trace/vehicles/{vehicleId}/ticks/{tick}` | one vehicle state at one tick |
| `GET /api/simulation/trace/flow` | density, speed, and flow samples |
| `GET /api/simulation/trace/fundamental` | fundamental-diagram density bins |
| `GET /api/simulation/trace/roads/{roadId}/time-space` | vehicle trajectories on one road |
| `GET /api/simulation/physical-view?lat={lat}&lon={lon}&radiusMetres={m}` | static lane-level scene plus the current frame state |
| `GET /api/simulation/physical-view/state?lat={lat}&lon={lon}&radiusMetres={m}` | lightweight current physical-view state |
| `GET /api/statistics` | combined statistics |
| `GET /api/statistics/traffic` | traffic statistics |
| `GET /api/statistics/algorithm/{name}` | algorithm statistics |
| `GET /api/experiments/latest` | checked-in experiment baseline |
| `POST /api/experiments/run` | rerun the deterministic experiment suite |

## Map Data

Available map files:

| File | Purpose |
|---|---|
| `data/maps/district1_network.json` | default full-viewport District 1 network and buildings |
| `data/demand/default_assumptions.json` | replaceable occupancy, attraction, decay, and hourly curves |
| `data/scenarios/district1_typical_day.json` | seeded 24-hour weekday proxy: 38,518 deterministic vehicle plans, a five-minute physical pre-roll, and no scripted vehicles |
| `data/results/experiment_results.json` | reproducible experiment baseline |

The District 1 scenario is an educational, research-informed microscopic
sample rather than a calibrated traffic forecast. Verified map/run facts,
historical measurements, research proxies, and authored assumptions are kept
separate. JICA reported 2.323 million daily non-walking trip ends for the former
District 1 in 2002, while a 2019 TCIP/World Bank count measured 35,613 vehicles
in one peak hour at the Thủ Thiêm tunnel. Those anchors prove that the old
156-plan workload was only a smoke test; they do not supply a current OD matrix
for this exact map rectangle.

For the committed seed, 38,518 simulated plans contain 69,837 vehicle legs, and
zero plans fail structural generation. Each plan is one vehicle for its whole
multi-leg day, so the mode split counts plans, not legs: 34,506 motorcycles and
4,012 cars, or 89.6% motorcycles.
This is roughly a 1:9 agent sample of the documented 0.4–0.7 million
vehicle-leg/day historical proxy for the 3.05 km² study window. Counts are not
automatically expanded in statistics, and the exact cordon allocation, dwell
and purpose-specific curves remain assumptions. See the
[scenario evidence and reproducibility note](docs/DISTRICT1_SCENARIO.md) and
[`data/README.md`](data/README.md).

The committed map has 730 directed roads representing 560 physical segments.
An audit against a fresh OpenStreetMap/Overpass extract covered all 439 source
ways: 390 physical segments are one-way (380 tagged `oneway=yes`, 10
roundabouts), while 170 are represented in both directions. Of the 168
one-way segments with at least three lanes, 155 have direct OSM lane tags and
13 use generator inference/defaults. Direction, lane, and name mismatches found
during the audit were corrected. Eleven OSM ways allow a motorcycle-specific
reverse exception (`oneway:motorcycle=no`); the current all-vehicle road schema
cannot express that exception, so it deliberately preserves the general
one-way rule.

### Units

| Field | Unit or range |
|---|---|
| `distance` | kilometres |
| `speedLimit` | kilometres per hour |
| `roadClass` | street hierarchy used for zoom-aware rendering |
| `laneCount` | directed lanes on this road (positive integer) |
| `laneWidthMetres` / `roadWidthMetres` | metres |
| travel time and routing cost | hours |
| simulation and event time | seconds |
| `congestionLevel` | `0.0` to `1.0` |
| `vehicleDensity` | `0.0` to `1.0` |

Congestion-aware cost is:

```text
travelCost = distance / speedLimit
             * (1 + congestionLevel + vehicleDensity)
```

A blocked or closed road has an infinite routing cost. A road that is merely at
its current storage capacity stays topologically routable and receives a soft
queue delay; physical admission still rejects entry until space becomes
available.

The District 1 and Small City coordinates and street geometries are derived from
OpenStreetMap data. They are stored locally so simulation
and routing do not depend on an external routing service at runtime. Map data
© OpenStreetMap contributors, available under the ODbL.

The current centreline dataset can be regenerated with
`python3 tools/generate_osm_map.py`. It accepts only freshness-verifiable OSM
base snapshots no older than 24 hours during normal regeneration, writes cache
files atomically, and permits stale/legacy cache input only with the explicit
`--offline-cache` flag. It uses public Overpass mirrors. See the
[macro and physical map plan](docs/PHYSICAL_VIEW_PLAN.md)
for the local-PBF ingestion roadmap and physical-view design background.

### Network Editor

Open **Network editor** in the right rail to edit the in-memory scenario. New
junctions are placed by clicking the map. New roads request real street
geometry from OSRM and fall back to a straight geographic segment if OSRM is
unavailable. Two-way streets are represented by two directed `Road` objects
with distinct IDs and one shared `physicalRoadId`. Lane count and lane width can
be entered explicitly; imports without them derive defaults from `roadClass`.

Pause the simulation before editing. Occupied roads, intersections used as a
vehicle endpoint, and roads referenced by unresolved events cannot be deleted.
Waiting vehicles recompute their routes after topology or condition changes.
Edits last for the current server session; use **Export network JSON** to save
the result without overwriting the bundled scenarios. Because replay frames do
not copy the whole geographic network, any network edit starts a new timeline;
old frames are discarded instead of being rendered on incompatible geometry.

Use **Import JSON** in the header to load a real-map network. Imports require
latitude/longitude inside the displayed HCMC operating area, are limited to
2 MB, 600 junctions, and 1,200 directed roads, and replace the network only after the whole file validates. Stored road
geometry is preserved; missing geometry uses OSRM with a straight fallback.
Map JSON may persist explicit `junctionMovements` and `turnaroundPoints`.
Movement rules can also be edited through the movement API while the simulation
is paused. The same movement graph drives routing, junction admission, and
physical lane-connector geometry.

## Statistics and Experiments

The Performance panel reports completed-trip time and distance, network
density, waiting vehicles, routing computation time, dynamic recalculations,
and path optimality. Completed trips remain in the run statistics after an
arrived marker's short display-retention window ends. Initialize, reset,
scenario activation, and map import start a new statistical run. Signals are
junction-owned cyclic phase plans; they are not traffic events.

Run the four required algorithm experiments:

```powershell
mvn -q exec:java -Dexec.mainClass=team.traffic.experiment.ExperimentRunner
```

Use `-Dexec.args=--write` to refresh the baseline JSON and Markdown report.

## Example Simulation Payload

```json
{
  "vehicles": [
    {
      "id": "car-1",
      "type": "CAR",
      "startIntersectionId": "I1",
      "destinationIntersectionId": "I6"
    }
  ],
  "events": [
    {
      "id": "event-1",
      "type": "ACCIDENT",
      "affectedRoadId": "R11",
      "startTimeSeconds": 20,
      "durationSeconds": 50,
      "positionAlongRoad": 0.65,
      "blockedLaneIndexes": [0],
      "causingVehicleId": "car-1",
      "origin": "SCENARIO",
      "informationPolicy": {
        "kind": "ANNOUNCED_AT",
        "publicationSeconds": 10
      }
    }
  ],
  "adaptiveTraffic": {
    "enabled": true,
    "strategicCadenceSeconds": 2,
    "routing": {
      "maximumLookaheadRoads": 64,
      "knowledgeCacheSeconds": 5,
      "retryBackoffSeconds": [5, 15, 30, 60]
    },
    "uTurns": {
      "networkPolicy": "EXPLICIT_OR_CONSERVATIVE_LOCAL_INFERENCE",
      "defaultPenaltySeconds": 25
    },
    "deadlock": {
      "scanIntervalSeconds": 5,
      "confirmSeconds": 30,
      "minimumProgressMetres": 1,
      "reservationSeconds": 8,
      "assistedRecovery": "DISABLED"
    }
  },
  "driverProfiles": {
    "CAR": {
      "decision": {
        "informationAccess": "CONNECTED_NAVIGATION",
        "informationLatencySeconds": 3,
        "reroutePatienceSeconds": 8,
        "minimumExpectedSavingSeconds": 15,
        "rerouteCooldownSeconds": 30,
        "stallEscapeSeconds": 45,
        "uTurnPolicy": "ONLY_WHEN_CURRENT_ROUTE_BLOCKED"
      }
    }
  }
}
```

`startTick` and `durationTicks` remain accepted as input-only migration aliases;
all responses and frontend controls use seconds. Omitted adaptive and driver
fields use deterministic defaults; `assistedRecovery` currently accepts only
`DISABLED` because the strict resolver never teleports or despawns traffic.

Send it to:

```text
POST /api/simulation/initialize
```

Then call:

```text
POST /api/simulation/start
GET  /api/simulation/snapshot
```

While running, the backend advances automatically. Pause the simulation before
using `POST /api/simulation/tick` for manual, single-step playback.

## Test Suite

Run every backend test:

```powershell
mvn test
```

Run the frontend production build:

```powershell
cd frontend
npm run lint
npm test
npm run build
```

Build the executable application JAR:

```powershell
mvn clean package
java -jar target/traffic-event-system-1.0-SNAPSHOT.jar
```

Run the JAR from the repository root because the checked-in map, demand, and
scenario JSON files are intentionally not embedded in it.

Optional environment variables:

| Variable | Default | Purpose |
|---|---:|---|
| `PORT` | `7070` | HTTP server port |
| `SCENARIO_FILE` | `data/scenarios/district1_typical_day.json` | complete startup scenario; takes precedence over `MAP_FILE` |
| `MAP_FILE` | unset | topology-only startup map when `SCENARIO_FILE` is unset |
| `VITE_API_BASE_URL` | `/api` | build-time frontend API base (for example `https://host.example/api`) |
| `VITE_TILE_URL` | OpenStreetMap | frontend tile provider URL |
| `VITE_TILE_ATTRIBUTION` | OpenStreetMap attribution | tile attribution text |

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Macro and physical map plan](docs/PHYSICAL_VIEW_PLAN.md)
- [Class diagram](docs/CLASS_DIAGRAM.md)
- [API contract](docs/API_CONTRACT.md)
- [Algorithm explanation](docs/ALGORITHM_EXPLANATION.md)
- [Experiment results](docs/EXPERIMENT_RESULTS.md)
- [Live demo guide](docs/DEMO_GUIDE.md)
- [Planning overview](docs/PLANNING_OVERVIEW.md)
- [Demand and scenario design](docs/DEMAND_AND_SCENARIO_DESIGN.md)
- [District 1 scenario evidence and reproducibility](docs/DISTRICT1_SCENARIO.md)
- [Student A network and persistence notes](docs/planning/Binh-network-persistence.md)
- [Student B routing notes](docs/planning/nguynie-routing-algorithms.md)
- [Student C vehicle and event notes](docs/planning/Hyo-vehicle-traffic_event.md)
- [Student D simulation/API/frontend notes](docs/planning/An-simulation-API-services-frontend.md)
