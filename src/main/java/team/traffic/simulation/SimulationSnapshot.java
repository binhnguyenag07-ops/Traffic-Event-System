package team.traffic.simulation;

import java.util.List;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.Road;
import team.traffic.domain.vehicle.Vehicle;

public record SimulationSnapshot(
        long tick,
        SimulationStatus status,
        List<Vehicle> vehicles,
        List<TrafficEvent> trafficEvents,
        List<Road> roads) {

    public SimulationSnapshot {
        if (status == null) {
            throw new IllegalArgumentException("Simulation status must not be null.");
        }
        vehicles = vehicles == null ? List.of() : List.copyOf(vehicles);
        trafficEvents = trafficEvents == null ? List.of() : List.copyOf(trafficEvents);
        roads = roads == null ? List.of() : List.copyOf(roads);
    }

    /**
     * Compact constructor kept for callers that predate dynamic road state.
     */
    public SimulationSnapshot(
            long tick,
            SimulationStatus status,
            List<Vehicle> vehicles,
            List<TrafficEvent> trafficEvents) {
        this(tick, status, vehicles, trafficEvents, List.of());
    }
}
