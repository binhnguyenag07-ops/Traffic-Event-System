package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

/**
 * Queue length used to count every vehicle's full standstill footprint whatever
 * its width, so a lane of motorcycles read as congested as a lane of cars while
 * carrying three times as many riders. A row of vehicles standing abreast holds
 * up one row's worth of road, and that is what these tests hold.
 */
class LaneQueueCapacityTest {
    private static final double LANE_WIDTH = 3.5;
    private static final double ROAD_LENGTH_KM = 1.0;

    @Test
    void aCarFillsTheWidthOfALaneAndAMotorcycleDoesNot() {
        Lane lane = laneOf(LANE_WIDTH, List.of());

        assertThat(lane.abreastCapacityOf(stub("c", VehicleType.CAR, 4.5, 1.8, 0)))
                .as("1.8 m of car plus clearance leaves no room for a second")
                .isEqualTo(1);
        assertThat(lane.abreastCapacityOf(stub("m", VehicleType.MOTORCYCLE, 2.1, 0.75, 0)))
                .isEqualTo(3);
        assertThat(lane.abreastCapacityOf(stub("b", VehicleType.BUS, 12.0, 2.5, 0)))
                .isEqualTo(1);
    }

    @Test
    void aNarrowerLaneHoldsFewerAbreast() {
        Lane residential = laneOf(3.0, List.of());

        assertThat(residential.abreastCapacityOf(stub("m", VehicleType.MOTORCYCLE, 2.1, 0.75, 0)))
                .as("3.0 m fits two riders abreast, not three")
                .isEqualTo(2);
        assertThat(residential.abreastCapacityOf(stub("c", VehicleType.CAR, 4.5, 1.8, 0)))
                .isEqualTo(1);
    }

    @Test
    void threeMotorcyclesAbreastHoldUpNoMoreRoadThanOneOfThem() {
        double alone = laneOf(LANE_WIDTH, List.of(motorcycle("a", 0.10))).getOccupiedLengthMetres();
        double abreast = laneOf(LANE_WIDTH,
                List.of(motorcycle("a", 0.10), motorcycle("b", 0.10), motorcycle("c", 0.10)))
                .getOccupiedLengthMetres();

        // A rider's full standstill footprint is 2.1 m of bike plus a 0.8 m gap.
        assertThat(alone)
                .as("one rider claims a third of a row")
                .isCloseTo(2.9 / 3, within(1e-9));
        assertThat(abreast)
                .as("three of them claim exactly one row between them")
                .isCloseTo(2.9, within(1e-9));
        assertThat(abreast).isCloseTo(alone * 3, within(1e-9));
    }

    @Test
    void threeMotorcyclesStillTakeLessRoomThanASingleCar() {
        double motorcycles = laneOf(LANE_WIDTH,
                List.of(motorcycle("a", 0.10), motorcycle("b", 0.10), motorcycle("c", 0.10)))
                .getOccupiedLengthMetres();
        double oneCar = laneOf(LANE_WIDTH, List.of(car("c", 0.10))).getOccupiedLengthMetres();

        assertThat(motorcycles).isLessThan(oneCar);
    }

    @Test
    void carsAreUnaffectedByTheChange() {
        Lane lane = laneOf(LANE_WIDTH, List.of(car("a", 0.10), car("b", 0.50)));

        // Two cars, 4.5 m of body and 2.5 m of standstill gap each.
        assertThat(lane.getOccupiedLengthMetres()).isCloseTo(14.0, within(1e-9));
        assertThat(lane.getPhysicalJamRatio()).isCloseTo(0.014, within(1e-9));
    }

    @Test
    void aBusStillCostsFarMoreThanACar() {
        double bus = laneOf(LANE_WIDTH, List.of(bus("b", 0.10))).getOccupiedLengthMetres();
        double car = laneOf(LANE_WIDTH, List.of(car("c", 0.10))).getOccupiedLengthMetres();

        assertThat(bus).isCloseTo(15.0, within(1e-9));
        assertThat(bus).isGreaterThan(car * 2);
    }

    @Test
    void aLaneJamsOnFarMoreMotorcyclesThanCars() {
        assertThat(howManyFit(this::car))
                .as("1000 m of lane at 7 m a car")
                .isEqualTo(142);
        assertThat(howManyFit(this::motorcycle))
                .as("three abreast at 2.9 m a row")
                .isGreaterThan(1000);
    }

    @Test
    void theEntryGapIsNotSharedBetweenVehiclesStandingAbreast() {
        // A rider joining behind a queue needs its own body length at the entry,
        // however much room there is beside it. Storage is shared; the entry is not.
        Lane blockedEntry = laneOf(LANE_WIDTH, List.of(motorcycle("tail", 0.001)));

        assertThat(blockedEntry.getPhysicalJamRatio()).isLessThan(0.01);
        assertThat(blockedEntry.canAdmit(motorcycle("late", 0.0)))
                .as("nearly empty lane, but nowhere to stop at the entry")
                .isFalse();
    }

    @Test
    void anEmptyMicroSegmentAdmitsOneVehicleLongerThanItsStorageLength() {
        Lane emptyConnector = laneOfLength(0.0105, LANE_WIDTH, List.of());

        assertThat(emptyConnector.canAdmit(bus("first", 0.0)))
                .as("an empty imported connector must be traversable by one bus")
                .isTrue();

        Lane occupiedConnector = laneOfLength(
                0.0105, LANE_WIDTH, List.of(bus("first", 0.5)));
        assertThat(occupiedConnector.canAdmit(bus("second", 0.0)))
                .as("the micro segment is not unlimited storage")
                .isFalse();
    }

    private int howManyFit(java.util.function.BiFunction<String, Double, TrafficParticipant> maker) {
        List<TrafficParticipant> queue = new ArrayList<>();
        for (int index = 0; index < 5_000; index++) {
            TrafficParticipant arriving = maker.apply("v" + index, 0.5);
            if (!laneOf(LANE_WIDTH, List.copyOf(queue)).canAdmit(arriving)) {
                return queue.size();
            }
            queue.add(arriving);
        }
        return queue.size();
    }

    private Lane laneOf(double laneWidthMetres, List<TrafficParticipant> vehicles) {
        return laneOfLength(ROAD_LENGTH_KM, laneWidthMetres, vehicles);
    }

    private Lane laneOfLength(
            double roadLengthKm,
            double laneWidthMetres,
            List<TrafficParticipant> vehicles) {
        Road road = new Road("R", "Road", "P", "A", "B", roadLengthKm, 50, 10_000, 0,
                1, laneWidthMetres, new RoadCondition(0, 0, RoadStatus.OPEN), List.of(),
                RoadClass.SECONDARY);
        return RoadOccupancy.of(road, vehicles, new RoadDataRoadLayout(), List.of()).lane(0);
    }

    private TrafficParticipant car(String id, double progress) {
        return stub(id, VehicleType.CAR, 4.5, 1.8, progress);
    }

    private TrafficParticipant motorcycle(String id, double progress) {
        return stub(id, VehicleType.MOTORCYCLE, 2.1, 0.75, progress);
    }

    private TrafficParticipant bus(String id, double progress) {
        return stub(id, VehicleType.BUS, 12.0, 2.5, progress);
    }

    private static Stub stub(
            String id, VehicleType type, double length, double width, double progress) {
        return new Stub(id, type, length, width, progress);
    }

    private record Stub(String id, VehicleType type, double length, double width, double progress)
            implements TrafficParticipant {
        @Override public String getId() { return id; }
        @Override public double getLength() { return length; }
        @Override public double getWidth() { return width; }
        @Override public double getRoadProgress() { return progress; }
        @Override public String getOccupiedRoadId() { return "R"; }
        @Override public double getInstantaneousSpeedMps() { return 0; }
        @Override public int getPriorityLevel() { return 0; }
        @Override public DriverProfile getDriverProfile() { return DriverProfile.forType(type); }
        @Override public int getLaneIndex() { return 0; }
    }
}
