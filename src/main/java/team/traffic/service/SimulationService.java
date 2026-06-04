package team.traffic.service;

import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;

public interface SimulationService {
    void start();

    void pause();

    void resume();

    void reset();

    void advanceOneTick();

    void setSimulationSpeed(double speedMultiplier);

    SimulationStatus getStatus();

    SimulationSnapshot getSnapshot();
}
