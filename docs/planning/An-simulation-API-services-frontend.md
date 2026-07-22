
# Project's Architecture

```text
src/main/java/team/traffic/
├── App.java
├── config/
│   └── ApplicationConfig.java
├── api/
│   ├── TrafficApiServer.java
│   ├── ApiRouteRegistry.java
│   ├── controller/
│   │   ├── HealthController.java
│   │   ├── NetworkController.java
│   │   ├── RoutingController.java
│   │   ├── VehicleController.java
│   │   ├── TrafficEventController.java
│   │   ├── SimulationController.java
│   │   └── StatisticsController.java
│   └── dto/
│       ├── ApiResponse.java
│       ├── RouteRequestDto.java
│       ├── RouteResultDto.java
│       ├── VehicleDto.java
│       ├── TrafficEventDto.java
│       └── SimulationSnapshotDto.java
├── simulation/
│   ├── TrafficSimulator.java
│   ├── TickProcessor.java
│   ├── SimulationClock.java
│   ├── SimulationSnapshot.java
│   ├── SimulationCommand.java
│   └── SimulationStatus.java
├── service/
│   ├── RoadNetworkService.java
│   ├── RoutingService.java
│   ├── VehicleService.java
│   ├── TrafficEventService.java
│   ├── SimulationService.java
│   └── StatisticsService.java
│   └── impl/
│       ├── RoadNetworkServiceImpl.java
│       ├── RoutingServiceImpl.java
│       ├── VehicleServiceImpl.java
│       ├── TrafficEventServiceImpl.java
│       ├── SimulationServiceImpl.java
│       └── StatisticsServiceImpl.java
└── domain/
    └── statistics/
        ├── TrafficStatistics.java
        └── AlgorithmStatistics.java

frontend/src/
├── App.jsx
├── main.jsx
├── styles.css
├── api/
│   └── trafficApi.js
├── hooks/
│   ├── useRoadNetwork.js
│   ├── useSimulation.js
│   └── useAlgorithms.js
└── components/
    ├── MapViewport.jsx
    ├── ControlPanel.jsx
    ├── AlgorithmPanel.jsx
    ├── EventPanel.jsx
    ├── VehiclePanel.jsx
    └── StatisticsPanel.jsx
```
# Class Diagram
```mermaid
classDiagram
    class TrafficSimulator {
        -RoadNetwork roadNetwork
        -List~Vehicle~ vehicles
        -List~TrafficEvent~ events
        -SimulationClock clock
        -SimulationStatus status
        +start()
        +pause()
        +resume()
        +reset()
        +advanceOneTick()
        +createSnapshot() SimulationSnapshot
    }

    class TickProcessor {
        +processTick(TrafficSimulator)
        +moveVehicles()
        +processEvents()
        +updateCongestion()
    }

    class SimulationClock {
        -long currentTick
        -double elapsedSeconds
        +advance(double simulationSeconds)
        +reset()
        +getCurrentTick() long
        +getElapsedSeconds() double
    }

    class SimulationSnapshot {
        -long tick
        -double elapsedSeconds
        -SimulationStatus status
        -List~Vehicle~ vehicles
        -List~TrafficSignal~ signals
        -RoadNetwork roadNetwork
        -TrafficStatistics statistics
    }

    class SimulationStatus {
        <<enumeration>>
        READY
        RUNNING
        PAUSED
        FINISHED
    }

    TrafficSimulator --> RoadNetwork
    TrafficSimulator --> Vehicle
    TrafficSimulator --> TrafficEvent
    TrafficSimulator --> SimulationClock
    TrafficSimulator --> SimulationSnapshot
    TrafficSimulator --> SimulationStatus
    TickProcessor --> TrafficSimulator

    class TrafficStatistics {
        -int totalVehicles
        -int arrivedVehicles
        -double averageTravelTime
        -double averageCongestionLevel
        -int blockedRoadCount
    }

    class AlgorithmStatistics {
        -String algorithmName
        -long averageComputationTime
        -int routeRecalculationCount
        -double averageRouteCost
        -int successfulRoutes
        -int failedRoutes
    }

    SimulationSnapshot --> TrafficStatistics

    class RoadNetworkService {
        <<interface>>
    }

    class RoutingService {
        <<interface>>
    }

    class VehicleService {
        <<interface>>
    }

    class TrafficEventService {
        <<interface>>
    }

    class SimulationService {
        <<interface>>
    }

    class StatisticsService {
        <<interface>>
    }

    class RoadNetworkServiceImpl
    class RoutingServiceImpl
    class VehicleServiceImpl
    class TrafficEventServiceImpl
    class SimulationServiceImpl
    class StatisticsServiceImpl

    RoadNetworkService <|.. RoadNetworkServiceImpl
    RoutingService <|.. RoutingServiceImpl
    VehicleService <|.. VehicleServiceImpl
    TrafficEventService <|.. TrafficEventServiceImpl
    SimulationService <|.. SimulationServiceImpl
    StatisticsService <|.. StatisticsServiceImpl

    RoutingServiceImpl --> RoutingStrategyRegistry
    VehicleServiceImpl --> VehicleFactory
    TrafficEventServiceImpl --> TrafficEventBus
    SimulationServiceImpl --> TrafficSimulator
    StatisticsServiceImpl --> TrafficStatistics
    StatisticsServiceImpl --> AlgorithmStatistics
    ```

## 2026 Dynamics Integration Update

The service and frontend ownership boundaries above remain unchanged. The
cross-layer contract now uses simulation seconds for event timing and exposes
the physical state required by the lane-aware dynamics owned by Student C:

- snapshots include `elapsedSeconds`, lane-resolved vehicle poses, and active
  junction signals;
- event create/update payloads use `startTimeSeconds`, `durationSeconds`,
  `positionAlongRoad`, and optional `blockedLaneIndexes`;
- `BREAKDOWN` targets a runtime vehicle, while traffic lights are persistent
  `TrafficSignal` domain objects rather than road events;
- the network editor accepts road name, lane count, and lane width while
  preserving the existing routing/capacity fields;
- flow samples, fundamental-diagram bins, and per-road time-space points are
  available below `/api/simulation/trace`.

The old `startTick` and `durationTicks` names remain input-only migration
aliases. Responses and the React models always use seconds so the public API
does not expose two competing time units.
