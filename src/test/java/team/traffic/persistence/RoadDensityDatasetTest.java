package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

class RoadDensityDatasetTest {

    @Test
    void loadsDensityDemoWithLowMediumHighAndFullRoads() throws IOException {
        RoadNetwork network = new JsonMapLoader().load("data/maps/density_demo.json");

        assertThat(network.getIntersectionCount()).isEqualTo(4);
        assertThat(network.getRoadCount()).isEqualTo(4);
        assertThat(network.getAverageVehicleDensity()).isEqualTo(0.6);
        assertThat(network.findRoadsAtOrAboveDensity(0.75))
                .extracting(Road::getId)
                .containsExactly("DR3", "DR4");

        Road fullRoad = network.getRoad("DR4");
        assertThat(fullRoad.getCurrentVehicleCount()).isEqualTo(fullRoad.getCapacity());
        assertThat(fullRoad.getCondition().getVehicleDensity()).isEqualTo(1.0);
        assertThat(fullRoad.getCondition().getRoadStatus()).isEqualTo(RoadStatus.CONGESTED);
        assertThat(fullRoad.isAvailable()).isFalse();
    }
}
