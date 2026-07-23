package team.traffic.domain.vehicle.dynamics;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;

/**
 * Detects unavoidable rear-end collisions from the captured road state.
 *
 * <p>Who can run into whom is decided by lateral overlap, not by a shared lane
 * index. Grouping by index was right while every vehicle sat on a lane centre,
 * but a filtering motorcycle is nominally in the lane it is nearest to while
 * physically riding beside its occupants — so an index-based check would
 * report it colliding with the very car it is safely passing.</p>
 *
 * <p>The clearance used here is zero. A collision is bodies touching, not the
 * comfort margin a rider keeps: two vehicles that pass closer than a driver
 * would like have not crashed.</p>
 */
public final class CollisionDetector {
    /** Bodies must actually meet, so no comfort margin is added. */
    private static final double CONTACT_CLEARANCE_METRES = 0.0;


    private final RoadLayout roadLayout;

    public CollisionDetector() {
        this(new RoadDataRoadLayout());
    }

    public CollisionDetector(RoadLayout roadLayout) {
        if (roadLayout == null) throw new IllegalArgumentException("Road layout is required.");
        this.roadLayout = roadLayout;
    }

    public List<Collision> detect(RoadNetwork network, List<? extends TrafficParticipant> participants) {
        if (network == null) throw new IllegalArgumentException("Road network is required.");
        List<TrafficParticipant> all = participants == null ? List.of() : List.copyOf(participants);
        List<Collision> collisions = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            List<TrafficParticipant> onRoad = all.stream()
                    .filter(vehicle -> road.getId().equals(vehicle.getOccupiedRoadId()))
                    .toList();
            if (onRoad.size() < 2) continue;
            RoadOccupancy occupancy = RoadOccupancy.of(road, onRoad, roadLayout, List.of());
            double roadLengthMetres = occupancy.roadLengthMetres();
            for (TrafficParticipant follower : onRoad) {
                TrafficParticipant leader = nearestOverlappingLeader(follower, onRoad, roadLengthMetres);
                if (leader == null) continue;
                double gap = (leader.getRoadProgress() - follower.getRoadProgress()) * roadLengthMetres
                        - leader.getLength();
                double required = requiredDeceleration(
                        follower.getInstantaneousSpeedMps(), leader.getInstantaneousSpeedMps(), gap);
                if (required > follower.getDecelerationMps2()) {
                    collisions.add(new Collision(
                            follower.getId(), leader.getId(), road.getId(), follower.getLaneIndex(),
                            Math.max(follower.getRoadProgress(), Math.min(1.0, leader.getRoadProgress())),
                            required));
                }
            }
        }
        return List.copyOf(collisions);
    }

    /**
     * Closest vehicle ahead whose body actually crosses the follower's path.
     * Ties on position are broken by id so a captured state always yields the
     * same collisions.
     */
    private static TrafficParticipant nearestOverlappingLeader(
            TrafficParticipant follower,
            List<TrafficParticipant> onRoad,
            double roadLengthMetres) {
        LateralExtent followerExtent = follower.lateralExtent();
        double followerPosition = follower.getRoadProgress() * roadLengthMetres;
        TrafficParticipant nearest = null;
        double nearestPosition = Double.POSITIVE_INFINITY;
        for (TrafficParticipant candidate : onRoad) {
            if (candidate == follower || candidate.getId().equals(follower.getId())) continue;
            if (!followerExtent.overlaps(candidate.lateralExtent(), CONTACT_CLEARANCE_METRES)) continue;
            double position = candidate.getRoadProgress() * roadLengthMetres;
            if (position < followerPosition) continue;
            if (position < nearestPosition
                    || position == nearestPosition
                            && candidate.getId().compareTo(nearest.getId()) < 0) {
                nearest = candidate;
                nearestPosition = position;
            }
        }
        return nearest;
    }

    static double requiredDeceleration(double followerSpeed, double leaderSpeed, double gapMetres) {
        if (gapMetres <= 0) return Double.POSITIVE_INFINITY;
        double speedSquaredDifference = followerSpeed * followerSpeed - leaderSpeed * leaderSpeed;
        return speedSquaredDifference <= 0 ? 0.0 : speedSquaredDifference / (2.0 * gapMetres);
    }
}
