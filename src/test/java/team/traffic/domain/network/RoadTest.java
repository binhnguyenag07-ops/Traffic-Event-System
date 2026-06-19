package team.traffic.domain.network;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
