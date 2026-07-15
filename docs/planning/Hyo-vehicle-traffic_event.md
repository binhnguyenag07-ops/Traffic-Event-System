# Student C Planning: Vehicle And Traffic Events

Student C owns the vehicle model, traffic-event model, observer event bus, and
the backend workflow that connects events to vehicle movement and rerouting.

## Scope

| Roadmap week | Student C responsibility | Current status |
|---|---|---|
| 1 | Vehicle/event design | Done |
| 2 | Vehicle model | Done |
| 3 | Vehicle route request | Done |
| 4 | Vehicle movement | Done |
| 5 | Accident/congestion/closure | Done |
| 6 | Multi-vehicle demo support | Done in backend/simulation |
| 7 | Event tests | Done |
| 8 | OOP docs | Done in planning, architecture, and class-diagram docs |
| Post-plan extension | Vehicle physical profile, continuous movement, and position snapshot | Done |

## Implemented Packages

```text
src/main/java/team/traffic/
|-- domain/
|   |-- vehicle/
|   |   |-- Vehicle.java
|   |   |-- Car.java
|   |   |-- Bus.java
|   |   |-- EmergencyVehicle.java
|   |   |-- VehicleFactory.java
|   |   |-- VehicleType.java
|   |   `-- VehicleStatus.java
|   `-- event/
|       |-- TrafficEvent.java
|       |-- CongestionEvent.java
|       |-- AccidentEvent.java
|       |-- RoadClosureEvent.java
|       |-- TrafficLightDelayEvent.java
|       |-- TrafficEventType.java
|       `-- TrafficEventStatus.java
|-- observer/
|   |-- TrafficEventBus.java
|   `-- TrafficEventListener.java
|-- service/
|   |-- VehicleService.java
|   |-- TrafficEventService.java
|   `-- impl/
|       |-- VehicleServiceImpl.java
|       `-- TrafficEventServiceImpl.java
|-- routing/
|   `-- DynamicReroutingListener.java
`-- simulation/
    |-- TrafficSimulator.java
    |-- TickProcessor.java
    |-- VehicleConfig.java
    `-- EventConfig.java
```

## Vehicle Responsibilities

`Vehicle` is the abstract base class for `Car`, `Bus`, and
`EmergencyVehicle`.

Implemented behavior:

- validates vehicle id, current intersection, destination, and movement speed;
- validates a physical vehicle profile: length, width, maximum speed, current
  speed, acceleration, deceleration, weight, and dispatch priority;
- stores current route, route preference, movement status, and travel history;
- requests routes through `RoutingService`;
- recomputes routes when a route becomes blocked or stale;
- moves continuously along one road using `currentSpeed`, road distance, and a
  simulation duration in seconds;
- stores the active road, segment endpoints, and normalized road progress while
  a vehicle travels between intersections;
- updates road vehicle counts through `RoadNetwork` when entering and actually
  leaving a road;
- becomes `ARRIVED` at the destination;
- becomes `BLOCKED` when no usable route or road is available.

## Student C Extension: Vehicle Physical Profile

The original roadmap and class diagram require the vehicle hierarchy but do not
specify physical dimensions or kinematics. Student C extends the shared
`Vehicle` API with a validated, type-specific physical profile. Existing
constructors, `movementSpeed`, APIs, routing, density, and simulation behavior
remain compatible.

| Property | Unit | Default profile |
|---|---:|---|
| `length` | metres | car 4.5, bus 12.0, emergency 5.0 |
| `width` | metres | car 1.8, bus 2.5, emergency 2.0 |
| `maxSpeed` / `currentSpeed` | km/h | car 120 / 50, bus 90 / 35, emergency 140 / 70 |
| `acceleration` / `deceleration` | m/s2 | car 2.5 / 6.5, bus 1.0 / 5.0, emergency 3.0 / 7.5 |
| `vehicleWeight` | kg | car 1,500, bus 12,000, emergency 2,500 |
| `priorityLevel` | level | standard vehicles 0; emergency supplied by caller |

`accelerate(seconds)` and `decelerate(seconds)` enforce valid speed bounds.
Movement now consumes physical time: each default simulation tick represents 60 seconds
and increases `roadProgress` by `(effectiveSpeed * seconds / 3600) / distance`.
`effectiveSpeed` is capped by the road speed limit. The vehicle only updates
`currentIntersectionId` after `roadProgress` reaches `1.0`, preventing the
former endpoint teleport.

While travelling, snapshots expose `currentRoadId`, segment start/end
intersections, `roadProgress`, the current route, `nextRouteStepIndex`, and
travel history. This gives the frontend enough information to interpolate a
vehicle position on the road and identify the remaining route without inferring
state from discrete intersection changes.

## Event Responsibilities

`TrafficEvent` is the abstract base class for concrete road-impact events.

Implemented event types:

| Event | Effect when applied | Effect when resolved |
|---|---|---|
| `CongestionEvent` | increases congestion level | restores previous road condition |
| `AccidentEvent` | marks road blocked and raises density impact | restores previous road condition |
| `RoadClosureEvent` | marks road closed | restores previous road condition |
| `TrafficLightDelayEvent` | increases congestion by delay-derived impact | restores previous road condition |

The base event class validates ids, road id, start tick, and duration. Concrete
events remember the previous road condition before applying their effect.

## Service And Runtime Flow

`VehicleServiceImpl`:

- creates vehicles with generated unique `V-...` ids;
- validates start and destination intersections;
- stores vehicles in memory;
- updates all vehicles per tick;
- requests or recomputes routes when needed;
- advances vehicles and updates road density through `Vehicle.advance`.

`TrafficEventServiceImpl`:

- stores scheduled events;
- exposes lookup by id, active events, and road id;
- activates events by tick;
- resolves expired events;
- publishes activation/resolution through `TrafficEventBus`;
- supports explicit API status updates and deletion.

`TickProcessor` and `TrafficSimulator` connect the Student C domain objects to
runtime simulation:

- build multiple vehicles from `VehicleConfig`;
- build events from `EventConfig`;
- process event activation and resolution;
- move vehicles each tick;
- recompute routes for blocked vehicles.

`DynamicReroutingListener` listens to traffic events and reroutes vehicles whose
current route uses the affected road.

## API Support

Student C functionality is exposed through:

- `GET /api/vehicles`
- `POST /api/vehicles`
- `GET /api/events`
- `POST /api/events`
- `GET /api/events/active`
- `GET /api/events/road/{roadId}`
- `GET /api/events/{id}`
- `PUT /api/events/{id}/status`
- `DELETE /api/events/{id}`
- simulation endpoints that initialize vehicles/events and advance ticks.

## Design Patterns

| Pattern | Classes | Reason |
|---|---|---|
| Factory | `VehicleFactory` | creates concrete vehicle subclasses behind a common type |
| Observer | `TrafficEventBus`, `TrafficEventListener`, `DynamicReroutingListener` | decouples event publication from rerouting reaction |
| Template Method style | `TrafficEvent.apply/resolve` overridden by subclasses | shared event lifecycle with subclass-specific road effect |
| Polymorphism | `Vehicle`, `TrafficEvent` hierarchies | common workflow with concrete behavior per type |

## Test Coverage

Relevant tests:

| Test class | Coverage |
|---|---|
| `VehicleTest` | route request, continuous progress, blocked roads, mid-road rerouting |
| `VehicleServiceImplTest` | vehicle creation, validation, continuous multi-vehicle updates |
| `VehicleDtoTest` | road position, route, and travel-history snapshot fields |
| `VehicleControllerTest` | vehicle API payload handling |
| `TrafficEventTest` | event apply/resolve and road-condition restore |
| `TrafficEventServiceImplTest` | event storage, activation, resolution, publishing |
| `TrafficEventControllerTest` | event API create/read/update/delete validation |
| `TrafficEventBusTest` | observer subscription and publish behavior |
| `DynamicReroutingListenerTest` | rerouting after affected-road events |
| `TrafficSimulatorTest` | multi-vehicle/event simulation setup and event creation |

## Completion Notes

The Student C backend work is complete against the team roadmap. Remaining
project risk is integration-level polish across the whole app, not missing
Student C domain functionality.
