package team.traffic.domain.network;

/** Where a junction-movement rule came from. */
public enum MovementProvenance {
    EXPLICIT,
    IMPORTED,
    INFERRED
}
