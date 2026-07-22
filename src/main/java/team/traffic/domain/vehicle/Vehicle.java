package team.traffic.domain.vehicle;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
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
import team.traffic.domain.vehicle.dynamics.Lane;
import team.traffic.domain.vehicle.dynamics.LeaderGap;
import team.traffic.domain.vehicle.dynamics.MovementPlan;
import team.traffic.domain.vehicle.dynamics.LaneChangeModel;
import team.traffic.domain.vehicle.dynamics.LaneChangeRequest;
import team.traffic.domain.vehicle.dynamics.MobilLaneChangeModel;
import team.traffic.domain.vehicle.dynamics.RoadOccupancy;
import team.traffic.domain.vehicle.dynamics.TrafficParticipant;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.domain.vehicle.dynamics.TurnDirection;
import team.traffic.domain.vehicle.pose.PolylineGeometry;
import team.traffic.service.RoutingService;

public abstract class Vehicle implements TrafficParticipant {
    /** Conversion factor between kilometres per hour and metres per second. */
    private static final double KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND = 3.6;
    /** Below this speed a vehicle is reported as queueing rather than moving. */
    private static final double QUEUEING_THRESHOLD_MPS = 0.2;
    /** Vehicles inside this distance participate in stop-line arbitration. */
    private static final double JUNCTION_APPROACH_DISTANCE_METRES = 50.0;

    private final String id;
    private String currentIntersectionId;
    private final String destinationIntersectionId;
    private Route currentRoute;
    private int nextRouteStepIndex;
    private RoutePreference routePreference;
    private final VehicleProfile profile;
    private double currentSpeed;
    private VehicleStatus status;
    private String occupiedRoadId;
    private String roadStartIntersectionId;
    private String roadEndIntersectionId;
    private double roadProgress;
    private int laneIndex;
    private final TripRecord trip;
    private String lastRoutingAlgorithm;
    private DriverProfile driverProfile;
    private CarFollowingModel carFollowingModel;
    private LaneChangeModel laneChangeModel;
    private double instantaneousSpeedMps;

    protected Vehicle(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId,
            VehicleProfile profile,
            double cruisingSpeedKph) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Vehicle id must not be blank.");
        }
        if (currentIntersectionId == null || currentIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Current intersection id must not be blank.");
        }
        if (destinationIntersectionId == null || destinationIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Destination intersection id must not be blank.");
        }
        if (profile == null) {
            throw new IllegalArgumentException("Vehicle profile is required.");
        }
        profile.requireReachable(cruisingSpeedKph);

        this.id = id;
        this.currentIntersectionId = currentIntersectionId;
        this.destinationIntersectionId = destinationIntersectionId;
        this.profile = profile;
        this.currentSpeed = cruisingSpeedKph;
        this.routePreference = RoutePreference.CONGESTION_AWARE;
        this.status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.WAITING;
        this.trip = new TripRecord(currentIntersectionId);
        this.carFollowingModel = new KraussCarFollowingModel();
        this.laneChangeModel = new MobilLaneChangeModel();
        this.instantaneousSpeedMps = currentSpeed / KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND;
        this.laneIndex = 0;
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

    /**
     * Advances this vehicle by one step of the microscopic traffic model.
     *
     * <p>The vehicle is placed among the others physically on its road. It
     * keeps a safe distance from the vehicle in front, brakes for a junction it
     * has not been cleared to cross, and holds at the stop line when the road
     * ahead has no room left. Queues, spillback, and the throughput drop of a
     * saturated road therefore emerge from the interaction of vehicles instead
     * of being imposed by a formula.</p>
     *
     * <p>Junction clearance must already have been decided for this step, so
     * call {@link TrafficState#resolveJunctions()} first. {@link
     * team.traffic.simulation.VehicleDynamicsEngine} performs the whole
     * sequence in the right order, planning every vehicle before moving any.</p>
     */
    public void advance(RoadNetwork roadNetwork, TrafficState trafficState, double simulationSeconds) {
        planMovement(roadNetwork, trafficState, simulationSeconds)
                .ifPresent(plan -> commitMovement(plan, roadNetwork, trafficState));
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
    public Optional<MovementPlan> planMovement(
            RoadNetwork roadNetwork,
            TrafficState trafficState,
            double simulationSeconds) {
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        Objects.requireNonNull(trafficState, "Traffic state must not be null.");
        if (!Double.isFinite(simulationSeconds) || simulationSeconds <= 0) {
            throw new IllegalArgumentException("Simulation seconds must be positive.");
        }

        if (status == VehicleStatus.STOPPED) {
            instantaneousSpeedMps = 0.0;
            return Optional.empty();
        }

        if (hasReachedDestination()) {
            releaseOccupiedRoad(roadNetwork, trafficState);
            instantaneousSpeedMps = 0;
            status = VehicleStatus.ARRIVED;
            return Optional.empty();
        }

        RouteStep step = getNextRouteStep();
        if (step == null || !step.getFromIntersectionId().equals(currentIntersectionId)) {
            releaseOccupiedRoad(roadNetwork, trafficState);
            instantaneousSpeedMps = 0;
            status = VehicleStatus.BLOCKED;
            return Optional.empty();
        }

        Road road = roadNetwork.getRoad(step.getRoadId());
        if (road == null) {
            releaseOccupiedRoad(roadNetwork, trafficState);
            instantaneousSpeedMps = 0;
            status = VehicleStatus.BLOCKED;
            return Optional.empty();
        }

        if (occupiedRoadId == null && !enterRoadUnderJunctionControl(roadNetwork, trafficState, step, road)) {
            return Optional.empty();
        }

        RoadOccupancy roadOccupancy = trafficState.occupancyOf(road.getId());
        int targetLaneIndex = laneIndex;
        if (roadOccupancy != null) {
            targetLaneIndex = laneChangeModel.chooseTargetLane(new LaneChangeRequest(
                    this, roadOccupancy, desiredSpeedMps(road),
                    profile.accelerationMps2(), profile.decelerationMps2(), simulationSeconds));
        }
        Lane lane = roadOccupancy == null ? null : roadOccupancy.lane(targetLaneIndex);
        LeaderGap leaderGap = lane == null ? LeaderGap.FREE_ROAD : lane.findLeaderFor(this);
        double distanceToExitMetres = lane == null
                ? (1.0 - roadProgress) * road.getDistance() * 1_000.0
                : lane.distanceToExitMetres(this);

        boolean heldAtRoadExit = shouldHoldAtRoadExit(
                step.getToIntersectionId(), distanceToExitMetres, trafficState.getElapsedSeconds());
        boolean mayLeaveRoad = !heldAtRoadExit
                && canLeaveRoadAhead(roadNetwork, trafficState, distanceToExitMetres);
        double stopDistanceMetres = mayLeaveRoad ? Double.POSITIVE_INFINITY : distanceToExitMetres;

        double plannedSpeedMps = getCarFollowingModel().nextSpeedMps(new FollowContext(
                instantaneousSpeedMps,
                desiredSpeedMps(road),
                profile.accelerationMps2(),
                profile.decelerationMps2(),
                getDriverProfile(),
                leaderGap,
                stopDistanceMetres,
                simulationSeconds,
                reproducibleNoise(trafficState.getTick())));

        return Optional.of(new MovementPlan(
                road.getId(),
                plannedSpeedMps,
                Math.min(plannedSpeedMps * simulationSeconds, distanceToExitMetres),
                mayLeaveRoad,
                targetLaneIndex));
    }

    /**
     * Second half of a step: carries out a plan made by {@link #planMovement},
     * and hands the vehicle over to the next road when it runs off the end of
     * this one.
     */
    public void commitMovement(MovementPlan plan, RoadNetwork roadNetwork, TrafficState trafficState) {
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        if (plan == null) {
            return;
        }

        Road road = roadNetwork.getRoad(plan.roadId());
        RouteStep step = getNextRouteStep();
        if (road == null || step == null) {
            return;
        }

        // The new speed only takes effect now, so a vehicle planning later in
        // the step still reads its leader at the speed it was going, not the
        // one it just chose.
        instantaneousSpeedMps = plan.speedMps();
        laneIndex = plan.targetLaneIndex();

        double previousProgress = roadProgress;
        roadProgress = Math.min(1.0, roadProgress + plan.travelMetres() / (road.getDistance() * 1_000.0));
        trip.addDistanceKm((roadProgress - previousProgress) * road.getDistance());

        if (instantaneousSpeedMps < QUEUEING_THRESHOLD_MPS) {
            trip.recordWaitingTick();
            status = VehicleStatus.WAITING;
        } else {
            status = VehicleStatus.MOVING;
        }

        if (roadProgress >= 1.0 && plan.mayLeaveRoad()) {
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
        if (hasReachedDestination()) {
            return Optional.empty();
        }
        if (occupiedRoadId != null) {
            return planRoadExitApproach(roadNetwork);
        }
        if (shouldHoldAtJunction(currentIntersectionId, trafficState.getElapsedSeconds())) {
            instantaneousSpeedMps = 0.0;
            trip.recordWaitingTick();
            status = VehicleStatus.WAITING;
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
                trip.getWaitingTicks()));
    }

    private Optional<JunctionApproach> planRoadExitApproach(RoadNetwork roadNetwork) {
        RouteStep currentStep = getNextRouteStep();
        if (currentStep == null || currentRoute == null
                || nextRouteStepIndex + 1 >= currentRoute.getStepCount()) {
            return Optional.empty();
        }
        Road approachRoad = roadNetwork.getRoad(occupiedRoadId);
        if (approachRoad == null || !currentStep.getRoadId().equals(occupiedRoadId)) {
            return Optional.empty();
        }
        double distanceToJunctionMetres = Math.max(
                0.0, (1.0 - roadProgress) * approachRoad.getDistance() * 1_000.0);
        if (distanceToJunctionMetres > JUNCTION_APPROACH_DISTANCE_METRES) {
            return Optional.empty();
        }

        RouteStep followingStep = currentRoute.getSteps().get(nextRouteStepIndex + 1);
        Road targetRoad = roadNetwork.getRoad(followingStep.getRoadId());
        if (targetRoad == null || !targetRoad.isAvailable()) {
            return Optional.empty();
        }
        TurnDirection turnDirection = TurnDirection.of(
                roadNetwork.getIntersection(currentStep.getFromIntersectionId()),
                roadNetwork.getIntersection(currentStep.getToIntersectionId()),
                roadNetwork.getIntersection(followingStep.getToIntersectionId()));
        return Optional.of(new JunctionApproach(
                this,
                currentStep.getToIntersectionId(),
                approachRoad,
                targetRoad,
                turnDirection,
                distanceToJunctionMetres,
                trip.getWaitingTicks()));
    }

    /** Extension point for transit dwell or another junction-side hold. */
    protected boolean shouldHoldAtJunction(String junctionId, double elapsedSeconds) {
        return false;
    }

    /** Extension point for a bus stop or another curb-side hold at the stop line. */
    protected boolean shouldHoldAtRoadExit(
            String junctionId, double distanceToExitMetres, double elapsedSeconds) {
        return false;
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
        double postedKph = Math.min(profile.maxSpeedKph(), road.getSpeedLimit()) * getDriverProfile().speedFactor();
        double eventSlowdown = 1.0 + road.getCondition().getCongestionLevel();
        double unconstrainedMps = postedKph / eventSlowdown / KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND;
        double radiusMetres = PolylineGeometry.curvatureRadiusAt(road.getGeometry(), roadProgress);
        double corneringMps = Double.isInfinite(radiusMetres)
                ? Double.POSITIVE_INFINITY
                : Math.sqrt(getDriverProfile().lateralAccelerationMps2() * radiusMetres);
        return Math.min(unconstrainedMps, corneringMps);
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
            trip.recordWaitingTick();
            status = VehicleStatus.WAITING;
            return false;
        }

        RoadOccupancy targetOccupancy = trafficState.occupancyOf(road.getId());
        int entryLane = targetOccupancy == null ? 0 : targetOccupancy.bestLaneForEntry(this);
        if (entryLane < 0) {
            instantaneousSpeedMps = 0;
            trip.recordWaitingTick();
            status = VehicleStatus.WAITING;
            return false;
        }

        laneIndex = entryLane;
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
    private boolean canLeaveRoadAhead(
            RoadNetwork roadNetwork,
            TrafficState trafficState,
            double distanceToExitMetres) {
        if (currentRoute == null || nextRouteStepIndex + 1 >= currentRoute.getStepCount()) {
            return true;
        }

        RouteStep followingStep = currentRoute.getSteps().get(nextRouteStepIndex + 1);
        Road followingRoad = roadNetwork.getRoad(followingStep.getRoadId());
        if (followingRoad == null || !followingRoad.isAvailable()) {
            return false;
        }

        // A vehicle close enough to cross must have been cleared for the
        // current signal phase and conflicting approaches. Farther upstream it
        // may continue towards the line without reserving the junction.
        if (distanceToExitMetres <= JUNCTION_APPROACH_DISTANCE_METRES
                && !trafficState.isCleared(id)) {
            return false;
        }

        RoadOccupancy followingRoadOccupancy = trafficState.occupancyOf(followingRoad.getId());
        return followingRoadOccupancy == null || followingRoadOccupancy.canAdmit(this);
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
        trip.recordRouteRecalculation();
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
        this.trip.recordArrivalAt(nextIntersectionId);
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

    @Override
    public int getLaneIndex() {
        return laneIndex;
    }

    public String getNextRoadId() {
        RouteStep step = getNextRouteStep();
        return step == null ? null : step.getRoadId();
    }

    public void addTravelTime(double simulationSeconds) {
        trip.addTravelTime(simulationSeconds);
    }

    /** Everything this vehicle accumulated over its journey. */
    public TripRecord getTrip() {
        return trip;
    }

    public double getDistanceTravelledKm() { return trip.getDistanceTravelledKm(); }
    public double getTravelTimeSeconds() { return trip.getTravelTimeSeconds(); }
    public long getWaitingTicks() { return trip.getWaitingTicks(); }
    public int getRouteRecalculationCount() { return trip.getRouteRecalculationCount(); }

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
        return profile.lengthMetres();
    }

    /**
     * Vehicle width in metres.
     */
    public double getWidth() {
        return profile.widthMetres();
    }

    /**
     * Maximum road speed in kilometres per hour.
     */
    public double getMaxSpeed() {
        return profile.maxSpeedKph();
    }

    /**
     * Current road speed in kilometres per hour.
     */
    public double getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(double currentSpeed) {
        profile.requireReachable(currentSpeed);
        this.currentSpeed = currentSpeed;
    }

    /**
     * Acceleration in metres per second squared.
     */
    public double getAcceleration() {
        return profile.accelerationMps2();
    }

    @Override
    public double getAccelerationMps2() {
        return profile.accelerationMps2();
    }

    /**
     * Braking deceleration in metres per second squared.
     */
    public double getDeceleration() {
        return profile.decelerationMps2();
    }

    @Override
    public double getDecelerationMps2() {
        return profile.decelerationMps2();
    }

    /**
     * Vehicle mass in kilograms.
     */
    public double getVehicleWeight() {
        return profile.weightKilograms();
    }

    /**
     * Relative dispatch priority. Standard vehicles use {@code 0}.
     */
    public int getPriorityLevel() {
        return profile.priorityLevel();
    }

    /**
     * Behaviour parameters of this vehicle's driver. Defaults to the profile
     * for its type and can be replaced to model a specific driver.
     */
    public DriverProfile getDriverProfile() {
        if (driverProfile == null) {
            driverProfile = DriverProfile.variedFor(getType(), id);
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

    public LaneChangeModel getLaneChangeModel() {
        return laneChangeModel;
    }

    public void setLaneChangeModel(LaneChangeModel laneChangeModel) {
        if (laneChangeModel == null) throw new IllegalArgumentException("Lane-change model is required.");
        this.laneChangeModel = laneChangeModel;
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
        setCurrentSpeed(Math.min(profile.maxSpeedKph(), currentSpeed + profile.accelerationMps2() * validateDuration(durationSeconds) * KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND));
    }

    /**
     * Reduces the current speed for a duration, floored at zero.
     * This operation is intentionally independent of the graph-based tick movement.
     */
    public void decelerate(double durationSeconds) {
        setCurrentSpeed(Math.max(0.0, currentSpeed - profile.decelerationMps2() * validateDuration(durationSeconds) * KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND));
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        if (status == null) throw new IllegalArgumentException("Vehicle status is required.");
        this.status = status;
    }

    public void stop() {
        instantaneousSpeedMps = 0.0;
        status = VehicleStatus.STOPPED;
    }

    public void resumeFromStop() {
        if (status == VehicleStatus.STOPPED) {
            status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.WAITING;
        }
    }

    public List<String> getTravelHistory() {
        return trip.getVisitedIntersections();
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
        laneIndex = 0;
        roadStartIntersectionId = null;
        roadEndIntersectionId = null;
        roadProgress = 0.0;
    }
}
