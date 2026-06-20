package team.traffic.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
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
    void trafficLightDelayAddsOneNormalizedImpact() {
        RoadNetwork network = createNetwork(new RoadCondition(0.1, 0, RoadStatus.OPEN));
        TrafficEvent event = new TrafficLightDelayEvent("event-1", "AB", 0, 10, 2);

        event.apply(network);

        assertThat(network.getRoad("AB").getCondition().getCongestionLevel()).isCloseTo(0.3, within(1e-9));
        event.resolve(network);
        assertThat(network.getRoad("AB").getCondition().getCongestionLevel()).isEqualTo(0.1);
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

    private RoadNetwork createNetwork(RoadCondition condition) {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("AB", "A", "B", 1, 50, 10, 0, condition));
        return network;
    }
}
