package team.traffic.service;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.routing.PathFindingStrategy;
import java.util.List;
import team.traffic.domain.statistics.AlgorithmStatistics;
import team.traffic.domain.routing.BuildingRoutePlan;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.routing.BuildingRoutePlanner;

public interface RoutingService {
    RouteResult findRoute(RoadNetwork network, RouteRequest request);

    default RouteResult findRoute(RoadNetwork network, RouteRequest request, String algorithm) {
        return findRoute(network, request);
    }

    default List<PathFindingStrategy> getStrategies() {
        return List.of();
    }

    default List<AlgorithmStatistics> getAlgorithmStatistics() {
        return List.of();
    }

    default void recordRouteRecalculation(String algorithmName) {}

    default BuildingRoutePlan findRouteToBuilding(RoadNetwork network, String startIntersectionId,
            String destinationBuildingId, RoutePreference preference) {
        return new BuildingRoutePlanner(this)
                .findRouteToBuilding(network, startIntersectionId, destinationBuildingId, preference);
    }

    default BuildingRoutePlan findRouteBetweenBuildings(RoadNetwork network, String originBuildingId,
            String destinationBuildingId, RoutePreference preference) {
        return new BuildingRoutePlanner(this)
                .findRouteBetweenBuildings(network, originBuildingId, destinationBuildingId, preference);
    }
}
