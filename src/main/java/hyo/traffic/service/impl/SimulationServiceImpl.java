package hyo.traffic.service.impl;

import hyo.traffic.simulation.SimulationSnapshot;
import hyo.traffic.simulation.SimulationStatus;
import hyo.traffic.simulation.TrafficSimulator;
import hyo.traffic.service.SimulationService;

public class SimulationServiceImpl implements SimulationService {

    private final TrafficSimulator simulator;

    public SimulationServiceImpl(TrafficSimulator simulator) {
        this.simulator = simulator;
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void pause() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void resume() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void advanceOneTick() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void setSimulationSpeed(double speedMultiplier) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public SimulationStatus getStatus() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public SimulationSnapshot getSnapshot() {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
