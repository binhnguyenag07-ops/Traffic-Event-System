package team.traffic.domain.routing;

public enum RerouteReason {
    KNOWN_HARD_BLOCK,
    EXPECTED_DELAY,
    LOCAL_OBSTRUCTION,
    STALL_TIMEOUT,
    DEADLOCK_RECOVERY,
    NETWORK_EDIT
}
