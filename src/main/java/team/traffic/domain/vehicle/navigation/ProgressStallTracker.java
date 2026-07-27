package team.traffic.domain.vehicle.navigation;

import team.traffic.domain.vehicle.dynamics.ExitReachability;
import team.traffic.domain.vehicle.dynamics.MotionOutcome;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.domain.vehicle.dynamics.WaitState;

/** Cause-aware liveness tracker with deterministic exponential retry backoff. */
public final class ProgressStallTracker {
    private final double minimumProgressMetres;
    private final double[] retryBackoffSeconds;
    private double totalProgressMetres;
    private double progressSinceLastMeaningfulMetres;
    private double lastProgressAtSeconds;
    private WaitState waitState = WaitState.none(0.0);
    private double lastRerouteAttemptAtSeconds = Double.NaN;
    private String repeatedRouteFingerprint;
    private double nextRetryAtSeconds;
    private int failedAttempts;
    private ExitReachability exitReachability = ExitReachability.UNKNOWN;

    public ProgressStallTracker() {
        this(1.0, new double[] {5.0, 15.0, 30.0, 60.0});
    }

    public ProgressStallTracker(double minimumProgressMetres, double[] retryBackoffSeconds) {
        if (!Double.isFinite(minimumProgressMetres) || minimumProgressMetres <= 0
                || retryBackoffSeconds == null || retryBackoffSeconds.length == 0) {
            throw new IllegalArgumentException("Progress threshold and retry backoff are required.");
        }
        this.minimumProgressMetres = minimumProgressMetres;
        this.retryBackoffSeconds = retryBackoffSeconds.clone();
        for (double value : this.retryBackoffSeconds) {
            if (!Double.isFinite(value) || value <= 0) {
                throw new IllegalArgumentException("Retry backoff must be finite and positive.");
            }
        }
    }

    public void update(MotionOutcome outcome, ExitReachability reachability) {
        if (outcome == null) return;
        double progress = outcome.movement().map(plan -> plan.travelMetres()).orElse(0.0);
        totalProgressMetres += progress;
        progressSinceLastMeaningfulMetres += progress;
        if (progressSinceLastMeaningfulMetres >= minimumProgressMetres) {
            lastProgressAtSeconds = outcome.observedAtSeconds() + outcome.durationSeconds();
            progressSinceLastMeaningfulMetres = 0.0;
            waitState = WaitState.none(lastProgressAtSeconds);
            failedAttempts = 0;
            nextRetryAtSeconds = lastProgressAtSeconds;
        } else {
            waitState = outcome.waitState();
        }
        exitReachability = reachability == null ? ExitReachability.UNKNOWN : reachability;
    }

    public void recordRouteAttempt(
            double simulationSeconds,
            String fingerprint,
            boolean accepted) {
        lastRerouteAttemptAtSeconds = simulationSeconds;
        repeatedRouteFingerprint = fingerprint;
        if (accepted) {
            failedAttempts = 0;
            nextRetryAtSeconds = simulationSeconds;
            return;
        }
        double backoff = retryBackoffSeconds[
                Math.min(failedAttempts, retryBackoffSeconds.length - 1)];
        failedAttempts++;
        nextRetryAtSeconds = simulationSeconds + backoff;
    }

    public boolean retryDue(double simulationSeconds) {
        return simulationSeconds >= nextRetryAtSeconds;
    }

    public StallState snapshot() {
        return new StallState(
                totalProgressMetres,
                lastProgressAtSeconds,
                waitState.cause(),
                waitState.sinceSeconds(),
                waitState.resource(),
                lastRerouteAttemptAtSeconds,
                repeatedRouteFingerprint,
                nextRetryAtSeconds,
                exitReachability);
    }
}
