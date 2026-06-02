package team.traffic.domain.network;

public class Road {
    private String id;
    private String startIntersectionId;
    private String endIntersectionId;
    private double distance;
    private double speedLimit;
    private double travelCost;
    
    // New fields for capacity and congestion mechanics
    private int capacity;
    private int currentVehicleCount;
    private RoadCondition condition;

    public Road(String id, String startIntersectionId, String endIntersectionId, double distance, double speedLimit, int capacity) {
        this.id = id;
        this.startIntersectionId = startIntersectionId;
        this.endIntersectionId = endIntersectionId;
        this.distance = distance;
        this.speedLimit = speedLimit;
        this.capacity = capacity;
        this.currentVehicleCount = 0;
        this.condition = new RoadCondition();
        this.travelCost = distance / speedLimit;
    }

    public String getId() { return id; }
    public String getStartIntersectionId() { return startIntersectionId; }
    public String getEndIntersectionId() { return endIntersectionId; }
    public double getDistance() { return distance; }
    public double getSpeedLimit() { return speedLimit; }
    public double getTravelCost() { return travelCost; }
    public void setTravelCost(double travelCost) { this.travelCost = travelCost; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public int getCurrentVehicleCount() { return currentVehicleCount; }
    public void setCurrentVehicleCount(int currentVehicleCount) { this.currentVehicleCount = currentVehicleCount; }

    public RoadCondition getCondition() { return condition; }
    
    public boolean isAvailable() {
        return !RoadStatus.CLOSED.equals(condition.getStatus()) && !RoadStatus.BLOCKED.equals(condition.getStatus());
    }
}
