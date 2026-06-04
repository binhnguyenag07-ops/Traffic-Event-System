package team.traffic.observer;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.RoadCondition;

public interface TrafficEventListener {
    void onEventTriggered(TrafficEvent event);

    void onEventResolved(TrafficEvent event);

    void onRoadConditionChanged(String roadId, RoadCondition condition);
}
