# Architecture

The Traffic Event System is a layered Java and React application. The backend
owns the road graph, routing, simulation clock, physical state, events, and
statistics. The frontend consumes REST endpoints and a Server-Sent Events
stream. React-Leaflet renders the geographic macro view; React and PixiJS render
the lane-level physical view. Neither renderer advances the simulation.

## Runtime layers

| Layer | Responsibility |
|---|---|
| Domain | Roads/lane geometry, vehicles, resolved demand plans, signals, routes, positioned events, and statistics records |
| Routing | Interchangeable BFS, Dijkstra, and A* strategies |
| Simulation | Seconds-based clock, demand spawning, authoritative building access links, multi-leg/dwell lifecycle, sub-step dynamics, adaptive navigation, deadlock detection/recovery, event overlays, immutable renderer-frame replay, diagnostics, and completed-trip history |
| Services | Use-case boundaries for networks, routing, vehicles, events, simulation, and statistics |
| API | Javalin controllers, DTOs, static frontend hosting, and the live SSE stream |
| Persistence | JSON/CSV map loading, complete scenario/demand loading, JSON export, validation, and synthetic grids |
| Integration | Optional OSRM road geometry with a straight-line fallback |
| Visualization | React-Leaflet macro map, React/PixiJS physical view, controls, editor, statistics, and experiment comparison |

`RoadNetwork` is the graph aggregate. It owns intersections and directed roads,
including incoming and outgoing adjacency indexes. `TrafficSimulator` is the
simulation aggregate. It owns the active vehicles and events, advances the
clock through `TickProcessor`, and archives a `CompletedTrip` when an arrived
vehicle exits the simulation.

Each directed `Road` carries a stable physical-road id, lane count, lane width,
class, and optional polyline. `RoadLayout` is the narrow read boundary used by
dynamics. `Junction` is derived from the network; degree-two shape points are
not treated as controlled intersections.

`VehicleDynamicsEngine` requests one-second-or-shorter physical sub-steps, capped
at 60 sub-steps per logical tick. All shipped UI paces stay at or below 30
simulated seconds per tick and therefore preserve that one-second bound; the API
accepts larger positive ticks, for which the 60-step safety cap takes priority.
Each sub-step captures immutable `TrafficState`, arbitrates
stop-line approaches (including `TrafficSignal` phases), plans all car-following
and lateral changes, then commits them. Cars, buses and emergency vehicles use
MOBIL; `Motorcycle` selects the dedicated continuous-corridor
`FilteringLaneChangeModel`. Its compact physical/driver profiles let it use safe
openings that reject a car without weakening target-follower braking safety.
Once a lateral change is accepted, `LateralMotionModel` advances a signed offset
continuously towards the selected lane centre or between-lane target instead of
teleporting sideways. That order preserves the existing vehicle-list
order-independence invariant.

At detail cadence `SimulationFrameHistory` captures each post-movement
sub-step. For large overview ticks the simulator retains at most three
intermediate physics frames plus the final frame, while `TickProcessor` always
reports exact state/event boundaries. This avoids copying the complete
730-road frame dozens of times per scheduler callback without losing a
transition. The bounded history is indexed by simulated milliseconds and
exposed by one `timelineId`; network edits reset that id because map geometry
is not copied into every frame.

`TrafficEventCoordinator` rebuilds road effects from a baseline plus all
still-active overlays, so events that overlap or resolve out of order do not
corrupt road state. `TickProcessor` also publishes event transitions through
`TrafficEventBus`, which remains an Observer extension boundary. The production
application does not subscribe `DynamicReroutingListener`; current adaptive
rerouting is instead coordinated by `AdaptiveNavigationCoordinator`, using
driver information access and latency, observed congestion, route savings,
cooldowns, and cause-specific stall state. This distinction matters: event
publication and route choice are related inputs, not the same mechanism.

`RoadNetwork` also owns the authoritative `JunctionMovementGraph`. Routing uses
edge state `(incoming road, junction)` and consumes the same legal movements as
junction arbitration and physical connector geometry. U-turns therefore require
a real directed reverse road plus a vehicle-type/scenario policy; neither an
ordinary nor emergency vehicle may invent wrong-way travel or bypass a red
signal. `BusStop` similarly belongs to one directed road and a fractional
position, not to every intermediate junction.

`VehicleDynamicsEngine` owns `DeadlockCoordinator`. Explicit wait resources form
a bipartite wait-for graph; persistent strongly connected cycles are confirmed
only after timed red/dwell waits are excluded. Strict recovery gates new entries
and grants one deterministic drain reservation through legal, physically free
space. It never teleports, overlaps or despawns a vehicle, and reports a closed
saturated cycle as unresolvable when no legal drain exists.

`MapImportService` validates a complete replacement graph before changing live
state. `ExperimentService` works on isolated networks and does not mutate the
live simulation.

## State ownership, snapshots, and failure boundaries

`TrafficSimulator` synchronizes aggregate reads and mutations. Continuous
playback runs on one scheduled executor thread, while API commands enter through
the same synchronized simulator boundary. Collection getters and
`SimulationSnapshot` copy their list containers, so callers cannot add or remove
entries from the simulator's lists.

That protection is intentionally not described as deep immutability:
`SimulationSnapshot` still refers to mutable `Vehicle`, `TrafficEvent`, and
`TrafficSignal` domain objects. It is suitable for immediately materializing a
REST DTO, but it is not the renderer/replay consistency contract.
`SimulationFrame` is that contract: it captures value records and copied
collections under the simulator boundary, and the SSE, frame-history, macro-map,
and physical-view paths read those immutable values.

If a scheduled tick throws a runtime failure, `SimulationServiceImpl` logs it at
`SEVERE`, attempts to move the simulator to `PAUSED`, cancels future scheduled
ticks, and publishes the resulting snapshot. If pausing itself fails, both
failures are logged and the runner remains cancelled. Fatal JVM `Error`s receive
the same cleanup before being rethrown. A failed scheduled tick therefore cannot
silently leave a nominal playback loop advancing.

## Current project structure

```text
Traffic-Event-System/
|-- data/
|   |-- maps/                     the District 1 road network (input)
|   |-- demand/                   replaceable parameter and curve tables
|   |-- scenarios/                complete seeded runs (input)
|   `-- results/                  generated output; traces land here
|-- docs/                         architecture, class diagram, algorithms, API, demo, results
|-- frontend/
|   |-- public/assets/            artwork slots: vehicles, surfaces, land, buildings (see ASSETS.md)
|   `-- src/
|       |-- api/trafficAPI.js     REST/SSE client
|       |-- components/
|       |   |-- TrafficMap.jsx
|       |   |-- DemandSchedulePanel.jsx
|       |   |-- NetworkEditor.jsx
|       |   |-- TimelineScrubber.jsx
|       |   `-- ExperimentPanel.jsx
|       |-- physical/                 PixiJS lane-level renderer
|       |   |-- PhysicalScene.jsx      owns the app, polls, follows the camera
|       |   |-- sceneTransform.js      metres <-> world units
|       |   |-- interpolate.js         smoothing between two live frames
|       |   |-- vehicleSprites.js      textures, baked or loaded
|       |   |-- assetManifest.js       every artwork slot
|       |   `-- layers/drawScene.js    ground, blocks, road, markings, signals
|       |-- hooks/useTrafficDashboard.js
|       |-- displayLabels.js             human-readable road/building labels
|       |-- simulationTime.js            24-hour clock/day-offset projection
|       |-- spawnPolicies.js             pluggable vehicle-request planning
|       |-- App.jsx
|       `-- styles.css
|-- src/main/java/team/traffic/
|   |-- api/                      server, routes, controllers, DTOs
|   |-- domain/
|   |   |-- demand/               sources, place resolution, plans, model strategies
|   |   |-- network/              roads, movements, intersections, bus stops, buildings, geo points
|   |   |-- vehicle/              hierarchy, dynamics, pose
|   |   |-- scene/                lane geometry derived for the physical view
|   |   |-- signal/ event/ routing/ statistics/
|   |-- experiment/               deterministic comparison runner
|   |-- observer/                 traffic-event Observer extension boundary
|   |-- persistence/              JSON/CSV loaders, saver, synthetic maps
|   |-- routing/                  BFS, Dijkstra, A*, registry, route snapshots
|   |-- service/                  interfaces and implementations
|   `-- simulation/               simulator, clock, navigation, deadlock, frame history, traces
|-- src/test/java/team/traffic/   domain, service, API, persistence, routing, simulation tests
|-- tools/generate_osm_map.py     District 1 OpenStreetMap dataset generator
|-- pom.xml
|-- run-demo.sh
`-- run-demo.ps1
```

`randomBuildingSpawnPolicy` is a frontend request-planning seam, not a second
simulation authority. It prefers a residential source, excludes a destination
on the same access node, checks directed reachability, and selects a
CAR/MOTORCYCLE type using the documented citywide registered-stock ratio as a
test proxy. `useTrafficDashboard` sends the normal building-form
`POST /api/vehicles` command. The backend still resolves the access pair,
computes the route, advances both driveway links and every road tick, and
publishes the pose rendered by both map modes.

Generated scenario traffic follows a separate backend-owned path:
`JsonScenarioLoader` resolves a map and parameter catalog,
`GravityDemandModel` deterministically expands source/flow data into exact
`TripPlan`s, and `DemandDrivenSpawner` creates due vehicles. `Vehicle` advances
the same identity through every resolved leg and dwell, while
`RETURN_TO_ORIGIN` plans end at the exact concrete origin.
`TripScheduleEstimator` projects those already-resolved plans onto a directed
free-flow timetable for a requested page of
`GET /api/simulation/demand/schedule`; it is a reader-facing estimate, never a
second runtime model. Full-day hourly/purpose/vehicle aggregates are computed
without routing every plan for each request.

The shipped scenario performs a five-minute initialization pre-roll through
the same demand spawner, signals, arbitration and microscopic dynamics as live
playback. Its intermediate frames and traces are intentionally discarded; the
resulting populated 06:05 state becomes the first frame of the interactive
session. This is scenario initialization, not frontend fast-forward.

On a normal launch, `App` loads and initializes the complete file selected by
`SCENARIO_FILE`, defaulting to `data/scenarios/district1_typical_day.json`,
before the HTTP server starts. If `SCENARIO_FILE` is unset and `MAP_FILE` is
explicitly set, startup is topology-only instead. Scenario activation through
the API repeats the complete map/configuration/demand initialization boundary.

## Live data flow

```mermaid
flowchart LR
    UI["React controls"] -->|manual commands| API["Javalin controllers"]
    UI --> SpawnPolicy["Reachable building spawn policy"]
    SpawnPolicy -->|POST vehicle request| API
    API --> Services["Service interfaces"]
    Services --> Simulator["TrafficSimulator"]
    Scenario["Scenario + parameter JSON"] --> Demand["TripDemandModel"]
    Demand --> Spawner["DemandDrivenSpawner"]
    Demand --> Schedule["Free-flow schedule projection"]
    Schedule --> API
    Spawner --> Simulator
    Simulator --> Graph["RoadNetwork"]
    Simulator --> Tick["TickProcessor"]
    Tick --> Dynamics["Lane + Krauss + MOBIL/filtering"]
    Tick --> Signals["TrafficSignal phases"]
    Tick --> Events["TrafficEventCoordinator overlays"]
    Events --> Bus["TrafficEventBus extension"]
    Tick --> Navigation["AdaptiveNavigationCoordinator"]
    Navigation --> AdaptiveRouter["Adaptive edge-state Dijkstra"]
    Tick --> Traces["Flow and time-space traces"]
    Tick --> Frames["Immutable renderer frames"]
    Frames -->|current SimulationFrame| SSE["SSE stream"]
    Frames -->|timeline + frame lookup| API
    SSE --> Macro["React + Leaflet macro map"]
    Graph --> PhysicalAPI["Physical-view REST"]
    Frames --> PhysicalAPI
    PhysicalAPI --> Physical["React + PixiJS physical view"]
    Simulator --> Trips["CompletedTrip history"]
    Trips --> Stats["StatisticsService"]
    Stats --> UI
```

The macro map consumes current or retained `SimulationFrame` data. The physical
view loads network-derived static geometry once per timeline, then polls a
lighter state response whose vehicles and signals come from the current frame
within the camera radius plus overscan. State coordinates are rebased onto the
retained static origin, and off-viewport vehicle sprites/trails are culled
before Pixi submission.
The Physical View is lazy-loaded, keeping PixiJS out of the initial Leaflet
dashboard bundle. PixiJS interpolates only between received poses for display
smoothness: it samples backend-provided lane/connector topology and blends
authoritative headings, but it does not run lane changes, collision handling,
junction arbitration, or a second physics simulation.

Building access is not frontend interpolation. `Vehicle` advances
`DEPARTING_ORIGIN` and `ARRIVING_DESTINATION` at 15 km/h without road
occupancy; `VehiclePoseResolver` publishes each authoritative body-centre pose.
The frontend may smooth only between those successive poses.

## Performance and GPU boundary

PixiJS 8 normally uses WebGL, so rasterization, transforms, and sprite
composition in the Physical View use the GPU when browser hardware
acceleration is available. The Java simulator remains deliberately CPU-owned:
routing, graph mutations, signal/right-of-way arbitration, stateful
car-following, events, and deterministic ordering do not map cleanly to a GPU
kernel at this scale. A GPU-compute rewrite would add transfer/synchronization
cost and a second implementation surface without fixing an interpolation or
data-contract bug.

The implemented controls target the actual bottlenecks:

- static Pixi geometry is retained once per timeline; the view is lazy-loaded
  and capped at 60 FPS;
- physical state is lightweight and polled at 250 ms while changing;
- physical state requests follow the camera viewport instead of serializing the
  district-wide fleet;
- while Physical View is mounted, the full `SimulationFrame` SSE and snapshot
  fallback are closed rather than parsed behind the overlay; a one-second
  lightweight status poll keeps backend RUNNING/PAUSED/READY, clock and pace
  truthful without carrying fleet/road arrays;
- SSE supplies the live head directly; the timeline index is refreshed at most
  once per wall second and statistics at most once every two seconds;
- renderer history retains 900 timestamps, vehicle traces 5,000 ticks, and
  flow history 100,000 samples;
- long-dwell agents remain in the simulator/statistics for their return leg but
  are omitted from renderer frames and traces until they re-depart;
- expired vehicles are removed as a batch before one final frame rather than
  copying one full scene per removal;
- generated plans are ordered once, due-plan intervals use binary bounds, and
  reusable origin/leg destination candidates and gravity weights are cached;
- the demand schedule endpoint routes only a bounded page (default 250, maximum
  1,000) instead of all 39,434 plans;
- **Real time**, **Balanced**, and **24 h overview** run at approximately 1×,
  10×, and 120× simulated time. A 24-hour demand scenario may use overview
  pace on the macro map; opening Physical View forces 0.25 s / 250 ms live
  cadence, so a 30-second overview tick is never used for turn inspection.

## OOP and design patterns

| Requirement | Implementation |
|---|---|
| Encapsulation | Domain objects validate and manage their own mutable state |
| Inheritance | `Vehicle` and `TrafficEvent` define shared abstract behavior |
| Polymorphism | Routing strategies and event/vehicle subclasses are used through abstractions |
| Abstraction | Core use-case controllers depend on service interfaces; scenario import, experiments and scene assembly also use focused concrete collaborators; routing uses `PathFindingStrategy` |
| Strategy | BFS/Dijkstra/A*, trip-demand models, car-following, lane-changing, and right-of-way policies |
| Observer | `TrafficEventBus`/`TrafficEventListener` as an event extension point; snapshot listeners feeding SSE |
| Factory | `VehicleFactory` creates car, motorcycle, bus, and emergency variants |
| MVC-like separation | React view, API/controllers, services, and domain model |

## Member contributions

| Member | Primary area |
|---|---|
| Student A | Road network and persistence |
| Student B | Routing algorithms |
| Student C | Vehicles and traffic events |
| Student D | Simulation, API, services, and frontend |

See [CLASS_DIAGRAM.md](CLASS_DIAGRAM.md) for the implemented class
relationships and [API_CONTRACT.md](API_CONTRACT.md) for the external HTTP
boundary. See [PHYSICAL_VIEW_PLAN.md](PHYSICAL_VIEW_PLAN.md) for local-PBF
ingestion roadmap notes and the background behind the microscopic renderer
boundary.
