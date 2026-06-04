package team.traffic.service.impl;

import team.traffic.service.SimulationService;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.TrafficSimulator;

public class SimulationServiceImpl implements SimulationService {
    private final TrafficSimulator simulator;

    public SimulationServiceImpl(TrafficSimulator simulator) {
        this.simulator = simulator;
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
    public void advanceOneTick() {
        simulator.advanceOneTick();
    }

    @Override
    public void setSimulationSpeed(double speedMultiplier) {
        // TODO: map multiplier to SimulationClock tick duration.
        throw new UnsupportedOperationException("TODO: implement simulation speed control.");
    }

    @Override
    public SimulationStatus getStatus() {
        return simulator.getStatus();
    }

    @Override
    public SimulationSnapshot getSnapshot() {
        return simulator.createSnapshot();
    }
}
