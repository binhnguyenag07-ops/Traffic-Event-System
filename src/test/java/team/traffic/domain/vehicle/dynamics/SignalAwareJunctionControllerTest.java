package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

class SignalAwareJunctionControllerTest {
    @Test
    void redApproachWaitsWhileEmergencyVehicleReceivesPreemption() {
        Road incoming = road("IN", "A", "J");
        Road outgoing = road("OUT", "J", "B");
        JunctionController ordinaryController = new JunctionController("J", new PriorityRightOfWayPolicy());
        ordinaryController.register(approach(new Stub("car", 0), incoming, outgoing));
        JunctionController emergencyController = new JunctionController("J", new PriorityRightOfWayPolicy());
        emergencyController.register(approach(new Stub("ambulance", 2), incoming, outgoing));
        JunctionEnvironment red = new JunctionEnvironment() {
            @Override public boolean hasPhysicalSpaceFor(JunctionApproach approach) { return true; }
            @Override public double secondsUntilNextArrival(String junctionId, String excluded) { return Double.POSITIVE_INFINITY; }
            @Override public boolean isSignalGreen(JunctionApproach approach) { return false; }
        };

        assertThat(ordinaryController.resolve(red)).isEmpty();
        assertThat(emergencyController.resolve(red)).containsExactly("ambulance");
    }

    private static JunctionApproach approach(TrafficParticipant vehicle, Road from, Road to) {
        return new JunctionApproach(vehicle, "J", from, to, TurnDirection.STRAIGHT, 0, 0);
    }

    private static Road road(String id, String from, String to) {
        return new Road(id, from, to, 0.1, 40, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }

    private record Stub(String id, int priority) implements TrafficParticipant {
        @Override public String getId() { return id; }
        @Override public double getLength() { return 4.5; }
        @Override public double getRoadProgress() { return 1; }
        @Override public String getOccupiedRoadId() { return null; }
        @Override public double getInstantaneousSpeedMps() { return 0; }
        @Override public int getPriorityLevel() { return priority; }
        @Override public DriverProfile getDriverProfile() {
            return DriverProfile.forType(priority > 0 ? VehicleType.EMERGENCY : VehicleType.CAR);
        }
    }
}
