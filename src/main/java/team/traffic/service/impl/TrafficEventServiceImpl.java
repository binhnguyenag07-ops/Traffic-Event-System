package team.traffic.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.observer.TrafficEventBus;
import team.traffic.service.TrafficEventService;

public class TrafficEventServiceImpl implements TrafficEventService {
    private final List<TrafficEvent> events = new ArrayList<>();
    private final TrafficEventBus eventBus = new TrafficEventBus();

    @Override
    public void scheduleEvent(TrafficEvent event) {
        events.add(event);
    }

    @Override
    public void triggerEvent(String eventId) {
        TrafficEvent event = findById(eventId);
        if (event != null) {
            event.apply();
            eventBus.publishEventTriggered(event);
        }
    }

    @Override
    public void resolveEvent(String eventId) {
        TrafficEvent event = findById(eventId);
        if (event != null) {
            event.resolve();
            eventBus.publishEventResolved(event);
        }
    }

    @Override
    public List<TrafficEvent> getActiveEvents() {
        return Collections.unmodifiableList(events);
    }

    @Override
    public List<TrafficEvent> getEventsForRoad(String roadId) {
        List<TrafficEvent> result = new ArrayList<>();
        for (TrafficEvent event : events) {
            if (event.getAffectedRoadId().equals(roadId)) {
                result.add(event);
            }
        }
        return result;
    }

    @Override
    public void processEventsForTick(long tick) {
        // TODO: trigger scheduled events and resolve expired events.
    }

    public TrafficEventBus getEventBus() {
        return eventBus;
    }

    private TrafficEvent findById(String eventId) {
        return events.stream()
                .filter(event -> event.getId().equals(eventId))
                .findFirst()
                .orElse(null);
    }
}
