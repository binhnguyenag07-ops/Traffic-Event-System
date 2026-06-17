package team.traffic.domain.network;

public class Road {
    private final String id;
    private final String startIntersectionId;
    private final String endIntersectionId;
    private final double distance;
    private final double speedLimit;
    private final int capacity;
    private int currentVehicleCount;
    private RoadCondition condition;

    public Road(String id, String startIntersectionId, String endIntersectionId,
            double distance, double speedLimit, int capacity,
            int currentVehicleCount, RoadCondition condition) {
        // Basic validation keeps impossible roads out of the network.
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Road id is required");
        }
        if (startIntersectionId == null || startIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Start intersection id is required");
        }
        if (endIntersectionId == null || endIntersectionId.isBlank()) {
            throw new IllegalArgumentException("End intersection id is required");
        }
        if (distance <= 0) {
            throw new IllegalArgumentException("Distance must be greater than 0");
        }
        if (speedLimit <= 0) {
            throw new IllegalArgumentException("Speed limit must be greater than 0");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        if (currentVehicleCount < 0 || currentVehicleCount > capacity) {
            throw new IllegalArgumentException("Current vehicle count must be between 0 and capacity");
        }
        this.id = id;
        this.startIntersectionId = startIntersectionId;
        this.endIntersectionId = endIntersectionId;
        this.distance = distance;
        this.speedLimit = speedLimit;
        this.capacity = capacity;
        this.currentVehicleCount = currentVehicleCount;
        this.condition = condition == null ? new RoadCondition(0, 0, RoadStatus.OPEN) : condition;
    }
    
    // Estimates routing cost from free-flow travel time and current road condition.
    public double calculateTravelCost() {
        if (!isAvailable()) {
            return Double.POSITIVE_INFINITY;
        }

        return getFreeFlowTravelTime() * condition.getTravelCostMultiplier() * getCapacityLoadMultiplier();
    }

    public double getFreeFlowTravelTime() {
        return distance / speedLimit;
    }

    public double getCapacityLoadMultiplier() {
        return 1 + getCapacityUsage();
    }

    public double getCapacityUsage() {
        return (double) currentVehicleCount / capacity;
    }

    // A road can be used only when it is open enough and still has capacity.
    public boolean isAvailable() {
        return condition.isRoutable() && hasRemainingCapacity();
    }

    public boolean hasRemainingCapacity() {
        return currentVehicleCount < capacity;
    }

    public String getId() {
        return id;
    }

    public String getStartIntersectionId() {
        return startIntersectionId;
    }

    public String getEndIntersectionId() {
        return endIntersectionId;
    }

    public double getDistance() {
        return distance;
    }

    public double getSpeedLimit() {
        return speedLimit;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getCurrentVehicleCount() {
        return currentVehicleCount;
    }

    public void setCurrentVehicleCount(int currentVehicleCount) {
        if (currentVehicleCount < 0 || currentVehicleCount > capacity) {
            throw new IllegalArgumentException("Current vehicle count must be between 0 and capacity");
        }
        this.currentVehicleCount = currentVehicleCount;
    }

    public RoadCondition getCondition() {
        return condition;
    }

    public RoadCondition getRoadCondition() {
        return condition;
    }

    public void setCondition(RoadCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("Road condition is required");
        }
        this.condition = condition;
    }

    public void setRoadCondition(RoadCondition condition) {
        setCondition(condition);
    }
}
