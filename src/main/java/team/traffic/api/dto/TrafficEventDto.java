package team.traffic.api.dto;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.event.TrafficEventType;

public class TrafficEventDto {
    private String id;
    private TrafficEventType type;
    private String affectedRoadId;
    private long startTick;
    private long durationTicks;
    private TrafficEventStatus status;

    public TrafficEventDto() {}

    public TrafficEventDto(TrafficEvent event) {
        this.id = event.getId();
        this.type = event.getType();
        this.affectedRoadId = event.getAffectedRoadId();
        this.startTick = event.getStartTick();
        this.durationTicks = event.getDurationTicks();
        this.status = event.getStatus();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public TrafficEventType getType() { return type; }
    public void setType(TrafficEventType type) { this.type = type; }
    public String getAffectedRoadId() { return affectedRoadId; }
    public void setAffectedRoadId(String affectedRoadId) { this.affectedRoadId = affectedRoadId; }
    public long getStartTick() { return startTick; }
    public void setStartTick(long startTick) { this.startTick = startTick; }
    public long getDurationTicks() { return durationTicks; }
    public void setDurationTicks(long durationTicks) { this.durationTicks = durationTicks; }
    public TrafficEventStatus getStatus() { return status; }
    public void setStatus(TrafficEventStatus status) { this.status = status; }
}
