package team.traffic.service;

import java.util.List;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.routing.PathFindingStrategy;

public interface RoutingService {
    RouteResult calculateRoute(RouteRequest request);

    RouteResult recalculateRoute(String vehicleId);

    void registerStrategy(PathFindingStrategy strategy);

    void switchStrategy(String strategyName);

    List<String> getAvailableStrategyNames();
}
