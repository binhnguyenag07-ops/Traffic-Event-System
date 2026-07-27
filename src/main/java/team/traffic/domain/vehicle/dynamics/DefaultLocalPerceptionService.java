package team.traffic.domain.vehicle.dynamics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.Road;
import team.traffic.domain.signal.TrafficSignal;
import team.traffic.domain.vehicle.Vehicle;

/** Builds local snapshots from the already-indexed authoritative traffic state. */
public final class DefaultLocalPerceptionService implements LocalPerceptionService {
    public static final double DEFAULT_MINIMUM_SIGHT_METRES = 30.0;
    public static final double DEFAULT_SAFETY_MARGIN_METRES = 5.0;

    @Override
    public LocalPerception observe(Vehicle vehicle, TrafficState state) {
        if (vehicle == null || state == null) {
            throw new IllegalArgumentException("Vehicle and traffic state are required.");
        }
        double horizon = sensorHorizon(vehicle);
        VehicleLocation location = new VehicleLocation(
                Optional.ofNullable(vehicle.getOccupiedRoadId()),
                vehicle.getOccupiedRoadId() == null
                        ? Optional.of(vehicle.getCurrentIntersectionId())
                        : Optional.empty(),
                vehicle.getRoadProgress(),
                vehicle.getLaneIndex(),
                vehicle.getLateralOffsetMetres());
        if (vehicle.getOccupiedRoadId() == null) {
            return new LocalPerception(
                    state.getElapsedSeconds(),
                    horizon,
                    location,
                    Optional.empty(),
                    List.of(),
                    Optional.empty(),
                    junctionAtDeparture(vehicle, state),
                    ExitReachability.UNKNOWN);
        }

        Road road = state.getNetwork().getRoad(vehicle.getOccupiedRoadId());
        RoadOccupancy occupancy = state.occupancyOf(vehicle.getOccupiedRoadId());
        if (road == null || occupancy == null) {
            return new LocalPerception(
                    state.getElapsedSeconds(), horizon, location,
                    Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
                    ExitReachability.UNKNOWN);
        }

        Optional<LeaderObservation> leader = leader(vehicle, occupancy, horizon);
        List<LaneObservation> lanes = lanes(vehicle, occupancy, horizon);
        Optional<SignalObservation> signal = signal(vehicle, road, state, horizon);
        Optional<JunctionObservation> junction = junction(vehicle, road, state, horizon);
        ExitReachability reachability = reachability(vehicle, state, occupancy, lanes);
        return new LocalPerception(
                state.getElapsedSeconds(),
                horizon,
                location,
                leader,
                lanes,
                signal,
                junction,
                reachability);
    }

    private double sensorHorizon(Vehicle vehicle) {
        double speed = vehicle.getInstantaneousSpeedMps();
        double reaction = speed * vehicle.getDriverProfile().reactionTimeSeconds();
        double braking = speed * speed / (2.0 * vehicle.getDecelerationMps2());
        return Math.max(
                DEFAULT_MINIMUM_SIGHT_METRES,
                reaction + braking + DEFAULT_SAFETY_MARGIN_METRES);
    }

    private Optional<LeaderObservation> leader(
            Vehicle vehicle,
            RoadOccupancy occupancy,
            double horizon) {
        TrafficParticipant leader = occupancy.nearestOverlappingLeader(
                vehicle, vehicle.getDriverProfile().minimumGapMetres() * 0.1);
        if (leader == null) return Optional.empty();
        double roadLength = occupancy.roadLengthMetres();
        double rawGap = (leader.getRoadProgress() - vehicle.getRoadProgress()) * roadLength
                - leader.getLength();
        if (rawGap < 0 || rawGap > horizon) return Optional.empty();
        return Optional.of(new LeaderObservation(
                leader.getId(),
                rawGap,
                leader.getInstantaneousSpeedMps(),
                vehicle.getInstantaneousSpeedMps() - leader.getInstantaneousSpeedMps(),
                leader.getInstantaneousSpeedMps() < 0.1));
    }

    private List<LaneObservation> lanes(
            Vehicle vehicle,
            RoadOccupancy occupancy,
            double horizon) {
        List<LaneObservation> result = new ArrayList<>();
        int lower = Math.max(0, vehicle.getLaneIndex() - 1);
        int upper = Math.min(occupancy.getLaneCount() - 1, vehicle.getLaneIndex() + 1);
        for (int laneIndex = lower; laneIndex <= upper; laneIndex++) {
            Lane lane = occupancy.lane(laneIndex);
            LeaderGap forward = lane.findLeaderFor(vehicle);
            TrafficParticipant follower = lane.findFollowerBehind(vehicle);
            double rear = follower == null
                    ? Double.POSITIVE_INFINITY
                    : Math.max(0.0,
                            lane.positionMetresOf(vehicle)
                                    - lane.positionMetresOf(follower)
                                    - vehicle.getLength());
            Optional<ObstructionObservation> obstruction = lane.getObstructions().stream()
                    .filter(event -> event.getPositionAlongRoad()
                                    * lane.getRoadLengthMetres()
                            >= lane.positionMetresOf(vehicle))
                    .map(event -> obstruction(event, lane, vehicle))
                    .filter(observation -> observation.distanceAheadMetres() <= horizon)
                    .min(Comparator.comparingDouble(ObstructionObservation::distanceAheadMetres));
            double meanSpeed = lane.getVehicles().stream()
                    .filter(other -> Math.abs(
                            lane.positionMetresOf(other) - lane.positionMetresOf(vehicle)) <= horizon)
                    .mapToDouble(TrafficParticipant::getInstantaneousSpeedMps)
                    .average()
                    .orElse(vehicle.getInstantaneousSpeedMps());
            result.add(new LaneObservation(
                    laneIndex,
                    occupancy.laneCentreOffsetMetres(laneIndex),
                    Math.min(horizon, forward.gapMetres()),
                    Math.min(horizon, rear),
                    obstruction,
                    meanSpeed,
                    true));
        }
        return List.copyOf(result);
    }

    private ObstructionObservation obstruction(
            TrafficEvent event,
            Lane lane,
            Vehicle vehicle) {
        double distance = event.getPositionAlongRoad() * lane.getRoadLengthMetres()
                - lane.positionMetresOf(vehicle);
        boolean seals = event.getBlockedLaneIndexes().isEmpty()
                || event.getBlockedLaneIndexes().contains(lane.getLaneIndex());
        return new ObstructionObservation(
                event.getId(), Math.max(0.0, distance), lane.getLaneIndex(), seals);
    }

    private Optional<SignalObservation> signal(
            Vehicle vehicle,
            Road road,
            TrafficState state,
            double horizon) {
        double distance = (1.0 - vehicle.getRoadProgress()) * road.getDistance() * 1_000.0;
        if (distance > horizon) return Optional.empty();
        Optional<TrafficSignal> signal = state.signalAt(road.getEndIntersectionId());
        if (signal.isEmpty()) return Optional.empty();
        double untilGreen = signal.get().secondsUntilGreenFor(
                road.getId(), state.getElapsedSeconds());
        return Optional.of(new SignalObservation(
                signal.get().isGreenFor(road.getId(), state.getElapsedSeconds())
                        ? SignalObservation.Aspect.GREEN
                        : SignalObservation.Aspect.RED,
                Double.isFinite(untilGreen)
                        ? untilGreen
                        : signal.get().secondsUntilPhaseChange(
                                state.getElapsedSeconds()),
                distance));
    }

    private Optional<JunctionObservation> junction(
            Vehicle vehicle,
            Road road,
            TrafficState state,
            double horizon) {
        double distance = (1.0 - vehicle.getRoadProgress()) * road.getDistance() * 1_000.0;
        if (distance > horizon) return Optional.empty();
        Optional<JunctionDecision> decision = state.junctionDecisionFor(vehicle.getId());
        boolean downstream = decision.map(value -> value.granted()
                        || value.denialReason() != JunctionDenialReason.DOWNSTREAM_FULL)
                .orElse(true);
        return Optional.of(new JunctionObservation(
                road.getEndIntersectionId(),
                decision.filter(value -> !value.granted()).map(JunctionDecision::denialReason),
                downstream));
    }

    private Optional<JunctionObservation> junctionAtDeparture(
            Vehicle vehicle,
            TrafficState state) {
        Optional<JunctionDecision> decision = state.junctionDecisionFor(vehicle.getId());
        return Optional.of(new JunctionObservation(
                vehicle.getCurrentIntersectionId(),
                decision.filter(value -> !value.granted()).map(JunctionDecision::denialReason),
                decision.map(JunctionDecision::granted).orElse(true)));
    }

    private ExitReachability reachability(
            Vehicle vehicle,
            TrafficState state,
            RoadOccupancy occupancy,
            List<LaneObservation> lanes) {
        LaneObservation current = lanes.stream()
                .filter(lane -> lane.laneIndex() == vehicle.getLaneIndex())
                .findFirst()
                .orElse(null);
        if (current != null && current.obstruction().isPresent()) {
            boolean adjacentOpen = lanes.stream()
                    .filter(lane -> lane.laneIndex() != vehicle.getLaneIndex())
                    .anyMatch(lane -> lane.obstruction().isEmpty()
                            && occupancy.isLaterallyClear(
                                    vehicle,
                                    lane.centreOffsetMetres(),
                                    vehicle.getDriverProfile().minimumGapMetres() * 0.1,
                                    vehicle.getLength() * 2.0));
            if (adjacentOpen) return ExitReachability.REACHABLE_AFTER_SAFE_LANE_CHANGE;
            boolean everyLaneSealed = lanes.stream().allMatch(lane -> lane.obstruction().isPresent());
            if (everyLaneSealed) return ExitReachability.SEALED_BEFORE_NEXT_MANOEUVRE;
        }
        String nextRoadId = vehicle.getFollowingRoadId();
        if (nextRoadId != null) {
            RoadOccupancy next = state.occupancyOf(nextRoadId);
            if (next != null && !next.canAdmit(vehicle)) {
                return ExitReachability.WAITING_FOR_TEMPORARY_SPACE;
            }
        }
        return ExitReachability.REACHABLE;
    }
}
