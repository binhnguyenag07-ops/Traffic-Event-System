package team.traffic.domain.vehicle.dynamics;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;

/**
 * Derived, immutable view of one network node as a physical junction.
 * Artificial degree-two shape points are deliberately not treated as real
 * junctions.
 */
public record Junction(
        String intersectionId,
        List<Road> incomingRoads,
        List<Road> outgoingRoads,
        int degree,
        double estimatedRadiusMetres) {

    public Junction {
        if (intersectionId == null || intersectionId.isBlank()) {
            throw new IllegalArgumentException("Intersection id is required.");
        }
        incomingRoads = incomingRoads == null ? List.of() : List.copyOf(incomingRoads);
        outgoingRoads = outgoingRoads == null ? List.of() : List.copyOf(outgoingRoads);
        if (degree < 0) {
            throw new IllegalArgumentException("Junction degree must not be negative.");
        }
        if (!Double.isFinite(estimatedRadiusMetres) || estimatedRadiusMetres < 0) {
            throw new IllegalArgumentException("Junction radius must not be negative.");
        }
    }

    public static Junction at(RoadNetwork network, String intersectionId) {
        return at(network, intersectionId, new RoadDataRoadLayout());
    }

    public static Junction at(RoadNetwork network, String intersectionId, RoadLayout layout) {
        if (network == null) {
            throw new IllegalArgumentException("Road network is required.");
        }
        if (intersectionId == null || !network.containsIntersection(intersectionId)) {
            throw new IllegalArgumentException("Intersection does not exist: " + intersectionId);
        }
        if (layout == null) {
            throw new IllegalArgumentException("Road layout is required.");
        }

        List<Road> incoming = network.findIncomingRoads(intersectionId);
        List<Road> outgoing = network.findOutgoingRoads(intersectionId);
        Set<String> physicalBranches = new LinkedHashSet<>();
        double widestBranch = 0.0;
        for (Road road : concat(incoming, outgoing)) {
            physicalBranches.add(road.getPhysicalRoadId());
            widestBranch = Math.max(widestBranch, layout.roadWidthMetresOf(road));
        }
        double radius = physicalBranches.isEmpty() ? 0.0 : Math.max(3.0, widestBranch / 2.0 + 1.5);
        return new Junction(intersectionId, incoming, outgoing, physicalBranches.size(), radius);
    }

    public boolean isRealJunction() {
        return degree >= 3;
    }

    public List<Road> allDirectedBranches() {
        return List.copyOf(concat(incomingRoads, outgoingRoads));
    }

    private static List<Road> concat(List<Road> first, List<Road> second) {
        java.util.ArrayList<Road> roads = new java.util.ArrayList<>(first.size() + second.size());
        roads.addAll(first);
        roads.addAll(second);
        return roads;
    }
}
