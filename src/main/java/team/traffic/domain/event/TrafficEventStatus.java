package team.traffic.domain.event;

public class TrafficEventStatus {
    public static final String SCHEDULED = "SCHEDULED";
    public static final String ACTIVE = "ACTIVE";
    public static final String RESOLVED = "RESOLVED";
    public static final String CANCELLED = "CANCELLED";

    private TrafficEventStatus() {
    }
}
