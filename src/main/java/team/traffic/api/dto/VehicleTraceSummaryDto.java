package team.traffic.api.dto;

import java.util.List;
import team.traffic.simulation.trace.VehicleTraceSummary;

public class VehicleTraceSummaryDto {
    private Long firstTick;
    private Long lastTick;
    private int recordedTickCount;
    private List<String> vehicleIds;

    public VehicleTraceSummaryDto() {}

    public VehicleTraceSummaryDto(VehicleTraceSummary summary) {
        this.firstTick = summary.firstTick();
        this.lastTick = summary.lastTick();
        this.recordedTickCount = summary.recordedTickCount();
        this.vehicleIds = summary.vehicleIds();
    }

    public Long getFirstTick() { return firstTick; }
    public void setFirstTick(Long firstTick) { this.firstTick = firstTick; }
    public Long getLastTick() { return lastTick; }
    public void setLastTick(Long lastTick) { this.lastTick = lastTick; }
    public int getRecordedTickCount() { return recordedTickCount; }
    public void setRecordedTickCount(int recordedTickCount) { this.recordedTickCount = recordedTickCount; }
    public List<String> getVehicleIds() { return vehicleIds; }
    public void setVehicleIds(List<String> vehicleIds) { this.vehicleIds = vehicleIds; }
}
