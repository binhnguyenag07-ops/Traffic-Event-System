package team.traffic.observer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import team.traffic.domain.event.TrafficEvent;

public class TrafficEventBus {
    private final List<TrafficEventListener> listeners;

    public TrafficEventBus() {
        this.listeners = new ArrayList<>();
    }

    public void subscribe(TrafficEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unsubscribe(TrafficEventListener listener) {
        listeners.remove(listener);
    }

    public void publish(TrafficEvent event) {
        for (TrafficEventListener listener : listeners) {
            listener.onTrafficEvent(event);
        }
    }

    public List<TrafficEventListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }
}
