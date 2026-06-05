# Planning for project's architecture & class diagram in module vehicle & traffic event
## Project's Architecture

```
src/main/java/team/traffic/
├── domain/
│   ├── vehicle/
│   │   ├── Vehicle.java (abstract class)
│   │   ├── Car.java
│   │   ├── Bus.java
│   │   ├── EmergencyVehicle.java
│   │   ├── VehicleFactory.java
│   │   ├── VehicleType.java (enum)
│   │   └── VehicleStatus.java (enum)
│   │
│   └── event/
│       ├── TrafficEvent.java (abstract class)
│       ├── CongestionEvent.java
│       ├── AccidentEvent.java
│       ├── RoadClosureEvent.java
│       ├── TrafficLightDelayEvent.java
│       ├── TrafficEventType.java (enum)
│       └── TrafficEventStatus.java (enum)
│
└── observer/
    ├── TrafficEventBus.java
    └── TrafficEventListener.java (interface)
```
## Class Diagram

```mermaid
classDiagram
    class Vehicle {
        <<abstract>>
        -String id
        -String currentIntersectionId
        -String destinationIntersectionId
        -Route currentRoute
        -double movementSpeed
        -VehicleStatus status
        -List~String~ travelHistory
        +getType()* VehicleType
        +requestRoute(RoutingService)
        +moveOneStep()
        +recomputeRoute(RoutingService)
        +hasReachedDestination() boolean
    }

    class Car {
        +getType() VehicleType
    }

    class Bus {
        -int passengerCapacity
        +getType() VehicleType
    }

    class EmergencyVehicle {
        -int priorityLevel
        +getType() VehicleType
    }

    class VehicleFactory {
        +createVehicle(VehicleType, String, String) Vehicle
        +createCar(String, String) Car
        +createBus(String, String) Bus
        +createEmergencyVehicle(String, String) EmergencyVehicle
    }

    class VehicleType {
        <<enumeration>>
        CAR
        BUS
        EMERGENCY
    }

    class VehicleStatus {
        <<enumeration>>
        WAITING
        MOVING
        ARRIVED
        BLOCKED
    }

    Vehicle <|-- Car
    Vehicle <|-- Bus
    Vehicle <|-- EmergencyVehicle
    Vehicle --> Route
    Vehicle --> VehicleStatus
    Vehicle --> VehicleType
    VehicleFactory --> Vehicle

    class TrafficEvent {
        <<abstract>>
        -String id
        -String affectedRoadId
        -long startTick
        -long durationTicks
        -TrafficEventStatus status
        +getType()* TrafficEventType
        +apply(RoadNetwork)
        +resolve(RoadNetwork)
        +isActive(long) boolean
    }

    class CongestionEvent {
        -double congestionIncrease
    }

    class AccidentEvent {
        -double capacityReduction
        -String causingVehicleId
    }

    class RoadClosureEvent

    class TrafficLightDelayEvent {
        -long delayTicks
    }

    class TrafficEventType {
        <<enumeration>>
        CONGESTION
        ACCIDENT
        ROAD_CLOSURE
        TRAFFIC_LIGHT_DELAY
    }

    class TrafficEventStatus {
        <<enumeration>>
        SCHEDULED
        ACTIVE
        RESOLVED
    }

    TrafficEvent <|-- CongestionEvent
    TrafficEvent <|-- AccidentEvent
    TrafficEvent <|-- RoadClosureEvent
    TrafficEvent <|-- TrafficLightDelayEvent
    TrafficEvent --> TrafficEventType
    TrafficEvent --> TrafficEventStatus

    class TrafficEventListener {
        <<interface>>
        +onTrafficEvent(TrafficEvent)
    }

    class TrafficEventBus {
        -List~TrafficEventListener~ listeners
        +subscribe(TrafficEventListener)
        +unsubscribe(TrafficEventListener)
        +publish(TrafficEvent)
    }

    TrafficEventBus --> TrafficEventListener
    TrafficEventBus --> TrafficEvent