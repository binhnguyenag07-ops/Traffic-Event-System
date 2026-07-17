package team.traffic.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.observer.TrafficEventListener;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.service.RoutingService;

public class DynamicReroutingListener implements TrafficEventListener {

    private final Supplier<RoadNetwork> network;
    private final RoutingService routingService;
    private final Supplier<List<Vehicle>> vehicles;
    private int reroutingCount;

    public DynamicReroutingListener(
            RoadNetwork network,
            RoutingService routingService,
            List<Vehicle> vehicles) {
        RoadNetwork fixedNetwork = Objects.requireNonNull(network, "RoadNetwork must not be null.");
        this.network = () -> fixedNetwork;
        this.routingService = Objects.requireNonNull(routingService, "RoutingService must not be null.");
        List<Vehicle> snapshot = new ArrayList<>(Objects.requireNonNull(vehicles, "Vehicles list must not be null."));
        this.vehicles = () -> snapshot;
        this.reroutingCount = 0;
    }

    public DynamicReroutingListener(
            RoadNetwork network,
            RoutingService routingService,
            Supplier<List<Vehicle>> vehicles) {
        RoadNetwork fixedNetwork = Objects.requireNonNull(network, "RoadNetwork must not be null.");
        this.network = () -> fixedNetwork;
        this.routingService = Objects.requireNonNull(routingService, "RoutingService must not be null.");
        this.vehicles = Objects.requireNonNull(vehicles, "Vehicles supplier must not be null.");
        this.reroutingCount = 0;
    }

    public DynamicReroutingListener(
            Supplier<RoadNetwork> network,
            RoutingService routingService,
            Supplier<List<Vehicle>> vehicles) {
        this.network = Objects.requireNonNull(network, "RoadNetwork supplier must not be null.");
        this.routingService = Objects.requireNonNull(routingService, "RoutingService must not be null.");
        this.vehicles = Objects.requireNonNull(vehicles, "Vehicles supplier must not be null.");
        this.reroutingCount = 0;
    }

    @Override
    public void onTrafficEvent(TrafficEvent event) {
        if (event == null || event.getStatus() != team.traffic.domain.event.TrafficEventStatus.ACTIVE) {
            return;
        }
        String affectedRoadId = event.getAffectedRoadId();

        for (Vehicle vehicle : vehicles.get()) {
            if (shouldReroute(vehicle, affectedRoadId)) {
                reroute(vehicle);
            }
        }
    }

    private boolean shouldReroute(Vehicle vehicle, String affectedRoadId) {
        if (vehicle.getStatus() == VehicleStatus.ARRIVED) {
            return false;
        }

        // A vehicle already travelling on the affected road completes that
        // segment before choosing a new route from the next intersection.
        if (affectedRoadId.equals(vehicle.getOccupiedRoadId())) {
            return false;
        }

        if (vehicle.getStatus() == VehicleStatus.BLOCKED) {
            return true;
        }

        if (vehicle.getCurrentRoute() == null) {
            return false;
        }

        return vehicle.getCurrentRoute().getSteps().stream()
                .skip(vehicle.getNextRouteStepIndex())
                .anyMatch(step -> step.getRoadId().equals(affectedRoadId));
    }

    private void reroute(Vehicle vehicle) {
        vehicle.recomputeRoute(network.get(), routingService);
        reroutingCount++;
    }

    public int getReroutingCount() {
        return reroutingCount;
    }

    public List<Vehicle> getVehicles() {
        return List.copyOf(vehicles.get());
    }
}
