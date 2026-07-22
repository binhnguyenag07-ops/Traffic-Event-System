package team.traffic.domain.signal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.dynamics.Junction;

class SignalPlanTest {
    @Test
    void oppositeApproachesShareAPhase() {
        RoadNetwork network = new RoadNetwork();
        addIntersection(network, "J", 0, 0);
        addIntersection(network, "N", 0, 1);
        addIntersection(network, "S", 0, -1);
        addIntersection(network, "E", 1, 0);
        addIntersection(network, "W", -1, 0);
        addIncoming(network, "NJ", "N");
        addIncoming(network, "SJ", "S");
        addIncoming(network, "EJ", "E");
        addIncoming(network, "WJ", "W");

        TrafficSignal signal = SignalPlan.defaultFor(Junction.at(network, "J"), network);

        assertThat(signal.getPhases()).hasSize(2);
        assertThat(signal.getPhases()).anySatisfy(phase ->
                assertThat(phase.greenIncomingRoadIds()).containsExactlyInAnyOrder("NJ", "SJ"));
        assertThat(signal.getPhases()).anySatisfy(phase ->
                assertThat(phase.greenIncomingRoadIds()).containsExactlyInAnyOrder("EJ", "WJ"));
    }

    private static void addIntersection(RoadNetwork network, String id, double x, double y) {
        network.addIntersection(new Intersection(id, id, x, y, LocationType.NORMAL));
    }

    private static void addIncoming(RoadNetwork network, String id, String from) {
        network.addRoad(new Road(id, from, "J", 0.1, 40, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
    }
}
