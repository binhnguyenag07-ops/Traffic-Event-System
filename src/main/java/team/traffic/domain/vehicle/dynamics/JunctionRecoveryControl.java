package team.traffic.domain.vehicle.dynamics;

/**
 * Read-only recovery directives prepared after the previous physical
 * sub-step. They may hold an approach or rank one reservation first, but never
 * bypass signals, conflict checks, gap acceptance, or physical admission.
 */
public interface JunctionRecoveryControl {
    JunctionRecoveryControl NONE = new JunctionRecoveryControl() {};

    default boolean isHeld(JunctionApproach approach) {
        return false;
    }

    default boolean isReserved(JunctionApproach approach) {
        return false;
    }
}
