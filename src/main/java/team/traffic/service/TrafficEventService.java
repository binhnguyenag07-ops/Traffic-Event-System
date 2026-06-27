package team.traffic.service;

import java.util.List;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.RoadNetwork;

public interface TrafficEventService {
    List<TrafficEvent> getAllEvents();
    void addEvent(TrafficEvent event);
    void processEventsForTick(long currentTick, RoadNetwork network);
}
