package team.traffic.domain.vehicle;

public enum VehicleStatus {
    WAITING,
    MOVING,
    ARRIVED,
    BLOCKED,
    /** Immobile but still physically occupying its lane. */
    STOPPED,
    /** Waiting at a resolved trip stop without occupying a road lane. */
    DWELLING
}
