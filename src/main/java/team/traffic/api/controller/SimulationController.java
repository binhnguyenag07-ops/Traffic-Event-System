package team.traffic.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.service.SimulationService;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.VehicleConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        ctx.json(buildSnapshot());
    }

    private void getStatus(Context ctx) {
        ctx.json(statusResponse());
    }

    private void getSnapshot(Context ctx) {
        ctx.json(buildSnapshot());
    }

    private void getCurrentTick(Context ctx) {
        ctx.json(Map.of("currentTick", simulationService.getCurrentTick()));
    }

    private Map<String, Object> statusResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", simulationService.getStatus().name());
        response.put("currentTick", simulationService.getCurrentTick());
        return response;
    }

    private Map<String, Object> buildSnapshot() {
        List<Map<String, Object>> vehicleList = simulationService.getVehicles().stream()
                .map(this::vehicleToMap)
                .collect(Collectors.toList());

        List<Map<String, Object>> eventList = simulationService.getEvents().stream()
                .map(this::eventToMap)
                .collect(Collectors.toList());

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("tick", simulationService.getCurrentTick());
        snapshot.put("vehicles", vehicleList);
        snapshot.put("trafficEvents", eventList);
        return snapshot;
    }

    private Map<String, Object> vehicleToMap(Vehicle v) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", v.getId());
        map.put("type", v.getType().name());
        map.put("currentIntersectionId", v.getCurrentIntersectionId());
        map.put("destinationIntersectionId", v.getDestinationIntersectionId());
        map.put("status", v.getStatus().name());
        map.put("travelHistory", v.getTravelHistory());
        return map;
    }

    private Map<String, Object> eventToMap(TrafficEvent e) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", e.getId());
        map.put("type", e.getType().name());
        map.put("affectedRoadId", e.getAffectedRoadId());
        map.put("startTick", e.getStartTick());
        map.put("durationTicks", e.getDurationTicks());
        map.put("status", e.getStatus().name());
        return map;
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
            if (roadId != null) {
                configs.add(new EventConfig(id, type, roadId, startTick, durationTicks));
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

    private Map<String, String> error(String message) {
        return Map.of("error", message);
    }
}
