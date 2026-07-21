# Routing Algorithms

## Shared graph model

The road network is a directed adjacency graph. A road is excluded when it is
closed, blocked, full, or above the configured avoidance threshold. Depending
on the route preference, its edge weight is one hop, distance in kilometres,
free-flow time in hours, or congestion-adjusted time.

## BFS

Breadth-First Search is the unweighted baseline. It visits intersections in
layers and returns the route with the fewest directed roads. Its complexity is
`O(V + E)`.

## Dijkstra

Dijkstra uses a priority queue and non-negative road weights. It is the
optimal baseline for shortest distance, shortest time, and congestion-aware
routing. Its queue implementation runs in `O((V + E) log V)`.

## A*

A* supports time and congestion-aware routes. Its heuristic is straight-line
distance divided by the maximum network speed, converting kilometres to hours.
Because congestion can only increase cost, this remains admissible. Immutable
priority-queue entries and stale-entry checks ensure improved scores are
reordered correctly.

## Dynamic routing

The Observer pattern publishes active congestion, accident, closure, and
signal events. Vehicles whose remaining route contains the affected road
request another route, while a vehicle already on that road completes its
current segment. A full or unavailable next road also triggers recomputation
on the next tick. Each successful replacement is included in algorithm and
vehicle statistics.

Path optimality is `oracle cost / returned cost × 100`, capped at 100. Dijkstra
is the weighted oracle; BFS is the minimum-hop oracle.
