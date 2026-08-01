package team.traffic.domain.routing;

import java.util.Set;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.navigation.DriverKnowledge;
import team.traffic.domain.vehicle.navigation.UTurnPolicy;

/** Location-, movement-, knowledge-, and version-aware route request. */
public record AdaptiveRouteRequest(
        String vehicleId,
        VehicleType vehicleType,
        RouteAnchor anchor,
        String destinationIntersectionId,
        RoutePreference preference,
        DriverKnowledge knowledge,
        Set<String> temporarilyAvoidedRoadIds,
        UTurnPolicy uTurnPolicy,
        boolean requireUTurnAsFirstMovement,
        RerouteReason reason,
        long sourceRouteVersion) {

    /** Compatibility constructor for an ordinary reroute rather than a forced turnaround. */
    public AdaptiveRouteRequest(
            String vehicleId,
            VehicleType vehicleType,
            RouteAnchor anchor,
            String destinationIntersectionId,
            RoutePreference preference,
            DriverKnowledge knowledge,
            Set<String> temporarilyAvoidedRoadIds,
            UTurnPolicy uTurnPolicy,
            RerouteReason reason,
            long sourceRouteVersion) {
        this(vehicleId, vehicleType, anchor, destinationIntersectionId,
                preference, knowledge, temporarilyAvoidedRoadIds, uTurnPolicy,
                false, reason, sourceRouteVersion);
    }

    public AdaptiveRouteRequest {
        if (vehicleId == null || vehicleId.isBlank()
                || vehicleType == null || anchor == null
                || destinationIntersectionId == null || destinationIntersectionId.isBlank()
                || preference == null || knowledge == null
                || uTurnPolicy == null || reason == null
                || sourceRouteVersion < 0) {
            throw new IllegalArgumentException("Adaptive route request values are invalid.");
        }
        temporarilyAvoidedRoadIds = temporarilyAvoidedRoadIds == null
                ? Set.of()
                : Set.copyOf(temporarilyAvoidedRoadIds);
    }
}
