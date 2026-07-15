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
    /** One simulation tick represents one minute of physical travel time. */
    public static final double DEFAULT_TICK_DURATION_SECONDS = 60.0;
    private static final double ROAD_COMPLETION_EPSILON = 1.0e-9;

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
    private String currentRoadId;
    private String segmentStartIntersectionId;
    private String segmentEndIntersectionId;
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

    /**
     * Clears the current route when the caller does not have a road network for
     * a new route calculation. Physical movement still requires a road network.
     */
    public void requestRoute(RoutingService routingService) {
        Objects.requireNonNull(routingService, "Routing service must not be null.");
        if (isTravelling()) {
            return;
        }
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
        String routeStartIntersectionId = getRouteStartIntersectionId();
        if (destinationIntersectionId.equals(routeStartIntersectionId)) {
            currentRoute = null;
            nextRouteStepIndex = 0;
            if (!isTravelling()) {
                status = VehicleStatus.ARRIVED;
            }
            return;
        }

        RouteRequest request = new RouteRequest(
                routeStartIntersectionId,
                destinationIntersectionId,
                preference);

        try {
            RouteResult result = routingService.findRoute(roadNetwork, request);
            currentRoute = result == null ? null : result.getRoute();
            nextRouteStepIndex = 0;
            if (!isTravelling()) {
                status = isUsableRoute(currentRoute, routeStartIntersectionId)
                        ? VehicleStatus.WAITING
                        : VehicleStatus.BLOCKED;
            }
        } catch (NoSuchElementException exception) {
            currentRoute = null;
            nextRouteStepIndex = 0;
            if (!isTravelling()) {
                status = VehicleStatus.BLOCKED;
            }
        }
    }

    /**
     * Advances vehicle movement by the supplied simulation duration.
     * Distance is measured in kilometres, speed in kilometres per hour, and
     * duration in seconds. A vehicle moves along at most one road per call.
     */
    public void advance(double durationSeconds, RoadNetwork roadNetwork) {
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        double duration = validateDuration(durationSeconds);
        if (duration == 0 || (hasReachedDestination() && !isTravelling())) {
            if (hasReachedDestination()) {
                status = VehicleStatus.ARRIVED;
            }
            return;
        }

        if (!isTravelling()) {
            beginNextRoad(roadNetwork);
        }

        if (isTravelling()) {
            advanceOnCurrentRoad(duration, roadNetwork);
        }
    }

    /**
     * Compatibility wrapper for the former one-step API. It now advances one
     * simulation tick of physical travel and never moves directly to a road endpoint.
     */
    @Deprecated
    public void moveOneStep(RoadNetwork roadNetwork) {
        advance(DEFAULT_TICK_DURATION_SECONDS, roadNetwork);
    }

    /**
     * Physical movement requires road distance and speed-limit data.
     */
    @Deprecated
    public void moveOneStep() {
        throw new IllegalStateException("Physical vehicle movement requires a road network.");
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

    /**
     * Administrative relocation used outside normal simulation movement.
     * A moving vehicle cannot be relocated because that would corrupt road density.
     */
    public void moveTo(String nextIntersectionId) {
        if (nextIntersectionId == null || nextIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Next intersection id must not be blank.");
        }
        if (isTravelling()) {
            throw new IllegalStateException("Cannot relocate a vehicle while it is travelling on a road.");
        }

        this.currentIntersectionId = nextIntersectionId;
        this.travelHistory.add(nextIntersectionId);
        this.status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.WAITING;
    }

    public boolean hasReachedDestination() {
        return destinationIntersectionId.equals(currentIntersectionId) && !isTravelling();
    }

    public boolean isTravelling() {
        return currentRoadId != null;
    }

    public String getId() {
        return id;
    }

    /**
     * The last fully reached intersection. While travelling, use segment and
     * progress fields to locate the vehicle between intersections.
     */
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
        } else if (!isTravelling()) {
            this.status = isUsableRoute(currentRoute, currentIntersectionId)
                    ? VehicleStatus.WAITING
                    : VehicleStatus.BLOCKED;
        }
    }

    public int getNextRouteStepIndex() {
        return nextRouteStepIndex;
    }

    public RoutePreference getRoutePreference() {
        return routePreference;
    }

    public String getCurrentRoadId() {
        return currentRoadId;
    }

    /**
     * Returns the road currently occupied by this vehicle. Use
     * {@link #getCurrentRoadId()} in new code.
     */
    public String getOccupiedRoadId() {
        return currentRoadId;
    }

    public String getSegmentStartIntersectionId() {
        return segmentStartIntersectionId;
    }

    public String getSegmentEndIntersectionId() {
        return segmentEndIntersectionId;
    }

    /**
     * Fraction of the current road already travelled, in the range {@code 0..1}.
     */
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

    /** Vehicle length in metres. */
    public double getLength() {
        return length;
    }

    /** Vehicle width in metres. */
    public double getWidth() {
        return width;
    }

    /** Maximum road speed in kilometres per hour. */
    public double getMaxSpeed() {
        return maxSpeed;
    }

    /** Current road speed in kilometres per hour. */
    public double getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(double currentSpeed) {
        validateCurrentSpeed(currentSpeed, maxSpeed);
        this.currentSpeed = currentSpeed;
    }

    /** Acceleration in metres per second squared. */
    public double getAcceleration() {
        return acceleration;
    }

    /** Braking deceleration in metres per second squared. */
    public double getDeceleration() {
        return deceleration;
    }

    /** Vehicle mass in kilograms. */
    public double getVehicleWeight() {
        return vehicleWeight;
    }

    /** Relative dispatch priority. Standard vehicles use {@code 0}. */
    public int getPriorityLevel() {
        return priorityLevel;
    }

    /** Advances the current speed for a duration, capped at {@link #getMaxSpeed()}. */
    public void accelerate(double durationSeconds) {
        setCurrentSpeed(Math.min(maxSpeed, currentSpeed + acceleration * validateDuration(durationSeconds) * 3.6));
    }

    /** Reduces the current speed for a duration, floored at zero. */
    public void decelerate(double durationSeconds) {
        setCurrentSpeed(Math.max(0.0, currentSpeed - deceleration * validateDuration(durationSeconds) * 3.6));
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = Objects.requireNonNull(status, "Vehicle status must not be null.");
    }

    public List<String> getTravelHistory() {
        return Collections.unmodifiableList(travelHistory);
    }

    private void beginNextRoad(RoadNetwork roadNetwork) {
        if (currentSpeed == 0) {
            status = VehicleStatus.WAITING;
            return;
        }

        RouteStep nextStep = getNextRouteStep();
        if (nextStep == null || !nextStep.getFromIntersectionId().equals(currentIntersectionId)) {
            status = VehicleStatus.BLOCKED;
            return;
        }
        if (!isStepAvailable(roadNetwork, nextStep)) {
            status = VehicleStatus.BLOCKED;
            return;
        }

        roadNetwork.recordVehicleEnteringRoad(nextStep.getRoadId());
        currentRoadId = nextStep.getRoadId();
        segmentStartIntersectionId = nextStep.getFromIntersectionId();
        segmentEndIntersectionId = nextStep.getToIntersectionId();
        roadProgress = 0.0;
        status = VehicleStatus.MOVING;
    }

    private void advanceOnCurrentRoad(double durationSeconds, RoadNetwork roadNetwork) {
        Road road = roadNetwork.getRoad(currentRoadId);
        if (road == null) {
            clearRoadPosition();
            status = VehicleStatus.BLOCKED;
            return;
        }

        double effectiveSpeed = Math.min(currentSpeed, road.getSpeedLimit());
        if (effectiveSpeed == 0) {
            return;
        }

        double travelledDistance = effectiveSpeed * durationSeconds / 3_600.0;
        roadProgress += travelledDistance / road.getDistance();
        if (roadProgress >= 1.0 - ROAD_COMPLETION_EPSILON) {
            roadProgress = 1.0;
            completeCurrentRoad(roadNetwork);
        }
    }

    private void completeCurrentRoad(RoadNetwork roadNetwork) {
        boolean completedCurrentRouteStep = currentRoadMatchesNextRouteStep();
        String arrivedIntersectionId = segmentEndIntersectionId;
        releaseCurrentRoad(roadNetwork);
        currentIntersectionId = arrivedIntersectionId;
        travelHistory.add(arrivedIntersectionId);
        if (completedCurrentRouteStep) {
            nextRouteStepIndex++;
        }

        if (hasReachedDestination()) {
            status = VehicleStatus.ARRIVED;
        } else if (getNextRouteStep() == null) {
            status = VehicleStatus.BLOCKED;
        } else {
            status = VehicleStatus.WAITING;
        }
    }

    private void releaseCurrentRoad(RoadNetwork roadNetwork) {
        if (currentRoadId != null) {
            Road occupiedRoad = roadNetwork.getRoad(currentRoadId);
            if (occupiedRoad != null && occupiedRoad.getCurrentVehicleCount() > 0) {
                roadNetwork.recordVehicleLeavingRoad(currentRoadId);
            }
        }
        clearRoadPosition();
    }

    private void clearRoadPosition() {
        currentRoadId = null;
        segmentStartIntersectionId = null;
        segmentEndIntersectionId = null;
        roadProgress = 0.0;
    }

    private String getRouteStartIntersectionId() {
        return isTravelling() ? segmentEndIntersectionId : currentIntersectionId;
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

    private boolean currentRoadMatchesNextRouteStep() {
        RouteStep nextStep = getNextRouteStep();
        return nextStep != null
                && nextStep.getRoadId().equals(currentRoadId)
                && nextStep.getFromIntersectionId().equals(segmentStartIntersectionId)
                && nextStep.getToIntersectionId().equals(segmentEndIntersectionId);
    }

    private boolean isUsableRoute(Route route, String routeStartIntersectionId) {
        if (route == null || route.isEmpty()) {
            return false;
        }

        List<RouteStep> steps = route.getSteps();
        return steps.get(0).getFromIntersectionId().equals(routeStartIntersectionId)
                && steps.get(steps.size() - 1).getToIntersectionId().equals(destinationIntersectionId);
    }

    private boolean isStepAvailable(RoadNetwork roadNetwork, RouteStep step) {
        Road road = roadNetwork.getRoad(step.getRoadId());
        return road != null
                && road.getStartIntersectionId().equals(step.getFromIntersectionId())
                && road.getEndIntersectionId().equals(step.getToIntersectionId())
                && road.isAvailable();
    }
}
