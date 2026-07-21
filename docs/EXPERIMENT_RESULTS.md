# Experiment Results

The deterministic runner uses seed `42` and 30 origin/destination requests per
scenario. Run it again with:

```bash
mvn -q exec:java -Dexec.mainClass=team.traffic.experiment.ExperimentRunner
```

Add `-Dexec.args=--write` to refresh the JSON baseline and this report. Timing
depends on the computer; route pairs, events, topology, and success results are
reproducible. BFS optimizes hop count, while Dijkstra and A* optimize time.

| Scenario | Algorithm | Success | Avg ms | Avg visited | Optimality |
|---|---|---:|---:|---:|---:|
| Normal traffic | BFS | 100% | 0.0714 | 2.0 | 100% |
| Normal traffic | Dijkstra | 100% | 0.0881 | 3.5 | 100% |
| Normal traffic | A* | 100% | 0.1147 | 3.4 | 100% |
| Heavy congestion | BFS | 100% | 0.0206 | 2.9 | 100% |
| Heavy congestion | Dijkstra | 100% | 0.0216 | 3.6 | 100% |
| Heavy congestion | A* | 100% | 0.0366 | 3.8 | 100% |
| Seeded random events | BFS | 100% | 0.0090 | 1.7 | 100% |
| Seeded random events | Dijkstra | 100% | 0.0108 | 3.2 | 100% |
| Seeded random events | A* | 100% | 0.0240 | 3.5 | 100% |
| 20×20 grid | BFS | 100% | 0.3207 | 148.7 | 100% |
| 20×20 grid | Dijkstra | 100% | 0.2009 | 164.7 | 100% |
| 20×20 grid | A* | 100% | 0.1979 | 45.0 | 100% |

On the large grid, A* visited about 73% fewer intersections than Dijkstra while
returning the same optimal cost. Small graphs are too small for the heuristic
to offset its bookkeeping overhead consistently.
