package team.traffic.domain.network;

public class RoadCondition {
    private double congestionLevel; // 0.0 to 1.0+
    private double vehicleDensity;
    private String status;

    public RoadCondition() {
        this.congestionLevel = 0.0;
        this.vehicleDensity = 0.0;
        this.status = RoadStatus.OPEN;
    }

    public double getCongestionLevel() { return congestionLevel; }
    public void setCongestionLevel(double congestionLevel) { this.congestionLevel = congestionLevel; }

    public double getVehicleDensity() { return vehicleDensity; }
    public void setVehicleDensity(double vehicleDensity) { this.vehicleDensity = vehicleDensity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
