package team.traffic.simulation;

import java.util.UUID;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.CongestionEvent;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.event.TrafficEventType;
import team.traffic.domain.event.TrafficLightDelayEvent;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleFactory;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.service.RoutingService;
import team.traffic.service.TrafficEventService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrafficSimulator {
    private final RoadNetwork network;
    private final RoutingService routingService;
    private final VehicleFactory vehicleFactory;
    private final TickProcessor tickProcessor;
    private final SimulationClock clock;

    private SimulationStatus status;
    private List<Vehicle> vehicles;
    private List<TrafficEvent> events;
    private List<VehicleConfig> vehicleConfigs;
    private List<EventConfig> eventConfigs;

    public TrafficSimulator(RoadNetwork network, RoutingService routingService) {
        this.network = network;
        this.routingService = routingService;
        this.vehicleFactory = new VehicleFactory();
        this.tickProcessor = new TickProcessor(routingService);
        this.clock = new SimulationClock();
        this.status = SimulationStatus.READY;
        this.vehicles = new ArrayList<>();
        this.events = new ArrayList<>();
        this.vehicleConfigs = new ArrayList<>();
        this.eventConfigs = new ArrayList<>();
    }

    public synchronized void initialize(List<VehicleConfig> vehicleConfigs, List<EventConfig> eventConfigs) {
        resolveActiveEvents();
        this.vehicleConfigs = new ArrayList<>(vehicleConfigs);
        this.eventConfigs = new ArrayList<>(eventConfigs);
        this.vehicles = buildVehicles(this.vehicleConfigs);
        this.events = buildEvents(this.eventConfigs);
        this.clock.reset();
        this.status = SimulationStatus.READY;
    }

    public synchronized void start() {
        if (status != SimulationStatus.READY) {
            throw new IllegalStateException("Cannot start: simulation is " + status);
        }
        status = SimulationStatus.RUNNING;
    }

    public synchronized void pause() {
        if (status != SimulationStatus.RUNNING) {
            throw new IllegalStateException("Cannot pause: simulation is " + status);
        }
        status = SimulationStatus.PAUSED;
    }

    public synchronized void resume() {
        if (status != SimulationStatus.PAUSED) {
            throw new IllegalStateException("Cannot resume: simulation is " + status);
        }
        status = SimulationStatus.RUNNING;
    }

    public synchronized void reset() {
        resolveActiveEvents();
        this.vehicles = buildVehicles(this.vehicleConfigs);
        this.events = buildEvents(this.eventConfigs);
        this.clock.reset();
        this.status = SimulationStatus.READY;
    }

    public synchronized void advanceTick() {
        tickProcessor.process(clock.getCurrentTick(), network, vehicles, events);
        clock.tick();
    }

    public synchronized SimulationStatus getStatus() {
        return status;
    }

    public synchronized long getCurrentTick() {
        return clock.getCurrentTick();
    }

    public synchronized List<Vehicle> getVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    public synchronized List<TrafficEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public synchronized SimulationSnapshot getSnapshot() {
        return new SimulationSnapshot(clock.getCurrentTick(), status, vehicles, events);
    }

    public synchronized void addEvent(TrafficEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    /**
     * Creates a traffic event with configurable parameters.
     *
     * <p>This public method allows callers (including the simulation initializer
     * and the API layer) to control severity, delay, and other event-specific
     * values instead of relying on hardcoded defaults.</p>
     *
     * @param type            the type of event to create
     * @param affectedRoadId  the road affected by the event
     * @param startTick       the simulation tick when the event starts
     * @param durationTicks   how many ticks the event lasts
     * @param value           type-specific value:
     *                        congestion increase for CONGESTION (0..1),
     *                        capacity reduction for ACCIDENT (0..1),
     *                        delay ticks for TRAFFIC_LIGHT_DELAY (&gt;= 0),
     *                        ignored for ROAD_CLOSURE. Uses defaults when null.
     * @param causingVehicleId required only for ACCIDENT events, defaults to "system"
     * @return the created event
     * @throws IllegalArgumentException if parameters are invalid
     */
    public TrafficEvent createEvent(
            TrafficEventType type,
            String affectedRoadId,
            long startTick,
            long durationTicks,
            Double value,
            String causingVehicleId) {

        if (type == null) {
            throw new IllegalArgumentException("Event type must not be null.");
        }
        if (affectedRoadId == null || affectedRoadId.isBlank()) {
            throw new IllegalArgumentException("Affected road id must not be blank.");
        }

        return createEventWithId(null, type, affectedRoadId, startTick, durationTicks, value, causingVehicleId);
    }

    private TrafficEvent createEventWithId(
            String id,
            TrafficEventType type,
            String affectedRoadId,
            long startTick,
            long durationTicks,
            Double value,
            String causingVehicleId) {
        String eventId = id != null && !id.isBlank()
                ? id
                : "EV-" + UUID.randomUUID().toString().substring(0, 8);

        return switch (type) {
            case CONGESTION -> new CongestionEvent(
                    eventId,
                    affectedRoadId,
                    startTick,
                    durationTicks,
                    value != null ? value : 0.5);
            case ACCIDENT -> new AccidentEvent(
                    eventId,
                    affectedRoadId,
                    startTick,
                    durationTicks,
                    value != null ? value : 0.5,
                    causingVehicleId != null && !causingVehicleId.isBlank() ? causingVehicleId : "system");
            case ROAD_CLOSURE -> new RoadClosureEvent(
                    eventId,
                    affectedRoadId,
                    startTick,
                    durationTicks);
            case TRAFFIC_LIGHT_DELAY -> new TrafficLightDelayEvent(
                    eventId,
                    affectedRoadId,
                    startTick,
                    durationTicks,
                    value != null ? value.longValue() : 5L);
        };
    }

    /**
     * Convenience overload that creates an event with default values.
     */
    public TrafficEvent createEvent(
            TrafficEventType type,
            String affectedRoadId,
            long startTick,
            long durationTicks) {
        return createEvent(type, affectedRoadId, startTick, durationTicks, null, null);
    }

    private void resolveActiveEvents() {
        for (TrafficEvent event : events) {
            if (event.getStatus() == TrafficEventStatus.ACTIVE) {
                try {
                    event.resolve(network);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private List<Vehicle> buildVehicles(List<VehicleConfig> configs) {
        List<Vehicle> result = new ArrayList<>();
        for (VehicleConfig config : configs) {
            if (!network.containsIntersection(config.startIntersectionId())
                    || !network.containsIntersection(config.destinationIntersectionId())) {
                continue;
            }
            try {
                Vehicle vehicle = createVehicle(config);
                vehicle.requestRoute(network, routingService);
                result.add(vehicle);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private Vehicle createVehicle(VehicleConfig config) {
        VehicleType type;
        try {
            type = VehicleType.valueOf(config.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            type = VehicleType.CAR;
        }
        return switch (type) {
            case CAR -> vehicleFactory.createCar(config.id(), config.startIntersectionId(), config.destinationIntersectionId());
            case BUS -> vehicleFactory.createBus(config.id(), config.startIntersectionId(), config.destinationIntersectionId());
            case EMERGENCY -> vehicleFactory.createEmergencyVehicle(config.id(), config.startIntersectionId(), config.destinationIntersectionId());
        };
    }

    private List<TrafficEvent> buildEvents(List<EventConfig> configs) {
        List<TrafficEvent> result = new ArrayList<>();
        for (EventConfig config : configs) {
            try {
                result.add(createEvent(config));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private TrafficEvent createEvent(EventConfig config) {
        TrafficEventType type;
        try {
            type = TrafficEventType.valueOf(config.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            type = TrafficEventType.CONGESTION;
        }
        return createEventWithId(
                config.id(),
                type,
                config.affectedRoadId(),
                config.startTick(),
                config.durationTicks(),
                config.value(),
                config.causingVehicleId());
    }
}
