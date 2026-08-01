package team.traffic.simulation.deadlock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.dynamics.TrafficState;

class DeadlockResolverTest {
    @Test
    void reservesARealAdmissibleMovement() {
        RoadNetwork network = network();
        Vehicle selected = selected();
        TrafficState state = TrafficState.capture(10, network, List.of(selected));

        DeadlockResolver.Resolution resolution = new DeadlockResolver(8.0).resolve(
                cycle("selected"),
                10.0,
                network,
                List.of(selected),
                state);

        assertThat(resolution.status()).isEqualTo(DeadlockStatus.RECOVERING);
        assertThat(resolution.directive().reservation()).isPresent();
        assertThat(resolution.directive().reservation().get().targetLaneIndex()).isZero();
    }

    @Test
    void reportsStrictlyUnresolvableWhenTargetLaneHasNoPhysicalSlot() {
        RoadNetwork network = network();
        Vehicle blocker = new Car("blocker", "J", "B");
        blocker.setCurrentRoute(route());
        TrafficState entry = TrafficState.capture(0, network, List.of(blocker));
        blocker.planJunctionApproach(network, entry).ifPresent(entry::requestCrossing);
        entry.resolveJunctions();
        blocker.advance(network, entry, 0.1);
        Vehicle selected = selected();
        TrafficState full = TrafficState.capture(10, network, List.of(blocker, selected));

        DeadlockResolver.Resolution resolution = new DeadlockResolver(8.0).resolve(
                cycle("selected"),
                10.0,
                network,
                List.of(blocker, selected),
                full);

        assertThat(resolution.status())
                .isEqualTo(DeadlockStatus.UNRESOLVABLE_STRICT);
        assertThat(resolution.reason())
                .isEqualTo("NO_FREE_STORAGE_OR_LEGAL_EXIT");
        assertThat(resolution.directive().reservation()).isEmpty();
    }

    private static Vehicle selected() {
        Vehicle vehicle = new Car("selected", "J", "B");
        vehicle.setCurrentRoute(route());
        return vehicle;
    }

    private static Route route() {
        return new Route(
                List.of(new RouteStep("J", "B", "JB", 1.0)),
                0.008,
                0.001,
                1.0);
    }

    private static DeadlockCycle cycle(String vehicleId) {
        return new DeadlockCycle(
                "cycle",
                Set.of(vehicleId),
                Set.of("LANE_ENTRY:JB:0"),
                0,
                10,
                DeadlockStatus.CONFIRMED);
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("J", "J", 0, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
        network.addRoad(new Road(
                "JB", "J", "B", 0.008, 30, 10, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        return network;
    }
}
