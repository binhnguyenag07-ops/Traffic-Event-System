package team.traffic.api.dto;

import java.util.List;
import java.util.stream.Collectors;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.domain.network.RoadNetwork;

public class SimulationSnapshotDto {
    private long tick;
    private SimulationStatus status;
    private List<VehicleDto> vehicles;
    private List<TrafficEventDto> trafficEvents;
    private List<RoadStateDto> roadStates;

    public SimulationSnapshotDto() {}

    public SimulationSnapshotDto(SimulationSnapshot snapshot) {
        this(snapshot, null);
    }

    public SimulationSnapshotDto(SimulationSnapshot snapshot, RoadNetwork network) {
        this.tick = snapshot.tick();
        this.status = snapshot.status();
        this.vehicles = snapshot.vehicles().stream()
                .map(VehicleDto::new)
                .collect(Collectors.toList());
        this.trafficEvents = snapshot.trafficEvents().stream()
                .map(TrafficEventDto::new)
                .collect(Collectors.toList());
        this.roadStates = network == null ? List.of() : network.getRoads().values().stream()
                .map(RoadStateDto::new)
                .collect(Collectors.toList());
    }

    public long getTick() {
        return tick;
    }

    public void setTick(long tick) {
        this.tick = tick;
    }

    public SimulationStatus getStatus() {
        return status;
    }

    public void setStatus(SimulationStatus status) {
        this.status = status;
    }

    public List<VehicleDto> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<VehicleDto> vehicles) {
        this.vehicles = vehicles;
    }

    public List<TrafficEventDto> getTrafficEvents() {
        return trafficEvents;
    }

    public void setTrafficEvents(List<TrafficEventDto> trafficEvents) {
        this.trafficEvents = trafficEvents;
    }

    public List<RoadStateDto> getRoadStates() { return roadStates; }
    public void setRoadStates(List<RoadStateDto> roadStates) { this.roadStates = roadStates; }
}
