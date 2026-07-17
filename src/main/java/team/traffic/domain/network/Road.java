package team.traffic.domain.network;

import java.util.List;

public class Road {
    private final String id;
    private final String name;
    private final String physicalRoadId;
    private final String startIntersectionId;
    private final String endIntersectionId;
    private final double distance;
    private final double speedLimit;
    private final int capacity;
    private final int laneCount;
    private final List<GeoPoint> geometry;
    private int currentVehicleCount;
    private RoadCondition condition;

    public Road(String id, String startIntersectionId, String endIntersectionId,
            double distance, double speedLimit, int capacity,
            int currentVehicleCount, RoadCondition condition) {
        this(
                id,
                id,
                defaultPhysicalRoadId(startIntersectionId, endIntersectionId),
                startIntersectionId,
                endIntersectionId,
                distance,
                speedLimit,
                capacity,
                currentVehicleCount,
                1,
                List.of(),
                condition);
    }

    /**
     * Creates one directed travel lane group of a physical road.
     * Opposite directions share {@code physicalRoadId}; their geometries are
     * ordered from their own start intersection to end intersection.
     */
    public Road(String id, String name, String physicalRoadId,
            String startIntersectionId, String endIntersectionId,
            double distance, double speedLimit, int capacity,
            int currentVehicleCount, int laneCount, List<GeoPoint> geometry,
            RoadCondition condition) {
        // Basic validation keeps impossible roads out of the network.
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Road id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Road name is required");
        }
        if (physicalRoadId == null || physicalRoadId.isBlank()) {
            throw new IllegalArgumentException("Physical road id is required");
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
        if (laneCount <= 0) {
            throw new IllegalArgumentException("Lane count must be greater than 0");
        }
        if (geometry == null) {
            throw new IllegalArgumentException("Road geometry is required");
        }
        if (geometry.size() == 1 || geometry.stream().anyMatch(point -> point == null)) {
            throw new IllegalArgumentException("Road geometry must be empty or contain at least two valid points");
        }
        this.id = id;
        this.name = name;
        this.physicalRoadId = physicalRoadId;
        this.startIntersectionId = startIntersectionId;
        this.endIntersectionId = endIntersectionId;
        this.distance = distance;
        this.speedLimit = speedLimit;
        this.capacity = capacity;
        this.laneCount = laneCount;
        this.geometry = List.copyOf(geometry);
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

    /** Duration used by real-time visualization before applying a speed multiplier. */
    public double getFreeFlowTravelTimeSeconds() {
        return getFreeFlowTravelTimeHours() * 3600;
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

    public String getName() {
        return name;
    }

    public String getPhysicalRoadId() {
        return physicalRoadId;
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

    public int getLaneCount() {
        return laneCount;
    }

    public List<GeoPoint> getGeometry() {
        return geometry;
    }

    public boolean hasGeometry() {
        return !geometry.isEmpty();
    }

    public int getCurrentVehicleCount() {
        return currentVehicleCount;
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

    private static String defaultPhysicalRoadId(String firstIntersectionId, String secondIntersectionId) {
        if (firstIntersectionId == null || secondIntersectionId == null) {
            return "legacy-road";
        }
        return firstIntersectionId.compareTo(secondIntersectionId) <= 0
                ? firstIntersectionId + "<->" + secondIntersectionId
                : secondIntersectionId + "<->" + firstIntersectionId;
    }
}
