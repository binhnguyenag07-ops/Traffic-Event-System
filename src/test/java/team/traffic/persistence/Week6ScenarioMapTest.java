package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

class Week6ScenarioMapTest {
    private final JsonMapLoader loader = new JsonMapLoader();
    private final MapSaver saver = new MapSaver();

    @TempDir
    Path tempDirectory;

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

        assertVisualizationDataIsConsistent(network);
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

        assertVisualizationDataIsConsistent(network);
        assertThat(network.getIntersections()).hasSize(6);
        assertThat(network.getRoad("A03").getCondition().getRoadStatus()).isEqualTo(RoadStatus.BLOCKED);
        assertThat(network.getRoad("A03").isAvailable()).isFalse();
        assertThat(network.getRoad("A07").isAvailable()).isTrue();
        assertThat(network.getRoad("A09").isAvailable()).isTrue();
        assertThat(network.getRoad("A11").isAvailable()).isTrue();
    }

    @Test
    void preservesVisualizationFieldsAcrossScenarioSaveLoadRoundTrips() throws IOException {
        assertScenarioRoundTrip("data/congestion_demo.json", "congestion-round-trip.json");
        assertScenarioRoundTrip("data/accident_demo.json", "accident-round-trip.json");
    }

    private void assertScenarioRoundTrip(String source, String outputName) throws IOException {
        RoadNetwork original = loader.load(source);
        Path output = tempDirectory.resolve(outputName);
        saver.save(original, output.toString());

        RoadNetwork restored = loader.load(output.toString());

        assertThat(restored.getIntersectionCount()).isEqualTo(original.getIntersectionCount());
        assertThat(restored.getRoadCount()).isEqualTo(original.getRoadCount());
        for (Road originalRoad : original.getRoads().values()) {
            Road restoredRoad = restored.getRoad(originalRoad.getId());
            assertThat(restoredRoad.getCurrentVehicleCount())
                    .isEqualTo(originalRoad.getCurrentVehicleCount());
            assertThat(restoredRoad.getCondition().getVehicleDensity())
                    .isEqualTo(originalRoad.getCondition().getVehicleDensity());
            assertThat(restoredRoad.getCondition().getCongestionLevel())
                    .isEqualTo(originalRoad.getCondition().getCongestionLevel());
            assertThat(restoredRoad.getCondition().getRoadStatus())
                    .isEqualTo(originalRoad.getCondition().getRoadStatus());
        }
    }

    private void assertVisualizationDataIsConsistent(RoadNetwork network) {
        Set<String> coordinates = new HashSet<>();
        for (Intersection intersection : network.getIntersections().values()) {
            assertThat(intersection.getX()).isFinite();
            assertThat(intersection.getY()).isFinite();
            assertThat(coordinates.add(intersection.getX() + ":" + intersection.getY()))
                    .as("intersection coordinates must be unique: %s", intersection.getId())
                    .isTrue();
        }

        for (Road road : network.getRoads().values()) {
            assertThat(network.containsIntersection(road.getStartIntersectionId())).isTrue();
            assertThat(network.containsIntersection(road.getEndIntersectionId())).isTrue();
            assertThat(road.getCondition().getVehicleDensity())
                    .isCloseTo(road.getCapacityUsage(), withinDensityTolerance());
        }
    }

    private org.assertj.core.data.Offset<Double> withinDensityTolerance() {
        return org.assertj.core.data.Offset.offset(1.0e-3);
    }
}
