package team.traffic.observer;

import team.traffic.domain.event.TrafficEvent;

public interface TrafficEventListener {
    void onTrafficEvent(TrafficEvent event);
}
