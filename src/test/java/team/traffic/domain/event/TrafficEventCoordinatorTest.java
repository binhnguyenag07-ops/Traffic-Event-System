package team.traffic.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

/**
 * Two events on one road each remember the condition they found. Resolving them
 * in the wrong order would therefore restore a snapshot that already contained
 * the other event's effect, and the road would keep an effect whose event has
 * ended, or lose one whose event has not. The coordinator exists to prevent
 * that, and these tests are what say so.
 */
class TrafficEventCoordinatorTest {
    private static final double BASELINE_CONGESTION = 0.1;
    private static final double BASELINE_DENSITY = 0.2;

    private final TrafficEventCoordinator coordinator = new TrafficEventCoordinator();

    @Test
    void overlappingEventsAccumulateOnTheSameRoad() {
        RoadNetwork network = network();
        TrafficEvent first = congestion("first", 0.3);
        TrafficEvent second = congestion("second", 0.4);

        coordinator.activate(first, network);
        coordinator.activate(second, network);

        assertThat(congestionOf(network)).isCloseTo(0.8, within(1e-9));
    }

    @Test
    void resolvingTheFirstEventKeepsTheSecondsEffect() {
        RoadNetwork network = network();
        TrafficEvent first = congestion("first", 0.3);
        TrafficEvent second = congestion("second", 0.4);
        coordinator.activate(first, network);
        coordinator.activate(second, network);

        coordinator.resolve(first, network, List.of(first, second));

        assertThat(first.getStatus()).isEqualTo(TrafficEventStatus.RESOLVED);
        assertThat(second.getStatus()).isEqualTo(TrafficEventStatus.ACTIVE);
        assertThat(congestionOf(network))
                .as("baseline plus the surviving event, with the resolved one removed")
                .isCloseTo(BASELINE_CONGESTION + 0.4, within(1e-9));
    }

    @Test
    void resolvingTheSecondEventKeepsTheFirstsEffect() {
        RoadNetwork network = network();
        TrafficEvent first = congestion("first", 0.3);
        TrafficEvent second = congestion("second", 0.4);
        coordinator.activate(first, network);
        coordinator.activate(second, network);

        coordinator.resolve(second, network, List.of(first, second));

        assertThat(congestionOf(network))
                .isCloseTo(BASELINE_CONGESTION + 0.3, within(1e-9));
    }

    @Test
    void resolutionOrderDoesNotChangeTheFinalRoadState() {
        RoadNetwork forwards = network();
        TrafficEvent firstA = congestion("first", 0.3);
        TrafficEvent secondA = congestion("second", 0.4);
        coordinator.activate(firstA, forwards);
        coordinator.activate(secondA, forwards);
        coordinator.resolve(firstA, forwards, List.of(firstA, secondA));
        coordinator.resolve(secondA, forwards, List.of(firstA, secondA));

        TrafficEventCoordinator other = new TrafficEventCoordinator();
        RoadNetwork backwards = network();
        TrafficEvent firstB = congestion("first", 0.3);
        TrafficEvent secondB = congestion("second", 0.4);
        other.activate(firstB, backwards);
        other.activate(secondB, backwards);
        other.resolve(secondB, backwards, List.of(firstB, secondB));
        other.resolve(firstB, backwards, List.of(firstB, secondB));

        assertThat(congestionOf(forwards)).isCloseTo(BASELINE_CONGESTION, within(1e-9));
        assertThat(congestionOf(backwards)).isCloseTo(congestionOf(forwards), within(1e-9));
    }

    @Test
    void resolvingEveryEventRestoresTheOriginalCondition() {
        RoadNetwork network = network();
        TrafficEvent first = congestion("first", 0.3);
        TrafficEvent second = congestion("second", 0.4);
        coordinator.activate(first, network);
        coordinator.activate(second, network);

        coordinator.resolve(first, network, List.of(first, second));
        coordinator.resolve(second, network, List.of(first, second));

        RoadCondition condition = network.getRoad("AB").getCondition();
        assertThat(condition.getCongestionLevel()).isCloseTo(BASELINE_CONGESTION, within(1e-9));
        assertThat(condition.getVehicleDensity()).isCloseTo(BASELINE_DENSITY, within(1e-9));
        assertThat(condition.getRoadStatus()).isEqualTo(RoadStatus.OPEN);
    }

    @Test
    void resolvingAnAccidentReopensTheRoadButLeavesUnrelatedCongestion() {
        RoadNetwork network = network();
        TrafficEvent jam = congestion("jam", 0.3);
        TrafficEvent accident = new AccidentEvent("crash", "AB", 0, 60, 0.8, "car-1");
        coordinator.activate(jam, network);
        coordinator.activate(accident, network);
        assertThat(network.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.BLOCKED);

        coordinator.resolve(accident, network, List.of(jam, accident));

        RoadCondition condition = network.getRoad("AB").getCondition();
        // Not open: the surviving congestion event marks the road congested as
        // it reapplies. The point is that it is no longer blocked, so traffic
        // and routing can use it again.
        assertThat(condition.getRoadStatus())
                .as("the accident is over, so the road stops being impassable")
                .isEqualTo(RoadStatus.CONGESTED);
        assertThat(condition.isRoutable()).isTrue();
        assertThat(condition.getVehicleDensity())
                .as("and the density the accident forced goes with it")
                .isCloseTo(BASELINE_DENSITY, within(1e-9));
        assertThat(condition.getCongestionLevel())
                .as("but the congestion event is still running")
                .isCloseTo(BASELINE_CONGESTION + 0.3, within(1e-9));
    }

    @Test
    void eventsOnDifferentRoadsDoNotDisturbEachOther() {
        RoadNetwork network = network();
        TrafficEvent onAb = congestion("ab", 0.3);
        TrafficEvent onBc = new CongestionEvent("bc", "BC", 0, 60, 0.5);
        coordinator.activate(onAb, network);
        coordinator.activate(onBc, network);

        coordinator.resolve(onAb, network, List.of(onAb, onBc));

        assertThat(congestionOf(network)).isCloseTo(BASELINE_CONGESTION, within(1e-9));
        assertThat(network.getRoad("BC").getCondition().getCongestionLevel())
                .isCloseTo(BASELINE_CONGESTION + 0.5, within(1e-9));
    }

    @Test
    void rejectsAMissingEventOrNetwork() {
        RoadNetwork network = network();

        assertThatThrownBy(() -> coordinator.activate(null, network))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event is required");
        assertThatThrownBy(() -> coordinator.activate(congestion("first", 0.3), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Road network is required");
    }

    @Test
    void rejectsAnEventWhoseRoadIsNotInTheNetwork() {
        RoadNetwork network = network();
        TrafficEvent orphan = new CongestionEvent("orphan", "missing", 0, 60, 0.3);

        assertThatThrownBy(() -> coordinator.activate(orphan, network))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
        assertThat(orphan.getStatus()).isEqualTo(TrafficEventStatus.SCHEDULED);
    }

    private double congestionOf(RoadNetwork network) {
        return network.getRoad("AB").getCondition().getCongestionLevel();
    }

    private TrafficEvent congestion(String id, double increase) {
        return new CongestionEvent(id, "AB", 0, 60, increase);
    }

    private RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
        network.addIntersection(new Intersection("C", "C", 2, 0));
        network.addRoad(new Road("AB", "A", "B", 1, 50, 10, 0, baseline()));
        network.addRoad(new Road("BC", "B", "C", 1, 50, 10, 0, baseline()));
        return network;
    }

    private RoadCondition baseline() {
        return new RoadCondition(BASELINE_CONGESTION, BASELINE_DENSITY, RoadStatus.OPEN);
    }
}
