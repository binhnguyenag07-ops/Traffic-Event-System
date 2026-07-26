# Demand and scenarios: design

`VEHICLE_FLOW.md` describes what a vehicle does once it exists. This describes
what decides that it exists at all.

**This designs a capability, not a scenario.** No traffic numbers are settled
here, and none should be: the densities, mode splits and departure curves are
unknowns waiting on urban and transport research. What has to be settled now is
whether the backend can *host* the problem once those numbers arrive.

The problem, stated plainly. A map carries `N` buildings, each with a tag `n`.
Each building emits some number of vehicles; the type of each vehicle depends on
the tag and on the time of day; so do its destination and its route. Every one of
those dependencies is a function to be supplied later, from research. The design
question is whether each is a parameter the backend reads, or a decision baked
into its code.

Implementation status: the backend capability described here is now built,
including resolved multi-leg plans, dwell, demand generation, scenario loading,
failure reporting and API activation. The numerical inputs shipped with the
default scenario remain labelled assumptions and still require calibration.

Worked examples used throughout — "motorcycles leaving home for work in the
morning", "an errand loop that returns home", "cars passing through from the
north" — are there to test whether the shape can express them. The percentages
in them are invented and are marked as such.

## Does the design host the problem?

The short answer, before the detail. Each dependency in the statement above maps
to one design element, and in every case the research value stays a parameter.

| Dependency | Expressed by | Stays a free parameter? |
|---|---|---|
| How many vehicles a place emits | `DemandSource.production` | yes, population-derived or absolute |
| Vehicle type depends on the tag | `FlowSpec.vehicleMix`, within a source group | yes |
| Vehicle type depends on the hour | `s(f) · m(v|f) · c_f(t)` | yes |
| Destination depends on the tag | each leg's `PlaceSpec` + gravity | yes |
| Destination depends on the hour | which flows are active at `t` | yes |
| Route | existing congestion-aware routing | already dynamic |
| A trip with stops, A→B→C→A | `TripPlan` with legs | structural, not a number |
| Traffic from or to outside the map | the same `FlowSpec`, with a boundary source or leg | yes |
| Whether a vehicle must return | `CompletionPolicy` | explicit and validated |
| A new purpose such as delivery | open `PurposeId`, not a Java enum | data-only |
| Research arriving as an OD matrix instead | `MatrixDemandModel` | strategy swap, no rewrite |

Three of those rows are structural rather than parametric — multi-leg trips,
generic place resolution and explicit completion policy — and they are the
things that genuinely have to be *built* before any research can be plugged in
at all. Boundary traffic is deliberately not a separate model: it is an ordinary
flow whose source or leg resolves to a boundary place. The rest is plumbing for
numbers that do not exist yet.

## Baseline gaps this implementation addresses

These were the four gaps measured before implementation; they are retained here
as the acceptance baseline.

| Gap | Evidence |
|---|---|
| No trip purpose or proportion | `VehicleConfig` is `(id, type, startIntersectionId, destinationIntersectionId)`. Nothing else. |
| No multi-leg trip | Zero matches for `waypoint`, `leg`, `tripChain` in `src/main/java`. A vehicle holds one destination, reaches it, and is removed 5 s later. |
| No departure schedule | `VehicleConfig` has no time field; `initialize` creates the whole fleet at t = 0. |
| No modelled external traffic | Boundary-to-boundary trips exist only in `frontend/src/demoTraffic.js`. The backend has no notion of "outside"; a boundary junction is an ordinary intersection. |

One piece of luck: **`Vehicle.destinationIntersectionId` is no longer `final`**
— PR #52 unsealed it so building routing could re-pick an access point. Multi-leg
trips therefore need no surgery on `Vehicle`; a vehicle that reaches a leg's end
can be given the next destination and re-routed, keeping its id and its
`TripRecord` intact.

## Constraints the design has to live with

These are measured, not guessed, and two of them shape the model.

**885 of 1162 buildings are `OTHER`.** OpenStreetMap tags them `building=yes`
with no name. Only 277 buildings carry a usable purpose:

| Type | Count |
|---|---|
| OTHER | 885 |
| RESIDENTIAL | 147 |
| OFFICE | 57 |
| COMMERCIAL | 36 |
| SCHOOL | 18 |
| HOSPITAL | 17 |
| PARKING | 2 |

A model that only used tagged buildings would run the whole district's traffic
through 147 homes. So `OTHER` has to participate — see below.

**Buildings cover 19.1% of the map.** The building stock is a sample, not a
census, so any population derived from it is a *relative* quantity. A scenario
must therefore carry an explicit `scale`, and the docs must never present the
resulting vehicle count as a real-world figure.

**Two parking buildings.** Parking inventory is not modellable. A closed
`TripPlan` can preserve one vehicle from its origin back to that same origin,
but the model does not claim to know how many parked cars a building owns.

**Routing succeeds about 87% of the time** on District 1, because it is a real
one-way layout and some origin–destination pairs have no directed path. A demand
model will therefore generate trips that cannot be routed. That is expected, and
must be counted and reported rather than silently dropped.

---

## The model

### 1. `PurposeId` — an open label, not an enum

Purpose is metadata for statistics, experiments and UI filtering:
`HOME_TO_WORK`, `ERRAND`, `DELIVERY`, `MEDICAL`, `TOURISM` and any later value
are all ordinary validated strings. Java code must not switch on a closed
`TripPurpose` enum.

Purpose does **not** secretly select a curve or destination policy. Those are
explicit fields on `FlowSpec`. This avoids two sources of truth and means adding
a purpose is a scenario/data change, not a recompile. A shipped purpose catalog
may provide display labels and suggested defaults, but the resolved flow remains
self-contained.

### 2. `PlaceSpec` and `ResolvedPlace`

A source or leg names a *kind* of place, and a `PlaceResolver` selects a concrete
routable place. That is what lets one flow definition stand for hundreds of
vehicles and also lets the same flow shape cover internal, inbound, outbound and
through traffic.

```text
PlaceSpec
  kind: BUILDING | BOUNDARY | ZONE | EXPLICIT | ORIGIN
  types:      [RESIDENTIAL, OFFICE, …]   when kind = BUILDING
  side:       NORTH | SOUTH | EAST | WEST | ANY   when kind = BOUNDARY
  zoneIds:    [district-1-west, …]        when kind = ZONE
  buildingId or intersectionId           when kind = EXPLICIT

ResolvedPlace
  kind: BUILDING | BOUNDARY | INTERSECTION
  id
  accessIntersectionId
```

`ORIGIN` is valid only in a leg. It resolves to the exact `ResolvedPlace` at
which that plan began, never another random building of the same type. `ZONE`
requires an optional zone catalog; a scenario using it against a map with no
zone data is rejected at load time. `EXPLICIT` exists for scripted demos and
tests, not for generated typical-day demand.

Boundary detection uses the outer margin of the map's bounding box — the same
rule `createBoundaryTripPlanner` uses today, moved into the domain. There is no
separate external-trip type.

### 3. `DemandSource`, `FlowSpec` and `CompletionPolicy`

Every generated trip starts from the same structure:

```text
DemandSource
  id
  from: PlaceSpec
  production:
    kind: POPULATION_DERIVED | ABSOLUTE
    tripsPerPersonPerDay      when POPULATION_DERIVED
    vehiclesPerDay            when ABSOLUTE
  flows: [FlowSpec, …]

FlowSpec
  id
  purpose: PurposeId
  share
  vehicleMix: [{ type, share }, …]
  curve: DemandCurve
  completion: RETURN_TO_ORIGIN | REMOVE_AT_LAST_STOP
  legs: [{ to: PlaceSpec, dwell?: DwellSpec }, …]

DwellSpec
  kind: FIXED | RANGE
  seconds                         when FIXED
  minSeconds, maxSeconds          when RANGE
```

`POPULATION_DERIVED` is for homes, offices and other building/zone sources whose
volume comes from the mapped stock. `ABSOLUTE` is for cordon counts, a stadium
release, a scripted visitor stream or any source whose volume research supplies
directly. Both are daily totals distributed by the flow's curve, so the units do
not change meaning between source kinds. An hourly count table is imported as
its daily total plus its normalized 24-hour shape.

All flows under one source divide that source's production, so their shares must
sum to at most 1. Each flow then divides its vehicles by `vehicleMix`, whose
shares must sum exactly to 1. A one-mode flow simply has one entry with share 1.

`RETURN_TO_ORIGIN` makes the generated plan end at its exact origin. The model
appends the resolved origin leg when it is not already last, so conservation
cannot be lost because a scenario author forgot an `ORIGIN` leg.
`REMOVE_AT_LAST_STOP` is an explicitly open trip: the vehicle leaves the model
after its final stop. A completion policy is required; there is no silent
default.

`DwellSpec` is resolved once into concrete `dwellSeconds` on `TripLeg`. A range
uses the scenario seed, so work-day length and errand duration can vary without
making replay nondeterministic. More distributions may be added as data readers
behind `DwellSpec`; vehicle movement still consumes only resolved seconds.
An omitted dwell is an explicit shorthand for fixed zero seconds.

This is intentionally one schema. Building→building, building→boundary,
boundary→building, boundary→boundary and multi-stop loops differ only in their
`PlaceSpec`s and completion policy.

### 4. `TripLeg` and `TripPlan`

```text
TripLeg
  to: ResolvedPlace
  dwellSeconds

TripPlan
  purpose: PurposeId
  vehicleType
  origin: ResolvedPlace
  legs: [TripLeg, …]
  completion: CompletionPolicy
  departAtSeconds
```

Note the difference from `PlaceSpec`: a scenario says "some office" or "the
north boundary"; a `TripPlan` says exactly which building or boundary
intersection. The model resolves every spec once, when it generates the plan —
the vehicle never re-rolls a destination mid-trip, so a run is reproducible and
a plan is inspectable.

A →B →C →A is one `TripPlan` and one vehicle. The vehicle is created once at
`departAtSeconds` and removed only after the last leg. `TripRecord` measures the
whole journey, which is the point: one loop's total travel time is meaningful;
three unrelated vehicles' times are not.

### 5. `DemandCurve`

A flow owns a 24-hour weight vector summing to 1. It distributes that flow's
share of its source's daily production. A morning commute can peak at
07:00–08:00, school earlier, errands around midday and delivery throughout the
day without any Java code knowing those purpose names.

The curve is read against `SimulationClock`, which already carries a time of day
and wraps at midnight. Its shape and numbers are research parameters stored in
data.

### 6. `TripDemandModel`

**Nobody writes generated trips by hand.** A scenario declares sources and
flows; the model expands them into concrete `TripPlan`s against the places on
the current map. Given the network, scenario, clock and seed, it answers which
trips begin in an interval.

For source `g`, flow `f`, vehicle type `v` and hour `t`, the common allocation is

```text
trips(g, f, v, t) =
    production(g) · s(f) · m(v | f) · c_f(t) · scale
```

For a population-derived building `b`, `production(g)` expands to
`P(b) · r(g)`. For an absolute source it is the supplied daily vehicle count.

- `P(b)` — occupancy: footprint area × per-type density, falling back to a
  per-type constant when no footprint is recorded.
- `r(g)` — trips per occupant per day for the source.
- `s(f)` — the flow's share of its source's daily trips.
- `m(v | f)` — vehicle type share inside the flow.
- `c_f(t)` — the flow's normalized departure curve.

The source decides how much demand exists; `PlaceResolver` and the gravity model
resolve each leg. For a building destination, attraction is weighted by the
candidate's size, requested types and distance. Purpose is a label, not hidden
selection logic.

A worked line, with placeholder constants: a 180 m² residential building at four
floors gives roughly 20 occupants; at 2.4 trips per occupant per day that is ~48
trips; a flow share of 45% is ~21; a motorcycle mix of 80% is ~17; a morning
curve putting 35% into 07:00–08:00 gives about 6 motorcycles in that hour.
Multiply across the district and dial with `scale`.

Two further rules:

- **`OTHER` buildings count as mixed use.** They emit trips and attract them at
  a reduced, explicitly assumed weight. Excluding 76% of the building stock
  would be worse than approximating it.
- **Seeded.** Same seed, same resolved places, types and departure times, because
  experiments and frame replay depend on reproducibility.

#### It is an interface, not a class

The gravity model derives origin–destination demand from buildings. Research may
instead arrive as a zone OD matrix or a scripted list, so the generation
strategy remains replaceable:

```text
TripDemandModel
  List<TripPlan> tripsBeginningIn(fromSeconds, toSeconds)

  GravityDemandModel   — expands DemandSource and FlowSpec with gravity
  MatrixDemandModel    — maps zone OD rows to concrete ResolvedPlaces
  ScriptedDemandModel  — resolves a hand-written plan list
```

All implementations produce the same `TripPlan`s. The spawner, vehicle runtime
and statistics never know whether a plan began at a building, boundary, zone or
explicit test point. Swapping the demand source is a scenario choice, not a
refactor.

### 7. `DemandDrivenSpawner`

Each tick, ask the model for due plans and create one vehicle per plan through a
generic `createVehicleForPlan`. It routes between each leg's resolved access
intersections. Existing `createVehicleBetweenBuildings` becomes a convenience
adapter, not the abstraction on which demand depends.

On reaching a leg's end the vehicle dwells, then takes the next leg. Only after
the last leg does it become `ARRIVED` and get removed.

Dwelling gets its own `DWELLING` status so statistics do not confuse a planned
stop with congestion. A dwelling vehicle releases road occupancy, retains its
plan and current `ResolvedPlace`, and is not rendered as if it were sitting in a
lane. Statistics report road-active, dwelling and completed vehicles separately;
the headline traffic count uses road-active vehicles. This is decided in the
design rather than deferred into implementation.

---

## The unknowns, enumerated

This design deliberately fixes **no traffic numbers**. Every quantity below is a
free parameter waiting for urban and transport research to supply it. Listing
them is half the point of the design: the backend is finished when all of these
are *inputs*, and none of them is a literal in a `.java` file.

| Symbol | Quantity | Depends on | Where a real value comes from |
|---|---|---|---|
| `P(b)` | occupants per building | tag, footprint area, storeys | building-density study |
| `r(g)` | trips per occupant per day | source definition | household travel survey |
| `s(f)` | flow share of one source | source, purpose | travel survey |
| `m(v|f)` | vehicle mix within a flow | flow and source tag | mode-share survey |
| `c_f(t)` | hourly departure profile | flow | traffic counts / survey |
| `A(b)` | attraction as a destination | tag, area | employment or POI data |
| `β` | distance decay in the gravity model | — | calibration against counts |
| `w(OTHER)` | mixed-use weight for untagged buildings | — | assumption, or better OSM tagging |
| `Q(g)` | absolute vehicles per day | source place or cordon | cordon / venue count |

Two consequences for how this gets built:

- **Parameters live in data, not code.** A `data/demand/` table keyed by tag,
  loaded like the map is. Changing `r(RESIDENTIAL)` from 2.4 to 3.1 when a survey
  says so must be a file edit, never a recompile.
- **Until a citation exists, a number is labelled an assumption**, the same rule
  `FilteringRules` follows. The traffic will look plausible whatever the inputs;
  only the labelling keeps it honest. A confident-looking morning peak built from
  invented constants is worse than no peak, because it reads as a finding.

### What the design does *not* guarantee

Worth naming so nobody discovers it later as a surprise.

**Closed plans conserve vehicles, not people or parking inventory.**
`RETURN_TO_ORIGIN` preserves one vehicle through all stops and back to the exact
place where it began. `REMOVE_AT_LAST_STOP` explicitly does not. Two independent
flows such as morning home→work and evening work→home remain independent draws
and must not be presented as the same commuters. A typical-day scenario should
therefore model commuting, school runs and errands as closed plans unless an
experiment deliberately needs open demand.

The model still does not maintain a parked-vehicle count or household identity
inside each building. Adding those later would be a separate `PlaceInventory`
capability; neither `TripPlan` nor the scenario schema has to change.

**Nobody reacts to congestion.** Departure times are drawn before the run and
never revised, so a jam does not make anyone leave earlier or stay home. Routing
*is* congestion-aware, so vehicles avoid blocked roads — but demand itself is
fixed. This is standard for a model at this level and far short of what a real
study would want.

## The scenario file

One file describes a run: which map, when it starts, how long, and what travels.
It names no individual building or vehicle for generated demand and is written
once per experiment, not per run.

Demand is grouped by **source place and production basis**, and each source's
production divides among its flows. A source may be buildings, a boundary, a
zone or an explicit demo point. This grouping makes shares have one unambiguous
denominator without creating separate internal and external flow schemas.

```jsonc
{
  "map": "data/maps/district1_network.json",
  "startTimeOfDay": "06:00",
  "durationHours": 18,       // long enough for closed commute plans to return
  "seed": 42,
  "scale": 1.0,              // calibrates all generated demand together

  "demandSources": [
    {
      "id": "homes",
      "from": {
        "kind": "BUILDING",
        "types": ["RESIDENTIAL", "OTHER"]
      },
      "production": {
        "kind": "POPULATION_DERIVED",
        "tripsPerPersonPerDay": 2.4
      },
      "flows": [
        {
          "id": "commute-loop",
          "purpose": "COMMUTE",
          "share": 0.55,
          "vehicleMix": [
            { "type": "MOTORCYCLE", "share": 0.80 },
            { "type": "CAR",        "share": 0.20 }
          ],
          "curve": "MORNING_PEAK",
          "completion": "RETURN_TO_ORIGIN",
          "legs": [
            {
              "to": { "kind": "BUILDING", "types": ["OFFICE", "COMMERCIAL"] },
              "dwell": { "kind": "RANGE", "minSeconds": 28800, "maxSeconds": 36000 }
            }
          ]
        },
        {
          "id": "moto-errand-loop",
          "purpose": "ERRAND",
          "share": 0.20,
          "vehicleMix": [{ "type": "MOTORCYCLE", "share": 1.0 }],
          "curve": "MIDDAY",
          "completion": "RETURN_TO_ORIGIN",
          "legs": [
            {
              "to": { "kind": "BUILDING", "types": ["COMMERCIAL"] },
              "dwell": { "kind": "FIXED", "seconds": 600 }
            },
            {
              "to": { "kind": "BUILDING", "types": ["SCHOOL"] },
              "dwell": { "kind": "FIXED", "seconds": 300 }
            }
          ]
        }
        // the remaining 25% of daily production is intentionally unallocated
      ]
    },
    {
      "id": "north-cordon",
      "from": { "kind": "BOUNDARY", "side": "NORTH" },
      "production": {
        "kind": "ABSOLUTE",
        "vehiclesPerDay": 7200
      },
      "flows": [
        {
          "id": "north-south-through",
          "purpose": "THROUGH",
          "share": 0.75,
          "vehicleMix": [{ "type": "CAR", "share": 1.0 }],
          "curve": "FLAT",
          "completion": "REMOVE_AT_LAST_STOP",
          "legs": [
            { "to": { "kind": "BOUNDARY", "side": "SOUTH" } }
          ]
        },
        {
          "id": "north-visitor",
          "purpose": "TOURISM",
          "share": 0.25,
          "vehicleMix": [
            { "type": "CAR",        "share": 0.70 },
            { "type": "MOTORCYCLE", "share": 0.30 }
          ],
          "curve": "DAYTIME",
          "completion": "REMOVE_AT_LAST_STOP",
          "legs": [
            {
              "to": { "kind": "BUILDING", "types": ["COMMERCIAL"] },
              "dwell": { "kind": "RANGE", "minSeconds": 1800, "maxSeconds": 7200 }
            },
            { "to": { "kind": "BOUNDARY", "side": "ANY" } }
          ]
        }
      ]
    }
  ],

  "events": [ /* unchanged from today's format */ ],

  "vehicles": [ /* still supported: a scripted vehicle, for a demo */ ]
}
```

The same schema now covers all four direction families:

- building→building: `commute-loop`;
- boundary→boundary: `north-south-through`;
- boundary→building→boundary: `north-visitor`;
- building→boundary: use a building source and a boundary final leg.

`RETURN_TO_ORIGIN` turns the commute and errand examples into closed plans
without a separately sampled evening flow. A delivery flow is the same shape:
give it purpose `DELIVERY`, a depot source, any number of building or zone legs,
and `RETURN_TO_ORIGIN`. No new Java enum or flow class is required.

### Extension contract

The no-refactor boundary is explicit:

- A new purpose, vehicle mix, departure curve, source volume, sequence of stops,
  building type, boundary direction or completion choice is **data only**.
- A new demand source such as an OD matrix is a new `TripDemandModel`; the
  spawner, vehicle and statistics remain unchanged.
- A new place-selection mechanism is a new `PlaceResolver` variant; resolved
  plans and vehicle movement remain unchanged.
- A behaviour that changes a plan while it is running — passenger pickup,
  parking inventory, dispatch reacting to congestion — is a new simulation
  capability, not merely a new flow. It may require code, but it must still emit
  or update the same `TripPlan`/`TripLeg` boundary rather than bypass it.

This is the acceptance test for the architecture: adding `DELIVERY` or
boundary→hospital→boundary must not touch Java; adding live taxi dispatch may
add a planner, but must not require another vehicle lifecycle.

Ship one of these as `data/scenarios/district1_typical_day.json` so the common
case needs no writing at all — a run picks the default and only an experiment
that wants something unusual edits a copy.

A hand-written `vehicles` list stays supported, and a scenario may carry both. A
scripted incident against a background of generated traffic is exactly what a
demonstration needs.

### Validation, at load time and loudly

- Every source declares exactly one production kind and the matching positive
  value. `POPULATION_DERIVED` requires a building or zone source;
  `ABSOLUTE` accepts any source kind.
- **Flow shares within one source must not exceed 1.** A remainder under 1 is
  legal and means that part of production is intentionally unallocated.
- Every `vehicleMix` is non-empty and sums to exactly 1.
- `purpose` is a non-blank stable identifier; it does not need a Java enum.
- `completion` is mandatory. `RETURN_TO_ORIGIN` produces an effective final
  origin leg; `REMOVE_AT_LAST_STOP` must be intentional in the scenario.
- A source or leg whose `PlaceSpec` resolves no candidate is an **error**, not
  an empty flow. This covers missing building types, zone data, explicit ids and
  boundary sides.
- Each `DwellSpec` has finite non-negative values and a valid range.
- `curve` must name a known normalized curve.
- Resolved plans must have at least one leg. The generator attempts reachability
  for every consecutive pair; a failure is recorded instead of producing a
  broken plan.
- Unroutable generation failures are counted and reported at the end of a run,
  not discarded in silence — roughly one in eight may fail on this map.

## Where the code goes

| Piece | Package | Owner |
|---|---|---|
| `PurposeId`, `PlaceSpec`, `ResolvedPlace`, `PlaceResolver` | new `domain/demand` | Student C |
| `DemandSource`, `FlowSpec`, `CompletionPolicy`, `DwellSpec`, `DemandCurve` | `domain/demand` | Student C |
| `TripLeg`, `TripPlan` | `domain/demand` | Student C |
| `TripDemandModel` interface + `GravityDemandModel` | `domain/demand` | Student C |
| `TripPlan` on `Vehicle`, leg advance, dwell | `domain/vehicle` | Student C |
| `DemandDrivenSpawner` | `simulation` | shared with D, agreed |
| Parameter tables | `data/demand/` | Student C |
| Scenario parsing | `persistence` | Student B's area |
| Scenario endpoint and UI | `api`, `frontend` | Student D's area |

The demand model is pure: network in, seed in, trip plans out. No simulation
state, so it can be tested exhaustively without running a tick — and a research
value can be swapped in and its effect measured without starting a simulation at
all.

## Build order

Ordered so that the structural work — the part research cannot supply and cannot
begin without — comes first, and the parameter plumbing follows.

1. **Resolved plan values.** Build `PurposeId`, `ResolvedPlace`,
   `CompletionPolicy`, `TripLeg` and `TripPlan` first.
2. **Multi-leg vehicle movement.** Drive a hand-built plan through leg advance,
   seeded dwell and final removal. No demand model or scenario parser yet.
3. **`PlaceSpec` and `PlaceResolver`.** Implement building, boundary, zone,
   explicit and origin resolution behind one interface.
4. **`DemandSource`, `FlowSpec`, `DwellSpec` and `DemandCurve`.** Pure values
   and load-time validation. No closed purpose enum or external flow class.
5. **`TripDemandModel` interface + `GravityDemandModel`.** Pure: network and
   seed in, trip plans out. Where the real work is.
6. **`DemandDrivenSpawner` + `createVehicleForPlan`.** Wires generic resolved
   places and due plans to the tick.
7. **Parameter tables in `data/demand/`.** Placeholder constants, every one
   marked as an assumption, ready to be replaced wholesale when research lands.
8. **Scenario file and loader.** Last, because it is the surface over everything
   above and its shape will be clearer once they exist.

Steps 1–5 need no agreement with anyone else — they are all inside Student C's
packages. Steps 6 and 8 cross package boundaries and need their owners to agree.

`MatrixDemandModel` is deliberately not on this list. It is the reason
`TripDemandModel` is an interface, but nothing should be built for it until an
actual matrix exists to read.

## Deliberately out of scope

- **Parking.** Two parking buildings on the map; there is nothing to model.
- **Public-transport timetables.** Buses keep their current routing.
- **Behavioural mode choice.** A flow declares a data-driven `vehicleMix`; the
  model samples it but does not infer whether a person decides to drive or ride.
- **Multi-day runs.** The clock counts days, but demand repeats identically.
- **Feedback from congestion to demand.** People do not reschedule when the road
  is blocked. Real, and far beyond this project.

## The risk worth naming

Beyond the parameters, one thing about this map cannot be fixed by better
research: the building stock covers 19.1% of the district and 76% of it is
untagged. Even with perfect densities and perfect curves, `P(b)` is being
computed over a *sample*. So the report must say that the population is derived
from OpenStreetMap building footprints — not from any census — and treat the
resulting vehicle counts as relative quantities that `scale` calibrates against
an observed count, not as absolute predictions.

That is a limitation of the data, and stating it is the difference between a
model and a claim.
