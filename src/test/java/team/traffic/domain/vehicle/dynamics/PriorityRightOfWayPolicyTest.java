package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.EmergencyVehicle;
import team.traffic.domain.vehicle.Vehicle;

class PriorityRightOfWayPolicyTest {

    private static final Road MAIN_ROAD = road("main", RoadClass.PRIMARY);
    private static final Road SIDE_STREET = road("side", RoadClass.RESIDENTIAL);
    private static final Road EXIT_NORTH = road("north", RoadClass.SECONDARY);
    private static final Road EXIT_EAST = road("east", RoadClass.SECONDARY);

    private final PriorityRightOfWayPolicy policy = new PriorityRightOfWayPolicy();

    @Test
    void anAmbulanceGoesBeforeOrdinaryTrafficOnTheMainRoad() {
        JunctionApproach ambulance = approach(
                new EmergencyVehicle("amb-1", "C", "N", 3), SIDE_STREET, EXIT_NORTH, TurnDirection.LEFT, 0);
        JunctionApproach car = approach(
                new Car("car-1", "C", "E"), MAIN_ROAD, EXIT_EAST, TurnDirection.STRAIGHT, 0);

        assertThat(order(ambulance, car)).containsExactly("amb-1", "car-1");
    }

    @Test
    void trafficOnTheMainRoadGoesBeforeTrafficOnTheSideStreet() {
        JunctionApproach main = approach(
                new Car("main-car", "C", "E"), MAIN_ROAD, EXIT_EAST, TurnDirection.STRAIGHT, 0);
        JunctionApproach side = approach(
                new Car("side-car", "C", "N"), SIDE_STREET, EXIT_NORTH, TurnDirection.STRAIGHT, 0);

        assertThat(order(side, main)).containsExactly("main-car", "side-car");
    }

    @Test
    void goingStraightOnBeatsTurningAcrossTheJunction() {
        JunctionApproach turning = approach(
                new Car("turning", "C", "N"), MAIN_ROAD, EXIT_NORTH, TurnDirection.LEFT, 0);
        JunctionApproach straight = approach(
                new Car("straight", "C", "E"), MAIN_ROAD, EXIT_EAST, TurnDirection.STRAIGHT, 0);

        assertThat(order(turning, straight)).containsExactly("straight", "turning");
    }

    @Test
    void aVehicleHeldOnTheSideStreetLongEnoughIsFinallyLetOut() {
        JunctionApproach starved = approach(
                new Car("starved", "C", "N"),
                SIDE_STREET,
                EXIT_NORTH,
                TurnDirection.STRAIGHT,
                PriorityRightOfWayPolicy.STARVATION_SECONDS);
        JunctionApproach mainRoad = approach(
                new Car("main-car", "C", "E"), MAIN_ROAD, EXIT_EAST, TurnDirection.STRAIGHT, 0);

        assertThat(order(mainRoad, starved)).containsExactly("starved", "main-car");
    }

    @Test
    void amongStarvedTrafficTheOneStuckLongestGoesFirst() {
        // A saturated junction leaves every approach starved, so the starvation
        // flag alone cannot separate them and the order would otherwise settle
        // on road class and vehicle id — the same arm winning every step.
        JunctionApproach mainRoadStuckBriefly = approach(
                new Car("main-car", "C", "E"), MAIN_ROAD, EXIT_EAST, TurnDirection.STRAIGHT,
                PriorityRightOfWayPolicy.STARVATION_SECONDS);
        JunctionApproach sideStreetStuckLonger = approach(
                new Car("side-car", "C", "N"), SIDE_STREET, EXIT_NORTH, TurnDirection.STRAIGHT,
                60.0);

        assertThat(order(mainRoadStuckBriefly, sideStreetStuckLonger))
                .containsExactly("side-car", "main-car");
    }

    @Test
    void theMainRoadStillWinsWhileTrafficIsMoving() {
        // Below the starvation threshold the hierarchy must still decide, so a
        // fractional difference in wait cannot demote the priority road.
        JunctionApproach mainRoad = approach(
                new Car("main-car", "C", "E"), MAIN_ROAD, EXIT_EAST, TurnDirection.STRAIGHT,
                0.5);
        JunctionApproach sideStreet = approach(
                new Car("side-car", "C", "N"), SIDE_STREET, EXIT_NORTH, TurnDirection.STRAIGHT,
                1.5);

        assertThat(order(sideStreet, mainRoad)).containsExactly("main-car", "side-car");
    }

    @Test
    void theOrderDoesNotDependOnHowTheApproachesWereCollected() {
        JunctionApproach first = approach(
                new Car("aaa", "C", "E"), MAIN_ROAD, EXIT_EAST, TurnDirection.STRAIGHT, 0);
        JunctionApproach second = approach(
                new Car("bbb", "C", "E"), MAIN_ROAD, EXIT_EAST, TurnDirection.STRAIGHT, 0);

        assertThat(order(first, second)).isEqualTo(order(second, first));
    }

    @Test
    void twoMovementsOntoTheSameRoadConflict() {
        JunctionApproach fromMain = approach(
                new Car("a", "C", "N"), MAIN_ROAD, EXIT_NORTH, TurnDirection.LEFT, 0);
        JunctionApproach fromSide = approach(
                new Car("b", "C", "N"), SIDE_STREET, EXIT_NORTH, TurnDirection.STRAIGHT, 0);

        assertThat(policy.conflicts(fromMain, fromSide)).isTrue();
    }

    @Test
    void vehiclesQueuedOnTheSameApproachDoNotConflictWithEachOther() {
        JunctionApproach leader = approach(
                new Car("a", "C", "N"), MAIN_ROAD, EXIT_NORTH, TurnDirection.LEFT, 0);
        JunctionApproach follower = approach(
                new Car("b", "C", "E"), MAIN_ROAD, EXIT_EAST, TurnDirection.STRAIGHT, 0);

        assertThat(policy.conflicts(leader, follower)).isFalse();
    }

    @Test
    void movementsFromDifferentApproachesCrossAndThereforeConflict() {
        JunctionApproach fromMain = approach(
                new Car("a", "C", "E"), MAIN_ROAD, EXIT_EAST, TurnDirection.STRAIGHT, 0);
        JunctionApproach fromSide = approach(
                new Car("b", "C", "N"), SIDE_STREET, EXIT_NORTH, TurnDirection.STRAIGHT, 0);

        assertThat(policy.conflicts(fromMain, fromSide)).isTrue();
    }

    private List<String> order(JunctionApproach... approaches) {
        List<JunctionApproach> sorted = new ArrayList<>(List.of(approaches));
        sorted.sort(policy::comparePriority);
        return sorted.stream().map(JunctionApproach::vehicleId).toList();
    }

    private static JunctionApproach approach(
            Vehicle vehicle, Road from, Road to, TurnDirection direction,
            double queueWaitSeconds) {
        return new JunctionApproach(
                vehicle, "C", from, to, direction, 0.0, queueWaitSeconds);
    }

    private static Road road(String id, RoadClass roadClass) {
        return new Road(id, "C", id + "-end", 1.0, 50.0, 20, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), roadClass);
    }
}
