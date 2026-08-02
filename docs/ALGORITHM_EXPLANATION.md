# Routing Algorithms

## Shared movement graph

The road network is directed, but search state is not only an intersection id.
`EdgeStateSearch` uses `(incoming road, junction)` so every expansion can ask the
authoritative `JunctionMovementGraph` which outgoing movements are legal for the
request's vehicle type. The same movement graph is used by routing, physical
junction arbitration and connector rendering, including explicit or
conservatively inferred U-turns.

Road legality and temporary admission are deliberately different:

- a missing, non-routable, `BLOCKED` or `CLOSED` road is excluded;
- a road above the configured congestion threshold is avoided on the first
  search;
- if that preference disconnects every otherwise legal path, search retries at
  threshold `1.0` and may return a route through a saturated queue;
- current lane/road capacity never deletes a legal edge. Microscopic dynamics
  holds the vehicle at the stop line or road boundary until admission is safe.

This fallback prevents transient fullness from turning ordinary queueing into a
false “no route” result and route-recalculation loop.

## Objectives and units

| Preference | Strategy support | Road component |
|---|---|---|
| `MINIMUM_HOPS` | BFS | one cost unit per legal road movement |
| `SHORTEST_DISTANCE` | Dijkstra | road distance in kilometres |
| `SHORTEST_TIME` | Dijkstra, A* | free-flow road time |
| `CONGESTION_AWARE` | Dijkstra, A* | congestion-adjusted travel time |

Non-BFS edge-state searches also add a non-negative junction-movement penalty.
Time objectives convert that penalty to the same time unit as their road cost.
The current `SHORTEST_DISTANCE` implementation adds
`movementPenaltySeconds / 3600` numerically to kilometres as a small generalized
turn cost. It therefore prefers short routes with fewer/highly penalized turns,
not a mathematically pure sum of geometric distance. That mixed-unit behavior is
documented here rather than being mislabeled as pure shortest distance; changing
it requires an explicit routing-contract decision and new optimality baselines.

## BFS

Breadth-First Search is the unit-weight baseline. It visits movement states in
cost layers and returns the route with the fewest legal directed road movements
inside the first-pass congestion filter (or its fallback graph). The current
shared `EdgeStateSearch` implementation uses the same priority queue as the
weighted strategies rather than a dedicated FIFO queue, so its actual bound is
`O((V + E) log V)` over movement states and legal movement edges. A conventional
FIFO BFS could reduce that implementation bound to `O(V + E)` without changing
the result.

## Dijkstra

Dijkstra uses a priority queue, immutable queue entries and stale-entry checks.
All supported edge costs are non-negative, so it is the weighted optimal
baseline for the exact cost function above. Its queue implementation runs in
`O((V + E) log V)` over movement states and legal movement edges.

## A*

A* supports `SHORTEST_TIME` and `CONGESTION_AWARE`. Its heuristic is
straight-line distance divided by maximum network speed. Congestion and movement
penalties are non-negative, so the heuristic does not overestimate the remaining
time cost. Immutable queue entries and stale-entry checks ensure an improved
score is reinserted with the correct priority.

## Adaptive routing

`TrafficEventBus` still publishes event transitions as an Observer extension
boundary, but production does not subscribe `DynamicReroutingListener` and no
synchronous listener mutates routes. `AdaptiveNavigationCoordinator` runs in the
ordered simulation loop and:

1. captures one immutable routing snapshot;
2. updates each driver's delayed, horizon-limited `DriverKnowledge`;
3. evaluates cadence, hard-block, congestion and cause-specific stall triggers;
4. applies savings thresholds, cooldown and retry backoff;
5. batches and caches route requests; and
6. atomically replaces only a validated route suffix after the vehicle's exact
   physical anchor.

A full next road normally causes admission waiting, not immediate whole-graph
recomputation. A vehicle already on a road is never teleported when its suffix
changes. U-turns are considered only when the movement graph and driver policy
allow them; emergency vehicles may use a lower U-turn cost but cannot drive
against a directed road or bypass a red signal.

Path optimality is `oracle cost / returned cost × 100`, capped at 100. Dijkstra
is the weighted oracle for its exact configured cost; BFS is the minimum-hop
oracle. Computation time is measured at runtime and is not deterministic across
machines even when seeded routes and their costs are reproducible.
