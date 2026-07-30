# Live Demo Guide

## Start from a clean build

From the repository root, run `./run-demo.sh` on Linux/macOS or
`.\run-demo.ps1` in PowerShell, then open `http://localhost:7070`.

The scripts run `npm ci`, build the Vite frontend, run a clean Maven package,
and launch the JAR. They stop at the first failed native command. The frontend
is embedded in the JAR, but the `data/` directory is a runtime input and must
remain available from the repository root. OpenStreetMap basemap tiles require
internet access; routing, demand, physics, and vehicle poses run locally.

The normal launch loads and initializes **Central District 1 - 24-hour Weekday
Proxy**. Its demand horizon starts at 06:00 with seed `20260728` and 39,434
deterministic demand plans / 71,267 vehicle legs. Initialization executes five
minutes of ordinary microscopic dynamics, so the first interactive frame is
06:05 with 300 due plans already spawned. It has no separately scripted
warm-start traffic. The scenario covers research-informed historical resident
internal/outbound proxies plus synthetic through, inbound-work, and visitor
cordon flows across the central study window.

The typical-day run begins with zero incidents and disables spontaneous
collision-event generation because it has no calibrated crash-frequency model.
Use the incident controls or the **Crashed vehicles must stay put** lab when
demonstrating accidents; ordinary car following and collision-safe spacing
remain active in the demand run.

The runtime is an approximately 1:9 microscopic agent sample against the
documented 0.6-million-leg/day historical midpoint, not an observed or
calibrated current District 1 count. Use the
[scenario evidence note](DISTRICT1_SCENARIO.md) if the audience asks which
claims are verified facts, research-informed proxies, or synthetic assumptions.

For a pre-demo check:

```text
GET /api/health
GET /api/scenarios/active
GET /api/simulation/status
GET /api/simulation/snapshot
```

Immediately after backend startup, status is `READY`; opening the dashboard
starts playback. If it remains `READY`, press **Start**. To restore the exact
seeded run, pause playback and press
**Reload scenario**, or call:

```text
POST /api/scenarios/district-1/activate
```

Reloading creates a new timeline and clears the old traces and completed-trip
history.

The 24-hour scenario initially selects **24 h overview · 120 sim s/s** on the
macro dashboard, so the complete demand day takes about 12 wall minutes.
Select **Balanced · 10 sim s/s** for a slower aggregate walkthrough. Opening
Physical View switches to **Live camera · 1 sim s/s**: four 0.25-second
authoritative ticks per wall second. It does not replay the day, seek the clock,
or run close-up turns with 30-second overview ticks. Closing the camera restores
the previous macro pace.

## Recommended walkthrough

1. Start on the React-Leaflet macro map. Show directed roads, pressure,
   incidents, routes, and the type-coloured vehicle dots. Explain that the
   backend resolves every position and heading; Leaflet only draws the received
   frame.

2. Open **Network editor** and inspect a road. Point out `physicalRoadId`, lane
   count, lane width, stored polyline geometry, and the wider rendering of
   multi-lane roads. Two directions of one street are separate directed roads
   sharing one physical-road id. The selector shows street names, endpoints,
   direction, lane count, and length in metres; the ids remain traceability
   fields, not something the presenter must memorize.

3. Read the top-bar 24-hour clock, then open **24-hour demand**. Show the
   read-only departure histogram and expand one of the 12 server-paged plans to
   see its named origin, destination, dwell, and estimated return. Explain that
   the histogram aggregates all 39,434 departures and is not a time selector;
   every plan spawns automatically when the backend clock reaches it.
   Arrival/return clocks are directed free-flow estimates and live
   queues/signals decide actual arrival. Generic or repeated place names include
   six-decimal coordinates so two households sharing the same label are still
   distinguishable without exposing raw ids.

4. Open the physical view near a junction. The control locks to
   **Live camera · 1 sim s/s** and shows the authoritative 24-hour clock. Watch
   a vehicle turn through several 0.25-second frames. The backend frame carries
   `inJunction`, incoming/outgoing road ids, position, and heading. The PixiJS
   renderer follows the lane connector curve, so a vehicle should not spin,
   cut a straight chord, or choose its own direction at the intersection.

5. Pause and press **Step** a few times if the audience wants to inspect that
   junction movement slowly. Manual step returns `409` while playback is
   running, so pause first. Resume afterwards.

6. Drag the footer across recorded frames. Confirm the header changes to
   **HISTORY**, road/event metrics stay fixed while live playback continues in
   the background, and **Live** returns to the current frame. This demonstrates
   that replay uses retained immutable `SimulationFrame` values rather than
   reconstructing mutable domain state.

7. Inspect `GET /api/simulation/status` and the Performance panel. Relate
   `scheduledTrips`, `spawnedTrips`, `pendingTrips`, and `failedPlanIds` to the
   seeded demand. The 06:05 starting fleet came from the first 300 due plans,
   not an untraceable warm-start list. Every vehicle can be matched to one plan
   through the paged `GET /api/simulation/demand/schedule`.

8. Open **Fleet** and click **Spawn random test vehicle**. The control submits a
   real `POST /api/vehicles` request for a reachable building-to-building car or
   motorcycle, preferring a residential origin. Confirm the returned
   human-readable building/street labels, then watch the vehicle leave the
   building, traverse its mapped access link, join a road, and appear in both
   renderers. The manual form also selects named buildings rather than opaque
   ids; BUS and EMERGENCY remain available.

9. Schedule an accident or breakdown with duration in seconds, a road position,
   and an optional zero-based lane. Confirm that unaffected lanes remain usable.
   Create two events on one road if time permits, then resolve them in either
   order: `TrafficEventCoordinator` rebuilds the remaining overlays from the
   baseline.

10. Pause playback, close a road used by a planned route, and resume. Adaptive
   rerouting is driven by `AdaptiveNavigationCoordinator`, not by the legacy
   event listener. A vehicle may wait for its information latency, strategic
   cadence, saving threshold, or cooldown instead of rerouting immediately;
   use `GET /api/simulation/diagnostics` to explain the accepted or rejected
   decision.

11. At a degree-three-or-more junction, use
    `GET /api/simulation/signals` to show cyclic phases. A red approach remains
    on its incoming road at the stop line; an emergency vehicle added through
    the manual fleet form can receive preemption. Signals in the physical view
    come from the same authoritative frame the vehicles obey.

12. Add a BUS through the manual fleet form, then inspect it at an intermediate
    route junction. Its capacity-derived dwell occurs at the stop line while it
    remains a lane occupant, so another lane can continue to move.

13. Query replay and diagnostics after several steps:

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

14. In the physical view, pan and zoom across the already loaded full-network
    geometry. Static lanes, junctions, buildings, and land cover load once per
    timeline; later vehicle/signal requests follow the camera radius with a 20%
    prefetch ring, and Pixi submits only sprites intersecting the viewport.
    Client interpolation smooths two backend poses for display and never
    changes physics. A filtering motorcycle can be drawn between lane centres,
    with a short trail behind it. Closing the physical view returns the macro
    map to the same camera area and restores the former overview pace.

    Add `?physical=<lat>,<lon>` to the URL to open directly at a known junction.

15. Let a vehicle arrive. It follows the authoritative destination access link
    into the building, remains in frame state briefly, then disappears
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
