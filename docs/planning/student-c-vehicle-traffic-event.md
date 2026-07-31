# Student C: Vehicle, Dynamics, Events, And Signals

This document records the implemented Student C scope after the 11-upgrade
roadmap. It replaces the earlier one-lane and traffic-light-delay description.

## Ownership and integration boundaries

Student C owns the vehicle hierarchy, microscopic dynamics, positioned traffic
events, event Observer integration, signal domain, and traffic diagnostics.
The implementation reuses Student A's `Road`/`RoadNetwork` aggregate and Student
B's routing contracts. API/frontend changes are limited to exposing the new
seconds-, lane-, signal-, and pose-based contract.

The lane fields lost after the earlier merge were restored to `Road`:
`name`, `physicalRoadId`, `laneCount`, and geometry, with the new
`laneWidthMetres`/`roadWidthMetres`. Existing constructors remain available.
JSON without lane values derives deterministic defaults from `RoadClass`, so
the 734-road District 1 dataset did not require a noisy bulk rewrite.

## Implemented roadmap

| Phase | Result |
|---|---|
| 0 | Event time in seconds; `RoadLayout`; derived `Junction` that ignores degree-two shape nodes |
| 1 | `FlowRecorder`, `FundamentalDiagram`, and `TimeSpaceDiagram` plus HTTP endpoints |
| 2 | Curvature speed cap, five-second arrived-marker retention, deterministic ±15% driver variation |
| 3 | `Lane`, `RoadOccupancy`, lane entry selection, lateral vehicle pose |
| 4 | MOBIL adjacent-lane changes with safety, incentive, politeness, and emergency settings |
| 5 | Collision-generated accidents, `STOPPED`, `BreakdownEvent`, positioned lane obstacles |
| 6 | Junction-owned `TrafficSignal`/`SignalPhase`; old delay event and wait state removed |
| 7 | Capacity-based bus stops that hold the bus physically in its lane at the stop line |
| 8 | Compact `Motorcycle` profile and safety-preserving low-threshold MOBIL filtering |

## Vehicle dynamics

`VehicleDynamicsEngine` divides a logical tick into sub-steps of at most one
simulated second. A sub-step runs in three phases:

1. capture `TrafficState`, including every road/lane occupancy and active event;
2. arbitrate junction approaches and signal phases;
3. plan every vehicle, then commit every `MovementPlan`.

Planning before committing keeps results independent of list order. The
existing reverse-list regression test remains bit-identical.

`KraussCarFollowingModel` determines longitudinal speed. Desired speed is the
minimum of vehicle/profile speed, posted road speed, congestion adjustment, and
the curve comfort limit:

```text
curveSpeedMps = sqrt(lateralAccelerationMps2 * curvatureRadiusMetres)
```

`DriverProfile.variedFor(type, vehicleId)` deterministically varies reaction
time, gap, speed factor, noise, lateral acceleration, and politeness. The same
vehicle id always produces the same profile.

## Lanes and MOBIL

`RoadLayout` isolates dynamics from map storage. Production uses
`RoadDataRoadLayout`; `RoadClassRoadLayout` is the fallback/reference mapping.
`RoadOccupancy` owns one `Lane` per directed lane. A lane contains physical
vehicles and positioned obstructions, calculates leader gaps, entry room, and
jam ratio.

Vehicles enter the lane with the best usable entry gap. `MobilLaneChangeModel`
checks adjacent lanes only:

- target followers must not require braking beyond their deceleration limit;
- the vehicle's acceleration gain, adjusted for politeness and follower cost,
  must exceed the threshold;
- emergency vehicles use zero politeness and zero incentive threshold.

Lane choice is stored in `MovementPlan.targetLaneIndex` and committed later.
The pose offset is:

```text
(laneIndex - (laneCount - 1) / 2) * laneWidthMetres
```

`Motorcycle` is 2.1 m long and 0.75 m wide. Its driver profile keeps shorter
gaps and its `FilteringLaneChangeModel` accepts any adjacent lane with a real
speed benefit while retaining the ordinary MOBIL target-follower braking check.
The smaller body therefore fits some longitudinal openings that reject a car.
This remains a discrete-lane approximation; continuous between-lane filtering
is deferred until sub-lane occupancy exists for the physical view.

The resolver offsets the road polyline normal and publishes latitude,
longitude, heading, lane index, and lateral metres to the frontend.

## Junctions and traffic signals

`Junction.at(network, id)` derives incoming/outgoing branches, unique physical
degree, and an estimated radius. `isRealJunction()` requires degree at least 3,
which prevents OSM shape points from receiving artificial signals.

`SignalPlan` groups opposing incoming approaches into two axes. `TrafficSignal`
selects a repeating phase from elapsed seconds. Red vehicles remain on their
incoming lane and brake at the stop line. Emergency priority can preempt red;
ordinary vehicles cannot.

`TrafficLightDelayEvent`, `TrafficLightWait`, `waitForTrafficLight`, and
`TRAFFIC_LIGHT_DELAY` were deleted rather than kept as a parallel model.

## Traffic events and collisions

All event timing is in seconds:

```text
startTimeSeconds <= elapsedSeconds < startTimeSeconds + durationSeconds
```

If a start or end falls inside a longer logical tick, `TickProcessor` splits
that tick at the exact boundary before invoking dynamics. For example, an
event from second 2 through second 5 inside a 10-second tick produces movement
segments of 2, 3, and 5 seconds; it cannot leak into the next tick.

`TrafficEvent` also owns `positionAlongRoad` (`0..1`) and a set of zero-based
blocked lane indexes. Empty means all lanes for physical obstructions.

Concrete events:

- `CongestionEvent` changes road congestion;
- `AccidentEvent` can carry causing and involved vehicle ids;
- `BreakdownEvent` stops one vehicle and leaves a lane obstacle;
- `RoadClosureEvent` closes the road.

`CollisionDetector` examines adjacent follower/leader pairs in each lane. If
the required braking

```text
(followerSpeed² - leaderSpeed²) / (2 * gap)
```

exceeds the follower's deceleration capability, `TickProcessor` emits a
positioned accident, stops both vehicles, and publishes it through
`TrafficEventBus`. They resume only after every event that stopped them has
resolved.

`TrafficEventCoordinator` fixes an important overlap edge case: it restores a
road baseline and reapplies every still-active event whenever one resolves.
Resolution order therefore cannot erase another event or leave stale state.
The HTTP request names a breakdown target as `affectedVehicleId`; the older
shared `causingVehicleId` spelling remains accepted as an input alias only.

## Bus stops and lifecycle

`BusStop` stores a junction and dwell seconds. Default dwell is derived from
`passengerCapacity`; every intermediate route junction becomes a default stop,
including after dynamic rerouting. A dwelling bus stays at the incoming road's
stop line, so it remains a real lane obstacle and other traffic can pass via
MOBIL or a free entry lane.

Arrived vehicles remain in snapshots for at least five simulated seconds so the
UI shows completion. They are then removed through the normal archive path;
`CompletedTrip` statistics remain available.

## Diagnostics and API surface

`FlowRecorder` samples occupied roads after each tick:

- density: vehicles per kilometre per lane;
- mean speed: metres per second;
- flow: vehicles per hour per lane.

`FundamentalDiagram` aggregates density bins. `TimeSpaceDiagram` exports each
vehicle's recorded position in metres on one road. Endpoints are documented in
[`API_CONTRACT.md`](../API_CONTRACT.md).

## Verification

Coverage includes lane capacity/pose, MOBIL safety and overtaking, motorcycle
compact-gap filtering and unsafe cut-in rejection, deterministic driver
variation, cornering, collision-to-event integration, overlapping event
resolution, signal stop-line behavior and emergency preemption, physical bus
dwell/overtaking, arrived cleanup, flow aggregation, JSON lane round-trips, and
the full pre-existing routing/persistence/API suite.

Run:

```bash
mvn -o test
cd frontend
npm run lint
npm test
npm run build
```
