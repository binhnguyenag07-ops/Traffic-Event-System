package team.traffic.service.impl;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.statistics.CompletedTrip;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.service.SimulationService;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.TrafficSimulator;
import team.traffic.simulation.VehicleConfig;
import team.traffic.simulation.trace.VehicleTracePoint;
import team.traffic.simulation.trace.VehicleTraceSummary;
import team.traffic.simulation.trace.FundamentalDiagramPoint;
import team.traffic.simulation.trace.RoadFlowSample;
import team.traffic.simulation.trace.TimeSpaceDiagram;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.signal.TrafficSignal;

public class SimulationServiceImpl implements SimulationService, AutoCloseable {
    private final TrafficSimulator simulator;
    private final ScheduledExecutorService runner;
    private final List<Consumer<SimulationSnapshot>> snapshotListeners = new CopyOnWriteArrayList<>();
    private volatile long playbackIntervalMillis = 1000;
    private ScheduledFuture<?> scheduledTicks;

    public SimulationServiceImpl(TrafficSimulator simulator) {
        this.simulator = simulator;
        this.runner = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "traffic-simulation-runner");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void initialize(List<VehicleConfig> vehicles, List<EventConfig> events) {
        cancelScheduledTicks();
        simulator.initialize(vehicles, events);
        publishSnapshot();
    }

    @Override
    public void start() {
        simulator.start();
        scheduleTicks();
    }

    @Override
    public void pause() {
        simulator.pause();
        cancelScheduledTicks();
    }

    @Override
    public void resume() {
        simulator.resume();
        scheduleTicks();
    }

    @Override
    public void reset() {
        cancelScheduledTicks();
        simulator.reset();
        publishSnapshot();
    }

    @Override
    public void advanceTick() {
        simulator.advanceTick();
        publishSnapshot();
    }

    @Override
    public void addEvent(TrafficEvent event) {
        simulator.addEvent(event);
    }

    @Override
    public SimulationStatus getStatus() {
        return simulator.getStatus();
    }

    @Override
    public long getCurrentTick() {
        return simulator.getCurrentTick();
    }

    @Override
    public double getElapsedSeconds() {
        return simulator.getElapsedSeconds();
    }

    @Override
    public SimulationSnapshot getSnapshot() {
        return simulator.getSnapshot();
    }

    @Override
    public List<Vehicle> getVehicles() {
        return simulator.getVehicles();
    }

    @Override
    public List<TrafficEvent> getEvents() {
        return simulator.getEvents();
    }

    @Override
    public List<TrafficSignal> getSignals() { return simulator.getSignals(); }

    @Override
    public List<CompletedTrip> getCompletedTrips() {
        return simulator.getCompletedTrips();
    }

    @Override
    public void addVehicle(Vehicle vehicle) {
        simulator.addVehicle(vehicle);
    }

    @Override
    public boolean removeVehicle(String vehicleId) {
        return simulator.removeVehicle(vehicleId);
    }

    @Override
    public boolean removeEvent(String eventId) {
        return simulator.removeEvent(eventId);
    }

    @Override
    public RoadNetwork getNetwork() {
        return simulator.getNetwork();
    }

    @Override
    public void replaceNetwork(RoadNetwork network) {
        cancelScheduledTicks();
        simulator.replaceNetwork(network);
        publishSnapshot();
    }

    @Override
    public synchronized void setParameters(double simulationSecondsPerTick, long playbackIntervalMillis) {
        if (playbackIntervalMillis < 250 || playbackIntervalMillis > 2000) {
            throw new IllegalArgumentException("Playback interval must be between 250 and 2000 milliseconds.");
        }
        simulator.setSimulationSecondsPerTick(simulationSecondsPerTick);
        this.playbackIntervalMillis = playbackIntervalMillis;
        if (simulator.getStatus() == SimulationStatus.RUNNING) {
            cancelScheduledTicks();
            scheduleTicks();
        }
    }

    @Override
    public double getSimulationSecondsPerTick() {
        return simulator.getSimulationSecondsPerTick();
    }

    @Override
    public long getPlaybackIntervalMillis() {
        return playbackIntervalMillis;
    }

    @Override
    public void addSnapshotListener(Consumer<SimulationSnapshot> listener) {
        snapshotListeners.add(listener);
    }

    @Override
    public void removeSnapshotListener(Consumer<SimulationSnapshot> listener) {
        snapshotListeners.remove(listener);
    }

    @Override
    public void publishCurrentSnapshot() {
        publishSnapshot();
    }

    private synchronized void scheduleTicks() {
        if (scheduledTicks != null && !scheduledTicks.isDone()) {
            return;
        }
        scheduledTicks = runner.scheduleWithFixedDelay(() -> {
            if (simulator.getStatus() == SimulationStatus.RUNNING) {
                advanceTick();
            }
        }, playbackIntervalMillis, playbackIntervalMillis, TimeUnit.MILLISECONDS);
    }

    private synchronized void cancelScheduledTicks() {
        if (scheduledTicks != null) {
            scheduledTicks.cancel(false);
            scheduledTicks = null;
        }
    }

    private void publishSnapshot() {
        SimulationSnapshot snapshot = simulator.getSnapshot();
        for (Consumer<SimulationSnapshot> listener : snapshotListeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException ignored) {
                // A disconnected client must not stop the simulation runner.
            }
        }
    }

    @Override
    public VehicleTraceSummary getVehicleTraceSummary() {
        return simulator.getVehicleTraceSummary();
    }

    @Override
    public boolean hasVehicleTraceAtTick(long tick) {
        return simulator.hasVehicleTraceAtTick(tick);
    }

    @Override
    public List<VehicleTracePoint> getVehicleStatesAtTick(long tick) {
        return simulator.getVehicleStatesAtTick(tick);
    }

    @Override
    public Optional<VehicleTracePoint> getVehicleStateAtTick(String vehicleId, long tick) {
        return simulator.getVehicleStateAtTick(vehicleId, tick);
    }

    @Override
    public List<VehicleTracePoint> getVehicleTrace(String vehicleId, long fromTick, long toTick) {
        return simulator.getVehicleTrace(vehicleId, fromTick, toTick);
    }

    @Override
    public List<RoadFlowSample> getRoadFlowSamples() { return simulator.getRoadFlowSamples(); }

    @Override
    public List<FundamentalDiagramPoint> getFundamentalDiagram() { return simulator.getFundamentalDiagram(); }

    @Override
    public TimeSpaceDiagram getTimeSpaceDiagram(String roadId) { return simulator.getTimeSpaceDiagram(roadId); }

    @Override
    public void close() {
        cancelScheduledTicks();
        runner.shutdownNow();
        snapshotListeners.clear();
    }
}
