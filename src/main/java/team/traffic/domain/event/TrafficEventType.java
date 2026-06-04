package team.traffic.domain.event;

public class TrafficEventType {
    public static final String CONGESTION = "CONGESTION";
    public static final String ACCIDENT = "ACCIDENT";
    public static final String ROAD_CLOSURE = "ROAD_CLOSURE";
    public static final String TRAFFIC_LIGHT_DELAY = "TRAFFIC_LIGHT_DELAY";

    private TrafficEventType() {
    }
}
