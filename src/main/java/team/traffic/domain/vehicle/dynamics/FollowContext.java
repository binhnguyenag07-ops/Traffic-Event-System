package team.traffic.domain.vehicle.dynamics;

/**
 * Everything a car-following model needs to choose a speed for one vehicle in
 * one time step. All speeds are metres per second and all distances metres, so
 * the model itself never performs a unit conversion.
 *
 * @param currentSpeedMps    speed at the start of the step
 * @param desiredSpeedMps    speed the driver would hold on an empty road
 * @param accelerationMps2   acceleration the vehicle can produce
 * @param decelerationMps2   comfortable braking rate
 * @param driverProfile      behaviour parameters of the driver
 * @param leaderGap          space and speed of the vehicle ahead
 * @param stopDistanceMetres distance to a point the vehicle must not pass, such
 *                           as a junction it has not been cleared to cross, or
 *                           {@link Double#POSITIVE_INFINITY} when the way ahead
 *                           is open
 * @param timeStepSeconds    length of the simulation step
 * @param noise              reproducible value in {@code [0, 1)} driving the
 *                           driver-imperfection term
 */
public record FollowContext(
        double currentSpeedMps,
        double desiredSpeedMps,
        double accelerationMps2,
        double decelerationMps2,
        DriverProfile driverProfile,
        LeaderGap leaderGap,
        double stopDistanceMetres,
        double timeStepSeconds,
        double noise) {

    public FollowContext {
        requireFiniteNonNegative("Current speed", currentSpeedMps);
        requireFiniteNonNegative("Desired speed", desiredSpeedMps);
        requireFinitePositive("Acceleration", accelerationMps2);
        requireFinitePositive("Deceleration", decelerationMps2);
        if (driverProfile == null) {
            throw new IllegalArgumentException("Driver profile is required.");
        }
        if (leaderGap == null) {
            throw new IllegalArgumentException("Leader gap is required.");
        }
        if (Double.isNaN(stopDistanceMetres) || stopDistanceMetres < 0) {
            throw new IllegalArgumentException("Stop distance must not be negative.");
        }
        requireFinitePositive("Time step", timeStepSeconds);
        if (!Double.isFinite(noise) || noise < 0 || noise >= 1) {
            throw new IllegalArgumentException("Noise must be in [0, 1).");
        }
    }

    /**
     * Folds the stop point into the leader constraint. A point the vehicle may
     * not pass behaves exactly like a permanently stationary leader.
     */
    public LeaderGap effectiveConstraint() {
        if (Double.isInfinite(stopDistanceMetres)) {
            return leaderGap;
        }
        return leaderGap.moreRestrictiveOf(LeaderGap.stoppedObstacleAt(stopDistanceMetres));
    }

    private static void requireFinitePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
    }

    private static void requireFiniteNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must not be negative.");
        }
    }
}
