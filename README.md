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
- event service, event API, and dynamic rerouting support;
- simulation clock, tick processing, pause/resume/reset, and snapshots;
- server-owned live playback with configurable tick timing and SSE snapshots;
- bounded authoritative frame history captured after every microscopic sub-step
  and event boundary, with a LIVE/HISTORY scrubber on the macro map;
- lane-offset vehicle pose (latitude, longitude, heading), collision detection,
  emergency priority, real stop-line control, and bus dwell stops;
- seeded building/boundary demand with open purpose ids, data-defined hourly
  curves and vehicle mixes, exact return-to-origin conservation, multi-stop
  plans, planned dwell, and explicit unroutable-plan reporting;
- open-boundary demo traffic where completed vehicles exit and different
  same-type vehicles enter from reachable boundary roads;
- traffic and algorithm statistics services;
- React operations console with an OpenStreetMap/Leaflet macro map for road
  pressure, incidents, routes, network editing, and statistics; lightweight
  glowing vehicle dots remain visible while image sprites are reserved for a
  separate physical view;
- a Fleet **Spawn random test vehicle** control that creates a real backend car
  or motorcycle on a reachable boundary-to-boundary route, with the request
  policy isolated for later land-use/demand-based origin-destination models;
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

The launch scripts install/build the frontend, package it inside the backend
JAR, and start the complete application at `http://localhost:7070`.

Linux or macOS:

```bash
./run-demo.sh
```

Windows PowerShell:

```powershell
.\run-demo.ps1
```

The map uses OpenStreetMap tiles, so the basemap requires an internet
connection. The traffic simulation, routing, events, and vehicle positions run
locally.

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
| `GET /api/network/intersections` | list intersections |
| `POST /api/network/intersections` | add an intersection |
| `DELETE /api/network/intersections/{id}` | remove an intersection and its safe-to-remove roads |
| `GET /api/network/roads` | list roads |
| `POST /api/network/roads` | add a directed or two-way road |
| `DELETE /api/network/roads/{id}` | remove an unoccupied road |
| `PUT /api/network/roads/{id}/condition` | update road status and congestion |
| `GET /api/network/movements` | list explicit and inferred legal junction movements |
| `PUT /api/network/movements` | add or replace an explicit movement rule |
| `DELETE /api/network/movements` | remove an explicit movement rule |
| `GET /api/network/intersections/{id}/roads` | list incoming and outgoing roads |
| `GET /api/network/export` | download the current network as JSON |
| `POST /api/scenarios/import` | upload a geographic JSON map in multipart field `map` |
| `POST /api/routes` | compute route |
| `GET /api/vehicles` | list vehicles |
| `POST /api/vehicles` | create vehicle |
| `GET /api/events` | list events |
| `POST /api/events` | create traffic event |
| `GET /api/events/active` | list active events |
| `GET /api/events/road/{roadId}` | list events affecting one road |
| `PUT /api/events/{id}/status` | activate or resolve event |
| `DELETE /api/events/{id}` | remove event |
| `GET /api/scenarios` | list bundled complete run definitions |
| `POST /api/scenarios/{id}/activate` | load a scenario map, demand, vehicles, and events |
| `POST /api/simulation/initialize` | load generated demand plus optional scripted vehicles/events |
| `POST /api/simulation/start` | start simulation |
| `POST /api/simulation/pause` | pause simulation |
| `POST /api/simulation/resume` | resume simulation |
| `POST /api/simulation/reset` | reset simulation |
| `POST /api/simulation/tick` | advance one tick |
| `PUT /api/simulation/parameters` | set logical seconds per tick and playback interval |
| `GET /api/simulation/snapshot` | current simulation snapshot |
| `GET /api/simulation/diagnostics` | adaptive navigation, information, wait, U-turn, and deadlock evidence |
| `GET /api/simulation/stream` | live snapshot stream using Server-Sent Events |
| `GET /api/simulation/signals` | generated traffic-signal plans |
| `GET /api/simulation/trace/frames` | retained authoritative frame timeline |
| `GET /api/simulation/trace/frames/at?elapsedSeconds={s}` | frame at or immediately before a simulated second |
| `GET /api/simulation/trace/flow` | density, speed, and flow samples |
| `GET /api/simulation/trace/fundamental` | fundamental-diagram density bins |
| `GET /api/simulation/trace/roads/{roadId}/time-space` | vehicle trajectories on one road |
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
| `data/scenarios/district1_typical_day.json` | bundled seeded multi-flow demand run |
| `data/results/experiment_results.json` | reproducible experiment baseline |

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
`python3 tools/generate_osm_map.py`. This legacy generator still uses public
Overpass mirrors. See the [macro and physical map plan](docs/PHYSICAL_VIEW_PLAN.md)
for the proposed reproducible local-PBF replacement and physical-view contract.

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

Build the self-contained application JAR:

```powershell
mvn package
java -jar target/traffic-event-system-1.0-SNAPSHOT.jar
```

Optional environment variables:

| Variable | Default | Purpose |
|---|---:|---|
| `PORT` | `7070` | HTTP server port |
| `MAP_FILE` | `data/maps/district1_network.json` | initial map before a scenario is activated |
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
- [Student A network and persistence notes](docs/planning/Binh-network-persistence.md)
- [Student B routing notes](docs/planning/nguynie-routing-algorithms.md)
- [Student C vehicle and event notes](docs/planning/Hyo-vehicle-traffic_event.md)
- [Student D simulation/API/frontend notes](docs/planning/An-simulation-API-services-frontend.md)
