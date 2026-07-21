# Class Diagram

The diagrams below show the implemented runtime classes at the level needed to
explain the OOP design. DTO getters and minor helper methods are omitted.

## Network and routing

```mermaid
classDiagram
    class RoadNetwork {
        -Map~String, Intersection~ intersections
        -Map~String, Road~ roads
        -Map~String, List~Road~~ outgoingRoads
        -Map~String, List~Road~~ incomingRoads
        +addIntersection(Intersection)
        +removeIntersection(String)
        +addRoad(Road)
        +removeRoad(String)
        +findOutgoingRoads(String) List~Road~
        +findIncomingRoads(String) List~Road~
        +updateRoadCondition(String, RoadCondition)
    }
    class Intersection {
        -String id
        -String name
        -double x
        -double y
        -Double latitude
        -Double longitude
    }
    class Road {
        -String id
        -String startIntersectionId
        -String endIntersectionId
        -double distance
        -double speedLimit
        -int capacity
        -int currentVehicleCount
        -RoadCondition condition
        +calculateTravelCost() double
        +isAvailable() boolean
    }
    class RoadCondition {
        -double congestionLevel
        -double vehicleDensity
        -RoadStatus roadStatus
    }
    class PathFindingStrategy {
        <<interface>>
        +getName() String
        +supports(RoutePreference) boolean
        +findRoute(RoadNetwork, RouteRequest) RouteResult
    }
    class BfsPathFindingStrategy
    class DijkstraPathFindingStrategy
    class AStarPathFindingStrategy
    class RoutingStrategyRegistry
    class RoutingService {
        <<interface>>
    }
    class RoutingServiceImpl

    RoadNetwork "1" *-- "*" Intersection
    RoadNetwork "1" *-- "*" Road
    Road --> RoadCondition
    PathFindingStrategy <|.. BfsPathFindingStrategy
    PathFindingStrategy <|.. DijkstraPathFindingStrategy
    PathFindingStrategy <|.. AStarPathFindingStrategy
    RoutingStrategyRegistry o-- PathFindingStrategy
    RoutingService <|.. RoutingServiceImpl
    RoutingServiceImpl --> RoutingStrategyRegistry
```

## Vehicles, simulation, and events

```mermaid
classDiagram
    class Vehicle {
        <<abstract>>
        -String id
        -String currentIntersectionId
        -String destinationIntersectionId
        -Route currentRoute
        -double currentSpeed
        -double roadProgress
        -List~String~ travelHistory
        -double distanceTravelledKm
        -double travelTimeSeconds
        +requestRoute(RoadNetwork, RoutingService)
        +advance(RoadNetwork, double)
        +recomputeRoute(RoadNetwork, RoutingService)
        +hasReachedDestination() boolean
        +getType()* VehicleType
    }
    class Car
    class Bus
    class EmergencyVehicle
    class VehicleFactory
    class TrafficEvent {
        <<abstract>>
        -String id
        -String affectedRoadId
        -long startTick
        -long durationTicks
        +apply(RoadNetwork)
        +resolve(RoadNetwork)
    }
    class CongestionEvent
    class AccidentEvent
    class RoadClosureEvent
    class TrafficLightDelayEvent
    class TrafficEventBus
    class DynamicReroutingListener
    class TrafficSimulator {
        -RoadNetwork network
        -List~Vehicle~ vehicles
        -List~TrafficEvent~ events
        -List~CompletedTrip~ completedTrips
        +initialize(List, List)
        +start()
        +pause()
        +resume()
        +reset()
        +advanceTick()
        +removeVehicle(String) boolean
        +getCompletedTrips() List~CompletedTrip~
    }
    class TickProcessor
    class SimulationClock
    class SimulationSnapshot
    class CompletedTrip {
        <<record>>
        +String vehicleId
        +VehicleType vehicleType
        +double travelTimeSeconds
        +double routeLengthKm
        +int routeRecalculationCount
    }

    Vehicle <|-- Car
    Vehicle <|-- Bus
    Vehicle <|-- EmergencyVehicle
    VehicleFactory --> Vehicle
    TrafficEvent <|-- CongestionEvent
    TrafficEvent <|-- AccidentEvent
    TrafficEvent <|-- RoadClosureEvent
    TrafficEvent <|-- TrafficLightDelayEvent
    TrafficEventBus --> DynamicReroutingListener
    TrafficSimulator *-- Vehicle
    TrafficSimulator *-- TrafficEvent
    TrafficSimulator *-- CompletedTrip
    TrafficSimulator --> TickProcessor
    TrafficSimulator --> SimulationClock
    TrafficSimulator --> SimulationSnapshot
```

## Services, persistence, and presentation

```mermaid
classDiagram
    class SimulationService {
        <<interface>>
        +getCompletedTrips() List~CompletedTrip~
    }
    class SimulationServiceImpl
    class StatisticsService {
        <<interface>>
    }
    class StatisticsServiceImpl
    class TrafficStatistics
    class AlgorithmStatistics
    class MapLoader {
        <<interface>>
        +load(String) RoadNetwork
    }
    class JsonMapLoader
    class CsvMapLoader
    class MapSaver
    class SyntheticMapGenerator
    class MapImportService
    class RoadGeometryProvider {
        <<interface>>
    }
    class OsrmRoadGeometryProvider
    class ExperimentService
    class ExperimentController
    class App
    class useTrafficDashboard
    class TrafficMap
    class NetworkEditor
    class ExperimentPanel

    SimulationService <|.. SimulationServiceImpl
    StatisticsService <|.. StatisticsServiceImpl
    StatisticsServiceImpl --> SimulationService
    StatisticsServiceImpl --> TrafficStatistics
    StatisticsServiceImpl --> AlgorithmStatistics
    MapLoader <|.. JsonMapLoader
    MapLoader <|.. CsvMapLoader
    MapImportService --> RoadGeometryProvider
    RoadGeometryProvider <|.. OsrmRoadGeometryProvider
    ExperimentController --> ExperimentService
    App --> useTrafficDashboard
    App --> TrafficMap
    App --> NetworkEditor
    App --> ExperimentPanel
```

The backend follows Strategy, Observer, and Factory patterns. The complete
layer responsibilities and runtime data flow are documented in
[ARCHITECTURE.md](ARCHITECTURE.md).
