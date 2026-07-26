package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;

class MapLoaderConsistencyTest {

    @Test
    void csvAndJsonSamplesCreateEquivalentNetworks() throws IOException {
        RoadNetwork csvNetwork = new CsvMapLoader().load("data/maps/sample_map.csv");
        RoadNetwork jsonNetwork = new JsonMapLoader().load("data/maps/sample_map.json");

        assertThat(csvNetwork.getIntersections().keySet())
                .containsExactlyElementsOf(jsonNetwork.getIntersections().keySet());
        assertThat(csvNetwork.getRoads().keySet())
                .containsExactlyElementsOf(jsonNetwork.getRoads().keySet());

        for (String intersectionId : csvNetwork.getIntersections().keySet()) {
            assertEquivalent(
                    csvNetwork.getIntersection(intersectionId),
                    jsonNetwork.getIntersection(intersectionId));
        }
        for (String roadId : csvNetwork.getRoads().keySet()) {
            assertEquivalent(csvNetwork.getRoad(roadId), jsonNetwork.getRoad(roadId));
        }
    }

    private void assertEquivalent(Intersection actual, Intersection expected) {
        assertThat(actual.getName()).isEqualTo(expected.getName());
        assertThat(actual.getX()).isEqualTo(expected.getX());
        assertThat(actual.getY()).isEqualTo(expected.getY());
        assertThat(actual.getLocationType()).isEqualTo(expected.getLocationType());
    }

    private void assertEquivalent(Road actual, Road expected) {
        assertThat(actual.getStartIntersectionId()).isEqualTo(expected.getStartIntersectionId());
        assertThat(actual.getEndIntersectionId()).isEqualTo(expected.getEndIntersectionId());
        assertThat(actual.getDistance()).isEqualTo(expected.getDistance());
        assertThat(actual.getSpeedLimit()).isEqualTo(expected.getSpeedLimit());
        assertThat(actual.getCapacity()).isEqualTo(expected.getCapacity());
        assertThat(actual.getCurrentVehicleCount()).isEqualTo(expected.getCurrentVehicleCount());
        assertThat(actual.getCondition().getCongestionLevel())
                .isEqualTo(expected.getCondition().getCongestionLevel());
        assertThat(actual.getCondition().getVehicleDensity())
                .isEqualTo(expected.getCondition().getVehicleDensity());
        assertThat(actual.getCondition().getRoadStatus())
                .isEqualTo(expected.getCondition().getRoadStatus());
    }
}
