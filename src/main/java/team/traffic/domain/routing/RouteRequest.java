package team.traffic.domain.routing;

public class RouteRequest {
    private final String startIntersectionId;
    private final String destinationIntersectionId;
    private final RoutePreference preference;

    public RouteRequest(String startIntersectionId, String destinationIntersectionId, RoutePreference preference) {
        this.startIntersectionId = startIntersectionId;
        this.destinationIntersectionId = destinationIntersectionId;
        this.preference = preference;
    }

    public String getStartIntersectionId() {
        return startIntersectionId;
    }

    public String getDestinationIntersectionId() {
        return destinationIntersectionId;
    }

    public RoutePreference getPreference() {
        return preference;
    }

    @Override
    public String toString() {
        return "RouteRequest{" + startIntersectionId + " -> " + destinationIntersectionId + ", preference=" + preference + "}";
    }
}
