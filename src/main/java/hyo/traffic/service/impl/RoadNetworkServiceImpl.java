package hyo.traffic.service.impl;

import hyo.traffic.domain.network.Intersection;
import hyo.traffic.domain.network.Road;
import hyo.traffic.domain.network.RoadCondition;
import hyo.traffic.domain.network.RoadNetwork;
import hyo.traffic.persistence.MapLoader;
import hyo.traffic.persistence.SyntheticMapGenerator;
import hyo.traffic.service.RoadNetworkService;

public class RoadNetworkServiceImpl implements RoadNetworkService {

    private final RoadNetwork roadNetwork;
    private final MapLoader mapLoader;
    private final SyntheticMapGenerator mapGenerator;

    public RoadNetworkServiceImpl(RoadNetwork roadNetwork, MapLoader mapLoader, SyntheticMapGenerator mapGenerator) {
        this.roadNetwork = roadNetwork;
        this.mapLoader = mapLoader;
        this.mapGenerator = mapGenerator;
    }

    @Override
    public RoadNetwork getRoadNetwork() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void addIntersection(Intersection intersection) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void removeIntersection(String intersectionId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void addRoad(Road road) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void removeRoad(String roadId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void updateRoadCondition(String roadId, RoadCondition condition) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void loadMap(String sourcePath) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void generateSyntheticMap(int intersectionCount, int roadCount) {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
