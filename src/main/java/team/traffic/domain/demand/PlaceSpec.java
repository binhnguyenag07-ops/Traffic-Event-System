package team.traffic.domain.demand;

import java.util.Set;
import team.traffic.domain.network.BuildingType;

/** Declarative selector for an origin or destination. */
public record PlaceSpec(
        PlaceKind kind,
        Set<BuildingType> types,
        BoundarySide side,
        Set<String> zoneIds,
        String buildingId,
        String intersectionId) {

    public PlaceSpec {
        if (kind == null) throw new IllegalArgumentException("Place kind is required");
        types = types == null ? Set.of() : Set.copyOf(types);
        zoneIds = zoneIds == null ? Set.of() : Set.copyOf(zoneIds);
        switch (kind) {
            case BUILDING -> {
                if (types.isEmpty()) {
                    throw new IllegalArgumentException("Building place requires at least one type");
                }
            }
            case BOUNDARY -> {
                if (side == null) throw new IllegalArgumentException("Boundary place requires a side");
            }
            case ZONE -> {
                if (zoneIds.isEmpty()) throw new IllegalArgumentException("Zone place requires zone ids");
            }
            case EXPLICIT -> {
                boolean hasBuilding = buildingId != null && !buildingId.isBlank();
                boolean hasIntersection = intersectionId != null && !intersectionId.isBlank();
                if (hasBuilding == hasIntersection) {
                    throw new IllegalArgumentException(
                            "Explicit place requires exactly one buildingId or intersectionId");
                }
            }
            case ORIGIN -> {
                // The resolver supplies the original concrete place.
            }
            case INTERSECTION ->
                    throw new IllegalArgumentException("INTERSECTION is a resolved kind, not a PlaceSpec kind");
        }
    }

    public static PlaceSpec buildings(Set<BuildingType> types) {
        return new PlaceSpec(PlaceKind.BUILDING, types, null, Set.of(), null, null);
    }

    public static PlaceSpec boundary(BoundarySide side) {
        return new PlaceSpec(PlaceKind.BOUNDARY, Set.of(), side, Set.of(), null, null);
    }

    public static PlaceSpec zones(Set<String> zoneIds) {
        return new PlaceSpec(PlaceKind.ZONE, Set.of(), null, zoneIds, null, null);
    }

    public static PlaceSpec explicitBuilding(String buildingId) {
        return new PlaceSpec(PlaceKind.EXPLICIT, Set.of(), null, Set.of(), buildingId, null);
    }

    public static PlaceSpec explicitIntersection(String intersectionId) {
        return new PlaceSpec(PlaceKind.EXPLICIT, Set.of(), null, Set.of(), null, intersectionId);
    }

    public static PlaceSpec origin() {
        return new PlaceSpec(PlaceKind.ORIGIN, Set.of(), null, Set.of(), null, null);
    }
}
