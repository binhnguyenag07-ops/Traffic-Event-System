# Synthetic weekday in the former District 1 study window

This is the evidence and reproducibility note for
`data/scenarios/district1_typical_day.json`.

The scenario is a deterministic educational workload for one **central study
window in the former District 1 area**. It is not a calibrated demand model, an
observed typical day or a forecast. “Typical” remains in the historical
filename for compatibility; the reader-facing scenario name is:

```text
Central District 1 - Synthetic Weekday
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
| **DATASET FACT** | Directly measured from the committed map/scenario or produced by its deterministic regression test. It is a fact about this artifact, not automatically about the former District 1 area. |
| **CITY-LEVEL PROXY** | Ho Chi Minh City-wide or historical transport evidence used only to motivate a plausible scenario shape. It is not a study-window count, OD matrix or modal calibration. |
| **SYNTHETIC ASSUMPTION** | An authored input without matching local observational evidence in this project. |

The former District 1 area and role cited below are external scope/context
references, not demand calibration inputs.

## Geographic scope

**DATASET FACT.** `tools/generate_osm_map.py` uses:

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

**DATASET FACT.** The OSM snapshot date is not embedded, and 76.2% of mapped
buildings are `OTHER`. Population and attraction derived from these footprints
are therefore relative model inputs, not census estimates.

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

**CITY-LEVEL PROXY.** The Ho Chi Minh City Media Center reported in December
2024 that the city managed more than 9.4 million vehicles, including about
980,000 cars and more than 8.4 million motorcycles. Among only those two
registered-stock categories, motorcycles are approximately 89.6%.

That is a stock ratio for the whole city, not:

- traffic flow through this study window;
- a trip-mode share;
- a classified count for this study window;
- a reason to set any exact scenario mix.

**CITY-LEVEL PROXY.** A JBIC/JICA-hosted 1999 research paper described
motorcycle-and-bicycle modal share above 90% in Hanoi and Ho Chi Minh City. It
is historical, combines bicycles with motorcycles and predates current
transport conditions. It supports only the broad statement that two-wheel
travel has long been important; it cannot calibrate a 2026 scenario.

**SYNTHETIC ASSUMPTION.** Every exact `vehicleMix` in the bundled scenario,
including 65%, 80%, 85% and 90% motorcycle shares, remains authored demo input.

### Volumes and time profiles

No official open hourly cordon count or local household-trip table for this
former District 1 study window is included in this repository.

The following are therefore all **SYNTHETIC ASSUMPTIONS**:

- 1.8 `tripsPerPersonPerDay` for the resident/mixed-use source;
- 3600 `vehiclesPerDay` at each of two cordons;
- source flow shares;
- `scale = 0.015`;
- all four named hourly curves;
- building occupancy and attraction parameters;
- gravity decay `β = 0.35`;
- dwell ranges;
- the seven-landmark visitor zone;
- the four warm-start vehicles.

The top-level `status: "ASSUMPTION"` in
`data/demand/default_assumptions.json` is deliberate.

## Scenario clock and sampling

| Field | Value | Classification |
|---|---:|---|
| `startTimeOfDay` | 06:00 | SYNTHETIC ASSUMPTION |
| `durationHours` | 18 | SYNTHETIC ASSUMPTION |
| `seed` | 20260728 | reproducibility control |
| `scale` | 0.015 | SYNTHETIC ASSUMPTION |

`durationHours` is the **departure horizon**, 06:00 to midnight. It does not
auto-stop the simulation. Vehicles may still be travelling or dwelling after
the horizon.

`scale` multiplies generated source-flow plan volume. It does not scale road
capacity, simulation speed, dwell, travel time or the scripted warm-start
fleet.

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

**DATASET FACT.** Regression tests verify the intended entry-to-opposite-exit
paths, all entry-to-tourism paths and all tourism-to-exit paths in the directed
map.

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

All values in this section are **SYNTHETIC ASSUMPTIONS**.

### Resident and mixed-use source

Origins are mapped `RESIDENTIAL` and `OTHER` buildings. Production uses
footprint ground area, not storeys.

| Flow | Share | Motorcycle / car | Curve | Destination and dwell | Completion |
|---|---:|---:|---|---|---|
| `resident-commute` | 0.40 | 0.85 / 0.15 | `COMMUTE_AM` | office/commercial, 8–10 h | return to origin |
| `resident-school-dropoff` | 0.08 | 0.90 / 0.10 | `SCHOOL_AM` | school, 5–15 min | return to origin |
| `resident-shopping` | 0.12 | 0.90 / 0.10 | `MIDDAY` | commercial, 20–60 min | return to origin |
| `resident-medical` | 0.05 | 0.80 / 0.20 | `DAYTIME` | hospital, 30–120 min | return to origin |

The shares total 0.65; 0.35 of production is intentionally unallocated.

### North-east and south-west cordons

Each cordon declares the same synthetic base production of 3600 vehicles/day
before `scale`.

| Flow per cordon | Share | Motorcycle / car | Curve | Chain |
|---|---:|---:|---|---|
| through | 0.45 | 0.80 / 0.20 | `DOUBLE_PEAK` | entry → opposite exit |
| inbound work | 0.35 | 0.80 / 0.20 | `COMMUTE_AM` | entry → office/commercial, dwell 8–10 h → any exit |
| visitor | 0.20 | 0.65 / 0.35 | `VISITOR_DAYTIME` | entry → tourism core, dwell 1–3 h → any exit |

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

**DATASET FACT.** The committed legacy map currently contains 14,117 persisted
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

| Measure | DATASET FACT |
|---|---:|
| Generated plans scheduled | 216 |
| Structural generation failures | 0 |
| Generated motorcycles | 184 |
| Generated cars | 32 |
| First generated departure | 272.10 s |
| Scripted warm-start vehicles at `t = 0` | 4 |

Purpose counts:

| Purpose | Plans |
|---|---:|
| `COMMUTE` | 74 |
| `INBOUND_WORK` | 37 |
| `MEDICAL` | 5 |
| `SCHOOL_DROPOFF` | 10 |
| `SHOPPING` | 20 |
| `THROUGH` | 48 |
| `VISITOR` | 22 |

The generated modal result is 85.2% motorcycles (`184 / 216`). It is a seeded
output of synthetic mixes, not an observed mode share.

The four scripted vehicles are present immediately:

| Vehicle | Type | Route |
|---|---|---|
| `warm-cau-khanh-hoi-to-ben-thanh` | motorcycle | Cầu Khánh Hội → Chợ Bến Thành |
| `warm-cach-mang-thang-8-to-bitexco` | motorcycle | Cách Mạng Tháng Tám → Bitexco |
| `warm-nguyen-du-to-saigon-centre` | car | Nguyễn Du → Saigon Centre |
| `warm-ton-duc-thang-to-opera-house` | motorcycle | Tôn Đức Thắng → Nhà hát Thành phố |

They are a presentation aid and are excluded from the 216 generated plans and
184/32 split.

Regression tests intentionally pin these values. A map, parameter, curve,
selector, random-call-order, reachability or seed change may legitimately
change the snapshot; update the scenario note and tests together after
reviewing the cause.

## Limitations

This scenario does not model:

- the full former District 1 boundary;
- observed hourly demand or an OD matrix;
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
- Named central corridors and event-specific alternatives, published
  23 March 2025:
  [Phòng Cảnh sát giao thông Thành phố Hồ Chí Minh](https://csgt-congan.hochiminhcity.gov.vn/wps/portal/Home/trang-chu/noi-dung-chi-tiet/tin-giao-thong/doi3_dieuchinhgtlehoitdtt2025).
- Historical two-wheel/public-transport context, published December 1999:
  [JBIC Research Paper No. 4, hosted by JICA](https://www.jica.go.jp/jica-ri/IFIC_and_JBICI-Studies/jica-ri/publication/archives/jbic/report/paper/pdf/rp04_e.pdf).
