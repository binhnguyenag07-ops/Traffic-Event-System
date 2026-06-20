package team.traffic.domain.network;

public enum RoadStatus {
    OPEN,
    CONGESTED,
    BLOCKED,
    CLOSED;

    public boolean isRoutable() {
        return this == OPEN || this == CONGESTED;
    }
}
