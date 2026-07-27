package team.traffic.routing;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.*;

import java.util.*;

public class BfsPathFindingStrategy implements PathFindingStrategy {

    private static final String NAME = "BFS";
    private final double congestionThreshold;

    public BfsPathFindingStrategy() {
        this(RouteBuilder.DEFAULT_CONGESTION_THRESHOLD);
    }

    public BfsPathFindingStrategy(double congestionThreshold) {
        this.congestionThreshold = congestionThreshold;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supports(RoutePreference preference) {
        return preference == RoutePreference.MINIMUM_HOPS;
    }

    @Override
    public RouteResult findRoute(RoadNetwork network, RouteRequest request) {
        return EdgeStateSearch.find(
                network, request, NAME, congestionThreshold,
                EdgeStateSearch.Mode.BFS, null);
    }
}
