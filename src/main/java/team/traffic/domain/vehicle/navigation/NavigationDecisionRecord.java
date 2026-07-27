package team.traffic.domain.vehicle.navigation;

import team.traffic.domain.routing.RouteCost;

/** Immutable evidence explaining the latest strategic route decision. */
public record NavigationDecisionRecord(
        String reason,
        long knowledgeVersion,
        String oldRouteFingerprint,
        String newRouteFingerprint,
        RouteCost oldExpectedCost,
        RouteCost newExpectedCost,
        boolean accepted,
        double decidedAtSeconds,
        double nextEligibleDecisionAtSeconds) {

    public NavigationDecisionRecord {
        if (reason == null || reason.isBlank()
                || knowledgeVersion < 0
                || !Double.isFinite(decidedAtSeconds)
                || decidedAtSeconds < 0
                || !Double.isFinite(nextEligibleDecisionAtSeconds)
                || nextEligibleDecisionAtSeconds < decidedAtSeconds) {
            throw new IllegalArgumentException(
                    "Navigation decision evidence is invalid.");
        }
    }
}
