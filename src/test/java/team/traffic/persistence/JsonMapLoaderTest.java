package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.exception.InvalidMapException;

class JsonMapLoaderTest {
    private final JsonMapLoader loader = new JsonMapLoader();

    @TempDir
    Path tempDirectory;

    @Test
    void loadsProjectSampleMap() throws IOException {
        RoadNetwork network = loader.load("data/sample_map.json");

        assertThat(network.getIntersectionCount()).isEqualTo(3);
        assertThat(network.getRoadCount()).isEqualTo(3);
        assertThat(network.getIntersection("I2").getName()).isEqualTo("Trần Hưng Đạo - Yersin");
    }

    @Test
    void rejectsMissingRequiredNumericField() throws IOException {
        Path file = writeJson("""
                {
                  "intersections": [
                    {"id":"I1","name":"First","y":0,"locationType":"NORMAL"}
                  ],
                  "roads": []
                }
                """);

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("Invalid JSON intersection at index 0")
                .hasMessageContaining("Required field is missing: longitude (or legacy x)");
    }

    @Test
    void rejectsRoadWithMissingEndpoint() throws IOException {
        Path file = writeJson("""
                {
                  "intersections": [
                    {"id":"I1","name":"First","x":0,"y":0,"locationType":"NORMAL"}
                  ],
                  "roads": [
                    {
                      "id":"R1",
                      "startIntersectionId":"I1",
                      "endIntersectionId":"UNKNOWN",
                      "distance":10,
                      "speedLimit":50,
                      "capacity":100,
                      "currentVehicleCount":0,
                      "congestionLevel":0,
                      "vehicleDensity":0,
                      "roadStatus":"OPEN"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("Invalid JSON road at index 0")
                .hasMessageContaining("End intersection does not exist: UNKNOWN");
    }

    @Test
    void rejectsRoadDensityThatDoesNotMatchVehicleCountAndCapacity() throws IOException {
        Path file = writeJson("""
                {
                  "intersections": [
                    {"id":"I1","name":"First","x":0,"y":0,"locationType":"NORMAL"},
                    {"id":"I2","name":"Second","x":1,"y":0,"locationType":"NORMAL"}
                  ],
                  "roads": [
                    {
                      "id":"R1",
                      "startIntersectionId":"I1",
                      "endIntersectionId":"I2",
                      "distance":10,
                      "speedLimit":50,
                      "capacity":10,
                      "currentVehicleCount":5,
                      "congestionLevel":0,
                      "vehicleDensity":0.1,
                      "roadStatus":"OPEN"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("Invalid JSON road at index 0")
                .hasMessageContaining("Vehicle density for road R1 must match currentVehicleCount / capacity")
                .hasMessageContaining("expected 0.500 but was 0.100");
    }

    @Test
    void rejectsMissingRoadStatusInsteadOfDefaultingIt() throws IOException {
        Path file = writeJson("""
                {
                  "intersections": [
                    {"id":"I1","name":"First","x":0,"y":0,"locationType":"NORMAL"},
                    {"id":"I2","name":"Second","x":1,"y":0,"locationType":"NORMAL"}
                  ],
                  "roads": [
                    {
                      "id":"R1",
                      "startIntersectionId":"I1",
                      "endIntersectionId":"I2",
                      "distance":10,
                      "speedLimit":50,
                      "capacity":100,
                      "currentVehicleCount":0,
                      "congestionLevel":0,
                      "vehicleDensity":0
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("Required field is missing: roadStatus");
    }

    @Test
    void ignoresLegacyLocationTypeAndLoadsModernGeographicFields() throws IOException {
        Path file = writeJson("""
                {
                  "intersections": [
                    {"id":"I1","name":"Road A - Road B","longitude":106.69,"latitude":10.76,"locationType":"SPACEPORT"},
                    {"id":"I2","name":"Road B - Road C","longitude":106.70,"latitude":10.77}
                  ],
                  "roads": [{
                    "id":"R1","name":"Road B","physicalRoadId":"ROAD-B-01",
                    "startIntersectionId":"I1","endIntersectionId":"I2",
                    "distance":1,"speedLimit":50,"capacity":100,"currentVehicleCount":0,
                    "laneCount":1,
                    "geometry":[
                      {"longitude":106.69,"latitude":10.76},
                      {"longitude":106.70,"latitude":10.77}
                    ],
                    "congestionLevel":0,"vehicleDensity":0,"roadStatus":"OPEN"
                  }]
                }
                """);

        RoadNetwork network = loader.load(file.toString());

        assertThat(network.getIntersection("I1").getLongitude()).isEqualTo(106.69);
        assertThat(network.getRoad("R1").getPhysicalRoadId()).isEqualTo("ROAD-B-01");
        assertThat(network.getRoad("R1").getGeometry()).hasSize(2);
    }

    @Test
    void rejectsNullOrEmptyMapRoot() throws IOException {
        Path nullRoot = writeJson("null");

        assertThatThrownBy(() -> loader.load(nullRoot.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessage("JSON map root must be an object");

        Path emptyMap = tempDirectory.resolve("empty.json");
        Files.writeString(emptyMap, "{\"intersections\":[],\"roads\":[]}");

        assertThatThrownBy(() -> loader.load(emptyMap.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessage("Map must contain at least one intersection");
    }

    private Path writeJson(String content) throws IOException {
        Path file = tempDirectory.resolve("map.json");
        Files.writeString(file, content);
        return file;
    }
}
