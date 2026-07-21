package team.traffic.domain.vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.dynamics.CarFollowingModel;
import team.traffic.domain.vehicle.dynamics.DriverProfile;
import team.traffic.domain.vehicle.dynamics.FollowContext;
import team.traffic.domain.vehicle.dynamics.JunctionApproach;
import team.traffic.domain.vehicle.dynamics.KraussCarFollowingModel;
import team.traffic.domain.vehicle.dynamics.LaneOccupancy;
import team.traffic.domain.vehicle.dynamics.LeaderGap;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.domain.vehicle.dynamics.TurnDirection;
import team.traffic.service.RoutingService;

public abstract class Vehicle {
    /** Conversion factor between kilometres per hour and metres per second. */
    private static final double KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND = 3.6;
    /** Below this speed a vehicle is reported as queueing rather than moving. */
    private static final double QUEUEING_THRESHOLD_MPS = 0.2;

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
    private String activeTrafficLightEventId;
    private long waitTicksRemaining;
    private final Set<String> completedTrafficLightEvents;
    private double distanceTravelledKm;
    private double travelTimeSeconds;
    private long waitingTicks;
    private int routeRecalculationCount;
    private String lastRoutingAlgorithm;
    private DriverProfile driverProfile;
    private CarFollowingModel carFollowingModel;
    private double instantaneousSpeedMps;
    private String plannedRoadId;
    private double plannedTravelMetres;
    private boolean plannedMayLeaveRoad;

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
        this.completedTrafficLightEvents = new HashSet<>();
        this.carFollowingModel = new KraussCarFollowingModel();
        this.instantaneousSpeedMps = currentSpeed / KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND;
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
            lastRoutingAlgorithm = result == null ? null : result.getAlgorithmName();
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
        double previousProgress = roadProgress;
        roadProgress = Math.min(1.0, roadProgress + distanceThisTick / road.getDistance());
        distanceTravelledKm += (roadProgress - previousProgress) * road.getDistance();
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

    /**
     * Advances this vehicle using the microscopic traffic model.
     *
     * <p>Where {@link #advance(RoadNetwork, double)} treats a road as a pipe
     * whose throughput is scaled by an aggregate congestion number, this
     * overload places the vehicle among the others physically on the road. It
     * keeps a safe distance from the vehicle in front, brakes for a junction it
     * has not been cleared to cross, and holds at the stop line when the road
     * ahead has no room left. Queues, spillback, and the throughput drop of a
     * saturated road therefore emerge from the interaction of vehicles instead
     * of being imposed by a formula.</p>
     *
     * <p>Junction clearance must already have been decided for this step, so
     * call {@link TrafficState#resolveJunctions()} first. {@link
     * team.traffic.simulation.VehicleDynamicsEngine} performs the whole
     * sequence in the right order.</p>
     */
    public void advance(RoadNetwork roadNetwork, TrafficState trafficState, double simulationSeconds) {
        planMovement(roadNetwork, trafficState, simulationSeconds);
        commitMovement(roadNetwork, trafficState);
    }

    /**
     * First half of a step: decides how fast to go and how far to travel,
     * without moving. Every vehicle plans against the same unmoved picture of
     * the road, so a vehicle's progress does not depend on whether its leader
     * happens to sit earlier or later in the vehicle list.
     *
     * <p>Entering a road is part of planning rather than moving, because the
     * junction has already ensured that at most one vehicle is cleared onto any
     * given road this step.</p>
     */
    public void planMovement(RoadNetwork roadNetwork, TrafficState trafficState, double simulationSeconds) {
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        Objects.requireNonNull(trafficState, "Traffic state must not be null.");
        if (!Double.isFinite(simulationSeconds) || simulationSeconds <= 0) {
            throw new IllegalArgumentException("Simulation seconds must be positive.");
        }

        plannedRoadId = null;

        if (hasReachedDestination()) {
            releaseOccupiedRoad(roadNetwork, trafficState);
            instantaneousSpeedMps = 0;
            status = VehicleStatus.ARRIVED;
            return;
        }

        RouteStep step = getNextRouteStep();
        if (step == null || !step.getFromIntersectionId().equals(currentIntersectionId)) {
            releaseOccupiedRoad(roadNetwork, trafficState);
            instantaneousSpeedMps = 0;
            status = VehicleStatus.BLOCKED;
            return;
        }

        Road road = roadNetwork.getRoad(step.getRoadId());
        if (road == null) {
            releaseOccupiedRoad(roadNetwork, trafficState);
            instantaneousSpeedMps = 0;
            status = VehicleStatus.BLOCKED;
            return;
        }

        if (occupiedRoadId == null && !enterRoadUnderJunctionControl(roadNetwork, trafficState, step, road)) {
            return;
        }

        LaneOccupancy lane = trafficState.occupancyOf(road.getId());
        LeaderGap leaderGap = lane == null ? LeaderGap.FREE_ROAD : lane.findLeaderFor(this);
        double distanceToExitMetres = lane == null
                ? (1.0 - roadProgress) * road.getDistance() * 1_000.0
                : lane.distanceToExitMetres(this);

        plannedMayLeaveRoad = canLeaveRoadAhead(roadNetwork, trafficState);
        double stopDistanceMetres = plannedMayLeaveRoad ? Double.POSITIVE_INFINITY : distanceToExitMetres;

        instantaneousSpeedMps = getCarFollowingModel().nextSpeedMps(new FollowContext(
                instantaneousSpeedMps,
                desiredSpeedMps(road),
                acceleration,
                deceleration,
                getDriverProfile(),
                leaderGap,
                stopDistanceMetres,
                simulationSeconds,
                reproducibleNoise(trafficState.getTick())));

        plannedTravelMetres = Math.min(instantaneousSpeedMps * simulationSeconds, distanceToExitMetres);
        plannedRoadId = road.getId();
    }

    /**
     * Second half of a step: applies the movement worked out by
     * {@link #planMovement}, and hands the vehicle over to the next road when
     * it runs off the end of this one.
     */
    public void commitMovement(RoadNetwork roadNetwork, TrafficState trafficState) {
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        if (plannedRoadId == null) {
            return;
        }

        Road road = roadNetwork.getRoad(plannedRoadId);
        RouteStep step = getNextRouteStep();
        plannedRoadId = null;
        if (road == null || step == null) {
            return;
        }

        double previousProgress = roadProgress;
        roadProgress = Math.min(1.0, roadProgress + plannedTravelMetres / (road.getDistance() * 1_000.0));
        distanceTravelledKm += (roadProgress - previousProgress) * road.getDistance();

        if (instantaneousSpeedMps < QUEUEING_THRESHOLD_MPS) {
            waitingTicks++;
            status = VehicleStatus.WAITING;
        } else {
            status = VehicleStatus.MOVING;
        }

        if (roadProgress >= 1.0 && plannedMayLeaveRoad) {
            nextRouteStepIndex++;
            moveTo(step.getToIntersectionId());
            releaseOccupiedRoad(roadNetwork, trafficState);
            if (hasReachedDestination()) {
                instantaneousSpeedMps = 0;
                status = VehicleStatus.ARRIVED;
            } else if (nextRouteStepIndex >= currentRoute.getStepCount()) {
                instantaneousSpeedMps = 0;
                status = VehicleStatus.BLOCKED;
            }
        }
    }

    /**
     * Describes this vehicle's intention to cross the junction it is standing
     * at, so the junction can rank it against the others waiting there. Returns
     * an empty result for a vehicle that is already on a road or has nowhere to
     * go.
     */
    public Optional<JunctionApproach> planJunctionApproach(RoadNetwork roadNetwork, TrafficState trafficState) {
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        if (occupiedRoadId != null || hasReachedDestination()) {
            return Optional.empty();
        }

        RouteStep step = getNextRouteStep();
        if (step == null || !step.getFromIntersectionId().equals(currentIntersectionId)) {
            return Optional.empty();
        }

        Road targetRoad = roadNetwork.getRoad(step.getRoadId());
        if (targetRoad == null || !targetRoad.isAvailable()) {
            return Optional.empty();
        }

        RouteStep previousStep = nextRouteStepIndex > 0
                ? currentRoute.getSteps().get(nextRouteStepIndex - 1)
                : null;
        Road approachRoad = previousStep == null ? null : roadNetwork.getRoad(previousStep.getRoadId());
        Intersection origin = previousStep == null
                ? null
                : roadNetwork.getIntersection(previousStep.getFromIntersectionId());
        TurnDirection turnDirection = TurnDirection.of(
                origin,
                roadNetwork.getIntersection(currentIntersectionId),
                roadNetwork.getIntersection(step.getToIntersectionId()));

        return Optional.of(new JunctionApproach(
                this,
                currentIntersectionId,
                approachRoad,
                targetRoad,
                turnDirection,
                0.0,
                waitingTicks));
    }

    /**
     * Speed this driver would hold on an empty road, in metres per second.
     *
     * <p>Takes the lower of the vehicle's own maximum and the posted limit,
     * adjusts it by the driver's habitual speed factor, and slows it down for
     * congestion imposed by traffic events. Vehicle density is deliberately
     * left out: under the microscopic model, crowding already shows up as the
     * vehicle in front being close.</p>
     */
    private double desiredSpeedMps(Road road) {
        double postedKph = Math.min(maxSpeed, road.getSpeedLimit()) * getDriverProfile().speedFactor();
        double eventSlowdown = 1.0 + road.getCondition().getCongestionLevel();
        return postedKph / eventSlowdown / KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND;
    }

    /**
     * Takes the vehicle onto its next road if the junction has cleared it and
     * the road physically has room. Returns false when it must keep waiting.
     */
    private boolean enterRoadUnderJunctionControl(
            RoadNetwork roadNetwork,
            TrafficState trafficState,
            RouteStep step,
            Road road) {
        if (!isStepAvailable(roadNetwork, step)) {
            instantaneousSpeedMps = 0;
            status = VehicleStatus.BLOCKED;
            return false;
        }
        if (!trafficState.isCleared(id)) {
            instantaneousSpeedMps = 0;
            waitingTicks++;
            status = VehicleStatus.WAITING;
            return false;
        }

        enterNextRoad(roadNetwork, step);
        roadStartIntersectionId = step.getFromIntersectionId();
        roadEndIntersectionId = step.getToIntersectionId();
        roadProgress = 0.0;
        trafficState.invalidateOccupancy(road.getId());
        return true;
    }

    /**
     * Whether the vehicle may run off the end of its current road. It may when
     * this road takes it to its destination, and otherwise only when the next
     * road still has physical room for it. Refusing otherwise is what makes a
     * queue back up through the junction into the road behind.
     */
    private boolean canLeaveRoadAhead(RoadNetwork roadNetwork, TrafficState trafficState) {
        if (currentRoute == null || nextRouteStepIndex + 1 >= currentRoute.getStepCount()) {
            return true;
        }

        RouteStep followingStep = currentRoute.getSteps().get(nextRouteStepIndex + 1);
        Road followingRoad = roadNetwork.getRoad(followingStep.getRoadId());
        if (followingRoad == null || !followingRoad.isAvailable()) {
            return false;
        }

        LaneOccupancy followingLane = trafficState.occupancyOf(followingRoad.getId());
        return followingLane == null || followingLane.canAdmit(this);
    }

    /**
     * Reproducible substitute for a random draw. The same vehicle at the same
     * tick always yields the same value, so driver imperfection never makes an
     * experiment unrepeatable or dependent on the order of the vehicle list.
     */
    private double reproducibleNoise(long tick) {
        long mixed = id.hashCode() * 0x9E3779B97F4A7C15L + tick;
        mixed ^= (mixed >>> 33);
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= (mixed >>> 33);
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private void releaseOccupiedRoad(RoadNetwork roadNetwork, TrafficState trafficState) {
        String releasedRoadId = occupiedRoadId;
        releaseOccupiedRoad(roadNetwork);
        if (trafficState != null) {
            trafficState.invalidateOccupancy(releasedRoadId);
        }
    }

    public void recomputeRoute(RoutingService routingService) {
        requestRoute(routingService);
    }

    public void recomputeRoute(RoadNetwork roadNetwork, RoutingService routingService) {
        recomputeRoute(roadNetwork, routingService, routePreference);
    }

    public void recomputeRoute(
            RoadNetwork roadNetwork,
            RoutingService routingService,
            RoutePreference preference) {
        routeRecalculationCount++;
        requestRoute(roadNetwork, routingService, preference);
        if (lastRoutingAlgorithm != null) {
            routingService.recordRouteRecalculation(lastRoutingAlgorithm);
        }
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

    public String getNextRoadId() {
        RouteStep step = getNextRouteStep();
        return step == null ? null : step.getRoadId();
    }

    /**
     * Holds the vehicle before it enters a road affected by a traffic-light
     * delay. Each event delays this vehicle at most once.
     */
    public boolean waitForTrafficLight(String eventId, long delayTicks) {
        if (eventId == null || eventId.isBlank() || delayTicks <= 0 || completedTrafficLightEvents.contains(eventId)) {
            return false;
        }
        if (!eventId.equals(activeTrafficLightEventId)) {
            activeTrafficLightEventId = eventId;
            waitTicksRemaining = delayTicks;
        }
        if (waitTicksRemaining > 0) {
            waitTicksRemaining--;
            waitingTicks++;
            status = VehicleStatus.WAITING;
            if (waitTicksRemaining == 0) {
                completedTrafficLightEvents.add(eventId);
                activeTrafficLightEventId = null;
            }
            return true;
        }
        return false;
    }

    public void clearTrafficLightWait() {
        activeTrafficLightEventId = null;
        waitTicksRemaining = 0;
    }

    public long getWaitTicksRemaining() {
        return waitTicksRemaining;
    }

    public void addTravelTime(double simulationSeconds) {
        if (!Double.isFinite(simulationSeconds) || simulationSeconds < 0) {
            throw new IllegalArgumentException("Simulation seconds must not be negative.");
        }
        travelTimeSeconds += simulationSeconds;
    }

    public double getDistanceTravelledKm() { return distanceTravelledKm; }
    public double getTravelTimeSeconds() { return travelTimeSeconds; }
    public long getWaitingTicks() { return waitingTicks; }
    public int getRouteRecalculationCount() { return routeRecalculationCount; }

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
     * Behaviour parameters of this vehicle's driver. Defaults to the profile
     * for its type and can be replaced to model a specific driver.
     */
    public DriverProfile getDriverProfile() {
        if (driverProfile == null) {
            driverProfile = DriverProfile.forType(getType());
        }
        return driverProfile;
    }

    public void setDriverProfile(DriverProfile driverProfile) {
        if (driverProfile == null) {
            throw new IllegalArgumentException("Driver profile is required.");
        }
        this.driverProfile = driverProfile;
    }

    /**
     * Model this driver uses to choose a speed behind another vehicle.
     * Defaults to {@link KraussCarFollowingModel}.
     */
    public CarFollowingModel getCarFollowingModel() {
        return carFollowingModel;
    }

    public void setCarFollowingModel(CarFollowingModel carFollowingModel) {
        if (carFollowingModel == null) {
            throw new IllegalArgumentException("Car-following model is required.");
        }
        this.carFollowingModel = carFollowingModel;
    }

    /**
     * Speed the vehicle is actually travelling at right now, in metres per
     * second, as chosen by the car-following model.
     *
     * <p>This is distinct from {@link #getCurrentSpeed()}, which is the speed
     * the driver would like to hold. The two differ exactly when traffic is
     * getting in the way, so their ratio is a direct measure of delay.</p>
     */
    public double getInstantaneousSpeedMps() {
        return instantaneousSpeedMps;
    }

    /** Actual travelling speed in kilometres per hour. */
    public double getInstantaneousSpeedKph() {
        return instantaneousSpeedMps * KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND;
    }

    public void setInstantaneousSpeedMps(double instantaneousSpeedMps) {
        if (!Double.isFinite(instantaneousSpeedMps) || instantaneousSpeedMps < 0) {
            throw new IllegalArgumentException("Instantaneous speed must not be negative.");
        }
        this.instantaneousSpeedMps = instantaneousSpeedMps;
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
