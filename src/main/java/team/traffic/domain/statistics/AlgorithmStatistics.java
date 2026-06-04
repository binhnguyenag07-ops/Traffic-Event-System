package team.traffic.domain.statistics;

public class AlgorithmStatistics {
    private String strategyName;
    private long routingComputationTimeMillis;
    private int recalculationCount;
    private double pathOptimalityScore;

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public long getRoutingComputationTimeMillis() {
        return routingComputationTimeMillis;
    }

    public void setRoutingComputationTimeMillis(long routingComputationTimeMillis) {
        this.routingComputationTimeMillis = routingComputationTimeMillis;
    }

    public int getRecalculationCount() {
        return recalculationCount;
    }

    public void setRecalculationCount(int recalculationCount) {
        this.recalculationCount = recalculationCount;
    }

    public double getPathOptimalityScore() {
        return pathOptimalityScore;
    }

    public void setPathOptimalityScore(double pathOptimalityScore) {
        this.pathOptimalityScore = pathOptimalityScore;
    }
}
