package team.traffic.domain.vehicle.dynamics;

import java.util.Optional;

/** Immutable physical location copied into a perception snapshot. */
public record VehicleLocation(
        Optional<String> roadId,
        Optional<String> junctionId,
        double roadProgress,
        int laneIndex,
        double lateralOffsetMetres) {

    public VehicleLocation {
        roadId = roadId == null ? Optional.empty() : roadId;
        junctionId = junctionId == null ? Optional.empty() : junctionId;
        if (!Double.isFinite(roadProgress) || roadProgress < 0 || roadProgress > 1
                || laneIndex < 0 || !Double.isFinite(lateralOffsetMetres)) {
            throw new IllegalArgumentException("Vehicle location values are invalid.");
        }
    }
}
