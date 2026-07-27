package team.traffic.domain.vehicle.navigation;

/** Mutable aggregate-owned strategic state with explicit update methods. */
public final class NavigationState {
    private NavigationMode mode = NavigationMode.FOLLOWING_ROUTE;
    private String lastRerouteReason;
    private double lastRerouteAtSeconds = Double.NaN;
    private double nextStrategicDecisionAtSeconds;
    private String lastRouteFingerprint;
    private String lastRejectedFingerprint;
    private long lastEvaluatedKnowledgeVersion = -1;
    private NavigationDecisionRecord lastDecision;

    public NavigationMode getMode() { return mode; }
    public String getLastRerouteReason() { return lastRerouteReason; }
    public double getLastRerouteAtSeconds() { return lastRerouteAtSeconds; }
    public double getNextStrategicDecisionAtSeconds() { return nextStrategicDecisionAtSeconds; }
    public String getLastRouteFingerprint() { return lastRouteFingerprint; }
    public String getLastRejectedFingerprint() { return lastRejectedFingerprint; }
    public long getLastEvaluatedKnowledgeVersion() { return lastEvaluatedKnowledgeVersion; }
    public NavigationDecisionRecord getLastDecision() { return lastDecision; }

    public void markEvaluating() {
        mode = NavigationMode.EVALUATING;
    }

    public void recordAccepted(
            String reason,
            String fingerprint,
            double simulationSeconds,
            double cooldownSeconds) {
        recordAccepted(
                reason,
                fingerprint,
                simulationSeconds,
                cooldownSeconds,
                0L,
                null,
                null,
                null);
    }

    public void recordAccepted(
            String reason,
            String fingerprint,
            double simulationSeconds,
            double cooldownSeconds,
            long knowledgeVersion,
            String oldFingerprint,
            team.traffic.domain.routing.RouteCost oldCost,
            team.traffic.domain.routing.RouteCost newCost) {
        validateTime(simulationSeconds);
        validateDuration(cooldownSeconds);
        if (knowledgeVersion < 0) {
            throw new IllegalArgumentException(
                    "Knowledge version must not be negative.");
        }
        lastRerouteReason = reason;
        lastRerouteAtSeconds = simulationSeconds;
        nextStrategicDecisionAtSeconds = simulationSeconds + cooldownSeconds;
        lastRouteFingerprint = fingerprint;
        lastRejectedFingerprint = null;
        lastEvaluatedKnowledgeVersion = knowledgeVersion;
        lastDecision = new NavigationDecisionRecord(
                reason,
                knowledgeVersion,
                oldFingerprint,
                fingerprint,
                oldCost,
                newCost,
                true,
                simulationSeconds,
                nextStrategicDecisionAtSeconds);
        mode = NavigationMode.REROUTED;
    }

    public void recordRejected(
            String reason,
            String fingerprint,
            double nextEligibleSeconds) {
        recordRejected(
                reason,
                fingerprint,
                nextEligibleSeconds,
                0L,
                null,
                null,
                null,
                0.0);
    }

    public void recordRejected(
            String reason,
            String fingerprint,
            double nextEligibleSeconds,
            long knowledgeVersion,
            String oldFingerprint,
            team.traffic.domain.routing.RouteCost oldCost,
            team.traffic.domain.routing.RouteCost newCost,
            double decidedAtSeconds) {
        validateTime(nextEligibleSeconds);
        validateTime(decidedAtSeconds);
        if (nextEligibleSeconds < decidedAtSeconds || knowledgeVersion < 0) {
            throw new IllegalArgumentException(
                    "Rejected-decision timing or knowledge version is invalid.");
        }
        lastRerouteReason = reason;
        lastRejectedFingerprint = fingerprint;
        nextStrategicDecisionAtSeconds = nextEligibleSeconds;
        lastEvaluatedKnowledgeVersion = knowledgeVersion;
        lastDecision = new NavigationDecisionRecord(
                reason,
                knowledgeVersion,
                oldFingerprint,
                fingerprint,
                oldCost,
                newCost,
                false,
                decidedAtSeconds,
                nextEligibleSeconds);
        mode = NavigationMode.FOLLOWING_ROUTE;
    }

    public void markStranded(String reason) {
        lastRerouteReason = reason;
        mode = NavigationMode.STRANDED;
    }

    public void markRecovery() {
        mode = NavigationMode.RECOVERY;
    }

    public void markTurnaroundRequested() {
        mode = NavigationMode.TURNAROUND_REQUESTED;
    }

    public void markFollowingRoute() {
        mode = NavigationMode.FOLLOWING_ROUTE;
    }

    public boolean isDecisionDue(double simulationSeconds) {
        return simulationSeconds >= nextStrategicDecisionAtSeconds;
    }

    public boolean hasUnseenKnowledge(long knowledgeVersion) {
        return knowledgeVersion > lastEvaluatedKnowledgeVersion;
    }

    private static void validateTime(double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Simulation time must be finite and non-negative.");
        }
    }

    private static void validateDuration(double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Duration must be finite and non-negative.");
        }
    }
}
