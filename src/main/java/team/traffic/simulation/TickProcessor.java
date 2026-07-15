package team.traffic.simulation;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.service.RoutingService;

import java.util.List;

public class TickProcessor {
    private final RoutingService routingService;

    public TickProcessor(RoutingService routingService) {
        this.routingService = routingService;
    }

    public void process(long currentTick, RoadNetwork network, List<Vehicle> vehicles, List<TrafficEvent> events) {
        for (TrafficEvent event : events) {
            if (event.getStatus() == TrafficEventStatus.SCHEDULED && event.getStartTick() <= currentTick) {
                try {
                    event.apply(network);
                } catch (Exception ignored) {
                }
            }
        }

        for (Vehicle vehicle : vehicles) {
            if (!vehicle.hasReachedDestination()) {
                vehicle.advance(Vehicle.DEFAULT_TICK_DURATION_SECONDS, network);
                if (vehicle.getStatus() == VehicleStatus.BLOCKED) {
                    try {
                        vehicle.recomputeRoute(network, routingService);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        for (TrafficEvent event : events) {
            if (event.getStatus() == TrafficEventStatus.ACTIVE && !event.isActive(currentTick)) {
                try {
                    event.resolve(network);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
