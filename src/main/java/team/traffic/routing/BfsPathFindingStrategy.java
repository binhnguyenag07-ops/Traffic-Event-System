package team.traffic.routing;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;

public class BfsPathFindingStrategy implements PathFindingStrategy {
    @Override
    public String getName() {
        return "BFS";
    }

    @Override
    public boolean supports(String preference) {
        return RoutePreference.SHORTEST_DISTANCE.equals(preference);
    }

    @Override
    public RouteResult findRoute(RoadNetwork network, RouteRequest request) {
        // TODO: implement unweighted BFS using queue, visited set, and parent map.
        throw new UnsupportedOperationException("TODO: implement BFS pathfinding.");
    }
}
