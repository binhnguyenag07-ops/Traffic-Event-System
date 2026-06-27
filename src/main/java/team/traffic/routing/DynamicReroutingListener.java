package team.traffic.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.observer.TrafficEventListener;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.service.RoutingService;

public class DynamicReroutingListener implements TrafficEventListener {

    private final RoadNetwork network;
    private final RoutingService routingService;
    private final List<Vehicle> vehicles;
    private int reroutingCount;

    public DynamicReroutingListener(
            RoadNetwork network,
            RoutingService routingService,
            List<Vehicle> vehicles) {
        this.network = Objects.requireNonNull(network, "RoadNetwork must not be null.");
        this.routingService = Objects.requireNonNull(routingService, "RoutingService must not be null.");
        this.vehicles = new ArrayList<>(Objects.requireNonNull(vehicles, "Vehicles list must not be null."));
        this.reroutingCount = 0;
    }

    @Override
    public void onTrafficEvent(TrafficEvent event) {
        String affectedRoadId = event.getAffectedRoadId();

        for (Vehicle vehicle : vehicles) {
            if (shouldReroute(vehicle, affectedRoadId)) {
                reroute(vehicle);
            }
        }
    }

    private boolean shouldReroute(Vehicle vehicle, String affectedRoadId) {
        if (vehicle.getStatus() == VehicleStatus.ARRIVED) {
            return false;
        }

        if (vehicle.getStatus() == VehicleStatus.BLOCKED) {
            return true;
        }

        if (vehicle.getCurrentRoute() == null) {
            return false;
        }

        return vehicle.getCurrentRoute().getSteps().stream()
                .anyMatch(step -> step.getRoadId().equals(affectedRoadId));
    }

    private void reroute(Vehicle vehicle) {
        vehicle.recomputeRoute(network, routingService);
        reroutingCount++;
    }

    public int getReroutingCount() {
        return reroutingCount;
    }

    public List<Vehicle> getVehicles() {
        return List.copyOf(vehicles);
    }
}
