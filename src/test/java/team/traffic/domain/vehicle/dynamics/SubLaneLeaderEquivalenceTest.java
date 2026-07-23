package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

/**
 * Replacing "shares my lane index" with "overlaps me sideways" is only safe if
 * the two agree wherever the old rule was right. For anything sitting squarely
 * on a lane centre they must give the same leader, otherwise every car in the
 * simulation quietly changes behaviour. This is that proof, kept as a
 * permanent regression test.
 */
class SubLaneLeaderEquivalenceTest {
    private static final double LANE_WIDTH = 3.5;
    private static final double CLEARANCE = 0.30;
    private static final Road ROAD = new Road("R", "Road", "P", "A", "B", 1.0, 50, 100, 0,
            2, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), RoadClass.SECONDARY);

    @Test
    void aCarOnALaneCentreGetsTheSameLeaderFromBothRules() {
        Stub follower = car("follower", 0, 0.20);
        Stub leader = car("leader", 0, 0.50);
        Stub elsewhere = car("elsewhere", 1, 0.30);
        RoadOccupancy occupancy = occupancy(List.of(follower, leader, elsewhere));

        LeaderGap byLaneIndex = occupancy.lane(0).findLeaderFor(follower);
        LeaderGap byOverlap = occupancy.findLeaderFor(follower, CLEARANCE);

        assertThat(byOverlap.gapMetres()).isCloseTo(byLaneIndex.gapMetres(), within(1e-9));
        assertThat(byOverlap.leaderSpeedMps()).isCloseTo(byLaneIndex.leaderSpeedMps(), within(1e-9));
    }

    @Test
    void aCarInTheNextLaneIsNotInTheWay() {
        Stub follower = car("follower", 0, 0.20);
        Stub neighbour = car("neighbour", 1, 0.50);
        RoadOccupancy occupancy = occupancy(List.of(follower, neighbour));

        assertThat(occupancy.lane(0).findLeaderFor(follower).isFreeRoad()).isTrue();
        assertThat(occupancy.findLeaderFor(follower, CLEARANCE).isFreeRoad())
                .as("1.7 m of clear tarmac separates two cars on adjacent lane centres")
                .isTrue();
    }

    @Test
    void anEmptyRoadAgreesTooAndReportsFreeRoad() {
        Stub alone = car("alone", 0, 0.4);
        RoadOccupancy occupancy = occupancy(List.of(alone));

        assertThat(occupancy.lane(0).findLeaderFor(alone).isFreeRoad()).isTrue();
        assertThat(occupancy.findLeaderFor(alone, CLEARANCE).isFreeRoad()).isTrue();
    }

    @Test
    void anObstructionBlockingALaneStillStopsTheCarInIt() {
        AccidentEvent accident = new AccidentEvent("A", "R", 0, 60, 0.5, "cause", 0.7, Set.of(0));
        Stub inBlockedLane = car("blocked", 0, 0.2);
        Stub inClearLane = car("clear", 1, 0.2);
        RoadOccupancy occupancy = RoadOccupancy.of(
                ROAD, List.of(inBlockedLane, inClearLane), new RoadDataRoadLayout(), List.of(accident));

        LeaderGap byLaneIndex = occupancy.lane(0).findLeaderFor(inBlockedLane);
        LeaderGap byOverlap = occupancy.findLeaderFor(inBlockedLane, CLEARANCE);

        assertThat(byOverlap.gapMetres()).isCloseTo(byLaneIndex.gapMetres(), within(1e-9));
        assertThat(occupancy.findLeaderFor(inClearLane, CLEARANCE).isFreeRoad())
                .as("the accident blocks lane zero only")
                .isTrue();
    }

    @Test
    void theWidestVehiclesStillClearEachOtherOnEveryRoadClass() {
        // The substitution is only safe if no pair of lane-centre vehicles can
        // overlap on a road this project actually builds. Two buses on the
        // narrowest class is the worst case: 3.0 m lanes leave 0.5 m between
        // them, comfortably more than the clearance a driver keeps.
        for (RoadClass roadClass : RoadClass.values()) {
            double laneWidth = roadClass.getDefaultLaneWidthMetres();
            LateralExtent left = new LateralExtent(
                    RoadLayout.laneCentreOffsetMetres(0, 2, laneWidth), 2.5);
            LateralExtent right = new LateralExtent(
                    RoadLayout.laneCentreOffsetMetres(1, 2, laneWidth), 2.5);

            assertThat(left.overlaps(right, CLEARANCE))
                    .as("two buses on adjacent %s lane centres", roadClass)
                    .isFalse();
        }
    }

    @Test
    void aVehicleThatDoesReachAcrossTheLineBlocksTheOneBesideIt() {
        Road narrow = new Road("N", "Narrow", "P", "A", "B", 1.0, 50, 100, 0,
                2, 2.4, new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), RoadClass.RESIDENTIAL);
        Stub bus = new Stub("bus", 1, 0.5, 0, VehicleType.BUS, 12.0, 2.5, laneCentre(1, 2.4));
        Stub follower = new Stub("car", 0, 0.2, 10, VehicleType.CAR, 4.5, 1.8, laneCentre(0, 2.4));
        RoadOccupancy occupancy =
                RoadOccupancy.of(narrow, List.of(bus, follower), new RoadDataRoadLayout(), List.of());

        // Lanes narrow enough to leave 0.25 m beside a bus: less than the
        // clearance, so the car behind it must yield. The lane-index rule is
        // blind to this because the two are nominally in different lanes.
        assertThat(occupancy.lane(0).findLeaderFor(follower).isFreeRoad()).isTrue();
        assertThat(occupancy.findLeaderFor(follower, CLEARANCE).isFreeRoad()).isFalse();
    }

    private static double laneCentre(int laneIndex, double laneWidth) {
        return RoadLayout.laneCentreOffsetMetres(laneIndex, 2, laneWidth);
    }

    private static Stub car(String id, int laneIndex, double progress) {
        return new Stub(id, laneIndex, progress, 10, VehicleType.CAR, 4.5, 1.8,
                laneCentre(laneIndex, LANE_WIDTH));
    }

    private static RoadOccupancy occupancy(List<TrafficParticipant> vehicles) {
        return RoadOccupancy.of(ROAD, vehicles, new RoadDataRoadLayout(), List.of());
    }

    private record Stub(
            String id, int laneIndex, double progress, double speed,
            VehicleType type, double length, double width, double lateralOffset)
            implements TrafficParticipant {
        @Override public String getId() { return id; }
        @Override public double getLength() { return length; }
        @Override public double getWidth() { return width; }
        @Override public double getRoadProgress() { return progress; }
        @Override public String getOccupiedRoadId() { return "R"; }
        @Override public double getInstantaneousSpeedMps() { return speed; }
        @Override public int getPriorityLevel() { return 0; }
        @Override public DriverProfile getDriverProfile() { return DriverProfile.forType(type); }
        @Override public int getLaneIndex() { return laneIndex; }
        @Override public double getLateralOffsetMetres() { return lateralOffset; }
    }
}
