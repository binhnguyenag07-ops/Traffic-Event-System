package team.traffic.domain.vehicle.dynamics;

import java.util.Optional;

/** One complete, immutable motion or zero-motion result for a physical sub-step. */
public record MotionOutcome(
        Optional<MovementPlan> movement,
        WaitState waitState,
        double observedAtSeconds,
        double durationSeconds) {

    public MotionOutcome {
        movement = movement == null ? Optional.empty() : movement;
        if (waitState == null
                || !Double.isFinite(observedAtSeconds)
                || observedAtSeconds < 0
                || !Double.isFinite(durationSeconds)
                || durationSeconds <= 0) {
            throw new IllegalArgumentException("A motion outcome needs a wait state and valid simulated time.");
        }
    }

    public boolean madeMeaningfulProgress(double thresholdMetres) {
        return movement.map(plan -> plan.travelMetres() >= thresholdMetres).orElse(false);
    }
}
