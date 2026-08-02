# The life of a vehicle

How a vehicle gets from being created to being gone, what decides each step, and
where the time-of-day demand model plugs in.

This is the reference for anyone writing a scenario or changing the movement
model. `MOTORCYCLE_FILTERING_PLAN.md` covers the lateral half of the movement
step in more depth.

## The whole path

```text
building ──> access link ──> routed/queued ──> on roads ──> access link ──> stop
                                 ▲               │                         │
                                 └── rerouted ───┘          dwell/next leg ┘
                                     (live conditions)
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
the pair of access points with the cheapest directed route, and creates the
vehicle at the origin building. It does not place the body midway along a road.
The Fleet UI lists human-readable building/street labels and the random control
prefers a residential origin with a reachable, different-access destination.

A vehicle is born with a `VehicleProfile` (length, width, top speed,
acceleration) fixed by its type, and a `DriverProfile` varied deterministically
from its id — so two cars in the same queue keep slightly different headways and
reaction times, and a rerun reproduces them exactly.

## 2. Routing

`Vehicle.requestRoute(network, routingService, preference)` asks for a path and
stores it in a `RoutePlan`, which holds the route and how far through it the
vehicle is. The preference (`CONGESTION_AWARE` by default) picks the strategy —
BFS, Dijkstra or A\*.

A vehicle with no usable route is `BLOCKED` rather than removed. It remains
counted and enters cause-aware retry/backoff; it can move once it has a legal
route and the next road can physically admit it.

**Rerouting** is coordinated by `AdaptiveNavigationCoordinator`, not by the
legacy `DynamicReroutingListener`. On each strategic cadence or cause-specific
trigger it combines local perception with the events/congestion that have
actually reached that driver's `DriverKnowledge`. Savings thresholds, latency,
cooldown and retry backoff can all produce an intentional wait. An accepted
route replaces only the suffix after the vehicle's exact `RouteAnchor`; current
road, progress, lane and lateral motion are preserved, so rerouting never
teleports a vehicle.

Momentary capacity is an admission constraint, not a missing graph edge. A full
next road keeps the vehicle at the boundary and is retried without launching a
new whole-graph search every simulated second. `CLOSED`, `BLOCKED` and illegal
junction movements remain hard routing constraints.

## 3. One tick

The default tick is 0.5 simulated seconds. `VehicleDynamicsEngine` requests
one-second-or-shorter sub-steps and caps the work at 60 sub-steps per logical
tick. The shipped UI uses at most 30 simulated seconds per tick, so every normal
UI sub-step is at most one second. The API accepts larger positive values; above
60 seconds the safety cap makes each sub-step longer than one second. Each
sub-step runs three phases:

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
speed. `Vehicle` derives that desired speed from the lower of the machine and
posted limits, the driver's speed factor, active event slowdown, and the
curvature-comfort cap for the bend ahead.

**Across the road.** The lane-change model returns a `LateralTarget` — an offset
in metres, not just a lane index. The vehicle then slides towards it at a bounded
lateral speed over multiple physical sub-steps. Its duration depends on lane
spacing, target offset and the vehicle-specific driver profile; it is never an
instantaneous sideways jump. While crossing, it is committed: the model is not
asked again until it arrives, or it would drift back and forth over the line
forever.

Who blocks whom is decided by **lateral overlap**, not by a shared lane index.
That is what lets a motorcycle ride the gap between two lanes.

**Position convention, worth knowing.** Dynamics `roadProgress` marks the
vehicle's **front bumper**, and following/stop gaps are measured from it.
`VehiclePoseResolver` converts that value to a **body-centre**
latitude/longitude exactly once. Physical-view `x`/`y` are projected from that
body centre, so the frontend must use them directly and must not apply another
half-length setback. This single conversion keeps the entire body behind the
painted stop line.

## 5. Junctions

A vehicle within 50 m of the end of its road submits an approach. The junction
grants crossings by right-of-way, priority level (an emergency vehicle outranks
ordinary traffic), and — where a junction is signalled — whether its arm is
green on this frame.

On red, the dynamics cap the **front-bumper** progress at the incoming road's
painted stop line. Pose resolution then places the body centre half a vehicle
length behind that front, so neither the sprite nor the physical vehicle crosses
the line while waiting.

A vehicle may only leave a road when the next road has room. Without that check a
junction would swallow traffic that has nowhere to go, and the queue would never
back up. Spillback is a consequence of this rule, not a separate feature.

`JunctionMovementGraph` is the common legality contract used by routing,
junction arbitration and connector rendering. Its state includes the incoming
road, so turn restrictions and vehicle-type-specific U-turn rules cannot be
lost. A permitted U-turn follows a real reverse directed road at a junction or
normalized turnaround point; it is never wrong-way driving, an arbitrary median
crossing or a sprite rotation in place. Emergency vehicles may receive a more
permissive inferred U-turn under scenario policy, but they still need a legal
movement, physical gap and, at a signalled junction, a green phase.

`DeadlockCoordinator` builds a wait-for graph from explicit wait resources,
excludes timed waits such as a known red release, confirms persistent strongly
connected cycles, gates new entries and grants one deterministic physical drain
reservation. Strict recovery never overlaps, teleports or despawns a vehicle;
a saturated cycle with no legal drain is reported as unresolvable.

### Bus stops

A `BusStop` belongs to one directed road at `positionAlongRoad`, not to a
junction. A bus serves only mapped stops on its current route and derives dwell
from passenger capacity. During dwell it remains physically in its lane, so
followers must wait or pass through another safe lane. Replanning rebuilds the
remaining mapped-stop list for the new route.

## 6. Building links, arrival, and removal

For a resolved building trip the backend owns two off-road phases:

```text
DEPARTING_ORIGIN:      building centre → selected access node
ARRIVING_DESTINATION: selected access node → building centre
```

They advance at 15 km/h using the access distance stored in the map. Time and
distance count toward the same trip record, but the vehicle occupies no road
while on a building link. A dwell/return plan uses the just-reached building as
the next leg's origin, so it visibly leaves that building again instead of
popping onto a road.

On reaching its destination a vehicle becomes `ARRIVED`, releases its road, and
remains in authoritative snapshots for `ARRIVED_RETENTION_SECONDS` (5 s) before
being removed. Both renderers hide `ARRIVED` immediately because it no longer
occupies the road network. Its `TripRecord` (distance, travel time, waiting time,
reroute and legal-U-turn counts) survives into completed-trip statistics.

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

The bundled scenario starts at 06:00 and has a 24-hour departure horizon, so it
covers every clock hour once and generated departure scheduling ends at 06:00
on simulation day `D+1`.

The top-bar clock comes from the authoritative live or selected historical
frame. The **24-hour demand** panel is backed by
`GET /api/simulation/demand/schedule`: it shows exact plan departures and named
places, while leg arrival, next-departure, and return clocks are clearly marked
as directed free-flow estimates rather than runtime observations.

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

A `DWELLING` vehicle stays in the simulator and in dwelling statistics so its
next leg remains authoritative, but it is not a road user at that time.
Renderer snapshots and per-tick traces omit it until it departs the building
again; otherwise long work/visitor dwells would serialize thousands of parked
sprites at identical building points.

### Directed reachability

`GravityDemandModel` does not independently roll destinations and discard the
broken combinations afterward. It samples only candidates for which the entire
directed suffix is feasible, including the return path of a closed plan.

Boundary roles are directional:

- an origin boundary needs an outgoing road;
- a destination boundary needs an incoming road.

The check is structural topology. A closure or capacity constraint that occurs
during simulation remains a normal routing, retry and rerouting concern.

The bundled scenario has 39,434 generated plans / 71,267 vehicle legs and 0
structural generation failures for its committed seed and map.

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
  "durationHours": 24,
  "seed": 20260728,
  "scale": 1.0,
  "warmUpSeconds": 300,
  "automaticCollisionEvents": false,
  "zones": [ /* named building/intersection groups */ ],
  "demandSources": [ /* source production and flows */ ],
  "events": [],
  "vehicles": [ /* optional scripted warm starts */ ]
}
```

`scale` multiplies generated plan volume only. It does not multiply scripted
vehicles, durations, road capacity or speed.

`automaticCollisionEvents` defaults to `true`. The checked-in typical-day run
sets it to `false` because its synthetic traffic demand has no calibrated crash
rate; manual incidents and collision labs are unaffected.

For `district1_typical_day.json`, the deterministic snapshot is:

| Output | Count |
|---|---:|
| Generated plans | 39,434 |
| Generated vehicle legs | 71,267 |
| Structural generation failures | 0 |
| Generated motorcycles | 34,219 |
| Generated cars | 4,161 |
| Generated buses | 719 |
| Generated emergency vehicles | 335 |
| Plans spawned during the 06:00–06:05 pre-roll | 278 |
| Scripted vehicles | 0 |

Purpose counts are `BUSINESS_INTERNAL` 28, `BUSINESS_OUTBOUND_PROXY` 68,
`INBOUND_WORK` 18,720, `PRIVATE_INTERNAL` 1,009,
`PRIVATE_OUTBOUND_PROXY` 787, `SCHOOL_INTERNAL_ESCORT` 122,
`SCHOOL_OUTBOUND_ESCORT_PROXY` 158, `THROUGH` 7,194, `VISITOR` 10,080,
`WORK_INTERNAL` 224, and `WORK_OUTBOUND_PROXY` 1,044.

The first generated plan departs less than one second after 06:00. Initialization
runs the same one-second-bounded physics for five simulated minutes, discards
intermediate replay history, and exposes the populated 06:05 result. There is
no scripted warm-start traffic, so every vehicle is traceable to the schedule.

Those values are deterministic model output, not observed traffic in the former
District 1 study window. The 71,267 legs are an explicit microscopic sample,
roughly 1:9 against the evidence note's 0.6-million-leg/day historical proxy
midpoint; runtime statistics are not automatically expanded.
The complete scenario, evidence labels and limitations are documented in
[`DISTRICT1_SCENARIO.md`](DISTRICT1_SCENARIO.md). The schema and algorithms are
documented in
[`DEMAND_AND_SCENARIO_DESIGN.md`](DEMAND_AND_SCENARIO_DESIGN.md).
