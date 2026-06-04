package team.traffic.domain.statistics;

public class TrafficStatistics {
    private long totalTravelTime;
    private double averageTravelTime;
    private double averageRouteLength;
    private int activeVehicleCount;
    private double averageCongestionLevel;
    private int blockedRoadCount;

    public long getTotalTravelTime() {
        return totalTravelTime;
    }

    public void setTotalTravelTime(long totalTravelTime) {
        this.totalTravelTime = totalTravelTime;
    }

    public double getAverageTravelTime() {
        return averageTravelTime;
    }

    public void setAverageTravelTime(double averageTravelTime) {
        this.averageTravelTime = averageTravelTime;
    }

    public double getAverageRouteLength() {
        return averageRouteLength;
    }

    public void setAverageRouteLength(double averageRouteLength) {
        this.averageRouteLength = averageRouteLength;
    }

    public int getActiveVehicleCount() {
        return activeVehicleCount;
    }

    public void setActiveVehicleCount(int activeVehicleCount) {
        this.activeVehicleCount = activeVehicleCount;
    }

    public double getAverageCongestionLevel() {
        return averageCongestionLevel;
    }

    public void setAverageCongestionLevel(double averageCongestionLevel) {
        this.averageCongestionLevel = averageCongestionLevel;
    }

    public int getBlockedRoadCount() {
        return blockedRoadCount;
    }

    public void setBlockedRoadCount(int blockedRoadCount) {
        this.blockedRoadCount = blockedRoadCount;
    }
}
