package team.traffic.domain.vehicle.navigation;

import java.util.Optional;
import team.traffic.domain.vehicle.dynamics.ExitReachability;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.domain.vehicle.dynamics.WaitResource;

/** Immutable progress/stall snapshot used by strategic decisions and deadlock scans. */
public record StallState(
        double lastLongitudinalProgressMetres,
        double lastProgressAtSeconds,
        WaitCause cause,
        double causeSinceSeconds,
        Optional<WaitResource> resource,
        double lastRerouteAttemptAtSeconds,
        String repeatedRouteFingerprint,
        double nextRetryAtSeconds,
        ExitReachability exitReachability) {

    public StallState {
        if (!Double.isFinite(lastLongitudinalProgressMetres) || lastLongitudinalProgressMetres < 0
                || !Double.isFinite(lastProgressAtSeconds) || lastProgressAtSeconds < 0
                || cause == null || !Double.isFinite(causeSinceSeconds) || causeSinceSeconds < 0
                || exitReachability == null) {
            throw new IllegalArgumentException("Stall-state values are invalid.");
        }
        resource = resource == null ? Optional.empty() : resource;
    }

    public double stalledForSeconds(double simulationSeconds) {
        return Math.max(0.0, simulationSeconds - lastProgressAtSeconds);
    }
}
