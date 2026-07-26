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
        RoadNetwork network = loader.load("data/maps/sample_map.json");

        assertThat(network.getIntersectionCount()).isEqualTo(3);
        assertThat(network.getRoadCount()).isEqualTo(3);
        assertThat(network.getIntersection("I2").getName()).isEqualTo("Library Junction");
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
                .hasMessageContaining("Required field is missing: x");
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
    void rejectsUnknownEnumValueAsInvalidJsonMap() throws IOException {
        Path file = writeJson("""
                {
                  "intersections": [
                    {"id":"I1","name":"First","x":0,"y":0,"locationType":"SPACEPORT"}
                  ],
                  "roads": []
                }
                """);

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("Invalid JSON map");
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

    @Test
    void loadsBuildingsWithMultipleRoadAccessNodes() throws IOException {
        Path file = writeJson("""
                {
                  "intersections": [
                    {"id":"A1","name":"West curb","x":0,"y":0,"locationType":"NORMAL"},
                    {"id":"A2","name":"East curb","x":1,"y":0,"locationType":"NORMAL"}
                  ],
                  "roads": [],
                  "buildings": [{
                    "id":"B1","name":"Office","type":"OFFICE","x":0.5,"y":0.2,
                    "latitude":10.77,"longitude":106.70,
                    "accessPoints":[
                      {"id":"B1-W","intersectionId":"A1","drivewayDistanceMetres":8},
                      {"id":"B1-E","intersectionId":"A2","drivewayDistanceMetres":12}
                    ]
                  }]
                }
                """);

        RoadNetwork network = loader.load(file.toString());

        assertThat(network.getBuildingCount()).isEqualTo(1);
        assertThat(network.getBuilding("B1").getAccessPoints())
                .extracting(access -> access.intersectionId())
                .containsExactly("A1", "A2");
    }

    @Test
    void rejectsBuildingThatReferencesMissingAccessNode() throws IOException {
        Path file = writeJson("""
                {
                  "intersections": [
                    {"id":"A1","name":"West curb","x":0,"y":0,"locationType":"NORMAL"}
                  ],
                  "roads": [],
                  "buildings": [{
                    "id":"B1","name":"Office","type":"OFFICE","x":0.5,"y":0.2,
                    "accessPoints":[{"id":"B1-E","intersectionId":"UNKNOWN"}]
                  }]
                }
                """);

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("Invalid JSON building at index 0")
                .hasMessageContaining("Building access intersection does not exist");
    }

    private Path writeJson(String content) throws IOException {
        Path file = tempDirectory.resolve("map.json");
        Files.writeString(file, content);
        return file;
    }
}
