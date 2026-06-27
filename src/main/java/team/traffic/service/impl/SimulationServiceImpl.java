package team.traffic.service.impl;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.service.SimulationService;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.TrafficSimulator;
import team.traffic.simulation.VehicleConfig;

import java.util.List;

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
    public SimulationStatus getStatus() {
        return simulator.getStatus();
    }

    @Override
    public long getCurrentTick() {
        return simulator.getCurrentTick();
    }

    @Override
    public List<Vehicle> getVehicles() {
        return simulator.getVehicles();
    }

    @Override
    public List<TrafficEvent> getEvents() {
        return simulator.getEvents();
    }
}
