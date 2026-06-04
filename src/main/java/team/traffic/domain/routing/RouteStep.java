package team.traffic.domain.routing;

public class RouteStep {
    private String fromIntersectionId;
    private String toIntersectionId;
    private String roadId;
    private double distance;
    private double estimatedTime;

    public RouteStep() {
    }

    public RouteStep(String fromIntersectionId, String toIntersectionId, String roadId) {
        this.fromIntersectionId = fromIntersectionId;
        this.toIntersectionId = toIntersectionId;
        this.roadId = roadId;
    }

    public String getFromIntersectionId() {
        return fromIntersectionId;
    }

    public void setFromIntersectionId(String fromIntersectionId) {
        this.fromIntersectionId = fromIntersectionId;
    }

    public String getToIntersectionId() {
        return toIntersectionId;
    }

    public void setToIntersectionId(String toIntersectionId) {
        this.toIntersectionId = toIntersectionId;
    }

    public String getRoadId() {
        return roadId;
    }

    public void setRoadId(String roadId) {
        this.roadId = roadId;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public double getEstimatedTime() {
        return estimatedTime;
    }

    public void setEstimatedTime(double estimatedTime) {
        this.estimatedTime = estimatedTime;
    }
}
