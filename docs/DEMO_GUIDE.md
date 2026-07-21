# Live Demo Guide

1. Run `./run-demo.sh` or `run-demo.ps1` and open `http://localhost:7070`.
2. Show that the OpenStreetMap view is constrained to the central HCMC area.
3. Load the demo fleet, start playback, and point out moving vehicles, density,
   congestion colours, and trip statistics. Demo vehicles make one cross-district
   trip, leave at a boundary junction, and are replaced after 1–3 seconds by a
   different same-type vehicle entering from another boundary. Confirm that the
   cumulative arrived count and trip metrics remain after the old marker leaves.
4. Schedule a signal-delay event and inspect a vehicle in `WAITING` state with
   remaining wait ticks.
5. Pause playback, close a road on a planned route, and resume to demonstrate
   dynamic rerouting.
6. Add and remove a junction and a two-way road, update a condition, and search
   incoming/outgoing connections.
7. Import a valid geographic JSON network, then export it again.
8. Compare routing algorithms and open Experiments to show all four required
   scenarios and the large-grid A* search reduction.

During questions, relate the demo to Strategy (routing), Observer (events),
Factory (vehicles), encapsulated domain state, and service/controller APIs.
