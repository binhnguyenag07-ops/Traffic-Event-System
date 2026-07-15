package team.traffic.simulation;

import java.util.List;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.vehicle.Vehicle;

public record SimulationSnapshot(
        long tick,
        SimulationStatus status,
        List<Vehicle> vehicles,
        List<TrafficEvent> trafficEvents) {

    public SimulationSnapshot {
        if (status == null) {
            throw new IllegalArgumentException("Simulation status must not be null.");
        }
        vehicles = vehicles == null ? List.of() : List.copyOf(vehicles);
        trafficEvents = trafficEvents == null ? List.of() : List.copyOf(trafficEvents);
    }
}
