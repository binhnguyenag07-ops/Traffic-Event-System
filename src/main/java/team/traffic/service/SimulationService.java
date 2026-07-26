package team.traffic.service;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.statistics.CompletedTrip;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationClock;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.VehicleConfig;
import team.traffic.simulation.trace.VehicleTracePoint;
import team.traffic.simulation.trace.VehicleTraceSummary;
import team.traffic.simulation.trace.FundamentalDiagramPoint;
import team.traffic.simulation.trace.RoadFlowSample;
import team.traffic.simulation.trace.TimeSpaceDiagram;
import team.traffic.simulation.history.SimulationFrame;
import team.traffic.simulation.history.SimulationTimeline;
import team.traffic.domain.signal.TrafficSignal;

import java.util.List;
import java.util.Optional;
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
    default double getElapsedSeconds() { return getCurrentTick(); }

    /**
     * Seconds since midnight the run starts at. Published so a client can label
     * any recorded frame with its clock time from that frame's elapsed seconds,
     * rather than every frame carrying a copy of a derived value.
     */
    default double getStartTimeOfDaySeconds() {
        return SimulationClock.DEFAULT_START_TIME_OF_DAY_SECONDS;
    }

    /** Seconds since midnight right now. */
    default double getTimeOfDaySeconds() {
        return SimulationClock.timeOfDayAt(getStartTimeOfDaySeconds(), getElapsedSeconds());
    }
    SimulationSnapshot getSnapshot();
    List<Vehicle> getVehicles();
    List<TrafficEvent> getEvents();
    default List<TrafficSignal> getSignals() { return List.of(); }
    default List<CompletedTrip> getCompletedTrips() {
        return List.of();
    }
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

    default void resetFrameTimeline() {}

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

    // --- vehicle trace timeline ---
    // Declared as defaults so a partial implementation, such as a test stub,
    // stays valid; an implementation that keeps no history reports none.

    default VehicleTraceSummary getVehicleTraceSummary() {
        return new VehicleTraceSummary(null, null, 0, List.of());
    }

    default boolean hasVehicleTraceAtTick(long tick) {
        return false;
    }

    default List<VehicleTracePoint> getVehicleStatesAtTick(long tick) {
        return List.of();
    }

    default Optional<VehicleTracePoint> getVehicleStateAtTick(String vehicleId, long tick) {
        return Optional.empty();
    }

    default List<VehicleTracePoint> getVehicleTrace(String vehicleId, long fromTick, long toTick) {
        return List.of();
    }

    default List<RoadFlowSample> getRoadFlowSamples() { return List.of(); }

    default List<FundamentalDiagramPoint> getFundamentalDiagram() { return List.of(); }

    default TimeSpaceDiagram getTimeSpaceDiagram(String roadId) {
        return new TimeSpaceDiagram(roadId, List.of());
    }

    // --- authoritative renderer-frame history ---

    default SimulationTimeline getFrameTimeline() {
        return new SimulationTimeline("unavailable", 0, 0, 0, 0, List.of());
    }

    default Optional<SimulationFrame> getFrameAtOrBefore(double elapsedSeconds) {
        return Optional.empty();
    }

    default Optional<SimulationFrame> getCurrentFrame() {
        return Optional.empty();
    }
}
