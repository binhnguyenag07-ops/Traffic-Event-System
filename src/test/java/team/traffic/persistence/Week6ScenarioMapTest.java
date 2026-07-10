package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

class Week6ScenarioMapTest {
    private final JsonMapLoader loader = new JsonMapLoader();

    @Test
    void loadsSmallCityMap() throws IOException {
        RoadNetwork network = loader.load("data/small_city.json");

        assertThat(network.getIntersections()).hasSize(6);
        assertThat(network.getRoads()).hasSize(17);
        assertThat(network.getRoad("R03").getCondition().getRoadStatus()).isEqualTo(RoadStatus.CONGESTED);
        assertThat(network.getRoad("R17").getCondition().getRoadStatus()).isEqualTo(RoadStatus.BLOCKED);
    }

    @Test
    void loadsCongestionDemoWithOpenAlternativeRoute() throws IOException {
        RoadNetwork network = loader.load("data/congestion_demo.json");

        assertThat(network.getIntersections()).hasSize(6);
        assertThat(network.getRoad("C03").getCondition().getRoadStatus()).isEqualTo(RoadStatus.CONGESTED);
        assertThat(network.getRoad("C05").getCondition().getRoadStatus()).isEqualTo(RoadStatus.CONGESTED);
        assertThat(network.getRoad("C07").isAvailable()).isTrue();
        assertThat(network.getRoad("C09").isAvailable()).isTrue();
        assertThat(network.getRoad("C11").isAvailable()).isTrue();
    }

    @Test
    void loadsAccidentDemoWithBlockedMainRoadAndOpenDetour() throws IOException {
        RoadNetwork network = loader.load("data/accident_demo.json");

        assertThat(network.getIntersections()).hasSize(6);
        assertThat(network.getRoad("A03").getCondition().getRoadStatus()).isEqualTo(RoadStatus.BLOCKED);
        assertThat(network.getRoad("A03").isAvailable()).isFalse();
        assertThat(network.getRoad("A07").isAvailable()).isTrue();
        assertThat(network.getRoad("A09").isAvailable()).isTrue();
        assertThat(network.getRoad("A11").isAvailable()).isTrue();
    }
}
