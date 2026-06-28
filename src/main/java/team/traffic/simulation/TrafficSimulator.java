package team.traffic.simulation;

import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.CongestionEvent;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.event.TrafficLightDelayEvent;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleFactory;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.service.RoutingService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrafficSimulator {
    private final RoadNetwork network;
    private final RoutingService routingService;
    private final VehicleFactory vehicleFactory;
    private final TickProcessor tickProcessor;
    private final SimulationClock clock;

    private SimulationStatus status;
    private List<Vehicle> vehicles;
    private List<TrafficEvent> events;
    private List<VehicleConfig> vehicleConfigs;
    private List<EventConfig> eventConfigs;

    public TrafficSimulator(RoadNetwork network, RoutingService routingService) {
        this.network = network;
        this.routingService = routingService;
        this.vehicleFactory = new VehicleFactory();
        this.tickProcessor = new TickProcessor(routingService);
        this.clock = new SimulationClock();
        this.status = SimulationStatus.READY;
        this.vehicles = new ArrayList<>();
        this.events = new ArrayList<>();
        this.vehicleConfigs = new ArrayList<>();
        this.eventConfigs = new ArrayList<>();
    }

    public synchronized void initialize(List<VehicleConfig> vehicleConfigs, List<EventConfig> eventConfigs) {
        resolveActiveEvents();
        this.vehicleConfigs = new ArrayList<>(vehicleConfigs);
        this.eventConfigs = new ArrayList<>(eventConfigs);
        this.vehicles = buildVehicles(this.vehicleConfigs);
        this.events = buildEvents(this.eventConfigs);
        this.clock.reset();
        this.status = SimulationStatus.READY;
    }

    public synchronized void start() {
        if (status != SimulationStatus.READY) {
            throw new IllegalStateException("Cannot start: simulation is " + status);
        }
        status = SimulationStatus.RUNNING;
    }

    public synchronized void pause() {
        if (status != SimulationStatus.RUNNING) {
            throw new IllegalStateException("Cannot pause: simulation is " + status);
        }
        status = SimulationStatus.PAUSED;
    }

    public synchronized void resume() {
        if (status != SimulationStatus.PAUSED) {
            throw new IllegalStateException("Cannot resume: simulation is " + status);
        }
        status = SimulationStatus.RUNNING;
    }

    public synchronized void reset() {
        resolveActiveEvents();
        this.vehicles = buildVehicles(this.vehicleConfigs);
        this.events = buildEvents(this.eventConfigs);
        this.clock.reset();
        this.status = SimulationStatus.READY;
    }

    public synchronized void advanceTick() {
        tickProcessor.process(clock.getCurrentTick(), network, vehicles, events);
        clock.tick();
    }

    public synchronized SimulationStatus getStatus() {
        return status;
    }

    public synchronized long getCurrentTick() {
        return clock.getCurrentTick();
    }

    public synchronized List<Vehicle> getVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    public synchronized List<TrafficEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public synchronized void addEvent(TrafficEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    private void resolveActiveEvents() {
        for (TrafficEvent event : events) {
            if (event.getStatus() == TrafficEventStatus.ACTIVE) {
                try {
                    event.resolve(network);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private List<Vehicle> buildVehicles(List<VehicleConfig> configs) {
        List<Vehicle> result = new ArrayList<>();
        for (VehicleConfig config : configs) {
            if (!network.containsIntersection(config.startIntersectionId())
                    || !network.containsIntersection(config.destinationIntersectionId())) {
                continue;
            }
            try {
                Vehicle vehicle = createVehicle(config);
                vehicle.requestRoute(network, routingService);
                result.add(vehicle);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private Vehicle createVehicle(VehicleConfig config) {
        VehicleType type;
        try {
            type = VehicleType.valueOf(config.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            type = VehicleType.CAR;
        }
        return switch (type) {
            case CAR -> vehicleFactory.createCar(config.id(), config.startIntersectionId(), config.destinationIntersectionId());
            case BUS -> vehicleFactory.createBus(config.id(), config.startIntersectionId(), config.destinationIntersectionId());
            case EMERGENCY -> vehicleFactory.createEmergencyVehicle(config.id(), config.startIntersectionId(), config.destinationIntersectionId());
        };
    }

    private List<TrafficEvent> buildEvents(List<EventConfig> configs) {
        List<TrafficEvent> result = new ArrayList<>();
        for (EventConfig config : configs) {
            try {
                result.add(createEvent(config));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private TrafficEvent createEvent(EventConfig config) {
        return switch (config.type().toUpperCase()) {
            case "CONGESTION" -> new CongestionEvent(config.id(), config.affectedRoadId(), config.startTick(), config.durationTicks(), 0.5);
            case "ACCIDENT" -> new AccidentEvent(config.id(), config.affectedRoadId(), config.startTick(), config.durationTicks(), 0.5, "system");
            case "ROAD_CLOSURE" -> new RoadClosureEvent(config.id(), config.affectedRoadId(), config.startTick(), config.durationTicks());
            case "TRAFFIC_LIGHT_DELAY" -> new TrafficLightDelayEvent(config.id(), config.affectedRoadId(), config.startTick(), config.durationTicks(), 5L);
            default -> new CongestionEvent(config.id(), config.affectedRoadId(), config.startTick(), config.durationTicks(), 0.3);
        };
    }
}
