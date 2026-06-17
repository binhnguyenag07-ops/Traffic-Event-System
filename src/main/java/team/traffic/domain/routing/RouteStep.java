package team.traffic.domain.routing;

public class RouteStep {
    private final String fromIntersectionId;
    private final String toIntersectionId;
    private final String roadId;
    private final double cost;

    public RouteStep(String fromIntersectionId, String toIntersectionId, String roadId, double cost) {
        this.fromIntersectionId = fromIntersectionId;
        this.toIntersectionId = toIntersectionId;
        this.roadId = roadId;
        this.cost = cost;
    }

    public String getFromIntersectionId() {
        return fromIntersectionId;
    }

    public String getToIntersectionId() {
        return toIntersectionId;
    }

    public String getRoadId() {
        return roadId;
    }

    public double getCost() {
        return cost;
    }

    @Override
    public String toString() {
        return fromIntersectionId + " -> " + toIntersectionId + " [road=" + roadId + ", cost=" + cost + "]";
    }
}
