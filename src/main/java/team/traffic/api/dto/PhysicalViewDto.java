package team.traffic.api.dto;

import java.util.List;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.scene.JunctionGeometry;
import team.traffic.domain.scene.LaneConnector;
import team.traffic.domain.scene.LaneGeometry;
import team.traffic.domain.scene.PhysicalScene;
import team.traffic.domain.scene.SceneBuilding;
import team.traffic.domain.scene.SceneLandCover;
import team.traffic.domain.scene.SceneSignal;
import team.traffic.domain.scene.SceneVehicle;
import team.traffic.domain.scene.StopLine;

/**
 * The physical scene as the renderer receives it: the same geometry, projected
 * from latitude and longitude into local metres around the region's origin.
 *
 * <p>This projection is one of exactly two coordinate boundaries in the feature.
 * Everything the domain produces is geographic; everything the renderer draws is
 * in metres with the query point at the origin, x running east and y running
 * north. Converting here, once, means the browser never sees a latitude and so
 * can never mix the two frames — the mistake this boundary exists to prevent.</p>
 *
 * <p>{@code timelineId} and {@code frameId} pin the scene to the exact frame it
 * was built from, so the physical view and the macro map stay on one clock.</p>
 */
public record PhysicalViewDto(
        OriginDto origin,
        double radiusMetres,
        String timelineId,
        long frameId,
        double elapsedSeconds,
        List<LaneDto> lanes,
        List<StopLineDto> stopLines,
        List<ConnectorDto> connectors,
        List<JunctionDto> junctions,
        List<LandCoverDto> landCovers,
        List<BuildingDto> buildings,
        List<SignalDto> signals,
        List<VehicleDto> vehicles) {

    /** Metres in one degree of latitude, matching {@code PolylineGeometry}. */
    private static final double METRES_PER_DEGREE_LATITUDE = 111_320.0;

    public static PhysicalViewDto of(
            PhysicalScene scene, String timelineId, long frameId, double elapsedSeconds) {
        GeoPoint origin = scene.origin();
        return new PhysicalViewDto(
                new OriginDto(origin.latitude(), origin.longitude()),
                scene.radiusMetres(),
                timelineId,
                frameId,
                elapsedSeconds,
                scene.lanes().stream().map(lane -> lane(lane, origin)).toList(),
                scene.stopLines().stream().map(stopLine -> stopLine(stopLine, origin)).toList(),
                scene.connectors().stream().map(connector -> connector(connector, origin)).toList(),
                scene.junctions().stream().map(junction -> junction(junction, origin)).toList(),
                scene.landCovers().stream()
                        .map(cover -> new LandCoverDto(cover.id(), cover.type().name(),
                                project(cover.polygon(), origin)))
                        .toList(),
                scene.buildings().stream().map(building -> building(building, origin)).toList(),
                scene.signals().stream().map(signal -> signal(signal, origin)).toList(),
                scene.vehicles().stream().map(vehicle -> vehicle(vehicle, origin)).toList());
    }

    private static LaneDto lane(LaneGeometry lane, GeoPoint origin) {
        return new LaneDto(
                lane.roadId(), lane.laneIndex(), lane.widthMetres(), lane.oneway(),
                project(lane.centreline(), origin));
    }

    private static StopLineDto stopLine(StopLine stopLine, GeoPoint origin) {
        return new StopLineDto(
                stopLine.roadId(), project(stopLine.from(), origin), project(stopLine.to(), origin));
    }

    private static ConnectorDto connector(LaneConnector connector, GeoPoint origin) {
        return new ConnectorDto(
                connector.fromRoadId(), connector.fromLane(),
                connector.toRoadId(), connector.toLane(),
                project(connector.curve(), origin));
    }

    private static JunctionDto junction(JunctionGeometry junction, GeoPoint origin) {
        return new JunctionDto(junction.junctionId(), project(junction.pavement(), origin));
    }

    private static BuildingDto building(SceneBuilding building, GeoPoint origin) {
        return new BuildingDto(
                building.id(),
                building.name(),
                building.type().name(),
                project(building.centre(), origin),
                project(building.footprint(), origin));
    }

    private static SignalDto signal(SceneSignal signal, GeoPoint origin) {
        return new SignalDto(
                signal.junctionId(),
                signal.approaches().stream()
                        .map(approach -> new ApproachDto(
                                approach.roadId(),
                                project(approach.head(), origin),
                                approach.green(),
                                approach.secondsRemaining()))
                        .toList());
    }

    private static VehicleDto vehicle(SceneVehicle vehicle, GeoPoint origin) {
        double[] point = project(new GeoPoint(vehicle.latitude(), vehicle.longitude()), origin);
        return new VehicleDto(
                vehicle.id(), vehicle.type(), point[0], point[1],
                vehicle.headingDegrees(), vehicle.speedMps(),
                vehicle.lengthMetres(), vehicle.widthMetres(),
                vehicle.laneIndex(), vehicle.lateralOffsetMetres());
    }

    private static List<double[]> project(List<GeoPoint> points, GeoPoint origin) {
        return points.stream().map(point -> project(point, origin)).toList();
    }

    /** Projects one geographic point to local metres east/north of the origin. */
    private static double[] project(GeoPoint point, GeoPoint origin) {
        double north = (point.latitude() - origin.latitude()) * METRES_PER_DEGREE_LATITUDE;
        double eastScale = METRES_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(origin.latitude()));
        double east = (point.longitude() - origin.longitude()) * eastScale;
        return new double[] {east, north};
    }

    public record OriginDto(double latitude, double longitude) {
    }

    public record LaneDto(
            String roadId, int laneIndex, double widthMetres, boolean oneway, List<double[]> centreline) {
    }

    public record StopLineDto(String roadId, double[] from, double[] to) {
    }

    public record ConnectorDto(
            String fromRoadId, int fromLane, String toRoadId, int toLane, List<double[]> curve) {
    }

    public record JunctionDto(String junctionId, List<double[]> pavement) {
    }

    public record LandCoverDto(String id, String type, List<double[]> polygon) {
    }

    public record BuildingDto(
            String id, String name, String type, double[] centre, List<double[]> footprint) {
    }

    public record SignalDto(String junctionId, List<ApproachDto> approaches) {
    }

    public record ApproachDto(
            String roadId, double[] head, boolean green, double secondsRemaining) {
    }

    public record VehicleDto(
            String id, String type, double x, double y,
            double headingDegrees, double speedMps,
            double lengthMetres, double widthMetres,
            int laneIndex, double lateralOffsetMetres) {
    }
}
