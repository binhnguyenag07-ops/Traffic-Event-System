package team.traffic.api.controller;

import io.javalin.Javalin;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.RoadStatus;
import team.traffic.exception.NetworkEditConflictException;
import team.traffic.service.NetworkEditingService;
import team.traffic.service.RoadNetworkService;

import java.util.Map;
import java.util.stream.Collectors;

public class NetworkController {
    private final RoadNetworkService networkService;
    private final NetworkEditingService editingService;

    public NetworkController(RoadNetworkService networkService) {
        this(networkService, null);
    }

    public NetworkController(RoadNetworkService networkService, NetworkEditingService editingService) {
        this.networkService = networkService;
        this.editingService = editingService;
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
        app.post("/api/network/intersections", this::addIntersection);
        app.delete("/api/network/intersections/{id}", ctx -> runEdit(ctx,
                () -> { requireEditor().removeIntersection(ctx.pathParam("id")); ctx.status(204); }));
        app.post("/api/network/roads", this::addRoad);
        app.delete("/api/network/roads/{id}", ctx -> runEdit(ctx,
                () -> { requireEditor().removeRoad(ctx.pathParam("id")); ctx.status(204); }));
        app.put("/api/network/roads/{id}/condition", this::updateCondition);
        app.get("/api/network/intersections/{id}/roads", this::connectedRoads);
        app.get("/api/network/export", ctx -> runEdit(ctx, () -> {
            ctx.header("Content-Disposition", "attachment; filename=traffic-network.json");
            ctx.contentType("application/json").result(requireEditor().exportJson());
        }));
    }

    private void addIntersection(io.javalin.http.Context ctx) {
        runEdit(ctx, () -> {
            IntersectionCreationRequest request = ctx.bodyAsClass(IntersectionCreationRequest.class);
            LocationType type = LocationType.valueOf(request.locationType.toUpperCase());
            Intersection intersection = requireEditor().addIntersection(
                    request.id, request.name, type, request.latitude, request.longitude);
            ctx.status(201).json(toMap(intersection));
        });
    }

    private void addRoad(io.javalin.http.Context ctx) {
        runEdit(ctx, () -> {
            RoadCreationRequest request = ctx.bodyAsClass(RoadCreationRequest.class);
            NetworkEditingService.RoadCreationOutcome outcome = requireEditor().addRoads(
                    new NetworkEditingService.RoadCreationRequest(request.id, request.reverseRoadId,
                            request.startIntersectionId, request.endIntersectionId,
                            request.speedLimit, request.capacity, request.bidirectional,
                            request.roadClass == null || request.roadClass.isBlank()
                                    ? RoadClass.UNCLASSIFIED
                                    : RoadClass.valueOf(request.roadClass.toUpperCase()),
                            request.laneCount, request.laneWidthMetres,
                            request.name, request.physicalRoadId));
            ctx.status(201).json(Map.of(
                    "roads", outcome.roads().stream().map(this::roadToMap).toList(),
                    "geometrySources", outcome.geometrySources()));
        });
    }

    private void updateCondition(io.javalin.http.Context ctx) {
        runEdit(ctx, () -> {
            RoadConditionRequest request = ctx.bodyAsClass(RoadConditionRequest.class);
            Road road = requireEditor().updateRoadCondition(ctx.pathParam("id"),
                    RoadStatus.valueOf(request.status.toUpperCase()), request.congestionLevel);
            ctx.json(roadToMap(road));
        });
    }

    private void connectedRoads(io.javalin.http.Context ctx) {
        runEdit(ctx, () -> {
            NetworkEditingService.ConnectedRoads roads = requireEditor().findConnectedRoads(ctx.pathParam("id"));
            ctx.json(Map.of(
                    "incoming", roads.incoming().stream().map(this::roadToMap).toList(),
                    "outgoing", roads.outgoing().stream().map(this::roadToMap).toList()));
        });
    }

    private NetworkEditingService requireEditor() {
        if (editingService == null) throw new UnsupportedOperationException("Network editing is not configured");
        return editingService;
    }

    private void runEdit(io.javalin.http.Context ctx, EditAction action) {
        try {
            action.run();
        } catch (NetworkEditConflictException exception) {
            ctx.status(409).json(Map.of("error", errorMessage(exception, "Network edit conflicts with simulation state")));
        } catch (IllegalArgumentException exception) {
            int status = exception.getMessage() != null && exception.getMessage().contains("not found") ? 404 : 400;
            ctx.status(status).json(Map.of("error", errorMessage(exception, "Invalid network edit request")));
        } catch (UnsupportedOperationException exception) {
            ctx.status(501).json(Map.of("error", errorMessage(exception, "Network editing is unavailable")));
        } catch (Exception exception) {
            ctx.status(400).json(Map.of("error", errorMessage(exception, "Unable to edit network")));
        }
    }

    private String errorMessage(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? fallback : exception.getMessage();
    }

    @FunctionalInterface
    private interface EditAction { void run() throws Exception; }

    private Map<String, Object> toMap(Intersection i) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", i.getId());
        result.put("name", i.getName());
        result.put("x", i.getX());
        result.put("y", i.getY());
        result.put("locationType", i.getLocationType().name());
        result.put("latitude", i.getLatitude());
        result.put("longitude", i.getLongitude());
        return result;
    }

    private Map<String, Object> roadToMap(Road r) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", r.getId());
        result.put("name", r.getName());
        result.put("physicalRoadId", r.getPhysicalRoadId());
        result.put("startIntersectionId", r.getStartIntersectionId());
        result.put("endIntersectionId", r.getEndIntersectionId());
        result.put("distance", r.getDistance());
        result.put("speedLimit", r.getSpeedLimit());
        result.put("capacity", r.getCapacity());
        result.put("laneCount", r.getLaneCount());
        result.put("laneWidthMetres", r.getLaneWidthMetres());
        result.put("roadWidthMetres", r.getRoadWidthMetres());
        result.put("roadClass", r.getRoadClass().name());
        result.put("currentVehicleCount", r.getCurrentVehicleCount());
        result.put("vehicleDensity", r.getCondition().getVehicleDensity());
        result.put("status", r.getCondition().getRoadStatus().name());
        result.put("congestionLevel", r.getCondition().getCongestionLevel());
        result.put("travelCost", r.calculateTravelCost());
        result.put("available", r.isAvailable());
        result.put("geometry", r.getGeometry());
        return result;
    }

    public static class IntersectionCreationRequest {
        public String id;
        public String name;
        public String locationType = "NORMAL";
        public double latitude;
        public double longitude;
    }

    public static class RoadCreationRequest {
        public String id;
        public String reverseRoadId;
        public String startIntersectionId;
        public String endIntersectionId;
        public double speedLimit;
        public int capacity;
        public boolean bidirectional;
        public String roadClass = "UNCLASSIFIED";
        public Integer laneCount;
        public Double laneWidthMetres;
        public String name;
        public String physicalRoadId;
    }

    public static class RoadConditionRequest {
        public String status;
        public double congestionLevel;
    }
}
