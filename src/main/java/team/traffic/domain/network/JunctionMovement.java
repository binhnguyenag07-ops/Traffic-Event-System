package team.traffic.domain.network;

import java.util.Optional;
import java.util.Set;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.dynamics.TurnDirection;

/**
 * One authoritative transition between directed roads at a junction.
 *
 * @param fromRoadId empty only for a network-departure rule
 */
public record JunctionMovement(
        String junctionId,
        Optional<String> fromRoadId,
        String toRoadId,
        TurnDirection direction,
        MovementPermission permission,
        Set<VehicleType> allowedVehicleTypes,
        double penaltySeconds,
        MovementProvenance provenance) {

    public JunctionMovement {
        if (junctionId == null || junctionId.isBlank()) {
            throw new IllegalArgumentException("Junction id is required.");
        }
        fromRoadId = fromRoadId == null ? Optional.empty() : fromRoadId;
        fromRoadId = fromRoadId.filter(value -> !value.isBlank());
        if (toRoadId == null || toRoadId.isBlank()) {
            throw new IllegalArgumentException("Target road id is required.");
        }
        if (direction == null || permission == null || provenance == null) {
            throw new IllegalArgumentException("Movement direction, permission, and provenance are required.");
        }
        allowedVehicleTypes = allowedVehicleTypes == null
                ? Set.of()
                : Set.copyOf(allowedVehicleTypes);
        if (!Double.isFinite(penaltySeconds) || penaltySeconds < 0) {
            throw new IllegalArgumentException("Movement penalty must be finite and non-negative.");
        }
    }

    public boolean allows(VehicleType vehicleType) {
        return permission == MovementPermission.ALLOWED
                && (allowedVehicleTypes.isEmpty() || allowedVehicleTypes.contains(vehicleType));
    }

    public boolean isUTurn() {
        return direction == TurnDirection.U_TURN;
    }
}
