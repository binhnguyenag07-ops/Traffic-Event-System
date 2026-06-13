package main.java.team.traffic.domain.network;

public class RoadCondition {
    private double congestionLevel;
    private double vehicleDensity;
    private RoadStatus roadStatus;

    public RoadCondition(double congestionLevel, double vehicleDensity, RoadStatus roadStatus) {
        this.congestionLevel = congestionLevel;
        this.vehicleDensity = vehicleDensity;
        this.roadStatus = roadStatus;
    }

    public double increaseCongestion() {
        double congestion = 0;
        return congestion;
    }
    public boolean markBlocked() {
        return true;
    }

    public boolean markOpen() {
        return true;
    }
}
