package team.traffic.domain.scene;

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
        double lateralOffsetMetres) {

    public SceneVehicle {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Vehicle id must not be blank.");
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new IllegalArgumentException("Vehicle position must be finite.");
        }
    }
}
