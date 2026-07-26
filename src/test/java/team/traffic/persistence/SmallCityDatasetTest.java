package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

class SmallCityDatasetTest {

    @Test
    void loadsSmallCityRoutingScenario() throws IOException {
        RoadNetwork network = new JsonMapLoader().load("data/maps/small_city.json");

        assertThat(network.getIntersectionCount()).isEqualTo(6);
        assertThat(network.getRoadCount()).isEqualTo(17);
        assertThat(network.getRoad("R17").getCondition().getRoadStatus())
                .isEqualTo(RoadStatus.BLOCKED);
        assertThat(network.findOutgoingRoads("I1"))
                .extracting(road -> road.getEndIntersectionId())
                .containsExactly("I2", "I4", "I6");
    }

    @Test
    void usesRealHcmcCoordinatesAndStreetGeometry() throws IOException {
        RoadNetwork network = new JsonMapLoader().load("data/maps/small_city.json");

        assertThat(network.getIntersection("I1").getName()).isEqualTo("Bến Thành Market");
        assertThat(network.getIntersection("I1").getLatitude()).isCloseTo(10.772803,
                org.assertj.core.data.Offset.offset(1.0e-8));
        assertThat(network.getRoad("R01").getGeometry()).hasSizeGreaterThan(2);
        assertThat(network.getRoads().values())
                .allSatisfy(road -> assertThat(road.getGeometry()).hasSizeGreaterThan(1));
    }

    @Test
    void providesMultipleUsableChoicesForRouting() throws IOException {
        RoadNetwork network = new JsonMapLoader().load("data/maps/small_city.json");

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
