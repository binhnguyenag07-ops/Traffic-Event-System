# API Contract

All endpoints use JSON unless noted. Errors use `{"error":"message"}`.

## Network and scenarios

| Method | Endpoint | Result |
|---|---|---|
| GET | `/api/network/intersections` | Geographic intersections |
| GET | `/api/network/roads` | Directed roads, conditions, density, geometry |
| POST | `/api/network/intersections` | Add a junction while playback is not running |
| POST | `/api/network/roads` | Add one directed road or a two-road bidirectional pair |
| PUT | `/api/network/roads/{id}/condition` | Change status and congestion |
| GET | `/api/network/intersections/{id}/roads` | Incoming and outgoing roads |
| GET | `/api/network/export` | Download current network JSON |
| GET | `/api/scenarios` | Bundled scenarios |
| POST | `/api/scenarios/{id}/activate` | Reset and activate a bundled scenario |
| POST | `/api/scenarios/import` | Multipart JSON import using field `map` |

Imports are limited to 2 MB, 600 intersections, and 1,200 roads. Every imported
intersection needs latitude/longitude inside the configured HCMC bounds. A
successful import clears simulation entities and returns `filename`, counts,
`geometrySources`, `bounds`, and `status`.

Road JSON and network API responses include `roadClass`: `PRIMARY`, `SECONDARY`,
`TERTIARY`, `UNCLASSIFIED`, `RESIDENTIAL`, or `LIVING_STREET`. The field is
optional on import and road creation; legacy payloads default to `UNCLASSIFIED`.

## Routing, simulation, and analysis

| Method | Endpoint | Result |
|---|---|---|
| POST | `/api/routes` | One BFS, Dijkstra, or A* route |
| POST | `/api/routes/compare` | All compatible algorithms |
| GET | `/api/routing/algorithms` | Algorithm names and preferences |
| POST | `/api/simulation/initialize` | Vehicles and scheduled events |
| POST | `/api/simulation/start` | Start continuous ticks |
| POST | `/api/simulation/pause` | Pause continuous ticks |
| POST | `/api/simulation/resume` | Resume continuous ticks |
| POST | `/api/simulation/tick` | One manual tick while not running |
| GET | `/api/simulation/stream` | Server-Sent Event snapshots |
| GET | `/api/statistics` | Traffic and algorithm metrics |
| GET | `/api/experiments/latest` | Checked-in experiment baseline |
| POST | `/api/experiments/run` | Fresh deterministic experiment run |

Vehicle snapshots include distance travelled, simulation travel time, waiting
ticks, and rerouting count. Traffic statistics include total/average travel
time, average completed route length, congestion, density, and state counts.
`arrivedVehicles` is the cumulative completed-trip count for the current run,
while `totalVehicles` is the number currently in the simulator. Completed-trip
metrics remain available after a vehicle is removed. Initialize, reset,
scenario activation, and map replacement clear the completed-trip history.
