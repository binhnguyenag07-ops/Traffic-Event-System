package team.traffic.domain.event;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import java.util.Set;
import java.util.TreeSet;

public abstract class TrafficEvent {
    private final String id;
    private final String affectedRoadId;
    private final double startTimeSeconds;
    private final double durationSeconds;
    private final double positionAlongRoad;
    private final Set<Integer> blockedLaneIndexes;
    private TrafficEventStatus status;
    private RoadCondition previousRoadCondition;

    protected TrafficEvent(String id, String affectedRoadId, double startTimeSeconds, double durationSeconds) {
        this(id, affectedRoadId, startTimeSeconds, durationSeconds, 0.5, Set.of());
    }

    protected TrafficEvent(
            String id,
            String affectedRoadId,
            double startTimeSeconds,
            double durationSeconds,
            double positionAlongRoad,
            Set<Integer> blockedLaneIndexes) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Event id must not be blank.");
        }
        if (affectedRoadId == null || affectedRoadId.isBlank()) {
            throw new IllegalArgumentException("Affected road id must not be blank.");
        }
        if (!Double.isFinite(startTimeSeconds) || startTimeSeconds < 0) {
            throw new IllegalArgumentException("Start time must be finite and not negative.");
        }
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0) {
            throw new IllegalArgumentException("Duration must be finite and positive.");
        }
        if (!Double.isFinite(positionAlongRoad) || positionAlongRoad < 0 || positionAlongRoad > 1) {
            throw new IllegalArgumentException("Position along road must be between 0 and 1.");
        }
        TreeSet<Integer> lanes = new TreeSet<>();
        if (blockedLaneIndexes != null) {
            for (Integer laneIndex : blockedLaneIndexes) {
                if (laneIndex == null || laneIndex < 0) {
                    throw new IllegalArgumentException("Blocked lane indexes must not be negative.");
                }
                lanes.add(laneIndex);
            }
        }
        this.id = id;
        this.affectedRoadId = affectedRoadId;
        this.startTimeSeconds = startTimeSeconds;
        this.durationSeconds = durationSeconds;
        this.positionAlongRoad = positionAlongRoad;
        this.blockedLaneIndexes = Set.copyOf(lanes);
        this.status = TrafficEventStatus.SCHEDULED;
    }

    public abstract TrafficEventType getType();

    public void apply(RoadNetwork roadNetwork) {
        validateRoadNetwork(roadNetwork);
        this.status = TrafficEventStatus.ACTIVE;
    }

    public void resolve(RoadNetwork roadNetwork) {
        validateRoadNetwork(roadNetwork);
        this.status = TrafficEventStatus.RESOLVED;
    }

    public boolean isActive(double elapsedSeconds) {
        return elapsedSeconds >= startTimeSeconds && elapsedSeconds < startTimeSeconds + durationSeconds;
    }

    public String getId() {
        return id;
    }

    public String getAffectedRoadId() {
        return affectedRoadId;
    }

    public double getStartTimeSeconds() {
        return startTimeSeconds;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }

    public double getPositionAlongRoad() {
        return positionAlongRoad;
    }

    /** Empty means every lane for events that create a physical obstruction. */
    public Set<Integer> getBlockedLaneIndexes() {
        return blockedLaneIndexes;
    }

    /** @deprecated event time is measured in seconds; use {@link #getStartTimeSeconds()}. */
    @Deprecated
    public long getStartTick() {
        return Math.round(startTimeSeconds);
    }

    /** @deprecated event time is measured in seconds; use {@link #getDurationSeconds()}. */
    @Deprecated
    public long getDurationTicks() {
        return Math.round(durationSeconds);
    }

    public TrafficEventStatus getStatus() {
        return status;
    }

    protected void setStatus(TrafficEventStatus status) {
        this.status = status;
    }

    protected void rememberCurrentRoadCondition(RoadNetwork roadNetwork) {
        if (previousRoadCondition == null) {
            previousRoadCondition = copyCondition(getAffectedRoad(roadNetwork).getCondition());
        }
    }

    protected RoadCondition copyCurrentRoadCondition(RoadNetwork roadNetwork) {
        return copyCondition(getAffectedRoad(roadNetwork).getCondition());
    }

    protected void restorePreviousRoadCondition(RoadNetwork roadNetwork) {
        if (previousRoadCondition != null) {
            roadNetwork.updateRoadCondition(getAffectedRoadId(), copyCondition(previousRoadCondition));
            previousRoadCondition = null;
        }
    }

    /** Clears the per-application snapshot before a coordinator reapplies active overlays. */
    void resetRoadEffectMemory() {
        previousRoadCondition = null;
    }

    private Road getAffectedRoad(RoadNetwork roadNetwork) {
        validateRoadNetwork(roadNetwork);
        Road road = roadNetwork.getRoad(affectedRoadId);
        if (road == null) {
            throw new IllegalArgumentException("Affected road does not exist: " + affectedRoadId);
        }
        if (blockedLaneIndexes.stream().anyMatch(index -> index >= road.getLaneCount())) {
            throw new IllegalArgumentException(
                    "Blocked lane index is outside road " + affectedRoadId + " (lane count "
                            + road.getLaneCount() + ").");
        }
        return road;
    }

    private RoadCondition copyCondition(RoadCondition condition) {
        return new RoadCondition(
                condition.getCongestionLevel(),
                condition.getVehicleDensity(),
                condition.getRoadStatus());
    }

    private void validateRoadNetwork(RoadNetwork roadNetwork) {
        if (roadNetwork == null) {
            throw new IllegalArgumentException("Road network must not be null.");
        }
    }
}
