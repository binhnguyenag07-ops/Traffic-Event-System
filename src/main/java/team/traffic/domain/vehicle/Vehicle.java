package team.traffic.domain.vehicle;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import team.traffic.domain.demand.PlaceKind;
import team.traffic.domain.demand.ResolvedPlace;
import team.traffic.domain.demand.TripPlan;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.JunctionMovement;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RouteAnchor;
import team.traffic.domain.routing.RouteCandidate;
import team.traffic.domain.routing.RouteCost;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteReplacementResult;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.dynamics.CarFollowingModel;
import team.traffic.domain.vehicle.dynamics.DriverProfile;
import team.traffic.domain.vehicle.dynamics.FilteringLaneChangeModel;
import team.traffic.domain.vehicle.dynamics.FilteringRules;
import team.traffic.domain.vehicle.dynamics.FollowContext;
import team.traffic.domain.vehicle.dynamics.LateralTarget;
import team.traffic.domain.vehicle.dynamics.LocalPerception;
import team.traffic.domain.vehicle.dynamics.JunctionApproach;
import team.traffic.domain.vehicle.dynamics.KraussCarFollowingModel;
import team.traffic.domain.vehicle.dynamics.Lane;
import team.traffic.domain.vehicle.dynamics.LateralMotionModel;
import team.traffic.domain.vehicle.dynamics.LeaderGap;
import team.traffic.domain.vehicle.dynamics.MovementPlan;
import team.traffic.domain.vehicle.dynamics.MotionOutcome;
import team.traffic.domain.vehicle.dynamics.LaneChangeModel;
import team.traffic.domain.vehicle.dynamics.LaneChangeIntent;
import team.traffic.domain.vehicle.dynamics.LaneChangeRequest;
import team.traffic.domain.vehicle.dynamics.MobilLaneChangeModel;
import team.traffic.domain.vehicle.dynamics.RoadOccupancy;
import team.traffic.domain.vehicle.dynamics.TrafficParticipant;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.domain.vehicle.dynamics.TurnDirection;
import team.traffic.domain.vehicle.dynamics.JunctionDecision;
import team.traffic.domain.vehicle.dynamics.JunctionDenialReason;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.domain.vehicle.dynamics.WaitResource;
import team.traffic.domain.vehicle.dynamics.WaitState;
import team.traffic.domain.vehicle.navigation.DriverKnowledge;
import team.traffic.domain.vehicle.navigation.NavigationState;
import team.traffic.domain.vehicle.navigation.ProgressStallTracker;
import team.traffic.domain.vehicle.navigation.StallState;
import team.traffic.domain.vehicle.pose.PolylineGeometry;
import team.traffic.service.RoutingService;

public abstract class Vehicle implements TrafficParticipant {
    /** Conversion factor between kilometres per hour and metres per second. */
    private static final double KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND = 3.6;
    /** Below this speed a vehicle is reported as queueing rather than moving. */
    private static final double QUEUEING_THRESHOLD_MPS = 0.2;
    /** Vehicles inside this distance participate in stop-line arbitration. */
    private static final double JUNCTION_APPROACH_DISTANCE_METRES = 50.0;
    /** Low-speed movement between a building centre and its selected access node. */
    public static final double BUILDING_ACCESS_SPEED_MPS = 15.0 / 3.6;

    private final String id;
    private String currentIntersectionId;
    private String destinationIntersectionId;
    private String originBuildingId;
    private String destinationBuildingId;
    private ResolvedPlace originResolvedPlace;
    private ResolvedPlace destinationResolvedPlace;
    private TripPlan tripPlan;
    private int currentTripLegIndex = -1;
    private ResolvedPlace currentResolvedPlace;
    private double dwellUntilSeconds = Double.NaN;
    private JourneyPhase accessLinkPhase;
    private double accessLinkDistanceMetres;
    private double accessLinkTravelledMetres;
    private boolean currentLegDepartureAccessComplete = true;
    private boolean currentLegStopProcessed;
    private final RoutePlan routePlan = new RoutePlan();
    private final VehicleProfile profile;
    private double cruisingSpeedKph;
    private VehicleStatus status;
    private final RoadPlacement placement = new RoadPlacement();
    private final TripRecord trip;
    private DriverProfile driverProfile;
    private CarFollowingModel carFollowingModel;
    private LaneChangeModel laneChangeModel;
    private double instantaneousSpeedMps;
    private WaitState waitState;
    private final DriverKnowledge driverKnowledge = new DriverKnowledge();
    private final NavigationState navigationState = new NavigationState();
    private ProgressStallTracker stallTracker = new ProgressStallTracker();
    private LocalPerception localPerception;
    private LaneChangeIntent pendingLaneChangeIntent = LaneChangeIntent.DISCRETIONARY;

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
        this.originResolvedPlace = ResolvedPlace.intersection(currentIntersectionId);
        this.destinationResolvedPlace = ResolvedPlace.intersection(destinationIntersectionId);
        this.currentResolvedPlace = this.originResolvedPlace;
        this.profile = profile;
        this.cruisingSpeedKph = cruisingSpeedKph;
        this.status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.WAITING;
        this.trip = new TripRecord(currentIntersectionId);
        this.carFollowingModel = new KraussCarFollowingModel();
        this.laneChangeModel = new MobilLaneChangeModel();
        this.instantaneousSpeedMps = cruisingSpeedKph / KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND;
        this.waitState = WaitState.none(0.0);
    }

    public abstract VehicleType getType();

    public void requestRoute(RoutingService routingService) {
        Objects.requireNonNull(routingService, "Routing service must not be null.");
        routePlan.clear();
        this.status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.WAITING;
    }

    public void requestRoute(RoadNetwork roadNetwork, RoutingService routingService) {
        requestRoute(roadNetwork, routingService, routePlan.getPreference());
    }

    public void requestRoute(
            RoadNetwork roadNetwork,
            RoutingService routingService,
            RoutePreference preference) {
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        Objects.requireNonNull(routingService, "Routing service must not be null.");
        Objects.requireNonNull(preference, "Route preference must not be null.");

        routePlan.setPreference(preference);

        if (hasReachedDestination()) {
            routePlan.clear();
            status = VehicleStatus.ARRIVED;
            return;
        }

        try {
            RouteResult result;
            if (destinationBuildingId == null || tripPlan != null) {
                RouteRequest request = new RouteRequest(
                        currentIntersectionId,
                        destinationIntersectionId,
                        preference);
                result = routingService.findRoute(roadNetwork, request);
            } else {
                var buildingPlan = routingService.findRouteToBuilding(
                        roadNetwork, currentIntersectionId, destinationBuildingId, preference);
                destinationIntersectionId = buildingPlan.destinationAccess().intersectionId();
                destinationResolvedPlace = ResolvedPlace.building(
                        destinationBuildingId, destinationIntersectionId);
                result = buildingPlan.routeResult();
            }
            routePlan.assign(
                    result == null ? null : result.getRoute(),
                    result == null ? null : result.getAlgorithmName());
            status = isUsableRoute(routePlan.getRoute())
                    ? VehicleStatus.WAITING : VehicleStatus.BLOCKED;
        } catch (NoSuchElementException exception) {
            routePlan.clear();
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
        commitMotion(
                planMotion(roadNetwork, trafficState, simulationSeconds),
                roadNetwork,
                trafficState);
    }

    /** Plans either movement or an explicit zero-motion reason for this sub-step. */
    public MotionOutcome planMotion(
            RoadNetwork roadNetwork,
            TrafficState trafficState,
            double simulationSeconds) {
        Optional<MovementPlan> movement = planMovement(
                roadNetwork, trafficState, simulationSeconds);
        double observedAt = trafficState.getElapsedSeconds();
        WaitState plannedWait = inferWaitState(
                movement, roadNetwork, trafficState, observedAt);
        return new MotionOutcome(movement, plannedWait, observedAt, simulationSeconds);
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

        if (isTraversingBuildingAccessLink()) {
            return Optional.empty();
        }

        if (status == VehicleStatus.STOPPED || status == VehicleStatus.DWELLING) {
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

        if (!placement.isOnRoad()
                && !enterRoadUnderJunctionControl(roadNetwork, trafficState, step, road)) {
            return Optional.empty();
        }

        RoadOccupancy roadOccupancy = trafficState.occupancyOf(road.getId());
        double lateralOffsetMetres = placement.getLateralOffsetMetres();
        int targetLaneIndex = placement.getLaneIndex();
        double plannedLateralOffsetMetres = lateralOffsetMetres;
        double targetOffsetMetres = lateralOffsetMetres;
        if (roadOccupancy != null) {
            targetOffsetMetres = placement.getLateralTargetOffsetMetres();
            // A vehicle still crossing towards its chosen position is committed
            // to it and does not get another say until it arrives. Asking the
            // model afresh every step would let it drift back and forth without
            // ever completing a manoeuvre.
            if (LateralMotionModel.hasSettled(lateralOffsetMetres, targetOffsetMetres)) {
                LateralTarget target = laneChangeModel.chooseTarget(new LaneChangeRequest(
                        this, roadOccupancy, desiredSpeedMps(road),
                        profile.accelerationMps2(), profile.decelerationMps2(), simulationSeconds,
                        pendingLaneChangeIntent));
                targetLaneIndex = target.laneIndex();
                targetOffsetMetres = target.offsetMetres();
                pendingLaneChangeIntent = LaneChangeIntent.DISCRETIONARY;
            }
            plannedLateralOffsetMetres = LateralMotionModel.nextOffsetMetres(
                    lateralOffsetMetres,
                    targetOffsetMetres,
                    getDriverProfile().maxLateralSpeedMps(),
                    simulationSeconds);
        }
        Lane lane = roadOccupancy == null ? null : roadOccupancy.lane(targetLaneIndex);
        LeaderGap leaderGap = roadOccupancy == null
                ? LeaderGap.FREE_ROAD
                : roadOccupancy.findLeaderAtOffset(
                        this, plannedLateralOffsetMetres, lateralClearanceMetres());
        double distanceToExitMetres = lane == null
                ? (1.0 - placement.getProgress()) * road.getDistance() * 1_000.0
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

        plannedSpeedMps = applyFilteringSpeedCap(
                plannedSpeedMps, roadOccupancy, plannedLateralOffsetMetres, targetLaneIndex);

        return Optional.of(new MovementPlan(
                road.getId(),
                plannedSpeedMps,
                Math.min(plannedSpeedMps * simulationSeconds, distanceToExitMetres),
                mayLeaveRoad,
                targetLaneIndex,
                plannedLateralOffsetMetres,
                targetOffsetMetres));
    }

    /**
     * Holds a filtering rider to a sane speed relative to the traffic it is
     * passing. A rider threading stopped traffic at road speed would produce
     * real throughput numbers from an unreal manoeuvre.
     */
    private double applyFilteringSpeedCap(
            double plannedSpeedMps,
            RoadOccupancy roadOccupancy,
            double lateralOffsetAfterStep,
            int targetLaneIndex) {
        if (roadOccupancy == null
                || !(laneChangeModel instanceof FilteringLaneChangeModel filtering)) {
            return plannedSpeedMps;
        }
        boolean betweenLanes = !LateralMotionModel.hasSettled(
                lateralOffsetAfterStep, roadOccupancy.laneCentreOffsetMetres(targetLaneIndex));
        FilteringRules rules = filtering.getRules();
        return rules.cappedSpeedMps(
                plannedSpeedMps,
                roadOccupancy.slowestNeighbourSpeedMps(this, rules.neighbourWindowMetres()),
                betweenLanes);
    }

    /** Room this vehicle's driver keeps beside them when judging who is in the way. */
    private double lateralClearanceMetres() {
        return laneChangeModel instanceof FilteringLaneChangeModel filtering
                ? filtering.getRules().lateralClearanceMetres()
                : FilteringRules.defaults().lateralClearanceMetres();
    }

    /**
     * Where this vehicle is currently steering towards: its lane centre unless
     * it is a filtering rider that chose a gap between lanes.
     */
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

        double previousProgress = placement.getProgress();
        double progress = Math.min(
                1.0, previousProgress + plan.travelMetres() / (road.getDistance() * 1_000.0));
        placement.moveTo(
                progress,
                plan.targetLaneIndex(),
                plan.lateralOffsetMetres(),
                plan.targetLateralOffsetMetres());
        trip.addDistanceKm((progress - previousProgress) * road.getDistance());

        if (instantaneousSpeedMps < QUEUEING_THRESHOLD_MPS) {
            trip.recordWaitingTick();
            status = VehicleStatus.WAITING;
        } else {
            status = VehicleStatus.MOVING;
        }

        if (progress >= 1.0 && plan.mayLeaveRoad()) {
            routePlan.advance();
            moveTo(step.getToIntersectionId());
            releaseOccupiedRoad(roadNetwork, trafficState);
            if (hasReachedDestination()) {
                instantaneousSpeedMps = 0;
                status = VehicleStatus.ARRIVED;
            } else if (routePlan.isExhausted()) {
                instantaneousSpeedMps = 0;
                status = VehicleStatus.BLOCKED;
            }
        }
    }

    /** Commits a plan and its cause-local waiting evidence as one outcome. */
    public void commitMotion(
            MotionOutcome outcome,
            RoadNetwork roadNetwork,
            TrafficState trafficState) {
        if (outcome == null) {
            return;
        }
        outcome.movement().ifPresent(plan -> commitMovement(plan, roadNetwork, trafficState));
        stallTracker.update(
                outcome,
                localPerception == null
                        ? team.traffic.domain.vehicle.dynamics.ExitReachability.UNKNOWN
                        : localPerception.exitReachability());

        if (status == VehicleStatus.ARRIVED) {
            waitState = WaitState.none(outcome.observedAtSeconds() + outcome.durationSeconds());
            return;
        }

        boolean meaningfulProgress = outcome.madeMeaningfulProgress(0.01);
        WaitState reported = outcome.waitState();
        if (meaningfulProgress || !reported.isWaiting()) {
            waitState = WaitState.none(outcome.observedAtSeconds() + outcome.durationSeconds());
            return;
        }

        if (outcome.movement().isEmpty()) {
            trip.recordWaitingTick();
        }
        trip.recordWaiting(outcome.durationSeconds(), reported.cause());
        double since = waitState.cause() == reported.cause()
                        && waitState.resource().equals(reported.resource())
                ? waitState.sinceSeconds()
                : reported.sinceSeconds();
        waitState = new WaitState(
                reported.cause(),
                since,
                reported.resource(),
                reported.expectedReleaseAtSeconds());
        if (status != VehicleStatus.STOPPED
                && status != VehicleStatus.DWELLING
                && status != VehicleStatus.BLOCKED) {
            status = VehicleStatus.WAITING;
        }
    }

    private WaitState inferWaitState(
            Optional<MovementPlan> movement,
            RoadNetwork roadNetwork,
            TrafficState trafficState,
            double observedAtSeconds) {
        if (status == VehicleStatus.DWELLING) {
            java.util.OptionalDouble release = Double.isFinite(dwellUntilSeconds)
                    ? java.util.OptionalDouble.of(Math.max(observedAtSeconds, dwellUntilSeconds))
                    : java.util.OptionalDouble.empty();
            return new WaitState(
                    WaitCause.DWELL,
                    observedAtSeconds,
                    Optional.empty(),
                    release);
        }
        if (status == VehicleStatus.STOPPED) {
            return waiting(WaitCause.EVENT_STOPPED, observedAtSeconds, Optional.empty());
        }
        if (status == VehicleStatus.BLOCKED || getNextRouteStep() == null) {
            return waiting(WaitCause.ROUTE_MISSING, observedAtSeconds, Optional.empty());
        }

        Optional<JunctionDecision> decision = trafficState.junctionDecisionFor(id);
        if (movement.isEmpty()) {
            if (decision.isPresent() && !decision.get().granted()) {
                return waitFromDecision(decision.get(), observedAtSeconds);
            }
            RouteStep step = getNextRouteStep();
            Road road = step == null ? null : roadNetwork.getRoad(step.getRoadId());
            if (road == null || !road.isRoutable()) {
                return waiting(WaitCause.ROAD_UNAVAILABLE, observedAtSeconds, Optional.empty());
            }
            return waiting(WaitCause.UNKNOWN, observedAtSeconds, Optional.empty());
        }

        MovementPlan plan = movement.get();
        if (plan.travelMetres() >= 0.01 && plan.speedMps() >= QUEUEING_THRESHOLD_MPS) {
            return WaitState.none(observedAtSeconds);
        }
        if (!plan.mayLeaveRoad()) {
            if (decision.isPresent() && !decision.get().granted()) {
                return waitFromDecision(decision.get(), observedAtSeconds);
            }
            RouteStep current = getNextRouteStep();
            RouteStep following = routePlan.stepAt(1);
            if (current != null && following != null) {
                return waiting(
                        WaitCause.DOWNSTREAM_FULL,
                        observedAtSeconds,
                        Optional.of(new WaitResource.JunctionMovement(
                                current.getToIntersectionId(),
                                current.getRoadId(),
                                following.getRoadId())));
            }
            return waiting(
                    WaitCause.DOWNSTREAM_FULL,
                    observedAtSeconds,
                    Optional.of(new WaitResource.RoadExit(plan.roadId())));
        }

        RoadOccupancy occupancy = trafficState.occupancyOf(plan.roadId());
        Lane lane = occupancy == null ? null : occupancy.lane(getLaneIndex());
        if (lane != null && !lane.getObstructions().isEmpty()) {
            return waiting(
                    WaitCause.LOCAL_OBSTRUCTION,
                    observedAtSeconds,
                    Optional.of(new WaitResource.LocalObstruction(
                            lane.getObstructions().get(0).getId())));
        }
        team.traffic.domain.vehicle.dynamics.TrafficParticipant leader =
                occupancy == null
                        ? null
                        : occupancy.nearestOverlappingLeader(
                                this, lateralClearanceMetres());
        return waiting(
                WaitCause.LEADER,
                observedAtSeconds,
                leader == null
                        ? Optional.empty()
                        : Optional.of(new WaitResource.Leader(leader.getId())));
    }

    private WaitState waitFromDecision(
            JunctionDecision decision,
            double observedAtSeconds) {
        JunctionApproach approach = decision.approach();
        WaitCause cause = switch (decision.denialReason()) {
            case RED_SIGNAL -> WaitCause.RED_SIGNAL;
            case DOWNSTREAM_FULL -> WaitCause.DOWNSTREAM_FULL;
            case UNSAFE_GAP, CONFLICTING_MOVEMENT -> WaitCause.JUNCTION_YIELD;
            case RECOVERY_HOLD -> WaitCause.RECOVERY_HOLD;
            case NONE -> WaitCause.NONE;
        };
        Optional<WaitResource> resource = Optional.of(new WaitResource.JunctionMovement(
                approach.junctionId(),
                approach.fromRoad() == null ? null : approach.fromRoad().getId(),
                approach.toRoad().getId()));
        return new WaitState(
                cause,
                observedAtSeconds,
                resource,
                decision.expectedReleaseAtSeconds());
    }

    private WaitState waiting(
            WaitCause cause,
            double observedAtSeconds,
            Optional<WaitResource> resource) {
        return new WaitState(
                cause,
                observedAtSeconds,
                resource,
                java.util.OptionalDouble.empty());
    }

    /**
     * Describes this vehicle's intention to cross the junction it is standing
     * at, so the junction can rank it against the others waiting there. Returns
     * an empty result for a vehicle that is already on a road or has nowhere to
     * go.
     */
    public Optional<JunctionApproach> planJunctionApproach(RoadNetwork roadNetwork, TrafficState trafficState) {
        return junctionApproach(roadNetwork, trafficState, true);
    }

    /** Side-effect-free recovery/deadlock inspection of the next legal movement. */
    public Optional<JunctionApproach> previewJunctionApproach(
            RoadNetwork roadNetwork,
            TrafficState trafficState) {
        return junctionApproach(roadNetwork, trafficState, false);
    }

    private Optional<JunctionApproach> junctionApproach(
            RoadNetwork roadNetwork,
            TrafficState trafficState,
            boolean applyRuntimeEffects) {
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        Objects.requireNonNull(trafficState, "Traffic state must not be null.");
        if (isTraversingBuildingAccessLink()
                || hasReachedDestination()
                || status == VehicleStatus.DWELLING) {
            return Optional.empty();
        }
        if (placement.isOnRoad()) {
            return planRoadExitApproach(roadNetwork, applyRuntimeEffects);
        }
        if (shouldHoldAtJunction(currentIntersectionId, trafficState.getElapsedSeconds())) {
            if (applyRuntimeEffects) {
                instantaneousSpeedMps = 0.0;
                trip.recordWaitingTick();
                status = VehicleStatus.WAITING;
            }
            return Optional.empty();
        }

        RouteStep step = getNextRouteStep();
        if (step == null || !step.getFromIntersectionId().equals(currentIntersectionId)) {
            return Optional.empty();
        }

        Road targetRoad = roadNetwork.getRoad(step.getRoadId());
        if (targetRoad == null || !targetRoad.isRoutable()) {
            return Optional.empty();
        }

        RouteStep previousStep = routePlan.stepAt(-1);
        Road approachRoad = previousStep == null ? null : roadNetwork.getRoad(previousStep.getRoadId());
        Optional<JunctionMovement> legalMovement = roadNetwork.getMovementGraph().find(
                roadNetwork,
                currentIntersectionId,
                approachRoad == null ? null : approachRoad.getId(),
                targetRoad.getId()).filter(movement -> movement.allows(getType()));
        if (legalMovement.isEmpty()) {
            if (applyRuntimeEffects) status = VehicleStatus.BLOCKED;
            return Optional.empty();
        }
        TurnDirection turnDirection = legalMovement.get().direction();

        return Optional.of(new JunctionApproach(
                this,
                currentIntersectionId,
                approachRoad,
                targetRoad,
                placement.getLastLaneIndex(),
                turnDirection,
                0.0,
                trip.getWaitingTicks()));
    }

    private Optional<JunctionApproach> planRoadExitApproach(
            RoadNetwork roadNetwork,
            boolean applyRuntimeEffects) {
        RouteStep currentStep = getNextRouteStep();
        RouteStep followingStep = routePlan.stepAt(1);
        if (currentStep == null || followingStep == null) {
            return Optional.empty();
        }
        Road approachRoad = roadNetwork.getRoad(placement.getOccupiedRoadId());
        if (approachRoad == null || !currentStep.getRoadId().equals(placement.getOccupiedRoadId())) {
            return Optional.empty();
        }
        double distanceToJunctionMetres = Math.max(
                0.0, (1.0 - placement.getProgress()) * approachRoad.getDistance() * 1_000.0);
        if (distanceToJunctionMetres > JUNCTION_APPROACH_DISTANCE_METRES) {
            return Optional.empty();
        }

        Road targetRoad = roadNetwork.getRoad(followingStep.getRoadId());
        if (targetRoad == null || !targetRoad.isRoutable()) {
            return Optional.empty();
        }
        Optional<JunctionMovement> legalMovement = roadNetwork.getMovementGraph().find(
                roadNetwork,
                currentStep.getToIntersectionId(),
                approachRoad.getId(),
                targetRoad.getId()).filter(movement -> movement.allows(getType()));
        if (legalMovement.isEmpty()) {
            if (applyRuntimeEffects) status = VehicleStatus.BLOCKED;
            return Optional.empty();
        }
        TurnDirection turnDirection = legalMovement.get().direction();
        return Optional.of(new JunctionApproach(
                this,
                currentStep.getToIntersectionId(),
                approachRoad,
                targetRoad,
                placement.getLaneIndex(),
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
        double radiusMetres = PolylineGeometry.curvatureRadiusAt(road.getGeometry(), placement.getProgress());
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
        int entryLane = targetOccupancy == null
                ? 0
                : targetOccupancy.continuationLaneForEntry(
                        this, placement.getLastLaneIndex());
        if (entryLane < 0) {
            instantaneousSpeedMps = 0;
            trip.recordWaitingTick();
            status = VehicleStatus.WAITING;
            return false;
        }

        double entryLaneCentreMetres = targetOccupancy == null
                ? 0.0
                : targetOccupancy.laneCentreOffsetMetres(entryLane);
        RouteStep previousStep = routePlan.stepAt(-1);
        boolean completesUTurn = previousStep != null
                && roadNetwork.getMovementGraph().find(
                        currentIntersectionId,
                        previousStep.getRoadId(),
                        step.getRoadId())
                        .map(JunctionMovement::isUTurn)
                        .orElse(false);
        releaseOccupiedRoad(roadNetwork);
        roadNetwork.recordVehicleEnteringRoad(step.getRoadId());
        placement.enter(
                step.getRoadId(),
                step.getFromIntersectionId(),
                step.getToIntersectionId(),
                entryLane,
                entryLaneCentreMetres);
        trafficState.invalidateOccupancy(road.getId());
        if (completesUTurn) {
            trip.recordLegalUTurnCompleted();
            navigationState.markFollowingRoute();
        }
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
        RouteStep followingStep = routePlan.stepAt(1);
        if (followingStep == null) {
            return true;
        }

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
        String releasedRoadId = placement.getOccupiedRoadId();
        releaseOccupiedRoad(roadNetwork);
        if (trafficState != null) {
            trafficState.invalidateOccupancy(releasedRoadId);
        }
    }

    public void recomputeRoute(RoutingService routingService) {
        requestRoute(routingService);
    }

    public void recomputeRoute(RoadNetwork roadNetwork, RoutingService routingService) {
        recomputeRoute(roadNetwork, routingService, routePlan.getPreference());
    }

    public void recomputeRoute(
            RoadNetwork roadNetwork,
            RoutingService routingService,
            RoutePreference preference) {
        trip.recordRouteRecalculation();
        if (placement.isOnRoad()) {
            replaceOnRoadRoute(roadNetwork, routingService, preference);
        } else {
            requestRoute(roadNetwork, routingService, preference);
        }
        if (routePlan.getLastAlgorithmName() != null) {
            routingService.recordRouteRecalculation(routePlan.getLastAlgorithmName());
        }
    }

    private void replaceOnRoadRoute(
            RoadNetwork roadNetwork,
            RoutingService routingService,
            RoutePreference preference) {
        RouteAnchor anchor = getRouteAnchor();
        if (!(anchor instanceof RouteAnchor.OnRoad onRoad)) {
            requestRoute(roadNetwork, routingService, preference);
            return;
        }
        long sourceVersion = routePlan.getVersion();
        try {
            RouteResult result = routingService.findRoute(
                    roadNetwork,
                    new RouteRequest(
                            onRoad.exitJunctionId(),
                            destinationIntersectionId,
                            preference));
            if (result == null || result.getRoute() == null) {
                status = VehicleStatus.BLOCKED;
                return;
            }
            Route candidateRoute = result.getRoute();
            Optional<JunctionMovement> firstMovement = candidateRoute.isEmpty()
                    ? Optional.empty()
                    : roadNetwork.getMovementGraph().find(
                            roadNetwork,
                            onRoad.exitJunctionId(),
                            onRoad.roadId(),
                            candidateRoute.getSteps().get(0).getRoadId());
            RouteCandidate candidate = new RouteCandidate(
                    candidateRoute,
                    RouteCost.from(candidateRoute),
                    firstMovement,
                    RouteCandidate.fingerprintOf(candidateRoute),
                    result.getAlgorithmName(),
                    sourceVersion);
            RouteReplacementResult replacement = replaceRemainingRoute(
                    anchor, candidate, roadNetwork);
            if (!replacement.replaced()) {
                status = VehicleStatus.BLOCKED;
            } else if (status == VehicleStatus.BLOCKED) {
                status = VehicleStatus.WAITING;
            }
        } catch (NoSuchElementException exception) {
            status = VehicleStatus.BLOCKED;
        }
    }

    public RouteAnchor getRouteAnchor() {
        if (placement.isOnRoad()) {
            return new RouteAnchor.OnRoad(
                    placement.getOccupiedRoadId(),
                    placement.getProgress(),
                    placement.getEndIntersectionId());
        }
        RouteStep previous = routePlan.stepAt(-1);
        return new RouteAnchor.AtJunction(
                currentIntersectionId,
                Optional.ofNullable(previous == null ? null : previous.getRoadId()));
    }

    public RouteReplacementResult replaceRemainingRoute(
            RouteAnchor expectedAnchor,
            RouteCandidate candidate,
            RoadNetwork network) {
        if (!getRouteAnchor().equals(expectedAnchor)) {
            return new RouteReplacementResult(
                    RouteReplacementResult.Status.REJECTED_ANCHOR,
                    "Vehicle moved after the route request was created.",
                    routePlan.getVersion());
        }
        return routePlan.replaceRemaining(
                expectedAnchor,
                candidate,
                network,
                network.getMovementGraph(),
                getType(),
                destinationIntersectionId);
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

    public String getOriginBuildingId() {
        return originBuildingId;
    }

    public String getDestinationBuildingId() {
        return destinationBuildingId;
    }

    /**
     * Attaches a fully resolved multi-leg plan. Routing still uses the existing
     * graph service, but it targets the exact access point chosen by demand.
     */
    public void assignTripPlan(TripPlan plan) {
        if (plan == null) throw new IllegalArgumentException("Trip plan is required");
        if (!plan.origin().accessIntersectionId().equals(currentIntersectionId)) {
            throw new IllegalArgumentException(
                    "Trip plan origin does not match vehicle current intersection");
        }
        this.tripPlan = plan;
        this.currentTripLegIndex = 0;
        this.originResolvedPlace = plan.origin();
        this.currentResolvedPlace = plan.origin();
        this.dwellUntilSeconds = Double.NaN;
        clearAccessLink();
        this.currentLegDepartureAccessComplete = plan.origin().kind() != PlaceKind.BUILDING;
        this.currentLegStopProcessed = false;
        this.originBuildingId = plan.origin().kind() == PlaceKind.BUILDING
                ? plan.origin().id() : null;
        applyDestination(plan.legs().get(0).to());
        this.status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.WAITING;
    }

    /**
     * Advances an arrived planned vehicle into dwell or its next leg.
     *
     * @return true only when the final leg of the whole journey is complete
     */
    public boolean updateTripPlanLifecycle(
            double elapsedSeconds,
            RoadNetwork roadNetwork,
            RoutingService routingService) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Elapsed seconds must be finite and non-negative");
        }
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        Objects.requireNonNull(routingService, "Routing service must not be null.");

        if (accessLinkPhase != null) return false;

        if (!currentLegDepartureAccessComplete) {
            if (beginBuildingAccess(
                    JourneyPhase.DEPARTING_ORIGIN,
                    originResolvedPlace,
                    roadNetwork)) {
                return false;
            }
            currentLegDepartureAccessComplete = true;
        }

        if (status == VehicleStatus.DWELLING) {
            if (elapsedSeconds + 1e-9 < dwellUntilSeconds) return false;
            dwellUntilSeconds = Double.NaN;
            if (tripPlan == null || currentTripLegIndex == tripPlan.legs().size() - 1) {
                status = VehicleStatus.ARRIVED;
                return true;
            }
            return beginNextTripLeg(elapsedSeconds, roadNetwork, routingService);
        }
        if (!hasReachedDestination()) return false;

        if (!destinationResolvedPlace.equals(currentResolvedPlace)) {
            if (beginBuildingAccess(
                    JourneyPhase.ARRIVING_DESTINATION,
                    destinationResolvedPlace,
                    roadNetwork)) {
                return false;
            }
            currentResolvedPlace = destinationResolvedPlace;
        }

        if (currentLegStopProcessed) {
            return status == VehicleStatus.ARRIVED
                    && (tripPlan == null
                    || currentTripLegIndex == tripPlan.legs().size() - 1);
        }
        currentLegStopProcessed = true;

        if (tripPlan == null) {
            instantaneousSpeedMps = 0;
            status = VehicleStatus.ARRIVED;
            return true;
        }

        double dwellSeconds = tripPlan.legs().get(currentTripLegIndex).dwellSeconds();
        if (dwellSeconds > 0) {
            releaseRoad(roadNetwork);
            instantaneousSpeedMps = 0;
            dwellUntilSeconds = elapsedSeconds + dwellSeconds;
            status = VehicleStatus.DWELLING;
            return false;
        }
        if (currentTripLegIndex == tripPlan.legs().size() - 1) {
            instantaneousSpeedMps = 0;
            status = VehicleStatus.ARRIVED;
            return true;
        }
        return beginNextTripLeg(elapsedSeconds, roadNetwork, routingService);
    }

    private boolean beginNextTripLeg(
            double elapsedSeconds,
            RoadNetwork roadNetwork,
            RoutingService routingService) {
        originResolvedPlace = currentResolvedPlace;
        originBuildingId = originResolvedPlace.kind() == PlaceKind.BUILDING
                ? originResolvedPlace.id() : null;
        currentLegDepartureAccessComplete =
                originResolvedPlace.kind() != PlaceKind.BUILDING;
        currentLegStopProcessed = false;
        currentTripLegIndex++;
        if (currentTripLegIndex >= tripPlan.legs().size()) {
            status = VehicleStatus.ARRIVED;
            return true;
        }
        applyDestination(tripPlan.legs().get(currentTripLegIndex).to());
        routePlan.clear();
        status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.WAITING;
        requestRoute(roadNetwork, routingService);
        // Consecutive resolved places can share one access node. Consume those
        // zero-length legs immediately without creating a fake road movement.
        if (hasReachedDestination()) {
            return updateTripPlanLifecycle(elapsedSeconds, roadNetwork, routingService);
        }
        return false;
    }

    /**
     * Advances the authoritative off-network building link without occupying a
     * road. Travel time is accumulated by the tick processor exactly once; this
     * method records only the physical driveway distance.
     */
    public void advanceBuildingAccessLink(
            double simulationSeconds,
            double elapsedSeconds,
            RoadNetwork roadNetwork,
            RoutingService routingService) {
        if (!isTraversingBuildingAccessLink()) return;
        if (!Double.isFinite(simulationSeconds) || simulationSeconds <= 0) {
            throw new IllegalArgumentException("Simulation seconds must be positive.");
        }
        double remaining = Math.max(0.0, accessLinkDistanceMetres - accessLinkTravelledMetres);
        double travelled = Math.min(
                remaining, BUILDING_ACCESS_SPEED_MPS * simulationSeconds);
        accessLinkTravelledMetres += travelled;
        trip.addDistanceKm(travelled / 1_000.0);
        instantaneousSpeedMps = remaining > 1e-9 ? BUILDING_ACCESS_SPEED_MPS : 0.0;
        if (accessLinkTravelledMetres + 1e-9 < accessLinkDistanceMetres) return;

        JourneyPhase completedPhase = accessLinkPhase;
        clearAccessLink();
        instantaneousSpeedMps = 0.0;
        if (completedPhase == JourneyPhase.DEPARTING_ORIGIN) {
            currentLegDepartureAccessComplete = true;
            status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.WAITING;
        } else {
            currentResolvedPlace = destinationResolvedPlace;
            status = VehicleStatus.ARRIVED;
        }
        updateTripPlanLifecycle(elapsedSeconds, roadNetwork, routingService);
    }

    private boolean beginBuildingAccess(
            JourneyPhase phase,
            ResolvedPlace place,
            RoadNetwork roadNetwork) {
        if (place == null || place.kind() != PlaceKind.BUILDING) return false;
        double distanceMetres = drivewayDistanceMetres(place, roadNetwork);
        if (distanceMetres <= 1e-9) return false;
        releaseRoad(roadNetwork);
        accessLinkPhase = phase;
        accessLinkDistanceMetres = distanceMetres;
        accessLinkTravelledMetres = 0.0;
        instantaneousSpeedMps = BUILDING_ACCESS_SPEED_MPS;
        status = VehicleStatus.MOVING;
        return true;
    }

    private static double drivewayDistanceMetres(
            ResolvedPlace place,
            RoadNetwork roadNetwork) {
        Building building = roadNetwork.getBuilding(place.id());
        if (building == null) {
            throw new IllegalStateException("Resolved building is missing: " + place.id());
        }
        return building.getAccessPoints().stream()
                .filter(access -> access.intersectionId().equals(place.accessIntersectionId()))
                .mapToDouble(BuildingAccessPoint::drivewayDistanceMetres)
                .min()
                .orElseThrow(() -> new IllegalStateException(
                        "Building " + place.id() + " has no access at "
                                + place.accessIntersectionId()));
    }

    private void clearAccessLink() {
        accessLinkPhase = null;
        accessLinkDistanceMetres = 0.0;
        accessLinkTravelledMetres = 0.0;
    }

    private void applyDestination(ResolvedPlace place) {
        destinationResolvedPlace = place;
        destinationIntersectionId = place.accessIntersectionId();
        destinationBuildingId = place.kind() == PlaceKind.BUILDING ? place.id() : null;
    }

    public TripPlan getTripPlan() {
        return tripPlan;
    }

    public int getCurrentTripLegIndex() {
        return currentTripLegIndex;
    }

    public ResolvedPlace getCurrentResolvedPlace() {
        return currentResolvedPlace;
    }

    public double getDwellUntilSeconds() {
        return dwellUntilSeconds;
    }

    public boolean hasActiveTripPlan() {
        return tripPlan != null && !isJourneyComplete();
    }

    public boolean isJourneyComplete() {
        if (tripPlan == null) {
            if (destinationResolvedPlace.kind() != PlaceKind.BUILDING) {
                return hasReachedDestination();
            }
            return hasReachedDestination()
                    && accessLinkPhase == null
                    && destinationResolvedPlace.equals(currentResolvedPlace)
                    && status == VehicleStatus.ARRIVED;
        }
        return currentTripLegIndex == tripPlan.legs().size() - 1
                && hasReachedDestination()
                && accessLinkPhase == null
                && destinationResolvedPlace.equals(currentResolvedPlace)
                && status == VehicleStatus.ARRIVED;
    }

    /** Marks this vehicle as a building-to-building trip without breaking legacy intersection trips. */
    public void assignBuildingTrip(String originBuildingId, String destinationBuildingId) {
        if (originBuildingId == null || originBuildingId.isBlank()) {
            throw new IllegalArgumentException("Origin building id is required");
        }
        if (destinationBuildingId == null || destinationBuildingId.isBlank()) {
            throw new IllegalArgumentException("Destination building id is required");
        }
        this.originBuildingId = originBuildingId;
        this.destinationBuildingId = destinationBuildingId;
        this.originResolvedPlace = ResolvedPlace.building(
                originBuildingId, currentIntersectionId);
        this.destinationResolvedPlace = ResolvedPlace.building(
                destinationBuildingId, destinationIntersectionId);
        this.currentResolvedPlace = originResolvedPlace;
        clearAccessLink();
        this.currentLegDepartureAccessComplete = false;
        this.currentLegStopProcessed = false;
    }

    public ResolvedPlace getOriginResolvedPlace() {
        return originResolvedPlace;
    }

    public ResolvedPlace getDestinationResolvedPlace() {
        return destinationResolvedPlace;
    }

    /**
     * Renderer-facing phase derived from authoritative placement and lifecycle
     * state; it does not introduce a second movement state machine.
     */
    public JourneyPhase getJourneyPhase() {
        if (accessLinkPhase != null) {
            return accessLinkPhase;
        }
        if (status == VehicleStatus.DWELLING) {
            return JourneyPhase.DWELLING;
        }
        if (status == VehicleStatus.ARRIVED && isJourneyComplete()) {
            return JourneyPhase.ARRIVED;
        }
        if (placement.isOnRoad()) {
            return JourneyPhase.ON_ROAD;
        }
        if (!currentLegDepartureAccessComplete
                && routePlan.getNextStepIndex() == 0
                && currentIntersectionId.equals(originResolvedPlace.accessIntersectionId())) {
            return JourneyPhase.DEPARTING_ORIGIN;
        }
        return JourneyPhase.AT_JUNCTION;
    }

    public boolean isTraversingBuildingAccessLink() {
        return accessLinkPhase == JourneyPhase.DEPARTING_ORIGIN
                || accessLinkPhase == JourneyPhase.ARRIVING_DESTINATION;
    }

    public double getAccessLinkProgress() {
        if (accessLinkDistanceMetres <= 0) {
            return currentLegDepartureAccessComplete ? 1.0 : 0.0;
        }
        return Math.min(1.0, accessLinkTravelledMetres / accessLinkDistanceMetres);
    }

    public double getAccessLinkDistanceMetres() {
        return accessLinkDistanceMetres;
    }

    public Route getCurrentRoute() {
        return routePlan.getRoute();
    }

    public void setCurrentRoute(Route currentRoute) {
        routePlan.assign(currentRoute, routePlan.getLastAlgorithmName());
        if (hasReachedDestination()) {
            this.status = VehicleStatus.ARRIVED;
        } else {
            this.status = isUsableRoute(currentRoute) ? VehicleStatus.WAITING : VehicleStatus.BLOCKED;
        }
    }

    public int getNextRouteStepIndex() {
        return routePlan.getNextStepIndex();
    }

    public RoutePreference getRoutePreference() {
        return routePlan.getPreference();
    }

    public String getOccupiedRoadId() {
        return placement.getOccupiedRoadId();
    }

    public void releaseRoad(RoadNetwork roadNetwork) {
        releaseOccupiedRoad(roadNetwork);
    }

    public String getRoadStartIntersectionId() {
        return placement.getStartIntersectionId();
    }

    public String getRoadEndIntersectionId() {
        return placement.getEndIntersectionId();
    }

    public double getRoadProgress() {
        return placement.getProgress();
    }

    @Override
    public int getLaneIndex() {
        return placement.getLaneIndex();
    }

    /**
     * The lane held on the current road, or on the one just left while the
     * vehicle waits at a junction. See {@link RoadPlacement#getLastLaneIndex()}.
     */
    public int getLastLaneIndex() {
        return placement.getLastLaneIndex();
    }

    /**
     * Where the vehicle sits across the road in metres, signed from the road
     * centreline. Equal to its lane's centre except while a lane change is in
     * progress, when it reports the intermediate position.
     */
    @Override
    public double getLateralOffsetMetres() {
        return placement.getLateralOffsetMetres();
    }

    public String getNextRoadId() {
        RouteStep step = getNextRouteStep();
        return step == null ? null : step.getRoadId();
    }

    /** Road after the occupied/current route step, if one remains. */
    public String getFollowingRoadId() {
        RouteStep step = routePlan.stepAt(placement.isOnRoad() ? 1 : 0);
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
    public double getWaitingSeconds() { return trip.getWaitingSeconds(); }
    public int getRouteRecalculationCount() { return trip.getRouteRecalculationCount(); }
    public int getLegalUTurnsCompleted() { return trip.getLegalUTurnsCompleted(); }
    public long getRouteVersion() { return routePlan.getVersion(); }
    public WaitState getWaitState() { return waitState; }
    public DriverKnowledge getDriverKnowledge() { return driverKnowledge; }
    public NavigationState getNavigationState() { return navigationState; }
    public StallState getStallState() { return stallTracker.snapshot(); }
    public ProgressStallTracker getStallTracker() { return stallTracker; }

    public void configureStallTracking(
            double minimumProgressMetres,
            java.util.List<Double> retryBackoffSeconds) {
        if (retryBackoffSeconds == null || retryBackoffSeconds.isEmpty()) {
            throw new IllegalArgumentException("Retry backoff is required.");
        }
        stallTracker = new ProgressStallTracker(
                minimumProgressMetres,
                retryBackoffSeconds.stream().mapToDouble(Double::doubleValue).toArray());
    }
    public Optional<LocalPerception> getLocalPerception() {
        return Optional.ofNullable(localPerception);
    }

    public void recordLocalPerception(LocalPerception perception) {
        if (perception == null) {
            throw new IllegalArgumentException("Local perception is required.");
        }
        this.localPerception = perception;
    }

    public void requestLaneChangeIntent(LaneChangeIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("Lane-change intent is required.");
        }
        pendingLaneChangeIntent = intent;
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
     * Speed this vehicle aims to travel at, in kilometres per hour.
     *
     * <p>Not the speed it is doing: that is
     * {@link #getInstantaneousSpeedMps()}, which the car-following model
     * rewrites every sub-step. This is a preference set when the vehicle is
     * created and changed only by {@link #accelerate} and {@link #decelerate},
     * so the two differ whenever traffic is holding the vehicle below what it
     * would like to be doing.</p>
     *
     * <p>It was called {@code currentSpeed}, which made it look like the second
     * of two copies of the same number that had drifted apart. They are
     * different quantities and are meant to differ.</p>
     */
    public double getCruisingSpeedKph() {
        return cruisingSpeedKph;
    }

    public void setCruisingSpeedKph(double cruisingSpeedKph) {
        profile.requireReachable(cruisingSpeedKph);
        this.cruisingSpeedKph = cruisingSpeedKph;
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
        setCruisingSpeedKph(Math.min(profile.maxSpeedKph(), cruisingSpeedKph + profile.accelerationMps2() * validateDuration(durationSeconds) * KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND));
    }

    /**
     * Reduces the current speed for a duration, floored at zero.
     * This operation is intentionally independent of the graph-based tick movement.
     */
    public void decelerate(double durationSeconds) {
        setCruisingSpeedKph(Math.max(0.0, cruisingSpeedKph - profile.decelerationMps2() * validateDuration(durationSeconds) * KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND));
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
        return routePlan.currentStep();
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

    private void releaseOccupiedRoad(RoadNetwork roadNetwork) {
        if (roadNetwork == null || !placement.isOnRoad()) {
            return;
        }

        Road occupiedRoad = roadNetwork.getRoad(placement.getOccupiedRoadId());
        if (occupiedRoad != null && occupiedRoad.getCurrentVehicleCount() > 0) {
            roadNetwork.recordVehicleLeavingRoad(placement.getOccupiedRoadId());
        }
        placement.release();
    }
}
