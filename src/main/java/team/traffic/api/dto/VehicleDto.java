package team.traffic.api.dto;

import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.vehicle.pose.VehiclePose;
import java.util.List;

public class VehicleDto {
    private String id;
    private VehicleType type;
    private String currentIntersectionId;
    private String destinationIntersectionId;
    private String originBuildingId;
    private String destinationBuildingId;
    private VehicleStatus status;
    private double length;
    private double width;
    private double maxSpeed;
    private double cruisingSpeedKph;
    private double acceleration;
    private double deceleration;
    private double vehicleWeight;
    private int priorityLevel;
    private RouteDto currentRoute;
    private int nextRouteStepIndex;
    private RoutePreference routePreference;
    private String occupiedRoadId;
    private String roadStartIntersectionId;
    private String roadEndIntersectionId;
    private double roadProgress;
    private double distanceTravelledKm;
    private double travelTimeSeconds;
    private long waitingTicks;
    private int routeRecalculationCount;
    private List<String> travelHistory;
    private double instantaneousSpeed;
    private Double latitude;
    private Double longitude;
    private Double headingDegrees;
    private boolean inJunction;
    private int laneIndex;
    private double lateralOffsetMetres;

    public VehicleDto() {}

    /**
     * Adds the vehicle's resolved map position and heading, so a client can
     * draw it to scale without reimplementing the road geometry itself.
     */
    public VehicleDto(Vehicle vehicle, VehiclePose pose) {
        this(vehicle);
        if (pose != null) {
            this.latitude = pose.latitude();
            this.longitude = pose.longitude();
            this.headingDegrees = pose.headingDegrees();
            this.inJunction = pose.inJunction();
            this.laneIndex = pose.laneIndex();
            this.lateralOffsetMetres = pose.lateralOffsetMetres();
        }
    }

    public VehicleDto(Vehicle vehicle) {
        this.id = vehicle.getId();
        this.type = vehicle.getType();
        this.currentIntersectionId = vehicle.getCurrentIntersectionId();
        this.destinationIntersectionId = vehicle.getDestinationIntersectionId();
        this.originBuildingId = vehicle.getOriginBuildingId();
        this.destinationBuildingId = vehicle.getDestinationBuildingId();
        this.status = vehicle.getStatus();
        this.length = vehicle.getLength();
        this.width = vehicle.getWidth();
        this.maxSpeed = vehicle.getMaxSpeed();
        this.cruisingSpeedKph = vehicle.getCruisingSpeedKph();
        this.acceleration = vehicle.getAcceleration();
        this.deceleration = vehicle.getDeceleration();
        this.vehicleWeight = vehicle.getVehicleWeight();
        this.priorityLevel = vehicle.getPriorityLevel();
        this.currentRoute = vehicle.getCurrentRoute() == null ? null : new RouteDto(vehicle.getCurrentRoute());
        this.nextRouteStepIndex = vehicle.getNextRouteStepIndex();
        this.routePreference = vehicle.getRoutePreference();
        this.occupiedRoadId = vehicle.getOccupiedRoadId();
        this.roadStartIntersectionId = vehicle.getRoadStartIntersectionId();
        this.roadEndIntersectionId = vehicle.getRoadEndIntersectionId();
        this.roadProgress = vehicle.getRoadProgress();
        this.distanceTravelledKm = vehicle.getDistanceTravelledKm();
        this.travelTimeSeconds = vehicle.getTravelTimeSeconds();
        this.waitingTicks = vehicle.getWaitingTicks();
        this.routeRecalculationCount = vehicle.getRouteRecalculationCount();
        this.travelHistory = vehicle.getTravelHistory();
        this.instantaneousSpeed = vehicle.getInstantaneousSpeedKph();
        this.laneIndex = vehicle.getLaneIndex();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public VehicleType getType() { return type; }
    public void setType(VehicleType type) { this.type = type; }
    public String getCurrentIntersectionId() { return currentIntersectionId; }
    public void setCurrentIntersectionId(String currentIntersectionId) { this.currentIntersectionId = currentIntersectionId; }
    public String getDestinationIntersectionId() { return destinationIntersectionId; }
    public void setDestinationIntersectionId(String destinationIntersectionId) { this.destinationIntersectionId = destinationIntersectionId; }
    public String getOriginBuildingId() { return originBuildingId; }
    public void setOriginBuildingId(String originBuildingId) { this.originBuildingId = originBuildingId; }
    public String getDestinationBuildingId() { return destinationBuildingId; }
    public void setDestinationBuildingId(String destinationBuildingId) { this.destinationBuildingId = destinationBuildingId; }
    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }
    public double getLength() { return length; }
    public void setLength(double length) { this.length = length; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(double maxSpeed) { this.maxSpeed = maxSpeed; }
    public double getCruisingSpeedKph() { return cruisingSpeedKph; }
    public void setCruisingSpeedKph(double cruisingSpeedKph) { this.cruisingSpeedKph = cruisingSpeedKph; }
    public double getAcceleration() { return acceleration; }
    public void setAcceleration(double acceleration) { this.acceleration = acceleration; }
    public double getDeceleration() { return deceleration; }
    public void setDeceleration(double deceleration) { this.deceleration = deceleration; }
    public double getVehicleWeight() { return vehicleWeight; }
    public void setVehicleWeight(double vehicleWeight) { this.vehicleWeight = vehicleWeight; }
    public int getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(int priorityLevel) { this.priorityLevel = priorityLevel; }
    public RouteDto getCurrentRoute() { return currentRoute; }
    public void setCurrentRoute(RouteDto currentRoute) { this.currentRoute = currentRoute; }
    public int getNextRouteStepIndex() { return nextRouteStepIndex; }
    public void setNextRouteStepIndex(int nextRouteStepIndex) { this.nextRouteStepIndex = nextRouteStepIndex; }
    public RoutePreference getRoutePreference() { return routePreference; }
    public void setRoutePreference(RoutePreference routePreference) { this.routePreference = routePreference; }
    public String getOccupiedRoadId() { return occupiedRoadId; }
    public void setOccupiedRoadId(String occupiedRoadId) { this.occupiedRoadId = occupiedRoadId; }
    public String getRoadStartIntersectionId() { return roadStartIntersectionId; }
    public void setRoadStartIntersectionId(String roadStartIntersectionId) { this.roadStartIntersectionId = roadStartIntersectionId; }
    public String getRoadEndIntersectionId() { return roadEndIntersectionId; }
    public void setRoadEndIntersectionId(String roadEndIntersectionId) { this.roadEndIntersectionId = roadEndIntersectionId; }
    public double getRoadProgress() { return roadProgress; }
    public void setRoadProgress(double roadProgress) { this.roadProgress = roadProgress; }
    public double getDistanceTravelledKm() { return distanceTravelledKm; }
    public void setDistanceTravelledKm(double distanceTravelledKm) { this.distanceTravelledKm = distanceTravelledKm; }
    public double getTravelTimeSeconds() { return travelTimeSeconds; }
    public void setTravelTimeSeconds(double travelTimeSeconds) { this.travelTimeSeconds = travelTimeSeconds; }
    public long getWaitingTicks() { return waitingTicks; }
    public void setWaitingTicks(long waitingTicks) { this.waitingTicks = waitingTicks; }
    public int getRouteRecalculationCount() { return routeRecalculationCount; }
    public void setRouteRecalculationCount(int routeRecalculationCount) { this.routeRecalculationCount = routeRecalculationCount; }
    public List<String> getTravelHistory() { return travelHistory; }
    public void setTravelHistory(List<String> travelHistory) { this.travelHistory = travelHistory; }
    public double getInstantaneousSpeed() { return instantaneousSpeed; }
    public void setInstantaneousSpeed(double instantaneousSpeed) { this.instantaneousSpeed = instantaneousSpeed; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getHeadingDegrees() { return headingDegrees; }
    public void setHeadingDegrees(Double headingDegrees) { this.headingDegrees = headingDegrees; }
    public boolean isInJunction() { return inJunction; }
    public void setInJunction(boolean inJunction) { this.inJunction = inJunction; }
    public int getLaneIndex() { return laneIndex; }
    public void setLaneIndex(int laneIndex) { this.laneIndex = laneIndex; }
    public double getLateralOffsetMetres() { return lateralOffsetMetres; }
    public void setLateralOffsetMetres(double lateralOffsetMetres) { this.lateralOffsetMetres = lateralOffsetMetres; }
}
