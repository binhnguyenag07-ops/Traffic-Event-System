package team.traffic.simulation.deadlock;

import java.util.Optional;
import team.traffic.domain.vehicle.dynamics.JunctionApproach;
import team.traffic.domain.vehicle.dynamics.JunctionRecoveryControl;

/** Combined gate and optional single drain reservation for the next sub-step. */
public record RecoveryDirective(
        RegionGate gate,
        Optional<RecoveryReservation> reservation)
        implements JunctionRecoveryControl {

    public static final RecoveryDirective NONE =
            new RecoveryDirective(null, Optional.empty());

    public RecoveryDirective {
        reservation = reservation == null ? Optional.empty() : reservation;
    }

    @Override
    public boolean isHeld(JunctionApproach approach) {
        if (gate == null || approach == null || isReserved(approach)) return false;
        boolean externalEntry = gate.gatedRoadIds().contains(approach.toRoad().getId())
                && !gate.cohortVehicleIds().contains(approach.vehicleId());
        boolean reservedConflictRegion = reservation
                .map(value -> value.movement().junctionId().equals(approach.junctionId()))
                .orElse(false);
        return externalEntry || reservedConflictRegion;
    }

    @Override
    public boolean isReserved(JunctionApproach approach) {
        if (approach == null || reservation.isEmpty()) return false;
        RecoveryReservation selected = reservation.get();
        return selected.vehicleId().equals(approach.vehicleId())
                && selected.movement().junctionId().equals(approach.junctionId())
                && java.util.Objects.equals(
                        selected.movement().fromRoadId().orElse(null),
                        nullSafeRoadId(approach.fromRoad()))
                && selected.movement().toRoadId().equals(approach.toRoad().getId());
    }

    private static String nullSafeRoadId(team.traffic.domain.network.Road road) {
        return road == null ? null : road.getId();
    }
}
