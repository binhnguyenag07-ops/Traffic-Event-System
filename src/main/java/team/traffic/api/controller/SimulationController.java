package team.traffic.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import team.traffic.api.dto.SimulationSnapshotDto;
import team.traffic.api.dto.DemandScheduleDto;
import team.traffic.api.dto.VehicleTracePointDto;
import team.traffic.api.dto.VehicleTraceSummaryDto;
import team.traffic.domain.demand.DemandScenario;
import team.traffic.persistence.DemandScenarioParser;
import team.traffic.persistence.ScenarioConfigParser;
import team.traffic.persistence.AdaptiveTrafficConfigParser;
import team.traffic.persistence.DriverDecisionProfileParser;
import team.traffic.service.SimulationService;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.VehicleConfig;
import java.util.HashMap;
import team.traffic.simulation.SimulationClock;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;

public class SimulationController {
    private static final int DEFAULT_DEMAND_SCHEDULE_LIMIT = 250;
    private static final int MAX_DEMAND_SCHEDULE_LIMIT = 1_000;
    private static final String PACE_PROTOCOL_HEADER = "X-Traffic-Pace-Protocol";
    private static final String CURRENT_PACE_PROTOCOL = "2";

    private final SimulationService simulationService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object paceProtocolLock = new Object();
    private boolean currentPaceProtocolClaimed;

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
        app.get("/api/simulation/demand/schedule", this::getDemandSchedule);
        app.get("/api/simulation/diagnostics", ctx -> {
            Object diagnostics = simulationService.getDiagnostics();
            ctx.json(diagnostics == null ? Map.of() : diagnostics);
        });
        app.get("/api/simulation/tick/current", this::getCurrentTick);
        app.get("/api/simulation/trace", this::getTraceSummary);
        app.get("/api/simulation/trace/frames", ctx -> ctx.json(simulationService.getFrameTimeline()));
        app.get("/api/simulation/trace/frames/at", this::getFrameAtElapsedSecond);
        app.get("/api/simulation/trace/ticks/{tick}", this::getStatesAtTick);
        app.get("/api/simulation/trace/vehicles/{vehicleId}", this::getVehicleTrace);
        app.get("/api/simulation/trace/vehicles/{vehicleId}/ticks/{tick}", this::getVehicleStateAtTick);
        app.get("/api/simulation/trace/flow", ctx -> ctx.json(simulationService.getRoadFlowSamples()));
        app.get("/api/simulation/trace/fundamental", ctx -> ctx.json(simulationService.getFundamentalDiagram()));
        app.get("/api/simulation/trace/roads/{roadId}/time-space", this::getTimeSpaceDiagram);
        app.get("/api/simulation/signals", ctx -> ctx.json(
                simulationService.getSignals().stream()
                        .map(team.traffic.api.dto.TrafficSignalDto::new)
                        .toList()));
        app.put("/api/simulation/parameters", this::setParameters);
        app.sse("/api/simulation/stream", client -> {
            Consumer<SimulationSnapshot> listener = snapshot -> {
                Object payload = simulationService.getCurrentFrame()
                        .map(frame -> (Object) frame)
                        .orElseGet(() -> snapshotDto(snapshot));
                client.sendEvent("snapshot", payload);
            };
            simulationService.addSnapshotListener(listener);
            client.onClose(() -> simulationService.removeSnapshotListener(listener));
            listener.accept(simulationService.getSnapshot());
            client.keepAlive();
        });
    }

    private void getDemandSchedule(Context ctx) {
        try {
            int offset = parseNonNegativeInteger(ctx.queryParam("offset"), "offset", 0);
            int limit = parsePositiveInteger(
                    ctx.queryParam("limit"),
                    "limit",
                    DEFAULT_DEMAND_SCHEDULE_LIMIT);
            if (limit > MAX_DEMAND_SCHEDULE_LIMIT) {
                throw new IllegalArgumentException(
                        "limit must not exceed " + MAX_DEMAND_SCHEDULE_LIMIT);
            }
            ctx.json(DemandScheduleDto.of(
                    simulationService.getDemandPlans(),
                    simulationService.getDemandRunStatistics(),
                    simulationService.getNetwork(),
                    simulationService.getStartTimeOfDaySeconds(),
                    offset,
                    limit));
        } catch (IllegalArgumentException exception) {
            ctx.status(400).json(error(exception.getMessage()));
        }
    }

    private static int parseNonNegativeInteger(
            String raw,
            String field,
            int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " must be a non-negative integer");
        }
    }

    private static int parsePositiveInteger(
            String raw,
            String field,
            int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " must be a positive integer");
        }
    }

    private void initialize(Context ctx) {
        try {
            JsonNode body = objectMapper.readTree(ctx.body());
            List<VehicleConfig> vehicleConfigs =
                    ScenarioConfigParser.parseVehicles(body.get("vehicles"));
            List<EventConfig> eventConfigs =
                    ScenarioConfigParser.parseEvents(body.get("events"));
            if (body.has("adaptiveTraffic")) {
                simulationService.configureAdaptiveTraffic(
                        AdaptiveTrafficConfigParser.parse(body.get("adaptiveTraffic")));
            }
            if (body.has("driverProfiles")) {
                simulationService.configureDriverDecisionProfiles(
                        DriverDecisionProfileParser.parse(body.get("driverProfiles")));
            }
            Optional<DemandScenario> demand = new DemandScenarioParser().parseOptional(
                    body,
                    simulationService.getNetwork(),
                    Path.of(".").toAbsolutePath().normalize());
            if (demand.isPresent()) {
                simulationService.initialize(demand.get(), vehicleConfigs, eventConfigs);
            } else {
                simulationService.initialize(vehicleConfigs, eventConfigs);
            }
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
        if (simulationService.getStatus() == SimulationStatus.RUNNING) {
            ctx.status(409).json(error("Manual ticks are unavailable while the simulation is running."));
            return;
        }
        simulationService.advanceTick();
        ctx.json(snapshotDto(simulationService.getSnapshot()));
    }

    private void setParameters(Context ctx) {
        try {
            JsonNode body = objectMapper.readTree(ctx.body());
            double simulationSeconds = body.has("simulationSecondsPerTick")
                    ? body.get("simulationSecondsPerTick").asDouble()
                    : simulationService.getSimulationSecondsPerTick();
            long playbackInterval = body.has("playbackIntervalMs")
                    ? body.get("playbackIntervalMs").asLong()
                    : simulationService.getPlaybackIntervalMillis();
            boolean hasExpectedSimulationSeconds = body.has("expectedSimulationSecondsPerTick");
            boolean hasExpectedPlaybackInterval = body.has("expectedPlaybackIntervalMs");
            if (hasExpectedSimulationSeconds != hasExpectedPlaybackInterval) {
                throw new IllegalArgumentException(
                        "Both expected simulation step and playback interval are required.");
            }
            Map<String, Object> response;
            synchronized (paceProtocolLock) {
                if (!claimOrAcceptPaceProtocol(ctx)) {
                    ctx.status(409).json(error(
                            "Playback control came from an outdated dashboard; reload that tab."));
                    return;
                }
                boolean parametersApplied;
                if (hasExpectedSimulationSeconds) {
                    parametersApplied = simulationService.setParametersIfCurrent(
                            body.get("expectedSimulationSecondsPerTick").asDouble(),
                            body.get("expectedPlaybackIntervalMs").asLong(),
                            simulationSeconds,
                            playbackInterval);
                } else {
                    simulationService.setParameters(simulationSeconds, playbackInterval);
                    parametersApplied = true;
                }
                response = statusResponse();
                response.put("parametersApplied", parametersApplied);
            }
            ctx.json(response);
        } catch (Exception exception) {
            ctx.status(400).json(error(exception.getMessage()));
        }
    }

    private void getStatus(Context ctx) {
        synchronized (paceProtocolLock) {
            claimOrAcceptPaceProtocol(ctx);
        }
        ctx.json(statusResponse());
    }

    private boolean claimOrAcceptPaceProtocol(Context ctx) {
        if (CURRENT_PACE_PROTOCOL.equals(ctx.header(PACE_PROTOCOL_HEADER))) {
            currentPaceProtocolClaimed = true;
            return true;
        }
        return !currentPaceProtocolClaimed;
    }

    private void getSnapshot(Context ctx) {
        ctx.json(snapshotDto(simulationService.getSnapshot()));
    }

    private void getCurrentTick(Context ctx) {
        ctx.json(Map.of("currentTick", simulationService.getCurrentTick()));
    }

    private Map<String, Object> statusResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", simulationService.getStatus().name());
        response.put("currentTick", simulationService.getCurrentTick());
        response.put("elapsedSeconds", simulationService.getElapsedSeconds());
        response.put("simulationSecondsPerTick", simulationService.getSimulationSecondsPerTick());
        response.put("playbackIntervalMs", simulationService.getPlaybackIntervalMillis());
        response.put("startTimeOfDaySeconds", simulationService.getStartTimeOfDaySeconds());
        response.put("timeOfDaySeconds", simulationService.getTimeOfDaySeconds());
        response.put("timeOfDay", SimulationClock.formatTimeOfDay(simulationService.getTimeOfDaySeconds()));
        response.put("demand", simulationService.getDemandRunStatistics());
        return response;
    }

    private SimulationSnapshotDto snapshotDto(SimulationSnapshot snapshot) {
        SimulationSnapshotDto dto = new SimulationSnapshotDto(snapshot, simulationService.getNetwork());
        simulationService.getCurrentFrame().ifPresent(frame -> {
            if (frame.tick() == snapshot.tick()
                    && Math.abs(frame.elapsedSeconds() - snapshot.elapsedSeconds()) < 1e-9) {
                dto.setTimelineId(frame.timelineId());
                dto.setFrameId(frame.frameId());
            }
        });
        return dto;
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

    private void getFrameAtElapsedSecond(Context ctx) {
        try {
            double elapsedSeconds = parseNonNegativeDouble(
                    ctx.queryParam("elapsedSeconds"), "elapsedSeconds");
            simulationService.getFrameAtOrBefore(elapsedSeconds)
                    .ifPresentOrElse(
                            ctx::json,
                            () -> ctx.status(404).json(error(
                                    "No retained simulation frame at or before "
                                            + elapsedSeconds + " seconds.")));
        } catch (IllegalArgumentException exception) {
            ctx.status(400).json(error(exception.getMessage()));
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

    private double parseNonNegativeDouble(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed < 0) {
                throw new IllegalArgumentException(fieldName + " must be finite and not negative.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " must be a number.");
        }
    }

    private Map<String, String> error(String message) {
        return Map.of("error", message);
    }

    private void getTimeSpaceDiagram(Context ctx) {
        try {
            ctx.json(simulationService.getTimeSpaceDiagram(ctx.pathParam("roadId")));
        } catch (IllegalArgumentException exception) {
            ctx.status(404).json(error(exception.getMessage()));
        }
    }
}
