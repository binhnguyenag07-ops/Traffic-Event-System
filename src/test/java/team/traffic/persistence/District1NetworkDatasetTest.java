package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.service.impl.MapImportService;

class District1NetworkDatasetTest {

    @Test
    void defaultDistrictNetworkCoversTheVisibleMapAndPassesImportValidation() throws Exception {
        MapImportService service = new MapImportService((start, end) -> {
            throw new AssertionError("Bundled roads must already contain geometry");
        });

        try (var input = Files.newInputStream(Path.of("data/maps/district1_network.json"))) {
            var result = service.importJson("district1_network.json", input);

            assertThat(result.network().getIntersectionCount()).isGreaterThan(350);
            assertThat(result.network().getRoadCount()).isGreaterThan(700);
            assertThat(result.geometrySources()).containsEntry("FILE", result.network().getRoadCount());
            assertThat(result.network().getRoads().values())
                    .anyMatch(road -> road.getRoadClass() == RoadClass.RESIDENTIAL)
                    .anyMatch(road -> road.getRoadClass() == RoadClass.LIVING_STREET);
            assertThat(result.bounds().north() - result.bounds().south()).isGreaterThan(0.013);
            assertThat(result.bounds().east() - result.bounds().west()).isGreaterThan(0.017);
        }
    }

    @Test
    void physicalRoadDirectionsAndGeometryRemainInternallyConsistent() throws Exception {
        RoadNetwork network = loadBundledNetwork();
        var byPhysicalRoad = network.getRoads().values().stream()
                .collect(Collectors.groupingBy(Road::getPhysicalRoadId));

        assertThat(byPhysicalRoad).hasSize(560);
        assertThat(byPhysicalRoad.values()).allSatisfy(directions ->
                assertThat(directions).hasSizeBetween(1, 2));
        assertThat(byPhysicalRoad.values().stream().filter(roads -> roads.size() == 1))
                .hasSize(390);
        assertThat(byPhysicalRoad.values().stream().filter(roads -> roads.size() == 2))
                .hasSize(170);

        byPhysicalRoad.values().stream()
                .filter(directions -> directions.size() == 2)
                .forEach(directions -> {
                    Road forward = directions.get(0);
                    Road reverse = directions.get(1);
                    assertThat(reverse.getStartIntersectionId())
                            .isEqualTo(forward.getEndIntersectionId());
                    assertThat(reverse.getEndIntersectionId())
                            .isEqualTo(forward.getStartIntersectionId());
                    var reversedGeometry = new ArrayList<>(reverse.getGeometry());
                    java.util.Collections.reverse(reversedGeometry);
                    assertThat(reversedGeometry).containsExactlyElementsOf(forward.getGeometry());
                });
    }

    @Test
    void auditedOsmDirectionAndLaneCorrectionsRemainApplied() throws Exception {
        RoadNetwork network = loadBundledNetwork();

        // OSM way 35114033 is bidirectional; way 287132223 is one-way.
        assertThat(network.getRoad("W35114033-0F")).isNotNull();
        assertThat(network.getRoad("W35114033-0R")).isNotNull();
        assertThat(network.getRoad("W287132223-0F")).isNotNull();
        assertThat(network.getRoad("W287132223-0R")).isNull();

        assertThat(network.getRoad("W686500792-0F").getLaneCount()).isEqualTo(1);
        assertThat(network.getRoad("W971692799-0F").getLaneCount()).isEqualTo(3);
        assertThat(network.getRoad("W1184372243-0F").getLaneCount()).isEqualTo(3);
        assertThat(network.getRoad("W1267138238-0F").getLaneCount()).isEqualTo(3);
        assertThat(network.getRoad("W1267462154-0F").getLaneCount()).isEqualTo(3);
        assertThat(network.getRoad("W1267462155-0F").getLaneCount()).isEqualTo(3);
    }

    private RoadNetwork loadBundledNetwork() throws Exception {
        try (var input = Files.newInputStream(Path.of("data/maps/district1_network.json"))) {
            return new JsonMapLoader().load(input);
        }
    }
}
