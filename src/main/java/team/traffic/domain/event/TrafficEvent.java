package team.traffic.domain.event;

public abstract class TrafficEvent {
    protected String id;
    protected String affectedRoadId;
    protected long startTick;
    protected long durationTicks;
    protected String status; // e.g. ACTIVE, RESOLVED

    public TrafficEvent(String id, String affectedRoadId, long startTick, long durationTicks) {
        this.id = id;
        this.affectedRoadId = affectedRoadId;
        this.startTick = startTick;
        this.durationTicks = durationTicks;
        this.status = "ACTIVE";
    }

    public String getId() { return id; }
    public String getAffectedRoadId() { return affectedRoadId; }
    public long getStartTick() { return startTick; }
    public long getDurationTicks() { return durationTicks; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public abstract String getType();
    public abstract void apply();
    public abstract void resolve();
}
