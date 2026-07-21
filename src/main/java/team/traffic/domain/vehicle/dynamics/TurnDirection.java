package team.traffic.domain.vehicle.dynamics;

import team.traffic.domain.network.Intersection;

/**
 * Manoeuvre a vehicle performs when crossing a junction. Which movements have
 * to yield depends on this: a left turn crosses the opposing stream, a right
 * turn merges with it, and going straight crosses nothing.
 */
public enum TurnDirection {
    STRAIGHT,
    RIGHT,
    LEFT,
    U_TURN;

    /** Below this heading change the movement counts as going straight on. */
    private static final double STRAIGHT_TOLERANCE_DEGREES = 30.0;
    /** Above this heading change the movement counts as turning back. */
    private static final double U_TURN_THRESHOLD_DEGREES = 150.0;

    /**
     * Classifies the movement {@code from -> via -> to} from the map geometry.
     *
     * <p>Uses the planar {@code x}/{@code y} coordinates every intersection
     * carries, so it works for both synthetic grids and imported geographic
     * maps. Falls back to {@link #STRAIGHT} when the geometry is degenerate,
     * which keeps a malformed map from stalling the junction.</p>
     */
    public static TurnDirection of(Intersection from, Intersection via, Intersection to) {
        if (from == null || via == null || to == null) {
            return STRAIGHT;
        }

        double approachX = via.getX() - from.getX();
        double approachY = via.getY() - from.getY();
        double exitX = to.getX() - via.getX();
        double exitY = to.getY() - via.getY();

        double approachLength = Math.hypot(approachX, approachY);
        double exitLength = Math.hypot(exitX, exitY);
        if (approachLength == 0 || exitLength == 0) {
            return STRAIGHT;
        }

        double cross = approachX * exitY - approachY * exitX;
        double dot = approachX * exitX + approachY * exitY;
        double headingChangeDegrees = Math.toDegrees(Math.atan2(Math.abs(cross), dot));

        if (headingChangeDegrees <= STRAIGHT_TOLERANCE_DEGREES) {
            return STRAIGHT;
        }
        if (headingChangeDegrees >= U_TURN_THRESHOLD_DEGREES) {
            return U_TURN;
        }
        // With y pointing up, a positive cross product turns anticlockwise.
        return cross > 0 ? LEFT : RIGHT;
    }

    /**
     * Whether this movement cuts across a conflicting stream and therefore has
     * to wait for an acceptable gap.
     */
    public boolean crossesOpposingTraffic() {
        return this == LEFT || this == U_TURN;
    }
}
