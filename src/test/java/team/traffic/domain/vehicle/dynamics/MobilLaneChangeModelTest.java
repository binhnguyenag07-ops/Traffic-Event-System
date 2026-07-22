package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

class MobilLaneChangeModelTest {
    private static final Road ROAD = new Road("R", "Road", "P", "A", "B", 0.1, 50, 100, 0,
            2, 3.5, new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), RoadClass.SECONDARY);

    @Test
    void fasterVehicleMovesAroundASlowLeader() {
        Stub fast = new Stub("fast", 0, 0.30, 15);
        Stub slow = new Stub("slow", 0, 0.40, 3);
        RoadOccupancy occupancy = occupancy(List.of(fast, slow));

        int target = new MobilLaneChangeModel().chooseTargetLane(
                new LaneChangeRequest(fast, occupancy, 20, 2, 4.5, 1));

        assertThat(target).isEqualTo(1);
    }

    @Test
    void refusesLaneChangeWhenTargetFollowerIsTooClose() {
        Stub fast = new Stub("fast", 0, 0.30, 15);
        Stub slow = new Stub("slow", 0, 0.40, 3);
        Stub closeFollower = new Stub("close", 1, 0.29, 20);
        RoadOccupancy occupancy = occupancy(List.of(fast, slow, closeFollower));

        int target = new MobilLaneChangeModel().chooseTargetLane(
                new LaneChangeRequest(fast, occupancy, 20, 2, 4.5, 1));

        assertThat(target).isZero();
    }

    private static RoadOccupancy occupancy(List<TrafficParticipant> vehicles) {
        return RoadOccupancy.of(ROAD, vehicles, new RoadDataRoadLayout(), List.of());
    }

    private record Stub(String id, int laneIndex, double progress, double speed) implements TrafficParticipant {
        @Override public String getId() { return id; }
        @Override public double getLength() { return 4.5; }
        @Override public double getRoadProgress() { return progress; }
        @Override public String getOccupiedRoadId() { return "R"; }
        @Override public double getInstantaneousSpeedMps() { return speed; }
        @Override public int getPriorityLevel() { return 0; }
        @Override public DriverProfile getDriverProfile() { return DriverProfile.forType(VehicleType.CAR); }
        @Override public int getLaneIndex() { return laneIndex; }
        @Override public double getDecelerationMps2() { return 4.5; }
    }
}
