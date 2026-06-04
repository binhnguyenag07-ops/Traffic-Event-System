package team.traffic.api.dto;

public class SimulationSnapshotDto {
    private long tick;
    private String status;
    private int vehicleCount;
    private int activeEventCount;
    private double averageCongestionLevel;

    public long getTick() {
        return tick;
    }

    public void setTick(long tick) {
        this.tick = tick;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public void setVehicleCount(int vehicleCount) {
        this.vehicleCount = vehicleCount;
    }

    public int getActiveEventCount() {
        return activeEventCount;
    }

    public void setActiveEventCount(int activeEventCount) {
        this.activeEventCount = activeEventCount;
    }

    public double getAverageCongestionLevel() {
        return averageCongestionLevel;
    }

    public void setAverageCongestionLevel(double averageCongestionLevel) {
        this.averageCongestionLevel = averageCongestionLevel;
    }
}
