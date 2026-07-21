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
| Post-plan extension | Vehicle physical profile and speed controls | Done |
| Post-plan extension | Backend vehicle trace timeline | Done |
| Post-plan extension | Microscopic traffic model: car following, right of way, physical road capacity | Done |
| Post-plan extension | Vehicle pose: map position and heading for true-scale rendering | Done |
| Post-plan extension | Structural refactor: cycle, temporal coupling, `VehicleProfile`, drop legacy movement | Done |

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
    |-- EventConfig.java
    `-- trace/
        |-- VehicleTracePoint.java
        |-- VehicleTraceStore.java
        `-- VehicleTraceSummary.java
```

Two later extensions add to this list and are described in their own sections
below: the vehicle physical profile, and the microscopic traffic model in
`domain/vehicle/dynamics`.

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
- moves one route step at a time;
- updates road vehicle counts through `RoadNetwork` when occupying or leaving a road;
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
They intentionally do not alter the original graph-based movement rule, where a
simulation tick moves a vehicle by one `RouteStep`.

## Student C Extension: Microscopic Traffic Model

The physical profile above described vehicles that never actually interacted:
`length` was carried around but nothing read it, and a road was considered full
purely by counting vehicles, so a 12 m bus and a 4.5 m car each consumed one
unit of a road's `capacity`. This extension closes that gap. It lives in
`domain/vehicle/dynamics` plus one engine class in `simulation`, and follows the
conventions of the SUMO microsimulator so parameter values can be compared
against published defaults.

### Packages

```text
src/main/java/team/traffic/
|-- domain/vehicle/dynamics/
|   |-- DriverProfile.java              behaviour parameters of the driver
|   |-- CarFollowingModel.java          interface: choose a speed for one step
|   |-- KraussCarFollowingModel.java    SUMO's default car-following model
|   |-- FollowContext.java              inputs to one car-following decision
|   |-- LeaderGap.java                  space and speed of the vehicle ahead
|   |-- LaneOccupancy.java              physical state of one road
|   |-- TrafficState.java               working state of one simulation step
|   |-- JunctionEnvironment.java        interface: what a junction can observe
|   |-- JunctionApproach.java           one vehicle asking to cross
|   |-- JunctionController.java         arbitrates one junction per step
|   |-- RightOfWayPolicy.java           interface: who goes first, what conflicts
|   |-- PriorityRightOfWayPolicy.java   hierarchy-based right of way
|   |-- GapAcceptanceModel.java         will this driver pull out?
|   `-- TurnDirection.java              straight / left / right / U-turn
`-- simulation/
    `-- VehicleDynamicsEngine.java      runs observe, arbitrate, move
```

### Driver Profile

Separate from the physical profile, because they describe different things: the
vehicle is the machine, the driver is the person operating it.

| Property | Unit | Car | Bus | Emergency |
|---|---:|---:|---:|---:|
| `minimumGapMetres` (SUMO `minGap`) | m | 2.5 | 3.0 | 2.0 |
| `reactionTimeSeconds` (SUMO `tau`) | s | 1.0 | 1.4 | 0.7 |
| `imperfection` (SUMO `sigma`) | 0..1 | 0.5 | 0.4 | 0.1 |
| `gapAcceptanceSeconds` | s | 3.0 | 4.5 | 1.5 |
| `speedFactor` | multiplier | 1.0 | 0.95 | 1.3 |

### Physical Road Occupancy

`LaneOccupancy` holds the vehicles on one road ordered by position and answers
the questions the old count-based model could not:

- `getPhysicalJamRatio()` — share of the road's length consumed, where each
  vehicle takes `length + minimumGap` metres. A 100 m road holds roughly 14
  cars but only 6 buses.
- `canAdmit(vehicle)` — whether an arriving vehicle physically fits, both in
  total length and in the space immediately past the entry.
- `findLeaderFor(vehicle)` — bumper-to-bumper gap and speed of the vehicle
  directly ahead.

`TickProcessor` republishes each road's `vehicleDensity` from this ratio at the
end of every tick, so routing cost, the statistics panel, and the map colouring
all see size-aware congestion. The road's own `capacity` is untouched and still
serves as a hard upper bound.

### Car Following

`KraussCarFollowingModel` computes the fastest speed from which a vehicle could
still stop behind its leader:

```text
vSafe = vLeader + (gap - vLeader * tau) / ((vLeader + vFollower) / (2 * b) + tau)
v     = min(vDesired, v + a * dt, vSafe) - sigma * a * min(dt, tau) * noise
```

Two deviations from the textbook formulation, both deliberate:

- `noise` is supplied by the caller and derived from the vehicle id and tick, so
  driver imperfection never makes an experiment unrepeatable;
- the imperfection term is integrated over `min(dt, tau)` rather than `dt`,
  because a coarse step would otherwise subtract minutes' worth of acceleration
  and bring every vehicle to a standstill.

`Vehicle.currentSpeed` keeps its original meaning of the speed the driver wants.
The new `instantaneousSpeedMps` is the speed actually being travelled. Their
ratio is a direct measure of delay.

### Right Of Way And Gap Acceptance

`JunctionController` collects every `JunctionApproach` at a junction, sorts them
with a `RightOfWayPolicy`, then walks the list once, clearing a movement only
when it does not conflict with one already cleared, the driver accepts the gap,
and the target road physically has room.

`PriorityRightOfWayPolicy` orders approaches by dispatch priority (emergency
first), then starvation, then street hierarchy, then manoeuvre (straight before
right before left before U-turn), then distance to the stop line, then vehicle
id. The starvation rule promotes anyone held for six ticks so a side street is
never blocked forever by a steady stream on the main road.

`GapAcceptanceModel` requires a driver turning across the opposing stream to see
a headway of at least `gapAcceptanceSeconds` before pulling out. An ambulance
accepts a 1.5 s gap a bus driver would refuse.

### Step Sequence

`VehicleDynamicsEngine` runs three phases per sub-step:

1. **Observe** — `TrafficState.capture` records where every vehicle is;
2. **Arbitrate** — every waiting vehicle registers its approach and all
   junctions resolve at once;
3. **Move** — every vehicle calls `planMovement`, then every vehicle calls
   `commitMovement`.

Splitting plan from commit is what makes the result independent of the order of
the vehicle list: a vehicle sees its leader unmoved whether that leader sits
earlier or later in the list.

A tick is normally ten simulated seconds, far too coarse for a model tuned to a
one-second reaction time, so the engine splits it into sub-steps of at most one
second (capped at 60 per tick) and runs all three phases in each.

### Emergent Behaviour

None of the following is imposed by a formula; all of it falls out of vehicles
interacting:

- queues form behind a blockage, at spacing set by vehicle length and `minGap`;
- a full road refuses arrivals, so queues spill back through the junction into
  the road behind;
- throughput drops as a road saturates, because followers must brake;
- a lane of buses saturates long before a lane of cars.

## Student C Extension: Vehicle Pose

Everything above simulates where a vehicle is; this publishes it. The snapshot
used to say which road a vehicle was on and how far along it was, leaving each
client to interpolate the road shape for itself. That put the same geometry in
two places, and it broke down entirely at junctions.

```text
domain/vehicle/pose/
|-- PolylineGeometry.java      sample a position and bearing along a road shape
|-- VehiclePose.java           where a vehicle is, which way it faces, how big it is
`-- VehiclePoseResolver.java   resolve one vehicle against the network
```

`PolylineGeometry` measures a road by its length in metres rather than by its
point count. A degree of longitude is shorter than a degree of latitude away
from the equator, so treating the two alike misplaces a vehicle on any road
that does not run due north or east.

The junction case is the one that mattered. A vehicle waiting to cross holds no
road, so it had no shape to sample and therefore no position and no heading at
all — a renderer had nothing but the junction itself, and waiting vehicles
snapped round to face north. Junction arbitration made this more visible rather
than less, because vehicles now queue at stop lines instead of crossing
straight through: a probe over four junctions found **15.8%** of vehicle-ticks
in that state. Such a vehicle is now placed at the junction still facing the
way it arrived, taken from the route step before the one it waits on.

`SimulationSnapshotDto` resolves a pose per vehicle when it is given a network,
so `VehicleDto` carries `latitude`, `longitude`, `headingDegrees`, and
`inJunction` beside the existing `length` and `width`. That is enough to draw a
vehicle to true scale. Without a network the fields stay null and the original
progress-based fields are unchanged.

### Still Missing For True-Scale Rendering

Lane-level placement. `LaneOccupancy` models one road as one lane, so a vehicle
has no lane index and no lateral offset from the centreline, and every vehicle
would be drawn along the middle of the road. Closing that needs a lane count
and a road width, which belong to `domain/network`.

## Student C Refactor: Structure

Four changes made once the model was working, each fixing something specific.

**A dependency cycle.** `domain.vehicle.dynamics` imported `Vehicle` while
`Vehicle` imported the dynamics package, so neither could be reasoned about or
tested alone. The model never needed a whole vehicle: it needs a length, a
position on a road, a speed, a driver profile and a dispatch priority. Those
seven methods are now `TrafficParticipant`, `Vehicle` implements it, and the
dependency runs one way.

**Temporal coupling, and a bug it hid.** `planMovement` wrote its decision into
mutable fields that `commitMovement` later read; nothing said one had to run
before the other. It now returns a `MovementPlan` that commit takes as an
argument. That exposed a real defect: the newly chosen speed was written
straight back to the vehicle, and a follower reads its leader's speed when
judging the gap, so a vehicle planning later in the step saw its leader at the
speed it had *just picked*. The speed now rides in the plan. Reversing the
vehicle list reproduces every position to the last bit.

**Two state machines out of `Vehicle`.** `TrafficLightWait` owns the signal
countdown and which signals have been served. `TripRecord` owns distance, time,
waiting ticks, reroute count and the visited-junction list.

**The physical profile, and the old movement path.** The seven fixed physical
properties became `VehicleProfile`, an immutable record that validates itself.
The original graph-based movement — `moveOneStep(...)`, `advance(RoadNetwork,
double)` and `VehicleService.updateAllVehicles(...)` — is deleted. It had no
production caller left: the tick loop goes through `VehicleDynamicsEngine`, and
only tests kept the old path alive. `Vehicle` went from 920 to 754 lines.

## What Is Still Missing For True-Scale Rendering

Ordered by what blocks what.

| Work | Owner | Blocked by |
|---|---|---|
| Cornering speed limit from road curvature | Student C | nobody |
| Junctions with real length and turn paths | Student C | nobody |
| `Road.laneCount` and lane width | Student A | nobody |
| Lane index and lateral offset on a vehicle | Student C | `laneCount` |
| Reading the OSM `lanes` tag in the map generator | unassigned | nobody |

The critical path is the map generator, then `laneCount`, then lane placement.
Without lane data every road defaults to one lane and lane placement is
meaningless. The first two rows are independent of all of it.

Two known defects that are not Student C's to fix:

- `Road.setCurrentVehicleCount()` recomputes `vehicleDensity` from its own
  headcount, so within a tick the count-based and physical values disagree
  until the tick ends. The field has two writers.
- `TrafficMap.jsx` still computes vehicle positions and bearings itself rather
  than reading the published pose, and places every vehicle waiting at a
  junction on the same point.

## Compatibility

Nothing in `domain/network`, `routing`, `persistence`, or the frontend was
modified.

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
- delegates to `TrafficSimulator` so the API and the simulation read one list.

Moving vehicles is not its job. That belongs to the tick loop, which is the
only place with the whole-network picture the microscopic model needs.

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
| Strategy | `CarFollowingModel`, `RightOfWayPolicy` | swap how vehicles follow and who yields without touching the simulator |
| Value Object | `DriverProfile`, `LeaderGap`, `FollowContext`, `JunctionApproach` | immutable records carrying one decision's inputs |
| Mediator | `JunctionController` | vehicles never negotiate with each other; the junction decides for all of them |
| Interface Segregation | `JunctionEnvironment` | a junction sees only what it needs, so it can be tested without a network |

## Test Coverage

Relevant tests:

| Test class | Coverage |
|---|---|
| `VehicleTest` | route request, movement, blocked roads, route recomputation |
| `VehicleServiceImplTest` | vehicle creation, validation, multi-vehicle updates |
| `VehicleControllerTest` | vehicle API payload handling |
| `TrafficEventTest` | event apply/resolve and road-condition restore |
| `TrafficEventServiceImplTest` | event storage, activation, resolution, publishing |
| `TrafficEventControllerTest` | event API create/read/update/delete validation |
| `TrafficEventBusTest` | observer subscription and publish behavior |
| `DynamicReroutingListenerTest` | rerouting after affected-road events |
| `TrafficSimulatorTest` | multi-vehicle/event simulation setup and event creation |
| `VehicleTraceStoreTest` | immutable per-tick vehicle timeline and range queries |
| `KraussCarFollowingModelTest` | safe speed, stop points, no overshoot, coarse steps, reproducibility |
| `TurnDirectionTest` | manoeuvre classification from map geometry |
| `PriorityRightOfWayPolicyTest` | emergency priority, hierarchy, turns, starvation, conflicts |
| `JunctionControllerTest` | arbitration, gap acceptance, refusing a full road |
| `VehicleDynamicsEngineTest` | queueing, physical capacity, spillback, order independence |
| `PolylineGeometryTest` | metric sampling of a road shape, bearings, short-way turns |
| `VehiclePoseResolverTest` | pose on a road, at a junction, and at every moment of a trip |
| `SimulationSnapshotDtoTest` | pose published with a network, absent without one |
| `LaneOccupancyTest` | physical occupancy, tested with no `Vehicle` in the fixtures |
| `TrafficLightWaitTest` | signal countdown, served-once rule |
| `TripRecordTest` | journey accumulation and its validation |
| `TickProcessorTest` | rerouting a blocked vehicle, republishing physical density |

## Completion Notes

The Student C backend work is complete against the team roadmap. Remaining
project risk is integration-level polish across the whole app, not missing
Student C domain functionality.
