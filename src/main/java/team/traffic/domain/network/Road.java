package team.traffic.domain.network;

import java.util.List;

public class Road {
    private final String id;
    private final String startIntersectionId;
    private final String endIntersectionId;
    private final double distance;
    private final double speedLimit;
    private final int capacity;
    private final List<GeoPoint> geometry;
    private final RoadClass roadClass;
    private int currentVehicleCount;
    private RoadCondition condition;

    public Road(String id, String startIntersectionId, String endIntersectionId,
            double distance, double speedLimit, int capacity,
            int currentVehicleCount, RoadCondition condition) {
        this(id, startIntersectionId, endIntersectionId, distance, speedLimit, capacity,
                currentVehicleCount, condition, List.of(), RoadClass.UNCLASSIFIED);
    }

    public Road(String id, String startIntersectionId, String endIntersectionId,
            double distance, double speedLimit, int capacity,
            int currentVehicleCount, RoadCondition condition, List<GeoPoint> geometry) {
        this(id, startIntersectionId, endIntersectionId, distance, speedLimit, capacity,
                currentVehicleCount, condition, geometry, RoadClass.UNCLASSIFIED);
    }

    public Road(String id, String startIntersectionId, String endIntersectionId,
            double distance, double speedLimit, int capacity,
            int currentVehicleCount, RoadCondition condition, List<GeoPoint> geometry,
            RoadClass roadClass) {
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
        if (!Double.isFinite(distance) || distance <= 0) {
            throw new IllegalArgumentException("Distance must be finite and greater than 0");
        }
        if (!Double.isFinite(speedLimit) || speedLimit <= 0) {
            throw new IllegalArgumentException("Speed limit must be finite and greater than 0");
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
        this.geometry = geometry == null ? List.of() : List.copyOf(geometry);
        this.roadClass = roadClass == null ? RoadClass.UNCLASSIFIED : roadClass;
        this.currentVehicleCount = currentVehicleCount;
        this.condition = condition == null ? new RoadCondition(0, 0, RoadStatus.OPEN) : condition;
    }
    
    /**
     * Calculates a congestion-aware routing cost in equivalent travel hours.
     *
     * <p>Distance is measured in kilometres and speed limit in kilometres per
     * hour. Congestion level and vehicle density are normalized values in the
     * range 0..1. Vehicle density is the traffic-load input to the cost formula,
     * so capacity usage is not multiplied into the cost a second time.</p>
     *
     * @return positive infinity when the road cannot accept a route; otherwise
     *         free-flow hours multiplied by the road-condition cost multiplier
     */
    public double calculateTravelCost() {
        if (!isAvailable()) {
            return Double.POSITIVE_INFINITY;
        }

        return getFreeFlowTravelTimeHours() * condition.getTravelCostMultiplier();
    }

    /**
     * Returns free-flow travel time in hours.
     *
     * @deprecated Prefer {@link #getFreeFlowTravelTimeHours()} so the unit is
     *             explicit at the call site.
     */
    @Deprecated
    public double getFreeFlowTravelTime() {
        return getFreeFlowTravelTimeHours();
    }

    // Distance is kilometres and speed is kilometres per hour.
    public double getFreeFlowTravelTimeHours() {
        return distance / speedLimit;
    }

    // Capacity usage is retained for availability checks and traffic statistics.
    public double getCapacityUsage() {
        return (double) currentVehicleCount / capacity;
    }

    public double refreshVehicleDensity() {
        double density = getCapacityUsage();
        condition.setVehicleDensity(density);
        return density;
    }

    public void enterVehicle() {
        if (!isAvailable()) {
            throw new IllegalStateException("Road is not available: " + id);
        }
        setCurrentVehicleCount(currentVehicleCount + 1);
    }

    public void leaveVehicle() {
        if (currentVehicleCount == 0) {
            throw new IllegalStateException("Cannot remove vehicle from empty road: " + id);
        }
        setCurrentVehicleCount(currentVehicleCount - 1);
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

    public List<GeoPoint> getGeometry() {
        return geometry;
    }

    public RoadClass getRoadClass() {
        return roadClass;
    }

    public void setCurrentVehicleCount(int currentVehicleCount) {
        if (currentVehicleCount < 0 || currentVehicleCount > capacity) {
            throw new IllegalArgumentException("Current vehicle count must be between 0 and capacity");
        }
        this.currentVehicleCount = currentVehicleCount;
        refreshVehicleDensity();
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
