package team.traffic.routing;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;

public class AStarPathFindingStrategy implements PathFindingStrategy {
    private Heuristic heuristic;

    public AStarPathFindingStrategy(Heuristic heuristic) {
        this.heuristic = heuristic;
    }

    @Override
    public String getName() {
        return "A_STAR";
    }

    @Override
    public boolean supports(String preference) {
        return true;
    }

    @Override
    public RouteResult findRoute(RoadNetwork network, RouteRequest request) {
        // TODO: implement A* using gScore, fScore, and heuristic estimate.
        throw new UnsupportedOperationException("TODO: implement A* pathfinding.");
    }

    public Heuristic getHeuristic() {
        return heuristic;
    }

    public void setHeuristic(Heuristic heuristic) {
        this.heuristic = heuristic;
    }
}
