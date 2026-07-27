package team.traffic.simulation.deadlock;

import java.util.Set;
import team.traffic.domain.network.JunctionMovement;

/** One auditable, physically pre-validated drain attempt. */
public record RecoveryReservation(
        String vehicleId,
        JunctionMovement movement,
        int targetLaneIndex,
        String regionFingerprint,
        double issuedAtSeconds,
        double expiresAtSeconds,
        String reason,
        String baselineRoadId,
        double baselineRoadProgress,
        Set<String> targetOccupantIds) {
    public RecoveryReservation {
        if (vehicleId == null || vehicleId.isBlank() || movement == null
                || targetLaneIndex < 0
                || regionFingerprint == null || regionFingerprint.isBlank()
                || !Double.isFinite(issuedAtSeconds)
                || !Double.isFinite(expiresAtSeconds)
                || expiresAtSeconds <= issuedAtSeconds
                || reason == null || reason.isBlank()
                || !Double.isFinite(baselineRoadProgress)
                || baselineRoadProgress < 0 || baselineRoadProgress > 1) {
            throw new IllegalArgumentException("Recovery reservation is invalid.");
        }
        targetOccupantIds = targetOccupantIds == null
                ? Set.of()
                : java.util.Collections.unmodifiableSet(
                        new java.util.LinkedHashSet<>(
                                new java.util.TreeSet<>(targetOccupantIds)));
    }

    public boolean isExpired(double simulationSeconds) {
        return simulationSeconds >= expiresAtSeconds;
    }
}
