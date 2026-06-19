package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import team.traffic.domain.network.LocationType;
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
    void coversEverySupportedLocationType() throws IOException {
        RoadNetwork network = new JsonMapLoader().load("data/small_city.json");
        Set<LocationType> types = network.getIntersections().values().stream()
                .map(intersection -> intersection.getLocationType())
                .collect(Collectors.toSet());

        assertThat(types).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(LocationType.class));
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
