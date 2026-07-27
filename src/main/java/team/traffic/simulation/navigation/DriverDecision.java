package team.traffic.simulation.navigation;

import java.util.Optional;
import team.traffic.domain.routing.RerouteReason;
import team.traffic.domain.vehicle.dynamics.LaneChangeIntent;
import team.traffic.domain.vehicle.dynamics.WaitCause;

/** Pure policy output; coordinators and safety models validate every request. */
public record DriverDecision(
        DriverDecisionKind kind,
        String reason,
        Optional<RerouteReason> rerouteReason,
        Optional<LaneChangeIntent> laneChangeIntent,
        Optional<WaitCause> waitCause) {

    public DriverDecision {
        if (kind == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Decision kind and reason are required.");
        }
        rerouteReason = rerouteReason == null ? Optional.empty() : rerouteReason;
        laneChangeIntent = laneChangeIntent == null ? Optional.empty() : laneChangeIntent;
        waitCause = waitCause == null ? Optional.empty() : waitCause;
    }

    public static DriverDecision keepRoute() {
        return new DriverDecision(
                DriverDecisionKind.KEEP_ROUTE, "Current route remains acceptable.",
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}
