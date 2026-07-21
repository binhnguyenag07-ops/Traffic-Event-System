package team.traffic.api.dto;

import team.traffic.domain.network.Road;

public record RoadStateDto(
        String id,
        String status,
        double congestionLevel,
        double vehicleDensity,
        int currentVehicleCount,
        int capacity,
        boolean available,
        double travelCost) {
    public RoadStateDto(Road road) {
        this(
                road.getId(),
                road.getCondition().getRoadStatus().name(),
                road.getCondition().getCongestionLevel(),
                road.getCondition().getVehicleDensity(),
                road.getCurrentVehicleCount(),
                road.getCapacity(),
                road.isAvailable(),
                road.calculateTravelCost());
    }
}
