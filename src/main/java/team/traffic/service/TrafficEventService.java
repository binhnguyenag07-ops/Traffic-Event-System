package team.traffic.service;

import java.util.List;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.network.RoadNetwork;

public interface TrafficEventService {
    List<TrafficEvent> getAllEvents();
    TrafficEvent getEventById(String id);
    List<TrafficEvent> getActiveEvents();
    List<TrafficEvent> getEventsByRoad(String roadId);
    void addEvent(TrafficEvent event);
    boolean removeEvent(String id);
    TrafficEvent updateEventStatus(String id, TrafficEventStatus newStatus);
    void processEventsForTick(long currentTick, RoadNetwork network);
}
