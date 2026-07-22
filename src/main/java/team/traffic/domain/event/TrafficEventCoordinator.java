package team.traffic.domain.event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;

/**
 * Applies event overlays without letting out-of-order resolutions corrupt the
 * remaining road state.
 */
public final class TrafficEventCoordinator {
    private RoadNetwork network;
    private final Map<String, RoadCondition> baselineByRoadId = new LinkedHashMap<>();

    public void activate(TrafficEvent event, RoadNetwork roadNetwork) {
        require(event, roadNetwork);
        useNetwork(roadNetwork);
        Road road = requireRoad(event, roadNetwork);
        baselineByRoadId.computeIfAbsent(road.getId(), ignored -> copy(road.getCondition()));
        event.resetRoadEffectMemory();
        event.apply(roadNetwork);
    }

    public void resolve(TrafficEvent event, RoadNetwork roadNetwork, List<TrafficEvent> allEvents) {
        require(event, roadNetwork);
        useNetwork(roadNetwork);
        requireRoad(event, roadNetwork);
        event.resolve(roadNetwork);
        rebuild(event.getAffectedRoadId(), roadNetwork, allEvents == null ? List.of() : allEvents);
    }

    public void reset() {
        baselineByRoadId.clear();
        network = null;
    }

    private void rebuild(String roadId, RoadNetwork roadNetwork, List<TrafficEvent> allEvents) {
        RoadCondition baseline = baselineByRoadId.get(roadId);
        if (baseline == null) return;
        roadNetwork.updateRoadCondition(roadId, copy(baseline));
        List<TrafficEvent> active = allEvents.stream()
                .filter(candidate -> roadId.equals(candidate.getAffectedRoadId()))
                .filter(candidate -> candidate.getStatus() == TrafficEventStatus.ACTIVE)
                .toList();
        for (TrafficEvent candidate : active) {
            candidate.resetRoadEffectMemory();
            candidate.apply(roadNetwork);
        }
        if (active.isEmpty()) baselineByRoadId.remove(roadId);
    }

    private void useNetwork(RoadNetwork roadNetwork) {
        if (network != roadNetwork) {
            baselineByRoadId.clear();
            network = roadNetwork;
        }
    }

    private static Road requireRoad(TrafficEvent event, RoadNetwork roadNetwork) {
        Road road = roadNetwork.getRoad(event.getAffectedRoadId());
        if (road == null) {
            throw new IllegalArgumentException("Affected road does not exist: " + event.getAffectedRoadId());
        }
        return road;
    }

    private static void require(TrafficEvent event, RoadNetwork roadNetwork) {
        if (event == null) throw new IllegalArgumentException("Event is required.");
        if (roadNetwork == null) throw new IllegalArgumentException("Road network is required.");
    }

    private static RoadCondition copy(RoadCondition condition) {
        return new RoadCondition(condition.getCongestionLevel(), condition.getVehicleDensity(),
                condition.getRoadStatus());
    }
}
