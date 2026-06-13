package team.traffic.domain.event;

import team.traffic.domain.network.RoadNetwork;

public abstract class TrafficEvent {
    private final String id;
    private final String affectedRoadId;
    private final long startTick;
    private final long durationTicks;
    private TrafficEventStatus status;

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
        if (roadNetwork == null) {
            throw new IllegalArgumentException("Road network must not be null.");
        }
        this.status = TrafficEventStatus.ACTIVE;
    }

    public void resolve(RoadNetwork roadNetwork) {
        if (roadNetwork == null) {
            throw new IllegalArgumentException("Road network must not be null.");
        }
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
}
