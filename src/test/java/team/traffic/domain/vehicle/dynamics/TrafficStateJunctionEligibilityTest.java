package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.signal.SignalPhase;
import team.traffic.domain.signal.TrafficSignal;
import team.traffic.domain.vehicle.VehicleType;

class TrafficStateJunctionEligibilityTest {

    @Test
    void emergencyTrappedBehindARedQueueCannotReserveAgainstTheGreenArm() {
        RoadNetwork network = network();
        Road redIn = network.getRoad("RED_IN");
        Road redOut = network.getRoad("RED_OUT");
        Road greenIn = network.getRoad("GREEN_IN");
        Road greenOut = network.getRoad("GREEN_OUT");
        Stub redLeader = new Stub("red-leader", "RED_IN", 0.995, 0);
        Stub ambulance = new Stub("ambulance", "RED_IN", 0.94, 3);
        Stub greenHead = new Stub("green-head", "GREEN_IN", 0.995, 0);
        TrafficSignal signal = new TrafficSignal("J", List.of(
                new SignalPhase(Set.of("GREEN_IN"), 60.0),
                new SignalPhase(Set.of("RED_IN"), 60.0)));
        TrafficState state = TrafficState.capture(
                1,
                10.0,
                network,
                List.of(redLeader, ambulance, greenHead),
                List.of(),
                List.of(signal),
                new PriorityRightOfWayPolicy(),
                new GapAcceptanceModel(),
                new RoadDataRoadLayout());

        state.requestCrossing(approach(redLeader, redIn, redOut));
        state.requestCrossing(approach(ambulance, redIn, redOut));
        state.requestCrossing(approach(greenHead, greenIn, greenOut));
        state.resolveJunctions();

        assertThat(state.getClearedVehicleIds()).containsExactly("green-head");
        assertThat(state.junctionDecisionFor("ambulance")).isEmpty();
        assertThat(state.junctionDecisionFor("red-leader"))
                .get().extracting(JunctionDecision::denialReason)
                .isEqualTo(JunctionDenialReason.RED_SIGNAL);
    }

    @Test
    void queueHeadUsesTheSameLateralSafetyGapAsMovementPlanning() {
        RoadNetwork network = network();
        Road approachRoad = network.getRoad("RED_IN");
        Road exitRoad = network.getRoad("RED_OUT");
        Stub filteringLeader = new Stub(
                "filtering-leader", "RED_IN", 1.0, 0,
                0, 0.0, 0.75);
        Stub ambulance = new Stub(
                "ambulance", "RED_IN", 0.95, 3,
                1, 1.625, 2.0);
        TrafficSignal signal = new TrafficSignal("J", List.of(
                new SignalPhase(Set.of("RED_IN"), 60.0)));
        TrafficState state = TrafficState.capture(
                1,
                10.0,
                network,
                List.of(filteringLeader, ambulance),
                List.of(),
                List.of(signal),
                new PriorityRightOfWayPolicy(),
                new GapAcceptanceModel(),
                new RoadDataRoadLayout());

        state.requestCrossing(approach(filteringLeader, approachRoad, exitRoad));
        state.requestCrossing(approach(ambulance, approachRoad, exitRoad));
        state.resolveJunctions();

        assertThat(state.getClearedVehicleIds()).containsExactly("filtering-leader");
        assertThat(state.junctionDecisionFor("ambulance")).isEmpty();
    }

    @Test
    void coLocatedQueueMembersElectExactlyOneStableHead() {
        RoadNetwork network = network();
        Road approachRoad = network.getRoad("RED_IN");
        Road exitRoad = network.getRoad("RED_OUT");
        Stub laterId = new Stub("z-queue-member", "RED_IN", 1.0, 0);
        Stub earlierId = new Stub("a-queue-member", "RED_IN", 1.0, 0);
        TrafficState state = TrafficState.capture(
                1,
                10.0,
                network,
                List.of(laterId, earlierId),
                List.of(),
                List.of(),
                new PriorityRightOfWayPolicy(),
                new GapAcceptanceModel(),
                new RoadDataRoadLayout());

        // Register in the opposite order to the stable id tie-breaker.
        state.requestCrossing(approach(laterId, approachRoad, exitRoad));
        state.requestCrossing(approach(earlierId, approachRoad, exitRoad));
        state.resolveJunctions();

        assertThat(state.getClearedVehicleIds()).containsExactly("a-queue-member");
        assertThat(state.junctionDecisionFor("z-queue-member")).isEmpty();
    }

    private static JunctionApproach approach(Stub vehicle, Road from, Road to) {
        return new JunctionApproach(
                vehicle, "J", from, to, 0, TurnDirection.STRAIGHT, 0.5, 30.0);
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", -1, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
        network.addIntersection(new Intersection("C", "C", 0, -1));
        network.addIntersection(new Intersection("D", "D", 0, 1));
        network.addIntersection(new Intersection("J", "J", 0, 0));
        network.addRoad(road("RED_IN", "A", "J"));
        network.addRoad(road("RED_OUT", "J", "B"));
        network.addRoad(road("GREEN_IN", "C", "J"));
        network.addRoad(road("GREEN_OUT", "J", "D"));
        return network;
    }

    private static Road road(String id, String from, String to) {
        return new Road(
                id,
                id,
                id,
                from,
                to,
                0.1,
                40,
                30,
                0,
                2,
                3.25,
                new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(),
                team.traffic.domain.network.RoadClass.TERTIARY);
    }

    private record Stub(
            String id,
            String roadId,
            double progress,
            int priority,
            int laneIndex,
            double lateralOffsetMetres,
            double width) implements TrafficParticipant {
        private Stub(String id, String roadId, double progress, int priority) {
            this(id, roadId, progress, priority, 0, 0.0, 1.8);
        }
        @Override public String getId() { return id; }
        @Override public double getLength() { return 4.5; }
        @Override public double getWidth() { return width; }
        @Override public double getRoadProgress() { return progress; }
        @Override public String getOccupiedRoadId() { return roadId; }
        @Override public double getInstantaneousSpeedMps() { return 0; }
        @Override public int getPriorityLevel() { return priority; }
        @Override public int getLaneIndex() { return laneIndex; }
        @Override public double getLateralOffsetMetres() { return lateralOffsetMetres; }
        @Override public DriverProfile getDriverProfile() {
            return DriverProfile.forType(
                    priority > 0 ? VehicleType.EMERGENCY : VehicleType.CAR);
        }
    }
}
