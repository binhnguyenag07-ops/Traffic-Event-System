package team.traffic.domain.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import org.junit.jupiter.api.Test;

class RoadTest {

    @Test
    void rejectsNonFiniteDistance() {
        assertThatThrownBy(() -> createRoad(Double.NaN, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Distance must be finite and greater than 0");

        assertThatThrownBy(() -> createRoad(Double.POSITIVE_INFINITY, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Distance must be finite and greater than 0");
    }

    @Test
    void rejectsNonFiniteSpeedLimit() {
        assertThatThrownBy(() -> createRoad(10, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Speed limit must be finite and greater than 0");

        assertThatThrownBy(() -> createRoad(10, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Speed limit must be finite and greater than 0");
    }

    @Test
    @SuppressWarnings("deprecation")
    void calculatesFreeFlowTravelTimeInHours() {
        Road road = createRoad(10, 50);

        assertThat(road.getFreeFlowTravelTimeHours()).isEqualTo(0.2);
        assertThat(road.getFreeFlowTravelTime()).isEqualTo(0.2);
    }

    @Test
    void calculatesCostWithoutCountingTrafficLoadTwice() {
        Road road = new Road(
                "R1",
                "I1",
                "I2",
                10,
                50,
                100,
                50,
                new RoadCondition(0.3, 0.5, RoadStatus.CONGESTED));

        // 0.2 free-flow hours * (1 + 0.3 congestion + 0.5 density)
        assertThat(road.calculateTravelCost()).isCloseTo(0.36, offset(1.0e-12));
        assertThat(road.getCapacityUsage()).isEqualTo(0.5);
    }

    @Test
    void returnsInfiniteCostWhenRoadIsNotAvailable() {
        Road blockedRoad = roadWithStatus(RoadStatus.BLOCKED, 0);
        Road closedRoad = roadWithStatus(RoadStatus.CLOSED, 0);
        Road fullRoad = roadWithStatus(RoadStatus.OPEN, 100);

        assertThat(blockedRoad.calculateTravelCost()).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(closedRoad.calculateTravelCost()).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(fullRoad.calculateTravelCost()).isEqualTo(Double.POSITIVE_INFINITY);
    }

    private Road createRoad(double distance, double speedLimit) {
        return new Road(
                "R1",
                "I1",
                "I2",
                distance,
                speedLimit,
                100,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }

    private Road roadWithStatus(RoadStatus status, int currentVehicleCount) {
        return new Road(
                "R1",
                "I1",
                "I2",
                10,
                50,
                100,
                currentVehicleCount,
                new RoadCondition(0, 0, status));
    }
}
