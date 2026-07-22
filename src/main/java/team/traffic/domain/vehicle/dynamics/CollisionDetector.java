package team.traffic.domain.vehicle.dynamics;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;

/** Detects unavoidable rear-end collisions from the captured lane state. */
public final class CollisionDetector {
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
            for (Lane lane : occupancy.getLanes()) {
                List<TrafficParticipant> ordered = lane.getVehicles();
                for (int index = 0; index + 1 < ordered.size(); index++) {
                    TrafficParticipant follower = ordered.get(index);
                    TrafficParticipant leader = ordered.get(index + 1);
                    double gap = lane.positionMetresOf(leader) - leader.getLength()
                            - lane.positionMetresOf(follower);
                    double required = requiredDeceleration(
                            follower.getInstantaneousSpeedMps(), leader.getInstantaneousSpeedMps(), gap);
                    if (required > follower.getDecelerationMps2()) {
                        collisions.add(new Collision(
                                follower.getId(), leader.getId(), road.getId(), lane.getLaneIndex(),
                                Math.max(follower.getRoadProgress(), Math.min(1.0, leader.getRoadProgress())),
                                required));
                    }
                }
            }
        }
        return List.copyOf(collisions);
    }

    static double requiredDeceleration(double followerSpeed, double leaderSpeed, double gapMetres) {
        if (gapMetres <= 0) return Double.POSITIVE_INFINITY;
        double speedSquaredDifference = followerSpeed * followerSpeed - leaderSpeed * leaderSpeed;
        return speedSquaredDifference <= 0 ? 0.0 : speedSquaredDifference / (2.0 * gapMetres);
    }
}
