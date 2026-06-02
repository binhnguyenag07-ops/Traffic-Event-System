package hyo.traffic.service.impl;

import hyo.traffic.domain.network.RoadNetwork;
import hyo.traffic.domain.routing.RouteRequest;
import hyo.traffic.domain.routing.RouteResult;
import hyo.traffic.routing.PathFindingStrategy;
import hyo.traffic.routing.RoutingStrategyRegistry;
import hyo.traffic.service.RoutingService;

import java.util.List;

public class RoutingServiceImpl implements RoutingService {

    private final RoadNetwork roadNetwork;
    private final RoutingStrategyRegistry strategyRegistry;

    public RoutingServiceImpl(RoadNetwork roadNetwork, RoutingStrategyRegistry strategyRegistry) {
        this.roadNetwork = roadNetwork;
        this.strategyRegistry = strategyRegistry;
    }

    @Override
    public RouteResult calculateRoute(RouteRequest request) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public RouteResult recalculateRoute(String vehicleId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void registerStrategy(PathFindingStrategy strategy) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void switchStrategy(String strategyName) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public List<String> getAvailableStrategyNames() {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
