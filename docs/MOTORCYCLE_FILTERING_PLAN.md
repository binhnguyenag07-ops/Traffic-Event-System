# Motorcycle Filtering Plan

## Status

**Complete.** A motorcycle rides the gap between two lanes when both are blocked
and the gap is wide enough, stays in a lane when filtering buys nothing, and a
row of riders standing abreast now costs the queue one row rather than three.

| Phase | State |
|---|---|
| 1. `LateralExtent`, `TrafficParticipant.getWidth()` | done |
| 2. Overlap-based leader finding, with equivalence proof | done |
| 3. `LateralTarget`, `LaneChangeModel.chooseTarget` | done |
| 4. `FilteringLaneChangeModel`, `FilteringRules` | done |
| 5. Leader finding switched to lateral overlap for every type | done |
| 6. Width-aware queue length and jam ratio | done |

Phase 6 was held back on the grounds that it would change every figure in
`EXPERIMENT_RESULTS.md`. That was wrong: `ExperimentService` benchmarks routing
on static networks and creates no vehicles at all, so a jam ratio computed from
vehicles cannot reach it. The delay was unnecessary.

`Lane.abreastCapacityOf` divides the lane width by the vehicle's width plus its
queueing clearance: one car or bus to a 3.5 m lane, three motorcycles, two on a
3.0 m residential lane. Each vehicle then claims its standstill footprint
divided by that number. Storage is shared between vehicles abreast; the gap at
the lane entry is not, because a rider joining a queue still needs its own body
length there whatever is beside it.

`LaneOccupancy` was deleted alongside this. It was the single-lane predecessor
of `Lane`, superseded when lanes arrived, and nothing in `src/main` had
constructed it since; only its own test kept it compiling.

`MotorcycleLaneChangeModel` has been deleted. It was a delegate to
`MobilLaneChangeModel` with a zero incentive threshold and never filtered
anything, despite reporting its name as `MOTORCYCLE_FILTERING_MOBIL`. Its
replacement reports `MOTORCYCLE_SUBLANE_FILTERING` and earns it.

The rest of this document is the design as built. Everything lives in
`domain/vehicle/**`.

## What filtering actually is

A motorcycle filtering through traffic is not in a lane. It travels along the
boundary between two lanes, or between a lane and the kerb, and passes vehicles
that are themselves sitting on lane centres. Two properties define it:

1. its lateral position is a **free continuous variable**, not one of *n* lane
   centres;
2. what blocks it is decided by **lateral overlap**, not by a shared lane index.

The lateral position is now expressible. The second property is what the model
still cannot represent, and it is the whole of the remaining work.

## Why lane index cannot express it

Three places in the current model assume that sharing a lane index is the same
as being in each other's way.

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

`Vehicle.getWidth()` already exists; `TrafficParticipant` exposes only
`getLength()` and needs the matching accessor.

## Classes

New, all in `domain/vehicle/dynamics`:

- **`LateralExtent`** — record of `centreMetres` and `widthMetres`, with
  `overlaps(other, clearanceMetres)` and `clearGapTo(other)`. Pure value type,
  trivially testable, and the single definition of "in each other's way".
- **`SubLaneOccupancy`** — the lateral counterpart to `RoadOccupancy`. Holds
  every participant on one directed road with its extent, and answers
  `findLeaderFor(participant)` by scanning those whose extents overlap rather
  than those sharing an index.
- **`FilteringLaneChangeModel`** — replaces `MotorcycleLaneChangeModel`. Chooses
  a continuous target offset: the fastest laterally-reachable corridor that is
  wide enough, safe, and not disallowed by the rules below.
- **`FilteringRules`** — the speed and clearance limits, kept separate from the
  search so they can be tuned and tested without touching the geometry.

Changed:

- **`LaneChangeModel`** — `int chooseTargetLane(...)` is the wrong return type
  for a model that picks a position rather than a lane. Add
  `LateralTarget chooseTarget(LaneChangeRequest)` returning an offset and the
  lane index it corresponds to for reporting, and give the interface a default
  implementation that adapts the existing `chooseTargetLane` so MOBIL is
  unaffected.
- **`Vehicle.planMovement`** — plans against the target offset. `laneIndex`
  becomes derived: the nearest lane centre, kept for the API, snapshots, and
  anything still reasoning in lanes.
- **`TrafficParticipant`** — add `getWidth()`.

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

## Delivery sequence

Each phase leaves the suite green and is separately reviewable.

1. **`LateralExtent` plus `TrafficParticipant.getWidth()`.** Pure value work, no
   behaviour change. Unit tests for overlap and clearance arithmetic.
2. **`SubLaneOccupancy` alongside `RoadOccupancy`,** not replacing it. Assert on
   a fixture that for vehicles on lane centres it returns exactly the leaders
   `Lane.findLeaderFor` returns today. This is the equivalence proof that makes
   the swap safe.
3. **`LateralTarget` and the `LaneChangeModel` extension,** with MOBIL adapted
   through the default method. No behaviour change; existing lane-change tests
   must pass untouched.
4. **`FilteringLaneChangeModel` and `FilteringRules`,** wired to motorcycles
   only. Cars, buses, and emergency vehicles keep MOBIL.
5. **Switch leader-finding to `SubLaneOccupancy`** for motorcycles, then for all
   types once phase 2's equivalence holds in the full simulation.
6. **Queue length and jam ratio** — make `getOccupiedLengthMetres` width-aware so
   a row of motorcycles no longer consumes a lane each.

Phase 6 is deliberately last: it changes congestion numbers, and therefore every
experiment result, so it should land only once the movement model is settled.

## Tests

- a motorcycle passes two stopped cars straddling a lane line, and a car in the
  same position does not;
- a motorcycle refuses a corridor 0.2 m too narrow;
- two motorcycles travel side by side within one lane width;
- a filtering motorcycle never exceeds the speed differential cap;
- a corridor closing from the side aborts the manoeuvre and the motorcycle
  returns to a lane centre;
- the equivalence check from phase 2, as a permanent regression test;
- a full-network run on `district1_network.json` finishes with no collision
  reported by `CollisionDetector`.

The last one matters most. `CollisionDetector` currently reasons per lane; it
needs the same lateral-overlap treatment, or it will either miss real conflicts
between filtering motorcycles or invent conflicts that the new model has already
cleared.

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

`PHYSICAL_VIEW_PLAN.md` states that between-lane filtering must not be drawn
before the domain can represent it. Phase 4 is the point at which that condition
is met: from then on a motorcycle's reported offset is a real position it
actually occupies, and the renderer may draw it there.
