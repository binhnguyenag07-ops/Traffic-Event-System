package team.traffic.api.dto;

import team.traffic.domain.network.Road;

/**
 * Dynamic road state carried inside a simulation snapshot so the frontend can
 * redraw the map after each tick without a second round-trip. Field names match
 * the {@code /api/network/roads} payload to keep one road contract everywhere.
 */
public class RoadStateDto {
    private String id;
    private String startIntersectionId;
    private String endIntersectionId;
    private double distance;
    private double speedLimit;
    private int capacity;
    private int currentVehicleCount;
    private String status;
    private double congestionLevel;

    public RoadStateDto() {}

    public RoadStateDto(Road road) {
        this.id = road.getId();
        this.startIntersectionId = road.getStartIntersectionId();
        this.endIntersectionId = road.getEndIntersectionId();
        this.distance = road.getDistance();
        this.speedLimit = road.getSpeedLimit();
        this.capacity = road.getCapacity();
        this.currentVehicleCount = road.getCurrentVehicleCount();
        this.status = road.getCondition().getRoadStatus().name();
        this.congestionLevel = road.getCondition().getCongestionLevel();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStartIntersectionId() { return startIntersectionId; }
    public void setStartIntersectionId(String startIntersectionId) { this.startIntersectionId = startIntersectionId; }
    public String getEndIntersectionId() { return endIntersectionId; }
    public void setEndIntersectionId(String endIntersectionId) { this.endIntersectionId = endIntersectionId; }
    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }
    public double getSpeedLimit() { return speedLimit; }
    public void setSpeedLimit(double speedLimit) { this.speedLimit = speedLimit; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public int getCurrentVehicleCount() { return currentVehicleCount; }
    public void setCurrentVehicleCount(int currentVehicleCount) { this.currentVehicleCount = currentVehicleCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getCongestionLevel() { return congestionLevel; }
    public void setCongestionLevel(double congestionLevel) { this.congestionLevel = congestionLevel; }
}
