package team.traffic.api.dto;

import java.util.List;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.scene.PhysicalSceneState;
import team.traffic.domain.scene.SceneSignal;
import team.traffic.domain.scene.SceneVehicle;

/**
 * Frame-sized update for a physical map whose geometry is already loaded.
 */
public record PhysicalViewStateDto(
        OriginDto origin,
        double radiusMetres,
        String timelineId,
        long frameId,
        double elapsedSeconds,
        long playbackIntervalMs,
        List<SignalDto> signals,
        List<VehicleDto> vehicles) {

    private static final double METRES_PER_DEGREE_LATITUDE = 111_320.0;

    public static PhysicalViewStateDto of(
            PhysicalSceneState state,
            String timelineId,
            long frameId,
            double elapsedSeconds,
            long playbackIntervalMs) {
        GeoPoint origin = state.origin();
        return new PhysicalViewStateDto(
                new OriginDto(origin.latitude(), origin.longitude()),
                state.radiusMetres(),
                timelineId,
                frameId,
                elapsedSeconds,
                playbackIntervalMs,
                state.signals().stream().map(signal -> signal(signal, origin)).toList(),
                state.vehicles().stream().map(vehicle -> vehicle(vehicle, origin)).toList());
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
                vehicle.id(),
                vehicle.type(),
                point[0],
                point[1],
                vehicle.headingDegrees(),
                vehicle.speedMps(),
                vehicle.lengthMetres(),
                vehicle.widthMetres(),
                vehicle.laneIndex(),
                vehicle.lateralOffsetMetres(),
                vehicle.roadId(),
                vehicle.roadProgress(),
                vehicle.inJunction(),
                vehicle.junctionFromRoadId(),
                vehicle.junctionToRoadId(),
                vehicle.journeyPhase().name(),
                vehicle.originPlaceKind() == null ? null : vehicle.originPlaceKind().name(),
                vehicle.originPlaceId(),
                vehicle.originAccessIntersectionId(),
                vehicle.destinationPlaceKind() == null
                        ? null : vehicle.destinationPlaceKind().name(),
                vehicle.destinationPlaceId(),
                vehicle.destinationAccessIntersectionId());
    }

    private static double[] project(GeoPoint point, GeoPoint origin) {
        double north = (point.latitude() - origin.latitude()) * METRES_PER_DEGREE_LATITUDE;
        double eastScale = METRES_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(origin.latitude()));
        double east = (point.longitude() - origin.longitude()) * eastScale;
        return new double[] {east, north};
    }

    public record OriginDto(double latitude, double longitude) {
    }

    public record SignalDto(String junctionId, List<ApproachDto> approaches) {
    }

    public record ApproachDto(
            String roadId, double[] head, boolean green, double secondsRemaining) {
    }

    public record VehicleDto(
            String id,
            String type,
            double x,
            double y,
            double headingDegrees,
            double speedMps,
            double lengthMetres,
            double widthMetres,
            int laneIndex,
            double lateralOffsetMetres,
            String roadId,
            double roadProgress,
            boolean inJunction,
            String junctionFromRoadId,
            String junctionToRoadId,
            String journeyPhase,
            String originPlaceKind,
            String originPlaceId,
            String originAccessIntersectionId,
            String destinationPlaceKind,
            String destinationPlaceId,
            String destinationAccessIntersectionId) {
    }
}
