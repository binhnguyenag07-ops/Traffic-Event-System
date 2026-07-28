# The life of a vehicle

How a vehicle gets from being created to being gone, what decides each step, and
where the time-of-day demand model plugs in.

This is the reference for anyone writing a scenario or changing the movement
model. `MOTORCYCLE_FILTERING_PLAN.md` covers the lateral half of the movement
step in more depth.

## The whole path

```text
created ──> routed ──> waiting at a junction ──> on a road ──> arrived ──> gone
              ▲                │                     │
              └── rerouted ────┴─────────────────────┘
                  (an event blocked the way ahead)
```

## 1. Creation

Vehicles can enter through an operator/service call, a scripted scenario entry
or a due generated plan.

| Entry point | Input |
|---|---|
| `VehicleService.createVehicle` | two intersection ids |
| `VehicleService.createVehicleBetweenBuildings` | two building ids |
| scenario `vehicles` | one scripted origin/destination config, created during initialization |
| `DemandDrivenSpawner` | one resolved, due, possibly multi-leg `TripPlan` |

The second resolves each building to the junction its access point names, picks
the pair of access points with the cheapest route between them, and creates the
vehicle there. It existed for some time before the map had any buildings for it
to use.

A vehicle is born with a `VehicleProfile` (length, width, top speed,
acceleration) fixed by its type, and a `DriverProfile` varied deterministically
from its id — so two cars in the same queue keep slightly different headways and
reaction times, and a rerun reproduces them exactly.

## 2. Routing

`Vehicle.requestRoute(network, routingService, preference)` asks for a path and
stores it in a `RoutePlan`, which holds the route and how far through it the
vehicle is. The preference (`CONGESTION_AWARE` by default) picks the strategy —
BFS, Dijkstra or A\*.

A vehicle with no usable route is `BLOCKED` rather than removed. It is still
there, still counted, and will move the moment a route exists.

**Rerouting** happens when a `TrafficEvent` closes or blocks a road on the
remaining path. `DynamicReroutingListener` hears the event and asks the affected
vehicles to recompute from where they are now. A vehicle already on the blocked
road finishes its current segment first; it does not teleport off it.

## 3. One tick

The default tick is 0.5 simulated seconds. If a caller configures a longer
tick, `VehicleDynamicsEngine` splits it into sub-steps of at most one second
and runs three phases in each:

1. **Observe.** `TrafficState.capture` records where every vehicle physically
   is, once, before anyone decides anything.
2. **Arbitrate.** Every vehicle waiting at a junction submits a
   `JunctionApproach`; each junction then decides *all at once* who may cross,
   by right-of-way and signal phase.
3. **Move.** Every vehicle plans against the captured state, then all plans are
   committed.

Planning and committing are separate on purpose. Every vehicle decides against
the same unmoved picture, so the result does not depend on the order vehicles
happen to sit in the list — which is what makes a replay reproducible.

## 4. Movement, in one sub-step

`Vehicle.planMovement` produces a `MovementPlan` and changes nothing; then
`commitMovement` applies it.

**Along the road.** The Krauss car-following model chooses a speed from the gap
to the leader, the leader's speed, the driver's reaction time and the desired
speed. The desired speed is the lower of the posted limit times the driver's
speed factor, and what the bend ahead allows.

**Across the road.** The lane-change model returns a `LateralTarget` — an offset
in metres, not just a lane index. The vehicle then slides towards it at a bounded
lateral speed, so a lane change takes the two or three seconds it should. While
crossing, it is committed: the model is not asked again until it arrives, or it
would drift back and forth over the line forever.

Who blocks whom is decided by **lateral overlap**, not by a shared lane index.
That is what lets a motorcycle ride the gap between two lanes.

**Position convention, worth knowing.** `roadProgress` marks the vehicle's
**front bumper**, and following gaps are measured from it. Anything drawing a
vehicle body must set it back half its length, or half a car hangs over every
stop line.

## 5. Junctions

A vehicle within 50 m of the end of its road submits an approach. The junction
grants crossings by right-of-way, priority level (an emergency vehicle outranks
ordinary traffic), and — where a junction is signalled — whether its arm is
green on this frame.

A vehicle may only leave a road when the next road has room. Without that check a
junction would swallow traffic that has nowhere to go, and the queue would never
back up. Spillback is a consequence of this rule, not a separate feature.

## 6. Arrival and removal

On reaching its destination a vehicle becomes `ARRIVED`, releases its road, and
stays visible for `ARRIVED_RETENTION_SECONDS` (5 s) before being removed — long
enough to see it finish. Its `TripRecord` (distance, travel time, waiting time,
reroute count) survives into the completed-trip statistics.

---

## Where generated demand plugs in

Everything above still describes the physical lifecycle. Demand supplies the
creation schedule and the sequence of destinations without bypassing routing or
dynamics.

```text
DemandSource + FlowSpec + seed
             │
             ▼
     GravityDemandModel
   (resolve complete plans)
             │
             ▼
 tripsBeginningIn(last, now]
             │
             ▼
    DemandDrivenSpawner
             │
             ▼
 one Vehicle with TripPlan
   leg → dwell → leg → ...
```

### Clock and departure horizon

`SimulationClock` starts at the scenario's `startTimeOfDay`, advances with
simulated elapsed time, wraps at midnight and counts crossed days. Time of day is
derived from the start offset and frame elapsed seconds.

`durationHours` is frequently misunderstood. It is the window in which the
demand model may schedule **departures**. It does not stop the simulator.
Travel, dwell and a return leg may finish after that horizon.

The bundled scenario starts at 06:00 and has an 18-hour departure horizon, so
generated departure scheduling ends at midnight.

### Plan lifecycle

One generated `TripPlan` creates one vehicle. It can contain several
`TripLeg`s:

```text
home → school → home
```

is one plan and two legs. On each leg arrival:

1. the vehicle releases its road;
2. positive dwell puts it in `DWELLING`;
3. after dwell, the next resolved destination is applied and routed;
4. only the final leg completes the whole plan.

`RETURN_TO_ORIGIN` ends at the exact resolved origin.
`REMOVE_AT_LAST_STOP` completes at the last declared place. Demand statistics
count plans, not legs or survey person trips.

### Directed reachability

`GravityDemandModel` does not independently roll destinations and discard the
broken combinations afterward. It samples only candidates for which the entire
directed suffix is feasible, including the return path of a closed plan.

Boundary roles are directional:

- an origin boundary needs an outgoing road;
- a destination boundary needs an incoming road.

The check is structural topology. A closure or capacity constraint that occurs
during simulation remains a normal routing, retry and rerouting concern.

The bundled scenario has 216 generated plans and 0 structural generation
failures for its committed seed and map.

### Initialization and phantom occupancy

Before scripted or generated vehicles are routed, scenario initialization
clears legacy persisted runtime traffic:

```text
currentVehicleCount = 0
vehicleDensity      = 0
congestionLevel     = 0
roadStatus          = OPEN, unless explicitly CLOSED
```

Without this step the old map counts represented no `Vehicle` objects but still
consumed capacity and altered routing.

### Actual scenario fields

Generated demand is declared with top-level `demandSources`; there is no nested
`demand: { model: "GRAVITY" }` block.

```jsonc
{
  "startTimeOfDay": "06:00",
  "durationHours": 18,
  "seed": 20260728,
  "scale": 0.015,
  "zones": [ /* named building/intersection groups */ ],
  "demandSources": [ /* source production and flows */ ],
  "events": [],
  "vehicles": [ /* optional scripted warm starts */ ]
}
```

`scale` multiplies generated plan volume only. It does not multiply scripted
vehicles, durations, road capacity or speed.

For `district1_typical_day.json`, the deterministic snapshot is:

| Output | Count |
|---|---:|
| Generated plans | 216 |
| Structural generation failures | 0 |
| Generated motorcycles | 184 |
| Generated cars | 32 |
| Scripted vehicles at `t = 0` | 4 |

The first generated plan departs at 272.10 simulated seconds. The four scripted
vehicles are deliberate warm starts so the physical demo is active immediately;
they are excluded from the generated-demand counts.

Those values are deterministic model output, not observed traffic in the former
District 1 study window.
The complete scenario, evidence labels and limitations are documented in
[`DISTRICT1_SCENARIO.md`](DISTRICT1_SCENARIO.md). The schema and algorithms are
documented in
[`DEMAND_AND_SCENARIO_DESIGN.md`](DEMAND_AND_SCENARIO_DESIGN.md).
