package team.traffic.api.dto;

import java.util.List;
import java.util.stream.Collectors;
import team.traffic.domain.routing.Route;
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
    private String occupiedRoadId;
    private int nextRouteStepIndex;
    private List<String> travelHistory;
    private List<RouteResultDto.RouteStepDto> routeSteps;

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
        this.occupiedRoadId = vehicle.getOccupiedRoadId();
        this.nextRouteStepIndex = vehicle.getNextRouteStepIndex();
        this.travelHistory = vehicle.getTravelHistory();
        Route route = vehicle.getCurrentRoute();
        this.routeSteps = route == null ? List.of() : route.getSteps().stream()
                .map(step -> new RouteResultDto.RouteStepDto(
                        step.getFromIntersectionId(),
                        step.getToIntersectionId(),
                        step.getRoadId(),
                        step.getCost()))
                .collect(Collectors.toList());
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
    public String getOccupiedRoadId() { return occupiedRoadId; }
    public void setOccupiedRoadId(String occupiedRoadId) { this.occupiedRoadId = occupiedRoadId; }
    public int getNextRouteStepIndex() { return nextRouteStepIndex; }
    public void setNextRouteStepIndex(int nextRouteStepIndex) { this.nextRouteStepIndex = nextRouteStepIndex; }
    public List<String> getTravelHistory() { return travelHistory; }
    public void setTravelHistory(List<String> travelHistory) { this.travelHistory = travelHistory; }
    public List<RouteResultDto.RouteStepDto> getRouteSteps() { return routeSteps; }
    public void setRouteSteps(List<RouteResultDto.RouteStepDto> routeSteps) { this.routeSteps = routeSteps; }
}
