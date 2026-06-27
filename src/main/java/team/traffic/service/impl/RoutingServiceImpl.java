package team.traffic.service.impl;

import java.util.NoSuchElementException;
import java.util.Objects;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.routing.PathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.RoutingService;

public class RoutingServiceImpl implements RoutingService {

    private final RoutingStrategyRegistry registry;

    public RoutingServiceImpl(RoutingStrategyRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "RoutingStrategyRegistry must not be null.");
    }

    @Override
    public RouteResult findRoute(RoadNetwork network, RouteRequest request) {
        Objects.requireNonNull(network, "RoadNetwork must not be null.");
        Objects.requireNonNull(request, "RouteRequest must not be null.");

        if (!network.containsIntersection(request.getStartIntersectionId())) {
            throw new IllegalArgumentException(
                    "Start intersection not found: " + request.getStartIntersectionId());
        }
        if (!network.containsIntersection(request.getDestinationIntersectionId())) {
            throw new IllegalArgumentException(
                    "Destination intersection not found: " + request.getDestinationIntersectionId());
        }

        RoutePreference preference = request.getPreference();
        PathFindingStrategy strategy = registry.findForPreference(preference)
                .orElseThrow(() -> new NoSuchElementException(
                        "No routing strategy found for preference: " + preference));

        return strategy.findRoute(network, request);
    }
}
