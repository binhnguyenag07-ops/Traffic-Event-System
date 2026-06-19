package team.traffic.domain.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Route {
    private final List<RouteStep> steps;
    private final double totalDistance;
    private final double estimatedTravelTime;
    private final double totalCost;

    public Route(List<RouteStep> steps, double totalDistance, double estimatedTravelTime, double totalCost) {
        this.steps = new ArrayList<>(steps);
        this.totalDistance = totalDistance;
        this.estimatedTravelTime = estimatedTravelTime;
        this.totalCost = totalCost;
    }

    public List<RouteStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public double getEstimatedTravelTime() {
        return estimatedTravelTime;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public int getStepCount() {
        return steps.size();
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    @Override
    public String toString() {
        return "Route{steps=" + steps.size() + ", distance=" + totalDistance + ", time=" + estimatedTravelTime + ", cost=" + totalCost + "}";
    }
}
