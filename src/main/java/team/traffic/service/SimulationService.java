package team.traffic.service;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.VehicleConfig;
import team.traffic.simulation.trace.VehicleTracePoint;
import team.traffic.simulation.trace.VehicleTraceSummary;

import java.util.List;
import java.util.Optional;

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
    VehicleTraceSummary getVehicleTraceSummary();
    boolean hasVehicleTraceAtTick(long tick);
    List<VehicleTracePoint> getVehicleStatesAtTick(long tick);
    Optional<VehicleTracePoint> getVehicleStateAtTick(String vehicleId, long tick);
    List<VehicleTracePoint> getVehicleTrace(String vehicleId, long fromTick, long toTick);
}
