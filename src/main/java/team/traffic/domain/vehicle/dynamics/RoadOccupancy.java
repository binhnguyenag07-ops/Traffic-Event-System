package team.traffic.domain.vehicle.dynamics;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.Road;

/** Physical state of every lane on one directed road. */
public final class RoadOccupancy {
    private final Road road;
    private final List<Lane> lanes;
    private final double laneWidthMetres;

    static RoadOccupancy of(
            Road road,
            List<TrafficParticipant> vehicles,
            RoadLayout roadLayout,
            List<TrafficEvent> events) {
        if (road == null) throw new IllegalArgumentException("Road is required.");
        if (roadLayout == null) throw new IllegalArgumentException("Road layout is required.");
        int laneCount = roadLayout.laneCountOf(road);
        double laneWidthMetres = roadLayout.laneWidthMetresOf(road);
        List<Lane> lanes = new ArrayList<>(laneCount);
        for (int index = 0; index < laneCount; index++) {
            int laneIndex = index;
            List<TrafficParticipant> inLane = vehicles.stream()
                    .filter(vehicle -> vehicle.getLaneIndex() == laneIndex)
                    .toList();
            lanes.add(new Lane(road, index, laneWidthMetres, inLane, events));
        }
        return new RoadOccupancy(road, lanes, laneWidthMetres);
    }

    private RoadOccupancy(Road road, List<Lane> lanes, double laneWidthMetres) {
        this.road = road;
        this.lanes = List.copyOf(lanes);
        this.laneWidthMetres = laneWidthMetres;
    }

    public Road getRoad() { return road; }
    public int getLaneCount() { return lanes.size(); }
    public List<Lane> getLanes() { return lanes; }
    public double getLaneWidthMetres() { return laneWidthMetres; }

    /**
     * Signed offset of a lane's centre from the road centreline, in metres.
     *
     * <p>Carried here so a vehicle can steer towards its target lane without
     * having to be handed the road layout as well as its occupancy.</p>
     */
    public double laneCentreOffsetMetres(int laneIndex) {
        return RoadLayout.laneCentreOffsetMetres(laneIndex, lanes.size(), laneWidthMetres);
    }

    public Lane lane(int laneIndex) {
        if (laneIndex < 0 || laneIndex >= lanes.size()) return null;
        return lanes.get(laneIndex);
    }

    public Lane laneOf(TrafficParticipant vehicle) {
        if (vehicle == null) return null;
        Lane lane = lane(vehicle.getLaneIndex());
        return lane == null ? lanes.get(0) : lane;
    }

    public boolean canAdmit(TrafficParticipant arriving) {
        return lanes.stream().anyMatch(lane -> lane.canAdmit(arriving));
    }

    /** Lane with the largest clear entry gap, with index as deterministic tie-breaker. */
    public int bestLaneForEntry(TrafficParticipant arriving) {
        Lane best = null;
        for (Lane lane : lanes) {
            if (!lane.canAdmit(arriving)) continue;
            if (best == null || lane.getEntryGapMetres() > best.getEntryGapMetres()) best = lane;
        }
        return best == null ? -1 : best.getLaneIndex();
    }

    /**
     * Lane continuation for an approach that arrived in
     * {@code arrivingFromLaneIndex}, or {@code -1} while that continuation has
     * no entry space.
     *
     * <p>The index is clamped only when the outgoing road has fewer lanes. It is
     * never replaced by an arbitrary emptier lane: doing so made the simulation
     * teleport laterally inside the junction and forced the renderer to invent a
     * connector for a movement the lane topology never described. A vehicle
     * waits for this continuation and may change lanes gradually after entering
     * the road.</p>
     */
    public int continuationLaneForEntry(
            TrafficParticipant arriving,
            int arrivingFromLaneIndex) {
        if (arriving == null || lanes.isEmpty()) {
            return -1;
        }
        int continuationIndex = Math.max(
                0, Math.min(arrivingFromLaneIndex, lanes.size() - 1));
        Lane continuation = lane(continuationIndex);
        return continuation != null && continuation.canAdmit(arriving)
                ? continuationIndex
                : -1;
    }

    public boolean canAdmitFromLane(
            TrafficParticipant arriving,
            int arrivingFromLaneIndex) {
        return continuationLaneForEntry(arriving, arrivingFromLaneIndex) >= 0;
    }

    /** The strip of road a whole lane occupies, used to place obstructions. */
    public LateralExtent laneExtent(int laneIndex) {
        return new LateralExtent(laneCentreOffsetMetres(laneIndex), laneWidthMetres);
    }

    /**
     * Nearest thing ahead that is actually in the way of {@code follower},
     * decided by lateral overlap rather than by a shared lane index.
     *
     * <p>This is what lets a narrow vehicle ride past traffic it has room
     * beside. It scans every lane because a filtering motorcycle straddles two
     * of them and is obstructed by neither unless one of their occupants
     * reaches across the line it is riding.</p>
     *
     * <p>For a vehicle sitting squarely on a lane centre the answer is the same
     * one {@link Lane#findLeaderFor} gives, because only that lane's occupants
     * can overlap it. {@code SubLaneLeaderEquivalenceTest} holds that claim.</p>
     */
    public LeaderGap findLeaderFor(TrafficParticipant follower, double lateralClearanceMetres) {
        if (follower == null) return LeaderGap.FREE_ROAD;
        return findLeaderAt(follower, follower.lateralExtent(), lateralClearanceMetres);
    }

    /** Nearest laterally-overlapping vehicle ahead, excluding event obstructions. */
    public TrafficParticipant nearestOverlappingLeader(
            TrafficParticipant follower,
            double lateralClearanceMetres) {
        if (follower == null) return null;
        double followerPosition = positionMetresOf(follower);
        TrafficParticipant nearest = null;
        double nearestPosition = Double.POSITIVE_INFINITY;
        for (Lane lane : lanes) {
            for (TrafficParticipant candidate : lane.getVehicles()) {
                if (candidate == follower || candidate.getId().equals(follower.getId())) continue;
                if (!follower.lateralExtent().overlaps(
                        candidate.lateralExtent(), lateralClearanceMetres)) continue;
                double candidatePosition = positionMetresOf(candidate);
                if (LongitudinalOrder.isAhead(
                                candidate, candidatePosition, follower, followerPosition)
                        && candidatePosition < nearestPosition) {
                    nearest = candidate;
                    nearestPosition = candidatePosition;
                }
            }
        }
        return nearest;
    }

    /**
     * The leader the follower would have if it were riding at
     * {@code offsetMetres}, used to judge a corridor before committing to it.
     */
    public LeaderGap findLeaderAtOffset(
            TrafficParticipant follower, double offsetMetres, double lateralClearanceMetres) {
        if (follower == null) return LeaderGap.FREE_ROAD;
        return findLeaderAt(
                follower, new LateralExtent(offsetMetres, follower.getWidth()), lateralClearanceMetres);
    }

    private LeaderGap findLeaderAt(
            TrafficParticipant follower, LateralExtent followerExtent, double lateralClearanceMetres) {
        double followerPosition = positionMetresOf(follower);
        double closestPosition = Double.POSITIVE_INFINITY;
        double closestLength = 0.0;
        double closestSpeed = 0.0;

        for (Lane lane : lanes) {
            for (TrafficParticipant candidate : lane.getVehicles()) {
                if (candidate == follower || candidate.getId().equals(follower.getId())) continue;
                if (!followerExtent.overlaps(candidate.lateralExtent(), lateralClearanceMetres)) continue;
                double position = positionMetresOf(candidate);
                if (LongitudinalOrder.isAhead(
                                candidate, position, follower, followerPosition)
                        && position < closestPosition) {
                    closestPosition = position;
                    closestLength = candidate.getLength();
                    closestSpeed = candidate.getInstantaneousSpeedMps();
                }
            }
            LateralExtent blocked = laneExtent(lane.getLaneIndex());
            if (!followerExtent.overlaps(blocked, 0.0)) continue;
            for (TrafficEvent obstruction : lane.getObstructions()) {
                double position = obstruction.getPositionAlongRoad() * roadLengthMetres();
                if (position >= followerPosition && position < closestPosition) {
                    closestPosition = position;
                    closestLength = 0.0;
                    closestSpeed = 0.0;
                }
            }
        }

        if (Double.isInfinite(closestPosition)) return LeaderGap.FREE_ROAD;
        double bumperGap = closestPosition - closestLength - followerPosition;
        return new LeaderGap(
                bumperGap - follower.getDriverProfile().minimumGapMetres(),
                closestSpeed);
    }

    /** Whether anything laterally overlaps a vehicle of this width at {@code centreMetres}. */
    public boolean isLaterallyClear(
            TrafficParticipant mover,
            double centreMetres,
            double lateralClearanceMetres,
            double longitudinalWindowMetres) {
        if (mover == null) return false;
        LateralExtent candidate = new LateralExtent(centreMetres, mover.getWidth());
        double moverPosition = positionMetresOf(mover);
        for (Lane lane : lanes) {
            for (TrafficParticipant other : lane.getVehicles()) {
                if (other == mover || other.getId().equals(mover.getId())) continue;
                if (Math.abs(positionMetresOf(other) - moverPosition) > longitudinalWindowMetres) continue;
                if (candidate.overlaps(other.lateralExtent(), lateralClearanceMetres)) return false;
            }
        }
        return withinRoad(candidate);
    }

    /**
     * Whether anything alongside is itself mid-manoeuvre.
     *
     * <p>A gap beside a vehicle that is still crossing a lane line is not a gap
     * worth riding into: it is about to close. Detecting that is only possible
     * because lateral position is continuous, so a vehicle between lane centres
     * is visibly between them.</p>
     */
    public boolean hasManoeuvringNeighbour(TrafficParticipant mover, double longitudinalWindowMetres) {
        if (mover == null) return false;
        double moverPosition = positionMetresOf(mover);
        for (Lane lane : lanes) {
            for (TrafficParticipant other : lane.getVehicles()) {
                if (other == mover || other.getId().equals(mover.getId())) continue;
                if (Math.abs(positionMetresOf(other) - moverPosition) > longitudinalWindowMetres) continue;
                double ownLaneCentre = laneCentreOffsetMetres(other.getLaneIndex());
                if (!LateralMotionModel.hasSettled(other.getLateralOffsetMetres(), ownLaneCentre)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Slowest vehicle alongside {@code mover}, or its own speed when it is alone. */
    public double slowestNeighbourSpeedMps(
            TrafficParticipant mover, double longitudinalWindowMetres) {
        if (mover == null) return 0.0;
        double moverPosition = positionMetresOf(mover);
        double slowest = Double.POSITIVE_INFINITY;
        for (Lane lane : lanes) {
            for (TrafficParticipant other : lane.getVehicles()) {
                if (other == mover || other.getId().equals(mover.getId())) continue;
                if (Math.abs(positionMetresOf(other) - moverPosition) > longitudinalWindowMetres) continue;
                slowest = Math.min(slowest, other.getInstantaneousSpeedMps());
            }
        }
        return Double.isInfinite(slowest) ? mover.getInstantaneousSpeedMps() : slowest;
    }

    public double roadLengthMetres() {
        return road.getDistance() * 1_000.0;
    }

    private boolean withinRoad(LateralExtent extent) {
        double halfRoad = lanes.size() * laneWidthMetres / 2.0;
        return extent.lowerEdgeMetres() >= -halfRoad && extent.upperEdgeMetres() <= halfRoad;
    }

    private double positionMetresOf(TrafficParticipant participant) {
        return participant.getRoadProgress() * roadLengthMetres();
    }

    public double getPhysicalJamRatio() {
        return lanes.stream().mapToDouble(Lane::getPhysicalJamRatio).average().orElse(0.0);
    }

    public int getVehicleCount() {
        return lanes.stream().mapToInt(Lane::getVehicleCount).sum();
    }

    public List<TrafficParticipant> getVehicles() {
        return lanes.stream().flatMap(lane -> lane.getVehicles().stream()).toList();
    }
}
