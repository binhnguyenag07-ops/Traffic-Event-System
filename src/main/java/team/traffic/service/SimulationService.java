package team.traffic.service;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.VehicleConfig;

import java.util.List;
import java.util.function.Consumer;

public interface SimulationService {
    void initialize(List<VehicleConfig> vehicles, List<EventConfig> events);
    void start();
    void pause();
    void resume();
    void reset();
    void advanceTick();
    void addEvent(TrafficEvent event);
    SimulationStatus getStatus();
    long getCurrentTick();
    SimulationSnapshot getSnapshot();
    List<Vehicle> getVehicles();
    List<TrafficEvent> getEvents();
    default void setParameters(double simulationSecondsPerTick, long playbackIntervalMillis) {
        throw new UnsupportedOperationException("Simulation parameters are not supported.");
    }

    default double getSimulationSecondsPerTick() {
        return 10.0;
    }

    default long getPlaybackIntervalMillis() {
        return 1000;
    }

    default void addSnapshotListener(Consumer<SimulationSnapshot> listener) {}

    default void removeSnapshotListener(Consumer<SimulationSnapshot> listener) {}

    default void publishCurrentSnapshot() {}

    default void addVehicle(Vehicle vehicle) {
        throw new UnsupportedOperationException("Adding vehicles is not supported.");
    }

    default boolean removeVehicle(String vehicleId) {
        return false;
    }

    default boolean removeEvent(String eventId) {
        return false;
    }

    default RoadNetwork getNetwork() {
        return null;
    }

    default void replaceNetwork(RoadNetwork network) {
        throw new UnsupportedOperationException("Replacing the network is not supported.");
    }
}
