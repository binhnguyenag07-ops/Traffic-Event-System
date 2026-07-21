package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

/**
 * Exercises physical road occupancy without a {@code Vehicle} anywhere in
 * sight. Everything here is a plain stand-in, which is the point of depending
 * on {@link TrafficParticipant} rather than on the vehicle class.
 */
class LaneOccupancyTest {

    /** 100 metres of road. */
    private static final Road ROAD = new Road("AB", "A", "B", 0.1, 50, 100, 0,
            new RoadCondition(0, 0, RoadStatus.OPEN));

    @Test
    void aRoadHoldsFewerBusesThanCarsBecauseTheyTakeMoreSpace() {
        // Cars need 4.5 + 2.5 = 7 m each; buses need 12 + 3 = 15 m.
        double carSpace = DriverProfile.forType(VehicleType.CAR).jamSpacingMetres(4.5);
        double busSpace = DriverProfile.forType(VehicleType.BUS).jamSpacingMetres(12.0);

        assertThat(100.0 / carSpace).isGreaterThan(2 * (100.0 / busSpace));
    }

    @Test
    void occupancyCountsBodyLengthPlusTheGapEachDriverKeeps() {
        LaneOccupancy occupancy = LaneOccupancy.of(ROAD, List.of(car("a", 0.1), car("b", 0.5)));

        assertThat(occupancy.getOccupiedLengthMetres()).isCloseTo(14.0, within(1e-9));
        assertThat(occupancy.getPhysicalJamRatio()).isCloseTo(0.14, within(1e-9));
    }

    @Test
    void theLeaderIsTheNearestParticipantAheadNotSimplyTheNextInTheList() {
        LaneOccupancy occupancy = LaneOccupancy.of(
                ROAD, List.of(car("far", 0.9), car("near", 0.3), car("me", 0.1)));

        LeaderGap gap = occupancy.findLeaderFor(car("me", 0.1));

        // 'near' front bumper at 30 m, its rear at 25.5 m, mine at 10 m,
        // less the 2.5 m standstill gap I keep.
        assertThat(gap.gapMetres()).isCloseTo(13.0, within(1e-9));
    }

    @Test
    void aParticipantWithNothingAheadSeesAnOpenRoad() {
        LaneOccupancy occupancy = LaneOccupancy.of(ROAD, List.of(car("alone", 0.4)));

        assertThat(occupancy.findLeaderFor(car("alone", 0.4)).isFreeRoad()).isTrue();
    }

    @Test
    void aFullRoadRefusesAnArrival() {
        // Fourteen cars nose to tail at 7 m each fill 98 of the 100 metres.
        List<TrafficParticipant> queue = new java.util.ArrayList<>();
        for (int index = 1; index <= 14; index++) {
            queue.add(car("q" + index, index * 0.07));
        }
        LaneOccupancy occupancy = LaneOccupancy.of(ROAD, queue);

        assertThat(occupancy.getPhysicalJamRatio()).isCloseTo(0.98, within(1e-9));
        assertThat(occupancy.canAdmit(car("late", 0.0))).isFalse();
    }

    @Test
    void aRoadWithRoomLeftStillAcceptsAnArrival() {
        // The same road with half the queue, and the entry left clear.
        List<TrafficParticipant> queue = new java.util.ArrayList<>();
        for (int index = 1; index <= 7; index++) {
            queue.add(car("q" + index, 0.3 + index * 0.07));
        }
        LaneOccupancy occupancy = LaneOccupancy.of(ROAD, queue);

        assertThat(occupancy.getPhysicalJamRatio()).isCloseTo(0.49, within(1e-9));
        assertThat(occupancy.canAdmit(car("late", 0.0))).isTrue();
    }

    @Test
    void anArrivalIsRefusedWhenTheQueueTailSitsOnTheEntry() {
        LaneOccupancy occupancy = LaneOccupancy.of(ROAD, List.of(car("tail", 0.02)));

        // Plenty of room overall, but the tail's rear bumper is 2 m in.
        assertThat(occupancy.getPhysicalJamRatio()).isLessThan(0.1);
        assertThat(occupancy.canAdmit(car("late", 0.0))).isFalse();
    }

    @Test
    void orderingDoesNotDependOnTheOrderTheParticipantsWereGivenIn() {
        LaneOccupancy ascending = LaneOccupancy.of(ROAD, List.of(car("a", 0.1), car("b", 0.6)));
        LaneOccupancy descending = LaneOccupancy.of(ROAD, List.of(car("b", 0.6), car("a", 0.1)));

        assertThat(ascending.getVehicles().stream().map(TrafficParticipant::getId).toList())
                .isEqualTo(descending.getVehicles().stream().map(TrafficParticipant::getId).toList());
    }

    private static TrafficParticipant car(String id, double progress) {
        return new StubParticipant(id, 4.5, progress, "AB", 0.0, 0, VehicleType.CAR);
    }

    /** A moving thing that is not a vehicle, proving the model does not need one. */
    private record StubParticipant(
            String id,
            double length,
            double progress,
            String roadId,
            double speedMps,
            int priority,
            VehicleType type) implements TrafficParticipant {

        @Override public String getId() { return id; }
        @Override public double getLength() { return length; }
        @Override public double getRoadProgress() { return progress; }
        @Override public String getOccupiedRoadId() { return roadId; }
        @Override public double getInstantaneousSpeedMps() { return speedMps; }
        @Override public int getPriorityLevel() { return priority; }
        @Override public DriverProfile getDriverProfile() { return DriverProfile.forType(type); }
    }
}
