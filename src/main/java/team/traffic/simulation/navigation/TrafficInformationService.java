package team.traffic.simulation.navigation;

import java.util.List;
import java.util.OptionalDouble;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import team.traffic.domain.event.InformationPolicy;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.navigation.AdvisoryKind;
import team.traffic.domain.vehicle.navigation.DriverDecisionProfile;
import team.traffic.domain.vehicle.navigation.InformationAccess;
import team.traffic.domain.vehicle.navigation.InformationSource;
import team.traffic.domain.vehicle.navigation.RoadAdvisory;
import team.traffic.routing.RoutingSnapshot;

/**
 * Converts authoritative events into notices received according to each
 * driver's horizon, latency, access, and deterministic reception decision.
 */
public final class TrafficInformationService {
    private final long scenarioSeed;
    private final int maximumLookaheadRoads;
    private final double congestionFreshnessSeconds;
    private long delivered;
    private long missed;
    private double totalLatency;
    private final Set<String> attemptedDeliveries = new HashSet<>();
    private final Map<String, CongestionObservation> congestionByRoad =
            new HashMap<>();
    private long nextCongestionEpisode;

    public TrafficInformationService(long scenarioSeed, int maximumLookaheadRoads) {
        this(scenarioSeed, maximumLookaheadRoads, 10.0);
    }

    public TrafficInformationService(
            long scenarioSeed,
            int maximumLookaheadRoads,
            double congestionFreshnessSeconds) {
        if (maximumLookaheadRoads <= 0) {
            throw new IllegalArgumentException("Maximum lookahead roads must be positive.");
        }
        if (!Double.isFinite(congestionFreshnessSeconds)
                || congestionFreshnessSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Congestion freshness must be finite and positive.");
        }
        this.scenarioSeed = scenarioSeed;
        this.maximumLookaheadRoads = maximumLookaheadRoads;
        this.congestionFreshnessSeconds = congestionFreshnessSeconds;
    }

    /** Refreshes the authoritative measured-congestion index once per sub-step. */
    public void updateCongestion(
            RoadNetwork network,
            RoutingSnapshot snapshot,
            double simulationSeconds) {
        if (network == null || snapshot == null
                || !Double.isFinite(simulationSeconds)
                || simulationSeconds < 0) {
            throw new IllegalArgumentException(
                    "Network, snapshot, and simulated time are required.");
        }
        Set<String> stillCongested = new HashSet<>();
        snapshot.roads().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Road road = network.getRoad(entry.getKey());
                    RoutingSnapshot.RoadState state = entry.getValue();
                    if (road == null || state.dynamicallyAffected()) return;
                    double freeFlow = road.getFreeFlowTravelTimeSeconds();
                    double expectedDelay = Math.max(
                            0.0,
                            state.marginalTravelTimeSeconds() - freeFlow);
                    boolean material = state.temporarilyFull()
                            || expectedDelay >= Math.max(5.0, freeFlow * 0.15);
                    if (!material) return;
                    stillCongested.add(road.getId());
                    CongestionObservation observation =
                            congestionByRoad.get(road.getId());
                    if (observation == null
                            || simulationSeconds
                                    - observation.publishedAtSeconds
                                    >= congestionFreshnessSeconds) {
                        observation = new CongestionObservation(
                                "congestion:" + road.getId() + ":"
                                        + (++nextCongestionEpisode),
                                road.getId(),
                                simulationSeconds);
                        congestionByRoad.put(road.getId(), observation);
                    }
                    observation.expectedDelaySeconds = expectedDelay;
                    observation.confidence = state.confidence();
                    observation.queueDelay = state.temporarilyFull();
                    observation.lastObservedAtSeconds = simulationSeconds;
                });
        congestionByRoad.keySet().removeIf(
                roadId -> !stillCongested.contains(roadId));
    }

    public void deliverDue(
            Vehicle vehicle,
            RoadNetwork network,
            List<TrafficEvent> events,
            double simulationSeconds) {
        if (vehicle == null || network == null || events == null) {
            throw new IllegalArgumentException("Vehicle, network, and events are required.");
        }
        DriverDecisionProfile profile = vehicle.getDriverProfile().decision();
        for (TrafficEvent event : events) {
            DeliveryPosition position = deliveryPosition(vehicle, network, event);
            boolean local = locallyObserved(vehicle, event);
            if (!position.inHorizon() && !local) continue;
            if (!published(event, simulationSeconds, local)) continue;

            AdvisoryKind kind = event.getStatus() == TrafficEventStatus.RESOLVED
                    ? AdvisoryKind.CLEARED
                    : kindOf(event);
            double eventEnd =
                    event.getStartTimeSeconds() + event.getDurationSeconds();
            double publishedAt = local
                    ? simulationSeconds
                    : kind == AdvisoryKind.CLEARED
                            ? eventEnd
                            : event.getInformationPolicy().publicationTimeFor(event);
            double receivedAt = local
                    ? simulationSeconds
                    : publishedAt + profile.informationLatencySeconds();
            if (receivedAt > simulationSeconds) continue;
            if (!local && profile.informationAccess() == InformationAccess.LOCAL_ONLY) continue;
            String attemptKey = vehicle.getId() + "|" + event.getId() + "|" + kind
                    + "|" + (local ? "LOCAL" : "REMOTE");
            if (!attemptedDeliveries.add(attemptKey)) continue;
            if (!local && !receives(vehicle.getId(), event.getId(),
                    profile.noticeReceptionProbability())) {
                missed++;
                continue;
            }

            if (kind != AdvisoryKind.CLEARED && receivedAt >= eventEnd) continue;
            RoadAdvisory advisory = new RoadAdvisory(
                    event.getId() + ":" + kind,
                    event.getAffectedRoadId(),
                    position.routeStepOffset(),
                    position.distanceMetres(),
                    simulationSeconds + position.travelSeconds(),
                    kind,
                    expectedDelay(event, network, simulationSeconds),
                    local ? 1.0 : confidence(event),
                    local ? InformationSource.LOCAL_OBSERVATION : source(profile),
                    publishedAt,
                    receivedAt,
                    kind == AdvisoryKind.CLEARED
                            ? OptionalDouble.empty()
                            : OptionalDouble.of(Math.max(receivedAt, eventEnd)));
            if (vehicle.getDriverKnowledge().receive(advisory)) {
                delivered++;
                totalLatency += receivedAt - publishedAt;
            }
        }
        deliverCongestion(vehicle, network, simulationSeconds);
    }

    public TrafficInformationMetrics metrics() {
        return new TrafficInformationMetrics(delivered, missed, totalLatency);
    }

    private DeliveryPosition deliveryPosition(
            Vehicle vehicle,
            RoadNetwork network,
            TrafficEvent event) {
        return deliveryPosition(
                vehicle, network, event.getAffectedRoadId());
    }

    private DeliveryPosition deliveryPosition(
            Vehicle vehicle,
            RoadNetwork network,
            String affectedRoadId) {
        if (vehicle.getCurrentRoute() == null) {
            return new DeliveryPosition(0, 0.0, 0.0, false);
        }
        double distanceMetres = 0.0;
        double travelSeconds = 0.0;
        int offset = 0;
        int start = vehicle.getNextRouteStepIndex();
        List<RouteStep> steps = vehicle.getCurrentRoute().getSteps();
        for (int index = start; index < steps.size() && offset < maximumLookaheadRoads;
                index++, offset++) {
            RouteStep step = steps.get(index);
            Road road = network.getRoad(step.getRoadId());
            if (road == null) continue;
            double fraction = index == start && vehicle.getOccupiedRoadId() != null
                    ? 1.0 - vehicle.getRoadProgress()
                    : 1.0;
            if (step.getRoadId().equals(affectedRoadId)) {
                return new DeliveryPosition(
                        offset,
                        distanceMetres,
                        travelSeconds,
                        travelSeconds <= vehicle.getDriverProfile().decision()
                                .navigationHorizonSeconds());
            }
            distanceMetres += road.getDistance() * 1_000.0 * fraction;
            travelSeconds += road.getFreeFlowTravelTimeSeconds() * fraction;
            if (travelSeconds > vehicle.getDriverProfile().decision().navigationHorizonSeconds()) {
                break;
            }
        }
        return new DeliveryPosition(offset, distanceMetres, travelSeconds, false);
    }

    private void deliverCongestion(
            Vehicle vehicle,
            RoadNetwork network,
            double simulationSeconds) {
        DriverDecisionProfile profile = vehicle.getDriverProfile().decision();
        if (profile.informationAccess()
                != InformationAccess.CONNECTED_NAVIGATION) {
            return;
        }
        congestionByRoad.values().stream()
                .sorted(java.util.Comparator.comparing(
                        observation -> observation.roadId))
                .forEach(observation -> {
                    DeliveryPosition position = deliveryPosition(
                            vehicle, network, observation.roadId);
                    if (!position.inHorizon()) return;
                    double receivedAt = observation.publishedAtSeconds
                            + profile.informationLatencySeconds();
                    double validUntil = observation.lastObservedAtSeconds
                            + congestionFreshnessSeconds;
                    if (receivedAt > simulationSeconds
                            || receivedAt >= validUntil) {
                        return;
                    }
                    String attemptKey = vehicle.getId() + "|"
                            + observation.advisoryId;
                    if (!attemptedDeliveries.add(attemptKey)) return;
                    if (!receives(
                            vehicle.getId(),
                            observation.advisoryId,
                            profile.noticeReceptionProbability())) {
                        missed++;
                        return;
                    }
                    RoadAdvisory advisory = new RoadAdvisory(
                            observation.advisoryId,
                            observation.roadId,
                            position.routeStepOffset(),
                            position.distanceMetres(),
                            simulationSeconds + position.travelSeconds(),
                            observation.queueDelay
                                    ? AdvisoryKind.QUEUE_DELAY
                                    : AdvisoryKind.CONGESTED,
                            observation.expectedDelaySeconds,
                            observation.confidence,
                            InformationSource.CONNECTED_NAVIGATION,
                            observation.publishedAtSeconds,
                            receivedAt,
                            OptionalDouble.of(validUntil));
                    if (vehicle.getDriverKnowledge().receive(advisory)) {
                        delivered++;
                        totalLatency += receivedAt
                                - observation.publishedAtSeconds;
                    }
                });
    }

    private boolean locallyObserved(Vehicle vehicle, TrafficEvent event) {
        if (event.getAffectedRoadId().equals(vehicle.getOccupiedRoadId())) {
            return vehicle.getLocalPerception().map(perception -> perception.reachableLanes().stream()
                    .anyMatch(lane -> lane.obstruction()
                            .map(obstruction -> obstruction.eventId().equals(event.getId()))
                            .orElse(false))).orElse(false);
        }
        return false;
    }

    private boolean published(
            TrafficEvent event,
            double now,
            boolean locallyObserved) {
        if (locallyObserved) return true;
        InformationPolicy policy = event.getInformationPolicy();
        return switch (policy.kind()) {
            case LOCAL_ONLY -> false;
            case BROADCAST_ON_ACTIVATION ->
                    event.getStatus() != TrafficEventStatus.SCHEDULED
                            && now >= event.getStartTimeSeconds();
            case ANNOUNCED_AT -> now >= policy.publicationSeconds().getAsDouble();
        };
    }

    private boolean receives(String vehicleId, String eventId, double probability) {
        long value = scenarioSeed;
        value = value * 31 + vehicleId.hashCode();
        value = value * 31 + eventId.hashCode();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        double unit = (value >>> 11) * 0x1.0p-53;
        return unit < probability;
    }

    private AdvisoryKind kindOf(TrafficEvent event) {
        return switch (event.getType()) {
            case ROAD_CLOSURE -> AdvisoryKind.CLOSED;
            case ACCIDENT, BREAKDOWN -> event.getBlockedLaneIndexes().isEmpty()
                    ? AdvisoryKind.LANES_BLOCKED
                    : AdvisoryKind.INCIDENT_DELAY;
            case CONGESTION -> AdvisoryKind.CONGESTED;
        };
    }

    private double expectedDelay(
            TrafficEvent event,
            RoadNetwork network,
            double now) {
        Road road = network.getRoad(event.getAffectedRoadId());
        double remaining = Math.max(
                0.0,
                event.getStartTimeSeconds() + event.getDurationSeconds() - now);
        return switch (event.getType()) {
            case ROAD_CLOSURE -> remaining;
            case ACCIDENT, BREAKDOWN -> Math.min(
                    remaining,
                    road == null ? remaining : road.getFreeFlowTravelTimeSeconds() * 8.0);
            case CONGESTION -> road == null
                    ? 0.0
                    : road.getFreeFlowTravelTimeSeconds()
                            * (road.getCondition().getTravelCostMultiplier() - 1.0);
        };
    }

    private double confidence(TrafficEvent event) {
        return switch (event.getType()) {
            case ROAD_CLOSURE, ACCIDENT, BREAKDOWN -> 1.0;
            case CONGESTION -> 0.75;
        };
    }

    private InformationSource source(DriverDecisionProfile profile) {
        return profile.informationAccess() == InformationAccess.CONNECTED_NAVIGATION
                ? InformationSource.CONNECTED_NAVIGATION
                : InformationSource.BROADCAST;
    }

    private record DeliveryPosition(
            int routeStepOffset,
            double distanceMetres,
            double travelSeconds,
            boolean inHorizon) {}

    private static final class CongestionObservation {
        private final String advisoryId;
        private final String roadId;
        private final double publishedAtSeconds;
        private double lastObservedAtSeconds;
        private double expectedDelaySeconds;
        private double confidence;
        private boolean queueDelay;

        private CongestionObservation(
                String advisoryId,
                String roadId,
                double publishedAtSeconds) {
            this.advisoryId = advisoryId;
            this.roadId = roadId;
            this.publishedAtSeconds = publishedAtSeconds;
            this.lastObservedAtSeconds = publishedAtSeconds;
        }
    }
}
