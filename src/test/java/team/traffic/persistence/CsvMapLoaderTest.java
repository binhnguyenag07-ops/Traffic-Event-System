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

class CsvMapLoaderTest {
    private static final String HEADER = String.join(",",
            "type",
            "id",
            "name",
            "x",
            "y",
            "startIntersectionId",
            "endIntersectionId",
            "distance",
            "speedLimit",
            "capacity",
            "currentVehicleCount",
            "congestionLevel",
            "vehicleDensity",
            "roadStatus");

    private final CsvMapLoader loader = new CsvMapLoader();

    @TempDir
    Path tempDirectory;

    @Test
    void loadsProjectSampleMap() throws IOException {
        RoadNetwork network = loader.load("data/sample_map.csv");

        assertThat(network.getIntersectionCount()).isEqualTo(3);
        assertThat(network.getRoadCount()).isEqualTo(3);
        assertThat(network.getRoad("R2").getCondition().getCongestionLevel()).isEqualTo(0.55);
    }

    @Test
    void loadsRoadRecordsThatAppearBeforeIntersections() throws IOException {
        Path file = writeCsv(
                "ROAD,R1,,,,I1,I2,10,50,100,0,0,0,OPEN",
                "INTERSECTION,I1,First,0,0,,,,,,,,,",
                "INTERSECTION,I2,Second,1,0,,,,,,,,,");

        RoadNetwork network = loader.load(file.toString());

        assertThat(network.getRoad("R1")).isNotNull();
        assertThat(network.findOutgoingRoads("I1"))
                .extracting(road -> road.getEndIntersectionId())
                .containsExactly("I2");
    }

    @Test
    void rejectsUnsupportedRecordTypeWithRecordNumber() throws IOException {
        Path file = writeCsv("BUILDING,B1,Office,0,0,,,,,,,,,");

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("Unsupported CSV record type")
                .hasMessageContaining("record 1")
                .hasMessageContaining("BUILDING");
    }

    @Test
    void rejectsMissingRequiredHeaders() throws IOException {
        Path file = tempDirectory.resolve("missing-header.csv");
        Files.writeString(file, "type,id\nINTERSECTION,I1\n");

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("missing required headers")
                .hasMessageContaining("roadStatus");
    }

    @Test
    void reportsDuplicateIntersectionWithRecordContext() throws IOException {
        Path file = writeCsv(
                "INTERSECTION,I1,First,0,0,,,,,,,,,",
                "INTERSECTION,I1,Duplicate,1,1,,,,,,,,,");

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("Invalid INTERSECTION record 2")
                .hasMessageContaining("Duplicate intersection id: I1");
    }

    @Test
    void reportsMissingRoadEndpointWithRecordContext() throws IOException {
        Path file = writeCsv(
                "INTERSECTION,I1,First,0,0,,,,,,,,,",
                "ROAD,R1,,,,I1,UNKNOWN,10,50,100,0,0,0,OPEN");

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("Invalid ROAD record 2")
                .hasMessageContaining("End intersection does not exist: UNKNOWN");
    }

    @Test
    void rejectsRoadDensityThatDoesNotMatchVehicleCountAndCapacity() throws IOException {
        Path file = writeCsv(
                "INTERSECTION,I1,First,0,0,,,,,,,,,",
                "INTERSECTION,I2,Second,1,0,,,,,,,,,",
                "ROAD,R1,,,,I1,I2,10,50,10,5,0,0.1,OPEN");

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("Invalid ROAD record 3")
                .hasMessageContaining("Vehicle density for road R1 must match currentVehicleCount / capacity")
                .hasMessageContaining("expected 0.500 but was 0.100");
    }

    @Test
    void rejectsCsvWithoutIntersections() throws IOException {
        Path file = writeCsv();

        assertThatThrownBy(() -> loader.load(file.toString()))
                .isInstanceOf(InvalidMapException.class)
                .hasMessage("Map must contain at least one INTERSECTION record");
    }

    private Path writeCsv(String... records) throws IOException {
        Path file = tempDirectory.resolve("map.csv");
        String content = HEADER + System.lineSeparator()
                + String.join(System.lineSeparator(), records);
        Files.writeString(file, content);
        return file;
    }
}
