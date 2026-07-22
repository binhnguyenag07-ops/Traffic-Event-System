package team.traffic.domain.network;

/**
 * A curb or driveway through which vehicles enter or leave a building.
 *
 * <p>The referenced intersection is a routing access node, not necessarily a
 * real-world junction. A map can place that node on a road and split the road
 * at the node, allowing the existing graph algorithms to route to the exact
 * curb position.</p>
 */
public record BuildingAccessPoint(String id, String intersectionId, double drivewayDistanceMetres) {
    public BuildingAccessPoint {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Building access id is required");
        }
        if (intersectionId == null || intersectionId.isBlank()) {
            throw new IllegalArgumentException("Building access intersection id is required");
        }
        if (!Double.isFinite(drivewayDistanceMetres) || drivewayDistanceMetres < 0) {
            throw new IllegalArgumentException("Driveway distance must be finite and non-negative");
        }
    }
}
