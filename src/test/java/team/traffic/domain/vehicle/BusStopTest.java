package team.traffic.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;

class BusStopTest {
    @Test
    void busDwellsForCapacityBasedTimeAndServesStopOnlyOnce() {
        Bus bus = new Bus("bus", "A", "C", 40);
        BusStop stop = BusStop.forPassengerCapacity("B", bus.getPassengerCapacity());
        bus.setStops(List.of(stop));

        assertThat(bus.shouldHoldAtRoadExit("B", 20, 90)).isTrue();
        assertThat(bus.shouldHoldAtRoadExit("B", 0.5, 100)).isTrue();
        assertThat(bus.shouldHoldAtRoadExit("B", 0.5, 100 + stop.dwellSeconds() - 0.01)).isTrue();
        assertThat(bus.shouldHoldAtRoadExit("B", 0.5, 100 + stop.dwellSeconds())).isFalse();
        assertThat(bus.shouldHoldAtRoadExit("B", 0.5, 200)).isFalse();
        assertThat(bus.getServedStopJunctionIds()).containsExactly("B");
    }

    @Test
    void largerBusGetsLongerDefaultDwell() {
        assertThat(BusStop.forPassengerCapacity("B", 80).dwellSeconds())
                .isGreaterThan(BusStop.forPassengerCapacity("B", 20).dwellSeconds());
    }

    @Test
    void defaultStopsFollowEveryIntermediateJunctionInTheRoute() {
        Bus bus = new Bus("bus", "A", "D", 40);
        bus.setCurrentRoute(new Route(List.of(
                new RouteStep("A", "B", "AB", 1),
                new RouteStep("B", "C", "BC", 1),
                new RouteStep("C", "D", "CD", 1)), 3, 1, 1));

        bus.configureDefaultStopsFromRoute();

        assertThat(bus.getStops()).extracting(BusStop::junctionId).containsExactly("B", "C");
    }
}
