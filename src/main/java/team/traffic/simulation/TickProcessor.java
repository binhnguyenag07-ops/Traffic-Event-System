package team.traffic.simulation;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.service.RoutingService;
import team.traffic.observer.TrafficEventBus;

import java.util.List;

public class TickProcessor {
    public static final double DEFAULT_SIMULATION_SECONDS_PER_TICK = 10.0;
    private final RoutingService routingService;
    private final TrafficEventBus eventBus;

    public TickProcessor(RoutingService routingService) {
        this(routingService, null);
    }

    public TickProcessor(RoutingService routingService, TrafficEventBus eventBus) {
        this.routingService = routingService;
        this.eventBus = eventBus;
    }

    public void process(long currentTick, RoadNetwork network, List<Vehicle> vehicles, List<TrafficEvent> events) {
        process(currentTick, network, vehicles, events, DEFAULT_SIMULATION_SECONDS_PER_TICK);
    }

    public void process(
            long currentTick,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            double simulationSecondsPerTick) {
        for (TrafficEvent event : events) {
            if (event.getStatus() == TrafficEventStatus.SCHEDULED && event.getStartTick() <= currentTick) {
                event.apply(network);
                publish(event);
            }
        }

        for (Vehicle vehicle : vehicles) {
            if (!vehicle.hasReachedDestination()) {
                vehicle.advance(network, simulationSecondsPerTick);
                if (vehicle.getStatus() == VehicleStatus.BLOCKED) {
                    vehicle.recomputeRoute(network, routingService);
                }
            }
        }

        for (TrafficEvent event : events) {
            if (event.getStatus() == TrafficEventStatus.ACTIVE && !event.isActive(currentTick)) {
                event.resolve(network);
                publish(event);
            }
        }
    }

    private void publish(TrafficEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }
}
