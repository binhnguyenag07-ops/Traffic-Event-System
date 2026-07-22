package team.traffic.service;

import java.util.List;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.network.RoadNetwork;

public interface VehicleService {
    List<Vehicle> getAllVehicles();
    Vehicle getVehicleById(String id);
    Vehicle createVehicle(VehicleType type, String startIntersectionId, String destinationIntersectionId, RoadNetwork network);

    default Vehicle createVehicleBetweenBuildings(
            VehicleType type,
            String originBuildingId,
            String destinationBuildingId,
            RoadNetwork network) {
        throw new UnsupportedOperationException("Building trips are not supported");
    }

    default boolean removeVehicle(String id) {
        return false;
    }
}
