package team.traffic.routing;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.*;

import java.util.*;

public class BfsPathFindingStrategy implements PathFindingStrategy {

    private static final String NAME = "BFS";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supports(RoutePreference preference) {
        return preference == RoutePreference.SHORTEST_DISTANCE;
    }

    @Override
    public RouteResult findRoute(RoadNetwork network, RouteRequest request) {
        long startTime = System.currentTimeMillis();
        int visitedNodes = 0;

        String startId = request.getStartIntersectionId();
        String destId = request.getDestinationIntersectionId();

        if (startId.equals(destId)) {
            Route route = new Route(Collections.emptyList(), 0, 0, 0);
            return new RouteResult(route, NAME, System.currentTimeMillis() - startTime, 0);
        }

        Map<String, String> parent = new HashMap<>();
        Map<String, String> parentRoad = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(startId);
        visited.add(startId);

        boolean found = false;

        while (!queue.isEmpty() && !found) {
            String currentId = queue.poll();
            visitedNodes++;

            List<Road> outgoing = network.findOutgoingRoads(currentId);
            if (outgoing == null) continue;

            for (Road road : outgoing) {
                if (!RouteBuilder.isRoadUsable(road)) continue;

                String neighborId = road.getEndIntersectionId();

                if (!visited.contains(neighborId)) {
                    visited.add(neighborId);
                    parent.put(neighborId, currentId);
                    parentRoad.put(neighborId, road.getId());

                    if (neighborId.equals(destId)) {
                        found = true;
                        break;
                    }
                    queue.add(neighborId);
                }
            }
        }

        if (!found) {
            throw new NoSuchElementException("No route found from " + startId + " to " + destId);
        }

        Route route = RouteBuilder.reconstructRoute(network, startId, destId, parent, parentRoad, RoutePreference.SHORTEST_DISTANCE);

        long elapsed = System.currentTimeMillis() - startTime;
        return new RouteResult(route, NAME, elapsed, visitedNodes);
    }
}
