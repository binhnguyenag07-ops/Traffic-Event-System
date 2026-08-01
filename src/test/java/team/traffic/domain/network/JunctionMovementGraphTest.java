package team.traffic.domain.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.dynamics.TurnDirection;

class JunctionMovementGraphTest {
    @Test
    void explicitRuleValidatesItsRoadReferences() {
        RoadNetwork network = network();
        JunctionMovement invalid = new JunctionMovement(
                "J",
                Optional.of("MISSING"),
                "JB",
                TurnDirection.LEFT,
                MovementPermission.ALLOWED,
                Set.of(),
                0,
                MovementProvenance.EXPLICIT);

        assertThatThrownBy(() -> network.getMovementGraph().register(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source road");
    }

    @Test
    void vehicleRestrictionsAreEnforcedByTheAuthoritativeRule() {
        RoadNetwork network = network();
        network.getMovementGraph().register(new JunctionMovement(
                "J",
                Optional.of("AJ"),
                "JB",
                TurnDirection.STRAIGHT,
                MovementPermission.ALLOWED,
                Set.of(VehicleType.BUS),
                0,
                MovementProvenance.EXPLICIT));

        assertThat(network.getMovementGraph().isAllowed(
                "J", "AJ", "JB", VehicleType.BUS)).isTrue();
        assertThat(network.getMovementGraph().isAllowed(
                "J", "AJ", "JB", VehicleType.CAR)).isFalse();
    }

    @Test
    void configuredPenaltyIsAppliedToConservativelyInferredUTurn() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("J", "J", 1, 0));
        network.addRoad(localRoad("AJ", "A", "J"));
        network.addRoad(localRoad("JA", "J", "A"));
        network.getMovementGraph().setUTurnPolicy(
                UTurnNetworkPolicy.EXPLICIT_OR_CONSERVATIVE_LOCAL_INFERENCE);
        network.getMovementGraph().setInferredUTurnPenaltySeconds(47.0);

        JunctionMovement movement = network.getMovementGraph()
                .find("J", "AJ", "JA")
                .orElseThrow();

        assertThat(movement.isUTurn()).isTrue();
        assertThat(movement.penaltySeconds()).isEqualTo(47.0);
    }

    @Test
    void emergencyMayTurnAcrossAnExactMajorRoadReversePairButOrdinaryTrafficMayNot() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("J", "J", 1, 0));
        network.addRoad(majorRoad("AJ", "A", "J"));
        network.addRoad(majorRoad("JA", "J", "A"));
        network.getMovementGraph().setUTurnPolicy(
                UTurnNetworkPolicy.EXPLICIT_OR_CONSERVATIVE_LOCAL_INFERENCE);

        JunctionMovement movement = network.getMovementGraph()
                .find("J", "AJ", "JA")
                .orElseThrow();

        assertThat(movement.isUTurn()).isTrue();
        assertThat(movement.allows(VehicleType.EMERGENCY)).isTrue();
        assertThat(movement.allows(VehicleType.CAR)).isFalse();
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("J", "J", 1, 0));
        network.addIntersection(new Intersection("B", "B", 2, 0));
        network.addRoad(new Road(
                "AJ", "A", "J", 1, 40, 10, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        network.addRoad(new Road(
                "JB", "J", "B", 1, 40, 10, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        return network;
    }

    private static Road localRoad(String id, String from, String to) {
        return new Road(
                id,
                id,
                "physical-AJ",
                from,
                to,
                1.0,
                40,
                10,
                0,
                1,
                3.5,
                new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(),
                RoadClass.RESIDENTIAL);
    }

    private static Road majorRoad(String id, String from, String to) {
        return new Road(
                id,
                id,
                "physical-AJ",
                from,
                to,
                1.0,
                50,
                10,
                0,
                1,
                3.5,
                new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(),
                RoadClass.PRIMARY);
    }
}
