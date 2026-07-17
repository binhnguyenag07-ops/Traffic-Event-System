package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

class SyntheticMapGeneratorTest {
    private final SyntheticMapGenerator generator = new SyntheticMapGenerator();

    @Test
    void rejectsNonPositiveGridDimensions() {
        assertThatThrownBy(() -> generator.generateGridMap(0, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rows and columns must be greater than 0");
        assertThatThrownBy(() -> generator.generateGridMap(2, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rows and columns must be greater than 0");
    }

    @Test
    void createsSingleIntersectionWithoutRoads() {
        RoadNetwork network = generator.generateGridMap(1, 1);

        assertThat(network.getIntersectionCount()).isEqualTo(1);
        assertThat(network.getRoadCount()).isZero();
        assertThat(network.getIntersection("I_0_0").getX()).isZero();
        assertThat(network.getIntersection("I_0_0").getY()).isZero();
    }

    @Test
    void createsExpectedNumberOfDirectedRoads() {
        int rows = 2;
        int columns = 3;
        int expectedRoads = 2 * (rows * (columns - 1) + columns * (rows - 1));

        RoadNetwork network = generator.generateGridMap(rows, columns);

        assertThat(network.getIntersectionCount()).isEqualTo(rows * columns);
        assertThat(network.getRoadCount()).isEqualTo(expectedRoads);
        assertThat(network.getIntersections().keySet()).hasSize(rows * columns);
        assertThat(network.getRoads().keySet()).hasSize(expectedRoads);
    }

    @Test
    void connectsEachNeighborPairInBothDirections() {
        RoadNetwork network = generator.generateGridMap(2, 2);

        assertThat(network.getRoad("I_0_0_TO_I_0_1")).isNotNull();
        assertThat(network.getRoad("I_0_1_TO_I_0_0")).isNotNull();
        assertThat(network.getRoad("I_0_0_TO_I_1_0")).isNotNull();
        assertThat(network.getRoad("I_1_0_TO_I_0_0")).isNotNull();
        assertThat(network.findOutgoingRoads("I_0_0"))
                .extracting(Road::getEndIntersectionId)
                .containsExactly("I_0_1", "I_1_0");
    }

    @Test
    void createsRoadsWithDocumentedDefaultValues() {
        Road road = generator.generateGridMap(1, 2).getRoad("I_0_0_TO_I_0_1");

        assertThat(road.getDistance()).isEqualTo(1.0);
        assertThat(road.getSpeedLimit()).isEqualTo(50.0);
        assertThat(road.getCapacity()).isEqualTo(100);
        assertThat(road.getCurrentVehicleCount()).isZero();
        assertThat(road.getCondition().getCongestionLevel()).isZero();
        assertThat(road.getCondition().getVehicleDensity()).isZero();
        assertThat(road.getCondition().getRoadStatus()).isEqualTo(RoadStatus.OPEN);
    }

}
