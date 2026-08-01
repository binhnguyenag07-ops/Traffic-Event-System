package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.GeoPoint;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.BuildingType;

class MapSaverTest {
    private final MapSaver saver = new MapSaver();
    private final JsonMapLoader loader = new JsonMapLoader();

    @TempDir
    Path tempDirectory;

    @Test
    void savesAndLoadsEquivalentNetworkInStableIdOrder() throws IOException {
        RoadNetwork original = createNetworkInReverseIdOrder();
        Path output = tempDirectory.resolve("nested").resolve("saved-map.json");

        saver.save(original, output.toString());

        assertThat(output).exists();
        String json = Files.readString(output);
        assertThat(json.indexOf("\"id\" : \"I1\"")).isLessThan(json.indexOf("\"id\" : \"I2\""));
        assertThat(json.indexOf("\"id\" : \"R1\"")).isLessThan(json.indexOf("\"id\" : \"R2\""));
        assertThat(json).doesNotContain("locationType");

        RoadNetwork restored = loader.load(output.toString());
        assertThat(restored.getIntersectionCount()).isEqualTo(original.getIntersectionCount());
        assertThat(restored.getRoadCount()).isEqualTo(original.getRoadCount());
        assertThat(restored.getRoad("R1").getDistance()).isEqualTo(10);
        assertThat(restored.getRoad("R1").getGeometry())
                .containsExactly(new GeoPoint(10.77, 106.69), new GeoPoint(10.78, 106.70));
        assertThat(restored.getRoad("R1").getRoadClass()).isEqualTo(RoadClass.PRIMARY);
        assertThat(restored.getRoad("R1").getName()).isEqualTo("Main boulevard");
        assertThat(restored.getRoad("R1").getPhysicalRoadId()).isEqualTo("main-boulevard");
        assertThat(restored.getRoad("R1").getLaneCount()).isEqualTo(4);
        assertThat(restored.getRoad("R1").getLaneWidthMetres()).isEqualTo(3.2);
        assertThat(restored.getRoad("R1").getRoadWidthMetres()).isEqualTo(12.8);
        assertThat(restored.getRoad("R2").getCondition().getRoadStatus())
                .isEqualTo(RoadStatus.CONGESTED);
        assertThat(restored.getBuilding("B1").getType()).isEqualTo(BuildingType.OFFICE);
        assertThat(restored.getBuilding("B1").getAccessPoints())
                .extracting(BuildingAccessPoint::intersectionId)
                .containsExactly("I1", "I2");
    }

    @Test
    void rejectsMissingSaveArguments() {
        RoadNetwork network = new RoadNetwork();

        assertThatThrownBy(() -> saver.save(null, tempDirectory.resolve("map.json").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Road network is required");
        assertThatThrownBy(() -> saver.save(network, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Map file path is required");
    }

    private RoadNetwork createNetworkInReverseIdOrder() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("I2", "Second", 1, 0));
        network.addIntersection(new Intersection("I1", "First", 0, 0));
        network.addRoad(new Road(
                "R2", "I2", "I1", 12, 40, 80, 20,
                new RoadCondition(0.4, 0.25, RoadStatus.CONGESTED)));
        network.addRoad(new Road(
                "R1", "Main boulevard", "main-boulevard", "I1", "I2", 10, 50, 100, 0,
                4, 3.2, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(10.77, 106.69), new GeoPoint(10.78, 106.70)),
                RoadClass.PRIMARY));
        network.addBuilding(new Building(
                "B1", "Office", BuildingType.OFFICE, 0.5, 0.2, 10.77, 106.70,
                List.of(
                        new BuildingAccessPoint("B1-I1", "I1", 10),
                        new BuildingAccessPoint("B1-I2", "I2", 15))));
        return network;
    }
}
