package team.traffic.api.dto;

import java.util.List;
import java.util.stream.Collectors;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;

public class VehicleDto {
    private String id;
    private VehicleType type;
    private String currentIntersectionId;
    private String destinationIntersectionId;
    private VehicleStatus status;
    private double movementSpeed;
    private double length;
    private double width;
    private double maxSpeed;
    private double currentSpeed;
    private double acceleration;
    private double deceleration;
    private double vehicleWeight;
    private int priorityLevel;
    private String currentRoadId;
    private String segmentStartIntersectionId;
    private String segmentEndIntersectionId;
    private double roadProgress;
    private List<RouteResultDto.RouteStepDto> currentRoute;
    private int nextRouteStepIndex;
    private List<String> travelHistory;

    public VehicleDto() {}

    public VehicleDto(Vehicle vehicle) {
        this.id = vehicle.getId();
        this.type = vehicle.getType();
        this.currentIntersectionId = vehicle.getCurrentIntersectionId();
        this.destinationIntersectionId = vehicle.getDestinationIntersectionId();
        this.status = vehicle.getStatus();
        this.movementSpeed = vehicle.getMovementSpeed();
        this.length = vehicle.getLength();
        this.width = vehicle.getWidth();
        this.maxSpeed = vehicle.getMaxSpeed();
        this.currentSpeed = vehicle.getCurrentSpeed();
        this.acceleration = vehicle.getAcceleration();
        this.deceleration = vehicle.getDeceleration();
        this.vehicleWeight = vehicle.getVehicleWeight();
        this.priorityLevel = vehicle.getPriorityLevel();
        this.currentRoadId = vehicle.getCurrentRoadId();
        this.segmentStartIntersectionId = vehicle.getSegmentStartIntersectionId();
        this.segmentEndIntersectionId = vehicle.getSegmentEndIntersectionId();
        this.roadProgress = vehicle.getRoadProgress();
        this.nextRouteStepIndex = vehicle.getNextRouteStepIndex();
        this.currentRoute = vehicle.getCurrentRoute() == null
                ? List.of()
                : vehicle.getCurrentRoute().getSteps().stream()
                        .map(step -> new RouteResultDto.RouteStepDto(
                                step.getFromIntersectionId(),
                                step.getToIntersectionId(),
                                step.getRoadId(),
                                step.getCost()))
                        .collect(Collectors.toList());
        this.travelHistory = List.copyOf(vehicle.getTravelHistory());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public VehicleType getType() { return type; }
    public void setType(VehicleType type) { this.type = type; }
    public String getCurrentIntersectionId() { return currentIntersectionId; }
    public void setCurrentIntersectionId(String currentIntersectionId) { this.currentIntersectionId = currentIntersectionId; }
    public String getDestinationIntersectionId() { return destinationIntersectionId; }
    public void setDestinationIntersectionId(String destinationIntersectionId) { this.destinationIntersectionId = destinationIntersectionId; }
    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }
    public double getMovementSpeed() { return movementSpeed; }
    public void setMovementSpeed(double movementSpeed) { this.movementSpeed = movementSpeed; }
    public double getLength() { return length; }
    public void setLength(double length) { this.length = length; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(double maxSpeed) { this.maxSpeed = maxSpeed; }
    public double getCurrentSpeed() { return currentSpeed; }
    public void setCurrentSpeed(double currentSpeed) { this.currentSpeed = currentSpeed; }
    public double getAcceleration() { return acceleration; }
    public void setAcceleration(double acceleration) { this.acceleration = acceleration; }
    public double getDeceleration() { return deceleration; }
    public void setDeceleration(double deceleration) { this.deceleration = deceleration; }
    public double getVehicleWeight() { return vehicleWeight; }
    public void setVehicleWeight(double vehicleWeight) { this.vehicleWeight = vehicleWeight; }
    public int getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(int priorityLevel) { this.priorityLevel = priorityLevel; }
    public String getCurrentRoadId() { return currentRoadId; }
    public void setCurrentRoadId(String currentRoadId) { this.currentRoadId = currentRoadId; }
    public String getSegmentStartIntersectionId() { return segmentStartIntersectionId; }
    public void setSegmentStartIntersectionId(String segmentStartIntersectionId) { this.segmentStartIntersectionId = segmentStartIntersectionId; }
    public String getSegmentEndIntersectionId() { return segmentEndIntersectionId; }
    public void setSegmentEndIntersectionId(String segmentEndIntersectionId) { this.segmentEndIntersectionId = segmentEndIntersectionId; }
    public double getRoadProgress() { return roadProgress; }
    public void setRoadProgress(double roadProgress) { this.roadProgress = roadProgress; }
    public List<RouteResultDto.RouteStepDto> getCurrentRoute() { return currentRoute; }
    public void setCurrentRoute(List<RouteResultDto.RouteStepDto> currentRoute) { this.currentRoute = currentRoute; }
    public int getNextRouteStepIndex() { return nextRouteStepIndex; }
    public void setNextRouteStepIndex(int nextRouteStepIndex) { this.nextRouteStepIndex = nextRouteStepIndex; }
    public List<String> getTravelHistory() { return travelHistory; }
    public void setTravelHistory(List<String> travelHistory) { this.travelHistory = travelHistory; }
}
