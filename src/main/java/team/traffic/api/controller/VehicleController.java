package team.traffic.api.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import team.traffic.api.dto.VehicleDto;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.VehicleService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VehicleController {
    private final VehicleService vehicleService;
    private final RoadNetworkService networkService;

    public VehicleController(VehicleService vehicleService, RoadNetworkService networkService) {
        this.vehicleService = vehicleService;
        this.networkService = networkService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/vehicles", this::getAllVehicles);
        app.post("/api/vehicles", this::createVehicle);
    }

    private void getAllVehicles(Context ctx) {
        List<VehicleDto> vehicles = vehicleService.getAllVehicles().stream()
                .map(VehicleDto::new)
                .collect(Collectors.toList());
        ctx.status(200).json(vehicles);
    }

    private void createVehicle(Context ctx) {
        VehicleCreationRequest request;
        try {
            request = ctx.bodyAsClass(VehicleCreationRequest.class);
        } catch (Exception e) {
            ctx.status(400).json(error("Invalid request body: " + e.getMessage()));
            return;
        }

        if (request.type == null) {
            ctx.status(400).json(error("type is required"));
            return;
        }
        if (request.startIntersectionId == null) {
            ctx.status(400).json(error("startIntersectionId is required"));
            return;
        }
        String destinationIntersectionId = request.getDestinationIntersectionId();
        if (destinationIntersectionId == null) {
            ctx.status(400).json(error("destinationIntersectionId is required"));
            return;
        }

        VehicleType type;
        try {
            type = VehicleType.valueOf(request.type.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(error("Invalid type. Valid values: CAR, BUS, EMERGENCY"));
            return;
        }

        try {
            Vehicle vehicle = vehicleService.createVehicle(
                    type,
                    request.startIntersectionId,
                    destinationIntersectionId,
                    networkService.getNetwork());
            ctx.status(201).json(new VehicleDto(vehicle));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(error("Error creating vehicle: " + e.getMessage()));
        }
    }

    private Map<String, String> error(String message) {
        return Map.of("error", message);
    }

    public static class VehicleCreationRequest {
        public String type;
        public String startIntersectionId;
        public String destinationIntersectionId;
        public String endIntersectionId;

        public String getDestinationIntersectionId() {
            if (destinationIntersectionId != null && !destinationIntersectionId.isBlank()) {
                return destinationIntersectionId;
            }
            return endIntersectionId;
        }
    }
}
