package team.traffic.domain.event;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;

public abstract class TrafficEvent {
    private final String id;
    private final String affectedRoadId;
    private final long startTick;
    private final long durationTicks;
    private TrafficEventStatus status;
    private RoadCondition previousRoadCondition;

    protected TrafficEvent(String id, String affectedRoadId, long startTick, long durationTicks) {
        if (durationTicks < 0) {
            throw new IllegalArgumentException("Duration ticks must not be negative.");
        }
        this.id = id;
        this.affectedRoadId = affectedRoadId;
        this.startTick = startTick;
        this.durationTicks = durationTicks;
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

    public boolean isActive(long currentTick) {
        return currentTick >= startTick && currentTick < startTick + durationTicks;
    }

    public String getId() {
        return id;
    }

    public String getAffectedRoadId() {
        return affectedRoadId;
    }

    public long getStartTick() {
        return startTick;
    }

    public long getDurationTicks() {
        return durationTicks;
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

    private Road getAffectedRoad(RoadNetwork roadNetwork) {
        validateRoadNetwork(roadNetwork);
        Road road = roadNetwork.getRoad(affectedRoadId);
        if (road == null) {
            throw new IllegalArgumentException("Affected road does not exist: " + affectedRoadId);
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
