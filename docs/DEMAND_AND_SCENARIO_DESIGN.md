# Demand and scenario design

`VEHICLE_FLOW.md` describes what a vehicle does after it exists. This document
describes how generated vehicles are scheduled, resolved into multi-leg plans
and handed to that lifecycle.

Implementation status: **built and exercised by the bundled scenario**.
`GravityDemandModel`, multi-leg `TripPlan`s, dwell, reachability-aware place
selection, the clock, the demand spawner, file/HTTP parsing and run statistics
are implemented.

That statement is about software capability, not empirical validity. The
bundled numbers are explicitly synthetic. The project does not contain a
household survey, cordon count, hourly classified count or calibrated OD matrix
for its former District 1 study window. `district1_typical_day.json` is an
educational deterministic scenario, **not an observed or calibrated traffic
forecast**.

The exact bundled case, its evidence classes and reproducible output are in
[`DISTRICT1_SCENARIO.md`](DISTRICT1_SCENARIO.md).

## End-to-end boundary

```text
map + demand parameter catalog + scenario
                  │
                  ▼
        DemandScenarioParser
                  │
                  ▼
        GravityDemandModel
     (seeded, eager plan expansion)
                  │
                  ▼
        List<TripPlan> ordered by
        departure time, then plan id
                  │
                  ▼
        DemandDrivenSpawner
                  │
                  ▼
     Vehicle + normal routing/dynamics
       dwell → next leg → completion
```

The hand-written `vehicles` array is a second entry path. It is intentionally
still supported and may coexist with generated demand. Those entries are
legacy one-origin/one-destination vehicle configs; they are not authored
multi-leg `TripPlan`s.

## Values and responsibilities

### `DemandScenario`

The parsed scenario supplies:

```text
startTimeOfDaySeconds
durationSeconds
seed
scale
sources: [DemandSource, …]
zones: { id → ZoneDefinition }
parameters: DemandParameters
```

`startTimeOfDay` is parsed into seconds since midnight. `durationHours` or
`durationSeconds` defines the **departure-generation horizon** only. It does
not stop or pause the simulator. A vehicle may depart near the end of the
horizon and complete its travel or dwell after it.

Demand scenarios are currently limited to one departure day. The clock itself
can cross midnight, but this model does not repeat a daily demand schedule.

`seed` controls stochastic rounding, departure time within an hour, vehicle
type, destination and dwell range. With the same map, parameters, scenario,
algorithm and seed, the resolved plan list is reproducible.

`scale` is a dimensionless multiplier on generated source-flow volume. It does
not scale:

- the map;
- scripted `vehicles`;
- road capacity;
- speed or simulation time;
- dwell duration.

In the bundled case `scale = 0.015` is a **SYNTHETIC ASSUMPTION** selected for a
manageable demonstration load. It is not a fitted calibration coefficient.

### `DemandSource`, `FlowSpec` and production

```text
DemandSource
  id
  from: PlaceSpec
  production:
    POPULATION_DERIVED + tripsPerPersonPerDay
    or
    ABSOLUTE + vehiclesPerDay
  flows: [FlowSpec, …]

FlowSpec
  id
  purpose: open PurposeId string
  share
  vehicleMix: [{ type, share }, …]
  curve: normalized 24-hour DemandCurve
  completion: RETURN_TO_ORIGIN | REMOVE_AT_LAST_STOP
  legs: [{ to: PlaceSpec, dwell?: DwellSpec }, …]
```

Flow shares within a source may total less than 1; the remainder is
intentionally unallocated. `vehicleMix` must total exactly 1.

`PurposeId` is metadata, not a closed enum and not hidden behavior. Adding
`DELIVERY` or `MEDICAL` does not require Java changes; the scenario must still
declare its own curve, destinations, mix and completion policy.

There is an important units caveat. Despite the field name
`tripsPerPersonPerDay`, the current generator emits **plan starts**, not
person-trip records:

- one `TripPlan` creates one vehicle;
- a closed plan can contain two or more road legs;
- occupancy per vehicle and non-motorized mode share are not represented.

Consequently generated plan counts must not be compared directly with survey
person-trip totals.

### `PlaceSpec` and `ResolvedPlace`

```text
PlaceSpec.kind:
  BUILDING    selected building types
  BOUNDARY    NORTH | SOUTH | EAST | WEST | ANY
  ZONE        one or more named scenario zones
  EXPLICIT    one building or intersection id
  ORIGIN      the exact original ResolvedPlace, valid only in a leg

ResolvedPlace.kind:
  BUILDING | BOUNDARY | INTERSECTION
```

A `ResolvedPlace` contains the concrete id and its access intersection. The
vehicle never re-rolls a destination while travelling.

Boundary candidates are role-aware:

- a boundary **origin** must have at least one outgoing directed road;
- a boundary **destination** must have at least one incoming directed road.

Geometric boundary selection uses the outer 8% of the current map bounds.
Named zones can be preferable for a shipped scenario because they make entry
and exit semantics reviewable and stable.

For buildings, the resolver currently selects the access point with the
shortest driveway distance, breaking ties by access id. It does not optimize
across all access points during demand generation.

### `TripPlan`, legs and completion

```text
TripPlan
  id
  purpose
  vehicleType
  origin: ResolvedPlace
  legs: [TripLeg, …]
  completion
  departAtSeconds

TripLeg
  to: ResolvedPlace
  dwellSeconds
```

Plan and leg are different counting units. For example:

```text
home → school → home
```

is one plan, one vehicle and two legs. A visitor flow
`entry → landmark → exit` is also one plan and two legs.

`RETURN_TO_ORIGIN` appends the exact resolved origin when needed.
`REMOVE_AT_LAST_STOP` leaves the modeled network after its final stop. Dwell is
resolved once from a fixed value or seeded range. A dwelling vehicle releases
road occupancy, retains its plan, then routes to its next leg when dwell ends.

## Gravity generation

The model eagerly expands the entire departure horizon. Later interval queries
are read-only filters over that ordered list.

For one source, flow and clock-hour interval:

```text
expected plan starts =
    sourceDailyProduction
  × flow.share
  × flow.curve[clockHour]
  × intervalSeconds / 3600
  × scenario.scale
```

The expected value is stochastically rounded with the scenario PRNG. Each
generated plan then samples `vehicleMix`, a departure second inside the interval
and its destinations/dwell.

For `ABSOLUTE`, `sourceDailyProduction` is `vehiclesPerDay`.

For each population-derived building:

```text
sourceDailyProduction =
    footprintGroundAreaM²
  × occupantsPerSquareMetre
  × tripsPerPersonPerDay
```

If no footprint is available, a per-type fallback occupant count is used.
There is **no storey field in `Building`**, so the model does not multiply by
floors. The old four-storey worked example was incorrect.

`OTHER` participates as a low-weight mixed-use proxy because most mapped
buildings have no specific OSM use tag. That prevents all demand being forced
through the small tagged subset, but it is still a synthetic approximation.

For a candidate building destination:

```text
weight =
    attractionWeight(type)
  × max(1, footprintGroundAreaM²)
  × exp(-distanceDecayBeta × straightLineDistanceKm)
```

Non-building destinations have base attraction 1. Distance is geographic
Haversine distance when latitude/longitude exists, otherwise map-coordinate
Euclidean distance. This is a gravity heuristic, not a network-cost or
travel-time calibration.

## Reachability-aware resolution

Randomly choosing every leg independently produced broken plans on the
one-way network. Resolution now checks the complete directed chain before a
candidate may be sampled.

For each possible destination, the model requires:

1. a directed path from the current place to that destination;
2. at least one feasible suffix through every later leg;
3. for `RETURN_TO_ORIGIN`, a final directed path back to the exact origin.

Reachable-node sets are cached per origin intersection. The check is structural
topology: it follows directed roads and is deliberately independent of live
traffic events. A closure activated during a run remains the routing and
rerouting layer's responsibility.

Source handling is explicit:

- `ABSOLUTE` flows sample only origins that can complete the whole chain; if no
  such origin exists, model construction fails with the source and flow ids.
- For `POPULATION_DERIVED`, an origin-flow pair with no feasible chain is
  excluded before stochastic scheduling.
- An unexpected invalid resolved plan is retained in demand failure statistics
  by plan id instead of being spawned.

The bundled former District 1 study-window scenario resolves 216 generated
plans with 0 structural generation failures. That does not promise that no
vehicle will be blocked by a live closure, full downstream road or later
routing condition.

## Runtime hand-off and statistics

`DemandDrivenSpawner` queries the model over
`(lastScannedSeconds, currentElapsedSeconds]`. A due plan is converted into one
vehicle, assigned its first destination and routed through the ordinary routing
service.

Demand statistics report:

```text
scheduledTrips
spawnedTrips
failedTrips
pendingTrips
failedPlanIds
```

Here “trip” means scheduled plan, matching the runtime API name. It does not
mean leg or person trip.

Scenario initialization also normalizes legacy road state before routing any
vehicle:

- `currentVehicleCount = 0`;
- vehicle density = 0;
- congestion = 0;
- non-closed road status = `OPEN`;
- explicit `CLOSED` status is preserved.

The OSM generator now writes those zero runtime defaults as well. This removes
persisted “phantom” occupancy that previously consumed capacity without a
corresponding vehicle object.

## Actual scenario shape

The file/HTTP demand shape is `demandSources`, not a nested
`demand: { model: ... }` object:

```jsonc
{
  "map": "../maps/district1_network.json",
  "demandParameters": "../demand/default_assumptions.json",
  "startTimeOfDay": "06:00",
  "durationHours": 18,
  "seed": 20260728,
  "scale": 0.015,

  "zones": [
    {
      "id": "entry-zone",
      "intersectionIds": ["N..."]
    },
    {
      "id": "landmarks",
      "buildingIds": ["B..."]
    }
  ],

  "demandSources": [
    {
      "id": "cordon",
      "from": {"kind": "ZONE", "zoneIds": ["entry-zone"]},
      "production": {
        "kind": "ABSOLUTE",
        "vehiclesPerDay": 3600
      },
      "flows": [
        {
          "id": "visitor",
          "purpose": "VISITOR",
          "share": 0.20,
          "vehicleMix": [
            {"type": "MOTORCYCLE", "share": 0.65},
            {"type": "CAR", "share": 0.35}
          ],
          "curve": "VISITOR_DAYTIME",
          "completion": "REMOVE_AT_LAST_STOP",
          "legs": [
            {
              "to": {"kind": "ZONE", "zoneIds": ["landmarks"]},
              "dwell": {
                "kind": "RANGE",
                "minSeconds": 3600,
                "maxSeconds": 10800
              }
            }
          ]
        }
      ]
    }
  ],

  "events": [],
  "vehicles": [
    {
      "id": "scripted-warm-start",
      "type": "MOTORCYCLE",
      "currentIntersectionId": "N...",
      "destinationIntersectionId": "N..."
    }
  ]
}
```

The parameter catalog supplies all building-type parameters and named
normalized curves. Its top-level `status` is `ASSUMPTION`.

## Load-time invariants

- Exactly one production kind and its matching positive value are required.
- Population-derived production requires a building or zone source containing
  buildings.
- Source ids are unique; flow ids are unique across the whole scenario.
- Flow shares are in `(0, 1]` and total at most 1 per source.
- Vehicle-mix entries are positive and total 1.
- Curves contain exactly 24 finite non-negative weights totaling 1.
- Completion is mandatory and every flow has at least one leg.
- Place types, zones, explicit ids and boundary selectors must resolve.
- Dwell values are finite and non-negative; ranges are ordered.
- Demand duration is positive and no longer than one day.
- `durationHours` and `durationSeconds` cannot both be declared.

The parser currently accepts only the implemented gravity expansion for
`demandSources`. `TripDemandModel` remains an extension seam:

- `GravityDemandModel` is the file/HTTP scenario implementation.
- `ScriptedDemandModel` exists for programmatic resolved-plan tests and custom
  callers.
- `MatrixDemandModel` is a possible future implementation; it does **not**
  currently exist and must not be documented as a selectable scenario model.

## Claim discipline and limitations

Use these labels consistently:

| Label | Meaning |
|---|---|
| **DATASET FACT** | Measured from committed repository data or deterministic output. It is a fact about this artifact, not automatically the city. |
| **CITY-LEVEL PROXY** | External Ho Chi Minh City or historical evidence used to inform a plausible scenario shape. It is not former District 1 study-window calibration. |
| **SYNTHETIC ASSUMPTION** | An authored model input with no matching local observation in this project. |

The design supports replacing parameters later, but the current scenario still
omits:

- observed hourly cordon volumes and OD calibration for the former District 1
  study window;
- person/household identity, vehicle occupancy and parked-vehicle inventory;
- storeys and reliable building-use coverage;
- walking, cycling, metro, bus timetable and behavioral mode choice;
- demand response to congestion;
- a calibrated travel-time impedance or OD matrix;
- automatic simulation stop at the demand horizon.

Therefore `scale`, output counts and modal shares are demonstration controls.
Do not call them measured demand, validation results or forecasts.
