package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

class PositionedEventObstacleTest {
    @Test
    void accidentBlocksOnlyItsLaneAndAtItsActualPosition() {
        RoadNetwork network = network();
        Road road = network.getRoad("R");
        AccidentEvent accident = new AccidentEvent("A", "R", 0, 60, 0.5,
                "crashed", 0.9, Set.of(1));
        accident.apply(network);
        Stub laneZero = new Stub("zero", 0, 0.1);
        Stub laneOne = new Stub("one", 1, 0.1);

        RoadOccupancy occupancy = RoadOccupancy.of(road, List.of(laneZero, laneOne),
                new RoadDataRoadLayout(), List.of(accident));

        assertThat(occupancy.lane(0).findLeaderFor(laneZero).isFreeRoad()).isTrue();
        assertThat(occupancy.lane(1).findLeaderFor(laneOne).gapMetres()).isGreaterThan(70.0);
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("R", "Road", "P", "A", "B", 0.1, 50, 100, 0,
                2, 3.5, new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), RoadClass.SECONDARY));
        return network;
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
