package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.vehicle.VehicleType;

class CollisionDetectorTest {
    @Test
    void reportsFollowerAsCausingVehicleWhenRequiredBrakingIsImpossible() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("R", "A", "B", 0.1, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        Stub follower = new Stub("follower", 0.40, 20);
        Stub leader = new Stub("leader", 0.46, 0);

        List<Collision> collisions = new CollisionDetector().detect(network, List.of(follower, leader));

        assertThat(collisions).singleElement().satisfies(collision -> {
            assertThat(collision.causingVehicleId()).isEqualTo("follower");
            assertThat(collision.impactedVehicleId()).isEqualTo("leader");
            assertThat(collision.requiredDecelerationMps2()).isGreaterThan(4.5);
        });
    }

    private record Stub(String id, double progress, double speed) implements TrafficParticipant {
        @Override public String getId() { return id; }
        @Override public double getLength() { return 4.5; }
        @Override public double getRoadProgress() { return progress; }
        @Override public String getOccupiedRoadId() { return "R"; }
        @Override public double getInstantaneousSpeedMps() { return speed; }
        @Override public int getPriorityLevel() { return 0; }
        @Override public DriverProfile getDriverProfile() { return DriverProfile.forType(VehicleType.CAR); }
        @Override public double getDecelerationMps2() { return 4.5; }
    }
}
