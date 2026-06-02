package hyo.traffic.service.impl;

import hyo.traffic.domain.event.TrafficEvent;
import hyo.traffic.observer.TrafficEventBus;
import hyo.traffic.service.TrafficEventService;

import java.util.List;

public class TrafficEventServiceImpl implements TrafficEventService {

    private final TrafficEventBus eventBus;

    public TrafficEventServiceImpl(TrafficEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void scheduleEvent(TrafficEvent event) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void triggerEvent(String eventId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void resolveEvent(String eventId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public List<TrafficEvent> getActiveEvents() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public List<TrafficEvent> getEventsForRoad(String roadId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void processEventsForTick(long tick) {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
