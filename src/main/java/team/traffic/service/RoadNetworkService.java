package team.traffic.service;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;

public interface RoadNetworkService {
    RoadNetwork getRoadNetwork();

    void addIntersection(Intersection intersection);

    void removeIntersection(String intersectionId);

    void addRoad(Road road);

    void removeRoad(String roadId);

    void updateRoadCondition(String roadId, RoadCondition condition);

    RoadNetwork loadMap(String sourcePath);

    RoadNetwork generateSyntheticMap(int intersectionCount, int roadCount);
}
