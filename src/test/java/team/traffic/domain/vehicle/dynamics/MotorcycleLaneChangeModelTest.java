package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

class MotorcycleLaneChangeModelTest {
    private static final Road ROAD = new Road("R", "Road", "P", "A", "B", 0.1, 50, 100, 0,
            2, 3.5, new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), RoadClass.SECONDARY);

    @Test
    void compactMotorcycleUsesASafeGapThatIsTooShortForACar() {
        Stub slowLeader = participant("slow", 0, 0.40, 3, 4.5, VehicleType.CAR);
        Stub closeTargetFollower = participant("follower", 1, 0.25, 16, 4.5, VehicleType.CAR);
        Stub motorcycle = participant("motorcycle", 0, 0.30, 15, 2.1, VehicleType.MOTORCYCLE);
        Stub car = participant("car", 0, 0.30, 15, 4.5, VehicleType.CAR);

        RoadOccupancy motorcycleTraffic = occupancy(List.of(motorcycle, slowLeader, closeTargetFollower));
        RoadOccupancy carTraffic = occupancy(List.of(car, slowLeader, closeTargetFollower));

        int motorcycleTarget = new MotorcycleLaneChangeModel().chooseTargetLane(
                new LaneChangeRequest(motorcycle, motorcycleTraffic, 20, 3.2, 7.0, 1));
        int carTarget = new MobilLaneChangeModel().chooseTargetLane(
                new LaneChangeRequest(car, carTraffic, 20, 2.5, 6.5, 1));

        assertThat(motorcycleTarget).isEqualTo(1);
        assertThat(carTarget).isZero();
    }

    @Test
    void filteringStillRefusesAnUnsafeCutIn() {
        Stub motorcycle = participant("motorcycle", 0, 0.30, 15, 2.1, VehicleType.MOTORCYCLE);
        Stub slowLeader = participant("slow", 0, 0.40, 3, 4.5, VehicleType.CAR);
        Stub fasterFollower = participant("follower", 1, 0.275, 25, 4.5, VehicleType.CAR);
        RoadOccupancy traffic = occupancy(List.of(motorcycle, slowLeader, fasterFollower));

        int target = new MotorcycleLaneChangeModel().chooseTargetLane(
                new LaneChangeRequest(motorcycle, traffic, 20, 3.2, 7.0, 1));

        assertThat(target).isZero();
    }

    private static RoadOccupancy occupancy(List<TrafficParticipant> vehicles) {
        return RoadOccupancy.of(ROAD, vehicles, new RoadDataRoadLayout(), List.of());
    }

    private static Stub participant(
            String id, int laneIndex, double progress, double speed, double length, VehicleType type) {
        return new Stub(id, laneIndex, progress, speed, length, type);
    }

    private record Stub(
            String id,
            int laneIndex,
            double progress,
            double speed,
            double length,
            VehicleType type) implements TrafficParticipant {
        @Override public String getId() { return id; }
        @Override public double getLength() { return length; }
        @Override public double getRoadProgress() { return progress; }
        @Override public String getOccupiedRoadId() { return "R"; }
        @Override public double getInstantaneousSpeedMps() { return speed; }
        @Override public int getPriorityLevel() { return 0; }
        @Override public DriverProfile getDriverProfile() { return DriverProfile.forType(type); }
        @Override public int getLaneIndex() { return laneIndex; }
        @Override public double getDecelerationMps2() { return type == VehicleType.MOTORCYCLE ? 7.0 : 6.5; }
        @Override public double getAccelerationMps2() { return type == VehicleType.MOTORCYCLE ? 3.2 : 2.5; }
    }
}
