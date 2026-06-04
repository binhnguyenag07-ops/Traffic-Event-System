package team.traffic.observer;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.RoadCondition;

public class TrafficEventBus {
    private final List<TrafficEventListener> listeners = new ArrayList<>();

    public void subscribe(TrafficEventListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(TrafficEventListener listener) {
        listeners.remove(listener);
    }

    public void publishEventTriggered(TrafficEvent event) {
        for (TrafficEventListener listener : listeners) {
            listener.onEventTriggered(event);
        }
    }

    public void publishEventResolved(TrafficEvent event) {
        for (TrafficEventListener listener : listeners) {
            listener.onEventResolved(event);
        }
    }

    public void publishRoadConditionChanged(String roadId, RoadCondition condition) {
        for (TrafficEventListener listener : listeners) {
            listener.onRoadConditionChanged(roadId, condition);
        }
    }
}
