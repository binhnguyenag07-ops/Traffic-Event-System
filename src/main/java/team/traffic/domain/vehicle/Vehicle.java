package team.traffic.domain.vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.domain.routing.RouteStep;
import team.traffic.service.RoutingService;

public abstract class Vehicle {
    private final String id;
    private String currentIntersectionId;
    private final String destinationIntersectionId;
    private Route currentRoute;
    private int nextRouteStepIndex;
    private RoutePreference routePreference;
    private final double length;
    private final double width;
    private final double maxSpeed;
    private double currentSpeed;
    private final double acceleration;
    private final double deceleration;
    private final double vehicleWeight;
    private final int priorityLevel;
    private VehicleStatus status;
    private String occupiedRoadId;
    private String roadStartIntersectionId;
    private String roadEndIntersectionId;
    private double roadProgress;
    private final List<String> travelHistory;

    protected Vehicle(String id, String currentIntersectionId, String destinationIntersectionId, double movementSpeed) {
        this(
                id,
                currentIntersectionId,
                destinationIntersectionId,
                4.5,
                1.8,
                movementSpeed,
                movementSpeed,
                1.0,
                1.0,
                1_000.0,
                0);
    }

    protected Vehicle(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId,
            double length,
            double width,
            double maxSpeed,
            double currentSpeed,
            double acceleration,
            double deceleration,
            double vehicleWeight,
            int priorityLevel) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Vehicle id must not be blank.");
        }
        if (currentIntersectionId == null || currentIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Current intersection id must not be blank.");
        }
        if (destinationIntersectionId == null || destinationIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Destination intersection id must not be blank.");
        }
        validatePositive("Length", length);
        validatePositive("Width", width);
        validatePositive("Maximum speed", maxSpeed);
        validateCurrentSpeed(currentSpeed, maxSpeed);
        validatePositive("Acceleration", acceleration);
        validatePositive("Deceleration", deceleration);
        validatePositive("Vehicle weight", vehicleWeight);
        if (priorityLevel < 0) {
            throw new IllegalArgumentException("Priority level must not be negative.");
        }

        this.id = id;
        this.currentIntersectionId = currentIntersectionId;
        this.destinationIntersectionId = destinationIntersectionId;
        this.length = length;
        this.width = width;
        this.maxSpeed = maxSpeed;
        this.currentSpeed = currentSpeed;
        this.acceleration = acceleration;
        this.deceleration = deceleration;
        this.vehicleWeight = vehicleWeight;
        this.priorityLevel = priorityLevel;
        this.routePreference = RoutePreference.CONGESTION_AWARE;
        this.status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.WAITING;
        this.travelHistory = new ArrayList<>();
        this.travelHistory.add(currentIntersectionId);
    }

    public abstract VehicleType getType();

    public void requestRoute(RoutingService routingService) {
        Objects.requireNonNull(routingService, "Routing service must not be null.");
        this.currentRoute = null;
        this.nextRouteStepIndex = 0;
        this.status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.WAITING;
    }

    public void requestRoute(RoadNetwork roadNetwork, RoutingService routingService) {
        requestRoute(roadNetwork, routingService, routePreference);
    }

    public void requestRoute(
            RoadNetwork roadNetwork,
            RoutingService routingService,
            RoutePreference preference) {
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        Objects.requireNonNull(routingService, "Routing service must not be null.");
        Objects.requireNonNull(preference, "Route preference must not be null.");

        routePreference = preference;
        nextRouteStepIndex = 0;

        if (hasReachedDestination()) {
            currentRoute = null;
            status = VehicleStatus.ARRIVED;
            return;
        }

        RouteRequest request = new RouteRequest(
                currentIntersectionId,
                destinationIntersectionId,
                preference);

        try {
            RouteResult result = routingService.findRoute(roadNetwork, request);
            currentRoute = result == null ? null : result.getRoute();
            status = isUsableRoute(currentRoute) ? VehicleStatus.WAITING : VehicleStatus.BLOCKED;
        } catch (NoSuchElementException exception) {
            currentRoute = null;
            status = VehicleStatus.BLOCKED;
        }
    }

    public void moveOneStep() {
        moveOneStep(null);
    }

    public void moveOneStep(RoadNetwork roadNetwork) {
        if (hasReachedDestination()) {
            releaseOccupiedRoad(roadNetwork);
            this.status = VehicleStatus.ARRIVED;
            return;
        }

        RouteStep nextStep = getNextRouteStep();
        if (nextStep == null || !nextStep.getFromIntersectionId().equals(currentIntersectionId)) {
            releaseOccupiedRoad(roadNetwork);
            status = VehicleStatus.BLOCKED;
            return;
        }

        if (roadNetwork != null && !isStepAvailable(roadNetwork, nextStep)) {
            releaseOccupiedRoad(roadNetwork);
            status = VehicleStatus.BLOCKED;
            return;
        }

        enterNextRoad(roadNetwork, nextStep);
        nextRouteStepIndex++;
        moveTo(nextStep.getToIntersectionId());

        if (hasReachedDestination()) {
            releaseOccupiedRoad(roadNetwork);
            status = VehicleStatus.ARRIVED;
        } else if (nextRouteStepIndex >= currentRoute.getStepCount()) {
            releaseOccupiedRoad(roadNetwork);
            status = VehicleStatus.BLOCKED;
        }
    }

    /**
     * Advances this vehicle along its current road using simulation time.
     * Unlike {@link #moveOneStep(RoadNetwork)}, the vehicle remains on a road
     * until the travelled distance reaches that road's length.
     */
    public void advance(RoadNetwork roadNetwork, double simulationSeconds) {
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        if (!Double.isFinite(simulationSeconds) || simulationSeconds <= 0) {
            throw new IllegalArgumentException("Simulation seconds must be positive.");
        }
        if (hasReachedDestination()) {
            releaseOccupiedRoad(roadNetwork);
            status = VehicleStatus.ARRIVED;
            return;
        }

        RouteStep step = getNextRouteStep();
        if (step == null || !step.getFromIntersectionId().equals(currentIntersectionId)) {
            releaseOccupiedRoad(roadNetwork);
            status = VehicleStatus.BLOCKED;
            return;
        }

        Road road = roadNetwork.getRoad(step.getRoadId());
        if (road == null) {
            releaseOccupiedRoad(roadNetwork);
            status = VehicleStatus.BLOCKED;
            return;
        }

        if (occupiedRoadId == null) {
            if (!isStepAvailable(roadNetwork, step)) {
                status = VehicleStatus.BLOCKED;
                return;
            }
            enterNextRoad(roadNetwork, step);
            roadStartIntersectionId = step.getFromIntersectionId();
            roadEndIntersectionId = step.getToIntersectionId();
            roadProgress = 0.0;
        }

        double trafficFactor = 1.0
                + road.getCondition().getCongestionLevel()
                + road.getCondition().getVehicleDensity();
        double effectiveSpeedKph = Math.min(currentSpeed, road.getSpeedLimit()) / trafficFactor;
        double distanceThisTick = effectiveSpeedKph * simulationSeconds / 3600.0;
        roadProgress = Math.min(1.0, roadProgress + distanceThisTick / road.getDistance());
        status = VehicleStatus.MOVING;

        if (roadProgress >= 1.0) {
            nextRouteStepIndex++;
            moveTo(step.getToIntersectionId());
            releaseOccupiedRoad(roadNetwork);
            if (hasReachedDestination()) {
                status = VehicleStatus.ARRIVED;
            } else if (nextRouteStepIndex >= currentRoute.getStepCount()) {
                status = VehicleStatus.BLOCKED;
            }
        }
    }

    public void recomputeRoute(RoutingService routingService) {
        requestRoute(routingService);
    }

    public void recomputeRoute(RoadNetwork roadNetwork, RoutingService routingService) {
        requestRoute(roadNetwork, routingService, routePreference);
    }

    public void recomputeRoute(
            RoadNetwork roadNetwork,
            RoutingService routingService,
            RoutePreference preference) {
        requestRoute(roadNetwork, routingService, preference);
    }

    public void moveTo(String nextIntersectionId) {
        if (nextIntersectionId == null || nextIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Next intersection id must not be blank.");
        }

        this.currentIntersectionId = nextIntersectionId;
        this.travelHistory.add(nextIntersectionId);
        this.status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.MOVING;
    }

    public boolean hasReachedDestination() {
        return destinationIntersectionId.equals(currentIntersectionId);
    }

    public String getId() {
        return id;
    }

    public String getCurrentIntersectionId() {
        return currentIntersectionId;
    }

    public String getDestinationIntersectionId() {
        return destinationIntersectionId;
    }

    public Route getCurrentRoute() {
        return currentRoute;
    }

    public void setCurrentRoute(Route currentRoute) {
        this.currentRoute = currentRoute;
        this.nextRouteStepIndex = 0;
        if (hasReachedDestination()) {
            this.status = VehicleStatus.ARRIVED;
        } else {
            this.status = isUsableRoute(currentRoute) ? VehicleStatus.WAITING : VehicleStatus.BLOCKED;
        }
    }

    public int getNextRouteStepIndex() {
        return nextRouteStepIndex;
    }

    public RoutePreference getRoutePreference() {
        return routePreference;
    }

    public String getOccupiedRoadId() {
        return occupiedRoadId;
    }

    public void releaseRoad(RoadNetwork roadNetwork) {
        releaseOccupiedRoad(roadNetwork);
    }

    public String getRoadStartIntersectionId() {
        return roadStartIntersectionId;
    }

    public String getRoadEndIntersectionId() {
        return roadEndIntersectionId;
    }

    public double getRoadProgress() {
        return roadProgress;
    }

    /**
     * Returns the legacy simulation-speed value. New code should use
     * {@link #getCurrentSpeed()}.
     */
    public double getMovementSpeed() {
        return currentSpeed;
    }

    /**
     * Updates the legacy simulation-speed value. New code should use
     * {@link #setCurrentSpeed(double)}.
     */
    public void setMovementSpeed(double movementSpeed) {
        if (!Double.isFinite(movementSpeed) || movementSpeed <= 0) {
            throw new IllegalArgumentException("Movement speed must be positive.");
        }
        setCurrentSpeed(movementSpeed);
    }

    /**
     * Vehicle length in metres.
     */
    public double getLength() {
        return length;
    }

    /**
     * Vehicle width in metres.
     */
    public double getWidth() {
        return width;
    }

    /**
     * Maximum road speed in kilometres per hour.
     */
    public double getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * Current road speed in kilometres per hour.
     */
    public double getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(double currentSpeed) {
        validateCurrentSpeed(currentSpeed, maxSpeed);
        this.currentSpeed = currentSpeed;
    }

    /**
     * Acceleration in metres per second squared.
     */
    public double getAcceleration() {
        return acceleration;
    }

    /**
     * Braking deceleration in metres per second squared.
     */
    public double getDeceleration() {
        return deceleration;
    }

    /**
     * Vehicle mass in kilograms.
     */
    public double getVehicleWeight() {
        return vehicleWeight;
    }

    /**
     * Relative dispatch priority. Standard vehicles use {@code 0}.
     */
    public int getPriorityLevel() {
        return priorityLevel;
    }

    /**
     * Advances the current speed for a duration, capped at {@link #getMaxSpeed()}.
     * This operation is intentionally independent of the graph-based tick movement.
     */
    public void accelerate(double durationSeconds) {
        setCurrentSpeed(Math.min(maxSpeed, currentSpeed + acceleration * validateDuration(durationSeconds) * 3.6));
    }

    /**
     * Reduces the current speed for a duration, floored at zero.
     * This operation is intentionally independent of the graph-based tick movement.
     */
    public void decelerate(double durationSeconds) {
        setCurrentSpeed(Math.max(0.0, currentSpeed - deceleration * validateDuration(durationSeconds) * 3.6));
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = status;
    }

    public List<String> getTravelHistory() {
        return Collections.unmodifiableList(travelHistory);
    }

    private static void validatePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
    }

    private static void validateCurrentSpeed(double currentSpeed, double maxSpeed) {
        if (!Double.isFinite(currentSpeed) || currentSpeed < 0 || currentSpeed > maxSpeed) {
            throw new IllegalArgumentException("Current speed must be between zero and the maximum speed.");
        }
    }

    private static double validateDuration(double durationSeconds) {
        if (!Double.isFinite(durationSeconds) || durationSeconds < 0) {
            throw new IllegalArgumentException("Duration must not be negative.");
        }
        return durationSeconds;
    }

    private RouteStep getNextRouteStep() {
        if (currentRoute == null || nextRouteStepIndex >= currentRoute.getStepCount()) {
            return null;
        }
        return currentRoute.getSteps().get(nextRouteStepIndex);
    }

    private boolean isUsableRoute(Route route) {
        if (route == null || route.isEmpty()) {
            return false;
        }

        List<RouteStep> steps = route.getSteps();
        return steps.get(0).getFromIntersectionId().equals(currentIntersectionId)
                && steps.get(steps.size() - 1).getToIntersectionId().equals(destinationIntersectionId);
    }

    private boolean isStepAvailable(RoadNetwork roadNetwork, RouteStep step) {
        Road road = roadNetwork.getRoad(step.getRoadId());
        return road != null
                && road.getStartIntersectionId().equals(step.getFromIntersectionId())
                && road.getEndIntersectionId().equals(step.getToIntersectionId())
                && road.isAvailable();
    }

    private void enterNextRoad(RoadNetwork roadNetwork, RouteStep step) {
        if (roadNetwork == null) {
            return;
        }

        releaseOccupiedRoad(roadNetwork);
        roadNetwork.recordVehicleEnteringRoad(step.getRoadId());
        occupiedRoadId = step.getRoadId();
    }

    private void releaseOccupiedRoad(RoadNetwork roadNetwork) {
        if (roadNetwork == null || occupiedRoadId == null) {
            return;
        }

        Road occupiedRoad = roadNetwork.getRoad(occupiedRoadId);
        if (occupiedRoad != null && occupiedRoad.getCurrentVehicleCount() > 0) {
            roadNetwork.recordVehicleLeavingRoad(occupiedRoadId);
        }
        occupiedRoadId = null;
        roadStartIntersectionId = null;
        roadEndIntersectionId = null;
        roadProgress = 0.0;
    }
}
