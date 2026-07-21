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
import team.traffic.domain.statistics.CompletedTrip;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleFactory;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.service.RoutingService;
import team.traffic.service.TrafficEventService;
import team.traffic.simulation.trace.VehicleTracePoint;
import team.traffic.simulation.trace.VehicleTraceStore;
import team.traffic.simulation.trace.VehicleTraceSummary;
import team.traffic.observer.TrafficEventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class TrafficSimulator {
    private RoadNetwork network;
    private final RoutingService routingService;
    private final VehicleFactory vehicleFactory;
    private final TickProcessor tickProcessor;
    private final SimulationClock clock;
    private final VehicleTraceStore vehicleTraceStore;

    private SimulationStatus status;
    private List<Vehicle> vehicles;
    private List<TrafficEvent> events;
    private final List<CompletedTrip> completedTrips;
    private List<VehicleConfig> vehicleConfigs;
    private List<EventConfig> eventConfigs;
    private double simulationSecondsPerTick;

    public TrafficSimulator(RoadNetwork network, RoutingService routingService) {
        this(network, routingService, null);
    }

    public TrafficSimulator(RoadNetwork network, RoutingService routingService, TrafficEventBus eventBus) {
        this.network = network;
        this.routingService = routingService;
        this.vehicleFactory = new VehicleFactory();
        this.tickProcessor = new TickProcessor(routingService, eventBus);
        this.clock = new SimulationClock();
        this.vehicleTraceStore = new VehicleTraceStore();
        this.status = SimulationStatus.READY;
        this.vehicles = new ArrayList<>();
        this.events = new ArrayList<>();
        this.completedTrips = new ArrayList<>();
        this.vehicleConfigs = new ArrayList<>();
        this.eventConfigs = new ArrayList<>();
        this.simulationSecondsPerTick = TickProcessor.DEFAULT_SIMULATION_SECONDS_PER_TICK;
    }

    public synchronized void initialize(List<VehicleConfig> vehicleConfigs, List<EventConfig> eventConfigs) {
        resolveActiveEvents();
        releaseVehicleOccupancy();
        this.vehicleConfigs = new ArrayList<>(vehicleConfigs);
        this.eventConfigs = new ArrayList<>(eventConfigs);
        this.vehicles = buildVehicles(this.vehicleConfigs);
        this.events = buildEvents(this.eventConfigs);
        this.completedTrips.clear();
        this.clock.reset();
        this.status = SimulationStatus.READY;
        this.vehicleTraceStore.reset(clock.getCurrentTick(), vehicles);
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
        releaseVehicleOccupancy();
        this.vehicles = buildVehicles(this.vehicleConfigs);
        this.events = buildEvents(this.eventConfigs);
        this.completedTrips.clear();
        this.clock.reset();
        this.status = SimulationStatus.READY;
        this.vehicleTraceStore.reset(clock.getCurrentTick(), vehicles);
    }

    public synchronized void advanceTick() {
        tickProcessor.process(clock.getCurrentTick(), network, vehicles, events, simulationSecondsPerTick);
        clock.tick();
        vehicleTraceStore.record(clock.getCurrentTick(), vehicles);
    }

    public synchronized double getSimulationSecondsPerTick() {
        return simulationSecondsPerTick;
    }

    public synchronized void setSimulationSecondsPerTick(double simulationSecondsPerTick) {
        if (!Double.isFinite(simulationSecondsPerTick) || simulationSecondsPerTick <= 0) {
            throw new IllegalArgumentException("Simulation seconds per tick must be positive.");
        }
        this.simulationSecondsPerTick = simulationSecondsPerTick;
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

    public synchronized List<CompletedTrip> getCompletedTrips() {
        return List.copyOf(completedTrips);
    }

    public synchronized SimulationSnapshot getSnapshot() {
        return new SimulationSnapshot(clock.getCurrentTick(), status, vehicles, events);
    }

    public synchronized VehicleTraceSummary getVehicleTraceSummary() {
        return vehicleTraceStore.getSummary();
    }

    public synchronized boolean hasVehicleTraceAtTick(long tick) {
        return vehicleTraceStore.hasTick(tick);
    }

    public synchronized List<VehicleTracePoint> getVehicleStatesAtTick(long tick) {
        return vehicleTraceStore.getStatesAtTick(tick);
    }

    public synchronized Optional<VehicleTracePoint> getVehicleStateAtTick(String vehicleId, long tick) {
        return vehicleTraceStore.getVehicleStateAtTick(vehicleId, tick);
    }

    public synchronized List<VehicleTracePoint> getVehicleTrace(
            String vehicleId,
            long fromTick,
            long toTick) {
        return vehicleTraceStore.getVehicleTrace(vehicleId, fromTick, toTick);
    }

    public synchronized void addEvent(TrafficEvent event) {
        if (event != null && events.stream().noneMatch(existing -> existing.getId().equals(event.getId()))) {
            events.add(event);
        }
    }

    public synchronized boolean removeEvent(String eventId) {
        TrafficEvent event = events.stream()
                .filter(candidate -> candidate.getId().equals(eventId))
                .findFirst()
                .orElse(null);
        if (event == null) {
            return false;
        }
        if (event.getStatus() == TrafficEventStatus.ACTIVE) {
            event.resolve(network);
        }
        return events.remove(event);
    }

    public synchronized void addVehicle(Vehicle vehicle) {
        if (vehicle != null && vehicles.stream().noneMatch(existing -> existing.getId().equals(vehicle.getId()))) {
            vehicle.requestRoute(network, routingService);
            vehicles.add(vehicle);
        }
    }

    public synchronized boolean removeVehicle(String vehicleId) {
        Vehicle vehicle = vehicles.stream()
                .filter(candidate -> candidate.getId().equals(vehicleId))
                .findFirst()
                .orElse(null);
        if (vehicle == null) {
            return false;
        }
        vehicle.releaseRoad(network);
        if (vehicle.hasReachedDestination()) {
            completedTrips.add(new CompletedTrip(
                    vehicle.getId(),
                    vehicle.getType(),
                    vehicle.getTravelTimeSeconds(),
                    vehicle.getDistanceTravelledKm(),
                    vehicle.getRouteRecalculationCount()));
        }
        return vehicles.remove(vehicle);
    }

    public synchronized RoadNetwork getNetwork() {
        return network;
    }

    public synchronized void replaceNetwork(RoadNetwork replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException("Road network is required.");
        }
        resolveActiveEvents();
        releaseVehicleOccupancy();
        network = replacement;
        vehicles = new ArrayList<>();
        events = new ArrayList<>();
        completedTrips.clear();
        vehicleConfigs = new ArrayList<>();
        eventConfigs = new ArrayList<>();
        clock.reset();
        status = SimulationStatus.READY;
        vehicleTraceStore.reset(clock.getCurrentTick(), vehicles);
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

    private void releaseVehicleOccupancy() {
        for (Vehicle vehicle : vehicles) {
            vehicle.releaseRoad(network);
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
