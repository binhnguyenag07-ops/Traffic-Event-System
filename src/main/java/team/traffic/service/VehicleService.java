package team.traffic.service;

import java.util.List;
import team.traffic.domain.vehicle.Vehicle;

public interface VehicleService {
    Vehicle createVehicle(String id, String vehicleType, String startIntersectionId, String destinationIntersectionId);

    Vehicle findVehicleById(String vehicleId);

    List<Vehicle> getActiveVehicles();

    void removeVehicle(String vehicleId);

    void moveVehicleOneStep(String vehicleId);

    void recomputeRouteForVehicle(String vehicleId);
}
