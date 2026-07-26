package team.traffic.domain.demand;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import team.traffic.domain.network.RoadNetwork;

/** Directed-topology validation shared by generated and scripted plan paths. */
public final class TripPlanReachability {
    private TripPlanReachability() {}

    public static Optional<UnreachableLeg> firstUnreachableLeg(
            RoadNetwork network,
            TripPlan plan) {
        if (network == null) throw new IllegalArgumentException("Road network is required");
        if (plan == null) throw new IllegalArgumentException("Trip plan is required");
        String from = plan.origin().accessIntersectionId();
        for (TripLeg leg : plan.legs()) {
            String to = leg.to().accessIntersectionId();
            if (!from.equals(to) && !isReachable(network, from, to)) {
                return Optional.of(new UnreachableLeg(from, to));
            }
            from = to;
        }
        return Optional.empty();
    }

    public static boolean isReachable(RoadNetwork network, TripPlan plan) {
        return firstUnreachableLeg(network, plan).isEmpty();
    }

    private static boolean isReachable(
            RoadNetwork network,
            String start,
            String destination) {
        if (!network.containsIntersection(start)
                || !network.containsIntersection(destination)) {
            return false;
        }
        ArrayDeque<String> frontier = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        frontier.add(start);
        visited.add(start);
        while (!frontier.isEmpty()) {
            String current = frontier.removeFirst();
            for (var road : network.findOutgoingRoads(current)) {
                String next = road.getEndIntersectionId();
                if (next.equals(destination)) return true;
                if (visited.add(next)) frontier.addLast(next);
            }
        }
        return false;
    }

    public record UnreachableLeg(String fromIntersectionId, String toIntersectionId) {}
}
