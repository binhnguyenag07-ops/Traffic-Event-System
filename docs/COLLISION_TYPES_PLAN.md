# More kinds of collision than rear-end

Design sketch. Nothing here is built.

## What exists

`CollisionDetector` asks one question, per road, per follower:

```
a_required = (v_follower² − v_leader²) / (2 · gap)
collision  ⟺  a_required > a_brake(follower)
```

Who can hit whom is decided by **lateral overlap**, not lane index, so a
filtering motorcycle beside a car is not reported as hitting it.

The driver model that leads into it is real: reaction time enters
`KraussCarFollowingModel.safeSpeedMps`, inattention enters as the σ term in
`dawdling()`, and `DefaultLocalPerceptionService` gives each driver a sight
horizon. So crashes already arise from human error rather than being scripted.

## What that leaves out

The detector loops roads and keeps only vehicles whose `occupiedRoadId` matches,
and requires the other party to be **ahead**. Three whole classes fall outside:

| Not detected | Why |
|---|---|
| Anything in a junction | vehicles crossing hold `occupiedRoadId == null` and are filtered out entirely |
| Side-swipe while changing lane or filtering | the other party must be ahead, so a vehicle moving sideways into an occupied space is never checked |
| Head-on | opposing traffic is a different road, and roads are scanned one at a time |

There is also one thing the perception model cannot currently cause:

```java
return Math.max(DEFAULT_MINIMUM_SIGHT_METRES,
                reaction + braking + DEFAULT_SAFETY_MARGIN_METRES);
```

Sight distance is **defined as the driver's own stopping distance plus a
margin**, so it is always sufficient by construction. A driver can never fail to
see something in time. Limited visibility is therefore not yet a cause of
anything, only a horizon for planning.

## The types to add

Ordered by value for effort. Each needs a lab case before it needs code — the
harness in `data/scenarios/test/` is where "did this actually happen" gets
answered.

### 1. Side-swipe during a lane change or filtering manoeuvre

The cheapest real gain, because the geometry is already there. `LateralExtent`
knows each vehicle's lateral span and `LateralMotionModel` moves it gradually,
so a vehicle sliding sideways into an occupied span is detectable as **an
overlap that appears between two ticks while the two are longitudinally
alongside each other**.

Test: was there lateral overlap this tick that did not exist last tick, with
longitudinal separation smaller than the longer body? Then they touched.

This is also the manoeuvre motorcycle filtering makes constantly, so it is the
type most likely to be observed once it exists.

### 2. Junction and crossing collisions

The largest gap and the largest job. Vehicles in a junction hold no road, so
there is no shared coordinate to measure a gap along. It needs the crossing
geometry the renderer already draws: `LaneGeometryBuilder.connectorsAt` produces
the connector curves through a junction.

Two connectors that intersect are conflicting paths. A collision is two vehicles
occupying the same conflict point within each other's passing window. The
right-of-way strategy already decides who yields, so this type is also the test
of whether that decision is being obeyed.

Needs vehicles to carry a position **along their connector**, which the pose
resolver models but the dynamics do not.

### 3. Head-on

Rare in a one-way district and worth having anyway, because it is the failure
mode of overtaking into the opposing carriageway — which the system will need
once overtaking on two-lane roads is real.

Cheap once carriageways are comparable: `Carriageway.offsetMetres` now gives
each direction a signed side, so two vehicles on opposing roads over one
`physicalRoadId` can be projected onto a common axis and their approach closed.

### 4. Loss of control on a curve

`PolylineGeometry.curvatureRadiusAt` already exists and is already used to limit
cornering speed. A vehicle entering a bend above the speed its radius allows —
because it dawdled, or because the road was misjudged — is a single-vehicle
accident, no second party required.

Attractive because it is the only type that needs no interaction, so it can be
built and tested in isolation.

### 5. Collision into a stationary obstruction

A queue tail, a broken-down vehicle, an accident already blocking a lane. The
rear-end test covers this when the obstruction is a vehicle on the same road,
but not when it is the *accident itself* — the blocked lane is a road condition,
not a body. Making an active accident occupy space would close the loop where
one crash causes the next, which is the realistic and currently missing dynamic.

## The change that makes several of them possible

Give sight distance a source independent of braking capability:

```
horizon = min(driver's attention range,
              geometric sight line along the road's curvature,
              occlusion by a larger vehicle ahead)
```

Today the horizon is derived from the stopping distance, so it can never be the
binding constraint. Once it comes from the road's shape and what is in the way,
a driver can genuinely fail to see a stopped queue round a bend — which is how a
large share of real rear-end and curve accidents happen.

This is one change to `DefaultLocalPerceptionService.sensorHorizon` and it turns
perception from a planning horizon into a cause.

## Shape of the model

Today an accident is one class, `AccidentEvent`, with a capacity reduction and a
set of involved vehicles. That holds up: the *type* is about how the crash was
detected, not about what it does to the road afterwards.

So the proposal is **one detector interface, several implementations**, matching
how routing, car-following and lane-change are already handled:

```
CollisionCause
  List<Collision> detect(network, participants, state)

  RearEndDetector       — what exists today
  SideSwipeDetector     — lateral overlap appearing between ticks
  JunctionConflictDetector
  HeadOnDetector
  CorneringDetector     — single vehicle, curvature versus speed
```

`Collision` grows a `cause` field so statistics can report the mix, and
`AccidentEvent` carries it through to the frame and the UI. Everything
downstream — blocking lanes, stopping the vehicles involved, rerouting others —
stays exactly as it is.

## Suggested order

1. Independent sight distance (small, and it makes the existing type more real)
2. Side-swipe (geometry already present)
3. Cornering (no interaction needed)
4. Stationary obstruction
5. Junction conflicts (largest, needs connector-relative positions)
6. Head-on

Steps 1–4 are inside `domain/vehicle/**`. Step 5 touches junction arbitration and
should be agreed before starting.

## What not to do

- **Do not script accident types into scenarios.** A crash that a scenario file
  declares proves nothing about the model. Every type above must emerge from
  driver behaviour; scenarios only set up the conditions.
- **Do not add a severity model yet.** Speed, mass and angle are all available,
  but severity affects nothing downstream today, so it would be numbers nobody
  uses and nobody can check.
