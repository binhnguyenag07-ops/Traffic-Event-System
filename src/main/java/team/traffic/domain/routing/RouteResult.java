package team.traffic.domain.routing;

public class RouteResult {
    private Route route;
    private String strategyName;
    private long computationTimeMillis;
    private boolean recalculated;

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public long getComputationTimeMillis() {
        return computationTimeMillis;
    }

    public void setComputationTimeMillis(long computationTimeMillis) {
        this.computationTimeMillis = computationTimeMillis;
    }

    public boolean isRecalculated() {
        return recalculated;
    }

    public void setRecalculated(boolean recalculated) {
        this.recalculated = recalculated;
    }
}
