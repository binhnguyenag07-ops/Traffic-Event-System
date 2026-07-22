package team.traffic.simulation;

import java.util.UUID;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.CongestionEvent;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.event.TrafficEventType;
import team.traffic.domain.event.BreakdownEvent;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.statistics.CompletedTrip;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleFactory;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.Bus;
import team.traffic.service.RoutingService;
import team.traffic.service.TrafficEventService;
import team.traffic.simulation.trace.VehicleTracePoint;
import team.traffic.simulation.trace.VehicleTraceStore;
import team.traffic.simulation.trace.VehicleTraceSummary;
import team.traffic.simulation.trace.FlowRecorder;
import team.traffic.simulation.trace.FundamentalDiagramPoint;
import team.traffic.simulation.trace.RoadFlowSample;
import team.traffic.simulation.trace.FundamentalDiagram;
import team.traffic.simulation.trace.TimeSpaceDiagram;
import team.traffic.simulation.history.SimulationFrame;
import team.traffic.simulation.history.SimulationFrameHistory;
import team.traffic.simulation.history.SimulationTimeline;
import team.traffic.observer.TrafficEventBus;
import team.traffic.domain.signal.SignalPlan;
import team.traffic.domain.signal.TrafficSignal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class TrafficSimulator {
    public static final double ARRIVED_RETENTION_SECONDS = 5.0;
    private RoadNetwork network;
    private final RoutingService routingService;
    private final VehicleFactory vehicleFactory;
    private final TickProcessor tickProcessor;
    private final SimulationClock clock;
    private final VehicleTraceStore vehicleTraceStore;
    private final FlowRecorder flowRecorder;
    private final SimulationFrameHistory frameHistory;

    private SimulationStatus status;
    private List<Vehicle> vehicles;
    private List<TrafficEvent> events;
    private List<TrafficSignal> signals;
    private final List<CompletedTrip> completedTrips;
    private final Map<String, Double> arrivedAtSeconds;
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
        this.flowRecorder = new FlowRecorder();
        this.status = SimulationStatus.READY;
        this.vehicles = new ArrayList<>();
        this.events = new ArrayList<>();
        this.signals = new ArrayList<>(SignalPlan.defaultSignals(network));
        this.completedTrips = new ArrayList<>();
        this.arrivedAtSeconds = new LinkedHashMap<>();
        this.vehicleConfigs = new ArrayList<>();
        this.eventConfigs = new ArrayList<>();
        this.simulationSecondsPerTick = TickProcessor.DEFAULT_SIMULATION_SECONDS_PER_TICK;
        this.frameHistory = new SimulationFrameHistory();
        resetFrameHistory();
    }

    public synchronized void initialize(List<VehicleConfig> vehicleConfigs, List<EventConfig> eventConfigs) {
        resolveActiveEvents();
        releaseVehicleOccupancy();
        this.vehicleConfigs = new ArrayList<>(vehicleConfigs);
        this.eventConfigs = new ArrayList<>(eventConfigs);
        this.vehicles = buildVehicles(this.vehicleConfigs);
        this.events = buildEvents(this.eventConfigs);
        this.signals = new ArrayList<>(SignalPlan.defaultSignals(network));
        this.completedTrips.clear();
        this.arrivedAtSeconds.clear();
        this.clock.reset();
        this.status = SimulationStatus.READY;
        this.vehicleTraceStore.reset(clock.getCurrentTick(), vehicles);
        this.flowRecorder.reset();
        resetFrameHistory();
    }

    public synchronized void start() {
        if (status != SimulationStatus.READY) {
            throw new IllegalStateException("Cannot start: simulation is " + status);
        }
        status = SimulationStatus.RUNNING;
        recordCurrentFrame();
    }

    public synchronized void pause() {
        if (status != SimulationStatus.RUNNING) {
            throw new IllegalStateException("Cannot pause: simulation is " + status);
        }
        status = SimulationStatus.PAUSED;
        recordCurrentFrame();
    }

    public synchronized void resume() {
        if (status != SimulationStatus.PAUSED) {
            throw new IllegalStateException("Cannot resume: simulation is " + status);
        }
        status = SimulationStatus.RUNNING;
        recordCurrentFrame();
    }

    public synchronized void reset() {
        resolveActiveEvents();
        releaseVehicleOccupancy();
        this.vehicles = buildVehicles(this.vehicleConfigs);
        this.events = buildEvents(this.eventConfigs);
        this.signals = new ArrayList<>(SignalPlan.defaultSignals(network));
        this.completedTrips.clear();
        this.arrivedAtSeconds.clear();
        this.clock.reset();
        this.status = SimulationStatus.READY;
        this.vehicleTraceStore.reset(clock.getCurrentTick(), vehicles);
        this.flowRecorder.reset();
        resetFrameHistory();
    }

    public synchronized void advanceTick() {
        removeExpiredArrivals();
        tickProcessor.process(clock.getCurrentTick(), clock.getElapsedSeconds(),
                network, vehicles, events, signals, simulationSecondsPerTick,
                elapsedSeconds -> frameHistory.record(
                        clock.getCurrentTick(), elapsedSeconds, status,
                        network, vehicles, events, signals));
        clock.tick(simulationSecondsPerTick);
        vehicleTraceStore.record(clock.getCurrentTick(), vehicles);
        flowRecorder.record(clock.getCurrentTick(), clock.getElapsedSeconds(), network, vehicles);
        rememberNewArrivals();
        recordCurrentFrame();
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

    public synchronized double getElapsedSeconds() {
        return clock.getElapsedSeconds();
    }

    public synchronized List<Vehicle> getVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    public synchronized List<TrafficEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public synchronized List<TrafficSignal> getSignals() {
        return List.copyOf(signals);
    }

    public synchronized void setSignals(List<TrafficSignal> signals) {
        this.signals = signals == null ? new ArrayList<>() : new ArrayList<>(signals);
        recordCurrentFrame();
    }

    public synchronized List<CompletedTrip> getCompletedTrips() {
        return List.copyOf(completedTrips);
    }

    public synchronized SimulationSnapshot getSnapshot() {
        return new SimulationSnapshot(clock.getCurrentTick(), clock.getElapsedSeconds(),
                status, vehicles, events, signals);
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

    public synchronized SimulationTimeline getFrameTimeline() {
        return frameHistory.timeline();
    }

    public synchronized Optional<SimulationFrame> getFrameAtOrBefore(double elapsedSeconds) {
        return frameHistory.frameAtOrBefore(elapsedSeconds);
    }

    public synchronized SimulationFrame getCurrentFrame() {
        return frameHistory.currentFrame()
                .orElseThrow(() -> new IllegalStateException("Simulation frame history is empty."));
    }

    /** Starts a new replay timeline after an in-place network edit. */
    public synchronized void resetFrameTimeline() {
        resetFrameHistory();
    }

    public synchronized List<VehicleTracePoint> getVehicleTrace(
            String vehicleId,
            long fromTick,
            long toTick) {
        return vehicleTraceStore.getVehicleTrace(vehicleId, fromTick, toTick);
    }

    public synchronized List<RoadFlowSample> getRoadFlowSamples() {
        return flowRecorder.getSamples();
    }

    public synchronized List<FundamentalDiagramPoint> getFundamentalDiagram() {
        return new FundamentalDiagram(flowRecorder.getSamples()).points();
    }

    public synchronized TimeSpaceDiagram getTimeSpaceDiagram(String roadId) {
        team.traffic.domain.network.Road road = network.getRoad(roadId);
        if (road == null) throw new IllegalArgumentException("Road not found: " + roadId);
        return TimeSpaceDiagram.from(road, vehicleTraceStore);
    }

    public synchronized void addEvent(TrafficEvent event) {
        if (event != null && events.stream().noneMatch(existing -> existing.getId().equals(event.getId()))) {
            events.add(event);
            recordCurrentFrame();
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
            tickProcessor.resolveEvent(event, network, vehicles, events);
        }
        boolean removed = events.remove(event);
        if (removed) recordCurrentFrame();
        return removed;
    }

    public synchronized TrafficEvent updateEventStatus(String eventId, TrafficEventStatus newStatus) {
        TrafficEvent event = events.stream()
                .filter(candidate -> candidate.getId().equals(eventId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        if (newStatus == null) throw new IllegalArgumentException("status is required");
        if (event.getStatus() == newStatus) return event;
        switch (newStatus) {
            case ACTIVE -> {
                if (event.getStatus() != TrafficEventStatus.SCHEDULED) {
                    throw new IllegalStateException("Only a SCHEDULED event can be activated.");
                }
                tickProcessor.activateEvent(event, network, vehicles);
            }
            case RESOLVED -> {
                tickProcessor.resolveEvent(event, network, vehicles, events);
            }
            case SCHEDULED -> throw new IllegalStateException("An event cannot be moved back to SCHEDULED.");
        }
        recordCurrentFrame();
        return event;
    }

    public synchronized void addVehicle(Vehicle vehicle) {
        if (vehicle != null && vehicles.stream().noneMatch(existing -> existing.getId().equals(vehicle.getId()))) {
            vehicle.requestRoute(network, routingService);
            if (vehicle instanceof Bus bus) {
                configureDefaultBusStops(bus);
            }
            vehicles.add(vehicle);
            recordCurrentFrame();
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
        arrivedAtSeconds.remove(vehicleId);
        boolean removed = vehicles.remove(vehicle);
        if (removed) recordCurrentFrame();
        return removed;
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
        signals = new ArrayList<>(SignalPlan.defaultSignals(network));
        completedTrips.clear();
        arrivedAtSeconds.clear();
        vehicleConfigs = new ArrayList<>();
        eventConfigs = new ArrayList<>();
        clock.reset();
        status = SimulationStatus.READY;
        vehicleTraceStore.reset(clock.getCurrentTick(), vehicles);
        flowRecorder.reset();
        resetFrameHistory();
    }

    private void rememberNewArrivals() {
        for (Vehicle vehicle : vehicles) {
            if (vehicle.hasReachedDestination()
                    || vehicle.getStatus() == team.traffic.domain.vehicle.VehicleStatus.ARRIVED) {
                arrivedAtSeconds.putIfAbsent(vehicle.getId(), clock.getElapsedSeconds());
            }
        }
    }

    private void removeExpiredArrivals() {
        List<String> expired = arrivedAtSeconds.entrySet().stream()
                .filter(entry -> clock.getElapsedSeconds() - entry.getValue() >= ARRIVED_RETENTION_SECONDS)
                .map(Map.Entry::getKey)
                .toList();
        expired.forEach(this::removeVehicle);
    }

    private void resetFrameHistory() {
        frameHistory.reset(clock.getCurrentTick(), clock.getElapsedSeconds(), status,
                network, vehicles, events, signals);
    }

    private void recordCurrentFrame() {
        frameHistory.record(clock.getCurrentTick(), clock.getElapsedSeconds(), status,
                network, vehicles, events, signals);
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
     * @param startTimeSeconds elapsed simulation seconds when the event starts
     * @param durationSeconds how many simulated seconds the event lasts
     * @param value           type-specific value:
     *                        congestion increase for CONGESTION (0..1),
     *                        capacity reduction for ACCIDENT (0..1),
     *                        ignored for ROAD_CLOSURE. Uses defaults when null.
     * @param causingVehicleId required only for ACCIDENT events, defaults to "system"
     * @return the created event
     * @throws IllegalArgumentException if parameters are invalid
     */
    public TrafficEvent createEvent(
            TrafficEventType type,
            String affectedRoadId,
            double startTimeSeconds,
            double durationSeconds,
            Double value,
            String causingVehicleId) {

        if (type == null) {
            throw new IllegalArgumentException("Event type must not be null.");
        }
        if (affectedRoadId == null || affectedRoadId.isBlank()) {
            throw new IllegalArgumentException("Affected road id must not be blank.");
        }

        return createEventWithId(null, type, affectedRoadId, startTimeSeconds,
                durationSeconds, value, causingVehicleId, 0.5, java.util.Set.of());
    }

    private TrafficEvent createEventWithId(
            String id,
            TrafficEventType type,
            String affectedRoadId,
            double startTimeSeconds,
            double durationSeconds,
            Double value,
            String causingVehicleId,
            double positionAlongRoad,
            java.util.Set<Integer> blockedLaneIndexes) {
        String eventId = id != null && !id.isBlank()
                ? id
                : "EV-" + UUID.randomUUID().toString().substring(0, 8);

        return switch (type) {
            case CONGESTION -> new CongestionEvent(
                    eventId,
                    affectedRoadId,
                    startTimeSeconds,
                    durationSeconds,
                    value != null ? value : 0.5);
            case ACCIDENT -> new AccidentEvent(
                    eventId,
                    affectedRoadId,
                    startTimeSeconds,
                    durationSeconds,
                    value != null ? value : 0.5,
                    causingVehicleId != null && !causingVehicleId.isBlank() ? causingVehicleId : "system",
                    positionAlongRoad,
                    blockedLaneIndexes);
            case BREAKDOWN -> new BreakdownEvent(
                    eventId,
                    affectedRoadId,
                    startTimeSeconds,
                    durationSeconds,
                    causingVehicleId != null && !causingVehicleId.isBlank() ? causingVehicleId : "system",
                    positionAlongRoad,
                    blockedLaneIndexes);
            case ROAD_CLOSURE -> new RoadClosureEvent(
                    eventId,
                    affectedRoadId,
                    startTimeSeconds,
                    durationSeconds);
        };
    }

    /**
     * Convenience overload that creates an event with default values.
     */
    public TrafficEvent createEvent(
            TrafficEventType type,
            String affectedRoadId,
            double startTimeSeconds,
            double durationSeconds) {
        return createEvent(type, affectedRoadId, startTimeSeconds, durationSeconds, null, null);
    }

    private void resolveActiveEvents() {
        for (TrafficEvent event : events) {
            if (event.getStatus() == TrafficEventStatus.ACTIVE) {
                try {
                    tickProcessor.resolveEvent(event, network, vehicles, events);
                } catch (Exception ignored) {
                }
            }
        }
        tickProcessor.resetEventVehicleEffects();
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
                if (vehicle instanceof Bus bus) {
                    configureDefaultBusStops(bus);
                }
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
        return vehicleFactory.createVehicle(
                type,
                config.id(),
                config.startIntersectionId(),
                config.destinationIntersectionId());
    }

    private void configureDefaultBusStops(Bus bus) {
        bus.configureDefaultStopsFromRoute();
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
                config.startTimeSeconds(),
                config.durationSeconds(),
                config.value(),
                config.causingVehicleId(),
                config.positionAlongRoad() == null ? 0.5 : config.positionAlongRoad(),
                java.util.Set.copyOf(config.blockedLaneIndexes()));
    }
}
