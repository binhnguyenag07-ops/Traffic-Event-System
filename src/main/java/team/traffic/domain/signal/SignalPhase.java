package team.traffic.domain.signal;

import java.util.Set;

/** Incoming roads that receive green together for a fixed duration. */
public record SignalPhase(Set<String> greenIncomingRoadIds, double durationSeconds) {
    public SignalPhase {
        greenIncomingRoadIds = greenIncomingRoadIds == null ? Set.of() : Set.copyOf(greenIncomingRoadIds);
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0) {
            throw new IllegalArgumentException("Signal phase duration must be positive.");
        }
    }
}
