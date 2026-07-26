package team.traffic.domain.demand;

import java.util.Set;

/** Optional named group of buildings and intersections used by matrix/scenario demand. */
public record ZoneDefinition(
        String id,
        Set<String> buildingIds,
        Set<String> intersectionIds) {

    public ZoneDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Zone id is required");
        buildingIds = buildingIds == null ? Set.of() : Set.copyOf(buildingIds);
        intersectionIds = intersectionIds == null ? Set.of() : Set.copyOf(intersectionIds);
        if (buildingIds.isEmpty() && intersectionIds.isEmpty()) {
            throw new IllegalArgumentException("Zone must contain a building or intersection");
        }
    }
}
