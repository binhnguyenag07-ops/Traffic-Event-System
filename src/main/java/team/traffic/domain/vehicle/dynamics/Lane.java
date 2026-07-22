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
    private final Road road;
    private final int laneIndex;
    private final double roadLengthMetres;
    private final List<TrafficParticipant> vehicles;
    private final List<TrafficEvent> obstructions;

    Lane(Road road, int laneIndex, List<TrafficParticipant> vehicles, List<TrafficEvent> events) {
        this.road = road;
        this.laneIndex = laneIndex;
        this.roadLengthMetres = road.getDistance() * 1_000.0;
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

    public double positionMetresOf(TrafficParticipant vehicle) {
        return vehicle.getRoadProgress() * roadLengthMetres;
    }

    public double getOccupiedLengthMetres() {
        return vehicles.stream()
                .mapToDouble(vehicle -> vehicle.getDriverProfile().jamSpacingMetres(vehicle.getLength()))
                .sum();
    }

    public double getPhysicalJamRatio() {
        return roadLengthMetres <= 0 ? 1.0 : Math.min(1.0, getOccupiedLengthMetres() / roadLengthMetres);
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

    public boolean canAdmit(TrafficParticipant arriving) {
        if (arriving == null) return false;
        double required = arriving.getDriverProfile().jamSpacingMetres(arriving.getLength());
        return getOccupiedLengthMetres() + required <= roadLengthMetres
                && getEntryGapMetres() >= required;
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
