package team.traffic.domain.statistics;

import team.traffic.domain.vehicle.VehicleType;

public record CompletedTrip(
        String vehicleId,
        VehicleType vehicleType,
        double travelTimeSeconds,
        double routeLengthKm,
        int routeRecalculationCount) {
}
