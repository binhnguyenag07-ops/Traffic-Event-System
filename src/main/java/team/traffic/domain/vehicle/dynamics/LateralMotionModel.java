package team.traffic.domain.vehicle.dynamics;

/**
 * How far sideways a vehicle gets during one step while changing lane.
 *
 * <p>A lane change used to be a single assignment: the lane index changed and
 * the vehicle was simply somewhere else. Nothing in the model objected, because
 * nothing in the model had an opinion about where a vehicle sits across the
 * road. The consequence showed up as soon as anything tried to draw it — a car
 * crossing three and a half metres of tarmac in no time at all.</p>
 *
 * <p>This model gives that movement a speed. The vehicle slides towards the
 * centre of the lane it has chosen, covering at most
 * {@code maxLateralSpeedMps * timeStep} each step, and stops on arrival rather
 * than overshooting. At SUMO's default of one metre per second a car needs
 * between three and four seconds to cross a normal lane, which is what a real
 * lane change takes.</p>
 *
 * <p>The model is deliberately kinematic rather than dynamic: constant speed,
 * no lateral acceleration ramp. Steering effort is not what limits an ordinary
 * lane change, and a constant rate keeps the result exactly reproducible for a
 * given step size, which the frame replay depends on.</p>
 */
public final class LateralMotionModel {

    /**
     * Distance within which a vehicle counts as having arrived. Without it a
     * vehicle could sit fractions of a millimetre from its lane centre forever,
     * held permanently mid-change by floating-point residue and so never
     * allowed to consider another lane.
     */
    public static final double ARRIVAL_TOLERANCE_METRES = 1e-6;

    private LateralMotionModel() {
    }

    /**
     * Lateral position after one step of sliding from {@code currentOffsetMetres}
     * towards {@code targetOffsetMetres}.
     *
     * <p>Both offsets are signed distances from the road centreline. The result
     * never passes the target, so a vehicle that would arrive part-way through
     * the step finishes the step sitting exactly on its lane centre.</p>
     */
    public static double nextOffsetMetres(
            double currentOffsetMetres,
            double targetOffsetMetres,
            double maxLateralSpeedMps,
            double timeStepSeconds) {
        requireFinite("Current lateral offset", currentOffsetMetres);
        requireFinite("Target lateral offset", targetOffsetMetres);
        if (!Double.isFinite(maxLateralSpeedMps) || maxLateralSpeedMps <= 0) {
            throw new IllegalArgumentException("Maximum lateral speed must be positive.");
        }
        if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0) {
            throw new IllegalArgumentException("Time step must be positive.");
        }

        double remaining = targetOffsetMetres - currentOffsetMetres;
        double reachable = maxLateralSpeedMps * timeStepSeconds;
        if (Math.abs(remaining) <= reachable) {
            return targetOffsetMetres;
        }
        return currentOffsetMetres + Math.copySign(reachable, remaining);
    }

    /**
     * Whether a vehicle has finished crossing into the lane it chose.
     *
     * <p>This is what stops a vehicle changing its mind halfway. A lane change
     * only means anything as a manoeuvre if the vehicle is committed to it for
     * its duration; re-running the incentive calculation every step would let a
     * car drift back and forth across a lane line without ever completing a
     * change.</p>
     */
    public static boolean hasSettled(double currentOffsetMetres, double targetOffsetMetres) {
        requireFinite("Current lateral offset", currentOffsetMetres);
        requireFinite("Target lateral offset", targetOffsetMetres);
        return Math.abs(targetOffsetMetres - currentOffsetMetres) <= ARRIVAL_TOLERANCE_METRES;
    }

    /**
     * Seconds a vehicle needs to cross {@code widthMetres} sideways, useful when
     * choosing a lateral speed from an intended manoeuvre duration.
     */
    public static double crossingSecondsFor(double widthMetres, double maxLateralSpeedMps) {
        if (!Double.isFinite(widthMetres) || widthMetres < 0) {
            throw new IllegalArgumentException("Width must not be negative.");
        }
        if (!Double.isFinite(maxLateralSpeedMps) || maxLateralSpeedMps <= 0) {
            throw new IllegalArgumentException("Maximum lateral speed must be positive.");
        }
        return widthMetres / maxLateralSpeedMps;
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
    }
}
