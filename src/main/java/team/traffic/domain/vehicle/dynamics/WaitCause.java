package team.traffic.domain.vehicle.dynamics;

/** Cause-local explanation for a vehicle that made no meaningful progress. */
public enum WaitCause {
    NONE,
    LEADER,
    RED_SIGNAL,
    JUNCTION_YIELD,
    DOWNSTREAM_FULL,
    LOCAL_OBSTRUCTION,
    UNSAFE_LANE_GAP,
    ROAD_UNAVAILABLE,
    ROUTE_MISSING,
    EVENT_STOPPED,
    DWELL,
    RECOVERY_HOLD,
    UNKNOWN
}
