package team.traffic.api.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.pose.VehiclePoseResolver;

public class SimulationSnapshotDto {
    private String timelineId;
    private Long frameId;
    private long tick;
    private double elapsedSeconds;
    private SimulationStatus status;
    private List<VehicleDto> vehicles;
    private List<TrafficEventDto> trafficEvents;
    private List<RoadStateDto> roadStates;
    private List<TrafficSignalDto> trafficSignals;

    public SimulationSnapshotDto() {}

    public SimulationSnapshotDto(SimulationSnapshot snapshot) {
        this(snapshot, null);
    }

    public SimulationSnapshotDto(SimulationSnapshot snapshot, RoadNetwork network) {
        this.tick = snapshot.tick();
        this.elapsedSeconds = snapshot.elapsedSeconds();
        this.status = snapshot.status();
        // With a network to hand, each vehicle also carries its resolved map
        // position and heading; without one it carries progress along a road
        // only, and the client is left to place it.
        VehiclePoseResolver poseResolver = new VehiclePoseResolver();
        var poses = network == null
                ? Map.<String, team.traffic.domain.vehicle.pose.VehiclePose>of()
                : poseResolver.resolveAll(snapshot.vehicles(), network);
        this.vehicles = snapshot.vehicles().stream()
                .map(vehicle -> network == null
                        ? new VehicleDto(vehicle)
                        : new VehicleDto(vehicle, poses.get(vehicle.getId())))
                .collect(Collectors.toList());
        this.trafficEvents = snapshot.trafficEvents().stream()
                .map(TrafficEventDto::new)
                .collect(Collectors.toList());
        this.trafficSignals = snapshot.trafficSignals().stream()
                .map(TrafficSignalDto::new)
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

    public String getTimelineId() { return timelineId; }
    public void setTimelineId(String timelineId) { this.timelineId = timelineId; }
    public Long getFrameId() { return frameId; }
    public void setFrameId(Long frameId) { this.frameId = frameId; }

    public double getElapsedSeconds() { return elapsedSeconds; }
    public void setElapsedSeconds(double elapsedSeconds) { this.elapsedSeconds = elapsedSeconds; }

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
    public List<TrafficSignalDto> getTrafficSignals() { return trafficSignals; }
    public void setTrafficSignals(List<TrafficSignalDto> trafficSignals) { this.trafficSignals = trafficSignals; }
}
