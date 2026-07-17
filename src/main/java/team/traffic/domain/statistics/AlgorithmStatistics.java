package team.traffic.domain.statistics;

import java.util.List;

public class AlgorithmStatistics {
    private String algorithmName;
    private List<String> supportedPreferences;
    private long totalRoutesComputed;
    private long successfulRoutes;
    private long failedRoutes;
    private double averageComputationTime;
    private double averageVisitedNodes;
    private double averageRouteCost;
    private int routeRecalculationCount;

    public AlgorithmStatistics() {}

    public AlgorithmStatistics(String algorithmName, List<String> supportedPreferences) {
        this.algorithmName = algorithmName;
        this.supportedPreferences = supportedPreferences;
    }

    public String getAlgorithmName() { return algorithmName; }
    public void setAlgorithmName(String algorithmName) { this.algorithmName = algorithmName; }

    public List<String> getSupportedPreferences() { return supportedPreferences; }
    public void setSupportedPreferences(List<String> supportedPreferences) { this.supportedPreferences = supportedPreferences; }
    public long getTotalRoutesComputed() { return totalRoutesComputed; }
    public void setTotalRoutesComputed(long totalRoutesComputed) { this.totalRoutesComputed = totalRoutesComputed; }
    public long getSuccessfulRoutes() { return successfulRoutes; }
    public void setSuccessfulRoutes(long successfulRoutes) { this.successfulRoutes = successfulRoutes; }
    public long getFailedRoutes() { return failedRoutes; }
    public void setFailedRoutes(long failedRoutes) { this.failedRoutes = failedRoutes; }
    public double getAverageComputationTime() { return averageComputationTime; }
    public void setAverageComputationTime(double averageComputationTime) { this.averageComputationTime = averageComputationTime; }
    public double getAverageVisitedNodes() { return averageVisitedNodes; }
    public void setAverageVisitedNodes(double averageVisitedNodes) { this.averageVisitedNodes = averageVisitedNodes; }
    public double getAverageRouteCost() { return averageRouteCost; }
    public void setAverageRouteCost(double averageRouteCost) { this.averageRouteCost = averageRouteCost; }
    public int getRouteRecalculationCount() { return routeRecalculationCount; }
    public void setRouteRecalculationCount(int routeRecalculationCount) { this.routeRecalculationCount = routeRecalculationCount; }
}
