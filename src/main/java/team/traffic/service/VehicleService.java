package team.traffic.service;

import java.util.List;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.network.RoadNetwork;

public interface VehicleService {
    List<Vehicle> getAllVehicles();
    Vehicle getVehicleById(String id);
    Vehicle createVehicle(VehicleType type, String startIntersectionId, String destinationIntersectionId, RoadNetwork network);
    void updateAllVehicles(RoadNetwork network, RoutingService routingService);
}
