package team.traffic.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

class TrafficEventTest {

    @Test
    void congestionEventAppliesAndRestoresPreviousCondition() {
        RoadNetwork network = createNetwork(new RoadCondition(0.2, 0.3, RoadStatus.CONGESTED));
        TrafficEvent event = new CongestionEvent("event-1", "AB", 2, 5, 0.4);

        event.apply(network);

        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.ACTIVE);
        assertThat(network.getRoad("AB").getCondition().getCongestionLevel()).isCloseTo(0.6, within(1e-9));
        assertThat(network.getRoad("AB").getCondition().getVehicleDensity()).isEqualTo(0.3);

        event.resolve(network);

        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.RESOLVED);
        assertThat(network.getRoad("AB").getCondition().getCongestionLevel()).isEqualTo(0.2);
        assertThat(network.getRoad("AB").getCondition().getVehicleDensity()).isEqualTo(0.3);
        assertThat(network.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.CONGESTED);
    }

    @Test
    void accidentEventBlocksAndRestoresRoad() {
        RoadNetwork network = createNetwork(new RoadCondition(0.1, 0.2, RoadStatus.OPEN));
        TrafficEvent event = new AccidentEvent("event-1", "AB", 0, 3, 0.8, "car-1");

        event.apply(network);

        assertThat(network.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.BLOCKED);
        assertThat(network.getRoad("AB").getCondition().getVehicleDensity()).isEqualTo(0.8);

        event.resolve(network);

        assertThat(network.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.OPEN);
        assertThat(network.getRoad("AB").getCondition().getCongestionLevel()).isEqualTo(0.1);
        assertThat(network.getRoad("AB").getCondition().getVehicleDensity()).isEqualTo(0.2);
    }

    @Test
    void roadClosureEventUsesClosedStatusAndRestoresRoad() {
        RoadNetwork network = createNetwork(new RoadCondition(0, 0, RoadStatus.OPEN));
        TrafficEvent event = new RoadClosureEvent("event-1", "AB", 1, 2);

        event.apply(network);
        assertThat(network.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.CLOSED);
        assertThat(network.getRoad("AB").isAvailable()).isFalse();

        event.resolve(network);
        assertThat(network.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.OPEN);
        assertThat(network.getRoad("AB").isAvailable()).isTrue();
    }

    @Test
    void activeWindowIncludesStartAndExcludesEnd() {
        TrafficEvent event = new RoadClosureEvent("event-1", "AB", 3, 4);

        assertThat(event.isActive(2)).isFalse();
        assertThat(event.isActive(3)).isTrue();
        assertThat(event.isActive(6)).isTrue();
        assertThat(event.isActive(7)).isFalse();
    }

    @Test
    void failedApplyKeepsEventScheduled() {
        RoadNetwork network = createNetwork(new RoadCondition(0, 0, RoadStatus.OPEN));
        TrafficEvent event = new RoadClosureEvent("event-1", "missing-road", 0, 1);

        assertThatThrownBy(() -> event.apply(network))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing-road");
        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.SCHEDULED);
    }

    @Test
    void breakdownEventRaisesCongestionAndRestoresIt() {
        RoadNetwork network = createNetwork(new RoadCondition(0.1, 0.2, RoadStatus.OPEN));
        TrafficEvent event = new BreakdownEvent(
                "event-1", "AB", 0, 30, "car-1", 0.4, Set.of(0));

        event.apply(network);

        assertThat(event.getType()).isEqualTo(TrafficEventType.BREAKDOWN);
        assertThat(network.getRoad("AB").getCondition().getCongestionLevel())
                .isCloseTo(0.3, within(1e-9));

        event.resolve(network);

        assertThat(network.getRoad("AB").getCondition().getCongestionLevel()).isEqualTo(0.1);
        assertThat(network.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.OPEN);
    }

    @Test
    void breakdownEventRequiresTheVehicleThatFailed() {
        assertThatThrownBy(() -> new BreakdownEvent("event-1", "AB", 0, 30, " ", 0.4, Set.of(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vehicle id");
    }

    @Test
    void rejectsBlankIdentifiersAndImpossibleTiming() {
        assertThatThrownBy(() -> new RoadClosureEvent(" ", "AB", 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event id");
        assertThatThrownBy(() -> new RoadClosureEvent("event-1", " ", 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Affected road id");
        assertThatThrownBy(() -> new RoadClosureEvent("event-1", "AB", -1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start time");
        assertThatThrownBy(() -> new RoadClosureEvent("event-1", "AB", 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duration");
    }

    @Test
    void rejectsAPositionThatIsNotOnTheRoad() {
        assertThatThrownBy(() -> new BreakdownEvent("e", "AB", 0, 1, "car-1", -0.1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Position along road");
        assertThatThrownBy(() -> new BreakdownEvent("e", "AB", 0, 1, "car-1", 1.1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Position along road");
    }

    @Test
    void rejectsANegativeBlockedLaneIndex() {
        assertThatThrownBy(() -> new BreakdownEvent("e", "AB", 0, 1, "car-1", 0.5, Set.of(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Blocked lane");
    }

    @Test
    void blockedLaneIndexesAreCopiedAndUnmodifiable() {
        Set<Integer> requested = new HashSet<>(Set.of(2, 0));
        TrafficEvent event = new BreakdownEvent("e", "AB", 0, 1, "car-1", 0.5, requested);

        requested.add(9);

        assertThat(event.getBlockedLaneIndexes()).containsExactlyInAnyOrder(0, 2);
        assertThatThrownBy(() -> event.getBlockedLaneIndexes().add(9))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void applyRejectsALaneIndexTheRoadDoesNotHave() {
        RoadNetwork network = createNetwork(new RoadCondition(0, 0, RoadStatus.OPEN));
        TrafficEvent event = new BreakdownEvent("e", "AB", 0, 1, "car-1", 0.5, Set.of(4));

        assertThatThrownBy(() -> event.apply(network))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lane count");
        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.SCHEDULED);
    }

    @Test
    void anEventWithoutBlockedLanesNamesNoLaneAtAll() {
        TrafficEvent event = new RoadClosureEvent("event-1", "AB", 0, 1);

        // Empty means "every lane" to the dynamics model rather than "no lane";
        // the distinction lives in Lane, not here.
        assertThat(event.getBlockedLaneIndexes()).isEmpty();
        assertThat(event.getPositionAlongRoad()).isEqualTo(0.5);
    }

    @Test
    void deprecatedTickAccessorsRoundTheSecondsValue() {
        TrafficEvent event = new RoadClosureEvent("event-1", "AB", 3.6, 4.4);

        assertThat(event.getStartTimeSeconds()).isEqualTo(3.6);
        assertThat(event.getStartTick()).isEqualTo(4);
        assertThat(event.getDurationTicks()).isEqualTo(4);
    }

    private RoadNetwork createNetwork(RoadCondition condition) {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
        network.addRoad(new Road("AB", "A", "B", 1, 50, 10, 0, condition));
        return network;
    }
}
