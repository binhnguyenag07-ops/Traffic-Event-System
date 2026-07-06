package team.traffic.routing;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.*;

import java.util.*;

public class AStarPathFindingStrategy implements PathFindingStrategy {

    private static final String NAME = "A*";
    private final Heuristic heuristic;
    private final double congestionThreshold;

    public AStarPathFindingStrategy(Heuristic heuristic) {
        this(heuristic, RouteBuilder.DEFAULT_CONGESTION_THRESHOLD);
    }

    public AStarPathFindingStrategy(Heuristic heuristic, double congestionThreshold) {
        this.heuristic = heuristic;
        this.congestionThreshold = congestionThreshold;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supports(RoutePreference preference) {
        return preference == RoutePreference.SHORTEST_TIME || preference == RoutePreference.CONGESTION_AWARE;
    }

    @Override
    public RouteResult findRoute(RoadNetwork network, RouteRequest request) {
        long startTime = System.currentTimeMillis();
        int visitedNodes = 0;

        String startId = request.getStartIntersectionId();
        String destId = request.getDestinationIntersectionId();
        RoutePreference preference = request.getPreference();

        if (startId.equals(destId)) {
            Route route = new Route(Collections.emptyList(), 0, 0, 0);
            return new RouteResult(route, NAME, System.currentTimeMillis() - startTime, 0);
        }

        Intersection destIntersection = network.getIntersections().get(destId);
        if (destIntersection == null) {
            throw new NoSuchElementException("Destination intersection not found: " + destId);
        }

        Map<String, Double> gScore = new HashMap<>();
        Map<String, Double> fScore = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        Map<String, String> prevRoad = new HashMap<>();
        PriorityQueue<String> openSet = new PriorityQueue<>(Comparator.comparingDouble(fScore::get));

        gScore.put(startId, 0.0);

        Intersection startIntersection = network.getIntersections().get(startId);
        double h = (startIntersection != null) ? heuristic.estimate(startIntersection, destIntersection) : 0;
        fScore.put(startId, h);

        openSet.add(startId);

        while (!openSet.isEmpty()) {
            String currentId = openSet.poll();
            visitedNodes++;

            if (currentId.equals(destId)) {
                break;
            }

            List<Road> outgoing = network.findOutgoingRoads(currentId);
            if (outgoing == null) continue;

            for (Road road : outgoing) {
                if (RouteBuilder.isRoadAvoidable(road, congestionThreshold)) continue;

                String neighborId = road.getEndIntersectionId();
                double edgeWeight = RouteBuilder.calculateEdgeWeight(road, preference);
                double tentativeG = gScore.getOrDefault(currentId, Double.MAX_VALUE) + edgeWeight;

                if (tentativeG < gScore.getOrDefault(neighborId, Double.MAX_VALUE)) {
                    prev.put(neighborId, currentId);
                    prevRoad.put(neighborId, road.getId());
                    gScore.put(neighborId, tentativeG);

                    Intersection neighborIntersection = network.getIntersections().get(neighborId);
                    double heuristicValue = (neighborIntersection != null)
                            ? heuristic.estimate(neighborIntersection, destIntersection)
                            : 0;
                    fScore.put(neighborId, tentativeG + heuristicValue);

                    if (!openSet.contains(neighborId)) {
                        openSet.add(neighborId);
                    }
                }
            }
        }

        if (!prev.containsKey(destId)) {
            throw new NoSuchElementException("No route found from " + startId + " to " + destId);
        }

        Route route = RouteBuilder.reconstructRoute(network, startId, destId, prev, prevRoad, preference);

        long elapsed = System.currentTimeMillis() - startTime;
        return new RouteResult(route, NAME, elapsed, visitedNodes);
    }
}
