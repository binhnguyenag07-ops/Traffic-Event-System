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
        return EdgeStateSearch.find(
                network, request, NAME, congestionThreshold,
                EdgeStateSearch.Mode.ASTAR, heuristic);
    }

    private double estimateCost(Intersection from, Intersection destination, double maximumSpeed) {
        if (from == null || destination == null) {
            return 0.0;
        }
        double estimatedDistance = Math.max(0.0, heuristic.estimate(from, destination));
        return estimatedDistance / maximumSpeed;
    }

}
