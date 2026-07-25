# Motorcycle Filtering

Design record for sub-lane filtering. **Built**, in `domain/vehicle/**`.

A motorcycle rides the gap between two lanes when both are blocked and the gap
is wide enough, stays in a lane when filtering buys nothing, and a row of riders
standing abreast costs the queue one row rather than three.

`Lane.abreastCapacityOf` divides the lane width by the vehicle's width plus its
queueing clearance: one car or bus to a 3.5 m lane, three motorcycles, two on a
3.0 m residential lane. Each vehicle claims its standstill footprint divided by
that number. Storage is shared between vehicles abreast; the gap at the lane
entry is not, because a rider joining a queue still needs its own body length
there whatever is beside it.

Two classes went with this work. `LaneOccupancy` was the single-lane predecessor
of `Lane`, superseded when lanes arrived and unconstructed anywhere in
`src/main`. `MotorcycleLaneChangeModel` delegated to `MobilLaneChangeModel` with
a zero incentive threshold and never filtered anything, despite reporting its
name as `MOTORCYCLE_FILTERING_MOBIL`; its replacement reports
`MOTORCYCLE_SUBLANE_FILTERING` and earns it.

## What filtering actually is

A motorcycle filtering through traffic is not in a lane. It travels along the
boundary between two lanes, or between a lane and the kerb, and passes vehicles
that are themselves sitting on lane centres. Two properties define it:

1. its lateral position is a **free continuous variable**, not one of *n* lane
   centres;
2. what blocks it is decided by **lateral overlap**, not by a shared lane index.

Both are now expressible: continuous lateral position came first, and lateral
overlap replaced lane equality after it.

## Why lane index could not express it

Three places in the model had assumed that sharing a lane index is the same as
being in each other's way.

| Location | Assumption | Consequence for a filtering motorcycle |
|---|---|---|
| `RoadOccupancy.of` | membership is `getLaneIndex() == laneIndex` | a motorcycle between lanes has no valid index to hold |
| `Lane.findLeaderFor` | any vehicle ahead in the lane is a leader | a motorcycle is stopped by a car it has ample room to pass |
| `Lane.getOccupiedLengthMetres` | every vehicle consumes its full jam spacing | two motorcycles side by side consume two cars' worth of queue |

Widening `laneIndex` to a fractional "sub-lane index", as SUMO does, would work
but imports SUMO's discretisation choices for no benefit here: the offset is
already a metre value, so the natural move is to compare metres.

## Core change: lateral overlap replaces lane equality

Give every participant a lateral extent — its offset plus or minus half its
width — and treat two participants as longitudinally relevant to each other only
when those extents overlap by more than a clearance margin.

```text
car        offset -1.75, width 1.80  ->  [-2.65, -0.85]
car        offset +1.75, width 1.80  ->  [ 0.85,  2.65]
gap between them: 1.70 m
motorcycle width 0.75 + 2 x 0.30 clearance = 1.35 m  ->  it fits
```

This one substitution produces filtering, side-by-side motorcycles, and correct
leader-finding, without any of them being special-cased. A car keeps behaving
exactly as it does now, because a car sitting on a lane centre overlaps only
vehicles on the same lane centre.

## Classes

Added, all in `domain/vehicle/dynamics`:

- **`LateralExtent`** — record of `centreMetres` and `widthMetres`, with
  `overlaps(other, clearanceMetres)` and `clearGapTo(other)`. Pure value type,
  trivially testable, and the single definition of "in each other's way".
- **`FilteringLaneChangeModel`** — replaced `MotorcycleLaneChangeModel`. Chooses
  a continuous target offset: the fastest laterally-reachable corridor that is
  wide enough, safe, and not disallowed by the rules below.
- **`FilteringRules`** — the speed and clearance limits, kept separate from the
  search so they can be tuned and tested without touching the geometry.
- **`LateralTarget`** — an offset plus the lane index nearest to it, so
  snapshots and statistics that still reason in lanes keep working.

Changed:

- **`RoadOccupancy`** — gained `findLeaderFor(follower, clearanceMetres)`, which
  scans every lane for participants whose extents overlap rather than those
  sharing an index. The design first called for a separate `SubLaneOccupancy`
  beside it; putting the method on `RoadOccupancy` avoided a second class
  holding the same vehicles for the same road.
- **`LaneChangeModel`** — `int chooseTargetLane(...)` is the wrong return type
  for a model that picks a position rather than a lane, so
  `LateralTarget chooseTarget(LaneChangeRequest)` was added with a default
  implementation adapting `chooseTargetLane`, leaving MOBIL unaffected.
- **`Vehicle.planMovement`** — plans against the target offset; `laneIndex`
  became derived from it.
- **`TrafficParticipant`** — gained `getWidth()` and `lateralExtent()`.

## Safety rules

Filtering without constraints produces a motorcycle that threads a 40 km/h gap
at 80 km/h, which is neither realistic nor defensible in a report. Four limits,
all in `FilteringRules`:

1. **Lateral clearance** — at least 0.30 m to each neighbour's extent. Below
   that the corridor does not count as available.
2. **Speed differential** — while any part of the manoeuvre overlaps another
   vehicle laterally, cap speed at the overlapped vehicle's speed plus a margin
   (start at 6 m/s, roughly the 20 km/h differential most filtering guidance
   uses).
3. **Absolute speed ceiling** — no filtering above roughly 14 m/s. Above that
   speed riders use lanes.
4. **No filtering into a closing gap** — implemented as
   `RoadOccupancy.hasManoeuvringNeighbour`: if anything alongside is still
   crossing towards its own lane centre, no corridor is treated as available.
   This is a conservative reading of the rule. It refuses corridors that a
   neighbour is moving *away* from as well as ones it is closing, because a
   vehicle's target is not visible to others during the planning half of a
   sub-step. The check is only possible at all because lateral position is now
   continuous, so a vehicle between lane centres is visibly mid-manoeuvre.

Rules 2 and 4 are the ones that keep the result honest. Without them the model
would report throughput gains that come from motorcycles behaving impossibly.

## Where the behaviour is pinned

- `LateralExtentTest` — overlap and clearance arithmetic.
- `SubLaneLeaderEquivalenceTest` — the proof that made the swap safe: vehicles on
  lane centres get the same leader from the overlap rule as from the old
  lane-index rule, and no pair of the widest vehicles can overlap on any road
  class this project builds.
- `FilteringLaneChangeModelTest` — a rider takes the gap between two stopped
  cars, a car in the same position cannot, a corridor narrowed by a bus is
  refused, and a rider holds position when a neighbour is still mid-manoeuvre.
- `FilteringRulesTest` — the speed differential and absolute ceiling.
- `LaneQueueCapacityTest` — three riders abreast cost one row, cars unaffected.
- `CollisionDetectorLateralTest` — a filtering rider is not reported as colliding
  with the car it is passing.

## What stays out of scope

- **Lateral dynamics with acceleration.** Lateral movement stays constant-speed.
  Steering effort is not what limits filtering, and a constant rate keeps frame
  replay exactly reproducible.
- **Contraflow filtering.** `RoadOccupancy` is per directed road, so a
  motorcycle cannot use the oncoming side. Changing that means pairing directed
  roads, which is `domain/network` and belongs to Student A.
- **Kerbside and pavement riding.** Requires road-edge geometry the domain does
  not carry.

## Effect on the physical view

`PHYSICAL_VIEW_PLAN.md` required that between-lane filtering not be drawn before
the domain could represent it. That condition is met: a motorcycle's reported
offset is a position it actually occupies, so the renderer draws it there and
must not fall back to the lane centre implied by its index.
