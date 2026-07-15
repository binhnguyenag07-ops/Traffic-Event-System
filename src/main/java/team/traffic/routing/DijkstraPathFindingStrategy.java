package team.traffic.routing;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.*;

import java.util.*;

public class DijkstraPathFindingStrategy implements PathFindingStrategy {

    private static final String NAME = "Dijkstra";
    private final double congestionThreshold;

    public DijkstraPathFindingStrategy() {
        this(RouteBuilder.DEFAULT_CONGESTION_THRESHOLD);
    }

    public DijkstraPathFindingStrategy(double congestionThreshold) {
        this.congestionThreshold = congestionThreshold;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supports(RoutePreference preference) {
        return preference == RoutePreference.SHORTEST_DISTANCE
                || preference == RoutePreference.SHORTEST_TIME
                || preference == RoutePreference.CONGESTION_AWARE;
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

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        Map<String, String> prevRoad = new HashMap<>();
        PriorityQueue<String> queue = new PriorityQueue<>(Comparator.comparingDouble(dist::get));

        dist.put(startId, 0.0);
        queue.add(startId);

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            double currentDist = dist.get(currentId);

            if (currentId.equals(destId)) break;

            if (visitedNodes > 0 && currentDist > dist.getOrDefault(destId, Double.MAX_VALUE)) {
                break;
            }

            visitedNodes++;

            List<Road> outgoing = network.findOutgoingRoads(currentId);
            if (outgoing == null) continue;

            for (Road road : outgoing) {
                if (RouteBuilder.isRoadAvoidable(road, congestionThreshold)) continue;

                String neighborId = road.getEndIntersectionId();
                double edgeWeight = RouteBuilder.calculateEdgeWeight(road, preference);
                double newDist = currentDist + edgeWeight;

                if (newDist < dist.getOrDefault(neighborId, Double.MAX_VALUE)) {
                    dist.put(neighborId, newDist);
                    prev.put(neighborId, currentId);
                    prevRoad.put(neighborId, road.getId());
                    queue.add(neighborId);
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
