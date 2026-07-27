package team.traffic.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.Route;
import team.traffic.domain.vehicle.navigation.AdvisoryKind;
import team.traffic.domain.vehicle.navigation.DriverKnowledge;

/** Immutable road-cost and event-overlay evidence for one strategic instant. */
public final class RoutingSnapshot {
    public record RoadState(
            double travelTimeSeconds,
            double confidence,
            boolean networkRoutable,
            boolean temporarilyFull,
            boolean dynamicallyAffected,
            int virtualDemand) {

        public RoadState {
            if (!Double.isFinite(travelTimeSeconds) || travelTimeSeconds < 0
                    || !Double.isFinite(confidence) || confidence < 0 || confidence > 1
                    || virtualDemand < 0) {
                throw new IllegalArgumentException("Routing road-state values are invalid.");
            }
        }

        public double marginalTravelTimeSeconds() {
            return travelTimeSeconds * (1.0 + Math.min(2.0, virtualDemand * 0.08));
        }
    }

    private final double capturedAtSeconds;
    private final Map<String, RoadState> roads;

    public RoutingSnapshot(double capturedAtSeconds, Map<String, RoadState> roads) {
        if (!Double.isFinite(capturedAtSeconds) || capturedAtSeconds < 0 || roads == null) {
            throw new IllegalArgumentException("Routing snapshot time and roads are required.");
        }
        this.capturedAtSeconds = capturedAtSeconds;
        this.roads = Map.copyOf(roads);
    }

    public static RoutingSnapshot freeFlow(
            RoadNetwork network,
            List<TrafficEvent> events,
            double capturedAtSeconds) {
        Set<String> dynamicRoads = activeEventRoads(events);
        Map<String, RoadState> states = new LinkedHashMap<>();
        network.getRoads().values().forEach(road -> states.put(
                road.getId(),
                new RoadState(
                        road.getFreeFlowTravelTimeSeconds()
                                * (road.isRoutable()
                                        ? road.getCondition().getTravelCostMultiplier()
                                        : 10.0),
                        0.5,
                        road.isRoutable(),
                        !road.hasRemainingCapacity(),
                        dynamicRoads.contains(road.getId()),
                        0)));
        return new RoutingSnapshot(capturedAtSeconds, states);
    }

    public double capturedAtSeconds() { return capturedAtSeconds; }
    public Map<String, RoadState> roads() { return roads; }
    public RoadState road(String roadId) { return roads.get(roadId); }

    /**
     * Stable cache discriminator for the within-batch load already assigned to
     * candidate routes. The physical snapshot may be cached briefly, but a
     * newly accepted route must still influence later drivers in the batch.
     */
    public String virtualDemandSignature() {
        String signature = roads.entrySet().stream()
                .filter(entry -> entry.getValue().virtualDemand() > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue().virtualDemand())
                .collect(Collectors.joining(","));
        return signature.isEmpty() ? "NONE" : signature;
    }

    public RoutingSnapshot withVirtualDemand(Route route) {
        if (route == null || route.isEmpty()) return this;
        Map<String, RoadState> updated = new LinkedHashMap<>(roads);
        route.getSteps().forEach(step -> {
            RoadState state = updated.get(step.getRoadId());
            if (state != null) {
                updated.put(step.getRoadId(), new RoadState(
                        state.travelTimeSeconds(),
                        state.confidence(),
                        state.networkRoutable(),
                        state.temporarilyFull(),
                        state.dynamicallyAffected(),
                        state.virtualDemand() + 1));
            }
        });
        return new RoutingSnapshot(capturedAtSeconds, updated);
    }

    /**
     * Masks measured conditions that have not reached this driver. Static
     * topology remains authoritative, while event/congestion delay and
     * momentary admission are visible only locally or through an advisory.
     */
    public RoutingSnapshot visibleTo(
            RoadNetwork network,
            DriverKnowledge knowledge,
            String locallyOccupiedRoadId,
            double simulationSeconds) {
        if (network == null || knowledge == null) {
            throw new IllegalArgumentException(
                    "Network and driver knowledge are required.");
        }
        Map<String, RoadState> visible = new LinkedHashMap<>();
        roads.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Road road = network.getRoad(entry.getKey());
                    RoadState state = entry.getValue();
                    boolean informed = knowledge.forRoad(
                                    entry.getKey(), simulationSeconds).stream()
                            .map(advisory -> advisory.kind())
                            .anyMatch(kind -> kind != AdvisoryKind.CLEARED);
                    boolean local = entry.getKey().equals(locallyOccupiedRoadId);
                    double freeFlow = road == null
                            ? state.travelTimeSeconds()
                            : road.getFreeFlowTravelTimeSeconds();
                    boolean measuredDelay = state.temporarilyFull()
                            || state.dynamicallyAffected()
                            || state.travelTimeSeconds() > freeFlow * 1.10;
                    if (informed || local || !measuredDelay) {
                        visible.put(entry.getKey(), state);
                    } else {
                        visible.put(entry.getKey(), new RoadState(
                                freeFlow,
                                Math.min(state.confidence(), 0.35),
                                state.networkRoutable(),
                                false,
                                state.dynamicallyAffected(),
                                state.virtualDemand()));
                    }
                });
        return new RoutingSnapshot(capturedAtSeconds, visible);
    }

    private static Set<String> activeEventRoads(List<TrafficEvent> events) {
        if (events == null) return Set.of();
        return events.stream()
                .filter(event -> event.getStatus() == TrafficEventStatus.ACTIVE)
                .map(TrafficEvent::getAffectedRoadId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
