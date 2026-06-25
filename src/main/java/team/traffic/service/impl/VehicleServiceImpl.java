package team.traffic.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleFactory;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.service.RoutingService;
import team.traffic.service.VehicleService;

public class VehicleServiceImpl implements VehicleService {

    private final List<Vehicle> vehicles;
    private final VehicleFactory vehicleFactory;

    public VehicleServiceImpl() {
        this.vehicles = new ArrayList<>();
        this.vehicleFactory = new VehicleFactory();
    }

    @Override
    public List<Vehicle> getAllVehicles() {
        return new ArrayList<>(vehicles);
    }

    @Override
    public Vehicle getVehicleById(String id) {
        return vehicles.stream()
                .filter(v -> v.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Vehicle createVehicle(VehicleType type, String startIntersectionId, String destinationIntersectionId, RoadNetwork network) {
        String id = "V-" + UUID.randomUUID().toString().substring(0, 8);
        Vehicle vehicle = vehicleFactory.createVehicle(type, startIntersectionId, destinationIntersectionId);
        // Using reflection or a specific method to set ID if possible, but VehicleFactory might not have overloaded method with ID in interface.
        // Wait, VehicleFactory has createVehicle(VehicleType, String, String) and also createCar(String, String, String).
        // Let's assume createVehicle handles ID generation internally, or we use it as is.
        // I will use createVehicle directly.
        vehicles.add(vehicle);
        return vehicle;
    }

    @Override
    public void updateAllVehicles(RoadNetwork network, RoutingService routingService) {
        if (network == null || routingService == null) {
            return;
        }

        for (Vehicle vehicle : vehicles) {
            if (vehicle.getStatus() == VehicleStatus.WAITING && vehicle.getCurrentRoute() == null) {
                // Not initialized route
                vehicle.requestRoute(network, routingService);
            } else if (vehicle.getStatus() == VehicleStatus.MOVING || vehicle.getStatus() == VehicleStatus.WAITING) {
                vehicle.moveOneStep(network);
            } else if (vehicle.getStatus() == VehicleStatus.BLOCKED) {
                vehicle.recomputeRoute(network, routingService);
                if (vehicle.getStatus() == VehicleStatus.WAITING) {
                    // Try to move again if recompute succeeded
                    vehicle.moveOneStep(network);
                }
            }
        }
    }
}
