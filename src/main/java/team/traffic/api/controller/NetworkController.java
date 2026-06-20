package team.traffic.api.controller;

import io.javalin.Javalin;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.service.RoadNetworkService;

import java.util.Map;
import java.util.stream.Collectors;

public class NetworkController {
    private final RoadNetworkService networkService;

    public NetworkController(RoadNetworkService networkService) {
        this.networkService = networkService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/network", ctx -> {
            RoadNetwork network = networkService.getNetwork();
            ctx.json(Map.of(
                    "intersectionCount", network.getIntersectionCount(),
                    "roadCount", network.getRoadCount()));
        });

        app.get("/api/network/intersections", ctx -> {
            RoadNetwork network = networkService.getNetwork();
            ctx.json(network.getIntersections().values().stream()
                    .map(this::toMap)
                    .collect(Collectors.toList()));
        });

        app.get("/api/network/intersections/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Intersection intersection = networkService.getNetwork().getIntersection(id);
            if (intersection == null) {
                ctx.status(404).json(Map.of("error", "Intersection not found: " + id));
                return;
            }
            ctx.json(toMap(intersection));
        });
    }

    private Map<String, Object> toMap(Intersection i) {
        return Map.of(
                "id", i.getId(),
                "name", i.getName(),
                "x", i.getX(),
                "y", i.getY(),
                "locationType", i.getLocationType().name());
    }
}
