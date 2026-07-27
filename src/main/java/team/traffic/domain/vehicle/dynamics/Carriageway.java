package team.traffic.domain.vehicle.dynamics;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;

/**
 * Which side of a shared centreline a direction of travel drives on.
 *
 * <p>A two-way street is stored as two directed roads over one line of geometry.
 * Left centred, both directions land on that line: their lanes are painted on
 * top of each other and their traffic drives through itself. Each direction is
 * therefore pushed to its own side by half the road's width.</p>
 *
 * <p>This lives here, below both callers, because it is a fact about where a
 * road physically is and two subsystems must not answer it differently. They
 * did: the scene builder offset the painted lanes while the pose resolver left
 * vehicles on the centreline, so vehicles were drawn off their own tarmac and
 * oncoming traffic overlapped. A rule that two places compute separately is a
 * rule that will eventually disagree with itself.</p>
 */
public final class Carriageway {
    private Carriageway() {}

    /**
     * Signed offset of this road's carriageway from the shared centreline, in
     * metres, positive to the right of travel — which is also the side traffic
     * here drives on. Zero for a one-way road, which has no opposing
     * carriageway to make room for.
     */
    public static double offsetMetres(RoadNetwork network, Road road, RoadLayout layout) {
        if (road == null) throw new IllegalArgumentException("Road is required.");
        if (layout == null) throw new IllegalArgumentException("Road layout is required.");
        if (!hasOpposite(network, road)) {
            return 0.0;
        }
        return layout.roadWidthMetresOf(road) / 2.0;
    }

    /** Whether some other road carries the opposite direction over this geometry. */
    public static boolean hasOpposite(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return false;
        }
        return network.getRoads().values().stream()
                .anyMatch(other -> isReverseOf(other, road));
    }

    private static boolean isReverseOf(Road candidate, Road road) {
        return candidate != road
                && candidate.getStartIntersectionId().equals(road.getEndIntersectionId())
                && candidate.getEndIntersectionId().equals(road.getStartIntersectionId());
    }
}
