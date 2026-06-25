# Backend Progress Audit

Date: 2026-06-25

## Purpose

This document verifies Student C's claim:

> Students A, B, and C accidentally worked ahead, so the backend for weeks 4, 5, 6, and 7 is almost finished, while the frontend is still around week 3.

Audit scope:

- Use the team's roadmap image as the source of truth for weekly progress.
- Use `OOP - Group Project.pdf` as the source of truth for project requirements and expected functionality.
- Use the current repository code and tests as implementation evidence.

Note: the timeline inside the PDF is not used to judge schedule progress. The weekly progress comparison is based on the team's roadmap image.

## Executive Summary

Student C is partially correct. Students A, B, and C have implemented several backend foundations ahead of the frontend: road density, blocked and congested road states, BFS/Dijkstra/A*, congestion-aware routing, vehicle domain movement, traffic event domain classes, observer/factory/strategy patterns, and many unit tests.

However, the claim that the backend for weeks 4 to 7 is "almost finished" is too optimistic at application level. Many important classes in the service, simulation, API, DTO, and statistics layers are still empty or not wired together.

Team-level conclusion:

> The backend has a strong foundation and some modules are ahead of the frontend, but the basic project should not be considered finished yet. The team should first complete a minimal end-to-end demo, then accelerate frontend work and improve the project quality.

## Test Result

The full Maven test suite was run:

```text
mvn test
tests=201 failures=0 errors=0 skipped=0
```

This is a strong positive signal. It shows that the currently tested domain, persistence, and routing code is stable.

However, passing tests does not prove that the backend is complete, because several important modules still have no implementation and therefore no real tests.

## Progress Against The Team Roadmap

### Student A

| Week | Roadmap Item | Current Status | Evidence |
|---|---|---|---|
| 4 | Road density | Done | `RoadNetwork.recordVehicleEnteringRoad`, `recordVehicleLeavingRoad`, `updateVehicleDensity`, `getAverageVehicleDensity`, `findRoadsAtOrAboveDensity` |
| 5 | Blocked/congested states | Done | `RoadStatus`, `RoadCondition`, `Road.isAvailable`, routing skips blocked and closed roads |
| 6 | Scenario maps | Partial | `density_demo.json` and `small_city.json` exist; `congestion_demo.json` and `accident_demo.json` are empty |
| 7 | Unit tests | Mostly done for owned modules | Network, persistence, density, and related routing tests pass |

Assessment: Student A is genuinely ahead on backend foundation work. The main missing piece is completing scenario maps for event, congestion, and accident demos.

### Student B

| Week | Roadmap Item | Current Status | Evidence |
|---|---|---|---|
| 4 | Dynamic routing | Partial | Vehicles can call route recomputation, but no simulation service automatically triggers rerouting |
| 5 | Avoid bad roads | Done at algorithm level | `RouteBuilder.isRoadUsable` filters unavailable roads; BFS, Dijkstra, and A* use it |
| 6 | Algorithm metrics | Partial | `RouteResult` stores computation time and visited nodes; `AlgorithmStatistics` is empty |
| 7 | Algorithm comparison table | Not done | No statistics module or comparison report/table exists yet |

Assessment: Student B has strong algorithm work and good test coverage. However, metrics and comparison features are still not complete as application features.

### Student C

| Week | Roadmap Item | Current Status | Evidence |
|---|---|---|---|
| 4 | Vehicle movement | Partial | `Vehicle.moveOneStep` exists and is tested; `VehicleService`/`VehicleServiceImpl` are not implemented |
| 5 | Accident/congestion/closure | Done at domain level, not done at app level | `AccidentEvent`, `CongestionEvent`, `RoadClosureEvent`, and `TrafficLightDelayEvent` exist; event service and API are empty |
| 6 | Multi-vehicle demo | Not done in backend | No implemented simulation engine or vehicle service manages multiple vehicles |
| 7 | Event tests | Done at unit level | `TrafficEventTest` covers event apply/resolve behavior |

Assessment: Student C has implemented important domain classes, but the feature is not yet a runnable backend workflow. Events are not triggered by simulation ticks, and vehicles are not automatically notified or rerouted.

## Backend Gaps

The following source files are currently empty or effectively not implemented:

```text
src/main/java/team/traffic/api/controller/SimulationController.java
src/main/java/team/traffic/api/controller/StatisticsController.java
src/main/java/team/traffic/api/controller/TrafficEventController.java
src/main/java/team/traffic/api/controller/VehicleController.java
src/main/java/team/traffic/api/dto/SimulationSnapshotDto.java
src/main/java/team/traffic/api/dto/TrafficEventDto.java
src/main/java/team/traffic/api/dto/VehicleDto.java
src/main/java/team/traffic/domain/statistics/AlgorithmStatistics.java
src/main/java/team/traffic/domain/statistics/TrafficStatistics.java
src/main/java/team/traffic/service/SimulationService.java
src/main/java/team/traffic/service/StatisticsService.java
src/main/java/team/traffic/service/TrafficEventService.java
src/main/java/team/traffic/service/impl/SimulationServiceImpl.java
src/main/java/team/traffic/service/impl/StatisticsServiceImpl.java
src/main/java/team/traffic/service/impl/TrafficEventServiceImpl.java
src/main/java/team/traffic/service/impl/VehicleServiceImpl.java
src/main/java/team/traffic/simulation/SimulationClock.java
src/main/java/team/traffic/simulation/SimulationCommand.java
src/main/java/team/traffic/simulation/SimulationSnapshot.java
src/main/java/team/traffic/simulation/SimulationStatus.java
src/main/java/team/traffic/simulation/TickProcessor.java
src/main/java/team/traffic/simulation/TrafficSimulator.java
```

`VehicleService.java` currently exists as a nearly empty class. It should likely become an interface, matching the existing `RoadNetworkService` and `RoutingService` pattern.

## Functional Issues

### 1. Simulation engine is missing

The PDF requires the final application to have a simulation engine that updates state, advances time, processes vehicle movement, handles traffic events, and collects statistics. The classes in the `simulation` package are still placeholders.

Impact: the project cannot yet demonstrate a real traffic simulator.

### 2. Traffic events are not connected to runtime flow

The event domain can `apply` and `resolve` road conditions, but there is no implemented `TrafficEventService` or `TickProcessor` to trigger events at the correct tick.

Impact: accidents, congestion, and closures exist as objects, but not as running simulation scenarios.

### 3. Dynamic rerouting is not automatic

`Vehicle.recomputeRoute(...)` exists, but no simulation or service layer automatically calls it when a road becomes blocked, congested, closed, or affected by an accident.

Impact: route recomputation is possible, but it is not yet dynamic rerouting inside the application.

### 4. Multiple vehicles are not managed by the backend

Vehicle domain objects exist, but `VehicleServiceImpl` is empty. There is no backend registry for vehicles, no API for creating vehicles, no API for listing vehicles, and no simulation loop that moves multiple vehicles.

Impact: the Week 6 multi-vehicle demo is not complete.

### 5. Statistics are missing

`TrafficStatistics` and `AlgorithmStatistics` are empty. The PDF expects metrics such as average travel time, total vehicles, congestion metrics, algorithm computation time, route recalculation count, blocked roads, and algorithm comparison charts/tables.

Impact: the statistics panel and experiment summary cannot yet be backed by real backend data.

### 6. Frontend and backend API contracts do not match

The frontend API client already calls endpoints such as:

```text
/api/vehicles
/api/events
/api/simulation/start
/api/simulation/tick
/api/statistics
/api/network/roads
/api/network/generate
/api/routing/algorithms
```

The backend currently registers only:

```text
/api/health
/api/network
/api/network/intersections
/api/network/intersections/{id}
/api/routes
```

Impact: many frontend components cannot work against the current backend.

## Existing Strengths

The project already has a strong base:

- Road graph, intersections, roads, and graph operations exist.
- JSON/CSV map loading exists and is tested.
- Synthetic map generation exists.
- Road density and capacity behavior exist.
- Blocked, closed, congested, and open road states exist.
- BFS, Dijkstra, and A* exist and are tested.
- Congestion-aware route cost exists.
- Vehicle movement and route recomputation methods exist.
- Traffic event domain classes exist.
- Observer pattern foundation exists through `TrafficEventBus`.
- Factory pattern foundation exists through `VehicleFactory`.
- Strategy pattern foundation exists through routing strategies.
- The full test suite currently passes.

This means the team does not need to restart. The remaining work is mostly integration and application-level behavior.

## Recommended Plan

### Phase 1: Finish a basic runnable backend

Goal: make the app demonstrate the main PDF requirements at a basic level.

Required tasks:

1. Implement `SimulationStatus`, `SimulationClock`, `SimulationSnapshot`, `TrafficSimulator`, and `TickProcessor`.
2. Implement `VehicleService` and `VehicleServiceImpl`.
3. Implement `TrafficEventService` and `TrafficEventServiceImpl`.
4. Implement `SimulationService` and `SimulationServiceImpl`.
5. Implement `TrafficStatistics`, `AlgorithmStatistics`, `StatisticsService`, and `StatisticsServiceImpl`.
6. Add DTOs for vehicles, events, snapshots, and statistics.
7. Add controllers for vehicles, events, simulation, and statistics.
8. Register the new controllers in `ApiRouteRegistry`.
9. Add tests for service and simulation behavior.

Minimum demo flow:

```text
load map
create multiple vehicles
create traffic events
start simulation
advance one tick
pause/resume/reset
apply event effects
reroute affected vehicles
show statistics
```

### Phase 2: Align frontend with real backend endpoints

Goal: the frontend should call only real endpoints, or the backend should implement the endpoints the frontend already expects.

Required tasks:

1. Add a backend endpoint for `/api/network/roads`.
2. Decide whether `/api/network/load`, `/api/network/generate`, and `/api/network/save` are in scope for the basic demo.
3. Connect the vehicle panel to real vehicle endpoints.
4. Connect the event panel to real event endpoints.
5. Connect the control panel to real simulation endpoints.
6. Connect the statistics panel to real statistics endpoints.
7. Hide or remove frontend controls for features that do not have backend support yet.

### Phase 3: Improve the project after the baseline works

Only move to this phase after Phase 1 and Phase 2 produce an end-to-end demo.

Possible upgrades:

- better map visualization;
- vehicle animation;
- congestion heatmap;
- richer scenario maps;
- algorithm comparison table;
- experiment charts;
- large-scale synthetic networks;
- stable presentation demo;
- clearer README and setup instructions.

## Opinion On Student C's Proposal

Student C's proposal has a good direction: if the basic project is stable, the team should stop only chasing "correctness" and start improving quality and polish.

However, the condition "the basic project is stable" is not true yet. The backend has several domain modules ahead of schedule, but the full backend application is not done. Ending the basic project now would be risky because the project still lacks simulation, statistics, event runtime flow, vehicle services, and API integration.

Recommended team decision:

> Do not declare the basic project finished yet. Spend the next sprint closing backend integration gaps and connecting the frontend to real endpoints. Once there is a complete end-to-end demo, it becomes reasonable to accelerate frontend polish and advanced improvements.

## Final Verdict

Student C is partially correct, but the claim is overstated.

Correct:

- A, B, and C have implemented many backend domain foundations ahead of the frontend.
- The tested backend foundation is currently stable.
- The project has enough backend base to accelerate frontend work soon.

Not correct:

- The backend for weeks 4 to 7 is not almost complete at application level.
- Simulation, multi-vehicle runtime, traffic event orchestration, statistics, and several APIs are still missing.

Actionable conclusion:

> Finish the minimal end-to-end backend first, then accelerate frontend work and project polish.
