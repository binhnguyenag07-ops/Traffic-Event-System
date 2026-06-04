package team.traffic.service.impl;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.routing.PathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.RoutingService;

public class RoutingServiceImpl implements RoutingService {
    private final RoutingStrategyRegistry strategyRegistry = new RoutingStrategyRegistry();
    private final RoadNetworkService roadNetworkService;
    private String activeStrategyName;

    public RoutingServiceImpl(RoadNetworkService roadNetworkService) {
        this.roadNetworkService = roadNetworkService;
    }

    @Override
    public RouteResult calculateRoute(RouteRequest request) {
        // TODO: validate request and measure computation time.
        PathFindingStrategy strategy = resolveStrategy(request);
        if (strategy == null) {
            throw new IllegalStateException("No routing strategy registered.");
        }
        return strategy.findRoute(roadNetworkService.getRoadNetwork(), request);
    }

    @Override
    public RouteResult recalculateRoute(String vehicleId) {
        // TODO: build RouteRequest from vehicle current position and destination.
        throw new UnsupportedOperationException("TODO: implement route recalculation.");
    }

    @Override
    public void registerStrategy(PathFindingStrategy strategy) {
        strategyRegistry.register(strategy);
    }

    @Override
    public void switchStrategy(String strategyName) {
        this.activeStrategyName = strategyName;
    }

    @Override
    public List<String> getAvailableStrategyNames() {
        List<String> names = new ArrayList<>();
        for (PathFindingStrategy strategy : strategyRegistry.getStrategies()) {
            names.add(strategy.getName());
        }
        return names;
    }

    private PathFindingStrategy resolveStrategy(RouteRequest request) {
        if (request.getStrategyName() != null) {
            return strategyRegistry.findByName(request.getStrategyName());
        }
        if (activeStrategyName != null) {
            return strategyRegistry.findByName(activeStrategyName);
        }
        return strategyRegistry.findDefaultFor(request.getPreference());
    }
}
