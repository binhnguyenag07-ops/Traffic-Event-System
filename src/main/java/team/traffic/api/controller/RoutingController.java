package team.traffic.api.controller;

import java.util.List;
import team.traffic.api.dto.ApiResponse;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.service.RoutingService;

public class RoutingController {
    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    public ApiResponse<RouteResult> calculateRoute(RouteRequest request) {
        return ApiResponse.success(routingService.calculateRoute(request));
    }

    public ApiResponse<RouteResult> recalculateRoute(String vehicleId) {
        return ApiResponse.success(routingService.recalculateRoute(vehicleId));
    }

    public ApiResponse<Void> switchStrategy(String strategyName) {
        routingService.switchStrategy(strategyName);
        return ApiResponse.success(null);
    }

    public ApiResponse<List<String>> getAvailableStrategies() {
        return ApiResponse.success(routingService.getAvailableStrategyNames());
    }
}
