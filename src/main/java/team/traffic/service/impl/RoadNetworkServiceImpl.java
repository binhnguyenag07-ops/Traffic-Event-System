package team.traffic.service.impl;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.service.RoadNetworkService;

public class RoadNetworkServiceImpl implements RoadNetworkService {
    private RoadNetwork roadNetwork = new RoadNetwork();

    @Override
    public RoadNetwork getRoadNetwork() {
        return roadNetwork;
    }

    @Override
    public void addIntersection(Intersection intersection) {
        roadNetwork.addIntersection(intersection);
    }

    @Override
    public void removeIntersection(String intersectionId) {
        roadNetwork.removeIntersection(intersectionId);
    }

    @Override
    public void addRoad(Road road) {
        roadNetwork.addRoad(road);
    }

    @Override
    public void removeRoad(String roadId) {
        roadNetwork.removeRoad(roadId);
    }

    @Override
    public void updateRoadCondition(String roadId, RoadCondition condition) {
        roadNetwork.updateRoadCondition(roadId, condition);
    }

    @Override
    public RoadNetwork loadMap(String sourcePath) {
        // TODO: choose CsvMapLoader or JsonMapLoader based on file extension.
        throw new UnsupportedOperationException("TODO: implement map loading service.");
    }

    @Override
    public RoadNetwork generateSyntheticMap(int intersectionCount, int roadCount) {
        // TODO: delegate to SyntheticMapGenerator.
        throw new UnsupportedOperationException("TODO: implement synthetic map generation service.");
    }

    public void setRoadNetwork(RoadNetwork roadNetwork) {
        this.roadNetwork = roadNetwork;
    }
}
