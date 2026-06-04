package team.traffic.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleFactory;
import team.traffic.service.VehicleService;

public class VehicleServiceImpl implements VehicleService {
    private final VehicleFactory vehicleFactory = new VehicleFactory();
    private final List<Vehicle> vehicles = new ArrayList<>();

    @Override
    public Vehicle createVehicle(String id, String vehicleType, String startIntersectionId, String destinationIntersectionId) {
        Vehicle vehicle = vehicleFactory.createVehicle(id, vehicleType, startIntersectionId, destinationIntersectionId);
        vehicles.add(vehicle);
        return vehicle;
    }

    @Override
    public Vehicle findVehicleById(String vehicleId) {
        return vehicles.stream()
                .filter(vehicle -> vehicle.getId().equals(vehicleId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Vehicle> getActiveVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    @Override
    public void removeVehicle(String vehicleId) {
        vehicles.removeIf(vehicle -> vehicle.getId().equals(vehicleId));
    }

    @Override
    public void moveVehicleOneStep(String vehicleId) {
        Vehicle vehicle = findVehicleById(vehicleId);
        if (vehicle != null) {
            vehicle.moveOneStep();
        }
    }

    @Override
    public void recomputeRouteForVehicle(String vehicleId) {
        Vehicle vehicle = findVehicleById(vehicleId);
        if (vehicle != null) {
            vehicle.recomputeRoute();
        }
    }
}
