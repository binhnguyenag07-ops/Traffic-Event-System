package team.traffic.domain.vehicle.dynamics;

/** Visible signal state for the current incoming road. */
public record SignalObservation(
        Aspect aspect,
        double secondsUntilPhaseChange,
        double distanceToStopLineMetres) {

    public enum Aspect {
        GREEN,
        RED
    }

    public SignalObservation {
        if (aspect == null
                || !Double.isFinite(secondsUntilPhaseChange) || secondsUntilPhaseChange < 0
                || !Double.isFinite(distanceToStopLineMetres) || distanceToStopLineMetres < 0) {
            throw new IllegalArgumentException("Signal observation values are invalid.");
        }
    }
}
