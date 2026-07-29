package team.traffic.routing;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Stream;

import team.traffic.domain.network.Building;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.BuildingRoutePlan;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.service.RoutingService;

/** Selects the cheapest reachable entrance instead of the nearest junction. */
public final class BuildingRoutePlanner {
    private static final double DRIVEWAY_SPEED_KPH = 15.0;
    private final RoutingService routingService;

    public BuildingRoutePlanner(RoutingService routingService) {
        this.routingService = Objects.requireNonNull(routingService, "Routing service is required");
    }

    public BuildingRoutePlan findRouteToBuilding(RoadNetwork network, String startIntersectionId,
            String destinationBuildingId, RoutePreference preference) {
        Objects.requireNonNull(network, "Road network is required");
        Building destination = requireBuilding(network, destinationBuildingId);
        return destination.getAccessPoints().stream()
                .flatMap(access -> candidate(network, startIntersectionId, null, access, preference))
                .min(planComparator())
                .orElseThrow(() -> new NoSuchElementException(
                        "No reachable access point for building: " + destinationBuildingId));
    }

    public BuildingRoutePlan findRouteBetweenBuildings(RoadNetwork network, String originBuildingId,
            String destinationBuildingId, RoutePreference preference) {
        Objects.requireNonNull(network, "Road network is required");
        Building origin = requireBuilding(network, originBuildingId);
        Building destination = requireBuilding(network, destinationBuildingId);
        return origin.getAccessPoints().stream()
                .flatMap(start -> destination.getAccessPoints().stream()
                        .flatMap(end -> candidate(network, start.intersectionId(), start, end, preference)))
                .filter(plan -> !plan.routeResult().getRoute().isEmpty())
                .min(planComparator())
                .orElseThrow(() -> new NoSuchElementException(
                        "No route between buildings " + originBuildingId + " and " + destinationBuildingId));
    }

    private Stream<BuildingRoutePlan> candidate(RoadNetwork network, String startIntersectionId,
            BuildingAccessPoint originAccess, BuildingAccessPoint destinationAccess,
            RoutePreference preference) {
        try {
            RouteResult result = routingService.findRoute(network,
                    new RouteRequest(startIntersectionId, destinationAccess.intersectionId(), preference));
            double drivewayMetres = destinationAccess.drivewayDistanceMetres()
                    + (originAccess == null ? 0 : originAccess.drivewayDistanceMetres());
            return Stream.of(new BuildingRoutePlan(originAccess, destinationAccess, result,
                    score(result, preference, drivewayMetres)));
        } catch (RuntimeException exception) {
            return Stream.empty();
        }
    }

    private double score(RouteResult result, RoutePreference preference, double drivewayMetres) {
        return switch (preference) {
            case SHORTEST_DISTANCE -> result.getRoute().getTotalDistance() + drivewayMetres / 1_000.0;
            case MINIMUM_HOPS -> result.getRoute().getStepCount() + drivewayMetres / 1_000_000.0;
            case SHORTEST_TIME, CONGESTION_AWARE -> result.getRoute().getTotalCost()
                    + drivewayMetres / 1_000.0 / DRIVEWAY_SPEED_KPH;
        };
    }

    private Comparator<BuildingRoutePlan> planComparator() {
        return Comparator.comparingDouble(BuildingRoutePlan::totalScore)
                .thenComparing(plan -> plan.originAccess() == null ? "" : plan.originAccess().id())
                .thenComparing(plan -> plan.destinationAccess().id());
    }

    private Building requireBuilding(RoadNetwork network, String buildingId) {
        if (buildingId == null || buildingId.isBlank()) {
            throw new IllegalArgumentException("Building id is required");
        }
        Building building = network.getBuilding(buildingId);
        if (building == null) throw new IllegalArgumentException("Building not found: " + buildingId);
        return building;
    }
}
