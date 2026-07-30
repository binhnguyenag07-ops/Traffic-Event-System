# Demand and scenario design

`VEHICLE_FLOW.md` describes what a vehicle does after it exists. This document
describes how generated vehicles are scheduled, resolved into multi-leg plans
and handed to that lifecycle.

Implementation status: **built and exercised by the bundled scenario**.
`GravityDemandModel`, multi-leg `TripPlan`s, dwell, reachability-aware place
selection, the clock, the demand spawner, file/HTTP parsing and run statistics
are implemented.

That statement is about software capability, not empirical validity. The
bundled case combines verified artifact facts, research-informed citywide
proxies, and explicitly synthetic parameters. The project does not contain a
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
warmUpSeconds
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
The bundled start is 06:00 with a 24-hour horizon, so it samples each clock hour
once and ends its departure window at 06:00 on day `D+1`.

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

In the bundled case `scale = 1.0`; the configured productions therefore map
directly to simulated plan starts. The two 18,000-vehicle/day cordon
productions are still **SYNTHETIC ASSUMPTIONS**, not fitted observations.

`warmUpSeconds` is an initialization pre-roll, not a playback pace. The bundled
value is 300 seconds. `TrafficSimulator` advances the normal demand spawner,
signals, junction arbitration and microscopic vehicle model in one-second
batches, then discards the pre-roll trace/frame history and publishes only the
resulting 06:05 state. It does not alter the later tick size, seek the frontend,
or repeat demand.

`automaticCollisionEvents` defaults to `true` for backwards compatibility and
for focused physics labs. The bundled typical-day scenario explicitly sets it
to `false`: no observed crash-rate/exposure model has been calibrated for this
synthetic demand sample, so its braking-risk detector is not used as a crash
generator. This does not disable car following, gap acceptance, collision-safe
motion, or explicit accident/breakdown events.

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

This is especially important for the
[JICA HOUTRANS follow-up result](https://openjicareport.jica.go.jp/pdf/12305124_01.pdf):
83.0% motorcycle, 5.3% car, and 6.3% bus are **2013 person-trip modal shares**, not
probabilities for creating vehicle instances. A passenger bus can account for
many person trips. The generator has no vehicle-occupancy or bus
route/headway/fleet-allocation conversion, so those shares are context only and
are never copied mechanically into `vehicleMix`.

### How the bundled residential proxy is parameterized

The final scenario uses only the 147 buildings explicitly typed
`RESIDENTIAL`; the 885 `OTHER` buildings are not silently treated as homes.
Several inputs are traceable historical/citywide proxies:

- `tripsPerPersonPerDay = 1.84` uses the non-home-purpose sum for the HOUTRANS
  Table 1.1.2 one-motorcycle household row. The schema field name is broader
  than that historical statistic, so 1.84 remains a research-informed
  production proxy, not a measured rate for these mapped residents.
- Work/school/private/business shares
  `0.3473 / 0.1130 / 0.5033 / 0.0364` are derived from the same table.
- Internal/outbound splits use the historical former-District-1 results in
  HOUTRANS Table 2.1.25: work 26/74, school 42/58, private 40/60, and business
  29/71. A perimeter gateway stands in for the unmodelled outside destination;
  this is an upper-bound OD proxy, not a real gateway count.
- `HOUTRANS_2002_ALL_NON_WALKING_DIGITIZED_PROXY` normalizes a digitization of
  Figures 2.1.18–2.1.19 on p. 2-32 for general private/business/through timing.
- `0.8955 / 0.1045` motorcycle/car is the ratio among the 2024 citywide
  registered stocks cited in `DISTRICT1_SCENARIO.md`, not a traffic-flow or
  person-trip mode share.

The HOUTRANS 2002 trip-rate table is reproduced in Appendix 1 of
[JICA, Data Collection Survey on Traffic Conditions of Southern Roads and Bridges](https://openjicareport.jica.go.jp/pdf/12305017_02.pdf).
The former-District-1 split table and hourly figures are in
[JICA, HOUTRANS Volume 2, Main Text](https://openjicareport.jica.go.jp/pdf/11764636_02.pdf).
The cordon base of 18,000 vehicles/day per side, scenario scale `1.0`, cordon
20/50/30 allocation, dwell ranges, and
`COMMUTE_AM`/`SCHOOL_AM`/`VISITOR_DAYTIME` curves remain synthetic assumptions.

The chosen runtime size is an explicit microscopic sample. HOUTRANS reported
2.323 million daily non-walking trip ends for the former District 1 in 2002;
after area allocation, internal-trip de-duplication, modal shares and observed
occupancy conversion, the evidence note derives a broad 0.4–0.7 million
vehicle-leg/day historical proxy for this 3.05 km² window. The bundled 71,267
simulated legs are about 1:9 against the 0.6-million midpoint. Runtime
statistics remain unweighted simulated-agent counts; no result is silently
expanded.

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

The selected access is physically visible at runtime. At 15 km/h the
authoritative vehicle moves from the building centre to the access node before
occupying its first road, and from the destination access node to the building
centre after leaving its final road. These
`DEPARTING_ORIGIN`/`ARRIVING_DESTINATION` phases accumulate trip time and
distance without creating phantom lane occupancy. After dwell, a return or
later leg repeats the same departure-link lifecycle from its actual current
building.

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

The bundled former District 1 study-window scenario resolves 39,434 generated
plans / 71,267 legs with 0 structural generation failures. That does not
promise immediate road admission: a structurally valid plan that is due while
its entry is full or temporarily closed becomes a normal `BLOCKED` runtime
vehicle and retries through the ordinary routing logic. It is not reclassified
as malformed demand and discarded.

## Runtime hand-off and statistics

`DemandDrivenSpawner` queries the model over
`(lastScannedSeconds, currentElapsedSeconds]`. `GravityDemandModel` stores plans
in departure order and uses an upper-bound binary search, so each poll does not
scan all 39,434 future plans. A due plan is converted into one vehicle, assigned
its first destination and routed through the ordinary routing service.

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

### Reader-facing schedule

`GET /api/simulation/demand/schedule?offset={n}&limit={n}` exposes a bounded
page of the exact already-resolved plan list; it does not regenerate or
resample demand. The default page is 250 and the maximum is 1,000. Top-level
`totalPlans`, `hourlyPlanCounts`, `purposeCounts`, and `vehicleTypeCounts`
summarize the complete schedule, while `offset`, `limit`, and `returnedPlans`
describe the page. Each paged entry carries:

- plan id, purpose, vehicle type, and completion policy;
- human-readable origin and destination names, nullable coordinates for
  disambiguating repeated/generic labels, and ids retained for traceability;
- exact scheduled departure clock/day offset;
- each leg's estimated travel, arrival, dwell, and next departure;
- estimated final/return arrival.

The estimator uses directed Dijkstra free-flow road time plus the 15 km/h
building access links and declared dwell. It deliberately ignores live queues,
signals, closures, and incidents; the UI labels every arrival/return value as
an estimate. Route estimation runs only for the requested page rather than all
39,434 plans. The **24-hour demand** panel renders the complete hourly
aggregate as a read-only profile and requests 12 ordered plan estimates per UI
page. It has no hour/purpose control that could be mistaken for a spawn rule.

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
  "durationHours": 24,
  "seed": 20260728,
  "scale": 1.0,
  "warmUpSeconds": 300,
  "automaticCollisionEvents": false,

  "zones": [
    {
      "id": "north-east-entries",
      "intersectionIds": ["N..."]
    },
    {
      "id": "tourism-core",
      "buildingIds": ["B..."]
    }
  ],

  "demandSources": [
    {
      "id": "north-east-cordon",
      "from": {"kind": "ZONE", "zoneIds": ["north-east-entries"]},
      "production": {
        "kind": "ABSOLUTE",
        "vehiclesPerDay": 18000
      },
      "flows": [
        {
          "id": "visitor",
          "purpose": "VISITOR",
          "share": 0.30,
          "vehicleMix": [
            {"type": "MOTORCYCLE", "share": 0.8955},
            {"type": "CAR", "share": 0.1045}
          ],
          "curve": "VISITOR_DAYTIME",
          "completion": "REMOVE_AT_LAST_STOP",
          "legs": [
            {
              "to": {"kind": "ZONE", "zoneIds": ["tourism-core"]},
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
  "vehicles": []
}
```

The snippet abbreviates the other resident and cordon flows but uses their
current field shape. The bundled case intentionally has no scripted warm-start
traffic.

The shipped JSON also includes a reader-facing `demandMagnitude` object with
the deterministic plan/leg counts, cordon total and sampling warning. It is
audit metadata, not an input consumed by `DemandScenarioParser`, not a hidden
expansion factor, and not permission to multiply runtime statistics.

The parameter catalog supplies all building-type parameters and named
normalized curves. Its top-level `status` is `ASSUMPTION`.

## Load-time invariants

- Exactly one production kind and its matching positive value are required.
- Population-derived production requires a building or zone source containing
  buildings.
- Source ids are unique. Flow ids are unique inside each source; separate
  sources may deliberately reuse the same flow id.
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
| **VERIFIED FACT** | Measured from committed repository data/deterministic output or stated by a cited official source. Its artifact/city/source scope must be explicit. |
| **RESEARCH-INFORMED PROXY** | Official Ho Chi Minh City or historical evidence used only to inform a plausible ratio, shape, or order of magnitude. It is not former District 1 study-window calibration. |
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

Therefore production, output counts and modal shares are modeling controls.
The shipped output is a documented microscopic sample, not a measured demand,
validation result or forecast.
