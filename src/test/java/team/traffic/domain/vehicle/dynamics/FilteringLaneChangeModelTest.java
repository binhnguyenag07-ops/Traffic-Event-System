package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

/**
 * The behaviour the old model only claimed in its name: a motorcycle riding the
 * gap between two lanes rather than queueing in one of them.
 */
class FilteringLaneChangeModelTest {
    private static final double LANE_WIDTH = 3.5;
    private static final Road ROAD = new Road("R", "Road", "P", "A", "B", 1.0, 50, 100, 0,
            2, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), RoadClass.SECONDARY);
    /** Halfway between the two lane centres: the corridor a rider filters along. */
    private static final double BETWEEN_LANES = 0.0;
    /** Stopped traffic, and a rider ten metres behind it on a one-kilometre road. */
    private static final double QUEUE = 0.50;
    private static final double BEHIND_QUEUE = 0.49;

    private final FilteringLaneChangeModel model = new FilteringLaneChangeModel();

    @Test
    void ridesTheGapBetweenTwoStoppedCars() {
        Stub carLeft = car("left", 0, QUEUE, 0);
        Stub carRight = car("right", 1, QUEUE, 0);
        Stub rider = motorcycle("rider", 0, BEHIND_QUEUE);
        RoadOccupancy occupancy = occupancy(List.of(carLeft, carRight, rider));

        LateralTarget target = model.chooseTarget(request(rider, occupancy));

        assertThat(target.offsetMetres())
                .as("both lanes are blocked, but the 1.7 m between them is not")
                .isCloseTo(BETWEEN_LANES, within(1e-9));
        assertThat(target.isLaneCentre(occupancy)).isFalse();
    }

    @Test
    void aCarInTheSamePositionCannotUseThatGap() {
        Stub carLeft = car("left", 0, QUEUE, 0);
        Stub carRight = car("right", 1, QUEUE, 0);
        Stub follower = car("follower", 0, BEHIND_QUEUE, 0);
        RoadOccupancy occupancy = occupancy(List.of(carLeft, carRight, follower));

        LateralTarget target = model.chooseTarget(request(follower, occupancy));

        assertThat(target.isLaneCentre(occupancy))
                .as("1.8 m of car does not fit through 1.7 m of gap")
                .isTrue();
    }

    @Test
    void staysInLaneWhenTheRoadAheadIsClear() {
        Stub rider = motorcycle("rider", 0, BEHIND_QUEUE);
        RoadOccupancy occupancy = occupancy(List.of(rider));

        LateralTarget target = model.chooseTarget(request(rider, occupancy));

        assertThat(target.isLaneCentre(occupancy))
                .as("filtering buys nothing on an empty road, so the rider keeps its lane")
                .isTrue();
        assertThat(target.offsetMetres()).isCloseTo(laneCentre(0), within(1e-9));
    }

    @Test
    void refusesAGapNarrowedByAWideVehicle() {
        // A 2.5 m bus on the lane-one centre reaches back to 0.5 m from the road
        // centreline. A motorcycle riding the corridor would come within 0.125 m
        // of it, well inside the clearance a rider keeps.
        Stub bus = new Stub("bus", 1, QUEUE, 0, VehicleType.BUS, 12.0, 2.5, laneCentre(1));
        Stub carLeft = car("left", 0, QUEUE, 0);
        Stub rider = motorcycle("rider", 0, BEHIND_QUEUE);
        RoadOccupancy occupancy = occupancy(List.of(bus, carLeft, rider));

        LateralTarget target = model.chooseTarget(request(rider, occupancy));

        assertThat(target.offsetMetres())
                .as("the corridor is too narrow, so the rider does not aim into it")
                .isNotCloseTo(BETWEEN_LANES, within(0.1));
    }

    @Test
    void holdsPositionWhenANeighbourIsStillChangingLane() {
        Stub carLeft = car("left", 0, QUEUE, 0);
        Stub carRight = car("right", 1, QUEUE, 0);
        // Mid-manoeuvre: its offset is nowhere near its own lane centre.
        Stub drifting = new Stub("drifting", 1, 0.492, 0, VehicleType.CAR, 4.5, 1.8, 0.9);
        Stub rider = motorcycle("rider", 0, BEHIND_QUEUE);
        RoadOccupancy occupancy = occupancy(List.of(carLeft, carRight, drifting, rider));

        LateralTarget target = model.chooseTarget(request(rider, occupancy));

        assertThat(target.isLaneCentre(occupancy))
                .as("a gap beside a vehicle still crossing the line is about to close")
                .isTrue();
    }

    @Test
    void reportsTheLaneItIsNearestToWhileFiltering() {
        Stub carLeft = car("left", 0, QUEUE, 0);
        Stub carRight = car("right", 1, QUEUE, 0);
        Stub rider = motorcycle("rider", 0, BEHIND_QUEUE);
        RoadOccupancy occupancy = occupancy(List.of(carLeft, carRight, rider));

        LateralTarget target = model.chooseTarget(request(rider, occupancy));

        // Exactly between two lanes, so either index is defensible; what matters
        // is that it names a real lane so snapshots and statistics still work.
        assertThat(target.laneIndex()).isBetween(0, 1);
        assertThat(model.chooseTargetLane(request(rider, occupancy)))
                .isEqualTo(target.laneIndex());
    }

    @Test
    void isNamedForWhatItActuallyDoes() {
        assertThat(model.getName()).isEqualTo("MOTORCYCLE_SUBLANE_FILTERING");
    }

    private static double laneCentre(int laneIndex) {
        return RoadLayout.laneCentreOffsetMetres(laneIndex, 2, LANE_WIDTH);
    }

    private static LaneChangeRequest request(Stub vehicle, RoadOccupancy occupancy) {
        return new LaneChangeRequest(vehicle, occupancy, 14.0, 3.2, 7.0, 1.0);
    }

    private static Stub car(String id, int laneIndex, double progress, double speed) {
        return new Stub(id, laneIndex, progress, speed, VehicleType.CAR, 4.5, 1.8,
                laneCentre(laneIndex));
    }

    private static Stub motorcycle(String id, int laneIndex, double progress) {
        return new Stub(id, laneIndex, progress, 5.0, VehicleType.MOTORCYCLE, 2.1, 0.75,
                laneCentre(laneIndex));
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
