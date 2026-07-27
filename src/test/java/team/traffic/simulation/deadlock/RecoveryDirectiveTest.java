package team.traffic.simulation.deadlock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.JunctionMovement;
import team.traffic.domain.network.MovementPermission;
import team.traffic.domain.network.MovementProvenance;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.dynamics.JunctionApproach;
import team.traffic.domain.vehicle.dynamics.TurnDirection;

class RecoveryDirectiveTest {
    @Test
    void reservationSelectsOneVehicleAndGatesExternalEntry() {
        Road incoming = road("IN", "A", "J");
        Road target = road("OUT", "J", "B");
        JunctionMovement movement = new JunctionMovement(
                "J",
                Optional.of("IN"),
                "OUT",
                TurnDirection.STRAIGHT,
                MovementPermission.ALLOWED,
                Set.of(),
                0.0,
                MovementProvenance.EXPLICIT);
        RecoveryReservation reservation = new RecoveryReservation(
                "selected", movement, 0, "cycle", 10, 20, "DRAIN",
                "IN", 0.9, Set.of());
        RecoveryDirective directive = new RecoveryDirective(
                new RegionGate("cycle", Set.of("OUT"), Set.of("selected"), 10, 20),
                Optional.of(reservation));
        JunctionApproach selected = approach("selected", incoming, target);
        JunctionApproach external = approach("external", incoming, target);

        assertThat(directive.isReserved(selected)).isTrue();
        assertThat(directive.isHeld(selected)).isFalse();
        assertThat(directive.isHeld(external)).isTrue();
    }

    private static JunctionApproach approach(
            String id,
            Road incoming,
            Road target) {
        return new JunctionApproach(
                new Car(id, "A", "B"),
                "J",
                incoming,
                target,
                TurnDirection.STRAIGHT,
                0.0,
                0);
    }

    private static Road road(String id, String from, String to) {
        return new Road(
                id, from, to, 0.1, 30, 10, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }
}
