package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.EmergencyVehicle;
import team.traffic.domain.vehicle.Vehicle;

class JunctionControllerTest {

    private static final Road MAIN_ROAD = road("main", RoadClass.PRIMARY);
    private static final Road SIDE_STREET = road("side", RoadClass.RESIDENTIAL);
    private static final Road EXIT_NORTH = road("north", RoadClass.SECONDARY);
    private static final Road EXIT_EAST = road("east", RoadClass.SECONDARY);

    @Test
    void onlyOneOfTwoVehiclesHeadingForTheSameRoadIsLetThrough() {
        JunctionController controller = controller();
        controller.register(approach(new Car("main-car", "C", "N"), MAIN_ROAD, EXIT_NORTH, TurnDirection.STRAIGHT));
        controller.register(approach(new Car("side-car", "C", "N"), SIDE_STREET, EXIT_NORTH, TurnDirection.STRAIGHT));

        Set<String> cleared = controller.resolve(openJunction());

        assertThat(cleared).containsExactly("main-car");
    }

    @Test
    void anAmbulanceOnTheSideStreetTakesPrecedenceOverTheMainRoad() {
        JunctionController controller = controller();
        controller.register(approach(new Car("main-car", "C", "N"), MAIN_ROAD, EXIT_NORTH, TurnDirection.STRAIGHT));
        controller.register(approach(
                new EmergencyVehicle("amb", "C", "N", 3), SIDE_STREET, EXIT_NORTH, TurnDirection.STRAIGHT));

        assertThat(controller.resolve(openJunction())).containsExactly("amb");
    }

    @Test
    void nobodyIsLetOntoARoadWithNoRoomLeft() {
        JunctionController controller = controller();
        controller.register(approach(new Car("car", "C", "N"), MAIN_ROAD, EXIT_NORTH, TurnDirection.STRAIGHT));

        Set<String> cleared = controller.resolve(new StubEnvironment(false, Double.POSITIVE_INFINITY));

        assertThat(cleared).isEmpty();
    }

    @Test
    void aLeftTurnWaitsUntilTheOncomingGapIsBigEnough() {
        JunctionController controller = controller();
        controller.register(approach(new Car("turning", "C", "N"), MAIN_ROAD, EXIT_NORTH, TurnDirection.LEFT));

        assertThat(controller.resolve(new StubEnvironment(true, 1.0))).isEmpty();
        assertThat(controller.resolve(new StubEnvironment(true, 10.0))).containsExactly("turning");
    }

    @Test
    void goingStraightOnDoesNotWaitForAGap() {
        JunctionController controller = controller();
        controller.register(approach(new Car("straight", "C", "N"), MAIN_ROAD, EXIT_NORTH, TurnDirection.STRAIGHT));

        assertThat(controller.resolve(new StubEnvironment(true, 0.0))).containsExactly("straight");
    }

    @Test
    void anAmbulanceAcceptsAGapAnOrdinaryDriverWouldRefuse() {
        JunctionController ordinary = controller();
        ordinary.register(approach(new Car("car", "C", "N"), MAIN_ROAD, EXIT_NORTH, TurnDirection.LEFT));
        JunctionController emergency = controller();
        emergency.register(approach(
                new EmergencyVehicle("amb", "C", "N", 2), MAIN_ROAD, EXIT_NORTH, TurnDirection.LEFT));

        JunctionEnvironment tightGap = new StubEnvironment(true, 2.0);

        assertThat(ordinary.resolve(tightGap)).isEmpty();
        assertThat(emergency.resolve(tightGap)).containsExactly("amb");
    }

    @Test
    void twoVehiclesQueuedOnTheSameApproachBothGetThroughWhenTheirExitsDiffer() {
        JunctionController controller = controller();
        controller.register(approach(new Car("first", "C", "N"), MAIN_ROAD, EXIT_NORTH, TurnDirection.STRAIGHT));
        controller.register(approach(new Car("second", "C", "E"), MAIN_ROAD, EXIT_EAST, TurnDirection.STRAIGHT));

        assertThat(controller.resolve(openJunction())).containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void anApproachForAnotherJunctionIsRejected() {
        JunctionController controller = controller();

        assertThatThrownBy(() -> controller.register(new JunctionApproach(
                new Car("car", "X", "N"), "X", MAIN_ROAD, EXIT_NORTH, TurnDirection.STRAIGHT, 0.0, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("junction");
    }

    private static JunctionController controller() {
        return new JunctionController("C", new PriorityRightOfWayPolicy());
    }

    private static JunctionApproach approach(Vehicle vehicle, Road from, Road to, TurnDirection direction) {
        return new JunctionApproach(vehicle, "C", from, to, direction, 0.0, 0);
    }

    private static JunctionEnvironment openJunction() {
        return new StubEnvironment(true, Double.POSITIVE_INFINITY);
    }

    private static Road road(String id, RoadClass roadClass) {
        return new Road(id, "C", id + "-end", 1.0, 50.0, 20, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), roadClass);
    }

    /** Stands in for a whole network so the junction rules can be tested alone. */
    private record StubEnvironment(boolean hasSpace, double gapSeconds) implements JunctionEnvironment {

        @Override
        public boolean hasPhysicalSpaceFor(JunctionApproach approach) {
            return hasSpace;
        }

        @Override
        public double secondsUntilNextArrival(String junctionId, String excludedIncomingRoadId) {
            return gapSeconds;
        }
    }
}
