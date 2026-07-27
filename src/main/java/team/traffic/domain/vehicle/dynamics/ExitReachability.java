package team.traffic.domain.vehicle.dynamics;

/** Whether the next legal manoeuvre point is physically reachable. */
public enum ExitReachability {
    REACHABLE,
    REACHABLE_AFTER_SAFE_LANE_CHANGE,
    WAITING_FOR_TEMPORARY_SPACE,
    SEALED_BEFORE_NEXT_MANOEUVRE,
    UNKNOWN
}
