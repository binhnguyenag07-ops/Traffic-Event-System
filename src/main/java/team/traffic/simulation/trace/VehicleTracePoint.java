package team.traffic.simulation.trace;

import java.util.List;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;

/** Immutable vehicle state captured after a simulation tick completes. */
public record VehicleTracePoint(
        long tick,
        String vehicleId,
        VehicleType type,
        VehicleStatus status,
        String currentIntersectionId,
        String destinationIntersectionId,
        String currentRoadId,
        String segmentStartIntersectionId,
        String segmentEndIntersectionId,
        double roadProgress,
        double currentSpeed,
        double instantaneousSpeed,
        int nextRouteStepIndex,
        List<String> routeRoadIds) {

    public VehicleTracePoint {
        if (tick < 0) {
            throw new IllegalArgumentException("Trace tick must not be negative.");
        }
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("Trace vehicle id must not be blank.");
        }
        if (type == null || status == null) {
            throw new IllegalArgumentException("Trace vehicle type and status must not be null.");
        }
        if (!Double.isFinite(roadProgress) || roadProgress < 0.0 || roadProgress > 1.0) {
            throw new IllegalArgumentException("Trace road progress must be between 0 and 1.");
        }
        if (!Double.isFinite(currentSpeed) || currentSpeed < 0.0) {
            throw new IllegalArgumentException("Trace current speed must not be negative.");
        }
        if (!Double.isFinite(instantaneousSpeed) || instantaneousSpeed < 0.0) {
            throw new IllegalArgumentException("Trace instantaneous speed must not be negative.");
        }
        if (nextRouteStepIndex < 0) {
            throw new IllegalArgumentException("Trace route step index must not be negative.");
        }
        routeRoadIds = routeRoadIds == null ? List.of() : List.copyOf(routeRoadIds);
    }

    public static VehicleTracePoint from(long tick, Vehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle must not be null.");
        }

        List<String> routeRoadIds = vehicle.getCurrentRoute() == null
                ? List.of()
                : vehicle.getCurrentRoute().getSteps().stream()
                        .map(step -> step.getRoadId())
                        .toList();

        return new VehicleTracePoint(
                tick,
                vehicle.getId(),
                vehicle.getType(),
                vehicle.getStatus(),
                vehicle.getCurrentIntersectionId(),
                vehicle.getDestinationIntersectionId(),
                vehicle.getOccupiedRoadId(),
                vehicle.getRoadStartIntersectionId(),
                vehicle.getRoadEndIntersectionId(),
                vehicle.getRoadProgress(),
                vehicle.getCurrentSpeed(),
                vehicle.getInstantaneousSpeedKph(),
                vehicle.getNextRouteStepIndex(),
                routeRoadIds);
    }
}
