package team.traffic.simulation.deadlock;

import java.util.Set;

/** Roads feeding a confirmed SCC that external traffic may not enter yet. */
public record RegionGate(
        String regionFingerprint,
        Set<String> gatedRoadIds,
        Set<String> cohortVehicleIds,
        double issuedAtSeconds,
        double expiresAtSeconds) {
    public RegionGate {
        if (regionFingerprint == null || regionFingerprint.isBlank()
                || gatedRoadIds == null || cohortVehicleIds == null
                || !Double.isFinite(issuedAtSeconds)
                || !Double.isFinite(expiresAtSeconds)
                || issuedAtSeconds < 0 || expiresAtSeconds <= issuedAtSeconds) {
            throw new IllegalArgumentException("Region gate is invalid.");
        }
        gatedRoadIds = sorted(gatedRoadIds);
        cohortVehicleIds = sorted(cohortVehicleIds);
    }

    public boolean isExpired(double simulationSeconds) {
        return simulationSeconds >= expiresAtSeconds;
    }

    private static Set<String> sorted(Set<String> values) {
        return java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(new java.util.TreeSet<>(values)));
    }
}
