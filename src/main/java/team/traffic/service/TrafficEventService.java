package team.traffic.service;

import java.util.List;
import team.traffic.domain.event.TrafficEvent;

public interface TrafficEventService {
    void scheduleEvent(TrafficEvent event);

    void triggerEvent(String eventId);

    void resolveEvent(String eventId);

    List<TrafficEvent> getActiveEvents();

    List<TrafficEvent> getEventsForRoad(String roadId);

    void processEventsForTick(long tick);
}
