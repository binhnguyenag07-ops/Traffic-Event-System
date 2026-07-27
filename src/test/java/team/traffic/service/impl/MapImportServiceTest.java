package team.traffic.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.MovementPermission;
import team.traffic.domain.network.RoadClass;
import team.traffic.exception.InvalidMapException;
import team.traffic.service.RoadGeometryProvider;

class MapImportServiceTest {

    @Test
    void preservesFileGeometryAndRoutesMissingGeometry() throws Exception {
        RoadGeometryProvider provider = (start, end) -> new RoadGeometryProvider.RoadGeometryResult(
                List.of(new GeoPoint(start.getLatitude(), start.getLongitude()),
                        new GeoPoint(10.775, 106.702),
                        new GeoPoint(end.getLatitude(), end.getLongitude())),
                1.25, "OSRM");
        MapImportService service = new MapImportService(provider);

        var result = service.importJson("district.json", input(validMap()));

        assertThat(result.network().getRoad("AB").getGeometry()).hasSize(2);
        assertThat(result.network().getRoad("BA").getGeometry()).hasSize(3);
        assertThat(result.network().getRoad("BA").getDistance()).isEqualTo(1.25);
        assertThat(result.network().getRoad("AB").getRoadClass()).isEqualTo(RoadClass.RESIDENTIAL);
        assertThat(result.network().getRoad("BA").getRoadClass()).isEqualTo(RoadClass.UNCLASSIFIED);
        assertThat(result.geometrySources()).containsEntry("FILE", 1).containsEntry("OSRM", 1);
    }

    @Test
    void rejectsMapsOutsideOperatingArea() {
        String invalid = validMap().replace("10.770", "11.770");
        MapImportService service = new MapImportService((start, end) -> null);

        assertThatThrownBy(() -> service.importJson("outside.json", input(invalid)))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("outside the HCMC operating area");
    }

    @Test
    void rejectsNonJsonFiles() {
        MapImportService service = new MapImportService((start, end) -> null);

        assertThatThrownBy(() -> service.importJson("map.csv", input(validMap())))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining(".json");
    }

    @Test
    void geometryEnrichmentPreservesRoadMetadataAndMovementRules() throws Exception {
        MapImportService service = new MapImportService((start, end) ->
                new RoadGeometryProvider.RoadGeometryResult(
                        List.of(
                                new GeoPoint(start.getLatitude(), start.getLongitude()),
                                new GeoPoint(end.getLatitude(), end.getLongitude())),
                        1.0,
                        "STRAIGHT"));
        String map = validMap().replace(
                "\"roads\": [",
                """
                "junctionMovements": [
                  {
                    "junctionId":"B",
                    "fromRoadId":"AB",
                    "toRoadId":"BA",
                    "direction":"U_TURN",
                    "permission":"FORBIDDEN",
                    "penaltySeconds":31
                  }
                ],
                "roads": [""").replace(
                "\"id\":\"AB\",\"startIntersectionId\"",
                "\"id\":\"AB\",\"name\":\"Alpha\",\"physicalRoadId\":\"P\","
                        + "\"laneCount\":2,\"laneWidthMetres\":3.2,"
                        + "\"startIntersectionId\"")
                .replace(
                        "\"id\":\"BA\",\"startIntersectionId\"",
                        "\"id\":\"BA\",\"name\":\"Alpha\",\"physicalRoadId\":\"P\","
                                + "\"laneCount\":2,\"laneWidthMetres\":3.2,"
                                + "\"startIntersectionId\"");

        var result = service.importJson("metadata.json", input(map));

        var road = result.network().getRoad("AB");
        assertThat(road.getName()).isEqualTo("Alpha");
        assertThat(road.getPhysicalRoadId()).isEqualTo("P");
        assertThat(road.getLaneCount()).isEqualTo(2);
        assertThat(road.getLaneWidthMetres()).isEqualTo(3.2);
        assertThat(result.network().getMovementGraph()
                .find("B", "AB", "BA"))
                .get()
                .extracting(movement -> movement.permission())
                .isEqualTo(MovementPermission.FORBIDDEN);
    }

    private ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private String validMap() {
        return """
                {
                  "intersections": [
                    {"id":"A","name":"A","x":0,"y":0,"locationType":"NORMAL","latitude":10.770,"longitude":106.700},
                    {"id":"B","name":"B","x":1,"y":1,"locationType":"NORMAL","latitude":10.780,"longitude":106.710}
                  ],
                  "roads": [
                    {"id":"AB","startIntersectionId":"A","endIntersectionId":"B","distance":1,"speedLimit":40,"capacity":10,"currentVehicleCount":0,"congestionLevel":0,"vehicleDensity":0,"roadStatus":"OPEN","roadClass":"RESIDENTIAL","geometry":[[10.770,106.700],[10.780,106.710]]},
                    {"id":"BA","startIntersectionId":"B","endIntersectionId":"A","distance":1,"speedLimit":40,"capacity":10,"currentVehicleCount":0,"congestionLevel":0,"vehicleDensity":0,"roadStatus":"OPEN"}
                  ]
                }
                """;
    }
}
