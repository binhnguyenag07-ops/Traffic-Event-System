package team.traffic.simulation;

import java.util.List;

public record DemandRunStatistics(
        int scheduledTrips,
        int spawnedTrips,
        int failedTrips,
        int pendingTrips,
        List<String> failedPlanIds) {

    public DemandRunStatistics {
        if (scheduledTrips < 0 || spawnedTrips < 0 || failedTrips < 0 || pendingTrips < 0) {
            throw new IllegalArgumentException("Demand statistics must not be negative");
        }
        failedPlanIds = failedPlanIds == null ? List.of() : List.copyOf(failedPlanIds);
    }

    public static DemandRunStatistics empty() {
        return new DemandRunStatistics(0, 0, 0, 0, List.of());
    }
}
