package team.traffic.simulation;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.event.TrafficLightDelayEvent;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.service.RoutingService;
import team.traffic.observer.TrafficEventBus;

import java.util.ArrayList;
import java.util.List;

public class TickProcessor {
    public static final double DEFAULT_SIMULATION_SECONDS_PER_TICK = 10.0;
    private final RoutingService routingService;
    private final TrafficEventBus eventBus;
    private final VehicleDynamicsEngine dynamicsEngine;

    public TickProcessor(RoutingService routingService) {
        this(routingService, null);
    }

    public TickProcessor(RoutingService routingService, TrafficEventBus eventBus) {
        this(routingService, eventBus, new VehicleDynamicsEngine());
    }

    /**
     * Creates a processor with a specific vehicle-dynamics engine. Passing
     * {@code null} falls back to the original movement rule, where each vehicle
     * travels at its own speed scaled by the road's aggregate congestion and
     * density, ignoring the vehicles physically around it.
     */
    public TickProcessor(
            RoutingService routingService,
            TrafficEventBus eventBus,
            VehicleDynamicsEngine dynamicsEngine) {
        this.routingService = routingService;
        this.eventBus = eventBus;
        this.dynamicsEngine = dynamicsEngine;
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

        List<Vehicle> movable = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (vehicle.hasReachedDestination()) {
                continue;
            }
            vehicle.addTravelTime(simulationSecondsPerTick);
            TrafficLightDelayEvent lightDelay = findLightDelay(vehicle, events);
            if (lightDelay != null && vehicle.waitForTrafficLight(lightDelay.getId(), lightDelay.getDelayTicks())) {
                continue;
            }
            if (lightDelay == null) {
                vehicle.clearTrafficLightWait();
            }
            movable.add(vehicle);
        }

        moveVehicles(currentTick, network, movable, simulationSecondsPerTick);

        for (Vehicle vehicle : movable) {
            if (vehicle.getStatus() == VehicleStatus.BLOCKED) {
                vehicle.recomputeRoute(network, routingService);
            }
        }

        for (TrafficEvent event : events) {
            if (event.getStatus() == TrafficEventStatus.ACTIVE && !event.isActive(currentTick)) {
                event.resolve(network);
                publish(event);
            }
        }
    }

    private void moveVehicles(
            long currentTick,
            RoadNetwork network,
            List<Vehicle> movable,
            double simulationSecondsPerTick) {
        if (dynamicsEngine == null) {
            for (Vehicle vehicle : movable) {
                vehicle.advance(network, simulationSecondsPerTick);
            }
            return;
        }

        TrafficState state = dynamicsEngine.step(currentTick, network, movable, simulationSecondsPerTick);
        publishPhysicalDensity(network, state);
    }

    /**
     * Republishes each road's density from the physical space its vehicles take
     * up, replacing the plain vehicle-count ratio the road maintains itself.
     *
     * <p>Routing cost, the statistics panel, and the map colouring all read
     * {@code vehicleDensity}, so this is what lets the rest of the system see a
     * road as full of buses rather than merely holding a few vehicles. The road
     * recomputes the count-based value whenever a vehicle enters or leaves, so
     * this runs once after all movement for the tick is done.</p>
     */
    private void publishPhysicalDensity(RoadNetwork network, TrafficState state) {
        if (state == null) {
            return;
        }
        // Empty roads already read zero from the road's own count, so only the
        // occupied ones need republishing.
        for (String roadId : state.getOccupiedRoadIds()) {
            Road road = network.getRoad(roadId);
            if (road != null) {
                road.getCondition().setVehicleDensity(state.physicalJamRatioOf(roadId));
            }
        }
    }

    private TrafficLightDelayEvent findLightDelay(Vehicle vehicle, List<TrafficEvent> events) {
        if (vehicle.getOccupiedRoadId() != null || vehicle.getNextRoadId() == null) {
            return null;
        }
        return events.stream()
                .filter(TrafficLightDelayEvent.class::isInstance)
                .map(TrafficLightDelayEvent.class::cast)
                .filter(event -> event.getStatus() == TrafficEventStatus.ACTIVE)
                .filter(event -> event.getAffectedRoadId().equals(vehicle.getNextRoadId()))
                .findFirst()
                .orElse(null);
    }

    private void publish(TrafficEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }
}
