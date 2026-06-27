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
        validateVehicleRequest(type, startIntersectionId, destinationIntersectionId, network);

        String id = "V-" + UUID.randomUUID().toString().substring(0, 8);
        Vehicle vehicle = vehicleFactory.createVehicle(type, id, startIntersectionId, destinationIntersectionId);
        vehicles.add(vehicle);
        return vehicle;
    }

    @Override
    public void updateAllVehicles(RoadNetwork network, RoutingService routingService) {
        if (network == null || routingService == null) {
            return;
        }

        for (Vehicle vehicle : vehicles) {
            if (vehicle.getStatus() == VehicleStatus.ARRIVED) {
                continue;
            }

            if (vehicle.getCurrentRoute() == null || vehicle.getStatus() == VehicleStatus.BLOCKED) {
                vehicle.recomputeRoute(network, routingService);
            }

            moveIfReady(vehicle, network);

            if (vehicle.getStatus() == VehicleStatus.BLOCKED && !vehicle.hasReachedDestination()) {
                vehicle.recomputeRoute(network, routingService);
                moveIfReady(vehicle, network);
            }
        }
    }

    private void moveIfReady(Vehicle vehicle, RoadNetwork network) {
        if (vehicle.getStatus() == VehicleStatus.WAITING || vehicle.getStatus() == VehicleStatus.MOVING) {
            vehicle.moveOneStep(network);
        }
    }

    private void validateVehicleRequest(
            VehicleType type,
            String startIntersectionId,
            String destinationIntersectionId,
            RoadNetwork network) {
        if (type == null) {
            throw new IllegalArgumentException("Vehicle type is required");
        }
        if (startIntersectionId == null || startIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Start intersection id is required");
        }
        if (destinationIntersectionId == null || destinationIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Destination intersection id is required");
        }
        if (network != null && !network.containsIntersection(startIntersectionId)) {
            throw new IllegalArgumentException("Start intersection not found: " + startIntersectionId);
        }
        if (network != null && !network.containsIntersection(destinationIntersectionId)) {
            throw new IllegalArgumentException("Destination intersection not found: " + destinationIntersectionId);
        }
    }
}
