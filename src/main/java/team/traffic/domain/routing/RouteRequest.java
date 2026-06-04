package team.traffic.domain.routing;

public class RouteRequest {
    private String vehicleId;
    private String startIntersectionId;
    private String destinationIntersectionId;
    private String preference = RoutePreference.SHORTEST_DISTANCE;
    private String strategyName;

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getStartIntersectionId() {
        return startIntersectionId;
    }

    public void setStartIntersectionId(String startIntersectionId) {
        this.startIntersectionId = startIntersectionId;
    }

    public String getDestinationIntersectionId() {
        return destinationIntersectionId;
    }

    public void setDestinationIntersectionId(String destinationIntersectionId) {
        this.destinationIntersectionId = destinationIntersectionId;
    }

    public String getPreference() {
        return preference;
    }

    public void setPreference(String preference) {
        this.preference = preference;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }
}
