package team.traffic.domain.demand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;

/**
 * Projects a resolved trip onto a stable, explainable free-flow timetable.
 *
 * <p>This is a planning estimate, not a second simulation. Runtime arrival
 * times still emerge from queues, signals, incidents and driver behaviour. The
 * estimate uses directed road topology, posted speed limits and the selected
 * building driveway distances, then adds each declared dwell.</p>
 */
public final class TripScheduleEstimator {
    public static final String BASIS =
            "DIRECTED_FREE_FLOW_SHORTEST_TIME_PLUS_BUILDING_DRIVEWAYS_AND_DWELL";
    private static final double DRIVEWAY_SPEED_METRES_PER_SECOND = 15.0 / 3.6;

    private final RoadNetwork network;
    private final Map<NodePair, Double> travelSecondsByPair = new HashMap<>();

    public TripScheduleEstimator(RoadNetwork network) {
        if (network == null) throw new IllegalArgumentException("Road network is required");
        this.network = network;
    }

    public Estimate estimate(TripPlan plan) {
        if (plan == null) throw new IllegalArgumentException("Trip plan is required");
        List<LegEstimate> legs = new ArrayList<>();
        ResolvedPlace from = plan.origin();
        double cursor = plan.departAtSeconds();
        for (int index = 0; index < plan.legs().size(); index++) {
            TripLeg leg = plan.legs().get(index);
            double travelSeconds = travelSeconds(from, leg.to());
            double arrivalSeconds = cursor + travelSeconds;
            double nextDepartureSeconds = arrivalSeconds + leg.dwellSeconds();
            legs.add(new LegEstimate(
                    index + 1,
                    travelSeconds,
                    arrivalSeconds,
                    nextDepartureSeconds,
                    leg.dwellSeconds()));
            cursor = nextDepartureSeconds;
            from = leg.to();
        }
        return new Estimate(List.copyOf(legs), legs.get(legs.size() - 1).arrivalSeconds());
    }

    private double travelSeconds(ResolvedPlace from, ResolvedPlace to) {
        double graph = shortestTravelSeconds(
                from.accessIntersectionId(), to.accessIntersectionId());
        return graph + drivewaySeconds(from) + drivewaySeconds(to);
    }

    private double drivewaySeconds(ResolvedPlace place) {
        if (place.kind() != PlaceKind.BUILDING) return 0;
        Building building = network.getBuilding(place.id());
        if (building == null) return 0;
        return building.getAccessPoints().stream()
                .filter(access -> access.intersectionId().equals(place.accessIntersectionId()))
                .mapToDouble(access -> access.drivewayDistanceMetres()
                        / DRIVEWAY_SPEED_METRES_PER_SECOND)
                .min()
                .orElse(0);
    }

    private double shortestTravelSeconds(String start, String destination) {
        if (start.equals(destination)) return 0;
        NodePair key = new NodePair(start, destination);
        return travelSecondsByPair.computeIfAbsent(key, ignored -> dijkstra(start, destination));
    }

    private double dijkstra(String start, String destination) {
        Map<String, Double> best = new HashMap<>();
        PriorityQueue<NodeCost> frontier = new PriorityQueue<>(
                Comparator.comparingDouble(NodeCost::seconds));
        best.put(start, 0.0);
        frontier.add(new NodeCost(start, 0.0));
        while (!frontier.isEmpty()) {
            NodeCost current = frontier.remove();
            if (current.seconds() > best.getOrDefault(
                    current.intersectionId(), Double.POSITIVE_INFINITY)) {
                continue;
            }
            if (current.intersectionId().equals(destination)) return current.seconds();
            for (Road road : network.findOutgoingRoads(current.intersectionId())) {
                double candidate = current.seconds() + road.getFreeFlowTravelTimeSeconds();
                if (candidate + 1e-9 < best.getOrDefault(
                        road.getEndIntersectionId(), Double.POSITIVE_INFINITY)) {
                    best.put(road.getEndIntersectionId(), candidate);
                    frontier.add(new NodeCost(road.getEndIntersectionId(), candidate));
                }
            }
        }
        throw new IllegalStateException(
                "Resolved demand leg is no longer reachable: " + start + " -> " + destination);
    }

    public record Estimate(List<LegEstimate> legs, double finalArrivalSeconds) {
        public Estimate {
            legs = List.copyOf(legs);
        }
    }

    public record LegEstimate(
            int sequence,
            double travelSeconds,
            double arrivalSeconds,
            double nextDepartureSeconds,
            double dwellSeconds) {
    }

    private record NodePair(String start, String destination) {
    }

    private record NodeCost(String intersectionId, double seconds) {
    }
}
