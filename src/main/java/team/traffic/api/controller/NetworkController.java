package team.traffic.api.controller;

import io.javalin.Javalin;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.JunctionMovement;
import team.traffic.domain.network.MovementPermission;
import team.traffic.domain.network.MovementProvenance;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.dynamics.TurnDirection;
import team.traffic.exception.NetworkEditConflictException;
import team.traffic.service.NetworkEditingService;
import team.traffic.service.RoadNetworkService;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
                    "roadCount", network.getRoadCount(),
                    "buildingCount", network.getBuildingCount()));
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
        app.get("/api/network/movements", ctx -> ctx.json(
                networkService.getNetwork().getMovementGraph().explicitMovements()
                        .stream().map(this::movementToMap).toList()));
        app.get("/api/network/buildings", ctx -> {
            RoadNetwork network = networkService.getNetwork();
            ctx.json(network.getBuildings().values().stream()
                    .map(this::buildingToMap)
                    .collect(Collectors.toList()));
        });
        app.get("/api/network/buildings/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Building building = networkService.getNetwork().getBuilding(id);
            if (building == null) {
                ctx.status(404).json(Map.of("error", "Building not found: " + id));
                return;
            }
            ctx.json(buildingToMap(building));
        });
        app.post("/api/network/intersections", this::addIntersection);
        app.delete("/api/network/intersections/{id}", ctx -> runEdit(ctx,
                () -> { requireEditor().removeIntersection(ctx.pathParam("id")); ctx.status(204); }));
        app.post("/api/network/roads", this::addRoad);
        app.delete("/api/network/roads/{id}", ctx -> runEdit(ctx,
                () -> { requireEditor().removeRoad(ctx.pathParam("id")); ctx.status(204); }));
        app.put("/api/network/roads/{id}/condition", this::updateCondition);
        app.put("/api/network/movements", this::upsertMovement);
        app.delete("/api/network/movements", this::removeMovement);
        app.get("/api/network/intersections/{id}/roads", this::connectedRoads);
        app.get("/api/network/export", ctx -> runEdit(ctx, () -> {
            ctx.header("Content-Disposition", "attachment; filename=traffic-network.json");
            ctx.contentType("application/json").result(requireEditor().exportJson());
        }));
    }

    private void upsertMovement(io.javalin.http.Context ctx) {
        runEdit(ctx, () -> {
            JunctionMovementRequest request =
                    ctx.bodyAsClass(JunctionMovementRequest.class);
            JunctionMovement movement = requireEditor().upsertMovement(
                    new JunctionMovement(
                            request.junctionId,
                            Optional.ofNullable(request.fromRoadId),
                            request.toRoadId,
                            TurnDirection.valueOf(request.direction.toUpperCase()),
                            request.permission == null
                                    ? MovementPermission.ALLOWED
                                    : MovementPermission.valueOf(
                                            request.permission.toUpperCase()),
                            request.allowedVehicleTypes == null
                                    ? Set.of()
                                    : request.allowedVehicleTypes.stream()
                                            .map(value -> VehicleType.valueOf(
                                                    value.toUpperCase()))
                                            .collect(java.util.stream.Collectors.toSet()),
                            request.penaltySeconds,
                            MovementProvenance.EXPLICIT));
            ctx.json(movementToMap(movement));
        });
    }

    private void removeMovement(io.javalin.http.Context ctx) {
        runEdit(ctx, () -> {
            String junctionId = ctx.queryParam("junctionId");
            String fromRoadId = ctx.queryParam("fromRoadId");
            String toRoadId = ctx.queryParam("toRoadId");
            if (junctionId == null || junctionId.isBlank()
                    || toRoadId == null || toRoadId.isBlank()) {
                throw new IllegalArgumentException(
                        "junctionId and toRoadId are required");
            }
            if (!requireEditor().removeMovement(
                    junctionId, fromRoadId, toRoadId)) {
                ctx.status(404).json(Map.of("error", "Movement rule not found"));
                return;
            }
            ctx.status(204);
        });
    }

    private void addIntersection(io.javalin.http.Context ctx) {
        runEdit(ctx, () -> {
            IntersectionCreationRequest request = ctx.bodyAsClass(IntersectionCreationRequest.class);
            Intersection intersection = requireEditor().addIntersection(
                    request.id, request.name, request.latitude, request.longitude);
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

    private Map<String, Object> buildingToMap(Building building) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", building.getId());
        result.put("name", building.getName());
        result.put("type", building.getType().name());
        result.put("x", building.getX());
        result.put("y", building.getY());
        result.put("latitude", building.getLatitude());
        result.put("longitude", building.getLongitude());
        result.put("accessPoints", building.getAccessPoints().stream().map(access -> Map.of(
                "id", access.id(),
                "intersectionId", access.intersectionId(),
                "drivewayDistanceMetres", access.drivewayDistanceMetres())).toList());
        return result;
    }

    private Map<String, Object> movementToMap(JunctionMovement movement) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("junctionId", movement.junctionId());
        result.put("fromRoadId", movement.fromRoadId().orElse(null));
        result.put("toRoadId", movement.toRoadId());
        result.put("direction", movement.direction().name());
        result.put("permission", movement.permission().name());
        result.put("allowedVehicleTypes", movement.allowedVehicleTypes());
        result.put("penaltySeconds", movement.penaltySeconds());
        result.put("provenance", movement.provenance().name());
        return result;
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class IntersectionCreationRequest {
        public String id;
        public String name;
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

    public static class JunctionMovementRequest {
        public String junctionId;
        public String fromRoadId;
        public String toRoadId;
        public String direction;
        public String permission = "ALLOWED";
        public java.util.List<String> allowedVehicleTypes = java.util.List.of();
        public double penaltySeconds;
    }
}
