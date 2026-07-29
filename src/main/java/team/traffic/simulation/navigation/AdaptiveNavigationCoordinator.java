package team.traffic.simulation.navigation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.AdaptiveRouteRequest;
import team.traffic.domain.routing.RerouteReason;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteAnchor;
import team.traffic.domain.routing.RouteCandidate;
import team.traffic.domain.routing.RouteCost;
import team.traffic.domain.routing.RouteReplacementResult;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.dynamics.LaneChangeIntent;
import team.traffic.domain.vehicle.dynamics.LocalPerception;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.domain.vehicle.navigation.AdvisoryKind;
import team.traffic.domain.vehicle.navigation.RoadAdvisory;
import team.traffic.routing.AdaptiveRouter;
import team.traffic.routing.RoadTravelTimeEstimator;
import team.traffic.routing.RoutingSnapshot;
import team.traffic.simulation.AdaptiveTrafficConfig;

/** Batches deterministic strategic decisions against one immutable cost snapshot. */
public final class AdaptiveNavigationCoordinator {
    private final AdaptiveTrafficConfig config;
    private final long scenarioSeed;
    private final TrafficInformationService informationService;
    private final DriverDecisionPolicy decisionPolicy = new DriverDecisionPolicy();
    private final AdaptiveRouter router = new AdaptiveRouter();
    private final RoadTravelTimeEstimator travelTimeEstimator;
    private final Map<String, RouteCandidate> routeCache = new HashMap<>();
    private final Map<RerouteReason, Long> requestedByReason =
            new EnumMap<>(RerouteReason.class);
    private final Map<RerouteReason, Long> acceptedByReason =
            new EnumMap<>(RerouteReason.class);
    private final Map<RerouteReason, Long> rejectedByReason =
            new EnumMap<>(RerouteReason.class);
    private final Map<WaitCause, Long> stallEscalationsByCause =
            new EnumMap<>(WaitCause.class);
    private final Map<String, RoutePrediction> predictionsByVehicle = new HashMap<>();
    private final Map<String, Integer> observedUTurnCompletions = new HashMap<>();
    private double nextCadenceAtSeconds;
    private double routeCacheExpiresAtSeconds;
    private long requested;
    private long accepted;
    private long rejected;
    private long stranded;
    private long routeChurn;
    private long repeatedRouteRejections;
    private long stallEscalations;
    private long uTurnRequested;
    private long uTurnAccepted;
    private long uTurnCompleted;
    private long uTurnRejected;
    private long routeComputations;
    private long cacheHits;
    private long routingComputationNanos;
    private long predictionSamples;
    private double predictedRemainingSeconds;
    private double realizedRemainingSeconds;

    public AdaptiveNavigationCoordinator(AdaptiveTrafficConfig config, long scenarioSeed) {
        this.config = config == null ? AdaptiveTrafficConfig.disabled() : config;
        this.scenarioSeed = scenarioSeed;
        this.informationService = new TrafficInformationService(
                scenarioSeed,
                this.config.routing().maximumLookaheadRoads(),
                Math.max(
                        1.0,
                        Math.max(
                                this.config.routing().knowledgeCacheSeconds(),
                                this.config.strategicCadenceSeconds() * 2.0)));
        this.travelTimeEstimator = new RoadTravelTimeEstimator(
                this.config.congestion().travelTimeEmaHalfLifeSeconds());
    }

    public boolean isEnabled() {
        return config.enabled();
    }

    public void process(
            double simulationSeconds,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            team.traffic.domain.vehicle.dynamics.TrafficState postMovementState) {
        if (!config.enabled()) return;
        if (network == null || vehicles == null || events == null || postMovementState == null) {
            throw new IllegalArgumentException("Complete adaptive-navigation state is required.");
        }
        observeCompletions(vehicles, simulationSeconds);
        travelTimeEstimator.update(postMovementState);

        network.getMovementGraph().setUTurnPolicy(config.uTurns().networkPolicy());
        network.getMovementGraph().setInferredUTurnPenaltySeconds(
                config.uTurns().defaultPenaltySeconds());
        RoutingSnapshot snapshot = travelTimeEstimator.snapshot(
                network, events, simulationSeconds);
        informationService.updateCongestion(
                network, snapshot, simulationSeconds);
        List<Vehicle> orderedVehicles = vehicles.stream()
                .filter(vehicle -> vehicle.getStatus() != VehicleStatus.ARRIVED
                        && vehicle.getStatus() != VehicleStatus.DWELLING)
                .sorted(Comparator.comparing(Vehicle::getId))
                .toList();
        orderedVehicles.forEach(vehicle -> informationService.deliverDue(
                vehicle, network, events, simulationSeconds));

        boolean cadenceDue = simulationSeconds + 1e-9 >= nextCadenceAtSeconds;
        if (!cadenceDue && orderedVehicles.stream().noneMatch(
                vehicle -> vehicle.getStallTracker().retryDue(simulationSeconds)
                        && vehicle.getStallState().cause()
                                != team.traffic.domain.vehicle.dynamics.WaitCause.NONE)) {
            return;
        }
        if (cadenceDue) {
            nextCadenceAtSeconds = simulationSeconds + config.strategicCadenceSeconds();
        }
        if (simulationSeconds + 1e-9 >= routeCacheExpiresAtSeconds) {
            routeCache.clear();
            routeCacheExpiresAtSeconds = simulationSeconds
                    + config.routing().knowledgeCacheSeconds();
        }

        List<Request> requests = new ArrayList<>();
        for (Vehicle vehicle : orderedVehicles) {
            Optional<LocalPerception> local = vehicle.getLocalPerception();
            if (local.isEmpty()) continue;
            DriverContext context = new DriverContext(
                    local.get(),
                    vehicle.getDriverKnowledge(),
                    vehicle.getNavigationState(),
                    vehicle.getStallState(),
                    // The current policy does not inspect route cost until it
                    // actually asks for a reroute. Keep this cheap, meaningful
                    // context value here and construct the much larger
                    // driver-visible snapshot only for a real request below.
                    remainingCost(vehicle, network, snapshot),
                    vehicle.getDriverProfile(),
                    simulationSeconds);
            DriverDecision decision = decisionPolicy.decide(context);
            applyNonRoutingDecision(vehicle, decision);
            if (decision.kind() == DriverDecisionKind.REPORT_STRANDED) {
                if (vehicle.getNavigationState().getMode()
                        != team.traffic.domain.vehicle.navigation.NavigationMode.STRANDED) {
                    vehicle.getNavigationState().markStranded(decision.reason());
                    stranded++;
                    recordStallEscalation(context.stall().cause());
                }
            }
            if (decision.kind() == DriverDecisionKind.REQUEST_REROUTE
                    || decision.kind() == DriverDecisionKind.REQUEST_LEGAL_UTURN) {
                RerouteReason reason = decision.rerouteReason()
                        .orElse(RerouteReason.EXPECTED_DELAY);
                boolean uTurn = decision.kind()
                        == DriverDecisionKind.REQUEST_LEGAL_UTURN;
                if (reason == RerouteReason.STALL_TIMEOUT) {
                    recordStallEscalation(context.stall().cause());
                }
                RoutingSnapshot visibleSnapshot = snapshot.visibleTo(
                        network,
                        vehicle.getDriverKnowledge(),
                        vehicle.getOccupiedRoadId(),
                        simulationSeconds);
                vehicle.getNavigationState().markEvaluating();
                requests.add(new Request(
                        vehicle,
                        reason,
                        remainingCost(vehicle, network, visibleSnapshot),
                        RouteCandidate.fingerprintOf(
                                vehicle.getCurrentRoute()),
                        uTurn));
            }
        }

        requests.sort(Comparator.comparingLong(request ->
                rotatingOrder(request.vehicle().getId(), simulationSeconds)));
        RoutingSnapshot batchSnapshot = snapshot;
        for (Request request : requests) {
            requested++;
            requestedByReason.merge(request.reason(), 1L, Long::sum);
            if (request.uTurnRequested()) uTurnRequested++;
            RoutingSnapshot visibleBatchSnapshot = batchSnapshot.visibleTo(
                    network,
                    request.vehicle().getDriverKnowledge(),
                    request.vehicle().getOccupiedRoadId(),
                    simulationSeconds);
            RouteCandidate candidate = route(
                    request,
                    network,
                    visibleBatchSnapshot,
                    simulationSeconds);
            if (candidate == null) {
                reject(request, "NO_ROUTE", null, simulationSeconds);
                continue;
            }
            boolean hardBlock = request.reason() == RerouteReason.KNOWN_HARD_BLOCK
                    || request.reason() == RerouteReason.STALL_TIMEOUT
                    || request.reason() == RerouteReason.DEADLOCK_RECOVERY;
            double saving = request.currentCost().generalizedSeconds()
                    - candidate.expectedCost().generalizedSeconds();
            boolean materiallyBetter = saving
                    >= request.vehicle().getDriverProfile().decision()
                            .minimumExpectedSavingSeconds();
            boolean repeated = candidate.routeFingerprint().equals(
                    request.vehicle().getNavigationState().getLastRejectedFingerprint())
                    || candidate.routeFingerprint().equals(
                            request.vehicle().getNavigationState().getLastRouteFingerprint());
            if ((!hardBlock && !materiallyBetter) || repeated) {
                if (repeated) repeatedRouteRejections++;
                reject(
                        request,
                        candidate.routeFingerprint(),
                        candidate.expectedCost(),
                        simulationSeconds);
                continue;
            }
            RouteReplacementResult result = request.vehicle().replaceRemainingRoute(
                    request.vehicle().getRouteAnchor(), candidate, network);
            if (!result.replaced()) {
                reject(
                        request,
                        candidate.routeFingerprint(),
                        candidate.expectedCost(),
                        simulationSeconds);
                continue;
            }
            request.vehicle().getStallTracker().recordRouteAttempt(
                    simulationSeconds, candidate.routeFingerprint(), true);
            accepted++;
            acceptedByReason.merge(request.reason(), 1L, Long::sum);
            if (predictionsByVehicle.containsKey(request.vehicle().getId())) {
                routeChurn++;
            }
            predictionsByVehicle.put(
                    request.vehicle().getId(),
                    new RoutePrediction(
                            candidate.expectedCost().generalizedSeconds(),
                            simulationSeconds));
            request.vehicle().getNavigationState().recordAccepted(
                    request.reason().name(),
                    candidate.routeFingerprint(),
                    simulationSeconds,
                    request.vehicle().getDriverProfile().decision()
                            .rerouteCooldownSeconds(),
                    request.vehicle().getDriverKnowledge().getVersion(),
                    request.oldRouteFingerprint(),
                    request.currentCost(),
                    candidate.expectedCost());
            if (request.uTurnRequested()) {
                uTurnAccepted++;
                request.vehicle().getNavigationState().markTurnaroundRequested();
            }
            batchSnapshot = batchSnapshot.withVirtualDemand(candidate.route());
        }
    }

    public AdaptiveNavigationMetrics metrics() {
        return new AdaptiveNavigationMetrics(
                requested,
                accepted,
                rejected,
                requestedByReason,
                acceptedByReason,
                rejectedByReason,
                stranded,
                routeChurn,
                repeatedRouteRejections,
                stallEscalations,
                stallEscalationsByCause,
                uTurnRequested,
                uTurnAccepted,
                uTurnCompleted,
                uTurnRejected,
                routeComputations,
                cacheHits,
                routingComputationNanos,
                predictionSamples,
                predictedRemainingSeconds,
                realizedRemainingSeconds);
    }

    public TrafficInformationMetrics informationMetrics() {
        return informationService.metrics();
    }

    private RouteCandidate route(
            Request request,
            RoadNetwork network,
            RoutingSnapshot snapshot,
            double now) {
        Vehicle vehicle = request.vehicle();
        Set<String> avoid = knownHardBlocks(vehicle, now);
        AdaptiveRouteRequest routeRequest = new AdaptiveRouteRequest(
                vehicle.getId(),
                vehicle.getType(),
                vehicle.getRouteAnchor(),
                vehicle.getDestinationIntersectionId(),
                vehicle.getRoutePreference(),
                vehicle.getDriverKnowledge(),
                avoid,
                vehicle.getDriverProfile().decision().uTurnPolicy(),
                request.reason(),
                vehicle.getRouteVersion());
        String cacheKey = cacheKey(routeRequest, snapshot, now);
        RouteCandidate cached = routeCache.get(cacheKey);
        if (cached != null) {
            cacheHits++;
            return withVersion(cached, vehicle.getRouteVersion());
        }
        try {
            routeComputations++;
            long startedAtNanos = System.nanoTime();
            try {
                RouteCandidate candidate = router.findRoute(
                        network, snapshot, routeRequest);
                routeCache.put(cacheKey, candidate);
                return candidate;
            } finally {
                routingComputationNanos += System.nanoTime() - startedAtNanos;
            }
        } catch (NoSuchElementException exception) {
            return null;
        }
    }

    private RouteCandidate withVersion(RouteCandidate candidate, long version) {
        return new RouteCandidate(
                candidate.route(),
                candidate.expectedCost(),
                candidate.firstMovement(),
                candidate.routeFingerprint(),
                candidate.algorithmName(),
                version);
    }

    private String cacheKey(
            AdaptiveRouteRequest request,
            RoutingSnapshot snapshot,
            double now) {
        return request.anchor() + "|" + request.destinationIntersectionId()
                + "|" + request.knowledge().signature(now)
                + "|" + request.vehicleType() + "|" + request.preference()
                + "|" + request.uTurnPolicy() + "|" + request.reason()
                + "|" + new java.util.TreeSet<>(
                        request.temporarilyAvoidedRoadIds())
                + "|" + snapshot.virtualDemandSignature();
    }

    private void reject(
            Request request,
            String fingerprint,
            RouteCost candidateCost,
            double simulationSeconds) {
        rejected++;
        rejectedByReason.merge(request.reason(), 1L, Long::sum);
        if (request.uTurnRequested()) uTurnRejected++;
        Vehicle vehicle = request.vehicle();
        vehicle.getStallTracker().recordRouteAttempt(
                simulationSeconds, fingerprint, false);
        vehicle.getNavigationState().recordRejected(
                request.reason().name(),
                fingerprint,
                vehicle.getStallState().nextRetryAtSeconds(),
                vehicle.getDriverKnowledge().getVersion(),
                request.oldRouteFingerprint(),
                request.currentCost(),
                candidateCost,
                simulationSeconds);
    }

    private void recordStallEscalation(WaitCause cause) {
        stallEscalations++;
        if (cause != null && cause != WaitCause.NONE) {
            stallEscalationsByCause.merge(cause, 1L, Long::sum);
        }
    }

    private void observeCompletions(
            List<Vehicle> vehicles,
            double simulationSeconds) {
        for (Vehicle vehicle : vehicles.stream()
                .sorted(Comparator.comparing(Vehicle::getId))
                .toList()) {
            int completed = vehicle.getLegalUTurnsCompleted();
            int previous = observedUTurnCompletions.getOrDefault(
                    vehicle.getId(), 0);
            if (completed > previous) {
                uTurnCompleted += completed - previous;
            }
            observedUTurnCompletions.put(vehicle.getId(), completed);

            if (vehicle.getStatus() == VehicleStatus.ARRIVED) {
                RoutePrediction prediction =
                        predictionsByVehicle.remove(vehicle.getId());
                if (prediction != null) {
                    predictionSamples++;
                    predictedRemainingSeconds +=
                            prediction.predictedRemainingSeconds();
                    realizedRemainingSeconds += Math.max(
                            0.0,
                            simulationSeconds - prediction.acceptedAtSeconds());
                }
            }
        }
    }

    private void applyNonRoutingDecision(Vehicle vehicle, DriverDecision decision) {
        if (decision.kind() == DriverDecisionKind.REQUEST_LANE_CHANGE) {
            vehicle.requestLaneChangeIntent(decision.laneChangeIntent()
                    .orElse(LaneChangeIntent.DISCRETIONARY));
        }
    }

    private Set<String> knownHardBlocks(Vehicle vehicle, double now) {
        Set<String> roads = new LinkedHashSet<>();
        for (RoadAdvisory advisory : vehicle.getDriverKnowledge().activeAt(now)) {
            if (advisory.kind() == AdvisoryKind.CLOSED
                    || advisory.kind() == AdvisoryKind.LANES_BLOCKED) {
                roads.add(advisory.roadId());
            }
        }
        return Set.copyOf(roads);
    }

    private RouteCost remainingCost(
            Vehicle vehicle,
            RoadNetwork network,
            RoutingSnapshot snapshot) {
        Route route = vehicle.getCurrentRoute();
        if (route == null) {
            return new RouteCost(Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.0);
        }
        double seconds = 0.0;
        double distance = 0.0;
        List<team.traffic.domain.routing.RouteStep> steps = route.getSteps();
        for (int index = vehicle.getNextRouteStepIndex(); index < steps.size(); index++) {
            var step = steps.get(index);
            var state = snapshot.road(step.getRoadId());
            if (state == null) continue;
            double fraction = index == vehicle.getNextRouteStepIndex()
                            && vehicle.getOccupiedRoadId() != null
                    ? 1.0 - vehicle.getRoadProgress()
                    : 1.0;
            seconds += state.marginalTravelTimeSeconds() * fraction;
            var road = network.getRoad(step.getRoadId());
            if (road != null) {
                distance += road.getDistance() * fraction;
            }
        }
        return new RouteCost(seconds, distance, 0.0, 0.0);
    }

    private long rotatingOrder(String vehicleId, double simulationSeconds) {
        long bucket = (long) Math.floor(simulationSeconds / config.strategicCadenceSeconds());
        long value = scenarioSeed * 0x9E3779B97F4A7C15L
                ^ bucket * 0x632BE59BD9B4E019L
                ^ vehicleId.hashCode();
        value ^= value >>> 33;
        return value;
    }

    private record Request(
            Vehicle vehicle,
            RerouteReason reason,
            RouteCost currentCost,
            String oldRouteFingerprint,
            boolean uTurnRequested) {}

    private record RoutePrediction(
            double predictedRemainingSeconds,
            double acceptedAtSeconds) {}
}
