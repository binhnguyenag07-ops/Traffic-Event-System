package team.traffic.simulation;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.BreakdownEvent;
import team.traffic.domain.event.TrafficEventCoordinator;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.domain.vehicle.dynamics.Collision;
import team.traffic.domain.vehicle.dynamics.CollisionDetector;
import team.traffic.domain.signal.TrafficSignal;
import team.traffic.service.RoutingService;
import team.traffic.observer.TrafficEventBus;
import team.traffic.simulation.navigation.AdaptiveNavigationCoordinator;
import team.traffic.simulation.navigation.AdaptiveNavigationMetrics;
import team.traffic.simulation.navigation.TrafficInformationMetrics;
import team.traffic.simulation.deadlock.DeadlockCycle;
import team.traffic.simulation.deadlock.DeadlockMetrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;

public class TickProcessor {
    /**
     * Simulation time advanced per tick.
     *
     * <p>This is a rendering constraint as much as a modelling one, and it was
     * set as though it were only the latter. At ten seconds a vehicle at the
     * 40 km/h limit covers 111 m between two consecutive frames — further than
     * the length of the junction it is crossing. Measured on the four-way test
     * case, not one of 22 sampled vehicle-frames caught a vehicle inside a
     * junction: they passed clean through between observations. Nothing
     * downstream could then draw a turn, because there was no turn recorded to
     * draw, and the renderer was left to bridge a right-angle corner and 111 m
     * of road in a single interpolation.</p>
     *
     * <p>Half a second puts that step at about 5.5 m, comfortably shorter than
     * a junction is wide, so a crossing is observed several times while it
     * happens. The cost is more ticks for the same simulated hour; the
     * alternative is a lane-level view that cannot show a vehicle turning.</p>
     */
    public static final double DEFAULT_SIMULATION_SECONDS_PER_TICK = 0.5;
    private static final double TIME_EPSILON_SECONDS = 1e-9;
    private final RoutingService routingService;
    private final TrafficEventBus eventBus;
    private final VehicleDynamicsEngine dynamicsEngine;
    private final CollisionDetector collisionDetector;
    private final AdaptiveNavigationCoordinator navigationCoordinator;
    private final TrafficEventCoordinator eventCoordinator = new TrafficEventCoordinator();
    private final Map<String, Set<String>> stoppedVehicleIdsByEventId = new HashMap<>();

    public TickProcessor(RoutingService routingService) {
        this(routingService, null);
    }

    public TickProcessor(RoutingService routingService, TrafficEventBus eventBus) {
        this(routingService, eventBus, new VehicleDynamicsEngine(),
                AdaptiveTrafficConfig.disabled());
    }

    /** Creates a processor with a specific vehicle-dynamics engine. */
    public TickProcessor(
            RoutingService routingService,
            TrafficEventBus eventBus,
            VehicleDynamicsEngine dynamicsEngine) {
        this(routingService, eventBus, dynamicsEngine, AdaptiveTrafficConfig.disabled());
    }

    public TickProcessor(
            RoutingService routingService,
            TrafficEventBus eventBus,
            VehicleDynamicsEngine dynamicsEngine,
            AdaptiveTrafficConfig adaptiveTrafficConfig) {
        this(routingService, eventBus, dynamicsEngine, adaptiveTrafficConfig, 0L);
    }

    public TickProcessor(
            RoutingService routingService,
            TrafficEventBus eventBus,
            VehicleDynamicsEngine dynamicsEngine,
            AdaptiveTrafficConfig adaptiveTrafficConfig,
            long scenarioSeed) {
        if (dynamicsEngine == null) {
            throw new IllegalArgumentException("Vehicle dynamics engine is required.");
        }
        this.routingService = routingService;
        this.eventBus = eventBus;
        this.dynamicsEngine = dynamicsEngine;
        this.dynamicsEngine.configureDeadlock(adaptiveTrafficConfig);
        this.collisionDetector = new CollisionDetector();
        this.navigationCoordinator = new AdaptiveNavigationCoordinator(
                adaptiveTrafficConfig, scenarioSeed);
    }

    public void process(long currentTick, RoadNetwork network, List<Vehicle> vehicles, List<TrafficEvent> events) {
        process(currentTick, currentTick * DEFAULT_SIMULATION_SECONDS_PER_TICK,
                network, vehicles, events, DEFAULT_SIMULATION_SECONDS_PER_TICK);
    }

    public void process(
            long currentTick,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            double simulationSecondsPerTick) {
        process(currentTick, currentTick * simulationSecondsPerTick,
                network, vehicles, events, simulationSecondsPerTick);
    }

    public void process(
            long currentTick,
            double elapsedSeconds,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            double simulationSecondsPerTick) {
        process(currentTick, elapsedSeconds, network, vehicles, events,
                List.of(), simulationSecondsPerTick);
    }

    public void process(
            long currentTick,
            double elapsedSeconds,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            List<TrafficSignal> signals,
            double simulationSecondsPerTick) {
        process(currentTick, elapsedSeconds, network, vehicles, events, signals,
                simulationSecondsPerTick, ignoredSeconds -> {});
    }

    /**
     * Processes one tick and reports every authoritative replay boundary.
     * Boundaries include physical sub-steps plus event activation/resolution,
     * allowing callers to replace a frame at the same timestamp with the final
     * post-transition state.
     */
    public void process(
            long currentTick,
            double elapsedSeconds,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            List<TrafficSignal> signals,
            double simulationSecondsPerTick,
            DoubleConsumer frameObserver) {
        if (frameObserver == null) {
            throw new IllegalArgumentException("Frame observer is required.");
        }
        List<Vehicle> movable = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (vehicle.getStatus() == VehicleStatus.DWELLING) {
                vehicle.addTravelTime(simulationSecondsPerTick);
                continue;
            }
            if (vehicle.isJourneyComplete()) {
                continue;
            }
            vehicle.addTravelTime(simulationSecondsPerTick);
            movable.add(vehicle);
        }

        double tickEndSeconds = elapsedSeconds + simulationSecondsPerTick;
        double cursorSeconds = elapsedSeconds;
        if (transitionEventsAt(cursorSeconds, network, vehicles, events)) {
            frameObserver.accept(cursorSeconds);
        }

        while (cursorSeconds < tickEndSeconds - TIME_EPSILON_SECONDS) {
            if (detectCollisions(network, vehicles, events, cursorSeconds)) {
                frameObserver.accept(cursorSeconds);
            }
            double boundarySeconds = nextEventBoundary(events, cursorSeconds, tickEndSeconds);
            double stepSeconds = boundarySeconds - cursorSeconds;
            if (stepSeconds > TIME_EPSILON_SECONDS) {
                moveVehicles(currentTick, cursorSeconds, network, movable, events, signals,
                        stepSeconds, frameObserver);
            }
            cursorSeconds = boundarySeconds;
            if (transitionEventsAt(cursorSeconds, network, vehicles, events)) {
                frameObserver.accept(cursorSeconds);
            }
        }

        if (!navigationCoordinator.isEnabled()) {
            for (Vehicle vehicle : movable) {
                if (vehicle.getStatus() == VehicleStatus.BLOCKED) {
                    vehicle.recomputeRoute(network, routingService);
                }
            }
        }
    }

    /** Applies and resolves event overlays at their exact simulated-second boundary. */
    private boolean transitionEventsAt(
            double elapsedSeconds,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events) {
        boolean transitioned = false;
        for (TrafficEvent event : events) {
            if (event.getStatus() == TrafficEventStatus.ACTIVE
                    && eventEndSeconds(event) <= elapsedSeconds + TIME_EPSILON_SECONDS) {
                resolveEvent(event, network, vehicles, events);
                publish(event);
                transitioned = true;
            }
        }
        for (TrafficEvent event : events) {
            if (event.getStatus() != TrafficEventStatus.SCHEDULED) continue;
            if (eventEndSeconds(event) <= elapsedSeconds + TIME_EPSILON_SECONDS) {
                // A configuration loaded after its whole time window should not
                // receive a full-tick effect merely because the scheduler is late.
                event.resolve(network);
                publish(event);
                transitioned = true;
            } else if (event.getStartTimeSeconds() <= elapsedSeconds + TIME_EPSILON_SECONDS) {
                activateEvent(event, network, vehicles);
                publish(event);
                transitioned = true;
            }
        }
        return transitioned;
    }

    private static double nextEventBoundary(
            List<TrafficEvent> events,
            double cursorSeconds,
            double tickEndSeconds) {
        double boundary = tickEndSeconds;
        for (TrafficEvent event : events) {
            double candidate = switch (event.getStatus()) {
                case ACTIVE -> eventEndSeconds(event);
                case SCHEDULED -> event.getStartTimeSeconds();
                case RESOLVED -> Double.POSITIVE_INFINITY;
            };
            if (candidate > cursorSeconds + TIME_EPSILON_SECONDS && candidate < boundary) {
                boundary = candidate;
            }
        }
        return boundary;
    }

    private static double eventEndSeconds(TrafficEvent event) {
        return event.getStartTimeSeconds() + event.getDurationSeconds();
    }

    private void moveVehicles(
            long currentTick,
            double elapsedSeconds,
            RoadNetwork network,
            List<Vehicle> movable,
            List<TrafficEvent> events,
            List<TrafficSignal> signals,
            double simulationSecondsPerTick,
            DoubleConsumer frameObserver) {
        dynamicsEngine.step(currentTick, elapsedSeconds,
                network, movable, events, signals, simulationSecondsPerTick,
                (stepEndSeconds, subStepState) -> {
                    publishPhysicalDensity(network, subStepState);
                    navigationCoordinator.process(
                            stepEndSeconds, network, movable, events, subStepState);
                    frameObserver.accept(stepEndSeconds);
                });
    }

    public AdaptiveNavigationMetrics getAdaptiveNavigationMetrics() {
        return navigationCoordinator.metrics();
    }

    public TrafficInformationMetrics getTrafficInformationMetrics() {
        return navigationCoordinator.informationMetrics();
    }

    public DeadlockMetrics getDeadlockMetrics() {
        return dynamicsEngine.getDeadlockMetrics();
    }

    public List<DeadlockCycle> getDeadlockCycles() {
        return dynamicsEngine.getDeadlockCycles();
    }

    public SimulationDiagnostics getDiagnostics() {
        return getDiagnostics(List.of());
    }

    public SimulationDiagnostics getDiagnostics(List<Vehicle> vehicles) {
        return getDiagnostics(vehicles, Map.of());
    }

    public SimulationDiagnostics getDiagnostics(
            List<Vehicle> vehicles,
            Map<team.traffic.domain.vehicle.dynamics.WaitCause, Double>
                    priorWaitingSecondsByCause) {
        return SimulationDiagnostics.from(
                getAdaptiveNavigationMetrics(),
                getTrafficInformationMetrics(),
                getDeadlockMetrics(),
                getDeadlockCycles(),
                vehicles,
                priorWaitingSecondsByCause);
    }

    /**
     * Republishes each road's density from the physical space its vehicles take
     * up, replacing the plain vehicle-count ratio the road maintains itself.
     *
     * <p>Routing cost, the statistics panel, and the map colouring all read
     * {@code vehicleDensity}, so this is what lets the rest of the system see a
     * road as full of buses rather than merely holding a few vehicles. The road
     * recomputes the count-based value whenever a vehicle enters or leaves, so
     * this runs once after all movement for the tick is done.</p>
     */
    private void publishPhysicalDensity(RoadNetwork network, TrafficState state) {
        if (state == null) {
            return;
        }
        // Empty roads already read zero from the road's own count, so only the
        // occupied ones need republishing.
        for (String roadId : state.getOccupiedRoadIds()) {
            Road road = network.getRoad(roadId);
            if (road != null) {
                road.getCondition().setVehicleDensity(state.physicalJamRatioOf(roadId));
            }
        }
    }

    private void publish(TrafficEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    private boolean detectCollisions(
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            double elapsedSeconds) {
        boolean detected = false;
        for (Collision collision : collisionDetector.detect(network, vehicles)) {
            boolean alreadyReported = events.stream()
                    .filter(AccidentEvent.class::isInstance)
                    .map(AccidentEvent.class::cast)
                    .anyMatch(event -> event.getCausingVehicleId().equals(collision.causingVehicleId())
                            && event.getStatus() != TrafficEventStatus.RESOLVED);
            if (alreadyReported) continue;
            AccidentEvent event = new AccidentEvent(
                    "collision-" + Long.toUnsignedString(Double.doubleToLongBits(elapsedSeconds), 36)
                            + "-" + collision.causingVehicleId(),
                    collision.roadId(), elapsedSeconds, 300.0, 0.8,
                    collision.causingVehicleId(), collision.positionAlongRoad(),
                    java.util.Set.of(collision.laneIndex()),
                    java.util.Set.of(collision.causingVehicleId(), collision.impactedVehicleId()));
            events.add(event);
            activateEvent(event, network, vehicles);
            publish(event);
            detected = true;
        }
        return detected;
    }

    void onEventActivated(TrafficEvent event, List<Vehicle> vehicles) {
        java.util.LinkedHashSet<String> affectedIds = new java.util.LinkedHashSet<>();
        if (event instanceof BreakdownEvent breakdown) {
            vehicles.stream()
                    .filter(vehicle -> vehicle.getId().equals(breakdown.getAffectedVehicleId()))
                    .filter(vehicle -> breakdown.getAffectedRoadId().equals(vehicle.getOccupiedRoadId()))
                    .map(Vehicle::getId)
                    .forEach(affectedIds::add);
        }
        if (event instanceof AccidentEvent accident) {
            vehicles.stream()
                    .filter(vehicle -> accident.getInvolvedVehicleIds().contains(vehicle.getId()))
                    .filter(vehicle -> accident.getAffectedRoadId().equals(vehicle.getOccupiedRoadId()))
                    .map(Vehicle::getId)
                    .forEach(affectedIds::add);
        }
        if (!affectedIds.isEmpty()) {
            stoppedVehicleIdsByEventId.put(event.getId(), Set.copyOf(affectedIds));
            vehicles.stream()
                    .filter(vehicle -> affectedIds.contains(vehicle.getId()))
                    .forEach(Vehicle::stop);
        }
    }

    void onEventResolved(TrafficEvent event, List<Vehicle> vehicles) {
        Set<String> releasedIds = stoppedVehicleIdsByEventId.remove(event.getId());
        if (releasedIds == null || releasedIds.isEmpty()) return;
        vehicles.stream()
                .filter(vehicle -> releasedIds.contains(vehicle.getId()))
                .filter(vehicle -> stoppedVehicleIdsByEventId.values().stream()
                        .noneMatch(ids -> ids.contains(vehicle.getId())))
                .forEach(Vehicle::resumeFromStop);
    }

    void resetEventVehicleEffects() {
        stoppedVehicleIdsByEventId.clear();
        eventCoordinator.reset();
    }

    void activateEvent(TrafficEvent event, RoadNetwork network, List<Vehicle> vehicles) {
        eventCoordinator.activate(event, network);
        onEventActivated(event, vehicles);
    }

    void resolveEvent(
            TrafficEvent event,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> allEvents) {
        eventCoordinator.resolve(event, network, allEvents);
        onEventResolved(event, vehicles);
    }
}
