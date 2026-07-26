package team.traffic.domain.demand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.RoadNetwork;

/** Resolves scenario place selectors against one immutable view of a road network. */
public final class PlaceResolver {
    public static final double DEFAULT_BOUNDARY_MARGIN_RATIO = 0.08;

    private final RoadNetwork network;
    private final Map<String, ZoneDefinition> zones;
    private final double boundaryMarginRatio;

    public PlaceResolver(RoadNetwork network, Map<String, ZoneDefinition> zones) {
        this(network, zones, DEFAULT_BOUNDARY_MARGIN_RATIO);
    }

    public PlaceResolver(
            RoadNetwork network,
            Map<String, ZoneDefinition> zones,
            double boundaryMarginRatio) {
        if (network == null) throw new IllegalArgumentException("Road network is required");
        if (!Double.isFinite(boundaryMarginRatio) || boundaryMarginRatio <= 0 || boundaryMarginRatio >= 0.5) {
            throw new IllegalArgumentException("Boundary margin ratio must be between 0 and 0.5");
        }
        this.network = network;
        this.zones = zones == null ? Map.of() : Map.copyOf(zones);
        this.boundaryMarginRatio = boundaryMarginRatio;
    }

    public List<ResolvedPlace> resolve(PlaceSpec spec, ResolvedPlace origin) {
        if (spec == null) throw new IllegalArgumentException("Place spec is required");
        List<ResolvedPlace> result = switch (spec.kind()) {
            case BUILDING -> network.getBuildings().values().stream()
                    .filter(building -> spec.types().contains(building.getType()))
                    .map(this::buildingPlace)
                    .toList();
            case BOUNDARY -> boundaryPlaces(spec.side());
            case ZONE -> zonePlaces(spec.zoneIds());
            case EXPLICIT -> explicitPlace(spec);
            case ORIGIN -> {
                if (origin == null) throw new IllegalArgumentException("ORIGIN requires a resolved trip origin");
                yield List.of(origin);
            }
            case INTERSECTION -> throw new IllegalArgumentException("INTERSECTION is not a scenario selector");
        };
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Place spec resolves no candidate: " + spec);
        }
        return result.stream()
                .sorted(Comparator.comparing((ResolvedPlace place) -> place.kind().name())
                        .thenComparing(ResolvedPlace::id)
                        .thenComparing(ResolvedPlace::accessIntersectionId))
                .toList();
    }

    private ResolvedPlace buildingPlace(Building building) {
        BuildingAccessPoint access = building.getAccessPoints().stream()
                .min(Comparator.comparingDouble(BuildingAccessPoint::drivewayDistanceMetres)
                        .thenComparing(BuildingAccessPoint::id))
                .orElseThrow();
        return ResolvedPlace.building(building.getId(), access.intersectionId());
    }

    private List<ResolvedPlace> explicitPlace(PlaceSpec spec) {
        if (spec.buildingId() != null && !spec.buildingId().isBlank()) {
            Building building = network.getBuilding(spec.buildingId());
            return building == null ? List.of() : List.of(buildingPlace(building));
        }
        return network.containsIntersection(spec.intersectionId())
                ? List.of(ResolvedPlace.intersection(spec.intersectionId()))
                : List.of();
    }

    private List<ResolvedPlace> zonePlaces(java.util.Set<String> zoneIds) {
        Map<String, ResolvedPlace> unique = new LinkedHashMap<>();
        for (String zoneId : zoneIds) {
            ZoneDefinition zone = zones.get(zoneId);
            if (zone == null) throw new IllegalArgumentException("Zone not found: " + zoneId);
            for (String buildingId : zone.buildingIds()) {
                Building building = network.getBuilding(buildingId);
                if (building == null) throw new IllegalArgumentException(
                        "Zone " + zoneId + " references missing building " + buildingId);
                ResolvedPlace place = buildingPlace(building);
                unique.put(place.kind() + ":" + place.id(), place);
            }
            for (String intersectionId : zone.intersectionIds()) {
                if (!network.containsIntersection(intersectionId)) {
                    throw new IllegalArgumentException(
                            "Zone " + zoneId + " references missing intersection " + intersectionId);
                }
                ResolvedPlace place = ResolvedPlace.intersection(intersectionId);
                unique.put(place.kind() + ":" + place.id(), place);
            }
        }
        return List.copyOf(unique.values());
    }

    private List<ResolvedPlace> boundaryPlaces(BoundarySide side) {
        List<Intersection> intersections = new ArrayList<>(network.getIntersections().values());
        if (intersections.isEmpty()) return List.of();
        double minX = intersections.stream().mapToDouble(Intersection::getX).min().orElseThrow();
        double maxX = intersections.stream().mapToDouble(Intersection::getX).max().orElseThrow();
        double minY = intersections.stream().mapToDouble(Intersection::getY).min().orElseThrow();
        double maxY = intersections.stream().mapToDouble(Intersection::getY).max().orElseThrow();
        double marginX = Math.max((maxX - minX) * boundaryMarginRatio, 1e-9);
        double marginY = Math.max((maxY - minY) * boundaryMarginRatio, 1e-9);

        return intersections.stream()
                .filter(intersection -> onSide(intersection, side, minX, maxX, minY, maxY, marginX, marginY))
                .filter(intersection -> !network.findOutgoingRoads(intersection.getId()).isEmpty()
                        || !network.findIncomingRoads(intersection.getId()).isEmpty())
                .map(intersection -> ResolvedPlace.boundary(intersection.getId()))
                .toList();
    }

    private boolean onSide(
            Intersection intersection,
            BoundarySide side,
            double minX,
            double maxX,
            double minY,
            double maxY,
            double marginX,
            double marginY) {
        boolean north = intersection.getY() >= maxY - marginY;
        boolean south = intersection.getY() <= minY + marginY;
        boolean east = intersection.getX() >= maxX - marginX;
        boolean west = intersection.getX() <= minX + marginX;
        return switch (side) {
            case NORTH -> north;
            case SOUTH -> south;
            case EAST -> east;
            case WEST -> west;
            case ANY -> north || south || east || west;
        };
    }
}
