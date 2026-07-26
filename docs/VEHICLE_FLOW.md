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

Two ways in, and they differ in what they take as the endpoints.

| Entry point | Endpoints | Reachable today |
|---|---|---|
| `VehicleService.createVehicle` | two intersection ids | yes |
| `VehicleService.createVehicleBetweenBuildings` | two building ids | only since the map gained buildings |

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

A tick is normally ten simulated seconds — far too coarse for a car-following
model tuned to about a second of reaction time. `VehicleDynamicsEngine` splits
it into sub-steps of at most one second and runs three phases in each:

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

## Where the demand model plugs in

Everything above describes a vehicle that already exists. Nothing yet decides
**how many** vehicles there should be, **where** they start, or **when**. Today
a scenario lists them by hand, or the UI spawns them at random boundary
junctions.

### The clock

`SimulationClock` now carries a time of day. It starts at 06:00 by default,
advances with the simulation, wraps at midnight, and counts the days crossed.

The time of day is **derived, never stored per frame**. A frame already records
its elapsed seconds; writing the clock time beside it would be a second copy of
the same fact. The API publishes `startTimeOfDaySeconds` once, and any frame can
be labelled from that plus its own elapsed seconds:

```text
GET /api/simulation/status
{ "elapsedSeconds": 5400, "startTimeOfDaySeconds": 21600,
  "timeOfDaySeconds": 27000, "timeOfDay": "07:30", ... }
```

### The model to come

A `TripDemandModel` answering, for the current hour: which trips begin, from
which building, to which building, in what vehicle.

- **Population per building** — footprint area times a per-type density, so a
  tower generates more trips than a shophouse.
- **Purposes** — `HOME_TO_WORK`, `WORK_TO_HOME`, `HOME_TO_SCHOOL`,
  `SCHOOL_TO_HOME`, `OTHER`, each with an hourly departure curve. The classic
  double peak: work outbound around 07:00–08:00, inbound around 17:00–18:00,
  school an hour earlier, a midday errand bump.
- **Destination choice** — a gravity model, weighted by the destination's size
  and suitability for the purpose, divided by a function of travel distance.
  Nearby offices win over distant ones.
- **Vehicle type by trip** — short trips lean motorcycle, long trips car, school
  runs motorcycle and bus. That is the District 1 mix, and it exercises the
  filtering model.
- **Seeded** — a scenario replays identically, which the experiments depend on.

A `DemandDrivenSpawner` then asks the model each tick what departs now and calls
`createVehicleBetweenBuildings`, which is what finally makes that method
reachable in a real run.

**Constants must be sourced or labelled.** A plausible-looking peak built from
invented numbers is worse than no peak at all, because it reads as a finding.
Every density and curve gets a citation or an explicit "assumption" comment, the
same rule the filtering limits follow.

### What a scenario will need to say

Scenarios today are a list of vehicles and events
(`data/scenarios/demo_simulation.json`, matching the body of
`POST /api/simulation/initialize`). A demand-driven scenario says instead:

```jsonc
{
  "startTimeOfDay": "06:00",
  "durationHours": 4,
  "seed": 42,
  "demand": {
    "model": "GRAVITY",
    "scale": 1.0            // multiplier on the whole population
  },
  "events": [ /* unchanged */ ]
}
```

The hand-written `vehicles` list stays supported. A scenario with both is a
scripted fleet plus a background of generated traffic, which is exactly what a
demonstration of one incident against realistic load needs.
