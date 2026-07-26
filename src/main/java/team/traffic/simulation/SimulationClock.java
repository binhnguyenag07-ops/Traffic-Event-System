package team.traffic.simulation;

/**
 * How far the simulation has run, and what time of day that makes it.
 *
 * <p>Elapsed seconds alone cannot say whether traffic should be heading to work
 * or coming home from it. A demand model needs a wall clock, so the simulation
 * is given a starting time of day and everything else follows from how long it
 * has been running.</p>
 *
 * <p>The time of day is <em>derived</em>, never stored alongside each frame. A
 * frame already records its elapsed seconds; writing the clock time next to it
 * would be a second copy of the same fact, free to disagree if the start time
 * were ever changed mid-run. Anything that needs the hour asks the clock, or
 * computes it from elapsed seconds and the start offset the API publishes.</p>
 */
public class SimulationClock {

    public static final int SECONDS_PER_DAY = 86_400;
    /**
     * Default start, 06:00. Chosen so a run reaches the morning peak within a
     * few minutes of simulated time rather than idling through the small hours.
     */
    public static final double DEFAULT_START_TIME_OF_DAY_SECONDS = 6 * 3_600;

    private final double startTimeOfDaySeconds;
    private long currentTick;
    private double elapsedSeconds;

    public SimulationClock() {
        this(DEFAULT_START_TIME_OF_DAY_SECONDS);
    }

    /**
     * @param startTimeOfDaySeconds seconds since midnight the run begins at
     */
    public SimulationClock(double startTimeOfDaySeconds) {
        this.startTimeOfDaySeconds = normalise(startTimeOfDaySeconds);
        this.currentTick = 0;
        this.elapsedSeconds = 0.0;
    }

    public void tick(double simulationSeconds) {
        if (!Double.isFinite(simulationSeconds) || simulationSeconds <= 0) {
            throw new IllegalArgumentException("Simulation seconds must be positive.");
        }
        currentTick++;
        elapsedSeconds += simulationSeconds;
    }

    /** Compatibility helper for callers that model one second per tick. */
    public void tick() {
        tick(1.0);
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    /** Seconds since midnight the run began at. */
    public double getStartTimeOfDaySeconds() {
        return startTimeOfDaySeconds;
    }

    /** Seconds since midnight right now, wrapping at the end of each day. */
    public double getTimeOfDaySeconds() {
        return timeOfDayAt(elapsedSeconds);
    }

    /** Hour of the clock, 0 to 23, for a demand curve to index by. */
    public int getHourOfDay() {
        return (int) (getTimeOfDaySeconds() / 3_600);
    }

    /** Whole days crossed since the run started. */
    public long getDaysElapsed() {
        return (long) ((startTimeOfDaySeconds + elapsedSeconds) / SECONDS_PER_DAY);
    }

    /**
     * The time of day a given elapsed reading corresponds to.
     *
     * <p>Exposed so a recorded frame can be labelled with its clock time without
     * the frame having to carry one.</p>
     */
    public double timeOfDayAt(double elapsed) {
        return timeOfDayAt(startTimeOfDaySeconds, elapsed);
    }

    /** As above, for callers holding only the published start offset. */
    public static double timeOfDayAt(double startTimeOfDaySeconds, double elapsed) {
        if (!Double.isFinite(elapsed) || elapsed < 0) {
            throw new IllegalArgumentException("Elapsed seconds must not be negative.");
        }
        double raw = (normalise(startTimeOfDaySeconds) + elapsed) % SECONDS_PER_DAY;
        return raw < 0 ? raw + SECONDS_PER_DAY : raw;
    }

    /** {@code HH:mm} for display. */
    public String formatTimeOfDay() {
        return formatTimeOfDay(getTimeOfDaySeconds());
    }

    public static String formatTimeOfDay(double timeOfDaySeconds) {
        int total = (int) Math.floor(timeOfDaySeconds);
        return String.format("%02d:%02d", (total / 3_600) % 24, (total % 3_600) / 60);
    }

    public void reset() {
        currentTick = 0;
        elapsedSeconds = 0.0;
    }

    private static double normalise(double timeOfDaySeconds) {
        if (!Double.isFinite(timeOfDaySeconds)) {
            throw new IllegalArgumentException("Start time of day must be finite.");
        }
        double wrapped = timeOfDaySeconds % SECONDS_PER_DAY;
        return wrapped < 0 ? wrapped + SECONDS_PER_DAY : wrapped;
    }
}
