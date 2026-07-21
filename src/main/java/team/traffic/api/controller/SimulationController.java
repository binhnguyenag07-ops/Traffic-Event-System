package team.traffic.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import team.traffic.api.dto.SimulationSnapshotDto;
import team.traffic.api.dto.VehicleTracePointDto;
import team.traffic.api.dto.VehicleTraceSummaryDto;
import team.traffic.service.SimulationService;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.VehicleConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulationController {
    private final SimulationService simulationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    public void registerRoutes(Javalin app) {
        app.post("/api/simulation/initialize", this::initialize);
        app.post("/api/simulation/start", this::start);
        app.post("/api/simulation/pause", this::pause);
        app.post("/api/simulation/resume", this::resume);
        app.post("/api/simulation/reset", this::reset);
        app.post("/api/simulation/tick", this::tick);
        app.get("/api/simulation/status", this::getStatus);
        app.get("/api/simulation/snapshot", this::getSnapshot);
        app.get("/api/simulation/tick/current", this::getCurrentTick);
        app.get("/api/simulation/trace", this::getTraceSummary);
        app.get("/api/simulation/trace/ticks/{tick}", this::getStatesAtTick);
        app.get("/api/simulation/trace/vehicles/{vehicleId}", this::getVehicleTrace);
        app.get("/api/simulation/trace/vehicles/{vehicleId}/ticks/{tick}", this::getVehicleStateAtTick);
        app.put("/api/simulation/parameters", ctx -> ctx.json(statusResponse()));
    }

    private void initialize(Context ctx) {
        try {
            JsonNode body = objectMapper.readTree(ctx.body());
            List<VehicleConfig> vehicleConfigs = parseVehicleConfigs(body.get("vehicles"));
            List<EventConfig> eventConfigs = parseEventConfigs(body.get("events"));
            simulationService.initialize(vehicleConfigs, eventConfigs);
            ctx.json(statusResponse());
        } catch (Exception e) {
            ctx.status(400).json(error("Failed to initialize: " + e.getMessage()));
        }
    }

    private void start(Context ctx) {
        try {
            simulationService.start();
            ctx.json(statusResponse());
        } catch (IllegalStateException e) {
            ctx.status(400).json(error(e.getMessage()));
        }
    }

    private void pause(Context ctx) {
        try {
            simulationService.pause();
            ctx.json(statusResponse());
        } catch (IllegalStateException e) {
            ctx.status(400).json(error(e.getMessage()));
        }
    }

    private void resume(Context ctx) {
        try {
            simulationService.resume();
            ctx.json(statusResponse());
        } catch (IllegalStateException e) {
            ctx.status(400).json(error(e.getMessage()));
        }
    }

    private void reset(Context ctx) {
        simulationService.reset();
        ctx.json(statusResponse());
    }

    private void tick(Context ctx) {
        simulationService.advanceTick();
        ctx.json(new SimulationSnapshotDto(simulationService.getSnapshot()));
    }

    private void getStatus(Context ctx) {
        ctx.json(statusResponse());
    }

    private void getSnapshot(Context ctx) {
        ctx.json(new SimulationSnapshotDto(simulationService.getSnapshot()));
    }

    private void getCurrentTick(Context ctx) {
        ctx.json(Map.of("currentTick", simulationService.getCurrentTick()));
    }

    private void getTraceSummary(Context ctx) {
        ctx.json(new VehicleTraceSummaryDto(simulationService.getVehicleTraceSummary()));
    }

    private void getStatesAtTick(Context ctx) {
        try {
            long tick = parseNonNegativeLong(ctx.pathParam("tick"), "tick");
            if (!simulationService.hasVehicleTraceAtTick(tick)) {
                ctx.status(404).json(error("No vehicle trace recorded at tick " + tick + "."));
                return;
            }
            ctx.json(simulationService.getVehicleStatesAtTick(tick).stream()
                    .map(VehicleTracePointDto::new)
                    .toList());
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(error(e.getMessage()));
        }
    }

    private void getVehicleTrace(Context ctx) {
        try {
            String vehicleId = ctx.pathParam("vehicleId");
            long fromTick = parseOptionalNonNegativeLong(ctx.queryParam("fromTick"), 0, "fromTick");
            long toTick = parseOptionalNonNegativeLong(
                    ctx.queryParam("toTick"),
                    simulationService.getCurrentTick(),
                    "toTick");
            ctx.json(simulationService.getVehicleTrace(vehicleId, fromTick, toTick).stream()
                    .map(VehicleTracePointDto::new)
                    .toList());
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(error(e.getMessage()));
        }
    }

    private void getVehicleStateAtTick(Context ctx) {
        try {
            String vehicleId = ctx.pathParam("vehicleId");
            long tick = parseNonNegativeLong(ctx.pathParam("tick"), "tick");
            simulationService.getVehicleStateAtTick(vehicleId, tick)
                    .ifPresentOrElse(
                            point -> ctx.json(new VehicleTracePointDto(point)),
                            () -> ctx.status(404).json(error(
                                    "No trace for vehicle " + vehicleId + " at tick " + tick + ".")));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(error(e.getMessage()));
        }
    }

    private Map<String, Object> statusResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", simulationService.getStatus().name());
        response.put("currentTick", simulationService.getCurrentTick());
        return response;
    }

    private List<VehicleConfig> parseVehicleConfigs(JsonNode vehiclesNode) {
        List<VehicleConfig> configs = new ArrayList<>();
        if (vehiclesNode == null || !vehiclesNode.isArray()) return configs;
        for (JsonNode node : vehiclesNode) {
            String id = textOrDefault(node, "id", "vehicle-" + configs.size());
            String type = textOrDefault(node, "type", "CAR");
            String start = firstNonNull(node, "currentIntersectionId", "startIntersectionId");
            String dest = firstNonNull(node, "destinationIntersectionId", "endIntersectionId");
            if (start != null && dest != null) {
                configs.add(new VehicleConfig(id, type, start, dest));
            }
        }
        return configs;
    }

    private List<EventConfig> parseEventConfigs(JsonNode eventsNode) {
        List<EventConfig> configs = new ArrayList<>();
        if (eventsNode == null || !eventsNode.isArray()) return configs;
        for (JsonNode node : eventsNode) {
            String id = textOrDefault(node, "id", "event-" + configs.size());
            String type = textOrDefault(node, "type", "CONGESTION");
            String roadId = firstNonNull(node, "affectedRoadId", "roadId");
            long startTick = node.has("startTick") ? node.get("startTick").asLong(0) : 0;
            long durationTicks = node.has("durationTicks") ? node.get("durationTicks").asLong(10) : 10;
            Double value = node.has("value") && !node.get("value").isNull()
                    ? node.get("value").asDouble()
                    : null;
            String causingVehicleId = firstNonNull(node, "causingVehicleId");
            if (roadId != null) {
                configs.add(new EventConfig(id, type, roadId, startTick, durationTicks, value, causingVehicleId));
            }
        }
        return configs;
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : defaultValue;
    }

    private String firstNonNull(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode f = node.get(field);
            if (f != null && !f.isNull() && !f.asText().isBlank()) return f.asText();
        }
        return null;
    }

    private long parseOptionalNonNegativeLong(String value, long defaultValue, String fieldName) {
        return value == null || value.isBlank()
                ? defaultValue
                : parseNonNegativeLong(value, fieldName);
    }

    private long parseNonNegativeLong(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(fieldName + " must not be negative.");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be an integer.");
        }
    }

    private Map<String, String> error(String message) {
        return Map.of("error", message);
    }
}
