package team.traffic.domain.vehicle.dynamics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.BreakdownEvent;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.network.Road;

/** Physical occupancy and obstructions of one lane on one directed road. */
public final class Lane {
    /**
     * Room riders leave beside each other when queueing. Smaller than the
     * clearance kept while filtering past moving traffic, because a stationary
     * queue packs tighter than a manoeuvre does.
     */
    static final double QUEUE_LATERAL_CLEARANCE_METRES = 0.30;

    private final Road road;
    private final int laneIndex;
    private final double roadLengthMetres;
    private final double laneWidthMetres;
    private final List<TrafficParticipant> vehicles;
    private final List<TrafficEvent> obstructions;

    Lane(
            Road road,
            int laneIndex,
            double laneWidthMetres,
            List<TrafficParticipant> vehicles,
            List<TrafficEvent> events) {
        this.road = road;
        this.laneIndex = laneIndex;
        this.roadLengthMetres = road.getDistance() * 1_000.0;
        this.laneWidthMetres = laneWidthMetres;
        this.vehicles = new ArrayList<>(vehicles);
        this.vehicles.sort(Comparator.comparingDouble(TrafficParticipant::getRoadProgress)
                .thenComparing(TrafficParticipant::getId));
        this.obstructions = events == null ? List.of() : events.stream()
                .filter(event -> event.getStatus() == TrafficEventStatus.ACTIVE)
                .filter(event -> road.getId().equals(event.getAffectedRoadId()))
                .filter(Lane::createsPhysicalObstruction)
                .filter(this::blocksThisLane)
                .sorted(Comparator.comparingDouble(TrafficEvent::getPositionAlongRoad))
                .toList();
    }

    public Road getRoad() { return road; }
    public int getLaneIndex() { return laneIndex; }
    public double getRoadLengthMetres() { return roadLengthMetres; }
    public int getVehicleCount() { return vehicles.size(); }
    public List<TrafficParticipant> getVehicles() { return List.copyOf(vehicles); }

    /** Active events physically blocking this lane, ordered along the road. */
    public List<TrafficEvent> getObstructions() {
        return obstructions;
    }

    public double positionMetresOf(TrafficParticipant vehicle) {
        return vehicle.getRoadProgress() * roadLengthMetres;
    }

    /**
     * How many of this vehicle stand side by side across the lane.
     *
     * <p>A car needs 2.1 m of a 3.5 m lane once its mirrors and the room beside
     * them are counted, so only one fits and a second must queue behind. A
     * motorcycle needs 1.05 m, so three sit abreast and a row of three holds up
     * no more of the road than one car does.</p>
     */
    public int abreastCapacityOf(TrafficParticipant vehicle) {
        if (vehicle == null || laneWidthMetres <= 0) {
            return 1;
        }
        double requiredWidth = vehicle.getWidth() + QUEUE_LATERAL_CLEARANCE_METRES;
        return Math.max(1, (int) Math.floor(laneWidthMetres / requiredWidth));
    }

    /**
     * Road length this vehicle denies to others while queueing.
     *
     * <p>Its full standstill footprint divided between however many of it fit
     * abreast. Counting each vehicle's whole footprint regardless of width made
     * a lane of motorcycles look as congested as a lane of cars carrying three
     * times fewer riders.</p>
     */
    public double queueLengthContributionOf(TrafficParticipant vehicle) {
        return physicalQueueLengthOf(vehicle) / abreastCapacityOf(vehicle);
    }

    public double getOccupiedLengthMetres() {
        return vehicles.stream().mapToDouble(this::queueLengthContributionOf).sum();
    }

    public double getPhysicalJamRatio() {
        return roadLengthMetres <= 0 ? 1.0 : Math.min(1.0, getOccupiedLengthMetres() / roadLengthMetres);
    }

    private static double physicalQueueLengthOf(TrafficParticipant vehicle) {
        return vehicle.getDriverProfile().jamSpacingMetres(vehicle.getLength());
    }

    public double getEntryGapMetres() {
        double gap = roadLengthMetres;
        if (!vehicles.isEmpty()) {
            TrafficParticipant rearmost = vehicles.get(0);
            gap = Math.min(gap, Math.max(0.0, positionMetresOf(rearmost) - rearmost.getLength()));
        }
        if (!obstructions.isEmpty()) {
            gap = Math.min(gap, obstructions.get(0).getPositionAlongRoad() * roadLengthMetres);
        }
        return gap;
    }

    /**
     * Whether an arriving vehicle both fits in what is left of the lane and has
     * somewhere to stop once it is on it.
     *
     * <p>Two different lengths, deliberately. Storage is shared between
     * vehicles standing abreast, so a motorcycle takes a third of a lane row.
     * The gap at the entry is not shared: a motorcycle joining behind a queue
     * still needs its own body length there, whatever is beside it.</p>
     */
    public boolean canAdmit(TrafficParticipant arriving) {
        if (arriving == null) return false;
        return getOccupiedLengthMetres() + queueLengthContributionOf(arriving) <= roadLengthMetres
                && getEntryGapMetres() >= physicalQueueLengthOf(arriving);
    }

    public LeaderGap findLeaderFor(TrafficParticipant follower) {
        if (follower == null) return LeaderGap.FREE_ROAD;
        double followerPosition = positionMetresOf(follower);
        double closestPosition = Double.POSITIVE_INFINITY;
        double closestLength = 0.0;
        double closestSpeed = 0.0;
        for (TrafficParticipant candidate : vehicles) {
            if (candidate == follower || candidate.getId().equals(follower.getId())) continue;
            double position = positionMetresOf(candidate);
            if (position >= followerPosition && position < closestPosition) {
                closestPosition = position;
                closestLength = candidate.getLength();
                closestSpeed = candidate.getInstantaneousSpeedMps();
            }
        }
        for (TrafficEvent obstruction : obstructions) {
            double position = obstruction.getPositionAlongRoad() * roadLengthMetres;
            if (position >= followerPosition && position < closestPosition) {
                closestPosition = position;
                closestLength = 0.0;
                closestSpeed = 0.0;
            }
        }
        if (Double.isInfinite(closestPosition)) return LeaderGap.FREE_ROAD;
        double bumperGap = closestPosition - closestLength - followerPosition;
        return new LeaderGap(
                bumperGap - follower.getDriverProfile().minimumGapMetres(),
                closestSpeed);
    }

    /** Vehicle immediately behind the supplied position, used for lane-change safety. */
    public TrafficParticipant findFollowerBehind(TrafficParticipant participant) {
        if (participant == null) return null;
        double position = positionMetresOf(participant);
        TrafficParticipant follower = null;
        for (TrafficParticipant candidate : vehicles) {
            if (candidate.getId().equals(participant.getId())) continue;
            double candidatePosition = positionMetresOf(candidate);
            if (candidatePosition >= position) continue;
            if (follower == null || candidatePosition > positionMetresOf(follower)) follower = candidate;
        }
        return follower;
    }

    public double distanceToExitMetres(TrafficParticipant vehicle) {
        return Math.max(0.0, roadLengthMetres - positionMetresOf(vehicle));
    }

    private boolean blocksThisLane(TrafficEvent event) {
        return event.getBlockedLaneIndexes().isEmpty()
                || event.getBlockedLaneIndexes().contains(laneIndex);
    }

    private static boolean createsPhysicalObstruction(TrafficEvent event) {
        return event instanceof AccidentEvent || event instanceof RoadClosureEvent
                || event instanceof BreakdownEvent;
    }
}
