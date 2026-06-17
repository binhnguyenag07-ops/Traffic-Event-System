# Student C Presentation Notes

This note is for presenting the Student C work: vehicle models, traffic events, and observer support.

## My Responsibility

I worked on the Vehicle and Traffic Event part of the Traffic Event System.

Main packages:

```text
src/main/java/team/traffic/domain/vehicle
src/main/java/team/traffic/domain/event
src/main/java/team/traffic/observer
```

## What I Implemented

### Vehicle domain

Implemented:

- `Vehicle`
- `Car`
- `Bus`
- `EmergencyVehicle`
- `VehicleFactory`
- `VehicleType`
- `VehicleStatus`

Purpose:

- Represent different vehicle types in the simulation.
- Store current intersection, destination, speed, status, route, and travel history.
- Provide factory methods so other modules can create vehicles without directly using constructors everywhere.

Important design points:

- `Vehicle` is abstract.
- `Car`, `Bus`, and `EmergencyVehicle` extend `Vehicle`.
- `Bus` has `passengerCapacity`.
- `EmergencyVehicle` has `priorityLevel`.
- `VehicleFactory` supports the Factory Pattern.

### Traffic event domain

Implemented:

- `TrafficEvent`
- `CongestionEvent`
- `AccidentEvent`
- `RoadClosureEvent`
- `TrafficLightDelayEvent`
- `TrafficEventType`
- `TrafficEventStatus`

Purpose:

- Represent real traffic problems that affect roads.
- Each event has an id, affected road id, start tick, duration, and status.
- Each event can be applied to a `RoadNetwork` and later resolved.

Event behavior:

- `CongestionEvent` increases road congestion.
- `AccidentEvent` blocks the affected road.
- `RoadClosureEvent` closes the affected road.
- `TrafficLightDelayEvent` converts delay ticks into congestion impact.

Latest improvement:

- Events now remember the previous `RoadCondition` before applying.
- When an event resolves, it restores the original road condition instead of blindly setting the road to `OPEN`.
- This is safer because a road may already have been congested or modified before the event.

### Observer support

Implemented:

- `TrafficEventBus`
- `TrafficEventListener`

Purpose:

- Support the Observer Pattern.
- Simulation, API, or services can subscribe to traffic events.
- Events can be published without knowing which module will react to them.

## Roadmap Progress

Current progress is around Week 5.

Completed:

- Week 1: Vehicle and event design.
- Week 2: Vehicle model.
- Week 3: Vehicle route request contract.
- Week 4: Vehicle movement contract.
- Week 5: Accident, congestion, road closure, and traffic light delay events.

Not fully completed yet:

- Week 3-4 full route movement logic depends on the routing module.
- Week 6 multi-vehicle demo depends on simulation/API integration.
- Week 7 event tests can be written after the core dependencies are stable.

## Current Dependencies

Some dependencies are intentional because they are shown in the final class diagram.

Vehicle depends on:

- `Route`
- `RoutingService`

Traffic events depend on:

- `RoadNetwork`
- `RoadCondition`
- `RoadStatus`

These dependencies should not be removed just to make the code isolated, because the class diagram defines those connections.

What is still missing:

- The routing module still needs to provide `Route` and `RoutingService`.
- After that, `Vehicle.requestRoute()`, `Vehicle.recomputeRoute()`, and `Vehicle.moveOneStep()` can be completed with real routing behavior.

## Who Can Continue From My Work

Nguyie / Routing:

- Can connect `Route`, `RouteStep`, and `RoutingService` to `Vehicle`.
- This supports Week 3 and Week 4 route request and movement.

Binh / Network:

- Already added `RoadNetwork.updateRoadCondition()` and road condition logic.
- My event classes now use those APIs.
- Further improvements can include richer road state handling if needed.

An / Simulation, API, Frontend:

- Can use `VehicleFactory` to create vehicles.
- Can use `TrafficEvent` classes to create and apply events.
- Can use `TrafficEventBus` for simulation/API event notifications.
- This supports Week 4 to Week 6 integration work.

## If Asked Why It Does Not Fully Compile Yet

Answer:

The current backend build is blocked because `Vehicle` correctly follows the final class diagram and references `Route` and `RoutingService`, but those classes are part of the routing/service modules and are not fully available yet.

This is not because the vehicle/event design is random. It follows the class diagram:

```text
Vehicle --> Route
Vehicle.requestRoute(RoutingService)
Vehicle.recomputeRoute(RoutingService)
```

So the next step is to integrate with the routing module once it is merged.

## Short Presentation Script

My part is Student C: Vehicle and Traffic Event.

I implemented the vehicle hierarchy with `Vehicle`, `Car`, `Bus`, and `EmergencyVehicle`, plus `VehicleFactory` to create vehicles cleanly. I also added vehicle status and type enums.

For traffic events, I implemented a base `TrafficEvent` class and concrete events such as congestion, accident, road closure, and traffic light delay. These events can apply their effects to a `RoadNetwork` and later resolve.

The latest improvement is that events now remember the previous road condition and restore it when resolved. This avoids incorrectly resetting every road back to open.

I also added an observer event bus so simulation or API modules can subscribe to traffic events without tight coupling.

My work is currently around Week 5. Full route movement still depends on the routing module because the class diagram defines `Vehicle` as depending on `Route` and `RoutingService`.

## Quick Q&A

Q: Why does `Vehicle` depend on `Route`?

A: Because the final class diagram shows `Vehicle --> Route`, and vehicles need a route to move through the road network.

Q: Why not remove `RoutingService`?

A: Because `requestRoute(RoutingService)` and `recomputeRoute(RoutingService)` are part of the class diagram. Removing it would break the planned architecture.

Q: What is the most complete part right now?

A: The traffic event logic is the most complete, especially after adding restore behavior for previous road conditions.

Q: What is the next thing I should do?

A: After routing is available, complete real `Vehicle.moveOneStep()` behavior and add event tests.
