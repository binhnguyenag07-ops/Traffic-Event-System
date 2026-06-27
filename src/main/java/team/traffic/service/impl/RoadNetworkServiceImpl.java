package team.traffic.service.impl;

import java.util.List;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.service.RoadNetworkService;

public class RoadNetworkServiceImpl implements RoadNetworkService {
    private final RoadNetwork network;

    public RoadNetworkServiceImpl(RoadNetwork network) {
        if (network == null) {
            throw new IllegalArgumentException("Road network is required");
        }
        this.network = network;
    }

    @Override
    public RoadNetwork getNetwork() {
        return network;
    }

    @Override
    public Road recordVehicleEnteringRoad(String roadId) {
        return network.recordVehicleEnteringRoad(roadId);
    }

    @Override
    public Road recordVehicleLeavingRoad(String roadId) {
        return network.recordVehicleLeavingRoad(roadId);
    }

    @Override
    public double updateVehicleDensity(String roadId) {
        return network.updateVehicleDensity(roadId);
    }

    @Override
    public double getAverageVehicleDensity() {
        return network.getAverageVehicleDensity();
    }

    @Override
    public List<Road> findRoadsAtOrAboveDensity(double minimumDensity) {
        return network.findRoadsAtOrAboveDensity(minimumDensity);
    }
}
