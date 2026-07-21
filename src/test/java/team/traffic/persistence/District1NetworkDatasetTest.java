package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.RoadClass;
import team.traffic.service.impl.MapImportService;

class District1NetworkDatasetTest {

    @Test
    void defaultDistrictNetworkCoversTheVisibleMapAndPassesImportValidation() throws Exception {
        MapImportService service = new MapImportService((start, end) -> {
            throw new AssertionError("Bundled roads must already contain geometry");
        });

        try (var input = Files.newInputStream(Path.of("data/district1_network.json"))) {
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
}
