package team.traffic.simulation.navigation;

import java.util.Map;
import team.traffic.domain.routing.RerouteReason;
import team.traffic.domain.vehicle.dynamics.WaitCause;

/** Immutable aggregate evidence for strategic navigation decisions. */
public record AdaptiveNavigationMetrics(
        long requested,
        long accepted,
        long rejected,
        Map<RerouteReason, Long> requestedByReason,
        Map<RerouteReason, Long> acceptedByReason,
        Map<RerouteReason, Long> rejectedByReason,
        long stranded,
        long routeChurn,
        long repeatedRouteRejections,
        long stallEscalations,
        Map<WaitCause, Long> stallEscalationsByCause,
        long uTurnRequested,
        long uTurnAccepted,
        long uTurnCompleted,
        long uTurnRejected,
        long routeComputations,
        long cacheHits,
        long routingComputationNanos,
        long predictionSamples,
        double predictedRemainingSeconds,
        double realizedRemainingSeconds) {

    public AdaptiveNavigationMetrics {
        requestedByReason = requestedByReason == null
                ? Map.of()
                : Map.copyOf(requestedByReason);
        acceptedByReason = acceptedByReason == null
                ? Map.of()
                : Map.copyOf(acceptedByReason);
        rejectedByReason = rejectedByReason == null
                ? Map.of()
                : Map.copyOf(rejectedByReason);
        stallEscalationsByCause = stallEscalationsByCause == null
                ? Map.of()
                : Map.copyOf(stallEscalationsByCause);
    }

    public double cacheHitRate() {
        long lookups = routeComputations + cacheHits;
        return lookups == 0 ? 0.0 : (double) cacheHits / lookups;
    }

    public double meanRoutingComputationMillis() {
        return routeComputations == 0
                ? 0.0
                : routingComputationNanos / 1_000_000.0 / routeComputations;
    }

    public double meanPredictedRemainingSeconds() {
        return predictionSamples == 0
                ? 0.0
                : predictedRemainingSeconds / predictionSamples;
    }

    public double meanRealizedRemainingSeconds() {
        return predictionSamples == 0
                ? 0.0
                : realizedRemainingSeconds / predictionSamples;
    }

    public double getCacheHitRate() {
        return cacheHitRate();
    }

    public double getMeanRoutingComputationMillis() {
        return meanRoutingComputationMillis();
    }

    public double getMeanPredictedRemainingSeconds() {
        return meanPredictedRemainingSeconds();
    }

    public double getMeanRealizedRemainingSeconds() {
        return meanRealizedRemainingSeconds();
    }
}
