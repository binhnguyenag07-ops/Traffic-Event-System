package team.traffic.service.impl;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.service.SimulationService;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.TrafficSimulator;
import team.traffic.simulation.VehicleConfig;
import team.traffic.simulation.trace.VehicleTracePoint;
import team.traffic.simulation.trace.VehicleTraceSummary;

import java.util.List;
import java.util.Optional;

public class SimulationServiceImpl implements SimulationService {
    private final TrafficSimulator simulator;

    public SimulationServiceImpl(TrafficSimulator simulator) {
        this.simulator = simulator;
    }

    @Override
    public void initialize(List<VehicleConfig> vehicles, List<EventConfig> events) {
        simulator.initialize(vehicles, events);
    }

    @Override
    public void start() {
        simulator.start();
    }

    @Override
    public void pause() {
        simulator.pause();
    }

    @Override
    public void resume() {
        simulator.resume();
    }

    @Override
    public void reset() {
        simulator.reset();
    }

    @Override
    public void advanceTick() {
        simulator.advanceTick();
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
}
