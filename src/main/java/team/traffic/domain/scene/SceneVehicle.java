package team.traffic.domain.scene;

import team.traffic.domain.demand.PlaceKind;
import team.traffic.domain.vehicle.JourneyPhase;

/**
 * A vehicle as the physical renderer needs to see it: where it is, which way it
 * points, and how big it is.
 *
 * <p>This is a snapshot for drawing, not the {@code Vehicle} itself. It carries
 * a resolved geographic position rather than a road and a progress fraction, so
 * the renderer never has to re-run pose resolution, and it carries the real
 * lateral offset so a filtering motorcycle is drawn between the lanes it is
 * actually riding between rather than snapped to one.</p>
 *
 * @param id                  stable identity, for matching across frames
 * @param type                vehicle type name, for colour and sprite choice
 * @param latitude            resolved position
 * @param longitude           resolved position
 * @param headingDegrees      compass bearing it faces
 * @param speedMps            current speed, for trails and effects
 * @param lengthMetres        drawn length
 * @param widthMetres         drawn width
 * @param laneIndex           lane it is nearest to
 * @param lateralOffsetMetres signed offset from the road centreline
 * @param roadId              occupied road, or null while crossing a junction
 * @param roadProgress        progress along the occupied road
 * @param inJunction          whether the vehicle is between two roads
 * @param junctionFromRoadId  approach road for the current junction crossing
 * @param junctionToRoadId    exit road for the current junction crossing
 * @param journeyPhase        off-network/on-road lifecycle phase
 * @param originPlaceKind     resolved source kind
 * @param originPlaceId       resolved source id
 * @param originAccessIntersectionId graph node joining the source to the road
 * @param destinationPlaceKind resolved current destination kind
 * @param destinationPlaceId  resolved current destination id
 * @param destinationAccessIntersectionId graph node joining the destination
 */
public record SceneVehicle(
        String id,
        String type,
        double latitude,
        double longitude,
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
        JourneyPhase journeyPhase,
        PlaceKind originPlaceKind,
        String originPlaceId,
        String originAccessIntersectionId,
        PlaceKind destinationPlaceKind,
        String destinationPlaceId,
        String destinationAccessIntersectionId) {

    public SceneVehicle(
            String id,
            String type,
            double latitude,
            double longitude,
            double headingDegrees,
            double speedMps,
            double lengthMetres,
            double widthMetres,
            int laneIndex,
            double lateralOffsetMetres) {
        this(
                id, type, latitude, longitude, headingDegrees, speedMps,
                lengthMetres, widthMetres, laneIndex, lateralOffsetMetres,
                null, 0.0, false, null, null,
                JourneyPhase.ON_ROAD, null, null, null, null, null, null);
    }

    /** Compatibility constructor for scene producers without trip-place metadata. */
    public SceneVehicle(
            String id,
            String type,
            double latitude,
            double longitude,
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
            String junctionToRoadId) {
        this(
                id, type, latitude, longitude, headingDegrees, speedMps,
                lengthMetres, widthMetres, laneIndex, lateralOffsetMetres,
                roadId, roadProgress, inJunction, junctionFromRoadId, junctionToRoadId,
                roadId == null ? JourneyPhase.AT_JUNCTION : JourneyPhase.ON_ROAD,
                null, null, null, null, null, null);
    }

    public SceneVehicle {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Vehicle id must not be blank.");
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new IllegalArgumentException("Vehicle position must be finite.");
        }
        if (!Double.isFinite(roadProgress) || roadProgress < 0 || roadProgress > 1) {
            throw new IllegalArgumentException("Road progress must be between zero and one.");
        }
        if (journeyPhase == null) {
            throw new IllegalArgumentException("Journey phase is required.");
        }
    }
}
