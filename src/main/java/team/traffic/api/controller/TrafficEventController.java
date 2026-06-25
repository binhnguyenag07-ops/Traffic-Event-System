package team.traffic.api.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import team.traffic.api.dto.TrafficEventDto;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.CongestionEvent;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventType;
import team.traffic.domain.event.TrafficLightDelayEvent;
import team.traffic.service.TrafficEventService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TrafficEventController {
    private final TrafficEventService trafficEventService;

    public TrafficEventController(TrafficEventService trafficEventService) {
        this.trafficEventService = trafficEventService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/events", this::getAllEvents);
        app.post("/api/events", this::createEvent);
    }

    private void getAllEvents(Context ctx) {
        List<TrafficEventDto> events = trafficEventService.getAllEvents().stream()
                .map(TrafficEventDto::new)
                .collect(Collectors.toList());
        ctx.status(200).json(events);
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
            switch (type) {
                case CONGESTION:
                    event = new CongestionEvent(id, request.affectedRoadId, request.startTick, request.durationTicks, request.value != null ? request.value : 0.5);
                    break;
                case ACCIDENT:
                    event = new AccidentEvent(id, request.affectedRoadId, request.startTick, request.durationTicks, request.value != null ? request.value : 1.0, request.causingVehicleId != null ? request.causingVehicleId : "UNKNOWN");
                    break;
                case ROAD_CLOSURE:
                    event = new RoadClosureEvent(id, request.affectedRoadId, request.startTick, request.durationTicks);
                    break;
                case TRAFFIC_LIGHT_DELAY:
                    event = new TrafficLightDelayEvent(id, request.affectedRoadId, request.startTick, request.durationTicks, request.value != null ? request.value.longValue() : 5L);
                    break;
                default:
                    ctx.status(400).json(error("Unsupported event type"));
                    return;
            }
            trafficEventService.addEvent(event);
            ctx.status(201).json(new TrafficEventDto(event));
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
        public long startTick;
        public long durationTicks;
        public Double value;
        public String causingVehicleId;
    }
}
