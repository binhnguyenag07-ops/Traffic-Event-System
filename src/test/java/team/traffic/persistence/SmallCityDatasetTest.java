package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

class SmallCityDatasetTest {

    @Test
    void loadsSmallCityRoutingScenario() throws IOException {
        RoadNetwork network = new JsonMapLoader().load("data/small_city.json");

        assertThat(network.getIntersectionCount()).isEqualTo(6);
        assertThat(network.getRoadCount()).isEqualTo(17);
        assertThat(network.getRoad("R17").getCondition().getRoadStatus())
                .isEqualTo(RoadStatus.BLOCKED);
        assertThat(network.findOutgoingRoads("I1"))
                .extracting(road -> road.getEndIntersectionId())
                .containsExactly("I2", "I4", "I5");
    }

    @Test
    void providesJunctionNamesAndTwoDirectedLanesForVisualization() throws IOException {
        RoadNetwork network = new JsonMapLoader().load("data/small_city.json");

        assertThat(network.getIntersections().values())
                .allSatisfy(intersection -> assertThat(intersection.getName()).contains(" - "));
        assertThat(network.getRoads().values())
                .allSatisfy(road -> {
                    assertThat(road.getName()).isNotBlank();
                    assertThat(road.getPhysicalRoadId()).isNotBlank();
                    assertThat(road.getLaneCount()).isOne();
                    assertThat(road.getGeometry()).hasSizeGreaterThanOrEqualTo(2);
                });
        assertThat(network.findRoadsByPhysicalRoadId("THD-01"))
                .extracting(Road::getId)
                .containsExactly("R01", "R02");
    }

    @Test
    void providesMultipleUsableChoicesForRouting() throws IOException {
        RoadNetwork network = new JsonMapLoader().load("data/small_city.json");

        long usableRoads = network.getRoads().values().stream()
                .filter(road -> road.isAvailable())
                .count();
        long congestedRoads = network.getRoads().values().stream()
                .filter(road -> road.getCondition().getRoadStatus() == RoadStatus.CONGESTED)
                .count();

        assertThat(usableRoads).isEqualTo(16);
        assertThat(congestedRoads).isEqualTo(4);
    }
}
