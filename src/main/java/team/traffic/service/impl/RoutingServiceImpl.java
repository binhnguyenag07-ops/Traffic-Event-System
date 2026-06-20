package team.traffic.service.impl;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.routing.PathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.RoutingService;

public class RoutingServiceImpl implements RoutingService {
    private final RoutingStrategyRegistry registry;

    public RoutingServiceImpl(RoutingStrategyRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Routing strategy registry is required");
        }
        this.registry = registry;
    }

    @Override
    public RouteResult findRoute(RoadNetwork network, RouteRequest request) {
        if (network == null) {
            throw new IllegalArgumentException("Road network is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("Route request is required");
        }
        if (!network.containsIntersection(request.getStartIntersectionId())) {
            throw new IllegalArgumentException(
                    "Start intersection not found: " + request.getStartIntersectionId());
        }
        if (!network.containsIntersection(request.getDestinationIntersectionId())) {
            throw new IllegalArgumentException(
                    "Destination intersection not found: " + request.getDestinationIntersectionId());
        }

        PathFindingStrategy strategy = registry.findForPreference(request.getPreference())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No routing strategy available for preference: " + request.getPreference()));

        return strategy.findRoute(network, request);
    }
}
