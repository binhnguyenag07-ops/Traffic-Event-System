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
import team.traffic.domain.routing.BuildingRoutePlan;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.service.VehicleService;
import team.traffic.simulation.TrafficSimulator;

public class VehicleServiceImpl implements VehicleService {

    private final List<Vehicle> vehicles;
    private final VehicleFactory vehicleFactory;
    private final TrafficSimulator runtime;
    private final RoutingService routingService;

    public VehicleServiceImpl() {
        this.vehicles = new ArrayList<>();
        this.vehicleFactory = new VehicleFactory();
        this.runtime = null;
        this.routingService = null;
    }

    public VehicleServiceImpl(TrafficSimulator runtime) {
        this(runtime, null);
    }

    public VehicleServiceImpl(TrafficSimulator runtime, RoutingService routingService) {
        this.vehicles = new ArrayList<>();
        this.vehicleFactory = new VehicleFactory();
        this.runtime = runtime;
        this.routingService = routingService;
    }

    @Override
    public List<Vehicle> getAllVehicles() {
        return runtime == null ? new ArrayList<>(vehicles) : new ArrayList<>(runtime.getVehicles());
    }

    @Override
    public Vehicle getVehicleById(String id) {
        return getAllVehicles().stream()
                .filter(v -> v.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Vehicle createVehicle(VehicleType type, String startIntersectionId, String destinationIntersectionId, RoadNetwork network) {
        validateVehicleRequest(type, startIntersectionId, destinationIntersectionId, network);

        String id = "V-" + UUID.randomUUID().toString().substring(0, 8);
        Vehicle vehicle = vehicleFactory.createVehicle(type, id, startIntersectionId, destinationIntersectionId);
        if (runtime == null) {
            vehicles.add(vehicle);
        } else {
            runtime.addVehicle(vehicle);
        }
        return vehicle;
    }

    @Override
    public boolean removeVehicle(String id) {
        if (runtime != null) {
            return runtime.removeVehicle(id);
        }
        return vehicles.removeIf(vehicle -> vehicle.getId().equals(id));
    }

    @Override
    public Vehicle createVehicleBetweenBuildings(VehicleType type, String originBuildingId,
            String destinationBuildingId, RoadNetwork network) {
        if (type == null) throw new IllegalArgumentException("Vehicle type is required");
        if (network == null) throw new IllegalArgumentException("Road network is required");
        if (routingService == null) {
            throw new IllegalStateException("Routing service is required for building trips");
        }

        BuildingRoutePlan plan = routingService.findRouteBetweenBuildings(
                network, originBuildingId, destinationBuildingId, RoutePreference.CONGESTION_AWARE);
        String id = "V-" + UUID.randomUUID().toString().substring(0, 8);
        Vehicle vehicle = vehicleFactory.createVehicle(
                type,
                id,
                plan.originAccess().intersectionId(),
                plan.destinationAccess().intersectionId());
        vehicle.assignBuildingTrip(originBuildingId, destinationBuildingId);
        vehicle.setCurrentRoute(plan.routeResult().getRoute());

        if (runtime == null) {
            vehicles.add(vehicle);
        } else {
            runtime.addVehicle(vehicle);
        }
        return vehicle;
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
