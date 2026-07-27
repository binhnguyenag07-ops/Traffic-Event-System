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
        return EdgeStateSearch.find(
                network, request, NAME, congestionThreshold,
                EdgeStateSearch.Mode.DIJKSTRA, null);
    }
}
