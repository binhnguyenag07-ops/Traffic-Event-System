package team.traffic.simulation;

import java.util.List;
import team.traffic.domain.network.UTurnNetworkPolicy;

/** Scenario-wide algorithm settings; all units are simulated seconds/metres. */
public record AdaptiveTrafficConfig(
        boolean enabled,
        double strategicCadenceSeconds,
        Routing routing,
        Congestion congestion,
        UTurns uTurns,
        Deadlock deadlock) {

    public record Routing(
            int maximumLookaheadRoads,
            double knowledgeCacheSeconds,
            List<Double> retryBackoffSeconds) {
        public Routing {
            if (maximumLookaheadRoads <= 0
                    || !Double.isFinite(knowledgeCacheSeconds) || knowledgeCacheSeconds < 0
                    || retryBackoffSeconds == null || retryBackoffSeconds.isEmpty()
                    || retryBackoffSeconds.stream().anyMatch(
                            value -> value == null || !Double.isFinite(value) || value <= 0)) {
                throw new IllegalArgumentException("Adaptive routing configuration is invalid.");
            }
            retryBackoffSeconds = List.copyOf(retryBackoffSeconds);
        }
    }

    public record Congestion(double travelTimeEmaHalfLifeSeconds) {
        public Congestion {
            if (!Double.isFinite(travelTimeEmaHalfLifeSeconds)
                    || travelTimeEmaHalfLifeSeconds <= 0) {
                throw new IllegalArgumentException("Travel-time EMA half-life must be positive.");
            }
        }
    }

    public record UTurns(
            UTurnNetworkPolicy networkPolicy,
            double defaultPenaltySeconds) {
        public UTurns {
            if (networkPolicy == null
                    || !Double.isFinite(defaultPenaltySeconds)
                    || defaultPenaltySeconds < 0) {
                throw new IllegalArgumentException("U-turn configuration is invalid.");
            }
        }
    }

    public record Deadlock(
            double scanIntervalSeconds,
            double confirmSeconds,
            double minimumProgressMetres,
            double reservationSeconds,
            boolean assistedRecoveryEnabled) {
        public Deadlock {
            if (!Double.isFinite(scanIntervalSeconds) || scanIntervalSeconds <= 0
                    || !Double.isFinite(confirmSeconds) || confirmSeconds <= 0
                    || !Double.isFinite(minimumProgressMetres) || minimumProgressMetres <= 0
                    || !Double.isFinite(reservationSeconds) || reservationSeconds <= 0) {
                throw new IllegalArgumentException("Deadlock configuration is invalid.");
            }
            if (assistedRecoveryEnabled) {
                throw new IllegalArgumentException(
                        "Assisted deadlock recovery is not implemented; use DISABLED.");
            }
        }
    }

    public AdaptiveTrafficConfig {
        if (!Double.isFinite(strategicCadenceSeconds) || strategicCadenceSeconds <= 0
                || routing == null || congestion == null || uTurns == null || deadlock == null) {
            throw new IllegalArgumentException("Adaptive traffic configuration is invalid.");
        }
    }

    public static AdaptiveTrafficConfig defaults() {
        return new AdaptiveTrafficConfig(
                true,
                2.0,
                new Routing(64, 5.0, List.of(5.0, 15.0, 30.0, 60.0)),
                new Congestion(30.0),
                new UTurns(
                        UTurnNetworkPolicy.EXPLICIT_OR_CONSERVATIVE_LOCAL_INFERENCE,
                        25.0),
                new Deadlock(5.0, 30.0, 1.0, 8.0, false));
    }

    public static AdaptiveTrafficConfig disabled() {
        AdaptiveTrafficConfig defaults = defaults();
        return new AdaptiveTrafficConfig(
                false,
                defaults.strategicCadenceSeconds(),
                defaults.routing(),
                defaults.congestion(),
                new UTurns(
                        UTurnNetworkPolicy.EXPLICIT_ONLY,
                        defaults.uTurns().defaultPenaltySeconds()),
                defaults.deadlock());
    }
}
