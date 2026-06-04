package team.traffic.simulation;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.statistics.TrafficStatistics;
import team.traffic.domain.vehicle.Vehicle;

public class SimulationSnapshot {
    private long tick;
    private SimulationStatus status;
    private RoadNetwork roadNetwork;
    private List<Vehicle> vehicles = new ArrayList<>();
    private List<TrafficEvent> activeEvents = new ArrayList<>();
    private TrafficStatistics statistics = new TrafficStatistics();

    public long getTick() {
        return tick;
    }

    public void setTick(long tick) {
        this.tick = tick;
    }

    public SimulationStatus getStatus() {
        return status;
    }

    public void setStatus(SimulationStatus status) {
        this.status = status;
    }

    public RoadNetwork getRoadNetwork() {
        return roadNetwork;
    }

    public void setRoadNetwork(RoadNetwork roadNetwork) {
        this.roadNetwork = roadNetwork;
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<Vehicle> vehicles) {
        this.vehicles = vehicles;
    }

    public List<TrafficEvent> getActiveEvents() {
        return activeEvents;
    }

    public void setActiveEvents(List<TrafficEvent> activeEvents) {
        this.activeEvents = activeEvents;
    }

    public TrafficStatistics getStatistics() {
        return statistics;
    }

    public void setStatistics(TrafficStatistics statistics) {
        this.statistics = statistics;
    }
}
