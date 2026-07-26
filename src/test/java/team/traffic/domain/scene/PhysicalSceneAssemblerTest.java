package team.traffic.domain.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.BuildingType;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.LandCover;
import team.traffic.domain.network.LandCoverType;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

/**
 * The region query has one job: return what is inside the circle and nothing
 * outside it. These tests place roads and vehicles either side of the boundary
 * and check which come back.
 */
class PhysicalSceneAssemblerTest {
    // Two junctions about 1.1 km apart on a line of latitude.
    private static final double WEST_LON = 106.000;
    private static final double EAST_LON = 106.010;
    private static final double LAT = 10.000;

    @Test
    void returnsOnlyTheRoadsThatReachIntoTheRadius() {
        RoadNetwork network = twoRoadNetwork();
        PhysicalSceneAssembler assembler = new PhysicalSceneAssembler(network);

        // A tight radius around the west junction: the west road reaches it, the
        // far east road does not.
        PhysicalScene scene = assembler.sceneAround(LAT, WEST_LON, 120, List.of());

        assertThat(scene.lanes()).allSatisfy(lane ->
                assertThat(lane.roadId()).isEqualTo("WEST"));
        assertThat(scene.lanes()).isNotEmpty();
    }

    @Test
    void awiderRadiusReachesBothRoads() {
        RoadNetwork network = twoRoadNetwork();
        PhysicalSceneAssembler assembler = new PhysicalSceneAssembler(network);

        PhysicalScene scene = assembler.sceneAround(LAT, WEST_LON, 2_000, List.of());

        assertThat(scene.lanes().stream().map(LaneGeometry::roadId).distinct())
                .containsExactlyInAnyOrder("WEST", "EAST");
    }

    @Test
    void keepsVehiclesInsideTheRadiusAndDropsThoseOutside() {
        RoadNetwork network = twoRoadNetwork();
        PhysicalSceneAssembler assembler = new PhysicalSceneAssembler(network);
        SceneVehicle near = vehicle("near", LAT, WEST_LON);
        SceneVehicle far = vehicle("far", LAT, EAST_LON);

        PhysicalScene scene = assembler.sceneAround(LAT, WEST_LON, 120, List.of(near, far));

        assertThat(scene.vehicles()).extracting(SceneVehicle::id).containsExactly("near");
    }

    @Test
    void aRadiusOverNothingReturnsAnEmptyButValidScene() {
        RoadNetwork network = twoRoadNetwork();
        PhysicalSceneAssembler assembler = new PhysicalSceneAssembler(network);

        // Far out to sea, nothing near.
        PhysicalScene scene = assembler.sceneAround(0.0, 0.0, 100, List.of());

        assertThat(scene.lanes()).isEmpty();
        assertThat(scene.junctions()).isEmpty();
        assertThat(scene.vehicles()).isEmpty();
        assertThat(scene.origin()).isEqualTo(new GeoPoint(0.0, 0.0));
    }

    @Test
    void vehicleFilteringIsConsistentWithRoadFiltering() {
        RoadNetwork network = twoRoadNetwork();
        PhysicalSceneAssembler assembler = new PhysicalSceneAssembler(network);
        SceneVehicle onEastRoad = vehicle("east", LAT, EAST_LON);

        // Radius that excludes the east road also excludes a vehicle sitting on it.
        PhysicalScene tight = assembler.sceneAround(LAT, WEST_LON, 120, List.of(onEastRoad));
        assertThat(tight.lanes().stream().map(LaneGeometry::roadId).distinct())
                .doesNotContain("EAST");
        assertThat(tight.vehicles()).isEmpty();

        // A radius that includes the east road includes the vehicle too.
        PhysicalScene wide = assembler.sceneAround(LAT, WEST_LON, 2_000, List.of(onEastRoad));
        assertThat(wide.lanes().stream().map(LaneGeometry::roadId).distinct()).contains("EAST");
        assertThat(wide.vehicles()).extracting(SceneVehicle::id).containsExactly("east");
    }

    @Test
    void rejectsANonPositiveRadiusAndANonFiniteCentre() {
        PhysicalSceneAssembler assembler = new PhysicalSceneAssembler(twoRoadNetwork());

        assertThatThrownBy(() -> assembler.sceneAround(LAT, WEST_LON, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("radius");
        assertThatThrownBy(() -> assembler.sceneAround(Double.NaN, WEST_LON, 100, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("centre");
    }


    @Test
    void carriesTheBuildingsInRangeWithTheirOutlines() {
        RoadNetwork network = twoRoadNetwork();
        network.addBuilding(building("B-near", LAT, WEST_LON + 0.0002));
        network.addBuilding(building("B-far", LAT, EAST_LON));
        PhysicalSceneAssembler assembler = new PhysicalSceneAssembler(network);

        PhysicalScene scene = assembler.sceneAround(LAT, WEST_LON, 120, List.of());

        assertThat(scene.buildings()).extracting(SceneBuilding::id).containsExactly("B-near");
        assertThat(scene.buildings().get(0).hasFootprint())
                .as("an outlined building is drawn as its real shape")
                .isTrue();
    }

    @Test
    void placesASignalHeadOnEachControlledApproach() {
        RoadNetwork network = twoRoadNetwork();
        PhysicalSceneAssembler assembler = new PhysicalSceneAssembler(network);
        // The junction "M" is where the west road ends and the east road starts.
        SignalSnapshot snapshot = new SignalSnapshot("M", Set.of("WEST"), 7.5);

        PhysicalScene scene = assembler.sceneAround(LAT, 106.005, 300, List.of(), List.of(snapshot));

        assertThat(scene.signals()).hasSize(1);
        SceneSignal signal = scene.signals().get(0);
        assertThat(signal.approaches()).extracting(SceneSignal.Approach::roadId)
                .as("only roads arriving at the junction are controlled")
                .containsExactly("WEST");
        assertThat(signal.approaches().get(0).green()).isTrue();
        assertThat(signal.approaches().get(0).secondsRemaining()).isEqualTo(7.5);
    }

    @Test
    void reportsNoSignalsForAJunctionTheFrameSaysNothingAbout() {
        RoadNetwork network = twoRoadNetwork();

        PhysicalScene scene = new PhysicalSceneAssembler(network)
                .sceneAround(LAT, WEST_LON, 300, List.of(), List.of());

        assertThat(scene.signals()).isEmpty();
    }

    @Test
    void carriesTheLandCoverThatReachesIntoTheRadius() {
        RoadNetwork network = twoRoadNetwork();
        network.addLandCover(new LandCover("L-near", LandCoverType.PARK, List.of(
                new GeoPoint(LAT, WEST_LON),
                new GeoPoint(LAT + 0.0002, WEST_LON),
                new GeoPoint(LAT + 0.0002, WEST_LON + 0.0002))));
        network.addLandCover(new LandCover("L-far", LandCoverType.WATER, List.of(
                new GeoPoint(LAT, EAST_LON),
                new GeoPoint(LAT + 0.0002, EAST_LON),
                new GeoPoint(LAT + 0.0002, EAST_LON + 0.0002))));

        PhysicalScene scene = new PhysicalSceneAssembler(network)
                .sceneAround(LAT, WEST_LON, 120, List.of());

        assertThat(scene.landCovers()).extracting(SceneLandCover::id).containsExactly("L-near");
        assertThat(scene.landCovers().get(0).type()).isEqualTo(LandCoverType.PARK);
    }

    @Test
    void hasNoLandCoverWhenTheMapRecordsNone() {
        // NORMAL fabric is the background, not a polygon, so an unmapped map is
        // simply empty here rather than full of filler.
        PhysicalScene scene = new PhysicalSceneAssembler(twoRoadNetwork())
                .sceneAround(LAT, WEST_LON, 300, List.of());

        assertThat(scene.landCovers()).isEmpty();
    }

    private Building building(String id, double lat, double lon) {
        return new Building(id, id, BuildingType.RESIDENTIAL, lon, lat, lat, lon,
                List.of(new BuildingAccessPoint(id + "-A0", "W", 20.0)),
                List.of(new GeoPoint(lat - 0.0001, lon - 0.0001),
                        new GeoPoint(lat + 0.0001, lon - 0.0001),
                        new GeoPoint(lat + 0.0001, lon + 0.0001),
                        new GeoPoint(lat - 0.0001, lon + 0.0001)));
    }

    private RoadNetwork twoRoadNetwork() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(junction("W", LAT, WEST_LON));
        network.addIntersection(junction("M", LAT, 106.005));
        network.addIntersection(junction("E", LAT, EAST_LON));
        network.addRoad(new Road("WEST", "West road", "WEST", "W", "M", 0.55, 50, 100, 0,
                2, 3.5, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(LAT, WEST_LON), new GeoPoint(LAT, 106.005)),
                RoadClass.SECONDARY));
        network.addRoad(new Road("EAST", "East road", "EAST", "M", "E", 0.55, 50, 100, 0,
                2, 3.5, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(LAT, 106.005), new GeoPoint(LAT, EAST_LON)),
                RoadClass.SECONDARY));
        return network;
    }

    private SceneVehicle vehicle(String id, double lat, double lon) {
        return new SceneVehicle(id, "CAR", lat, lon, 90.0, 8.0, 4.5, 1.8, 0, 0.0);
    }

    private Intersection junction(String id, double lat, double lon) {
        return new Intersection(id, id, lon, lat, LocationType.NORMAL, lat, lon);
    }
}
