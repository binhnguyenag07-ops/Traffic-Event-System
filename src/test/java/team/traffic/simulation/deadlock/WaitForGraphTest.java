package team.traffic.simulation.deadlock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.dynamics.MotionOutcome;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.domain.vehicle.dynamics.WaitResource;
import team.traffic.domain.vehicle.dynamics.WaitState;

class WaitForGraphTest {
    @Test
    void findsStableBipartiteCycle() {
        WaitForGraph graph = new WaitForGraph();
        WaitResource first = new WaitResource.Leader("b");
        WaitResource second = new WaitResource.Leader("a");
        graph.addWait("a", first);
        graph.addOwner(first, "b");
        graph.addWait("b", second);
        graph.addOwner(second, "a");

        assertThat(graph.cyclicComponents()).singleElement()
                .satisfies(component -> {
                    assertThat(component.vehicleIds()).containsExactlyInAnyOrder("a", "b");
                    assertThat(component.resourceKeys())
                            .containsExactlyInAnyOrder("LEADER:a", "LEADER:b");
                });
    }

    @Test
    void excludesWaitWithKnownFutureRelease() {
        RoadNetwork network = network();
        Vehicle a = new Car("a", "A", "B");
        Vehicle b = new Car("b", "B", "A");
        TrafficState state = TrafficState.capture(0, network, List.of(a, b));
        commitWait(
                a,
                new WaitResource.Leader("b"),
                OptionalDouble.of(10.0),
                network,
                state);
        commitWait(
                b,
                new WaitResource.Leader("a"),
                OptionalDouble.empty(),
                network,
                state);

        assertThat(WaitForGraph.capture(List.of(a, b), state).cyclicComponents())
                .isEmpty();
    }

    @Test
    void confirmsOnlyAfterSameFingerprintPersists() {
        RoadNetwork network = network();
        Vehicle a = new Car("a", "A", "B");
        Vehicle b = new Car("b", "B", "A");
        TrafficState initial = TrafficState.capture(0, network, List.of(a, b));
        commitWait(a, new WaitResource.Leader("b"), OptionalDouble.empty(), network, initial);
        commitWait(b, new WaitResource.Leader("a"), OptionalDouble.empty(), network, initial);
        DeadlockMetrics metrics = new DeadlockMetrics();
        DeadlockDetector detector = new DeadlockDetector(1.0, 5.0, metrics);

        assertThat(detector.scan(0.0, List.of(a, b), initial))
                .extracting(DeadlockCycle::status)
                .containsExactly(DeadlockStatus.SUSPECTED);
        TrafficState later = TrafficState.capture(6, network, List.of(a, b));
        assertThat(detector.scan(6.0, List.of(a, b), later))
                .extracting(DeadlockCycle::status)
                .containsExactly(DeadlockStatus.CONFIRMED);
        assertThat(metrics.confirmed()).isEqualTo(1);
    }

    private static void commitWait(
            Vehicle vehicle,
            WaitResource resource,
            OptionalDouble release,
            RoadNetwork network,
            TrafficState state) {
        WaitState wait = new WaitState(
                WaitCause.LEADER,
                0.0,
                Optional.of(resource),
                release);
        vehicle.commitMotion(
                new MotionOutcome(Optional.empty(), wait, 0.0, 1.0),
                network,
                state);
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
        return network;
    }
}
