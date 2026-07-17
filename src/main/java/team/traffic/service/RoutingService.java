package team.traffic.service;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.routing.PathFindingStrategy;
import java.util.List;
import team.traffic.domain.statistics.AlgorithmStatistics;

public interface RoutingService {
    RouteResult findRoute(RoadNetwork network, RouteRequest request);

    default RouteResult findRoute(RoadNetwork network, RouteRequest request, String algorithm) {
        return findRoute(network, request);
    }

    default List<PathFindingStrategy> getStrategies() {
        return List.of();
    }

    default List<AlgorithmStatistics> getAlgorithmStatistics() {
        return List.of();
    }
}
