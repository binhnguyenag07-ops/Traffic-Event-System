package team.traffic.domain.statistics;

public class TrafficStatistics {
    private long currentTick;
    private int totalVehicles;
    private int arrivedVehicles;
    private int movingVehicles;
    private int blockedVehicles;
    private int waitingVehicles;
    private double averageCongestionLevel;
    private int congestedRoadCount;
    private int blockedRoadCount;
    private int closedRoadCount;
    private int activeEventCount;

    public TrafficStatistics() {}

    public long getCurrentTick() { return currentTick; }
    public void setCurrentTick(long currentTick) { this.currentTick = currentTick; }

    public int getTotalVehicles() { return totalVehicles; }
    public void setTotalVehicles(int totalVehicles) { this.totalVehicles = totalVehicles; }

    public int getArrivedVehicles() { return arrivedVehicles; }
    public void setArrivedVehicles(int arrivedVehicles) { this.arrivedVehicles = arrivedVehicles; }

    public int getMovingVehicles() { return movingVehicles; }
    public void setMovingVehicles(int movingVehicles) { this.movingVehicles = movingVehicles; }

    public int getBlockedVehicles() { return blockedVehicles; }
    public void setBlockedVehicles(int blockedVehicles) { this.blockedVehicles = blockedVehicles; }

    public int getWaitingVehicles() { return waitingVehicles; }
    public void setWaitingVehicles(int waitingVehicles) { this.waitingVehicles = waitingVehicles; }

    public double getAverageCongestionLevel() { return averageCongestionLevel; }
    public void setAverageCongestionLevel(double averageCongestionLevel) { this.averageCongestionLevel = averageCongestionLevel; }

    public int getCongestedRoadCount() { return congestedRoadCount; }
    public void setCongestedRoadCount(int congestedRoadCount) { this.congestedRoadCount = congestedRoadCount; }

    public int getBlockedRoadCount() { return blockedRoadCount; }
    public void setBlockedRoadCount(int blockedRoadCount) { this.blockedRoadCount = blockedRoadCount; }

    public int getClosedRoadCount() { return closedRoadCount; }
    public void setClosedRoadCount(int closedRoadCount) { this.closedRoadCount = closedRoadCount; }

    public int getActiveEventCount() { return activeEventCount; }
    public void setActiveEventCount(int activeEventCount) { this.activeEventCount = activeEventCount; }
}
