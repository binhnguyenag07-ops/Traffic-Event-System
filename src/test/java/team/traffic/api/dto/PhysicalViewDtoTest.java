package team.traffic.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.scene.LaneGeometry;
import team.traffic.domain.scene.PhysicalScene;
import team.traffic.domain.scene.SceneVehicle;

/**
 * The projection from latitude and longitude to local metres is one of the two
 * coordinate boundaries the feature relies on, so it is worth checking on its
 * own: the origin lands at (0, 0), north is +y, and east is +x.
 */
class PhysicalViewDtoTest {
    private static final double LAT = 10.0;
    private static final double LON = 106.0;
    private static final double METRES_PER_DEGREE = 111_320.0;

    @Test
    void theOriginProjectsToTheLocalZero() {
        PhysicalScene scene = sceneWithLaneFrom(new GeoPoint(LAT, LON), new GeoPoint(LAT, LON));

        PhysicalViewDto dto = PhysicalViewDto.of(scene, "t1", 5, 2.5);

        double[] first = dto.lanes().get(0).centreline().get(0);
        assertThat(first[0]).isCloseTo(0.0, within(1e-6));
        assertThat(first[1]).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void northIsPositiveYAndEastIsPositiveX() {
        GeoPoint origin = new GeoPoint(LAT, LON);
        GeoPoint oneDegreeNorth = new GeoPoint(LAT + 1.0, LON);
        GeoPoint oneDegreeEast = new GeoPoint(LAT, LON + 1.0);
        PhysicalScene scene = sceneWithLaneFrom(oneDegreeNorth, oneDegreeEast, origin);

        PhysicalViewDto dto = PhysicalViewDto.of(scene, "t1", 5, 2.5);
        double[] north = dto.lanes().get(0).centreline().get(0);
        double[] east = dto.lanes().get(0).centreline().get(1);

        assertThat(north[1])
                .as("a degree of latitude is about 111 km north")
                .isCloseTo(METRES_PER_DEGREE, within(1.0));
        assertThat(north[0]).isCloseTo(0.0, within(1.0));
        assertThat(east[0])
                .as("a degree of longitude here is 111 km times cos(latitude)")
                .isCloseTo(METRES_PER_DEGREE * Math.cos(Math.toRadians(LAT)), within(1.0));
        assertThat(east[1]).isCloseTo(0.0, within(1.0));
    }

    @Test
    void carriesTheFrameIdentityAndVehiclePosition() {
        GeoPoint origin = new GeoPoint(LAT, LON);
        SceneVehicle rider = new SceneVehicle(
                "V-1", "MOTORCYCLE", LAT, LON + 0.001, 90.0, 6.0, 2.1, 0.75, 1, 0.0);
        PhysicalScene scene = new PhysicalScene(
                origin, 200, List.of(), List.of(), List.of(), List.of(), List.of(rider));

        PhysicalViewDto dto = PhysicalViewDto.of(scene, "timeline-7", 42, 12.5);

        assertThat(dto.timelineId()).isEqualTo("timeline-7");
        assertThat(dto.frameId()).isEqualTo(42);
        assertThat(dto.elapsedSeconds()).isEqualTo(12.5);
        assertThat(dto.vehicles()).hasSize(1);
        PhysicalViewDto.VehicleDto drawn = dto.vehicles().get(0);
        assertThat(drawn.id()).isEqualTo("V-1");
        assertThat(drawn.type()).isEqualTo("MOTORCYCLE");
        assertThat(drawn.x())
                .as("0.001 degree east of the origin is a little over 100 m")
                .isGreaterThan(100.0);
        assertThat(drawn.y()).isCloseTo(0.0, within(1.0));
    }

    private PhysicalScene sceneWithLaneFrom(GeoPoint a, GeoPoint b) {
        return sceneWithLaneFrom(a, b, new GeoPoint(LAT, LON));
    }

    private PhysicalScene sceneWithLaneFrom(GeoPoint a, GeoPoint b, GeoPoint origin) {
        LaneGeometry lane = new LaneGeometry("R", 0, 3.5, List.of(a, b), true);
        return new PhysicalScene(
                origin, 200, List.of(lane), List.of(), List.of(), List.of(), List.of());
    }
}
