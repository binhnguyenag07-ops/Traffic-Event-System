package team.traffic.api.controller;

import io.javalin.Javalin;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
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

        app.get("/api/network/roads", ctx -> {
            RoadNetwork network = networkService.getNetwork();
            ctx.json(network.getRoads().values().stream()
                    .map(this::roadToMap)
                    .collect(Collectors.toList()));
        });

        app.get("/api/network/roads/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Road road = networkService.getNetwork().getRoad(id);
            if (road == null) {
                ctx.status(404).json(Map.of("error", "Road not found: " + id));
                return;
            }
            ctx.json(roadToMap(road));
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

    private Map<String, Object> roadToMap(Road r) {
        return Map.of(
                "id", r.getId(),
                "startIntersectionId", r.getStartIntersectionId(),
                "endIntersectionId", r.getEndIntersectionId(),
                "distance", r.getDistance(),
                "speedLimit", r.getSpeedLimit(),
                "capacity", r.getCapacity(),
                "currentVehicleCount", r.getCurrentVehicleCount(),
                "status", r.getCondition().getRoadStatus().name(),
                "congestionLevel", r.getCondition().getCongestionLevel());
    }
}
