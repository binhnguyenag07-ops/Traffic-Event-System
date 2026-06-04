package team.traffic.domain.vehicle;

public class VehicleStatus {
    public static final String WAITING_FOR_ROUTE = "WAITING_FOR_ROUTE";
    public static final String MOVING = "MOVING";
    public static final String STOPPED = "STOPPED";
    public static final String ARRIVED = "ARRIVED";

    private VehicleStatus() {
    }
}
