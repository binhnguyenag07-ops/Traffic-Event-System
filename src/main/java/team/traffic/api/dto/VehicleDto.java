package team.traffic.api.dto;

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

    public VehicleDto() {}

    public VehicleDto(Vehicle vehicle) {
        this.id = vehicle.getId();
        this.type = vehicle.getType();
        this.currentIntersectionId = vehicle.getCurrentIntersectionId();
        this.destinationIntersectionId = vehicle.getDestinationIntersectionId();
        this.status = vehicle.getStatus();
        this.movementSpeed = vehicle.getMovementSpeed();
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
}
