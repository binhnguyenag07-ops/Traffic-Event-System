package team.traffic.domain.vehicle.dynamics;

/**
 * Distance to the vehicle ahead and how fast that vehicle is moving.
 *
 * <p>The gap is bumper-to-bumper: it is measured from the front of the
 * follower to the rear of the leader, so it is the space actually available
 * for braking. A negative raw distance means the two vehicles overlap, which
 * the car-following model treats as a zero gap.</p>
 *
 * @param gapMetres      free space ahead in metres, or {@link Double#POSITIVE_INFINITY}
 *                       when nothing is in front
 * @param leaderSpeedMps speed of the vehicle ahead in metres per second
 */
public record LeaderGap(double gapMetres, double leaderSpeedMps) {

    /** No vehicle ahead, so the follower is limited only by its desired speed. */
    public static final LeaderGap FREE_ROAD = new LeaderGap(Double.POSITIVE_INFINITY, 0.0);

    public LeaderGap {
        if (Double.isNaN(gapMetres)) {
            throw new IllegalArgumentException("Gap must not be NaN.");
        }
        if (!Double.isFinite(leaderSpeedMps) || leaderSpeedMps < 0) {
            throw new IllegalArgumentException("Leader speed must be finite and non-negative.");
        }
        gapMetres = Math.max(0.0, gapMetres);
    }

    /** A standing obstacle such as a red light, a queue tail, or a closed road. */
    public static LeaderGap stoppedObstacleAt(double distanceMetres) {
        return new LeaderGap(distanceMetres, 0.0);
    }

    public boolean isFreeRoad() {
        return Double.isInfinite(gapMetres);
    }

    /** Returns the tighter of two constraints, which is the one that governs. */
    public LeaderGap moreRestrictiveOf(LeaderGap other) {
        if (other == null) {
            return this;
        }
        return other.gapMetres < gapMetres ? other : this;
    }
}
