package team.traffic.api.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import team.traffic.api.dto.TrafficEventDto;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.CongestionEvent;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.event.TrafficEventType;
import team.traffic.domain.event.BreakdownEvent;
import team.traffic.service.SimulationService;
import team.traffic.service.TrafficEventService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TrafficEventController {
    private final TrafficEventService trafficEventService;
    private final SimulationService simulationService;

    public TrafficEventController(TrafficEventService trafficEventService, SimulationService simulationService) {
        this.trafficEventService = trafficEventService;
        this.simulationService = simulationService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/events", this::getAllEvents);
        app.post("/api/events", this::createEvent);
        app.get("/api/events/active", this::getActiveEvents);
        app.get("/api/events/road/{roadId}", this::getEventsByRoad);
        app.get("/api/events/{id}", this::getEventById);
        app.put("/api/events/{id}/status", this::updateEventStatus);
        app.delete("/api/events/{id}", this::deleteEvent);
    }

    private void getAllEvents(Context ctx) {
        List<TrafficEventDto> events = trafficEventService.getAllEvents().stream()
                .map(TrafficEventDto::new)
                .collect(Collectors.toList());
        ctx.status(200).json(events);
    }

    private void getActiveEvents(Context ctx) {
        List<TrafficEventDto> events = trafficEventService.getActiveEvents().stream()
                .map(TrafficEventDto::new)
                .collect(Collectors.toList());
        ctx.status(200).json(events);
    }

    private void getEventsByRoad(Context ctx) {
        String roadId = ctx.pathParam("roadId");
        List<TrafficEventDto> events = trafficEventService.getEventsByRoad(roadId).stream()
                .map(TrafficEventDto::new)
                .collect(Collectors.toList());
        ctx.status(200).json(events);
    }

    private void getEventById(Context ctx) {
        String id = ctx.pathParam("id");
        TrafficEvent event = trafficEventService.getEventById(id);
        if (event == null) {
            ctx.status(404).json(error("Event not found: " + id));
            return;
        }
        ctx.status(200).json(new TrafficEventDto(event));
    }

    private void updateEventStatus(Context ctx) {
        String id = ctx.pathParam("id");
        StatusUpdateRequest request;
        try {
            request = ctx.bodyAsClass(StatusUpdateRequest.class);
        } catch (Exception e) {
            ctx.status(400).json(error("Invalid request body: " + e.getMessage()));
            return;
        }
        if (request.status == null) {
            ctx.status(400).json(error("status is required"));
            return;
        }

        TrafficEventStatus status;
        try {
            status = TrafficEventStatus.valueOf(request.status.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(error("Invalid status. Valid values: SCHEDULED, ACTIVE, RESOLVED"));
            return;
        }

        try {
            TrafficEvent event = trafficEventService.updateEventStatus(id, status);
            ctx.status(200).json(new TrafficEventDto(event));
        } catch (IllegalArgumentException e) {
            ctx.status(404).json(error(e.getMessage()));
        } catch (IllegalStateException e) {
            ctx.status(409).json(error(e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(error("Error updating event status: " + e.getMessage()));
        }
    }

    private void deleteEvent(Context ctx) {
        String id = ctx.pathParam("id");
        boolean removed = trafficEventService.removeEvent(id);
        if (!removed) {
            ctx.status(404).json(error("Event not found: " + id));
            return;
        }
        ctx.status(204);
    }

    private void createEvent(Context ctx) {
        EventCreationRequest request;
        try {
            request = ctx.bodyAsClass(EventCreationRequest.class);
        } catch (Exception e) {
            ctx.status(400).json(error("Invalid request body: " + e.getMessage()));
            return;
        }

        if (request.type == null) {
            ctx.status(400).json(error("type is required"));
            return;
        }
        if (request.affectedRoadId == null) {
            ctx.status(400).json(error("affectedRoadId is required"));
            return;
        }

        TrafficEventType type;
        try {
            type = TrafficEventType.valueOf(request.type.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(error("Invalid type."));
            return;
        }

        String id = "EV-" + UUID.randomUUID().toString().substring(0, 8);
        TrafficEvent event;

        try {
            team.traffic.domain.network.RoadNetwork network = simulationService.getNetwork();
            if (network != null) {
                team.traffic.domain.network.Road road = network.getRoad(request.affectedRoadId);
                if (road == null) {
                    throw new IllegalArgumentException("affected road does not exist: " + request.affectedRoadId);
                }
                if (request.blockedLaneIndexes != null && request.blockedLaneIndexes.stream()
                        .anyMatch(laneIndex -> laneIndex == null || laneIndex < 0
                                || laneIndex >= road.getLaneCount())) {
                    throw new IllegalArgumentException(
                            "blocked lane index must be between 0 and " + (road.getLaneCount() - 1));
                }
            }
            switch (type) {
                case CONGESTION:
                    event = new CongestionEvent(id, request.affectedRoadId,
                            request.startTimeSeconds(), request.durationSeconds(),
                            request.value != null ? request.value : 0.5);
                    break;
                case ACCIDENT:
                    event = new AccidentEvent(id, request.affectedRoadId,
                            request.startTimeSeconds(), request.durationSeconds(),
                            request.value != null ? request.value : 1.0,
                            request.causingVehicleId != null ? request.causingVehicleId : "UNKNOWN",
                            request.positionAlongRoad == null ? 0.5 : request.positionAlongRoad,
                            request.blockedLaneIndexes == null
                                    ? java.util.Set.of()
                                    : java.util.Set.copyOf(request.blockedLaneIndexes));
                    break;
                case BREAKDOWN:
                    String affectedVehicleId = request.affectedVehicleId();
                    if (affectedVehicleId == null || affectedVehicleId.isBlank()) {
                        throw new IllegalArgumentException("affected vehicle id is required for a breakdown");
                    }
                    if (simulationService.getVehicles().stream()
                            .noneMatch(vehicle -> vehicle.getId().equals(affectedVehicleId))) {
                        throw new IllegalArgumentException(
                                "affected vehicle does not exist: " + affectedVehicleId);
                    }
                    event = new BreakdownEvent(id, request.affectedRoadId,
                            request.startTimeSeconds(), request.durationSeconds(),
                            affectedVehicleId,
                            request.positionAlongRoad == null ? 0.5 : request.positionAlongRoad,
                            request.blockedLaneIndexes == null
                                    ? java.util.Set.of()
                                    : java.util.Set.copyOf(request.blockedLaneIndexes));
                    break;
                case ROAD_CLOSURE:
                    event = new RoadClosureEvent(id, request.affectedRoadId,
                            request.startTimeSeconds(), request.durationSeconds());
                    break;
                default:
                    ctx.status(400).json(error("Unsupported event type"));
                    return;
            }
            trafficEventService.addEvent(event);
            ctx.status(201).json(new TrafficEventDto(event));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(error("Invalid event parameters: " + e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(error("Error creating event: " + e.getMessage()));
        }
    }

    private Map<String, String> error(String message) {
        return Map.of("error", message);
    }

    public static class EventCreationRequest {
        public String type;
        public String affectedRoadId;
        public Double startTimeSeconds;
        public Double durationSeconds;
        /** Legacy input aliases; responses never emit these names. */
        public Double startTick;
        public Double durationTicks;
        public Double value;
        public String causingVehicleId;
        public String affectedVehicleId;
        public Double positionAlongRoad;
        public List<Integer> blockedLaneIndexes;

        double startTimeSeconds() {
            return startTimeSeconds != null ? startTimeSeconds : startTick == null ? 0.0 : startTick;
        }

        double durationSeconds() {
            return durationSeconds != null ? durationSeconds : durationTicks == null ? 0.0 : durationTicks;
        }

        String affectedVehicleId() {
            return affectedVehicleId != null ? affectedVehicleId : causingVehicleId;
        }
    }

    public static class StatusUpdateRequest {
        public String status;
    }
}
