package team.traffic.domain.vehicle.dynamics;

/**
 * The Krauss car-following model, which is the default model in SUMO.
 *
 * <p>The driver first works out the fastest speed from which it could still
 * stop without hitting the vehicle ahead, assuming that vehicle brakes too and
 * that the driver loses one reaction time before responding:</p>
 *
 * <pre>
 * vSafe = vLeader + (gap - vLeader * tau) / ((vLeader + vFollower) / (2 * b) + tau)
 * </pre>
 *
 * <p>The driver then takes the lowest of the safe speed, the speed it wants,
 * and the speed its engine can reach this step. Finally it gives away a random
 * fraction of its acceleration, which is what makes a dense platoon break down
 * into stop-and-go waves instead of gliding along in lockstep.</p>
 *
 * <p>The randomness is supplied by the caller as {@link FollowContext#noise()}
 * rather than drawn here, so a run stays reproducible: the same tick and the
 * same vehicle always produce the same value regardless of iteration order.</p>
 */
public final class KraussCarFollowingModel implements CarFollowingModel {

    @Override
    public double nextSpeedMps(FollowContext context) {
        LeaderGap constraint = context.effectiveConstraint();

        double safeSpeed = safeSpeedMps(
                constraint.gapMetres(),
                constraint.leaderSpeedMps(),
                context.currentSpeedMps(),
                context.decelerationMps2(),
                context.driverProfile().reactionTimeSeconds());

        double reachableSpeed = context.currentSpeedMps()
                + context.accelerationMps2() * context.timeStepSeconds();
        double desiredSpeed = Math.min(
                Math.min(context.desiredSpeedMps(), reachableSpeed),
                safeSpeed);

        double speed = Math.max(0.0, desiredSpeed - dawdling(context));

        return Math.min(speed, maximumSpeedWithoutOvershoot(constraint, context.timeStepSeconds()));
    }

    /**
     * Speed the driver gives away this step through inattention.
     *
     * <p>The original formulation multiplies by the step length, which is sound
     * while the step stays close to the reaction time it was calibrated for. A
     * coarse step would otherwise subtract minutes' worth of acceleration and
     * bring every vehicle to a standstill, so the term is integrated over the
     * reaction time instead once the step grows past it. Inattention is a
     * momentary lapse, not sustained braking.</p>
     */
    private static double dawdling(FollowContext context) {
        double effectiveSeconds = Math.min(
                context.timeStepSeconds(),
                context.driverProfile().reactionTimeSeconds());
        return context.driverProfile().imperfection()
                * context.accelerationMps2()
                * effectiveSeconds
                * context.noise();
    }

    /**
     * Fastest speed from which the follower can still come to a stop behind the
     * leader. Returns positive infinity on an open road.
     */
    public static double safeSpeedMps(
            double gapMetres,
            double leaderSpeedMps,
            double followerSpeedMps,
            double decelerationMps2,
            double reactionTimeSeconds) {
        if (Double.isInfinite(gapMetres)) {
            return Double.POSITIVE_INFINITY;
        }

        double denominator = (leaderSpeedMps + followerSpeedMps) / (2 * decelerationMps2)
                + reactionTimeSeconds;
        double safeSpeed = leaderSpeedMps
                + (gapMetres - leaderSpeedMps * reactionTimeSeconds) / denominator;
        return Math.max(0.0, safeSpeed);
    }

    /**
     * Hard safety net. The Krauss formula assumes a step close to the reaction
     * time; with a coarser step a vehicle could still be told to travel further
     * than the space in front of it. This caps the speed so that it cannot.
     */
    private static double maximumSpeedWithoutOvershoot(LeaderGap constraint, double timeStepSeconds) {
        if (constraint.isFreeRoad()) {
            return Double.POSITIVE_INFINITY;
        }
        return constraint.gapMetres() / timeStepSeconds + constraint.leaderSpeedMps();
    }

    @Override
    public String getName() {
        return "Krauss";
    }
}
