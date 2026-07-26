# Live Demo Guide

1. Run `./run-demo.sh` or `run-demo.ps1` and open `http://localhost:7070`.
2. Open **Network editor** and inspect a road. Point out `physicalRoadId`, lane
   count, lane width, and the wider rendering of multi-lane roads.
3. Load the demo fleet and start playback. The 24-vehicle mix contains 10 cars,
   8 motorcycles, 4 buses, and 2 emergency vehicles. The wide Leaflet macro map
   shows aggregate road pressure, incidents, and bright type-coloured dots
   without loading vehicle sprite assets. Toggle **Vehicles** to isolate them.
4. Open **Fleet** and click **Spawn random test vehicle**. The control chooses a
   reachable boundary-to-boundary route and creates one real backend car or
   motorcycle through `POST /api/vehicles`. Confirm the returned vehicle id and
   origin → destination appear below the button, then watch the matching dot
   advance. If playback is paused, press **Start** or **Resume**. BUS and
   EMERGENCY remain available in the manual form because bus dwell and emergency
   priority can intentionally make a movement smoke test look stationary. A page
   reload reconnects to the server-owned `RUNNING` state and keeps boundary-fleet
   replacement active, rather than letting the fleet decay to waiting buses.
5. Drag the footer across recorded frames. Confirm the header changes to
   **HISTORY**, road/event metrics stay fixed while live playback continues in
   the background, then use **Live** to return to the current frame.
6. Use the frame API to inspect a bus stop at an intermediate junction. It remains physically in its
   lane for the capacity-based dwell time while traffic can use another lane.
7. Schedule an accident or breakdown with duration in seconds, road position,
   and an optional zero-based lane. Confirm that unaffected lanes remain usable.
8. At a degree-three-or-more junction, use `GET /api/simulation/signals` to show
   cyclic phases. A red approach remains on its incoming road at the stop line;
   an emergency vehicle receives preemption.
9. Pause playback, close a road on a planned route, and resume to demonstrate
   Observer-driven dynamic rerouting. Overlapping events can be resolved in any
   order without losing the remaining effect.
10. Query replay and diagnostics after several steps:

   ```text
   GET /api/simulation/trace/frames
   GET /api/simulation/trace/frames/at?elapsedSeconds=5
   GET /api/simulation/trace/flow
   GET /api/simulation/trace/fundamental
   GET /api/simulation/trace/roads/{roadId}/time-space
   ```

11. Zoom the map to lane level and press **Open physical view**. The junction is
    drawn as it is: each lane on its own centreline, buildings coloured by what
    they are for, stop lines, and a lamp beside each controlled approach that
    matches the signal the vehicles are obeying. Drag to move — the scene
    refetches around wherever you are looking, so the whole map is reachable
    without closing it — and scroll to zoom. A motorcycle riding between two
    lanes is drawn between them, with a short trail behind it.

    `?physical=<lat>,<lon>` on the URL opens it straight away, which is the
    quickest way into a specific junction during a demo.

12. Let a vehicle arrive. It remains in the frame state briefly, then disappears
    while the completed-trip statistics stay available.
13. Compare routing algorithms and open Experiments for the deterministic
    scenarios and large-grid A* search reduction.

During questions, relate routing to Strategy, events to Observer, vehicles to
Factory/inheritance, `RoadLayout` and movement models to narrow interfaces, and
the plan/commit sub-step to deterministic order independence. `Motorcycle`
keeps its compact filtering behaviour in a dedicated `LaneChangeModel`; the
base `Vehicle` continues to use ordinary MOBIL.
