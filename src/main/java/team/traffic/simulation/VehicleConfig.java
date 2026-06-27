package team.traffic.simulation;

public record VehicleConfig(
        String id,
        String type,
        String startIntersectionId,
        String destinationIntersectionId) {}
