package team.traffic.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.RoutePreference;

import static org.assertj.core.api.Assertions.assertThat;

class RouteBuilderTest {

    @Test
    @DisplayName("edge weight for SHORTEST_DISTANCE returns road distance")
    void shortestDistanceWeight() {
        Road road = road("R1", 10.0, 50.0, RoadStatus.OPEN);
        double weight = RouteBuilder.calculateEdgeWeight(road, RoutePreference.SHORTEST_DISTANCE);
        assertThat(weight).isEqualTo(10.0);
    }

    @Test
    @DisplayName("edge weight for SHORTEST_TIME returns travel time")
    void shortestTimeWeight() {
        Road road = road("R1", 10.0, 50.0, RoadStatus.OPEN);
        double weight = RouteBuilder.calculateEdgeWeight(road, RoutePreference.SHORTEST_TIME);
        assertThat(weight).isEqualTo(0.2); // 10/50
    }

    @Test
    @DisplayName("edge weight for CONGESTION_AWARE returns travel cost")
    void congestionAwareWeight() {
        Road road = road("R1", 10.0, 50.0, RoadStatus.OPEN);
        double weight = RouteBuilder.calculateEdgeWeight(road, RoutePreference.CONGESTION_AWARE);
        assertThat(weight).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("road is usable when open and not at capacity")
    void roadIsUsable() {
        Road road = road("R1", 10.0, 50.0, RoadStatus.OPEN);
        assertThat(RouteBuilder.isRoadUsable(road)).isTrue();
    }

    @Test
    @DisplayName("road is not usable when blocked")
    void roadBlocked() {
        Road road = road("R1", 10.0, 50.0, RoadStatus.BLOCKED);
        assertThat(RouteBuilder.isRoadUsable(road)).isFalse();
    }

    @Test
    @DisplayName("road is not usable when closed")
    void roadClosed() {
        Road road = road("R1", 10.0, 50.0, RoadStatus.CLOSED);
        assertThat(RouteBuilder.isRoadUsable(road)).isFalse();
    }

    @Test
    @DisplayName("road is not usable when at capacity")
    void roadAtCapacity() {
        Road road = new Road("R1", "A", "B", 10, 50, 5, 5,
                new RoadCondition(0, 0, RoadStatus.OPEN));
        assertThat(RouteBuilder.isRoadUsable(road)).isFalse();
    }

    @Test
    @DisplayName("congested road is usable but has higher cost")
    void congestedRoadIsUsable() {
        Road road = new Road("R1", "A", "B", 10, 50, 100, 0,
                new RoadCondition(0.8, 0.5, RoadStatus.CONGESTED));
        assertThat(RouteBuilder.isRoadUsable(road)).isTrue();
        assertThat(RouteBuilder.calculateEdgeWeight(road, RoutePreference.CONGESTION_AWARE))
                .isGreaterThan(RouteBuilder.calculateEdgeWeight(road, RoutePreference.SHORTEST_TIME));
    }

    @Test
    @DisplayName("empty road condition defaults to open")
    void nullConditionDefaults() {
        Road road = new Road("R1", "A", "B", 10, 50, 100, 0, null);
        assertThat(RouteBuilder.isRoadUsable(road)).isTrue();
    }

    private static Road road(String id, double distance, double speedLimit, RoadStatus status) {
        return new Road(id, "A", "B", distance, speedLimit, 100, 0,
                new RoadCondition(0, 0, status));
    }
}
