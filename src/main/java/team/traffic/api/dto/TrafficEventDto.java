package team.traffic.api.dto;

import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.BreakdownEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.event.TrafficEventType;
import java.util.Set;

public class TrafficEventDto {
    private String id;
    private TrafficEventType type;
    private String affectedRoadId;
    private double startTimeSeconds;
    private double durationSeconds;
    private double positionAlongRoad;
    private Set<Integer> blockedLaneIndexes;
    private String causingVehicleId;
    private String affectedVehicleId;
    private Set<String> involvedVehicleIds;
    private TrafficEventStatus status;

    public TrafficEventDto() {}

    public TrafficEventDto(TrafficEvent event) {
        this.id = event.getId();
        this.type = event.getType();
        this.affectedRoadId = event.getAffectedRoadId();
        this.startTimeSeconds = event.getStartTimeSeconds();
        this.durationSeconds = event.getDurationSeconds();
        this.positionAlongRoad = event.getPositionAlongRoad();
        this.blockedLaneIndexes = event.getBlockedLaneIndexes();
        if (event instanceof AccidentEvent accident) {
            this.causingVehicleId = accident.getCausingVehicleId();
            this.involvedVehicleIds = accident.getInvolvedVehicleIds();
        }
        if (event instanceof BreakdownEvent breakdown) {
            this.affectedVehicleId = breakdown.getAffectedVehicleId();
        }
        this.status = event.getStatus();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public TrafficEventType getType() { return type; }
    public void setType(TrafficEventType type) { this.type = type; }
    public String getAffectedRoadId() { return affectedRoadId; }
    public void setAffectedRoadId(String affectedRoadId) { this.affectedRoadId = affectedRoadId; }
    public double getStartTimeSeconds() { return startTimeSeconds; }
    public void setStartTimeSeconds(double startTimeSeconds) { this.startTimeSeconds = startTimeSeconds; }
    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double durationSeconds) { this.durationSeconds = durationSeconds; }
    public double getPositionAlongRoad() { return positionAlongRoad; }
    public void setPositionAlongRoad(double positionAlongRoad) { this.positionAlongRoad = positionAlongRoad; }
    public Set<Integer> getBlockedLaneIndexes() { return blockedLaneIndexes; }
    public void setBlockedLaneIndexes(Set<Integer> blockedLaneIndexes) { this.blockedLaneIndexes = blockedLaneIndexes; }
    public String getCausingVehicleId() { return causingVehicleId; }
    public void setCausingVehicleId(String causingVehicleId) { this.causingVehicleId = causingVehicleId; }
    public String getAffectedVehicleId() { return affectedVehicleId; }
    public void setAffectedVehicleId(String affectedVehicleId) { this.affectedVehicleId = affectedVehicleId; }
    public Set<String> getInvolvedVehicleIds() { return involvedVehicleIds; }
    public void setInvolvedVehicleIds(Set<String> involvedVehicleIds) { this.involvedVehicleIds = involvedVehicleIds; }
    public TrafficEventStatus getStatus() { return status; }
    public void setStatus(TrafficEventStatus status) { this.status = status; }
}
