# Live Demo Guide

1. Run `./run-demo.sh` or `run-demo.ps1` and open `http://localhost:7070`.
2. Open **Network editor** and inspect a road. Point out `physicalRoadId`, lane
   count, lane width, and the wider rendering of multi-lane roads.
3. Load the demo fleet and start playback. Vehicle markers use backend-resolved
   latitude/longitude, heading, and lateral lane offset rather than all sharing
   the centreline.
4. Watch a bus stop at an intermediate junction. It remains physically in its
   lane for the capacity-based dwell time while traffic can use another lane.
5. Schedule an accident or breakdown with duration in seconds, road position,
   and an optional zero-based lane. Confirm that unaffected lanes remain usable.
6. At a degree-three-or-more junction, use `GET /api/simulation/signals` to show
   cyclic phases. A red approach remains on its incoming road at the stop line;
   an emergency vehicle receives preemption.
7. Pause playback, close a road on a planned route, and resume to demonstrate
   Observer-driven dynamic rerouting. Overlapping events can be resolved in any
   order without losing the remaining effect.
8. Query diagnostics after several steps:

   ```text
   GET /api/simulation/trace/flow
   GET /api/simulation/trace/fundamental
   GET /api/simulation/trace/roads/{roadId}/time-space
   ```

9. Let a vehicle arrive. Its marker remains briefly, then disappears while the
   completed-trip statistics stay available.
10. Compare routing algorithms and open Experiments for the deterministic
    scenarios and large-grid A* search reduction.

During questions, relate routing to Strategy, events to Observer, vehicles to
Factory/inheritance, `RoadLayout` and movement models to narrow interfaces, and
the plan/commit sub-step to deterministic order independence.
