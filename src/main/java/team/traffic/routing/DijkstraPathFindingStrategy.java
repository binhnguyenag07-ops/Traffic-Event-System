package team.traffic.routing;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;

public class DijkstraPathFindingStrategy implements PathFindingStrategy {
    @Override
    public String getName() {
        return "DIJKSTRA";
    }

    @Override
    public boolean supports(String preference) {
        return true;
    }

    @Override
    public RouteResult findRoute(RoadNetwork network, RouteRequest request) {
        // TODO: implement weighted shortest path using road travel cost.
        throw new UnsupportedOperationException("TODO: implement Dijkstra pathfinding.");
    }
}
