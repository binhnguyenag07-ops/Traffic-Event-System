package team.traffic.api.dto;

import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.vehicle.pose.VehiclePose;
import java.util.List;
import java.util.Map;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.domain.vehicle.navigation.NavigationMode;
import team.traffic.domain.vehicle.navigation.NavigationDecisionRecord;

public class VehicleDto {
    private String id;
    private VehicleType type;
    private String currentIntersectionId;
    private String destinationIntersectionId;
    private String originBuildingId;
    private String destinationBuildingId;
    private String tripPlanId;
    private String tripPurpose;
    private int currentTripLegIndex = -1;
    private int tripLegCount;
    private String currentPlaceId;
    private Double dwellUntilSeconds;
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
    private double waitingSeconds;
    private Map<WaitCause, Double> waitingSecondsByCause;
    private WaitCause waitCause;
    private double waitCauseSeconds;
    private String blockingResource;
    private NavigationMode navigationMode;
    private long knowledgeVersion;
    private long routeVersion;
    private String lastRerouteReason;
    private Double lastRerouteAtSeconds;
    private double nextStrategicDecisionAtSeconds;
    private String nextPlannedMovement;
    private NavigationDecisionRecord lastNavigationDecision;
    private int legalUTurnsCompleted;
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
        if (vehicle.getTripPlan() != null) {
            this.tripPlanId = vehicle.getTripPlan().id();
            this.tripPurpose = vehicle.getTripPlan().purpose().value();
            this.currentTripLegIndex = vehicle.getCurrentTripLegIndex();
            this.tripLegCount = vehicle.getTripPlan().legs().size();
            this.currentPlaceId = vehicle.getCurrentResolvedPlace() == null
                    ? null : vehicle.getCurrentResolvedPlace().id();
            this.dwellUntilSeconds = Double.isFinite(vehicle.getDwellUntilSeconds())
                    ? vehicle.getDwellUntilSeconds() : null;
        }
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
        this.waitingSeconds = vehicle.getWaitingSeconds();
        this.waitingSecondsByCause = vehicle.getTrip().getWaitingSecondsByCause();
        this.waitCause = vehicle.getWaitState().cause();
        double observedAt = vehicle.getLocalPerception()
                .map(perception -> perception.observedAtSeconds())
                .orElse(vehicle.getWaitState().sinceSeconds());
        this.waitCauseSeconds = vehicle.getWaitState().isWaiting()
                ? Math.max(0.0, observedAt - vehicle.getWaitState().sinceSeconds())
                : 0.0;
        this.blockingResource = vehicle.getWaitState().resource()
                .map(resource -> resource.stableKey())
                .orElse(null);
        this.navigationMode = vehicle.getNavigationState().getMode();
        this.knowledgeVersion = vehicle.getDriverKnowledge().getVersion();
        this.routeVersion = vehicle.getRouteVersion();
        this.lastRerouteReason = vehicle.getNavigationState().getLastRerouteReason();
        this.lastRerouteAtSeconds = Double.isFinite(
                vehicle.getNavigationState().getLastRerouteAtSeconds())
                        ? vehicle.getNavigationState().getLastRerouteAtSeconds()
                        : null;
        this.nextStrategicDecisionAtSeconds =
                vehicle.getNavigationState().getNextStrategicDecisionAtSeconds();
        this.nextPlannedMovement = vehicle.getNextRoadId();
        this.lastNavigationDecision =
                vehicle.getNavigationState().getLastDecision();
        this.legalUTurnsCompleted = vehicle.getLegalUTurnsCompleted();
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
    public String getTripPlanId() { return tripPlanId; }
    public void setTripPlanId(String tripPlanId) { this.tripPlanId = tripPlanId; }
    public String getTripPurpose() { return tripPurpose; }
    public void setTripPurpose(String tripPurpose) { this.tripPurpose = tripPurpose; }
    public int getCurrentTripLegIndex() { return currentTripLegIndex; }
    public void setCurrentTripLegIndex(int currentTripLegIndex) { this.currentTripLegIndex = currentTripLegIndex; }
    public int getTripLegCount() { return tripLegCount; }
    public void setTripLegCount(int tripLegCount) { this.tripLegCount = tripLegCount; }
    public String getCurrentPlaceId() { return currentPlaceId; }
    public void setCurrentPlaceId(String currentPlaceId) { this.currentPlaceId = currentPlaceId; }
    public Double getDwellUntilSeconds() { return dwellUntilSeconds; }
    public void setDwellUntilSeconds(Double dwellUntilSeconds) { this.dwellUntilSeconds = dwellUntilSeconds; }
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
    public double getWaitingSeconds() { return waitingSeconds; }
    public void setWaitingSeconds(double waitingSeconds) { this.waitingSeconds = waitingSeconds; }
    public Map<WaitCause, Double> getWaitingSecondsByCause() { return waitingSecondsByCause; }
    public void setWaitingSecondsByCause(Map<WaitCause, Double> waitingSecondsByCause) {
        this.waitingSecondsByCause = waitingSecondsByCause;
    }
    public WaitCause getWaitCause() { return waitCause; }
    public void setWaitCause(WaitCause waitCause) { this.waitCause = waitCause; }
    public double getWaitCauseSeconds() { return waitCauseSeconds; }
    public void setWaitCauseSeconds(double waitCauseSeconds) { this.waitCauseSeconds = waitCauseSeconds; }
    public String getBlockingResource() { return blockingResource; }
    public void setBlockingResource(String blockingResource) { this.blockingResource = blockingResource; }
    public NavigationMode getNavigationMode() { return navigationMode; }
    public void setNavigationMode(NavigationMode navigationMode) { this.navigationMode = navigationMode; }
    public long getKnowledgeVersion() { return knowledgeVersion; }
    public void setKnowledgeVersion(long knowledgeVersion) { this.knowledgeVersion = knowledgeVersion; }
    public long getRouteVersion() { return routeVersion; }
    public void setRouteVersion(long routeVersion) { this.routeVersion = routeVersion; }
    public String getLastRerouteReason() { return lastRerouteReason; }
    public void setLastRerouteReason(String lastRerouteReason) { this.lastRerouteReason = lastRerouteReason; }
    public Double getLastRerouteAtSeconds() { return lastRerouteAtSeconds; }
    public void setLastRerouteAtSeconds(Double lastRerouteAtSeconds) { this.lastRerouteAtSeconds = lastRerouteAtSeconds; }
    public double getNextStrategicDecisionAtSeconds() { return nextStrategicDecisionAtSeconds; }
    public void setNextStrategicDecisionAtSeconds(double value) { this.nextStrategicDecisionAtSeconds = value; }
    public String getNextPlannedMovement() { return nextPlannedMovement; }
    public void setNextPlannedMovement(String nextPlannedMovement) { this.nextPlannedMovement = nextPlannedMovement; }
    public NavigationDecisionRecord getLastNavigationDecision() { return lastNavigationDecision; }
    public void setLastNavigationDecision(NavigationDecisionRecord value) { this.lastNavigationDecision = value; }
    public int getLegalUTurnsCompleted() { return legalUTurnsCompleted; }
    public void setLegalUTurnsCompleted(int value) { this.legalUTurnsCompleted = value; }
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
