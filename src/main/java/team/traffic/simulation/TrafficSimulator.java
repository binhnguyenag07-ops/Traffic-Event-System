package team.traffic.simulation;

import java.util.UUID;
import team.traffic.domain.demand.DemandScenario;
import team.traffic.domain.demand.GravityDemandModel;
import team.traffic.domain.demand.TripDemandModel;
import team.traffic.domain.demand.TripPlan;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.CongestionEvent;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.event.TrafficEventType;
import team.traffic.domain.event.BreakdownEvent;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
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
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.EnumMap;
import java.util.Optional;
import java.util.Set;
import team.traffic.domain.vehicle.navigation.DriverDecisionProfile;
import team.traffic.domain.vehicle.dynamics.WaitCause;

public class TrafficSimulator {
    public static final double ARRIVED_RETENTION_SECONDS = 5.0;
    private static final int MAX_INTERMEDIATE_FRAMES_PER_TICK = 3;
    private static final double FRAME_TIME_EPSILON_SECONDS = 1e-9;
    /** Warm-up keeps vehicle dynamics responsive and bounds departure admission delay to one second. */
    private static final double WARM_UP_BATCH_SECONDS = 1.0;
    private RoadNetwork network;
    private final RoutingService routingService;
    private final TrafficEventBus eventBus;
    private final VehicleFactory vehicleFactory;
    private TickProcessor tickProcessor;
    private AdaptiveTrafficConfig adaptiveTrafficConfig;
    private long adaptiveScenarioSeed;
    private boolean automaticCollisionEvents = true;
    private SimulationClock clock;
    private final VehicleTraceStore vehicleTraceStore;
    private final FlowRecorder flowRecorder;
    private final SimulationFrameHistory frameHistory;

    private SimulationStatus status;
    private List<Vehicle> vehicles;
    private final Set<String> vehicleIds;
    private List<TrafficEvent> events;
    private List<TrafficSignal> signals;
    private final List<CompletedTrip> completedTrips;
    private final Map<String, Double> arrivedAtSeconds;
    private final Map<WaitCause, Double> completedWaitingSecondsByCause;
    private List<VehicleConfig> vehicleConfigs;
    private List<EventConfig> eventConfigs;
    private TripDemandModel demandModel;
    private double demandStartTimeOfDaySeconds =
            SimulationClock.DEFAULT_START_TIME_OF_DAY_SECONDS;
    private double demandWarmUpSeconds;
    private DemandDrivenSpawner demandSpawner;
    private double simulationSecondsPerTick;

    public TrafficSimulator(RoadNetwork network, RoutingService routingService) {
        this(network, routingService, null);
    }

    public TrafficSimulator(RoadNetwork network, RoutingService routingService, TrafficEventBus eventBus) {
        this(network, routingService, eventBus, AdaptiveTrafficConfig.disabled());
    }

    public TrafficSimulator(
            RoadNetwork network,
            RoutingService routingService,
            TrafficEventBus eventBus,
            AdaptiveTrafficConfig adaptiveTrafficConfig) {
        this.network = network;
        this.routingService = routingService;
        this.eventBus = eventBus;
        this.vehicleFactory = new VehicleFactory();
        configureTickProcessor(adaptiveTrafficConfig);
        this.clock = new SimulationClock();
        this.vehicleTraceStore = new VehicleTraceStore();
        this.flowRecorder = new FlowRecorder();
        this.status = SimulationStatus.READY;
        this.vehicles = new ArrayList<>();
        this.vehicleIds = new HashSet<>();
        this.events = new ArrayList<>();
        this.signals = new ArrayList<>(SignalPlan.defaultSignals(network));
        this.completedTrips = new ArrayList<>();
        this.arrivedAtSeconds = new LinkedHashMap<>();
        this.completedWaitingSecondsByCause = new EnumMap<>(WaitCause.class);
        this.vehicleConfigs = new ArrayList<>();
        this.eventConfigs = new ArrayList<>();
        this.simulationSecondsPerTick = TickProcessor.DEFAULT_SIMULATION_SECONDS_PER_TICK;
        this.frameHistory = new SimulationFrameHistory();
        resetFrameHistory();
    }

    public synchronized void configureAdaptiveTraffic(AdaptiveTrafficConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Adaptive traffic config is required.");
        }
        if (status != SimulationStatus.READY) {
            throw new IllegalStateException(
                    "Adaptive traffic config can change only while the simulation is ready.");
        }
        configureTickProcessor(config, adaptiveScenarioSeed);
        if (vehicles != null) {
            vehicles.forEach(vehicleFactory::configureVehicle);
        }
    }

    public synchronized AdaptiveTrafficConfig getAdaptiveTrafficConfig() {
        return adaptiveTrafficConfig;
    }

    public synchronized void configureDriverDecisionProfiles(
            Map<VehicleType, DriverDecisionProfile> profiles) {
        if (status != SimulationStatus.READY) {
            throw new IllegalStateException(
                    "Driver profiles can change only while the simulation is ready.");
        }
        vehicleFactory.setDecisionProfiles(profiles);
        if (vehicles != null) {
            vehicles.forEach(vehicleFactory::configureVehicle);
        }
    }

    private void configureTickProcessor(AdaptiveTrafficConfig config) {
        configureTickProcessor(config, 0L);
    }

    private void configureTickProcessor(
            AdaptiveTrafficConfig config,
            long scenarioSeed) {
        adaptiveTrafficConfig = config == null
                ? AdaptiveTrafficConfig.disabled()
                : config;
        adaptiveScenarioSeed = scenarioSeed;
        network.getMovementGraph().setUTurnPolicy(
                adaptiveTrafficConfig.uTurns().networkPolicy());
        network.getMovementGraph().setInferredUTurnPenaltySeconds(
                adaptiveTrafficConfig.uTurns().defaultPenaltySeconds());
        vehicleFactory.setScenarioSeed(scenarioSeed);
        vehicleFactory.setStallTracking(
                adaptiveTrafficConfig.deadlock().minimumProgressMetres(),
                adaptiveTrafficConfig.routing().retryBackoffSeconds());
        tickProcessor = new TickProcessor(
                routingService,
                eventBus,
                new VehicleDynamicsEngine(),
                adaptiveTrafficConfig,
                scenarioSeed,
                automaticCollisionEvents);
    }

    public synchronized void initialize(List<VehicleConfig> vehicleConfigs, List<EventConfig> eventConfigs) {
        automaticCollisionEvents = true;
        configureTickProcessor(adaptiveTrafficConfig, 0L);
        initializeRun(
                vehicleConfigs,
                eventConfigs,
                null,
                SimulationClock.DEFAULT_START_TIME_OF_DAY_SECONDS,
                0);
    }

    public synchronized void initialize(
            DemandScenario demandScenario,
            List<VehicleConfig> vehicleConfigs,
            List<EventConfig> eventConfigs) {
        if (demandScenario == null) throw new IllegalArgumentException("Demand scenario is required");
        TripDemandModel nextDemandModel = new GravityDemandModel(network, demandScenario);
        automaticCollisionEvents = demandScenario.automaticCollisionEvents();
        configureTickProcessor(adaptiveTrafficConfig, demandScenario.seed());
        initializeRun(
                vehicleConfigs,
                eventConfigs,
                nextDemandModel,
                demandScenario.startTimeOfDaySeconds(),
                demandScenario.warmUpSeconds());
    }

    /**
     * Initializes the same runtime from any resolved demand strategy.
     *
     * <p>This is the extension seam for scripted plans and future OD-matrix
     * models; the spawner and vehicle lifecycle remain unchanged.</p>
     */
    public synchronized void initialize(
            TripDemandModel demandModel,
            double startTimeOfDaySeconds,
            List<VehicleConfig> vehicleConfigs,
            List<EventConfig> eventConfigs) {
        if (demandModel == null) throw new IllegalArgumentException("Demand model is required");
        automaticCollisionEvents = true;
        configureTickProcessor(adaptiveTrafficConfig, 0L);
        initializeRun(vehicleConfigs, eventConfigs, demandModel, startTimeOfDaySeconds, 0);
    }

    private void initializeRun(
            List<VehicleConfig> vehicleConfigs,
            List<EventConfig> eventConfigs,
            TripDemandModel demandModel,
            double startTimeOfDaySeconds,
            double warmUpSeconds) {
        if (!Double.isFinite(warmUpSeconds) || warmUpSeconds < 0) {
            throw new IllegalArgumentException(
                    "Demand warm-up seconds must be finite and non-negative");
        }
        resolveActiveEvents();
        releaseVehicleOccupancy();
        resetLegacyDynamicRoadState();
        this.vehicleConfigs = vehicleConfigs == null ? new ArrayList<>() : new ArrayList<>(vehicleConfigs);
        this.eventConfigs = eventConfigs == null ? new ArrayList<>() : new ArrayList<>(eventConfigs);
        this.demandModel = demandModel;
        this.demandStartTimeOfDaySeconds = SimulationClock.timeOfDayAt(
                startTimeOfDaySeconds, 0);
        this.demandWarmUpSeconds = warmUpSeconds;
        this.clock = new SimulationClock(this.demandStartTimeOfDaySeconds);
        this.vehicles = buildVehicles(this.vehicleConfigs);
        rebuildVehicleIdIndex();
        this.events = buildEvents(this.eventConfigs);
        this.signals = new ArrayList<>(SignalPlan.defaultSignals(network));
        this.completedTrips.clear();
        this.arrivedAtSeconds.clear();
        this.completedWaitingSecondsByCause.clear();
        this.demandSpawner = demandModel == null
                ? null
                : new DemandDrivenSpawner(demandModel, network, routingService, vehicleFactory);
        spawnDemandThrough(0);
        advanceVehicleLifecycles(0);
        warmUpDemand();
        this.status = SimulationStatus.READY;
        // Warm-up is initialization, not replay history. Only its resulting
        // current state becomes frame/trace zero for the interactive session.
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
        resetLegacyDynamicRoadState();
        configureTickProcessor(adaptiveTrafficConfig, adaptiveScenarioSeed);
        this.clock = new SimulationClock(demandStartTimeOfDaySeconds);
        this.vehicles = buildVehicles(this.vehicleConfigs);
        rebuildVehicleIdIndex();
        this.events = buildEvents(this.eventConfigs);
        this.signals = new ArrayList<>(SignalPlan.defaultSignals(network));
        this.completedTrips.clear();
        this.arrivedAtSeconds.clear();
        this.completedWaitingSecondsByCause.clear();
        this.demandSpawner = demandModel == null
                ? null
                : new DemandDrivenSpawner(demandModel, network, routingService, vehicleFactory);
        spawnDemandThrough(0);
        advanceVehicleLifecycles(0);
        warmUpDemand();
        this.status = SimulationStatus.READY;
        this.vehicleTraceStore.reset(clock.getCurrentTick(), vehicles);
        this.flowRecorder.reset();
        resetFrameHistory();
    }

    public synchronized void advanceTick() {
        advanceVehicleLifecycles(clock.getElapsedSeconds());
        spawnDemandThrough(clock.getElapsedSeconds());
        removeExpiredArrivals();
        double tickStartSeconds = clock.getElapsedSeconds();
        double tickEndSeconds = tickStartSeconds + simulationSecondsPerTick;
        double intermediateSpacingSeconds = simulationSecondsPerTick
                / (MAX_INTERMEDIATE_FRAMES_PER_TICK + 1.0);
        double[] lastIntermediateSeconds = {tickStartSeconds};
        tickProcessor.process(clock.getCurrentTick(), clock.getElapsedSeconds(),
                network, vehicles, events, signals, simulationSecondsPerTick,
                (elapsedSeconds, boundaryKind) -> {
                    // The final post-lifecycle state is recorded once below.
                    // For large overview ticks, retaining every one-second
                    // physics sub-step copied the whole 730-road scene dozens
                    // of times per scheduler callback. Keep at most three
                    // intermediate replay points while preserving small-step
                    // physical-view detail.
                    boolean sampledPhysics = boundaryKind
                            == TickProcessor.FrameBoundaryKind.PHYSICS_SUBSTEP;
                    if (sampledPhysics
                            && (elapsedSeconds >= tickEndSeconds - FRAME_TIME_EPSILON_SECONDS
                            || elapsedSeconds - lastIntermediateSeconds[0]
                            < intermediateSpacingSeconds - FRAME_TIME_EPSILON_SECONDS)) {
                        return;
                    }
                    frameHistory.record(
                            clock.getCurrentTick(), elapsedSeconds, status,
                            network, vehicles, events, signals);
                    lastIntermediateSeconds[0] = elapsedSeconds;
                });
        clock.tick(simulationSecondsPerTick);
        advanceVehicleLifecycles(clock.getElapsedSeconds());
        spawnDemandThrough(clock.getElapsedSeconds());
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
        return List.copyOf(vehicles);
    }

    public synchronized List<TrafficEvent> getEvents() {
        return List.copyOf(events);
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

    public synchronized SimulationDiagnostics getDiagnostics() {
        return tickProcessor.getDiagnostics(
                vehicles, completedWaitingSecondsByCause);
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
        if (vehicle != null && !vehicleIds.contains(vehicle.getId())) {
            vehicleFactory.configureVehicle(vehicle);
            vehicle.requestRoute(network, routingService);
            if (vehicle instanceof Bus bus) {
                configureDefaultBusStops(bus);
            }
            vehicles.add(vehicle);
            vehicleIds.add(vehicle.getId());
            vehicle.updateTripPlanLifecycle(clock.getElapsedSeconds(), network, routingService);
            recordCurrentFrame();
        }
    }

    public synchronized boolean removeVehicle(String vehicleId) {
        return removeVehicleInternal(vehicleId, true);
    }

    private boolean removeVehicleInternal(String vehicleId, boolean recordFrame) {
        Vehicle vehicle = vehicles.stream()
                .filter(candidate -> candidate.getId().equals(vehicleId))
                .findFirst()
                .orElse(null);
        if (vehicle == null) {
            return false;
        }
        vehicle.releaseRoad(network);
        if (vehicle.isJourneyComplete()) {
            completedTrips.add(new CompletedTrip(
                    vehicle.getId(),
                    vehicle.getType(),
                    vehicle.getTravelTimeSeconds(),
                    vehicle.getDistanceTravelledKm(),
                    vehicle.getRouteRecalculationCount(),
                    vehicle.getTripPlan() == null ? null : vehicle.getTripPlan().id(),
                    vehicle.getTripPlan() == null
                            ? null : vehicle.getTripPlan().purpose().value()));
        }
        arrivedAtSeconds.remove(vehicleId);
        vehicle.getTrip().getWaitingSecondsByCause().forEach(
                (cause, seconds) -> completedWaitingSecondsByCause.merge(
                        cause, seconds, Double::sum));
        boolean removed = vehicles.remove(vehicle);
        if (removed) vehicleIds.remove(vehicleId);
        if (removed && recordFrame) recordCurrentFrame();
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
        automaticCollisionEvents = true;
        configureTickProcessor(adaptiveTrafficConfig, adaptiveScenarioSeed);
        vehicles = new ArrayList<>();
        vehicleIds.clear();
        events = new ArrayList<>();
        signals = new ArrayList<>(SignalPlan.defaultSignals(network));
        completedTrips.clear();
        arrivedAtSeconds.clear();
        completedWaitingSecondsByCause.clear();
        vehicleConfigs = new ArrayList<>();
        eventConfigs = new ArrayList<>();
        demandModel = null;
        demandStartTimeOfDaySeconds = SimulationClock.DEFAULT_START_TIME_OF_DAY_SECONDS;
        demandWarmUpSeconds = 0;
        demandSpawner = null;
        clock = new SimulationClock();
        status = SimulationStatus.READY;
        vehicleTraceStore.reset(clock.getCurrentTick(), vehicles);
        flowRecorder.reset();
        resetFrameHistory();
    }

    private void rememberNewArrivals() {
        for (Vehicle vehicle : vehicles) {
            if (vehicle.isJourneyComplete()) {
                arrivedAtSeconds.putIfAbsent(vehicle.getId(), clock.getElapsedSeconds());
            }
        }
    }

    private void advanceVehicleLifecycles(double elapsedSeconds) {
        for (Vehicle vehicle : vehicles) {
            boolean completed = vehicle.updateTripPlanLifecycle(elapsedSeconds, network, routingService);
            if (!completed) {
                arrivedAtSeconds.remove(vehicle.getId());
            }
        }
    }

    private void spawnDemandThrough(double elapsedSeconds) {
        if (demandSpawner == null) return;
        for (Vehicle vehicle : demandSpawner.spawnDueThrough(elapsedSeconds)) {
            if (!vehicleIds.add(vehicle.getId())) {
                continue;
            }
            if (vehicle instanceof Bus bus) configureDefaultBusStops(bus);
            vehicles.add(vehicle);
            vehicle.updateTripPlanLifecycle(elapsedSeconds, network, routingService);
        }
    }

    private void rebuildVehicleIdIndex() {
        vehicleIds.clear();
        for (Vehicle vehicle : vehicles) {
            if (!vehicleIds.add(vehicle.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate vehicle id in scenario: " + vehicle.getId());
            }
        }
    }

    /**
     * Advances the configured pre-roll without producing thousands of
     * renderer frames or trace snapshots.
     *
     * <p>This is not a "fast playback" mode. It is a one-time scenario
     * initialization step using the ordinary vehicle model and signal logic,
     * so the first interactive frame contains traffic that has already been
     * entering the district for several minutes. The live tick size is not
     * changed.</p>
     */
    private void warmUpDemand() {
        if (demandSpawner == null || demandWarmUpSeconds <= 0) return;
        while (clock.getElapsedSeconds() + FRAME_TIME_EPSILON_SECONDS
                < demandWarmUpSeconds) {
            double elapsedSeconds = clock.getElapsedSeconds();
            advanceVehicleLifecycles(elapsedSeconds);
            spawnDemandThrough(elapsedSeconds);
            removeExpiredArrivals();
            double stepSeconds = Math.min(
                    WARM_UP_BATCH_SECONDS,
                    demandWarmUpSeconds - elapsedSeconds);
            tickProcessor.process(
                    clock.getCurrentTick(),
                    elapsedSeconds,
                    network,
                    vehicles,
                    events,
                    signals,
                    stepSeconds,
                    (ignoredSeconds, ignoredBoundary) -> {
                        // Intentionally no history during pre-roll.
                    });
            clock.tick(stepSeconds);
            advanceVehicleLifecycles(clock.getElapsedSeconds());
            spawnDemandThrough(clock.getElapsedSeconds());
            rememberNewArrivals();
        }
        removeExpiredArrivals();
    }

    public synchronized DemandRunStatistics getDemandRunStatistics() {
        return demandSpawner == null ? DemandRunStatistics.empty() : demandSpawner.statistics();
    }

    /** The immutable pre-resolved demand schedule for audit and presentation. */
    public synchronized List<TripPlan> getDemandPlans() {
        return demandModel == null ? List.of() : demandModel.allPlans();
    }

    private void removeExpiredArrivals() {
        List<String> expired = arrivedAtSeconds.entrySet().stream()
                .filter(entry -> clock.getElapsedSeconds() - entry.getValue() >= ARRIVED_RETENTION_SECONDS)
                .map(Map.Entry::getKey)
                .toList();
        // advanceTick records one final authoritative frame after this batch.
        // Recording a complete network frame per expired vehicle produced a
        // large arrival-wave spike and every frame had the same timestamp.
        expired.forEach(vehicleId -> removeVehicleInternal(vehicleId, false));
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

    /**
     * Starts every run without persisted or stale runtime traffic.
     *
     * <p>Map files describe topology and explicit closures. Vehicle occupancy,
     * density and congestion are runtime state, so importing non-zero legacy
     * values would otherwise reserve capacity for vehicles that do not exist.
     * Explicitly closed roads remain closed.</p>
     */
    private void resetLegacyDynamicRoadState() {
        network.getRoads().values().forEach(road -> {
            boolean preserveClosure =
                    road.getCondition().getRoadStatus() == RoadStatus.CLOSED;
            road.setCurrentVehicleCount(0);
            road.setCondition(new RoadCondition(
                    0,
                    0,
                    preserveClosure ? RoadStatus.CLOSED : RoadStatus.OPEN));
        });
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
        TrafficEvent event = createEventWithId(
                config.id(),
                type,
                config.affectedRoadId(),
                config.startTimeSeconds(),
                config.durationSeconds(),
                config.value(),
                config.causingVehicleId(),
                config.positionAlongRoad() == null ? 0.5 : config.positionAlongRoad(),
                java.util.Set.copyOf(config.blockedLaneIndexes()));
        event.configurePublication(config.origin(), config.informationPolicy());
        return event;
    }

    /**
     * The run clock, for anything that needs the time of day rather than just
     * how long the simulation has been going.
     */
    public SimulationClock getClock() {
        return clock;
    }
}
