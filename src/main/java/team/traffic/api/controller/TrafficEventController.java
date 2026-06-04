package team.traffic.api.controller;

import java.util.List;
import team.traffic.api.dto.ApiResponse;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.service.TrafficEventService;

public class TrafficEventController {
    private final TrafficEventService trafficEventService;

    public TrafficEventController(TrafficEventService trafficEventService) {
        this.trafficEventService = trafficEventService;
    }

    public ApiResponse<Void> scheduleEvent(TrafficEvent event) {
        trafficEventService.scheduleEvent(event);
        return ApiResponse.success(null);
    }

    public ApiResponse<Void> triggerEvent(String eventId) {
        trafficEventService.triggerEvent(eventId);
        return ApiResponse.success(null);
    }

    public ApiResponse<Void> resolveEvent(String eventId) {
        trafficEventService.resolveEvent(eventId);
        return ApiResponse.success(null);
    }

    public ApiResponse<List<TrafficEvent>> getActiveEvents() {
        return ApiResponse.success(trafficEventService.getActiveEvents());
    }
}
