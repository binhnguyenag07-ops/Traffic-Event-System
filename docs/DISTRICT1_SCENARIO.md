# 24-hour weekday proxy in the former District 1 study window

This is the evidence and reproducibility note for
`data/scenarios/district1_typical_day.json`.

The scenario is a deterministic educational workload for one **central study
window in the former District 1 area**. It is not a calibrated demand model, an
observed typical day or a forecast. “Typical” remains in the historical
filename for compatibility; the reader-facing scenario name is:

```text
Central District 1 - 24-hour Weekday Proxy
```

## Administrative-name note

`District 1` / `Quận 1` in the filename and scenario name is a familiar
historical-geographic label, not a claim that a current district-level
administrative unit has that boundary. Under Article 51 of Vietnam's 2025 Law
on Organization of Local Government, district-level authorities, including
urban districts (`quận`), ended operation on **1 July 2025**. Article 1 defines
two local-government levels: provincial level (including centrally governed
cities) and commune level (communes, wards and special zones).

A July 2025 Ho Chi Minh City Media Center article, for example, describes the
new Sài Gòn Ward as formed from Bến Nghé Ward, part of Đa Kao Ward and Nguyễn
Thái Bình Ward of the **former District 1**. The former district was reorganized
across multiple new wards, so the map rectangle must not be relabelled as if it
were wholly within Sài Gòn Ward. This document therefore uses “former District
1 area” or “study window” for geographic continuity.

## Evidence labels

Map, demand-input and deterministic-output quantities use these labels. Legal
dates and the former administrative area's cited size are external scope
context rather than demand evidence.

| Label | Meaning |
|---|---|
| **VERIFIED FACT** | Directly measured from the committed map/scenario or its deterministic regression test, or stated by the cited official source. The sentence must state whether its scope is this artifact, Ho Chi Minh City, or another source population. |
| **RESEARCH-INFORMED PROXY** | Official Ho Chi Minh City-wide or historical transport evidence used only to motivate a plausible ratio, shape, or order of magnitude. It is not a study-window count, OD matrix, or vehicle-flow calibration. |
| **SYNTHETIC ASSUMPTION** | An authored input without matching local observational evidence in this project. |

The former District 1 area and role cited below are external scope/context
references, not demand calibration inputs.

## Geographic scope

**VERIFIED FACT (committed generator).** `tools/generate_osm_map.py` uses:

```text
south  10.7685
west  106.6905
north  10.7825
east  106.7085
```

At this latitude the rectangle is approximately 1.55 km × 1.97 km, or
3.05 km². The committed map contains:

| Dataset object | Count |
|---|---:|
| Intersections | 389 |
| Directed roads | 730 |
| Buildings | 1162 |
| Land-cover polygons | 243 |

The old official District 1 overview reports the former administrative area as
7.7211 km² and describes its historical central administrative, diplomatic,
commercial and tourism role. Those statements are a **historical scope
baseline**, not a description of a current district-level unit and not a demand
measurement. The map rectangle is only a central subset of that former area; it
must not be called a full former District 1 network.

The map also is not a census:

| Building type | Dataset count |
|---|---:|
| `OTHER` | 885 |
| `RESIDENTIAL` | 147 |
| `OFFICE` | 57 |
| `COMMERCIAL` | 36 |
| `SCHOOL` | 18 |
| `HOSPITAL` | 17 |
| `PARKING` | 2 |

**VERIFIED FACT (committed map).** The OSM snapshot date is not embedded, and 76.2% of mapped
buildings are `OTHER`. Population and attraction derived from these footprints
are therefore relative model inputs, not census estimates.

### Direction and lane audit

The map's large number of one-way roads is not, by itself, a data error. The
complete audit compared all 439 source OSM ways with a fresh Overpass response
and then corrected the committed direction, lane, and name mismatches found.

| **VERIFIED FACT (audited artifact)** | Count |
|---|---:|
| Directed `Road` objects | 730 |
| Physical road segments | 560 |
| One-way physical segments | 390 (69.6%) |
| One-way because OSM says `oneway=yes` | 380 |
| One-way because the OSM way is a roundabout | 10 |
| One-way segments with at least 3 lanes | 168 |
| Those with a direct OSM lane tag | 155 |
| Those using conservative inference/defaults | 13 |
| Source OSM ways with a remaining direction/lane/name mismatch after fixes | 0 of 439 |

A three-lane one-way road is internally consistent: `lanes=3` is the width
available in that direction, not evidence of a reverse carriageway. When OSM
says `lanes=3` and `oneway=no` without `lanes:forward`/`lanes:backward`, the
generator conservatively creates one lane in each direction rather than
inventing an asymmetric split.

Eleven audited OSM ways have `oneway=yes` together with
`oneway:motorcycle=no`. That is an explicitly documented limitation: the domain
currently has one direction graph for all vehicle types, so adding a reverse
road would incorrectly allow cars and buses as well. The committed graph keeps
the general one-way direction until vehicle-specific access rules exist.

## What external evidence does and does not support

### District role and central corridors

The old official District 1 overview supports including administrative/work
and visitor/tourism purposes as relevant historical central-area use cases. It
supplies no hourly traffic volumes, modal shares or OD probabilities.

The Ho Chi Minh City Traffic Police notice for a March 2025 central-area event
names Lê Duẩn, Đồng Khởi, Tôn Đức Thắng, Nguyễn Huệ, Hàm Nghi, Pasteur and
nearby alternative corridors. This supports describing the selected map as a
recognizable central corridor network. The notice is event-specific; it is
**not** evidence for weekday demand volumes or peak curves.

### Vehicle mix

**VERIFIED FACT (citywide registered stock, not study-window flow).** The Ho
Chi Minh City Media Center reported in December 2024 that the city managed more
than 9.4 million vehicles, including about 980,000 cars and more than 8.4
million motorcycles, and that the bus system had roughly 2,200 vehicles.
Among only the reported motorcycle and car stocks, motorcycles are
approximately 89.6%.

That is a stock ratio for the whole city, not:

- traffic flow through this study window;
- a trip-mode share;
- a classified count for this study window;
- evidence that the scenario's exact vehicle mix was observed in District 1.

**VERIFIED FACT (2013 person trips, not vehicle instances).** JICA's HOUTRANS
follow-up report gives 2013 Ho Chi Minh City person-trip modal shares of 83.0%
motorcycle, 5.3% car, and 6.3% bus. These figures count trips by people. A bus
trip is one passenger trip, not one bus, and car/motorcycle occupancies are not
converted by this project.

The citywide stock ratio and the HOUTRANS shares are
**RESEARCH-INFORMED PROXIES** only. In particular, the HOUTRANS 83.0/5.3/6.3
shares must **not** be copied into `vehicleMix`: that field samples one simulated
vehicle instance per plan. The random manual test-spawn button uses only the
8.4-million/980-thousand registered-stock ratio as a transparent motorcycle/car
proxy and excludes buses because no route, fleet allocation, or headway model
exists.

The scheduled scenario likewise applies `0.8955 / 0.1045` uniformly as a
**RESEARCH-INFORMED PROXY**. Uniform application is a modeling assumption; the
result must not be described as a current District 1 classified count.

### Volumes and time profiles

No current OD matrix or count programme matching this exact 3.05 km² rectangle
is available in the repository. There are, however, strong official historical
anchors showing the correct order of magnitude:

- **VERIFIED FACT (historical former District 1, person trips).** HOUTRANS
  Table 2.1.24 reports 1.160 million generated and 1.163 million attracted
  non-walking person trips/day in 2002, or 2.323 million trip ends. Table
  2.1.25 reports purpose-specific intra-District-1 shares from 26% to 42%.
- **VERIFIED FACT (one adjacent gateway, vehicle flow).** A TCIP/World Bank
  environmental and social assessment reports a November 2019 classified count
  at the Thủ Thiêm tunnel of 35,613 vehicles/hour in the peak period, including
  32,722 motorcycles and 2,482 cars. This is one bidirectional gateway, not a
  count of vehicles staying in the map rectangle.
- **VERIFIED FACT (one adjacent gateway, daily motorcycles).** Ho Chi Minh City
  reported roughly 230,000 motorcycle crossings/day through the Sài Gòn River
  tunnel over the first eleven months of 2017, with managed peaks at 06:00–09:00
  and 16:00–19:00.

These measurements prove that 156 agents/day was a software smoke workload,
not plausible central traffic. They still do not identify this model's exact
gateways, OD allocation, vehicle dwell or current-day demand.

### Microscopic sampling envelope

The following calculation is a **RESEARCH-INFORMED PROXY with explicit
synthetic steps**, not a measured count:

```text
map area share = 3.05 / 7.7211 = 0.395
area-allocated 2002 trip ends = 2,323,000 × 0.395 = 917,635/day
unique journeys touching window = trip ends × (1 - intra-share / 2)
                                = 724,931–798,342/day
```

Applying the HOUTRANS modal shares and observed urban vehicle occupancies gives
approximately 411,000–453,000 motorized vehicle legs/day for 2002. Applying the
citywide 2002→2013 non-walking inter-zonal growth and 2013 mode shares gives
approximately 623,000–686,000 legs/day. Area-proportional allocation is the
largest assumption: a central business area need not have the former district's
average density.

The defensible historical order-of-magnitude envelope used here is therefore
0.4–0.7 million vehicle legs/day, with 0.6 million as a presentation midpoint.
The bundled run creates 71,267 explicit legs, or about one simulated agent leg
per 8.6 midpoint legs. This keeps roughly 300 due vehicles in the first
five-minute pre-roll and thousands of resolvable agents through the day without
claiming a one-to-one reconstruction. Runtime metrics are **not** multiplied by
8.6; they report only simulated agents.

TCVN 13592:2022 permits a peak-hour volume of 10–15% of daily traffic only as a
fallback when no study is available. This is useful as a broad
**RESEARCH-INFORMED PROXY/sanity range**, not a District 1 observation and not a
24-hour curve. It does not determine the exact hourly weights below.

Historical HOUTRANS evidence is used more directly, but still only as a proxy.
Table 1.1.2 is a later JICA report's reproduction of the 2002 HOUTRANS
trip-rate table:

- Table 1.1.2 supplies a one-motorcycle-household non-home-purpose total of
  1.84 and work/school/private/business shares
  `0.3473 / 0.1130 / 0.5033 / 0.0364`;
- former-District-1 Table 2.1.25 supplies historical internal/outbound splits:
  work 26/74, school 42/58, private 40/60, and business 29/71;
- the normalized
  `HOUTRANS_2002_ALL_NON_WALKING_DIGITIZED_PROXY` curve digitizes Figures
  2.1.18–2.1.19 on p. 2-32.

These are **RESEARCH-INFORMED PROXIES**, not current household behavior or
counts for the committed map. The 1.84 source value is placed into the current
`tripsPerPersonPerDay` field even though the historical row is more specific;
the outbound share ends at one of eight synthetic perimeter gateways because
the network does not contain the true outside destination.

The research-informed inputs are HOUTRANS-derived `1.84`, resident purpose
shares, historical internal/outbound splits and digitized general curve, plus
the 2024 registered-stock vehicle ratio. Their use on this map remains proxy
modeling, not calibration.

The following remain **SYNTHETIC ASSUMPTIONS**:

- 18,000 `vehiclesPerDay` at each of two modeled cordons;
- cordon 20/50/30 flow shares;
- `scale = 1.0`;
- the 300-second pre-roll length;
- `automaticCollisionEvents = false` because no crash-frequency model is
  calibrated for the synthetic agent sample;
- purpose-specific `COMMUTE_AM`, `SCHOOL_AM`, and `VISITOR_DAYTIME` curves;
- building occupancy and attraction parameters;
- gravity decay `β = 0.35`;
- dwell ranges;
- the seven-landmark visitor zone;
- the mapping of perimeter gateways to unmodelled outside destinations.

The top-level `status: "ASSUMPTION"` in
`data/demand/default_assumptions.json` is deliberate.

## Scenario clock and sampling

| Field | Value | Classification |
|---|---:|---|
| `startTimeOfDay` | 06:00 | SYNTHETIC ASSUMPTION |
| `durationHours` | 24 | SYNTHETIC ASSUMPTION |
| `seed` | 20260728 | reproducibility control |
| `scale` | 1.0 | direct simulated-volume multiplier |
| `warmUpSeconds` | 300 | SYNTHETIC ASSUMPTION |
| `automaticCollisionEvents` | false | modelling-scope control |

`durationHours` is the **departure horizon**, from 06:00 on day `D` through
06:00 on day `D+1`. This covers every clock hour exactly once. It does not
auto-stop the simulator; vehicles may still be travelling, dwelling, or
returning after the horizon.

Initialization advances the normal one-second-bounded microscopic model from
06:00 through 06:05, then discards intermediate replay history. The header
therefore first displays the authoritative 06:05 state; this pre-roll is not a
frontend playback pace. The 24-hour demand panel shows a read-only full-schedule
hourly profile and pages named origin/destination plans from the server. Its
arrival, next-departure, and return times are deterministic free-flow estimates;
signals, queues, closures, and incidents determine actual runtime arrival.

Automatic braking-risk-to-accident conversion is disabled only for this
typical-day scenario. At this authored cordon volume it otherwise mistakes
routine dense approaches for a calibrated crash process and creates cascading
lane obstructions. Manual incidents and dedicated collision test scenarios
still exercise the accident model.

`scale` multiplies generated source-flow plan volume. It does not scale road
capacity, simulation speed, dwell, or travel time.

For one source/flow/hour:

```text
expected plan starts =
    source daily production
  × flow share
  × hourly curve weight
  × interval fraction of one hour
  × scale
```

The result is seeded-stochastically rounded. One plan creates one vehicle, but a
plan can contain several legs. For example, `entry → landmark → exit` is one
plan and two legs. The field name `tripsPerPersonPerDay` must therefore be read
as a current plan-start proxy, not as a calibrated survey person-trip rate.

## Curated zones

The entry and exit intersections are deliberately directional.

| Zone | Dataset ids | Role |
|---|---|---|
| `north-east-entries` | `N5742669208` Nguyễn Du; `N9646671003` Tôn Đức Thắng | outgoing only |
| `south-west-entries` | `N1987339833` Cầu Khánh Hội; `N6786738899` Cách Mạng Tháng Tám | outgoing only |
| `north-east-exits` | `N9727223522` Nguyễn Hữu Cảnh; `N11283907795` Lê Duẩn | incoming only |
| `south-west-exits` | `N370821213` Cầu Khánh Hội; `N6786738898` Cách Mạng Tháng Tám | incoming only |

**VERIFIED FACT (regression-tested artifact).** Regression tests verify the
intended entry-to-opposite-exit paths, all entry-to-tourism paths and all
tourism-to-exit paths in the directed map.

`perimeter-gateways` contains eight bidirectionally connected internal graph
nodes around the study-window edge. They are a **SYNTHETIC ASSUMPTION** that
lets an outbound resident plan visibly reach the model boundary, dwell as a
proxy for activity outside the window, then return. They are not observed
screenline stations or an OD survey.

The `tourism-core` zone contains:

| Building id | Dataset name | Dataset type |
|---|---|---|
| `B39514795` | Chợ Bến Thành | `COMMERCIAL` |
| `B39514793` | Bưu điện Trung tâm Sài Gòn | `OTHER` |
| `B801950766` | Nhà thờ Đức Bà Sài Gòn | `OTHER` |
| `B801710792` | Nhà hát Thành phố | `OTHER` |
| `B802125674` | Trung tâm mua sắm Saigon Centre | `COMMERCIAL` |
| `B804073951` | Bitexco Financial Tower | `COMMERCIAL` |
| `B1217481835` | Vincom Center Đồng Khởi | `COMMERCIAL` |

The zone corrects for incomplete OSM building types: visitor destinations are
explicit landmarks rather than every `COMMERCIAL` or `HOSPITAL` building.
Selection of these seven POIs is still a **SYNTHETIC ASSUMPTION**, not a visitor
survey.

## Demand sources and flows

This section mixes research-informed proxies with explicit assumptions; the
classification column is part of the contract.

### Residential source

Origins are only the 147 mapped `RESIDENTIAL` buildings. The 885 `OTHER`
buildings are deliberately excluded rather than silently treated as homes.
Production uses footprint ground area, no storeys, synthetic occupancy factors,
and the HOUTRANS-informed 1.84 proxy.

All resident flows use the citywide registered-stock motorcycle/car ratio
`0.8955 / 0.1045`; this is a **RESEARCH-INFORMED PROXY**, not a traffic mix.

| Flow | Share | Time curve | Destination and dwell | Share/curve evidence |
|---|---:|---|---|---|
| `resident-work-internal` | 0.090298 | `COMMUTE_AM` | office/commercial, 10–11 h, return | HOUTRANS purpose × internal split; curve/dwell synthetic |
| `resident-work-outbound` | 0.257002 | `COMMUTE_AM` | perimeter gateway, 10–11 h, return | HOUTRANS purpose × outbound split; gateway/curve/dwell synthetic |
| `resident-school-internal` | 0.047460 | `SCHOOL_AM` | school, 5–15 min, return | HOUTRANS purpose × internal split; curve/dwell synthetic |
| `resident-school-outbound` | 0.065540 | `SCHOOL_AM` | perimeter gateway, 5–15 min, return | HOUTRANS purpose × outbound split; gateway/curve/dwell synthetic |
| `resident-private-internal` | 0.201320 | HOUTRANS digitized proxy | commercial/hospital/other, 0.5–3 h, return | HOUTRANS purpose × internal split and historical curve |
| `resident-private-outbound` | 0.301980 | HOUTRANS digitized proxy | perimeter gateway, 0.5–3 h, return | HOUTRANS purpose × outbound split; gateway/dwell synthetic |
| `resident-business-internal` | 0.010556 | HOUTRANS digitized proxy | office/commercial, 0.5–2 h, return | HOUTRANS purpose × internal split and historical curve |
| `resident-business-outbound` | 0.025844 | HOUTRANS digitized proxy | perimeter gateway, 0.5–2 h, return | HOUTRANS purpose × outbound split; gateway/dwell synthetic |

The shares total 1.0. Seeded stochastic rounding still applies to each
origin/flow/hour cell; the deterministic output table below, rather than a
hand-summed expectation, is the acceptance count.

### North-east and south-west cordons

Each cordon declares the same synthetic production of 18,000 vehicles/day at
`scale = 1.0`.

| Flow per cordon | Share | Motorcycle / car | Curve | Chain |
|---|---:|---:|---|---|
| through | 0.20 | 0.8955 / 0.1045 | HOUTRANS digitized proxy | entry → opposite exit |
| inbound work | 0.50 | 0.8955 / 0.1045 | `COMMUTE_AM` | entry → office/commercial, dwell 8–10 h → any exit |
| visitor | 0.30 | 0.8955 / 0.1045 | `VISITOR_DAYTIME` | entry → tourism core, dwell 1–3 h → any exit |

The common vehicle ratio is a **RESEARCH-INFORMED PROXY**. Cordon production,
20/50/30 shares, `COMMUTE_AM`/`VISITOR_DAYTIME`, dwell, and exact gateway/POI
allocation are **SYNTHETIC ASSUMPTIONS**. The HOUTRANS digitized curve supplies
only the through-flow time shape; its 0.20 volume share is not observed. The
combined 36,000 modeled cordon plan starts are a computational sample, not the
2017/2019 Thủ Thiêm count copied into two invented gates.

## Reachability and failure semantics

The generator caches directed topology reachability and evaluates the complete
remaining leg chain before sampling a destination. A closed plan must also have
a structural path back to its exact origin.

- Boundary origins require outgoing roads.
- Boundary destinations require incoming roads.
- Absolute sources sample only origins able to complete the flow.
- An absolute flow with no viable origin fails scenario construction with its
  source and flow ids.
- An infeasible population origin-flow pair is excluded before stochastic
  scheduling.

This is structural validation. Live closures, road capacity and later event
effects still belong to routing/retry behavior.

## Legacy road-state reset

**VERIFIED FACT (committed map).** The committed legacy map currently contains 14,115 persisted
road vehicle counts, but no matching vehicle objects. Those values previously
consumed capacity and affected routing as invisible traffic.

At scenario initialization and reset:

```text
currentVehicleCount = 0
vehicleDensity      = 0
congestionLevel     = 0
roadStatus          = OPEN for non-closed roads
```

Explicit `CLOSED` roads remain closed. The OSM generator now emits zero dynamic
traffic by default, so regenerated maps do not reintroduce this state.

## Deterministic acceptance snapshot

For the committed map, parameter catalog, scenario, algorithm and seed:

| Measure | VERIFIED FACT |
|---|---:|
| Generated plans scheduled | 39,434 |
| Generated vehicle legs | 71,267 |
| Structural generation failures | 0 |
| Generated motorcycles | 34,219 |
| Generated cars | 4,161 |
| First generated departure | less than 1 s after 06:00 |
| Plans spawned by the first interactive frame | 300 |
| First interactive clock | 06:05 |
| Scripted vehicles | 0 |

Purpose counts:

| Purpose | Plans |
|---|---:|
| `BUSINESS_INTERNAL` | 25 |
| `BUSINESS_OUTBOUND_PROXY` | 67 |
| `INBOUND_WORK` | 18,000 |
| `PRIVATE_INTERNAL` | 489 |
| `PRIVATE_OUTBOUND_PROXY` | 781 |
| `SCHOOL_INTERNAL_ESCORT` | 117 |
| `SCHOOL_OUTBOUND_ESCORT_PROXY` | 161 |
| `THROUGH` | 7,199 |
| `VISITOR` | 10,800 |
| `WORK_INTERNAL` | 246 |
| `WORK_OUTBOUND_PROXY` | 633 |

The generated vehicle-instance result is 86.8% motorcycles
(`34,219 / 39,434`), with 4,161 cars, 719 buses and 335 emergency vehicles. It
falls below the 89.6% stock proxy because buses and ambulances come from
dedicated transit and hospital flows instead of that ratio. It is
a seeded output of the citywide registered-stock proxy, not an observed traffic
mode share. There is no scripted traffic: the populated 06:05 state is produced
by the first 300 scheduled plans running through normal physics, and every
vehicle maps to one listed demand plan.

Regression tests intentionally pin these values. A map, parameter, curve,
selector, random-call-order, reachability or seed change may legitimately
change the snapshot; update the scenario note and tests together after
reviewing the cause.

## Limitations

This scenario does not model:

- the full former District 1 boundary;
- current calibrated hourly demand or an OD matrix;
- a representative OSM building census or storeys;
- walking, cycling, metro or scheduled public transport;
- vehicle occupancy or conversion from person trips to vehicle trips;
- parked-vehicle or household identity;
- behavioral mode choice;
- demand response to congestion;
- automatic stopping at the departure horizon.

It is suitable for demonstrating deterministic demand generation, multi-leg
vehicle lifecycle, directed routing, dwell and event interaction. It is not
suitable for policy, capacity or travel-demand forecasting.

## Sources

- Current two-level local-government structure and the end of district-level
  authorities on 1 July 2025:
  [Law No. 72/2025/QH15 on Organization of Local Government](https://congbao.hochiminhcity.gov.vn/cong-bao/van-ban/luat/so/luat-to-chuc-chinh-quyen-dia-phuong-nam-2025/ngay/16-06-2025/tai-ve/47680).
- Current ward context and the former District 1 provenance of Sài Gòn Ward,
  published 12 July 2025:
  [Ho Chi Minh City Media Center](https://ttbc-hcm.gov.vn/nhung-cong-trinh-mang-dau-an-the-ky-o-phuong-sai-gon-1019135.html).
- Historical former District 1 area and central role:
  [UBND Quận 1, “Tổng quan”](https://quan1.hochiminhcity.gov.vn/GIOI-THIEU/Tong-quan-dia-ly-lich-su/Tong-quan).
- Ho Chi Minh City registered vehicle stock, published 20 December 2024:
  [Ho Chi Minh City Media Center](https://ttbc-hcm.gov.vn/tphcm-phat-trien-giao-thong-xanh-huong-den-tin-chi-carbon-1017682.html).
- 2013 citywide person-trip modal shares (Table 4.3 in the JICA HOUTRANS
  follow-up report):
  [JICA, Data Collection Survey on Railways in Major Cities in Vietnam](https://openjicareport.jica.go.jp/pdf/12305124_01.pdf).
- Reproduction of the historical HOUTRANS trip-rate/purpose table (Table
  1.1.2):
  [JICA, Data Collection Survey on Traffic Conditions of Southern Roads and Bridges, Appendix 1](https://openjicareport.jica.go.jp/pdf/12305017_02.pdf).
- Historical former-District-1 internal/outbound table and digitized
  all-non-walking hourly figures, including Tables 2.1.24–2.1.25 for
  1.160/1.163 million generated/attracted daily person trips:
  [JICA, HOUTRANS Volume 2, Main Text](https://openjicareport.jica.go.jp/pdf/11764636_02.pdf).
- November 2019 classified Thủ Thiêm tunnel count, Table 2.11:
  [TCIP/World Bank, Ho Chi Minh City Green Transport Development Project ESIA](https://documents1.worldbank.org/curated/en/464371603972487255/pdf/%C4%90%C3%A1nh-Gi%C3%A1-T%C3%A1c-%C4%90%E1%BB%99ng-M%C3%B4i-Tr%C6%B0%E1%BB%9Dng-v%C3%A0-X%C3%A3-H%E1%BB%99i.pdf).
- Official 2017 Sài Gòn River tunnel daily motorcycle volume and managed peak
  windows:
  [Ho Chi Minh City Party Committee](https://hcmcpv.org.vn/tin-tuc/xu-ly-tinh-trang-un-tac-giao-thong-ham-song-sai-gon-1491840860).
- Peak-hour fallback of 10–15% of daily volume only when no study is available:
  [TCVN 13592:2022, Urban Roads — Design Requirements](https://sgtvt.hochiminhcity.gov.vn/Files/1032/01-TCVN%2013592_2022_%C4%90%C6%B0%E1%BB%9Dng%20%C4%91%C3%B4%20th%E1%BB%8B_Y%C3%AAu%20c%E1%BA%A7u%20thi%E1%BA%BFt%20k%E1%BA%BF%20%281%29.pdf).
- Named central corridors and event-specific alternatives, published
  23 March 2025:
  [Phòng Cảnh sát giao thông Thành phố Hồ Chí Minh](https://csgt-congan.hochiminhcity.gov.vn/wps/portal/Home/trang-chu/noi-dung-chi-tiet/tin-giao-thong/doi3_dieuchinhgtlehoitdtt2025).
- Official Traffic Police route-direction notice used as a directionality
  cross-check during the road audit:
  [Phòng Cảnh sát giao thông Thành phố Hồ Chí Minh](https://csgt-congan.hochiminhcity.gov.vn/wps/portal/Home/trang-chu/noi-dung-chi-tiet/tin-giao-thong/d3_36_28426).
- Road topology, `oneway`, lane, roundabout, and name tags audited through a
  fresh Overpass extract:
  [OpenStreetMap copyright and data licence](https://www.openstreetmap.org/copyright).
