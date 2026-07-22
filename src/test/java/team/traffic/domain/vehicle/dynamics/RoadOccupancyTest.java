package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

class RoadOccupancyTest {
    @Test
    void threeLanesProvideThreeTimesThePhysicalQueueLength() {
        Road road = new Road("R", "Road", "P", "A", "B", 0.1, 50, 100, 0,
                3, 3.5, new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), RoadClass.PRIMARY);
        List<TrafficParticipant> vehicles = new ArrayList<>();
        for (int lane = 0; lane < 3; lane++) {
            for (int index = 0; index < 10; index++) {
                vehicles.add(car("v-" + lane + "-" + index, lane, 0.2 + index * 0.07));
            }
        }

        RoadOccupancy occupancy = RoadOccupancy.of(
                road, vehicles, new RoadDataRoadLayout(), List.of());

        assertThat(occupancy.getVehicleCount()).isEqualTo(30);
        assertThat(occupancy.getLanes()).allSatisfy(lane -> assertThat(lane.getVehicleCount()).isEqualTo(10));
        assertThat(occupancy.getPhysicalJamRatio()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(1e-12));
    }

    private static TrafficParticipant car(String id, int laneIndex, double progress) {
        return new Stub(id, laneIndex, progress);
    }

    private record Stub(String id, int laneIndex, double progress) implements TrafficParticipant {
        @Override public String getId() { return id; }
        @Override public double getLength() { return 4.5; }
        @Override public double getRoadProgress() { return progress; }
        @Override public String getOccupiedRoadId() { return "R"; }
        @Override public double getInstantaneousSpeedMps() { return 0; }
        @Override public int getPriorityLevel() { return 0; }
        @Override public DriverProfile getDriverProfile() { return DriverProfile.forType(VehicleType.CAR); }
        @Override public int getLaneIndex() { return laneIndex; }
    }
}
