package team.traffic.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.observer.TrafficEventBus;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.TrafficEventService;
import team.traffic.simulation.TrafficSimulator;

public class TrafficEventServiceImpl implements TrafficEventService {

    private final List<TrafficEvent> events;
    private final TrafficEventBus eventBus;
    private final RoadNetworkService roadNetworkService;
    private final TrafficSimulator runtime;

    public TrafficEventServiceImpl(TrafficEventBus eventBus, RoadNetworkService roadNetworkService) {
        this.events = new ArrayList<>();
        this.eventBus = eventBus;
        this.roadNetworkService = roadNetworkService;
        this.runtime = null;
    }

    public TrafficEventServiceImpl(
            TrafficEventBus eventBus,
            RoadNetworkService roadNetworkService,
            TrafficSimulator runtime) {
        this.events = new ArrayList<>();
        this.eventBus = eventBus;
        this.roadNetworkService = roadNetworkService;
        this.runtime = runtime;
    }

    private List<TrafficEvent> currentEvents() {
        return runtime == null ? events : runtime.getEvents();
    }

    @Override
    public List<TrafficEvent> getAllEvents() {
        return new ArrayList<>(currentEvents());
    }

    @Override
    public TrafficEvent getEventById(String id) {
        return currentEvents().stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<TrafficEvent> getActiveEvents() {
        return currentEvents().stream()
                .filter(e -> e.getStatus() == TrafficEventStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    @Override
    public List<TrafficEvent> getEventsByRoad(String roadId) {
        return currentEvents().stream()
                .filter(e -> e.getAffectedRoadId().equals(roadId))
                .collect(Collectors.toList());
    }

    @Override
    public void addEvent(TrafficEvent event) {
        if (event != null) {
            if (runtime == null) {
                events.add(event);
            } else {
                runtime.addEvent(event);
            }
        }
    }

    @Override
    public boolean removeEvent(String id) {
        TrafficEvent event = getEventById(id);
        if (event == null) {
            return false;
        }
        if (event.getStatus() == TrafficEventStatus.ACTIVE) {
            event.resolve(roadNetworkService.getNetwork());
            if (eventBus != null) {
                eventBus.publish(event);
            }
        }
        return runtime == null ? events.remove(event) : runtime.removeEvent(id);
    }

    @Override
    public TrafficEvent updateEventStatus(String id, TrafficEventStatus newStatus) {
        TrafficEvent event = getEventById(id);
        if (event == null) {
            throw new IllegalArgumentException("Event not found: " + id);
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (event.getStatus() == newStatus) {
            return event;
        }

        RoadNetwork network = roadNetworkService.getNetwork();
        switch (newStatus) {
            case ACTIVE:
                if (event.getStatus() != TrafficEventStatus.SCHEDULED) {
                    throw new IllegalStateException("Only a SCHEDULED event can be activated.");
                }
                event.apply(network);
                break;
            case RESOLVED:
                if (event.getStatus() == TrafficEventStatus.SCHEDULED) {
                    event.resolve(network);
                } else if (event.getStatus() == TrafficEventStatus.ACTIVE) {
                    event.resolve(network);
                }
                break;
            case SCHEDULED:
                throw new IllegalStateException("An event cannot be moved back to SCHEDULED.");
            default:
                throw new IllegalArgumentException("Unsupported status: " + newStatus);
        }

        if (eventBus != null) {
            eventBus.publish(event);
        }
        return event;
    }

    @Override
    public void processEventsForTick(long currentTick, RoadNetwork network) {
        if (network == null) {
            return;
        }

        for (TrafficEvent event : currentEvents()) {
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
