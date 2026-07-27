package team.traffic.domain.routing;

/** Outcome of an atomic route-suffix replacement attempt. */
public record RouteReplacementResult(Status status, String reason, long routeVersion) {
    public enum Status {
        REPLACED,
        REJECTED_STALE,
        REJECTED_ANCHOR,
        REJECTED_DESTINATION,
        REJECTED_DISCONNECTED,
        REJECTED_ILLEGAL_MOVEMENT
    }

    public RouteReplacementResult {
        if (status == null || reason == null || reason.isBlank() || routeVersion < 0) {
            throw new IllegalArgumentException("Replacement status, reason, and route version are required.");
        }
    }

    public boolean replaced() {
        return status == Status.REPLACED;
    }
}
