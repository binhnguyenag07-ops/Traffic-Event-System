package team.traffic.simulation.navigation;

public enum DriverDecisionKind {
    KEEP_ROUTE,
    REQUEST_LANE_CHANGE,
    REQUEST_REROUTE,
    REQUEST_LEGAL_UTURN,
    WAIT,
    REPORT_STRANDED
}
