package team.traffic.domain.demand;

/** One concrete, routable place selected from a {@link PlaceSpec}. */
public record ResolvedPlace(PlaceKind kind, String id, String accessIntersectionId) {
    public ResolvedPlace {
        if (kind == null || kind == PlaceKind.ORIGIN || kind == PlaceKind.ZONE
                || kind == PlaceKind.EXPLICIT) {
            throw new IllegalArgumentException("Resolved place kind must be BUILDING, BOUNDARY or INTERSECTION");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Resolved place id is required");
        }
        if (accessIntersectionId == null || accessIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Resolved place access intersection is required");
        }
    }

    public static ResolvedPlace building(String buildingId, String accessIntersectionId) {
        return new ResolvedPlace(PlaceKind.BUILDING, buildingId, accessIntersectionId);
    }

    public static ResolvedPlace boundary(String intersectionId) {
        return new ResolvedPlace(PlaceKind.BOUNDARY, intersectionId, intersectionId);
    }

    public static ResolvedPlace intersection(String intersectionId) {
        return new ResolvedPlace(PlaceKind.INTERSECTION, intersectionId, intersectionId);
    }
}
