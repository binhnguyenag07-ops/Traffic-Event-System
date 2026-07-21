package team.traffic.api.dto;

import java.util.List;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.simulation.trace.VehicleTracePoint;

public class VehicleTracePointDto {
    private long tick;
    private String vehicleId;
    private VehicleType type;
    private VehicleStatus status;
    private String currentIntersectionId;
    private String destinationIntersectionId;
    private String currentRoadId;
    private String segmentStartIntersectionId;
    private String segmentEndIntersectionId;
    private double roadProgress;
    private double currentSpeed;
    private double instantaneousSpeed;
    private int nextRouteStepIndex;
    private List<String> routeRoadIds;

    public VehicleTracePointDto() {}

    public VehicleTracePointDto(VehicleTracePoint point) {
        this.tick = point.tick();
        this.vehicleId = point.vehicleId();
        this.type = point.type();
        this.status = point.status();
        this.currentIntersectionId = point.currentIntersectionId();
        this.destinationIntersectionId = point.destinationIntersectionId();
        this.currentRoadId = point.currentRoadId();
        this.segmentStartIntersectionId = point.segmentStartIntersectionId();
        this.segmentEndIntersectionId = point.segmentEndIntersectionId();
        this.roadProgress = point.roadProgress();
        this.currentSpeed = point.currentSpeed();
        this.instantaneousSpeed = point.instantaneousSpeed();
        this.nextRouteStepIndex = point.nextRouteStepIndex();
        this.routeRoadIds = point.routeRoadIds();
    }

    public long getTick() { return tick; }
    public void setTick(long tick) { this.tick = tick; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public VehicleType getType() { return type; }
    public void setType(VehicleType type) { this.type = type; }
    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }
    public String getCurrentIntersectionId() { return currentIntersectionId; }
    public void setCurrentIntersectionId(String currentIntersectionId) { this.currentIntersectionId = currentIntersectionId; }
    public String getDestinationIntersectionId() { return destinationIntersectionId; }
    public void setDestinationIntersectionId(String destinationIntersectionId) { this.destinationIntersectionId = destinationIntersectionId; }
    public String getCurrentRoadId() { return currentRoadId; }
    public void setCurrentRoadId(String currentRoadId) { this.currentRoadId = currentRoadId; }
    public String getSegmentStartIntersectionId() { return segmentStartIntersectionId; }
    public void setSegmentStartIntersectionId(String segmentStartIntersectionId) { this.segmentStartIntersectionId = segmentStartIntersectionId; }
    public String getSegmentEndIntersectionId() { return segmentEndIntersectionId; }
    public void setSegmentEndIntersectionId(String segmentEndIntersectionId) { this.segmentEndIntersectionId = segmentEndIntersectionId; }
    public double getRoadProgress() { return roadProgress; }
    public void setRoadProgress(double roadProgress) { this.roadProgress = roadProgress; }
    public double getCurrentSpeed() { return currentSpeed; }
    public void setCurrentSpeed(double currentSpeed) { this.currentSpeed = currentSpeed; }
    public double getInstantaneousSpeed() { return instantaneousSpeed; }
    public void setInstantaneousSpeed(double instantaneousSpeed) { this.instantaneousSpeed = instantaneousSpeed; }
    public int getNextRouteStepIndex() { return nextRouteStepIndex; }
    public void setNextRouteStepIndex(int nextRouteStepIndex) { this.nextRouteStepIndex = nextRouteStepIndex; }
    public List<String> getRouteRoadIds() { return routeRoadIds; }
    public void setRouteRoadIds(List<String> routeRoadIds) { this.routeRoadIds = routeRoadIds; }
}
