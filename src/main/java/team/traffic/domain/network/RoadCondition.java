package team.traffic.domain.network;

public class RoadCondition {
    private double congestionLevel;
    private double vehicleDensity;
    private RoadStatus roadStatus;

    // Stores road traffic values, keeping congestion and density in the 0..1 range.
    public RoadCondition(double congestionLevel, double vehicleDensity, RoadStatus roadStatus) {
        this.congestionLevel = clamp(congestionLevel);
        this.vehicleDensity = clamp(vehicleDensity);
        this.roadStatus = roadStatus == null ? RoadStatus.OPEN : roadStatus;
    }

    // Adds congestion and marks an open road as congested when needed.
    public double increaseCongestion(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Congestion increase cannot be negative");
        }

        congestionLevel = clamp(congestionLevel + amount);
        if (roadStatus == RoadStatus.OPEN && congestionLevel > 0) {
            roadStatus = RoadStatus.CONGESTED;
        }
        return congestionLevel;
    }

    public double getTravelCostMultiplier() {
        if (!isRoutable()) {
            return Double.POSITIVE_INFINITY;
        }
        return 1 + congestionLevel + vehicleDensity;
    }

    public boolean isRoutable() {
        return roadStatus.isRoutable();
    }

    // Blocks the road and treats it as fully congested.
    public void markBlocked() {
        roadStatus = RoadStatus.BLOCKED;
        congestionLevel = 1.0;
    }

    // Reopens the road and clears congestion.
    public void markOpen() {
        roadStatus = RoadStatus.OPEN;
        congestionLevel = 0;
    }

    public double getCongestionLevel() {
        return congestionLevel;
    }

    public void setCongestionLevel(double congestionLevel) {
        this.congestionLevel = clamp(congestionLevel);
        if (roadStatus == RoadStatus.OPEN && this.congestionLevel > 0) {
            roadStatus = RoadStatus.CONGESTED;
        } else if (roadStatus == RoadStatus.CONGESTED && this.congestionLevel == 0) {
            roadStatus = RoadStatus.OPEN;
        }
    }

    public double getVehicleDensity() {
        return vehicleDensity;
    }

    public void setVehicleDensity(double vehicleDensity) {
        this.vehicleDensity = clamp(vehicleDensity);
    }

    public RoadStatus getRoadStatus() {
        return roadStatus;
    }

    public void setRoadStatus(RoadStatus roadStatus) {
        if (roadStatus == null) {
            throw new IllegalArgumentException("Road status is required");
        }
        this.roadStatus = roadStatus;
    }

    // Keeps percentage-like values inside valid traffic bounds.
    private double clamp(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }
}
