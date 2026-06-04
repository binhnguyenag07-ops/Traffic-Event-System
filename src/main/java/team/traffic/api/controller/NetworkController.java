package team.traffic.api.controller;

import team.traffic.api.dto.ApiResponse;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.service.RoadNetworkService;

public class NetworkController {
    private final RoadNetworkService roadNetworkService;

    public NetworkController(RoadNetworkService roadNetworkService) {
        this.roadNetworkService = roadNetworkService;
    }

    public ApiResponse<RoadNetwork> getRoadNetwork() {
        return ApiResponse.success(roadNetworkService.getRoadNetwork());
    }

    public ApiResponse<Intersection> addIntersection(Intersection intersection) {
        roadNetworkService.addIntersection(intersection);
        return ApiResponse.success(intersection);
    }

    public ApiResponse<Void> removeIntersection(String intersectionId) {
        roadNetworkService.removeIntersection(intersectionId);
        return ApiResponse.success(null);
    }

    public ApiResponse<Road> addRoad(Road road) {
        roadNetworkService.addRoad(road);
        return ApiResponse.success(road);
    }

    public ApiResponse<Void> removeRoad(String roadId) {
        roadNetworkService.removeRoad(roadId);
        return ApiResponse.success(null);
    }

    public ApiResponse<Void> updateRoadCondition(String roadId, RoadCondition condition) {
        roadNetworkService.updateRoadCondition(roadId, condition);
        return ApiResponse.success(null);
    }
}
