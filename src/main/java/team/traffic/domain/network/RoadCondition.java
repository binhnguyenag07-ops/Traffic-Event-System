package team.traffic.domain.network;

public class RoadCondition {
    private double congestionLevel;
    private double vehicleDensity;
    private RoadStatus roadStatus;

    // Stores finite traffic ratios, keeping congestion and density in the 0..1 range.
    public RoadCondition(double congestionLevel, double vehicleDensity, RoadStatus roadStatus) {
        this.congestionLevel = clampFiniteRatio(congestionLevel, "Congestion level");
        this.vehicleDensity = clampFiniteRatio(vehicleDensity, "Vehicle density");
        this.roadStatus = roadStatus == null ? RoadStatus.OPEN : roadStatus;
    }

    // Adds congestion and marks an open road as congested when needed.
    public double increaseCongestion(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Congestion increase must be finite and non-negative");
        }

        congestionLevel = Math.min(1.0, congestionLevel + amount);
        if (roadStatus == RoadStatus.OPEN && congestionLevel > 0) {
            roadStatus = RoadStatus.CONGESTED;
        }
        return congestionLevel;
    }

    /**
     * Returns the multiplier used by congestion-aware routing.
     *
     * <p>The formula is {@code 1 + congestionLevel + vehicleDensity}. Both
     * traffic inputs are normalized to 0..1, so a routable road has a multiplier
     * from 1.0 to 3.0.</p>
     */
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
        this.congestionLevel = clampFiniteRatio(congestionLevel, "Congestion level");
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
        this.vehicleDensity = clampFiniteRatio(vehicleDensity, "Vehicle density");
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

    // Rejects undefined values and clamps finite percentage-like inputs.
    private double clampFiniteRatio(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }
}
