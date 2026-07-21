package team.traffic.api.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import team.traffic.api.dto.RouteRequestDto;
import team.traffic.api.dto.RouteResultDto;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.RoutingService;

import java.util.Map;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class RoutingController {
    private final RoutingService routingService;
    private final RoadNetworkService networkService;

    public RoutingController(RoutingService routingService, RoadNetworkService networkService) {
        this.routingService = routingService;
        this.networkService = networkService;
    }

    public void registerRoutes(Javalin app) {
        app.post("/api/routes", this::findRoute);
        app.get("/api/routing/algorithms", this::getAlgorithms);
        app.post("/api/routes/compare", this::compareRoutes);
    }

    private void findRoute(Context ctx) {
        RouteRequestDto dto;
        try {
            dto = ctx.bodyAsClass(RouteRequestDto.class);
        } catch (Exception e) {
            ctx.status(400).json(error("Invalid request body: " + e.getMessage()));
            return;
        }

        if (dto.startIntersectionId == null || dto.startIntersectionId.isBlank()) {
            ctx.status(400).json(error("startIntersectionId is required"));
            return;
        }
        if (dto.destinationIntersectionId == null || dto.destinationIntersectionId.isBlank()) {
            ctx.status(400).json(error("destinationIntersectionId is required"));
            return;
        }
        if (dto.preference == null || dto.preference.isBlank()) {
            ctx.status(400).json(error("preference is required (SHORTEST_DISTANCE, SHORTEST_TIME, CONGESTION_AWARE, MINIMUM_HOPS)"));
            return;
        }

        RoutePreference preference;
        try {
            preference = RoutePreference.valueOf(dto.preference.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(error("Invalid preference: " + dto.preference
                    + ". Valid values: SHORTEST_DISTANCE, SHORTEST_TIME, CONGESTION_AWARE, MINIMUM_HOPS"));
            return;
        }

        RoadNetwork network = networkService.getNetwork();

        if (!network.containsIntersection(dto.startIntersectionId)) {
            ctx.status(404).json(error("Start intersection not found: " + dto.startIntersectionId));
            return;
        }
        if (!network.containsIntersection(dto.destinationIntersectionId)) {
            ctx.status(404).json(error("Destination intersection not found: " + dto.destinationIntersectionId));
            return;
        }

        RouteRequest request = new RouteRequest(
                dto.startIntersectionId,
                dto.destinationIntersectionId,
                preference);

        try {
            RouteResult result = routingService.findRoute(network, request, dto.algorithm);
            ctx.status(200).json(toDto(result));
        } catch (NoSuchElementException e) {
            ctx.status(404).json(error("No route found: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(error("Internal server error: " + e.getMessage()));
        }
    }

    private void getAlgorithms(Context ctx) {
        ctx.json(routingService.getStrategies().stream().map(strategy -> Map.of(
                "algorithmName", strategy.getName(),
                "supportedPreferences", java.util.Arrays.stream(RoutePreference.values())
                        .filter(strategy::supports)
                        .map(Enum::name)
                        .toList())).toList());
    }

    private void compareRoutes(Context ctx) {
        RouteRequestDto dto;
        try {
            dto = ctx.bodyAsClass(RouteRequestDto.class);
        } catch (Exception exception) {
            ctx.status(400).json(error("Invalid request body: " + exception.getMessage()));
            return;
        }
        RoadNetwork network = networkService.getNetwork();
        if (dto.startIntersectionId == null || dto.destinationIntersectionId == null
                || !network.containsIntersection(dto.startIntersectionId)
                || !network.containsIntersection(dto.destinationIntersectionId)) {
            ctx.status(400).json(error("Valid startIntersectionId and destinationIntersectionId are required"));
            return;
        }
        RoutePreference weightedPreference;
        try {
            weightedPreference = dto.preference == null
                    ? RoutePreference.CONGESTION_AWARE
                    : RoutePreference.valueOf(dto.preference.toUpperCase());
        } catch (IllegalArgumentException exception) {
            ctx.status(400).json(error("Invalid preference: " + dto.preference));
            return;
        }

        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (var strategy : routingService.getStrategies()) {
            RoutePreference effectivePreference = strategy.supports(weightedPreference)
                    ? weightedPreference
                    : java.util.Arrays.stream(RoutePreference.values())
                            .filter(strategy::supports)
                            .findFirst()
                            .orElse(null);
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("algorithm", strategy.getName());
            item.put("preference", effectivePreference == null ? null : effectivePreference.name());
            if (effectivePreference == null) {
                item.put("success", false);
                item.put("error", "No compatible preference");
            } else {
                try {
                    RouteResult result = routingService.findRoute(network, new RouteRequest(
                            dto.startIntersectionId, dto.destinationIntersectionId, effectivePreference), strategy.getName());
                    RouteResultDto resultDto = toDto(result);
                    item.put("success", true);
                    item.put("result", resultDto);
                } catch (Exception exception) {
                    item.put("success", false);
                    item.put("error", exception.getMessage());
                }
            }
            results.add(item);
        }
        ctx.json(Map.of("results", results));
    }

    private RouteResultDto toDto(RouteResult result) {
        RouteResultDto dto = new RouteResultDto();
        dto.algorithm = result.getAlgorithmName();
        dto.totalDistance = result.getRoute().getTotalDistance();
        dto.estimatedTravelTime = result.getRoute().getEstimatedTravelTime();
        dto.totalCost = result.getRoute().getTotalCost();
        dto.computationTimeMillis = result.getComputationTimeMillis();
        dto.visitedNodes = result.getVisitedNodes();
        dto.steps = result.getRoute().getSteps().stream()
                .map(step -> new RouteResultDto.RouteStepDto(
                        step.getFromIntersectionId(),
                        step.getToIntersectionId(),
                        step.getRoadId(),
                        step.getCost()))
                .collect(Collectors.toList());
        return dto;
    }

    private Map<String, String> error(String message) {
        return Map.of("error", message);
    }
}
