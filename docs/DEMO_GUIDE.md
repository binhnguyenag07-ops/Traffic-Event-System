# Live Demo Guide

## Start from a clean build

From the repository root, run `./run-demo.sh` on Linux/macOS or
`.\run-demo.ps1` in PowerShell, then open `http://localhost:7070`.

The scripts run `npm ci`, build the Vite frontend, run a clean Maven package,
and launch the JAR. They stop at the first failed native command. The frontend
is embedded in the JAR, but the `data/` directory is a runtime input and must
remain available from the repository root. OpenStreetMap basemap tiles require
internet access; routing, demand, physics, and vehicle poses run locally.

The normal launch loads and initializes **Central District 1 - Synthetic
Weekday**. It starts at 06:00 with seed `20260728`, 216 deterministic demand
plans, and four scripted warm-start vehicles. The scenario covers resident
commute, school, shopping, medical, through, inbound-work, and visitor flows
across the central study window. These are explicit educational assumptions,
not observed or calibrated District 1 traffic counts. Use the
[scenario evidence note](DISTRICT1_SCENARIO.md) if the audience asks which
claims are dataset facts, city-level proxies, or synthetic assumptions.

For a pre-demo check:

```text
GET /api/health
GET /api/scenarios/active
GET /api/simulation/status
GET /api/simulation/snapshot
```

Immediately after backend startup, status is `READY`; opening the dashboard
starts playback when the warm-start fleet is present. If it remains `READY`,
press **Start**. To restore the exact seeded run, pause playback and press
**Reload scenario**, or call:

```text
POST /api/scenarios/district-1/activate
```

Reloading creates a new timeline and clears the old traces and completed-trip
history.

## Recommended walkthrough

1. Start on the React-Leaflet macro map. Show directed roads, pressure,
   incidents, routes, and the type-coloured vehicle dots. Explain that the
   backend resolves every position and heading; Leaflet only draws the received
   frame.

2. Open **Network editor** and inspect a road. Point out `physicalRoadId`, lane
   count, lane width, stored polyline geometry, and the wider rendering of
   multi-lane roads. Two directions of one street are separate directed roads
   sharing one physical-road id.

3. Open the physical view near a junction and watch a vehicle turn through
   several frames. The default logical step is 0.5 simulated seconds, and the
   backend frame carries `inJunction`, incoming/outgoing road ids, position, and
   heading. The PixiJS renderer follows the lane connector curve, so a vehicle
   should not spin, cut a straight chord, or choose its own direction at the
   intersection.

4. Pause and press **Step** a few times if the audience wants to inspect that
   junction movement slowly. Manual step returns `409` while playback is
   running, so pause first. Resume afterwards.

5. Drag the footer across recorded frames. Confirm the header changes to
   **HISTORY**, road/event metrics stay fixed while live playback continues in
   the background, and **Live** returns to the current frame. This demonstrates
   that replay uses retained immutable `SimulationFrame` values rather than
   reconstructing mutable domain state.

6. Inspect `GET /api/simulation/status` and the Performance panel. Relate
   `scheduledTrips`, `spawnedTrips`, `pendingTrips`, and `failedPlanIds` to the
   seeded demand. The four warm-start vehicles are scripted and therefore do
   not count as spawned demand trips.

7. Open **Fleet** and click **Spawn random test vehicle**. The control submits a
   real `POST /api/vehicles` request for a reachable boundary-to-boundary car or
   motorcycle. Confirm the returned id and origin → destination, then watch the
   same backend vehicle appear in both renderers. BUS and EMERGENCY remain
   available in the manual form.

8. Schedule an accident or breakdown with duration in seconds, a road position,
   and an optional zero-based lane. Confirm that unaffected lanes remain usable.
   Create two events on one road if time permits, then resolve them in either
   order: `TrafficEventCoordinator` rebuilds the remaining overlays from the
   baseline.

9. Pause playback, close a road used by a planned route, and resume. Adaptive
   rerouting is driven by `AdaptiveNavigationCoordinator`, not by the legacy
   event listener. A vehicle may wait for its information latency, strategic
   cadence, saving threshold, or cooldown instead of rerouting immediately;
   use `GET /api/simulation/diagnostics` to explain the accepted or rejected
   decision.

10. At a degree-three-or-more junction, use
    `GET /api/simulation/signals` to show cyclic phases. A red approach remains
    on its incoming road at the stop line; an emergency vehicle added through
    the manual fleet form can receive preemption. Signals in the physical view
    come from the same authoritative frame the vehicles obey.

11. Add a BUS through the manual fleet form, then inspect it at an intermediate
    route junction. Its capacity-derived dwell occurs at the stop line while it
    remains a lane occupant, so another lane can continue to move.

12. Query replay and diagnostics after several steps:

    ```text
    GET /api/simulation/trace
    GET /api/simulation/trace/frames
    GET /api/simulation/trace/frames/at?elapsedSeconds=5
    GET /api/simulation/trace/ticks/{tick}
    GET /api/simulation/trace/vehicles/{vehicleId}?fromTick=0&toTick=20
    GET /api/simulation/trace/flow
    GET /api/simulation/trace/fundamental
    GET /api/simulation/trace/roads/{roadId}/time-space
    ```

13. In the physical view, pan and zoom across the already loaded full-network
    geometry. Static lanes, junctions, buildings, and land cover load once per
    timeline; only vehicle and signal state is polled afterwards. Client
    interpolation smooths two backend poses for display and never changes
    physics. A filtering motorcycle can be drawn between lane centres, with a
    short trail behind it. Closing the physical view returns the macro map to
    the same camera area.

    Add `?physical=<lat>,<lon>` to the URL to open directly at a known junction.

14. Let a vehicle arrive. It remains in frame state briefly, then disappears
    while completed-trip statistics remain available. Finish by comparing
    routing algorithms and opening **Experiments** for deterministic scenarios
    and the large-grid A* search reduction.

## Troubleshooting during the presentation

- If the launcher exits, read the immediately preceding npm, Maven, or Java
  error; the scripts intentionally fail fast.
- Stop an existing demo process before rebuilding the shaded JAR. Do not run
  `mvn package` over the artifact a live JVM is still loading; stop it, build,
  then start the new JAR.
- If the basemap is blank but controls work, check internet access to the tile
  provider. The physical view still uses local network geometry.
- If vehicles stop and status changes to `PAUSED`, inspect the server for
  `Scheduled simulation tick failed`. The backend cancels future ticks and
  publishes the paused state instead of failing silently.
- If a manually started JAR cannot find a map or scenario, launch it from the
  repository root or set `SCENARIO_FILE` explicitly.
- If the frontend is built for a non-default API mount, set
  `VITE_API_BASE_URL` before `npm run build`; the default is `/api`.

During questions, relate BFS/Dijkstra/A* and vehicle behaviors to Strategy,
vehicle creation to Factory, and `Vehicle`/`TrafficEvent` hierarchies to
inheritance and polymorphism. Use `TrafficEventBus` and SSE snapshot listeners
as the Observer examples; do not describe current adaptive rerouting as
Observer-driven. The plan/commit dynamics step provides deterministic
order-independence, while immutable renderer frames keep both map modes on
backend-authoritative state.
