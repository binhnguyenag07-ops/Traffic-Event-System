package team.traffic.domain.vehicle.pose;

import team.traffic.domain.network.GeoPoint;

/**
 * Where a vehicle physically is and which way it is pointing.
 *
 * <p>This is what a renderer needs in order to draw a vehicle at true scale:
 * a place, a heading to rotate by, and a size to draw. It replaces the
 * arrangement where the backend published a progress fraction along a road and
 * left the client to work out the geometry, which meant the same interpolation
 * had to be reimplemented by every consumer and could disagree with the
 * simulation.</p>
 *
 * @param position       where the centre of the vehicle is
 * @param headingDegrees compass bearing it is pointing, clockwise from north
 * @param lengthMetres   bumper to bumper
 * @param widthMetres    mirror to mirror
 * @param roadId         the road it is on, or {@code null} while crossing a junction
 * @param laneIndex      zero-based lane occupied on the road
 * @param lateralOffsetMetres signed offset from the road centreline
 * @param inJunction     true while it is between roads
 */
public record VehiclePose(
        GeoPoint position,
        double headingDegrees,
        double lengthMetres,
        double widthMetres,
        String roadId,
        int laneIndex,
        double lateralOffsetMetres,
        boolean inJunction) {

    public VehiclePose(GeoPoint position, double headingDegrees,
            double lengthMetres, double widthMetres, String roadId, boolean inJunction) {
        this(position, headingDegrees, lengthMetres, widthMetres,
                roadId, 0, 0.0, inJunction);
    }

    public VehiclePose {
        if (position == null) {
            throw new IllegalArgumentException("Pose position is required.");
        }
        if (!Double.isFinite(headingDegrees)) {
            throw new IllegalArgumentException("Heading must be finite.");
        }
        if (!Double.isFinite(lengthMetres) || lengthMetres <= 0) {
            throw new IllegalArgumentException("Length must be positive.");
        }
        if (!Double.isFinite(widthMetres) || widthMetres <= 0) {
            throw new IllegalArgumentException("Width must be positive.");
        }
        if (laneIndex < 0) {
            throw new IllegalArgumentException("Lane index must not be negative.");
        }
        if (!Double.isFinite(lateralOffsetMetres)) {
            throw new IllegalArgumentException("Lateral offset must be finite.");
        }
        headingDegrees = (headingDegrees % 360.0 + 360.0) % 360.0;
    }

    public double latitude() {
        return position.latitude();
    }

    public double longitude() {
        return position.longitude();
    }
}
