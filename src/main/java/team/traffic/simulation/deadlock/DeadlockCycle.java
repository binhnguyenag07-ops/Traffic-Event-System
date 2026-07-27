package team.traffic.simulation.deadlock;

import java.util.Set;

/** Stable evidence for one cyclic cohort in the bipartite wait-for graph. */
public record DeadlockCycle(
        String fingerprint,
        Set<String> vehicleIds,
        Set<String> resourceKeys,
        double firstObservedAtSeconds,
        double lastObservedAtSeconds,
        DeadlockStatus status,
        String diagnosis) {

    public DeadlockCycle {
        if (fingerprint == null || fingerprint.isBlank()
                || !Double.isFinite(firstObservedAtSeconds)
                || !Double.isFinite(lastObservedAtSeconds)
                || firstObservedAtSeconds < 0
                || lastObservedAtSeconds < firstObservedAtSeconds
                || status == null) {
            throw new IllegalArgumentException("Deadlock-cycle evidence is invalid.");
        }
        vehicleIds = sorted(vehicleIds);
        resourceKeys = sorted(resourceKeys);
    }

    public DeadlockCycle(
            String fingerprint,
            Set<String> vehicleIds,
            Set<String> resourceKeys,
            double firstObservedAtSeconds,
            double lastObservedAtSeconds,
            DeadlockStatus status) {
        this(
                fingerprint,
                vehicleIds,
                resourceKeys,
                firstObservedAtSeconds,
                lastObservedAtSeconds,
                status,
                null);
    }

    private static Set<String> sorted(Set<String> values) {
        return java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(new java.util.TreeSet<>(values)));
    }
}
