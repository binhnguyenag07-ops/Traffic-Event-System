package team.traffic.domain.vehicle.dynamics;

/** Why a submitted junction approach was not granted this sub-step. */
public enum JunctionDenialReason {
    NONE,
    RED_SIGNAL,
    CONFLICTING_MOVEMENT,
    UNSAFE_GAP,
    DOWNSTREAM_FULL,
    RECOVERY_HOLD
}
