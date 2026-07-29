package team.traffic.domain.routing;

import team.traffic.domain.vehicle.VehicleType;

public class RouteRequest {
    private final String startIntersectionId;
    private final String destinationIntersectionId;
    private final RoutePreference preference;
    private final String incomingRoadId;
    private final VehicleType vehicleType;

    public RouteRequest(String startIntersectionId, String destinationIntersectionId, RoutePreference preference) {
        this(startIntersectionId, destinationIntersectionId, preference, null, VehicleType.CAR);
    }

    public RouteRequest(
            String startIntersectionId,
            String destinationIntersectionId,
            RoutePreference preference,
            String incomingRoadId,
            VehicleType vehicleType) {
        this.startIntersectionId = startIntersectionId;
        this.destinationIntersectionId = destinationIntersectionId;
        this.preference = preference;
        this.incomingRoadId = incomingRoadId == null || incomingRoadId.isBlank()
                ? null
                : incomingRoadId;
        this.vehicleType = vehicleType == null ? VehicleType.CAR : vehicleType;
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

    /** Directed road by which the route enters its start junction, if any. */
    public String getIncomingRoadId() {
        return incomingRoadId;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    @Override
    public String toString() {
        return "RouteRequest{" + startIntersectionId + " -> " + destinationIntersectionId
                + ", preference=" + preference
                + ", incomingRoad=" + incomingRoadId
                + ", vehicleType=" + vehicleType + "}";
    }
}
