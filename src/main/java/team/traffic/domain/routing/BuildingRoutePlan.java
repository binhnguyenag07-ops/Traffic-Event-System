package team.traffic.domain.routing;

import team.traffic.domain.network.BuildingAccessPoint;

/** The best graph route together with the chosen building entrances. */
public record BuildingRoutePlan(
        BuildingAccessPoint originAccess,
        BuildingAccessPoint destinationAccess,
        RouteResult routeResult,
        double totalScore) {
    public BuildingRoutePlan {
        if (destinationAccess == null) throw new IllegalArgumentException("Destination access is required");
        if (routeResult == null) throw new IllegalArgumentException("Route result is required");
        if (!Double.isFinite(totalScore) || totalScore < 0) {
            throw new IllegalArgumentException("Building route score must be finite and non-negative");
        }
    }
}
