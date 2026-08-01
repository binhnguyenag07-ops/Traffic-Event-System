package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

/**
 * Collision detection has to agree with the movement model about who is in
 * whose way. Once a motorcycle can ride between lanes, a detector that groups
 * by lane index reports it crashing into the car it is passing, because the
 * rider is nominally in that car's lane while physically beside it.
 */
class CollisionDetectorLateralTest {
    private static final double LANE_WIDTH = 3.5;

    private final CollisionDetector detector = new CollisionDetector();

    @Test
    void aFilteringRiderDoesNotCollideWithTheCarItPasses() {
        // Rider closing fast on a stopped car it is nominally behind, but riding
        // the corridor between the lanes rather than in the car's path.
        Stub stopped = car("stopped", 0, 0.500, 0);
        Stub rider = new Stub("rider", 0, 0.4995, 12.0, VehicleType.MOTORCYCLE, 2.1, 0.75, 0.0);

        List<Collision> collisions = detector.detect(network(), List.of(stopped, rider));

        assertThat(collisions)
                .as("1.7 m of clear road beside a car is not a collision with it")
                .isEmpty();
    }

    @Test
    void theSameApproachInsideTheLaneIsACollision() {
        Stub stopped = car("stopped", 0, 0.500, 0);
        Stub tailgater = new Stub("tailgater", 0, 0.4995, 12.0, VehicleType.MOTORCYCLE,
                2.1, 0.75, laneCentre(0));

        List<Collision> collisions = detector.detect(network(), List.of(stopped, tailgater));

        assertThat(collisions).hasSize(1);
        assertThat(collisions.get(0).causingVehicleId()).isEqualTo("tailgater");
        assertThat(collisions.get(0).impactedVehicleId()).isEqualTo("stopped");
        assertThat(collisions.get(0).roadId()).isEqualTo("AB");
    }

    @Test
    void twoRidersSideBySideInOneLaneDoNotCollide() {
        // Both nominally in lane zero, but 1.5 m apart across it: a real
        // arrangement on a wide lane that the index rule would call a crash.
        Stub leading = new Stub("leading", 0, 0.500, 0, VehicleType.MOTORCYCLE,
                2.1, 0.75, laneCentre(0) - 0.75);
        Stub trailing = new Stub("trailing", 0, 0.4995, 12.0, VehicleType.MOTORCYCLE,
                2.1, 0.75, laneCentre(0) + 0.75);

        assertThat(detector.detect(network(), List.of(leading, trailing))).isEmpty();
    }

    @Test
    void carsInAdjacentLanesNeverCollideWithEachOther() {
        Stub left = car("left", 0, 0.500, 0);
        Stub right = car("right", 1, 0.4995, 20);

        assertThat(detector.detect(network(), List.of(left, right))).isEmpty();
    }

    @Test
    void vehiclesOnDifferentRoadsAreNotCompared() {
        // Identical geometry to a guaranteed collision, but one road apart.
        Stub onAb = car("ab", 0, 0.500, 0);
        OtherRoadStub onBc = new OtherRoadStub("bc", 0.4995, 20, laneCentre(0));

        assertThat(detector.detect(network(), List.of(onAb, onBc))).isEmpty();
    }

    @Test
    void anEmptyRoadNetworkYieldsNoCollisions() {
        assertThat(detector.detect(network(), List.of())).isEmpty();
        assertThat(detector.detect(network(), null)).isEmpty();
    }

    private static double laneCentre(int laneIndex) {
        return RoadLayout.laneCentreOffsetMetres(laneIndex, 2, LANE_WIDTH);
    }

    private static Stub car(String id, int laneIndex, double progress, double speed) {
        return new Stub(id, laneIndex, progress, speed, VehicleType.CAR, 4.5, 1.8,
                laneCentre(laneIndex));
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
        network.addIntersection(new Intersection("C", "C", 2, 0));
        network.addRoad(new Road("AB", "Road", "P", "A", "B", 1.0, 50, 100, 0,
                2, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN), List.of(),
                RoadClass.SECONDARY));
        network.addRoad(new Road("BC", "Road", "P", "B", "C", 1.0, 50, 100, 0,
                2, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN), List.of(),
                RoadClass.SECONDARY));
        return network;
    }

    /** Same geometry as {@link Stub}, but sitting on the next road along. */
    private record OtherRoadStub(String id, double progress, double speed, double lateralOffset)
            implements TrafficParticipant {
        @Override public String getId() { return id; }
        @Override public double getLength() { return 4.5; }
        @Override public double getWidth() { return 1.8; }
        @Override public double getRoadProgress() { return progress; }
        @Override public String getOccupiedRoadId() { return "BC"; }
        @Override public double getInstantaneousSpeedMps() { return speed; }
        @Override public int getPriorityLevel() { return 0; }
        @Override public DriverProfile getDriverProfile() {
            return DriverProfile.forType(VehicleType.CAR);
        }
        @Override public int getLaneIndex() { return 0; }
        @Override public double getLateralOffsetMetres() { return lateralOffset; }
    }

    private record Stub(
            String id, int laneIndex, double progress, double speed,
            VehicleType type, double length, double width, double lateralOffset)
            implements TrafficParticipant {
        @Override public String getId() { return id; }
        @Override public double getLength() { return length; }
        @Override public double getWidth() { return width; }
        @Override public double getRoadProgress() { return progress; }
        @Override public String getOccupiedRoadId() { return "AB"; }
        @Override public double getInstantaneousSpeedMps() { return speed; }
        @Override public int getPriorityLevel() { return 0; }
        @Override public DriverProfile getDriverProfile() { return DriverProfile.forType(type); }
        @Override public int getLaneIndex() { return laneIndex; }
        @Override public double getLateralOffsetMetres() { return lateralOffset; }
        @Override public double getDecelerationMps2() { return 7.0; }
    }
}
