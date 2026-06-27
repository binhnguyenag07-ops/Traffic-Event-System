package team.traffic.service;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.VehicleConfig;

import java.util.List;

public interface SimulationService {
    void initialize(List<VehicleConfig> vehicles, List<EventConfig> events);
    void start();
    void pause();
    void resume();
    void reset();
    void advanceTick();
    SimulationStatus getStatus();
    long getCurrentTick();
    List<Vehicle> getVehicles();
    List<TrafficEvent> getEvents();
}
