package team.traffic.simulation;

import java.util.List;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.signal.TrafficSignal;

public record SimulationSnapshot(
        long tick,
        double elapsedSeconds,
        SimulationStatus status,
        List<Vehicle> vehicles,
        List<TrafficEvent> trafficEvents,
        List<TrafficSignal> trafficSignals) {

    public SimulationSnapshot {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Elapsed seconds must not be negative.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Simulation status must not be null.");
        }
        vehicles = vehicles == null ? List.of() : List.copyOf(vehicles);
        trafficEvents = trafficEvents == null ? List.of() : List.copyOf(trafficEvents);
        trafficSignals = trafficSignals == null ? List.of() : List.copyOf(trafficSignals);
    }

    public SimulationSnapshot(long tick, SimulationStatus status,
            List<Vehicle> vehicles, List<TrafficEvent> trafficEvents) {
        this(tick, tick, status, vehicles, trafficEvents, List.of());
    }

    public SimulationSnapshot(long tick, double elapsedSeconds, SimulationStatus status,
            List<Vehicle> vehicles, List<TrafficEvent> trafficEvents) {
        this(tick, elapsedSeconds, status, vehicles, trafficEvents, List.of());
    }
}
