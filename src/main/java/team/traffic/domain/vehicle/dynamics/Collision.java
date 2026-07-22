package team.traffic.domain.vehicle.dynamics;

/** A follower can no longer brake within the available lane gap. */
public record Collision(
        String causingVehicleId,
        String impactedVehicleId,
        String roadId,
        int laneIndex,
        double positionAlongRoad,
        double requiredDecelerationMps2) {
}
