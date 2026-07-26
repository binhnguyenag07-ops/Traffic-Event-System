package team.traffic.domain.statistics;

import team.traffic.domain.vehicle.VehicleType;

public record CompletedTrip(
        String vehicleId,
        VehicleType vehicleType,
        double travelTimeSeconds,
        double routeLengthKm,
        int routeRecalculationCount,
        String tripPlanId,
        String tripPurpose) {

    public CompletedTrip(
            String vehicleId,
            VehicleType vehicleType,
            double travelTimeSeconds,
            double routeLengthKm,
            int routeRecalculationCount) {
        this(vehicleId, vehicleType, travelTimeSeconds, routeLengthKm,
                routeRecalculationCount, null, null);
    }
}
