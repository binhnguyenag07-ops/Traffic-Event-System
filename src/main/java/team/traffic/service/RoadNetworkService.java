package team.traffic.service;

import java.util.List;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;

public interface RoadNetworkService {
    RoadNetwork getNetwork();

    Road recordVehicleEnteringRoad(String roadId);

    Road recordVehicleLeavingRoad(String roadId);

    double updateVehicleDensity(String roadId);

    double getAverageVehicleDensity();

    List<Road> findRoadsAtOrAboveDensity(double minimumDensity);

    default void replaceNetwork(RoadNetwork network) {
        throw new UnsupportedOperationException("Replacing the network is not supported.");
    }
}
