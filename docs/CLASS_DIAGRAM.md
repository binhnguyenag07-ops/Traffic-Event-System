# Implemented Class Diagram

The diagram focuses on the runtime domain after the lane, signal, and event
upgrades. API DTOs and persistence data carriers are omitted for readability.

```mermaid
classDiagram
    class RoadNetwork {
        +getRoad(id) Road
        +findIncomingRoads(id) List~Road~
        +findRoadsByPhysicalRoadId(id) List~Road~
    }
    class Road {
        -String id
        -String name
        -String physicalRoadId
        -int laneCount
        -double laneWidthMetres
        -RoadClass roadClass
        -List~GeoPoint~ geometry
        +getRoadWidthMetres() double
    }
    RoadNetwork "1" *-- "many" Road

    class RoadLayout {
        <<interface>>
        +laneCountOf(Road) int
        +laneWidthMetresOf(Road) double
    }
    class RoadDataRoadLayout
    class RoadClassRoadLayout
    RoadLayout <|.. RoadDataRoadLayout
    RoadLayout <|.. RoadClassRoadLayout

    class Vehicle {
        <<abstract>>
        -int laneIndex
        -double roadProgress
        -VehicleStatus status
        -JourneyPhase accessLinkPhase
        -double accessLinkTravelledMetres
        +planMovement(...) Optional~MovementPlan~
        +commitMovement(...)
        +planJunctionApproach(...) Optional~JunctionApproach~
        +assignTripPlan(TripPlan)
        +advanceBuildingAccessLink(...)
        +getJourneyPhase() JourneyPhase
        +stop()
        +resumeFromStop()
    }
    class JourneyPhase
    class Car
    class Motorcycle
    class Bus {
        -int passengerCapacity
        -List~BusStop~ stops
    }
    class EmergencyVehicle
    Vehicle <|-- Car
    Vehicle <|-- Motorcycle
    Vehicle <|-- Bus
    Vehicle <|-- EmergencyVehicle
    Vehicle --> JourneyPhase

    class TrafficState {
        +occupancyOf(roadId) RoadOccupancy
        +requestCrossing(JunctionApproach)
        +resolveJunctions()
    }
    class RoadOccupancy {
        +lane(index) Lane
        +bestLaneForEntry(vehicle) int
    }
    class Lane {
        +findLeaderFor(vehicle) LeaderGap
        +canAdmit(vehicle) boolean
    }
    class LaneChangeModel {
        <<interface>>
        +chooseTargetLane(request) int
    }
    class MobilLaneChangeModel
    class MotorcycleLaneChangeModel
    class CarFollowingModel {
        <<interface>>
        +nextSpeedMps(context) double
    }
    class KraussCarFollowingModel
    TrafficState --> RoadOccupancy
    RoadOccupancy "1" *-- "many" Lane
    LaneChangeModel <|.. MobilLaneChangeModel
    LaneChangeModel <|.. MotorcycleLaneChangeModel
    Motorcycle --> MotorcycleLaneChangeModel
    CarFollowingModel <|.. KraussCarFollowingModel
    Vehicle --> LaneChangeModel
    Vehicle --> CarFollowingModel

    class TrafficEvent {
        <<abstract>>
        -double startTimeSeconds
        -double durationSeconds
        -double positionAlongRoad
        -Set~Integer~ blockedLaneIndexes
        +apply(RoadNetwork)
        +resolve(RoadNetwork)
    }
    class CongestionEvent
    class AccidentEvent {
        -String causingVehicleId
        -Set~String~ involvedVehicleIds
    }
    class BreakdownEvent {
        -String affectedVehicleId
    }
    class RoadClosureEvent
    class TrafficEventCoordinator
    TrafficEvent <|-- CongestionEvent
    TrafficEvent <|-- AccidentEvent
    TrafficEvent <|-- BreakdownEvent
    TrafficEvent <|-- RoadClosureEvent
    TrafficEventCoordinator --> TrafficEvent

    class TrafficSignal {
        -String junctionId
        -List~SignalPhase~ phases
        +isGreenFor(roadId, elapsedSeconds) boolean
    }
    class SignalPhase {
        +Set~String~ greenIncomingRoadIds
        +double durationSeconds
    }
    class SignalPlan
    TrafficSignal "1" *-- "many" SignalPhase
    SignalPlan --> TrafficSignal

    class VehicleDynamicsEngine {
        +step(...) TrafficState
    }
    class CollisionDetector
    class TickProcessor
    class TrafficSimulator
    class FlowRecorder
    class VehicleTraceStore
    TrafficSimulator *-- TickProcessor
    TickProcessor *-- VehicleDynamicsEngine
    TickProcessor *-- CollisionDetector
    TrafficSimulator *-- FlowRecorder
    TrafficSimulator *-- VehicleTraceStore
    TrafficSimulator o-- TrafficSignal
```

`TrafficLightDelayEvent` and `TrafficLightWait` were removed. Signals now belong
to junctions, repeat by phase, and participate in stop-line arbitration. Event
time is expressed in seconds; integer ticks remain only as deterministic trace
indexes.

## Added since this diagram was drawn

Lateral movement and sub-lane filtering, in `domain/vehicle/dynamics`:

```mermaid
classDiagram
    class LateralExtent {
        +centreMetres double
        +widthMetres double
        +overlaps(other, clearance) boolean
        +clearGapTo(other) double
    }
    class LateralTarget {
        +laneIndex int
        +offsetMetres double
    }
    class LateralMotionModel {
        +nextOffsetMetres(...)$ double
        +hasSettled(...)$ boolean
    }
    class FilteringRules {
        +cappedSpeedMps(...) double
    }
    class LaneChangeModel {
        <<interface>>
        +chooseTarget(request) LateralTarget
    }
    class MobilLaneChangeModel
    class FilteringLaneChangeModel
    LaneChangeModel <|.. MobilLaneChangeModel
    LaneChangeModel <|.. FilteringLaneChangeModel
    FilteringLaneChangeModel *-- FilteringRules
    LaneChangeModel ..> LateralTarget
    TrafficParticipant ..> LateralExtent
```

`Vehicle` state, extracted so values that are only meaningful together live
together:

```mermaid
classDiagram
    class Vehicle
    class RoadPlacement {
        +enter(...)
        +release()
        +moveTo(...)
    }
    class RoutePlan {
        +stepAt(offset) RouteStep
        +advance()
        +isExhausted() boolean
    }
    Vehicle *-- RoadPlacement
    Vehicle *-- RoutePlan
    Vehicle *-- TripRecord
```

Demand generation and the resolved multi-leg runtime boundary:

```mermaid
classDiagram
    class TripDemandModel {
        <<interface>>
        +tripsBeginningIn(from, to) List~TripPlan~
    }
    class GravityDemandModel
    class ScriptedDemandModel
    class DemandScenario
    class DemandSource
    class FlowSpec
    class PlaceResolver
    class TripPlan
    class TripLeg
    class DemandDrivenSpawner
    class TripScheduleEstimator
    class Vehicle
    TripDemandModel <|.. GravityDemandModel
    TripDemandModel <|.. ScriptedDemandModel
    GravityDemandModel --> DemandScenario
    DemandScenario *-- DemandSource
    DemandSource *-- FlowSpec
    GravityDemandModel --> PlaceResolver
    GravityDemandModel --> TripPlan
    TripPlan *-- TripLeg
    DemandDrivenSpawner --> TripDemandModel
    DemandDrivenSpawner --> Vehicle
    TripScheduleEstimator --> TripPlan
    Vehicle o-- TripPlan
```

`FlowSpec.purpose` is an open `PurposeId`, while destination selectors are
resolved once into concrete places before a vehicle is spawned. The spawner is
therefore independent of whether plans came from building gravity, a scripted
list or a future OD matrix. `TripScheduleEstimator` is a read-only directed
free-flow projection for the schedule API; runtime arrival remains owned by
`Vehicle` and `TickProcessor`.

Scene geometry, in `domain/scene`, derived from the network for the physical
view and never stored:

```mermaid
classDiagram
    class LaneGeometryBuilder {
        +lanesOf(road) List~LaneGeometry~
        +stopLineOf(road) StopLine
        +connectorsAt(junctionId) List~LaneConnector~
    }
    class PhysicalSceneAssembler {
        +sceneAround(lat, lon, radius, vehicles, signals) PhysicalScene
    }
    class PhysicalScene
    class LaneGeometry
    class StopLine
    class LaneConnector
    class JunctionGeometry
    class SceneBuilding
    class SceneSignal
    class SceneVehicle
    PhysicalSceneAssembler *-- LaneGeometryBuilder
    PhysicalSceneAssembler ..> PhysicalScene
    PhysicalScene *-- LaneGeometry
    PhysicalScene *-- StopLine
    PhysicalScene *-- LaneConnector
    PhysicalScene *-- JunctionGeometry
    PhysicalScene *-- SceneBuilding
    PhysicalScene *-- SceneSignal
    PhysicalScene *-- SceneVehicle
```

`domain/scene` sits beside `domain/network` and `domain/vehicle` rather than
under either: it consumes `RoadLayout` and `PolylineGeometry` from
`domain/vehicle` while reading the network, and a package under `network`
reaching up into `vehicle` would invert the layering.

`SimulationClock` gained a time of day, so a demand model can ask what hour it
is. The clock time is derived from elapsed seconds and a published start offset,
never stored per frame.
