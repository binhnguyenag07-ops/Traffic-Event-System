package team.traffic.domain.routing;

public class RouteResult {
    private final Route route;
    private final String algorithmName;
    private final long computationTimeMillis;
    private final int visitedNodes;

    public RouteResult(Route route, String algorithmName, long computationTimeMillis, int visitedNodes) {
        this.route = route;
        this.algorithmName = algorithmName;
        this.computationTimeMillis = computationTimeMillis;
        this.visitedNodes = visitedNodes;
    }

    public Route getRoute() {
        return route;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public long getComputationTimeMillis() {
        return computationTimeMillis;
    }

    public int getVisitedNodes() {
        return visitedNodes;
    }

    @Override
    public String toString() {
        return "RouteResult{algorithm=" + algorithmName + ", steps=" + route.getStepCount()
                + ", visitedNodes=" + visitedNodes + ", computationTime=" + computationTimeMillis + "ms}";
    }
}
