package team.traffic.service.impl;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.observer.TrafficEventBus;
import team.traffic.service.TrafficEventService;

public class TrafficEventServiceImpl implements TrafficEventService {

    private final List<TrafficEvent> events;
    private final TrafficEventBus eventBus;

    public TrafficEventServiceImpl(TrafficEventBus eventBus) {
        this.events = new ArrayList<>();
        this.eventBus = eventBus;
    }

    @Override
    public List<TrafficEvent> getAllEvents() {
        return new ArrayList<>(events);
    }

    @Override
    public void addEvent(TrafficEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    @Override
    public void processEventsForTick(long currentTick, RoadNetwork network) {
        if (network == null) {
            return;
        }

        for (TrafficEvent event : events) {
            if (event.getStatus() == TrafficEventStatus.SCHEDULED) {
                if (currentTick >= event.getStartTick()) {
                    event.apply(network);
                    if (eventBus != null) {
                        eventBus.publish(event);
                    }
                }
            } else if (event.getStatus() == TrafficEventStatus.ACTIVE) {
                if (currentTick >= event.getStartTick() + event.getDurationTicks()) {
                    event.resolve(network);
                    if (eventBus != null) {
                        eventBus.publish(event);
                    }
                }
            }
        }
    }
}
